const fs = require('node:fs');
const path = require('node:path');
async function main() {
  let token;
  for (const root of [process.env.LOCALAPPDATA,process.env.APPDATA,process.env.USERPROFILE]) {
    for (const name of ['com.vercel.cli/Data/auth.json','com.vercel.cli/auth.json','.local/share/com.vercel.cli/auth.json','.config/vercel/auth.json']) {
      try {token = JSON.parse(fs.readFileSync(path.join(root,name),'utf8')).token;}catch{}
      if(token) break;
    }
    if(token) break;
  }
  if(!token) throw new Error('Vercel CLI credential location unavailable');
  const team = 'team_yQ9vFBIcgHxnTD3wKRpn1LJU';
  let id = 'dpl_32EaFMNDPd97RAq3uZXYMSm49oya';
  const get = async route => {
    const r=await fetch('https://api.vercel.com'+route+(route.includes('?')?'&':'?')+'teamId='+team,{headers:{Authorization:'Bearer '+token},signal:AbortSignal.timeout(20000)});
    if(!r.ok) throw new Error('Vercel source read: '+r.status+' '+(await r.text()).slice(0,300));
    return r.json();
  };
  if (process.argv.includes('--live')) {
    const latest=await get('/v13/deployments/basaya-notify-api.vercel.app');
    id=latest.id;
    console.log(JSON.stringify({liveDeployment:id,status:latest.readyState,url:latest.url}));
  }
  const tree=await get(`/v6/deployments/${id}/files`);
  const files=[];
  const walk=(nodes,prefix='')=>{for(const n of nodes){const name=prefix+n.name;if(n.children)walk(n.children,name+'/');else files.push({...n,path:name})}};
  walk(tree);
  console.log(JSON.stringify({sourceFiles:files.map(n=>n.path).filter(p=>p.startsWith('src/'))}));
  const file=files.find(n=>n.path==='lib/assessmentSpeech.js'||n.path.endsWith('/lib/assessmentSpeech.js'));
  if(!file){console.log(JSON.stringify({files:files.map(n=>n.path).filter(p=>/assessment|speech/i.test(p))}));return;}
  const result=await get(`/v8/deployments/${id}/files/${file.uid}?path=${encodeURIComponent(file.path)}`);
  const source = result.encoding==='base64'||/^[A-Za-z0-9+/\r\n]+={0,2}$/.test(result.data)?Buffer.from(result.data,'base64').toString('utf8'):result.data;
  if(typeof source!=='string') throw new Error('Unexpected deployment source format: '+JSON.stringify(Object.keys(result)));
  const current=fs.readFileSync('C:/xampp/htdocs/BaSaya-web/notify-api/lib/assessmentSpeech.js','utf8');
  console.log(JSON.stringify({deployedFile:file.path,forwardsOrigin:/Origin\s*:/.test(source),localForwardsOrigin:/Origin\s*:/.test(current),sameAsLocal:source.replace(/\r\n/g,'\n')===current.replace(/\r\n/g,'\n')}));
  console.log(JSON.stringify({keys:Object.keys(result),prefix:source.slice(0,160)}));
  const start=source.indexOf('async function recognizeAudio');
  if(start>=0)console.log(source.slice(start));
  if(process.argv.includes('--live')) {
    const assert=require('node:assert/strict');
    const staged=fs.readFileSync(path.resolve(__dirname,'../speech-api-fix/lib/assessmentSpeech.js'),'utf8');
    assert.equal(source.replace(/\r\n/g,'\n'),staged.replace(/\r\n/g,'\n'),'Production must contain the exact approved speech fix');
    const options=await fetch('https://basaya-notify-api.vercel.app/api/assessment',{method:'OPTIONS',headers:{Origin:'http://localhost','Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'authorization,content-type'},signal:AbortSignal.timeout(15000)});
    const denied=await fetch('https://basaya-notify-api.vercel.app/api/assessment',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(15000)});
    assert.equal(options.status,204);assert.equal(options.headers.get('Access-Control-Allow-Origin'),'http://localhost');assert.equal(denied.status,401);
    console.log(JSON.stringify({verification:'PASS',approvedSourceMatches:true,preflight:options.status,unauthenticatedRequest:denied.status}));
  }
  if(process.argv.includes('--prepare')) {
    const dir=path.resolve(__dirname,'../speech-api-fix');
    const sources=files.filter(n=>n.path.startsWith('src/') && /\.(js|json)$/.test(n.path) && !n.path.includes('/node_modules/') && !n.path.includes('/.vercel/'));
    await Promise.all(sources.map(async n=>{
      const found=await get(`/v8/deployments/${id}/files/${n.uid}?path=${encodeURIComponent(n.path)}`);
      let text=Buffer.from(found.data,'base64').toString('utf8');
      const relative=n.path.slice(4);
      if(relative==='lib/assessmentSpeech.js') {
        const old='method: "POST", headers: { "Content-Type": "audio/wav", Authorization: authorization },';
        if(!text.includes(old))throw new Error('Production speech source changed');
        text=text.replace(old,'method: "POST", headers: { "Content-Type": "audio/wav", Authorization: authorization,\n        Origin: process.env.STT_ALLOWED_ORIGIN || "https://basaya.onrender.com" },');
      }
      const target=path.resolve(dir,relative);
      if(!target.startsWith(dir+path.sep))throw new Error('Invalid source path');
      fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,text);
    }));
    fs.mkdirSync(path.join(dir,'.vercel'),{recursive:true});
    fs.copyFileSync('C:/xampp/htdocs/BaSaya-web/notify-api/.vercel/project.json',path.join(dir,'.vercel/project.json'));
    console.log(JSON.stringify({prepared:dir,files:sources.length,change:'Only add the allowed Origin header to speech forwarding'}));
  }
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
