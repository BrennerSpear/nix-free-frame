import {mkdir,open,rename,rm} from 'node:fs/promises';
import {join} from 'node:path';
export const APP_PACKAGE='works.tycho.frame';
export const MAX_APK_SIZE=20*1024*1024;
export type AppUpdate={version:1;packageName:typeof APP_PACKAGE;versionCode:number;sha256:string;size:number;url:string};
export function validVersion(value:unknown):value is number{return typeof value==='number'&&Number.isInteger(value)&&value>0&&value<=2147483647;}
export function validateUpdate(value:any):AppUpdate{
 if(value?.version!==1||value.packageName!==APP_PACKAGE||!validVersion(value.versionCode)||!Number.isInteger(value.size)||value.size<=0||value.size>MAX_APK_SIZE||!/^([a-f0-9]{64})$/.test(value.sha256)||value.url!==`/app-updates/${value.sha256}.apk`)throw new Error('Invalid app update metadata');
 return {version:1,packageName:APP_PACKAGE,versionCode:value.versionCode,sha256:value.sha256,size:value.size,url:value.url};
}
export async function readAppUpdate(dir:string):Promise<AppUpdate|null>{
 const file=Bun.file(join(dir,'app-update.json'));if(!await file.exists())return null;
 return validateUpdate(await file.json());
}
export const UPDATE_STATES=['downloaded','installing','installed','failed'] as const;
export const UPDATE_ERRORS=['download','hash','install','package','signature','version','permission'] as const;
export function validateUpdateStatus(value:any){
 if(!value||Array.isArray(value)||typeof value!=='object'||Object.keys(value).some(key=>!['versionCode','state','errorCode'].includes(key))||!validVersion(value.versionCode)||!UPDATE_STATES.includes(value.state)||value.errorCode!==undefined&&(!UPDATE_ERRORS.includes(value.errorCode)||value.state!=='failed'))throw new Error('Invalid update status');
 return {versionCode:value.versionCode as number,state:value.state as typeof UPDATE_STATES[number],...(value.errorCode?{errorCode:value.errorCode as typeof UPDATE_ERRORS[number]}:{})};
}
async function boundedJson(request:Request){
 if(request.headers.get('content-type')?.split(';')[0].trim()!=='application/json'||Number(request.headers.get('content-length'))>512||!request.body)throw new Error('Invalid status request');
 const reader=request.body.getReader();const parts:Uint8Array[]=[];let length=0;
 try{while(true){const {done,value}=await reader.read();if(done)break;length+=value.length;if(length>512)throw new Error('Status too large');parts.push(value);}}finally{await reader.cancel();}
 const bytes=new Uint8Array(length);let offset=0;for(const part of parts){bytes.set(part,offset);offset+=part.length;}return JSON.parse(new TextDecoder().decode(bytes));
}
export async function appUpdateRequest(request:Request,dir:string):Promise<Response|null>{
 const path=new URL(request.url).pathname;
 if(path==='/app-update-status'){
  if(request.method!=='POST')return new Response('Method not allowed',{status:405});
  let status;try{status=validateUpdateStatus(await boundedJson(request));}catch{return new Response('Invalid status',{status:400});}
  const temp=join(dir,`app-status-${crypto.randomUUID()}.tmp`);
  try{
   await mkdir(dir,{recursive:true,mode:0o700});const file=await open(temp,'wx',0o600);try{await file.writeFile(JSON.stringify({...status,receivedAt:new Date().toISOString()}));}finally{await file.close();}
   await rename(temp,join(dir,'app-update-status.json'));return new Response(null,{status:204});
  }finally{await rm(temp,{force:true});}
 }
 if(path==='/app-update.json'){
  if(request.method!=='GET'&&request.method!=='HEAD')return new Response('Method not allowed',{status:405});
  const update=await readAppUpdate(dir);return update?Response.json(update,{headers:{'Cache-Control':'no-store'}}):new Response('No app update',{status:404});
 }
 if(path.startsWith('/app-updates/')){
  if(request.method!=='GET'&&request.method!=='HEAD')return new Response('Method not allowed',{status:405});
  const update=await readAppUpdate(dir);if(!update||path!==update.url)return new Response('Not found',{status:404});
  const file=Bun.file(join(dir,'app-updates',`${update.sha256}.apk`));if(!await file.exists()||file.size!==update.size)return new Response('Not found',{status:404});
  return new Response(file,{headers:{'Content-Type':'application/vnd.android.package-archive','Cache-Control':'no-store','Content-Length':String(update.size)}});
 }
 return null;
}
