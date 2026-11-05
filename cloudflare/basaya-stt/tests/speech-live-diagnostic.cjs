const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
async function main() {
  const config = fs.readFileSync(path.join(process.env.APPDATA, 'xdg.config/.wrangler/config/default.toml'), 'utf8');
  const token = config.match(/^oauth_token\s*=\s*"([^"]+)"/m)?.[1];
  if (token) {
    const account = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../.wrangler/cache/wrangler-account.json'), 'utf8')).account.id;
    const response = await fetch(`https://api.cloudflare.com/client/v4/accounts/${account}/workers/scripts/basaya-stt/content`, { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(20000) });
    const source = await response.text();
    console.log(JSON.stringify({ check: 'deployed worker', status: response.status, bytes: source.length, sha256: crypto.createHash('sha256').update(source).digest('hex'), model: source.match(/@cf\/[^"\s]+/)?.[0], requiresOrigin: source.includes('Origin not allowed'), readsFirestore: source.includes('firestore.googleapis.com') }));
    if (!response.ok) console.log(source.slice(0, 400));
  }
  const properties = fs.readFileSync('C:/Projects/BaSaya/local.properties', 'utf8');
  const key = properties.match(/^stt\.appKey\s*=\s*(.+)$/m)?.[1]?.trim();
  const url = properties.match(/^stt\.workerUrl\s*=\s*(.+)$/m)?.[1]?.trim()?.replace(/\\:/g, ':');
  if (!key || !url) { console.log('Android speech configuration unavailable for test'); return; }
  const pcmBytes = 16000; // 0.5 s of silent 16 kHz mono PCM, no student data.
  const wav = Buffer.alloc(44 + pcmBytes);
  wav.write('RIFF'); wav.writeUInt32LE(36 + pcmBytes,4); wav.write('WAVE',8); wav.write('fmt ',12);
  wav.writeUInt32LE(16,16); wav.writeUInt16LE(1,20); wav.writeUInt16LE(1,22); wav.writeUInt32LE(16000,24); wav.writeUInt32LE(32000,28); wav.writeUInt16LE(2,32); wav.writeUInt16LE(16,34); wav.write('data',36); wav.writeUInt32LE(pcmBytes,40);
  const started = Date.now();
  const response = await fetch(url.replace(/\/$/,'')+'/transcribe', {method:'POST',headers:{'Content-Type':'audio/wav','X-App-Key':key},body:wav,signal:AbortSignal.timeout(25000)});
  console.log(JSON.stringify({check:'live silent WAV',status:response.status,elapsedMs:Date.now()-started,body:(await response.text()).slice(0,500)}));
}
main().catch(error=>{console.error(error.name, error.message);process.exitCode=1});
