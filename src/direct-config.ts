import {readFile,stat} from 'node:fs/promises';
import {join} from 'node:path';
import {createCipheriv,createPublicKey,publicEncrypt,constants,randomBytes} from 'node:crypto';

/** Optional private provisioning capability; never infer opt-in from host album settings. */
export function validateDirectConfig(value:any){
 const allowed=['version','revision','mode','albumUrl','syncHour','syncMinute','syncTimezone','intervalSeconds','verification'];
 if(!value||Array.isArray(value)||typeof value!=='object'||Object.keys(value).some(key=>!allowed.includes(key))||value.version!==1||typeof value.revision!=='string'||!/^[A-Za-z0-9_-]{1,64}$/.test(value.revision)||!['direct','host'].includes(value.mode))throw new Error('Invalid direct configuration');
 if(!Number.isInteger(value.syncHour)||value.syncHour<0||value.syncHour>23||!Number.isInteger(value.syncMinute)||value.syncMinute<0||value.syncMinute>59||!Number.isInteger(value.intervalSeconds)||value.intervalSeconds<5||value.intervalSeconds>3600||typeof value.syncTimezone!=='string'||value.syncTimezone.length>100)throw new Error('Invalid direct configuration');
 try{new Intl.DateTimeFormat('en',{timeZone:value.syncTimezone});}catch{throw new Error('Invalid direct configuration');}
 if(value.mode==='direct'){
  if(typeof value.albumUrl!=='string'||value.albumUrl.length>4096)throw new Error('Invalid direct configuration');
  let url:URL;try{url=new URL(value.albumUrl);}catch{throw new Error('Invalid direct configuration');}
  if(url.protocol!=='https:'||!['photos.app.goo.gl','photos.google.com'].includes(url.hostname)||url.username||url.password||url.port||url.hash)throw new Error('Invalid direct configuration');
 }else if(value.albumUrl!==undefined)throw new Error('Host configuration must omit album capability');
 if(value.verification!==undefined){
  const task=value.verification;
  if(!task||Array.isArray(task)||typeof task!=='object'||Object.keys(task).length!==3||Object.keys(task).some(key=>!['version','id','action'].includes(key))||task.version!==1||typeof task.id!=='string'||!/^[A-Za-z0-9_-]{1,64}$/.test(task.id)||!['alarm_probe','power_cycle','wifi_cycle','reboot','day_preview'].includes(task.action))throw new Error('Invalid verification request');
 }
 return Object.fromEntries(allowed.filter(key=>Object.hasOwn(value,key)).map(key=>[key,value[key]]));
}
export function sealDirectConfig(value:unknown,recipient:string){
 if(!/^[A-Za-z0-9+/=]{300,800}$/.test(recipient))throw new Error('Invalid recipient key');
 const publicKey=createPublicKey({key:Buffer.from(recipient,'base64'),type:'spki',format:'der'});
 if(publicKey.asymmetricKeyType!=='rsa'||publicKey.asymmetricKeyDetails?.modulusLength!==2048)throw new Error('Invalid recipient key');
 const nonce=randomBytes(12),key=randomBytes(32);
 const cipher=createCipheriv('aes-256-gcm',key,nonce);
 cipher.setAAD(Buffer.from('nix-free-frame-direct-config-v1','utf8'));
 const ciphertext=Buffer.concat([cipher.update(JSON.stringify(validateDirectConfig(value)),'utf8'),cipher.final(),cipher.getAuthTag()]);
 const wrappedKey=publicEncrypt({key:publicKey,padding:constants.RSA_PKCS1_OAEP_PADDING,oaepHash:'sha1'},key);
 return {version:1,nonce:nonce.toString('base64'),ciphertext:ciphertext.toString('base64'),wrappedKey:wrappedKey.toString('base64')};
}
export async function directConfigRequest(request:Request,dir:string):Promise<Response|null>{
 if(new URL(request.url).pathname!=='/direct-config')return null;
 const headers={'Cache-Control':'no-store','Pragma':'no-cache'};
 if(request.method!=='GET')return new Response('Method not allowed',{status:405,headers});
 try{
  const file=join(dir,'direct-config.json'),info=await stat(file);
  if((info.mode&0o777)!==0o600||info.size>16384)throw new Error();
  return Response.json(sealDirectConfig(JSON.parse(await readFile(file,'utf8')),request.headers.get('x-frame-config-key')??''),{headers});
 }catch(error:any){return new Response(error.code==='ENOENT'?'Not configured':'Invalid private configuration',{status:error.code==='ENOENT'?404:503,headers});}
}
