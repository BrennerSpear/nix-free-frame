import { fetchImageUrls, type ImageInfo } from '@marcus5914/google-photos-album-image-url-fetch';
import { mkdir, readFile, rename, rm, stat } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { capturedMonthFromJpeg } from './captured-month';
import { appUpdateRequest } from './app-updates';
import {frameEvidenceRequest} from './frame-evidence';

export {runtime, readConfig, type Config} from './config';
import {runtime, type Config} from './config';
export type Photo = { id: string; revision: number; sha256: string; url: string; width: number; height: number; capturedMonth?: string };
export type Manifest = { version: 1; intervalSeconds: number; syncedAt: string; photos: Photo[] };
export async function readManifest(dir = runtime): Promise<Manifest | null> {
  try { return JSON.parse(await readFile(join(dir, 'manifest.json'), 'utf8')); }
  catch (error: any) { if (error.code === 'ENOENT') return null; throw error; }
}
async function runSips(args:string[],signal:AbortSignal):Promise<string>{
  signal.throwIfAborted();
  const proc=Bun.spawn(['/usr/bin/sips',...args],{stdout:'pipe',stderr:'pipe'});
  const stop=()=>proc.kill();signal.addEventListener('abort',stop,{once:true});
  const deadline=setTimeout(stop,10000);
  try {
    const [exit,stdout]=await Promise.all([proc.exited,new Response(proc.stdout).text(),new Response(proc.stderr).text()]);
    signal.throwIfAborted();if(exit!==0)throw new Error('Image decode failed');return stdout;
  }finally{clearTimeout(deadline);signal.removeEventListener('abort',stop);}
}
export async function normalizeImage(input:string,output:string,signal=AbortSignal.timeout(20000)):Promise<{width:number;height:number}>{
  await runSips(['-s','format','jpeg','-Z','1920',input,'--out',output],signal);
  const text=await runSips(['-g','pixelWidth','-g','pixelHeight',output],signal);
  const width=Number(text.match(/pixelWidth:\s+(\d+)/)?.[1]);const height=Number(text.match(/pixelHeight:\s+(\d+)/)?.[1]);
  if(!width||!height||(await stat(output)).size<100)throw new Error('Invalid decoded image');return {width,height};
}
export async function acquireLock(dir:string):Promise<()=>Promise<void>>{
  // macOS BSD file lock: kernel releases it on process exit, without stale-PID races.
  const proc=Bun.spawn(['/usr/bin/lockf','-k','-s','-t','0',join(dir,'sync.flock'),'/bin/cat'],{stdin:'pipe',stdout:'pipe',stderr:'pipe'});
  const errors=new Response(proc.stderr).text();const reader=proc.stdout.getReader();
  proc.stdin.write('locked\n');proc.stdin.flush();
  const deadline=setTimeout(()=>proc.kill(),5000);
  try{const result=await reader.read();if(result.done)throw new Error('Sync already running or lock unavailable');}
  catch(error){proc.kill();await proc.exited;await errors;throw error;}
  finally{clearTimeout(deadline);reader.releaseLock();}
  return async()=>{proc.stdin.end();await proc.exited;await errors;};
}
export async function downloadImage(url: string, signal: AbortSignal): Promise<Uint8Array> {
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || !parsed.hostname.endsWith('.googleusercontent.com')) throw new Error('Unexpected image host');
  const response = await fetch(url, {signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]), redirect:'error'});
  if (!response.ok || !response.headers.get('content-type')?.startsWith('image/')) throw new Error('Photo download failed');
  if (Number(response.headers.get('content-length')) > 20_000_000) throw new Error('Photo too large');
  const reader = response.body!.getReader(); const parts: Uint8Array[] = []; let length = 0;
  try { while (true) { const {done,value} = await reader.read(); if (done) break; length += value.length; if (length > 20_000_000) throw new Error('Photo too large'); parts.push(value); } }
  finally { await reader.cancel(); }
  const bytes = new Uint8Array(length); let offset=0; for(const part of parts){bytes.set(part,offset);offset+=part.length;} return bytes;
}
export async function syncAlbum(config: Config, dir = runtime, deps: {
  enumerate?: (url:string, signal:AbortSignal)=>Promise<ImageInfo[]|null>;
  download?: typeof downloadImage;
} = {}): Promise<{photos:number;downloaded:number}> {
  await mkdir(dir,{recursive:true,mode:0o700});
  const releaseLock=await acquireLock(dir);
  const stage = join(dir, `stage-${crypto.randomUUID()}`);
  try {
    await mkdir(stage,{mode:0o700}); await mkdir(join(dir,'photos'),{mode:0o700,recursive:true});
    const signal = AbortSignal.timeout(20*60*1000);
    const items = await (deps.enumerate ?? fetchImageUrls)(config.albumUrl, signal);
    if (!items?.length) throw new Error('Album empty or unreadable; previous cache retained');
    const images = items.filter(item=>!item.isVideo);
    if (!images.length) throw new Error('No still photos; previous cache retained');
    const previous = await readManifest(dir); const photos:Photo[]=[]; let downloaded=0;
    const seen = new Set<string>();
    for(const item of images){
      if (signal.aborted) throw new Error('Sync timed out');
      if (!item.uid || !Number.isFinite(item.imageUpdateDate) || !Number.isFinite(item.width) || !Number.isFinite(item.height)) throw new Error('Invalid album metadata');
      const id=createHash('sha256').update(item.uid).digest('hex');
      if(seen.has(id)) continue; seen.add(id);
      const old=previous?.photos.find(photo=>photo.id===id && photo.revision===item.imageUpdateDate);
      if(old && await Bun.file(join(dir,'photos',`${old.sha256}.jpg`)).exists()){
        const capturedMonth=capturedMonthFromJpeg(await Bun.file(join(dir,'photos',`${old.sha256}.jpg`)).bytes());
        const {capturedMonth:previousMonth,...cached}=old;
        photos.push({...cached,...(capturedMonth?{capturedMonth}:{})});continue;
      }
      const source=join(stage,`${id}.input`), output=join(stage,`${id}.jpg`);
      await Bun.write(source, await (deps.download ?? downloadImage)(`${item.posterUrl}=w1920-h1920`,signal));
      const size=await normalizeImage(source,output,signal);
      const bytes=await Bun.file(output).bytes();
      const capturedMonth=capturedMonthFromJpeg(bytes);
      const hash=createHash('sha256').update(bytes).digest('hex');
      await rename(output,join(dir,'photos',`${hash}.jpg`));
      photos.push({id,revision:item.imageUpdateDate,sha256:hash,url:`/photos/${hash}.jpg`,...size,...(capturedMonth?{capturedMonth}:{})}); downloaded++;
    }
    const manifest:Manifest={version:1,intervalSeconds:config.intervalSeconds??15,syncedAt:new Date().toISOString(),photos};
    const temp=join(stage,'manifest.json'); await Bun.write(temp,JSON.stringify(manifest));
    await rename(temp,join(dir,'manifest.json'));
    return {photos:photos.length,downloaded};
  } finally {await rm(stage,{recursive:true,force:true});await releaseLock();}
}
export async function handleRequest(request:Request, dir=runtime, token=""):Promise<Response>{
  if (!token || request.headers.get('authorization') !== `Bearer ${token}`) return new Response('Unauthorized', {status:401});
  const evidence=await frameEvidenceRequest(request,dir);if(evidence)return evidence;
  const updateResponse=await appUpdateRequest(request,dir);if(updateResponse)return updateResponse;
  if(request.method!=='GET' && request.method!=='HEAD') return new Response('Method not allowed',{status:405});
  const path=new URL(request.url).pathname;
  const headers={'Cache-Control':'no-store'};
  if(path==='/health') return Response.json({ok:true});
  if(path==='/manifest.json') {const manifest=await readManifest(dir);return manifest ? Response.json(manifest,{headers}) : new Response('No sync yet',{status:503});}
  if(path==='/status') {const manifest=await readManifest(dir);return Response.json({syncedAt:manifest?.syncedAt??null,photos:manifest?.photos.length??0},{headers});}
  if(/^\/photos\/[a-f0-9]{64}\.jpg$/.test(path)){
    const manifest=await readManifest(dir); if(!manifest?.photos.some(photo=>photo.url===path))return new Response('Not found',{status:404});
    const file=Bun.file(join(dir,path.slice(1)));if(!await file.exists())return new Response('Not found',{status:404});
    return new Response(file,{headers:{'Content-Type':'image/jpeg','Cache-Control':'public, max-age=86400'}});
  }
  return new Response('Not found',{status:404});
}
