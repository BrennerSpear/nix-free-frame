import {mkdir,open,rename,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {validVersion} from './app-updates';

const booleans=['resumed','home','owner','runCommandGranted','termuxExempt','nightEnabled','nightActive'] as const;
const hashes=['apkSha256','settingsSha256','manifestSha256'] as const;
const numbers=['versionCode','cacheCount','nightStart','nightEnd'] as const;
const directKeys=['mode','directLastSuccess','directLastAttempt','directSyncState','directFailureCode','directPhotoCount','directDatedCount','imageChecksPassed','directManifestSha256','configRevision','syncHour','syncMinute','syncTimezone'] as const;
const powerKeys=['powerInteractive','powerWakeVerified','powerForceLockGranted','powerKeyguardSecure','powerNightOffEnabled','powerDisplayState','powerNextWakeAt','powerLastSleepAt','powerLastWakeAt','powerWakeCount','powerPresentationCount','powerScreenTimeout','powerStayOnPlugged','powerSleepCode'] as const;
const verificationKeys=['verificationId','verificationAction','verificationState','verificationCode','verificationStartedAt','verificationCompletedAt','verificationWifiState','verificationRestoreAt','verificationWifiOffAt','verificationWifiOnAt','verificationOfflinePresentations','verificationOfflineBacklight'] as const;
export function validateFrameEvidence(value:any){
 const keys:string[]=[...(value&&Object.hasOwn(value,'powerInteractive')?powerKeys:[]),...(value&&Object.hasOwn(value,'verificationId')?verificationKeys:[]),...booleans,...hashes,...numbers,...(value&&Object.hasOwn(value,'mode')?directKeys:[]),...(value&&Object.hasOwn(value,'mode')&&Object.hasOwn(value,'directDownloadedCount')?['directDownloadedCount']:[]),...(value&&Object.hasOwn(value,'mode')&&Object.hasOwn(value,'directAttemptRevision')?['directAttemptRevision','directSuccessRevision']:[])];
 if(!value||Array.isArray(value)||typeof value!=='object'||Object.keys(value).length!==keys.length||Object.keys(value).some(key=>!keys.includes(key as any)))throw new Error('Invalid frame evidence');
 if(booleans.some(key=>typeof value[key]!=='boolean')||hashes.some(key=>typeof value[key]!=='string'||!/^[a-f0-9]{64}$/.test(value[key])))throw new Error('Invalid frame evidence');
 if(!validVersion(value.versionCode)||!Number.isInteger(value.cacheCount)||value.cacheCount<0||value.cacheCount>5000||['nightStart','nightEnd'].some(key=>!Number.isInteger(value[key])||value[key]<0||value[key]>23))throw new Error('Invalid frame evidence');
 if(Object.hasOwn(value,'mode')){
  if(typeof value.imageChecksPassed!=='boolean')throw new Error('Invalid frame evidence');
  if(!['host','direct'].includes(value.mode)||!['idle','running','success','failed'].includes(value.directSyncState)||typeof value.directFailureCode!=='string'||!/^$|^(enumeration|download|normalize|cache|config|sync)$|^(enumeration|download)_(tls|timeout|http|limit|redirect|network)$/.test(value.directFailureCode)||typeof value.configRevision!=='string'||!/^([A-Za-z0-9_-]{1,64})?$/.test(value.configRevision)||typeof value.directManifestSha256!=='string'||!/^[a-f0-9]{64}$/.test(value.directManifestSha256))throw new Error('Invalid frame evidence');
  if(Object.hasOwn(value,'directDownloadedCount')&&(!Number.isInteger(value.directDownloadedCount)||value.directDownloadedCount<0||value.directDownloadedCount>5000))throw new Error('Invalid frame evidence');
  if(Object.hasOwn(value,'directAttemptRevision')&&['directAttemptRevision','directSuccessRevision'].some(key=>typeof value[key]!=='string'||!/^([A-Za-z0-9_-]{1,64})?$/.test(value[key])))throw new Error('Invalid frame evidence');
  if(['directLastSuccess','directLastAttempt','directPhotoCount','directDatedCount','syncHour','syncMinute'].some(key=>!Number.isSafeInteger(value[key])||value[key]<0)||value.directDatedCount>value.directPhotoCount||value.directPhotoCount>5000||value.syncHour>23||value.syncMinute>59||typeof value.syncTimezone!=='string'||value.syncTimezone.length>100)throw new Error('Invalid frame evidence');
  try{new Intl.DateTimeFormat('en',{timeZone:value.syncTimezone});}catch{throw new Error('Invalid frame evidence');}
 }
 if(Object.hasOwn(value,'powerInteractive')){
  if(['powerInteractive','powerWakeVerified','powerForceLockGranted','powerKeyguardSecure','powerNightOffEnabled'].some(key=>typeof value[key]!=='boolean')||!['unknown','off','on','doze'].includes(value.powerDisplayState)||!['none','missing_policy','secure_keyguard','alarm','locked','timeout','unsupported'].includes(value.powerSleepCode))throw new Error('Invalid power evidence');
  if(['powerNextWakeAt','powerLastSleepAt','powerLastWakeAt','powerWakeCount','powerPresentationCount'].some(key=>!Number.isSafeInteger(value[key])||value[key]<0||value[key]>9999999999999)||!Number.isInteger(value.powerScreenTimeout)||value.powerScreenTimeout< -1||value.powerScreenTimeout>2147483647||!Number.isInteger(value.powerStayOnPlugged)||value.powerStayOnPlugged< -1||value.powerStayOnPlugged>7)throw new Error('Invalid power evidence');
 }
 if(Object.hasOwn(value,'verificationId')){
  if(typeof value.verificationId!=='string'||!/^([A-Za-z0-9_-]{1,64})?$/.test(value.verificationId)||!['none','alarm_probe','power_cycle','wifi_cycle','reboot','day_preview'].includes(value.verificationAction)||!['idle','started','complete','failed'].includes(value.verificationState)||!['none','permission','alarm','secure_keyguard','policy','network','unsupported','internal'].includes(value.verificationCode)||!['unknown','off','on','changing'].includes(value.verificationWifiState)||['verificationStartedAt','verificationCompletedAt','verificationRestoreAt','verificationWifiOffAt','verificationWifiOnAt'].some(key=>!Number.isSafeInteger(value[key])||value[key]<0||value[key]>9999999999999))throw new Error('Invalid verification evidence');
  if(!Number.isInteger(value.verificationOfflinePresentations)||value.verificationOfflinePresentations<0||value.verificationOfflinePresentations>10000||!Number.isInteger(value.verificationOfflineBacklight)||value.verificationOfflineBacklight< -1||value.verificationOfflineBacklight>255)throw new Error('Invalid verification evidence');
 }
 return Object.fromEntries(keys.map(key=>[key,value[key]]));
}
export async function frameEvidenceRequest(request:Request,dir:string):Promise<Response|null>{
 if(new URL(request.url).pathname!=='/frame-status')return null;
 if(request.method!=='POST')return new Response('Method not allowed',{status:405});
 let value;
 try{
  if(request.headers.get('content-type')?.split(';')[0].trim()!=='application/json'||!request.body)throw new Error();
  const reader=request.body.getReader();let size=0;const parts:Uint8Array[]=[];
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>4096)throw new Error();parts.push(value);}}finally{await reader.cancel();}
  const bytes=new Uint8Array(size);let at=0;for(const part of parts){bytes.set(part,at);at+=part.length;}
  value=validateFrameEvidence(JSON.parse(new TextDecoder().decode(bytes)));
 }catch{return new Response('Invalid frame evidence',{status:400});}
 await mkdir(dir,{recursive:true,mode:0o700});
 const temp=join(dir,`frame-status-${crypto.randomUUID()}.tmp`);
 try{const file=await open(temp,'wx',0o600);try{await file.writeFile(JSON.stringify({...value,receivedAt:new Date().toISOString()}));}finally{await file.close();}await rename(temp,join(dir,'frame-status.json'));}
 finally{await rm(temp,{force:true});}
 return new Response(null,{status:204});
}
