import {mkdir,open,rename,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {validVersion} from './app-updates';

const booleans=['resumed','home','owner','runCommandGranted','termuxExempt','nightEnabled','nightActive'] as const;
const hashes=['apkSha256','settingsSha256','manifestSha256'] as const;
const numbers=['versionCode','cacheCount','nightStart','nightEnd'] as const;
export function validateFrameEvidence(value:any){
 const keys=[...booleans,...hashes,...numbers];
 if(!value||Array.isArray(value)||typeof value!=='object'||Object.keys(value).length!==keys.length||Object.keys(value).some(key=>!keys.includes(key as any)))throw new Error('Invalid frame evidence');
 if(booleans.some(key=>typeof value[key]!=='boolean')||hashes.some(key=>typeof value[key]!=='string'||!/^[a-f0-9]{64}$/.test(value[key])))throw new Error('Invalid frame evidence');
 if(!validVersion(value.versionCode)||!Number.isInteger(value.cacheCount)||value.cacheCount<0||value.cacheCount>5000||['nightStart','nightEnd'].some(key=>!Number.isInteger(value[key])||value[key]<0||value[key]>23))throw new Error('Invalid frame evidence');
 return Object.fromEntries(keys.map(key=>[key,value[key]]));
}
export async function frameEvidenceRequest(request:Request,dir:string):Promise<Response|null>{
 if(new URL(request.url).pathname!=='/frame-status')return null;
 if(request.method!=='POST')return new Response('Method not allowed',{status:405});
 let value;
 try{
  if(request.headers.get('content-type')?.split(';')[0].trim()!=='application/json'||!request.body)throw new Error();
  const reader=request.body.getReader();let size=0;const parts:Uint8Array[]=[];
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>2048)throw new Error();parts.push(value);}}finally{await reader.cancel();}
  const bytes=new Uint8Array(size);let at=0;for(const part of parts){bytes.set(part,at);at+=part.length;}
  value=validateFrameEvidence(JSON.parse(new TextDecoder().decode(bytes)));
 }catch{return new Response('Invalid frame evidence',{status:400});}
 await mkdir(dir,{recursive:true,mode:0o700});
 const temp=join(dir,`frame-status-${crypto.randomUUID()}.tmp`);
 try{const file=await open(temp,'wx',0o600);try{await file.writeFile(JSON.stringify({...value,receivedAt:new Date().toISOString()}));}finally{await file.close();}await rename(temp,join(dir,'frame-status.json'));}
 finally{await rm(temp,{force:true});}
 return new Response(null,{status:204});
}
