import {resolve} from 'node:path';
import {loadEnv,configuredPath,runtimeDirectory} from '../src/config';
import {stageUpdate} from '../src/stage-update';
const args=process.argv.slice(2);
if(args.includes('--help')||!args.length){console.log('Usage: bun run scripts/stage-update.ts --apk PATH --baseline-apk PATH --installed-proof PATH [--build-tools DIR] [--java-home DIR]\nInstalled proof JSON: {packageName:"works.tycho.frame",versionCode:NUMBER,sha256:"BASELINE_APK_SHA256",verifiedAt:"ISO_TIMESTAMP"}. Obtain from verified USB installed APK/version evidence; do not guess.\nOnly stages a newer, same-package, same-certificate APK verified for API25. Publication is immediate; use only when update deployment is authorized.');}
else{
 try{
  const values=new Map<string,string>();const allowed=['--apk','--baseline-apk','--installed-proof','--build-tools','--java-home'];
  for(let i=0;i<args.length;i+=2){if(!allowed.includes(args[i])||!args[i+1]||args[i+1].startsWith('--')||values.has(args[i]))throw new Error('Invalid CLI options');values.set(args[i],args[i+1]);}
  const env=await loadEnv();
  if(!values.has('--apk'))throw new Error('Missing required proof or APK option');
  for(const [flag,key] of [['--baseline-apk','FRAME_BASELINE_APK'],['--installed-proof','FRAME_INSTALLED_PROOF']] as const)if(!values.has(flag)&&env[key])values.set(flag,configuredPath(env[key],''));
  for(const name of allowed.slice(0,3))if(!values.has(name))throw new Error('Missing required proof or APK option');
  const update=await stageUpdate({apk:values.get('--apk')!,baselineApk:values.get('--baseline-apk')!,installedProof:values.get('--installed-proof')!,dir:await runtimeDirectory(),buildTools:configuredPath(values.get('--build-tools')??env.ANDROID_BUILD_TOOLS,`${env.ANDROID_SDK_ROOT??'android/.tools/sdk'}/build-tools/34.0.0`),javaHome:configuredPath(values.get('--java-home')??env.JAVA_HOME,'android/.tools/jdk/Contents/Home')});
  console.log(JSON.stringify({staged:true,versionCode:update.versionCode,size:update.size}));
 }catch(error){
  const safeMessages=['Invalid CLI options','Missing required proof or APK option','APK size outside allowed limit','APK changed during read','Official Android APK verification failed','Invalid APK metadata or signer','Invalid APK identity','Only the frame app can be staged','Installed proof does not match baseline APK','APK signer does not match installed app','APK does not support this Android frame','APK does not support this ARM32 frame','Invalid APK native ABI metadata','Update version must be newer than installed and staged versions','Invalid app update metadata','Sync already running or lock unavailable'];
  console.error(error instanceof Error&&safeMessages.includes(error.message)?error.message:'Update staging failed; inspect APK, installed proof, and local tool paths. Previous update retained.');process.exitCode=1;
 }
}
