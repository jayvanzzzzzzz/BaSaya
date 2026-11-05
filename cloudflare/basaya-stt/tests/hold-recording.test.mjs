import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { createRequire } from 'node:module';
import { setImmediate as flush } from 'node:timers/promises';

const web = 'C:/xampp/htdocs/BaSaya-web/';
const require = createRequire(web + 'notify-api/package.json');
const { parseHTML } = require('linkedom');
const settle = async () => { await flush(); await flush(); };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };

async function recordingHarness() {
  let processor, stops = 0;
  class AudioContext {
    state = 'running'; sampleRate = 16000; destination = {};
    createMediaStreamSource() { return { connect() {}, disconnect() {} }; }
    createScriptProcessor() { return processor = { connect() {}, disconnect() {}, onaudioprocess: null }; }
    async close() { this.state = 'closed'; }
  }
  const context = vm.createContext({ window: { AudioContext }, navigator: { mediaDevices: { getUserMedia: async () => ({ active: true, getTracks: () => [{ stop() { stops++; } }] }) } }, Blob, AbortController, setTimeout, clearTimeout, console });
  const source = (await readFile(web + 'student/speech.js', 'utf8')).replace(/^import .*;\r?\n/, '').replace(/export /g, '');
  vm.runInContext(source + '\nglobalThis.Microphone = Microphone;', context);
  const mic = new context.Microphone();
  assert.equal(await mic.open(), 'ok');
  return { mic, get stops() { return stops; }, feed(value = 0.1) { processor.onaudioprocess?.({ inputBuffer: { getChannelData: () => new Float32Array(4096).fill(value) } }); } };
}

test('recording waits through silence and sends a valid WAV only on release', async () => {
  const h = await recordingHarness();
  const cancel = new AbortController(), release = new AbortController();
  let resolved = false;
  const result = h.mic.record(() => {}, cancel.signal, release.signal).then(r => { resolved = true; return r; });
  h.feed(0.1);
  for (let i = 0; i < 12; i++) h.feed(0);
  await settle();
  assert.equal(resolved, false);
  release.abort();
  const bytes = new DataView(await (await result).wav.arrayBuffer());
  assert.equal(bytes.getUint32(24, true), 16000);
  assert.equal(bytes.getUint16(22, true), 1);
  assert.equal(bytes.getUint32(40, true), bytes.byteLength - 44);
  h.mic.close();
  assert.equal(h.stops, 1);
});

test('seven-second cap bounds the WAV but still waits for release', async () => {
  const h = await recordingHarness(), release = new AbortController();
  let resolved = false;
  const result = h.mic.record(() => {}, undefined, release.signal).then(r => { resolved = true; return r; });
  for (let i = 0; i < 100; i++) h.feed();
  await settle();
  assert.equal(resolved, false);
  release.abort();
  assert.equal((await result).wav.size, 44 + 7 * 16000 * 2);
  h.mic.close();
});

test('cancellation discards captured audio; silence and immediate release produce no WAV', async () => {
  const h = await recordingHarness(), cancel = new AbortController(), release = new AbortController();
  const result = h.mic.record(() => {}, cancel.signal, release.signal);
  h.feed(); cancel.abort(); release.abort();
  assert.equal((await result).cancelled, true);
  const quiet = new AbortController();
  const quietResult = h.mic.record(() => {}, undefined, quiet.signal);
  h.feed(0); quiet.abort();
  assert.equal((await quietResult).noSpeech, true);
  const alreadyReleased = new AbortController(); alreadyReleased.abort();
  assert.equal((await h.mic.record(() => {}, undefined, alreadyReleased.signal)).noSpeech, true);
  h.mic.close();
});

async function viewHarness({ delayedPermission = false, practice = true } = {}) {
  const { document, window } = parseHTML('<html><body><main></main></body></html>');
  const state = { opened: 0, recorded: 0, uploads: 0, assessments: 0, closed: 0, leave: null };
  const permission = deferred();
  class Microphone {
    static supported() { return true; }
    async open() { state.opened++; return delayedPermission ? permission.promise : 'ok'; }
    close() { state.closed++; }
    record(level, signal, release) {
      state.recorded++;
      level(0.8);
      return new Promise(resolve => {
        signal.addEventListener('abort', () => resolve({ cancelled: true }), { once: true });
        release.addEventListener('abort', () => resolve({ wav: new Blob(['audio']) }), { once: true });
      });
    }
  }
  const context = vm.createContext({ window, document, console, navigator: { onLine: true }, AbortController, Blob, crypto: { randomUUID: () => 'event-one' }, btoa: s => Buffer.from(s).toString('base64'), setTimeout, clearTimeout,
    Audio: class { pause() {} }, });
  const module = exports => new vm.SyntheticModule(Object.keys(exports), function () { for (const [key, value] of Object.entries(exports)) this.setExport(key, value); }, { context });
  const $ = s => document.querySelector(s);
  const modules = {
    'helpers.js': module({ $, escapeHtml: s => s, navigate() {}, openModal() {}, toast() {} }),
    'dictionary.js': module({ lookupWord: async () => null }),
    'syllables.js': module({ syllabify: s => s }),
    'data.js': module({ availability: () => 'open', clearResume: () => ({}), findLesson: async () => ({ practice }), loadLessonProgress: async () => ({}), loadPronunciation: async () => ({ words: [{ word: 'bahay' }] }), resumeOf: () => null, saveProgress: async () => ({}), saveResume() {}, assessmentRequest: async () => { state.assessments++; return { noSpeech: true }; } }),
    'router.js': module({ onLeave: fn => { state.leave = fn; } }),
    'speech.js': module({ Microphone, browserRecognitionAvailable: () => false, listenWithBrowser: async () => ({}), score: () => ({ band: 'try-again' }), transcribe: async () => { state.uploads++; return { text: '' }; } }),
    'ui.js': module({ content: () => $('main'), icon: () => '', talkingBuddy: () => '<div class="st-pron__buddy"></div>', confetti() {}, confettiFrom() {}, setBuddyMood() {}, setTalking() {}, shake() {} }),
    'results.js': module({ renderResults() {} }),
    firebase: module({ serverTimestamp: () => ({}) }),
  };
  const controller = new vm.SourceTextModule(await readFile(web + 'student/views/pronunciation.js', 'utf8'), { context });
  await controller.link(specifier => specifier.includes('gstatic') ? modules.firebase : modules[specifier.split('/').pop()]);
  await controller.evaluate();
  await controller.namespace.renderPronunciation('lesson');
  const mic = $('#pron-mic');
  mic.setPointerCapture = () => {};
  const event = (type, fields = {}) => {
    const ev = new window.Event(type, { bubbles: true, cancelable: true });
    Object.assign(ev, { button: 0, pointerId: 1, ...fields });
    mic.dispatchEvent(ev);
  };
  return { state, permission, event, $, window };
}

test('pointer hold does not upload; release sends exactly once and returns to idle', async () => {
  const h = await viewHarness();
  h.event('pointerdown'); await settle();
  assert.equal(h.state.recorded, 1);
  assert.equal(h.state.uploads, 0);
  assert.equal(h.$('#pron-hint').textContent, 'Bitawan upang suriin');
  h.event('pointerup'); h.event('pointerup'); await settle();
  assert.equal(h.state.uploads, 1);
  assert.equal(h.$('#pron-mic').getAttribute('aria-pressed'), 'false');
  h.state.leave();
});

test('pointer cancellation and page exit discard the recording', async () => {
  for (const mode of ['pointercancel', 'leave', 'blur']) {
    const h = await viewHarness(); h.event('pointerdown'); await settle();
    if (mode === 'leave') h.state.leave();
    else if (mode === 'blur') h.window.dispatchEvent(new h.window.Event('blur'));
    else h.event(mode);
    await settle();
    assert.equal(h.state.uploads, 0, mode);
    if (mode !== 'leave') h.state.leave();
  }
});

test('release during permission prompt prevents a late recording or upload', async () => {
  const h = await viewHarness({ delayedPermission: true });
  h.event('pointerdown'); h.event('pointerup');
  h.permission.resolve('ok'); await settle();
  assert.equal(h.state.recorded, 0);
  assert.equal(h.state.uploads, 0);
  assert.ok(h.state.closed > 0);
  h.state.leave();
});

test('a cancelled permission request cannot reset a newer hold', async () => {
  const h = await viewHarness({ delayedPermission: true });
  h.event('pointerdown');
  h.event('pointercancel');
  h.event('pointerdown', { pointerId: 2 });
  h.permission.resolve('ok'); await settle();
  assert.equal(h.state.recorded, 1);
  assert.equal(h.$('#pron-mic').getAttribute('aria-pressed'), 'true');
  h.event('pointerup', { pointerId: 2 }); await settle();
  assert.equal(h.state.uploads, 1);
  h.state.leave();
});

test('keyboard hold sends an assessed recording through the assessment server on release', async () => {
  const h = await viewHarness({ practice: false });
  h.event('keydown', { key: ' ', repeat: false }); await settle();
  h.event('keydown', { key: ' ', repeat: true });
  assert.equal(h.state.assessments, 0);
  assert.equal(h.state.recorded, 1);
  h.event('keyup', { key: ' ' }); await settle();
  assert.equal(h.state.assessments, 1);
  assert.equal(h.state.uploads, 0);
  h.state.leave();
});
