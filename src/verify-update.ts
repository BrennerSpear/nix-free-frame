// Manual, parent-coordinated verification. Never edits the Google album.
import {mkdir,readFile,rename,rm,open} from 'node:fs/promises';
import {join} from 'node:path';
import {runtime,readManifest,acquireLock,type Manifest} from './core';
const command=process.argv[2];
const directory=join(runtime,'verification');
const backup=join(directory,'manifest-original.json');
async function original():Promise<Manifest>{return JSON.parse(await readFile(backup,'utf8'));}
async function publish(manifest:Manifest){
  const path=join(runtime,`verification-${crypto.randomUUID()}.tmp`);
  try{await Bun.write(path,JSON.stringify(manifest));await rename(path,join(runtime,'manifest.json'));}
  finally{await rm(path,{force:true});}
}
if(command==='--help'||!command){
 console.log('Usage: bun run src/verify-update.ts omit|status|restore\nManual verification only. omit saves private rollback and omits first photo. status reads counts. restore publishes saved original. No Google writes.');
}else if(command==='status'){
 const saved=await original();const current=await readManifest();
 console.log(JSON.stringify({originalPhotos:saved.photos.length,currentPhotos:current?.photos.length??null,firstPhotoPresent:current?.photos.some(photo=>photo.id===saved.photos[0].id)??false}));
}else if(command==='omit'||command==='restore'){
 const release=await acquireLock(runtime);
 try{
  if(command==='omit'){
   const current=await readManifest();if(!current||current.photos.length<2)throw new Error('At least two cached photos required');
   await mkdir(directory,{mode:0o700,recursive:true});
   const handle=await open(backup,'wx',0o600);try{await handle.writeFile(JSON.stringify(current));}finally{await handle.close();}
   await publish({...current,photos:current.photos.slice(1)});
   console.log(JSON.stringify({phase:'omitted',originalPhotos:current.photos.length,currentPhotos:current.photos.length-1}));
  }else{
   const saved=await original();
   for(const photo of saved.photos)if(!await Bun.file(join(runtime,'photos',`${photo.sha256}.jpg`)).exists())throw new Error('Original cache missing; restore cannot publish');
   await publish(saved);console.log(JSON.stringify({phase:'restored',photos:saved.photos.length}));
  }
 }catch{console.error('Verification phase failed; no private details logged. Inspect local verification backup before retry.');process.exitCode=1;}
 finally{await release();}
}else{console.error('Unknown phase. Use --help.');process.exitCode=1;}
