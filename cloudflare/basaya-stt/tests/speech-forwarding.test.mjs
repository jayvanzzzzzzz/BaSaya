import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const fixedSource = await readFile(new URL('../speech-api-fix/lib/assessmentSpeech.js', import.meta.url), 'utf8');
const workerSource = await readFile(new URL('../src/index.js', import.meta.url), 'utf8');
const token = 'test.' + Buffer.from(JSON.stringify({ sub: 'student' })).toString('base64url') + '.test';

async function setup(source) {
  const state = { ai: 0, firestore: 0, answers: 0 };
  const workerContext = vm.createContext({ URL, Request, Response, atob, btoa, console, fetch: async () => {
    state.firestore++;
    return new Response(JSON.stringify({ fields: { role: { stringValue: 'student' } } }));
  } });
  const workerModule = new vm.SourceTextModule(workerSource, { context: workerContext });
  await workerModule.link(() => { throw new Error('Unexpected worker import'); });
  await workerModule.evaluate();
  const env = { ALLOWED_ORIGIN: 'https://basaya.onrender.com', FIREBASE_PROJECT_ID: 'basaya', AI: { run: async () => { state.ai++; return { text: 'bahay' }; } } };
  const context = vm.createContext({ Buffer, process: { env: {} }, AbortSignal, fetch: async (url, options) => {
    assert.equal(url, 'https://basaya-stt.jayvanzsingca5.workers.dev/transcribe');
    assert.equal(options.headers.Authorization, 'Bearer ' + token);
    return workerModule.namespace.default.fetch(new Request(url, options), env);
  } });
  const synthetic = exports => new vm.SyntheticModule(Object.keys(exports), function () { for (const [key, value] of Object.entries(exports)) this.setExport(key, value); }, { context });
  class AssessmentError extends Error { constructor(status, message) { super(message); this.status = status; } }
  const modules = {
    'firebase-admin/firestore': synthetic({ Timestamp: { fromMillis: n => n } }),
    './assessments.js': synthetic({ AssessmentError, assessmentOperation: async (_db, _uid, body) => {
      if (body.operation === 'speech-check') return { word: 'bahay' };
      assert.equal(body.operation, 'answer'); state.answers++; return { right: true };
    } }),
    './pronunciationScoring.js': synthetic({ score: () => ({ band: 'great' }) }),
  };
  const api = new vm.SourceTextModule(source, { context });
  await api.link(name => modules[name]); await api.evaluate();
  const db = { doc: path => ({ path }), runTransaction: async callback => callback({ get: async () => ({ get: () => [] }), set() {} }) };
  const wav = Buffer.alloc(64); wav.write('RIFF'); wav.write('WAVE', 8);
  const run = () => api.namespace.submitSpeech(db, 'student', { operation: 'speech', part: 'pronunciation', index: 0, eventId: 'test', audio: wav.toString('base64') }, 'Bearer ' + token);
  return { state, run };
}

test('old deployed forwarding is rejected by the Worker and returns the reported 503', async () => {
  const oldSource = fixedSource.replace(/,\n\s*Origin: process\.env\.STT_ALLOWED_ORIGIN \|\| "https:\/\/basaya\.onrender\.com"/, '');
  assert.notEqual(oldSource, fixedSource);
  const h = await setup(oldSource);
  await assert.rejects(h.run(), e => e.status === 503 && e.message === 'Hindi available ang pagsuri ng pagbigkas. Subukan muli mamaya.');
  assert.equal(h.state.ai, 0);
  assert.equal(h.state.answers, 0);
});

test('fixed forwarding supplies the allowed origin and records the server-derived result', async () => {
  const h = await setup(fixedSource);
  assert.equal((await h.run()).right, true);
  assert.equal(h.state.firestore, 1);
  assert.equal(h.state.ai, 1);
  assert.equal(h.state.answers, 1);
});
