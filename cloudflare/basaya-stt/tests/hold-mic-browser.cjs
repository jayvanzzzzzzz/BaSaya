const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require('C:/Users/Scean Andrei/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root = 'C:/xampp/htdocs/BaSaya-web';
const out = path.resolve(__dirname, '../hold-mic-preview');
const mocks = {
  '/js/helpers.js': `export const $=s=>document.querySelector(s); export const escapeHtml=s=>s; export const navigate=()=>{}; export const openModal=()=>{}; export const toast=()=>{};`,
  '/js/dictionary.js': 'export const lookupWord=async()=>null;',
  '/js/syllables.js': 'export const syllabify=()=>"ba·hay";',
  '/student/data.js': `export const availability=()=>"open", clearResume=()=>({}), findLesson=async()=>({practice:true}), loadLessonProgress=async()=>({}), loadPronunciation=async()=>({words:[{word:"Bahay"}]}), resumeOf=()=>null, saveProgress=async()=>({}), saveResume=()=>{}, assessmentRequest=async()=>({});`,
  '/student/router.js': 'export const onLeave=fn=>window.cleanup=fn;',
  '/student/views/results.js': 'export const renderResults=()=>{};',
  '/student/speech.js': `export class Microphone {
    static supported(){return true} async open(){return 'ok'} close(){}
    record(level,signal,release){return new Promise(resolve=>{let timer=setInterval(()=>level(.35+.6*Math.abs(Math.sin(performance.now()/230))),50); const done=r=>{clearInterval(timer);resolve(r)};signal.addEventListener('abort',()=>done({cancelled:true}),{once:true});release.addEventListener('abort',()=>done({wav:new Blob(['audio'])}),{once:true});});}
  } export const browserRecognitionAvailable=()=>false,listenWithBrowser=async()=>({}),score=()=>({band:'try-again'}),transcribe=async()=>{window.uploads++;await new Promise(r=>setTimeout(r,250));return {text:''}};`,
};
const css = ['base/tokens','base/reset','base/typography','base/animations','components/buttons','layout/student-shell','pages/student-lesson','pages/student-play'];
const html = `<!doctype html><html lang="fil"><head><meta name="viewport" content="width=device-width,initial-scale=1">${css.map(c=>`<link rel="stylesheet" href="/css/${c}.css">`).join('')}</head><body class="st-body"><div class="st-shell is-focus"><main class="st-content" id="page-content"></main></div><script type="module">window.uploads=0;import {renderPronunciation} from '/student/views/pronunciation.js';await renderPronunciation('preview');</script></body></html>`;
const server = http.createServer((req,res)=>{
  const route = new URL(req.url,'http://localhost').pathname;
  if(route==='/'){res.setHeader('Content-Type','text/html');return res.end(html)}
  if(mocks[route]){res.setHeader('Content-Type','text/javascript');return res.end(mocks[route])}
  const file=path.resolve(root,'.'+decodeURIComponent(route));
  if(!file.startsWith(path.resolve(root)+path.sep)){res.writeHead(403);return res.end()}
  try{res.setHeader('Content-Type',file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'application/octet-stream');res.end(fs.readFileSync(file))}catch{res.writeHead(404);res.end()}
});
(async()=>{
  fs.mkdirSync(out,{recursive:true});
  await new Promise(r=>server.listen(0,'127.0.0.1',r));
  let browser;
  try{
    browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
    for(const [label,width,height,dark] of [['phone',390,844,false],['desktop',1280,900,false],['phone-dark',390,844,true]]){
      const page=await browser.newPage({viewport:{width,height}});
      const errors=[];page.on('pageerror',e=>errors.push(e.message));
      await page.route('https://www.gstatic.com/**',route=>route.fulfill({contentType:'text/javascript',body:'export const serverTimestamp=()=>({});'}));
      await page.goto(`http://127.0.0.1:${server.address().port}/`);
      await page.locator('#pron-mic').waitFor();
      if(dark)await page.evaluate(()=>document.documentElement.dataset.theme='dark');
      await page.screenshot({path:path.join(out,label+'-idle.png'),fullPage:true});
      const box=await page.locator('#pron-mic').boundingBox();
      await page.mouse.move(box.x+box.width/2,box.y+box.height/2);
      await page.mouse.down();
      await page.waitForFunction(()=>document.querySelector('.st-pron__mic-area').classList.contains('is-listening'));
      await page.waitForFunction(()=>parseFloat(getComputedStyle(document.querySelector('.st-pron__wave')).opacity)===1);
      assert.equal(await page.evaluate(()=>window.uploads),0);
      const bars=await page.locator('.st-pron__wave i').evaluateAll(nodes=>nodes.map(n=>n.getBoundingClientRect().height));
      assert.ok(Math.max(...bars)>Math.min(...bars));
      await page.screenshot({path:path.join(out,label+'-holding.png'),fullPage:true});
      // Pointer capture must deliver release even after moving outside the button.
      await page.mouse.move(box.x-35,box.y-35);
      await page.mouse.up();
      await page.waitForFunction(()=>window.uploads===1);
      await page.screenshot({path:path.join(out,label+'-checking.png'),fullPage:true});
      await page.waitForFunction(()=>!document.querySelector('.st-pron__mic-area').classList.contains('is-thinking'));
      assert.deepEqual(errors,[]);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
      console.log(`PASS ${label}: hold, live waveform, outside release, one upload, no overflow or JS errors`);
      await page.close();
    }
  } finally {await browser?.close();server.close();}
})().catch(error=>{console.error(error);process.exitCode=1});
