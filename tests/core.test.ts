import {test,expect} from 'bun:test';
import {mkdtemp,rm,readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {syncAlbum,handleRequest,normalizeImage,downloadImage} from '../src/core';
const image=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jR1kAAAAASUVORK5CYII=','base64');
const item=(uid:string)=>({uid,url:'https://lh3.googleusercontent.com/a',posterUrl:'https://lh3.googleusercontent.com/a',videoUrl:null,isVideo:false,width:1,height:1,imageUpdateDate:1,albumAddDate:1});
const config={albumUrl:'https://photos.google.com/share/test',frameToken:'test'};
test('successful additions/removals publish changed manifest; failed download and enumeration retain exact previous manifest',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'frame-test-'));try{
 await syncAlbum({...config,intervalSeconds:23},dir,{enumerate:async()=>[item('a')],download:async()=>image});
 const before=await readFile(join(dir,'manifest.json'),'utf8');expect(JSON.parse(before).intervalSeconds).toBe(23);
 await expect(syncAlbum(config,dir,{enumerate:async()=>[item('b')],download:async()=>Buffer.from('garbage')})).rejects.toThrow();
 expect(await readFile(join(dir,'manifest.json'),'utf8')).toBe(before);
 await expect(syncAlbum(config,dir,{enumerate:async()=>{throw new Error('partial pagination');}})).rejects.toThrow();
 expect(await readFile(join(dir,'manifest.json'),'utf8')).toBe(before);
 await syncAlbum(config,dir,{enumerate:async()=>[item('b')],download:async()=>image});
 const manifest=JSON.parse(await readFile(join(dir,'manifest.json'),'utf8')); expect(manifest.photos).toHaveLength(1);expect(manifest.photos[0].id).not.toBe(JSON.parse(before).photos[0].id);
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('only authenticated manifest and allowlisted image paths are served',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'frame-routes-'));try{
 await syncAlbum(config,dir,{enumerate:async()=>[item('a')],download:async()=>image});
 const req=(path:string)=>new Request(`http://localhost${path}`,{headers:{authorization:'Bearer test'}});
 expect((await handleRequest(new Request('http://localhost/manifest.json'),dir,'test')).status).toBe(401);
 for(const path of ['/config.json','/photos/%2e%2e/config.json','/photos/'+'a'.repeat(64)+'.jpg'])expect((await handleRequest(req(path),dir,'test')).status).toBe(404);
 const manifest=await (await handleRequest(req('/manifest.json'),dir,'test')).json();const response=await handleRequest(req(manifest.photos[0].url),dir,'test');expect(response.status).toBe(200);expect(response.headers.get('content-type')).toBe('image/jpeg');
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('invalid downloaded bytes fail actual image decode',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'frame-decode-'));try{await Bun.write(join(dir,'bad'),'not a photo');await expect(normalizeImage(join(dir,'bad'),join(dir,'bad.jpg'))).rejects.toThrow();}finally{await rm(dir,{recursive:true,force:true});}
});
test('downloader rejects unrelated hosts without a network request',async()=>{await expect(downloadImage('https://example.com/photo',new AbortController().signal)).rejects.toThrow('Unexpected image host');});

test('real extractor rejects malformed raw entries and retains previous manifest',async()=>{
 const {fetchImageUrls}=await import('@marcus5914/google-photos-album-image-url-fetch');
 const dir=await mkdtemp(join(tmpdir(),'frame-parser-'));const realFetch=globalThis.fetch;
 try{
  await syncAlbum(config,dir,{enumerate:async()=>[item('a')],download:async()=>image});
  const before=await readFile(join(dir,'manifest.json'),'utf8');
  const valid=['a',['https://lh3.googleusercontent.com/a',1,1],123,null,null,123];
  const html=`<script>AF_initDataCallback(${JSON.stringify({data:[null,[valid,['malformed']],null]})});</script>`;
  globalThis.fetch=(async()=>new Response(html)) as unknown as typeof fetch;
  await expect(syncAlbum(config,dir,{enumerate:fetchImageUrls,download:async()=>image})).rejects.toThrow('Malformed album media entry');
  expect(await readFile(join(dir,'manifest.json'),'utf8')).toBe(before);
  globalThis.fetch=(async()=>new Response(`<script>AF_initDataCallback(${JSON.stringify({data:[null,[valid],42]})});</script>`)) as unknown as typeof fetch;
  await expect(fetchImageUrls(config.albumUrl)).rejects.toThrow('Malformed pagination token');
 }finally{globalThis.fetch=realFetch;await rm(dir,{recursive:true,force:true});}
});
test('stale lock files do not block; current live OS lock is preserved',async()=>{
 const {acquireLock}=await import('../src/core');const dir=await mkdtemp(join(tmpdir(),'frame-lock-'));
 try{await Bun.write(join(dir,'sync.flock'),'old stale file');const release=await acquireLock(dir);
 await expect(acquireLock(dir)).rejects.toThrow('Sync already running');expect(await Bun.file(join(dir,'sync.flock')).exists()).toBe(true);await release();const recovered=await acquireLock(dir);await recovered();
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('kernel lock releases after holder crashes',async()=>{
 const {acquireLock}=await import('../src/core');const dir=await mkdtemp(join(tmpdir(),'frame-crash-'));
 const modulePath=new URL('../src/core.ts',import.meta.url).pathname;
 const holder=Bun.spawn([process.execPath,'-e',`import {acquireLock} from ${JSON.stringify(modulePath)};await acquireLock(${JSON.stringify(dir)});console.log('locked');await new Promise(r=>setTimeout(r,60000));`],{stdout:'pipe',stderr:'pipe'});
 try{
  const reader=holder.stdout.getReader();const ready=await reader.read();reader.releaseLock();expect(new TextDecoder().decode(ready.value)).toContain('locked');
  holder.kill('SIGKILL');await holder.exited;
  let release:undefined|(()=>Promise<void>);
  for(let attempt=0;attempt<20;attempt++){try{release=await acquireLock(dir);break;}catch{await Bun.sleep(10);}}
  expect(release).toBeDefined();await release!();
 }finally{holder.kill();await rm(dir,{recursive:true,force:true});}
});
