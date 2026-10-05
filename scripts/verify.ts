import {readConfig,runtimeDirectory} from '../src/config';
import {readFile,stat} from 'node:fs/promises';
import {join} from 'node:path';
import {validateFrameEvidence} from '../src/frame-evidence';
import {createHash} from 'node:crypto';
const args=process.argv.slice(2);
if(args.includes('--help')){console.log('Usage: bun run scripts/verify.ts [--expect-version NUMBER] [--apk PATH] [--previous-receipt PATH] [--expect-mode host|direct]\nRead-only authenticated host health/cache and private installation receipt. Receipt reports app resume, hashes and retained roles; it is not a visual panel check or a privileged USB inspection. No values, URLs, tokens or photo IDs are printed.');process.exit(0);}
try{
 let expected:number|undefined;const options=new Map<string,string>();
 for(let i=0;i<args.length;i+=2){if(!['--expect-version','--apk','--previous-receipt','--expect-mode'].includes(args[i])||!args[i+1]||options.has(args[i]))throw new Error('Invalid options');options.set(args[i],args[i+1]);}
 if(options.has('--expect-version')){if(!/^\d+$/.test(options.get('--expect-version')!))throw new Error('Invalid options');expected=Number(options.get('--expect-version'));}
 if(options.has('--expect-mode')&&!['host','direct'].includes(options.get('--expect-mode')!))throw new Error('Invalid mode');
 const config=await readConfig(),dir=await runtimeDirectory();
 const base=`http://${config.bindAddress}:${config.port}`;
 async function request(path:string,authenticated=true){const response=await fetch(base+path,{headers:authenticated?{authorization:`Bearer ${config.frameToken}`}:{},signal:AbortSignal.timeout(5000),redirect:'error'});return {status:response.status,body:response.ok?await response.json():null};}
 const health=await request('/health');const status=await request('/status');const unauthorized=await request('/health',false);
 if(health.status!==200||status.status!==200||unauthorized.status!==401)throw new Error('Host authentication/health gate failed');
 let receipt:any=null;try{receipt=JSON.parse(await readFile(join(dir,'frame-status.json'),'utf8'));}catch(error:any){if(error.code!=='ENOENT')throw error;}
 if(receipt){const {receivedAt,...fields}=receipt;validateFrameEvidence(fields);if(typeof receivedAt!=='string')throw new Error('Invalid timestamp');}
 if(expected!==undefined&&!options.has('--apk'))throw new Error('--expect-version requires --apk for exact installed hash');
 const receiptSafe=receipt?{versionCode:receipt.versionCode,receivedAt:receipt.receivedAt,cacheCount:receipt.cacheCount,resumed:receipt.resumed,home:receipt.home,owner:receipt.owner,runCommandGranted:receipt.runCommandGranted,termuxExempt:receipt.termuxExempt,nightEnabled:receipt.nightEnabled,nightActive:receipt.nightActive,mode:receipt.mode??'host',directSyncState:receipt.directSyncState??null,directPhotoCount:receipt.directPhotoCount??0,directDatedCount:receipt.directDatedCount??0,imageChecksPassed:receipt.imageChecksPassed??false,directLastSuccess:receipt.directLastSuccess??0,privateFileMode:((await stat(join(dir,'frame-status.json'))).mode&0o777)===0o600}:null;
 console.log(JSON.stringify({health:'pass',unauthenticated:'rejected',photos:status.body.photos,syncedAt:status.body.syncedAt,frameReceipt:receiptSafe}));
 if(options.has('--apk')){const hash=createHash('sha256').update(await readFile(options.get('--apk')!)).digest('hex');if(!receipt||receipt.apkSha256!==hash)throw new Error('Installed APK hash gate failed');}
 if(options.has('--previous-receipt')){const previous=JSON.parse(await readFile(options.get('--previous-receipt')!,'utf8'));if(!receipt||receipt.settingsSha256!==previous.settingsSha256||(options.get('--expect-mode')!=='direct'&&receipt.manifestSha256!==previous.manifestSha256))throw new Error('Retained state hash gate failed');}
 if(options.has('--expect-mode')&&(!receipt||(receipt.mode??'host')!==options.get('--expect-mode')))throw new Error('Mode gate failed');
 if(options.get('--expect-mode')==='direct'&&(!receipt||receipt.directSyncState!=='success'||receipt.imageChecksPassed!==true||receipt.directLastSuccess<=0||receipt.directPhotoCount!==receipt.cacheCount||receipt.directPhotoCount<1||receipt.directSuccessRevision!==receipt.configRevision))throw new Error('Direct sync gate failed');
 if(expected!==undefined&&(!receipt||receipt.versionCode!==expected||!receipt.resumed||!receipt.home||!receipt.owner||!receipt.runCommandGranted||!receipt.termuxExempt||((receipt.mode??'host')==='host'&&receipt.cacheCount!==status.body.photos)||!receiptSafe.privateFileMode||!Number.isFinite(Date.parse(receipt.receivedAt))||Date.parse(receipt.receivedAt)>Date.now()+60000||Date.now()-Date.parse(receipt.receivedAt)>10*60*1000))throw new Error('Expected installed/resumed frame evidence gate not met');
}catch{console.error('Verification failed or requested frame evidence is missing/stale. Inspect local private state; no device changes performed.');process.exitCode=1;}
