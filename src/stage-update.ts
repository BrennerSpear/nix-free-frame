import {mkdir,open,rename,rm,stat} from 'node:fs/promises';
import {join,resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {acquireLock} from './core';
import {APP_PACKAGE,MAX_APK_SIZE,validVersion,readAppUpdate,type AppUpdate} from './app-updates';
export type ApkInfo={packageName:string;versionCode:number;minSdk:number;signerSha256:string;nativeAbis:string[]};
export type InstalledProof={packageName:string;versionCode:number;sha256:string;verifiedAt:string};
export function parseApkInfo(badging:string,certificates:string):ApkInfo{
 const packageLine=/^package: name='([^']+)' versionCode='(\d+)'/m.exec(badging);
 const sdk=/^sdkVersion:'(\d+)'/m.exec(badging);
 const nativeLine=/^native-code:(.*)$/m.exec(badging);
 const nativeAbis=nativeLine?[...nativeLine[1].matchAll(/'([^']+)'/g)].map(match=>match[1]):[];
 if(nativeLine&&!nativeAbis.length)throw new Error('Invalid APK native ABI metadata');
 const certs=[...certificates.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$/gm)];
 if(!packageLine||!sdk||certs.length!==1||!validVersion(Number(packageLine[2])))throw new Error('Invalid APK metadata or signer');
 return {packageName:packageLine[1],versionCode:Number(packageLine[2]),minSdk:Number(sdk[1]),signerSha256:certs[0][1].toLowerCase(),nativeAbis};
}
export function checkStage(candidate:ApkInfo,baseline:ApkInfo,proof:InstalledProof,baselineHash:string,stagedVersion=0){
 if(!validVersion(candidate.versionCode)||!validVersion(baseline.versionCode)||!Number.isInteger(candidate.minSdk)||candidate.minSdk<1||!/^[a-f0-9]{64}$/.test(candidate.signerSha256)||!/^[a-f0-9]{64}$/.test(baseline.signerSha256))throw new Error('Invalid APK identity');
 if(candidate.packageName!==APP_PACKAGE||baseline.packageName!==APP_PACKAGE||proof?.packageName!==APP_PACKAGE)throw new Error('Only the frame app can be staged');
 if(!validVersion(proof.versionCode)||proof.versionCode!==baseline.versionCode||proof.sha256!==baselineHash||!Number.isFinite(Date.parse(proof.verifiedAt)))throw new Error('Installed proof does not match baseline APK');
 if(candidate.signerSha256!==baseline.signerSha256)throw new Error('APK signer does not match installed app');
 if(candidate.nativeAbis.length&&!candidate.nativeAbis.some(abi=>abi==='armeabi-v7a'||abi==='armeabi'))throw new Error('APK does not support this ARM32 frame');
 if(candidate.minSdk>25)throw new Error('APK does not support this Android frame');
 if(candidate.versionCode<=Math.max(proof.versionCode,stagedVersion))throw new Error('Update version must be newer than installed and staged versions');
}
export async function inspectApk(path:string,buildTools:string,javaHome:string):Promise<ApkInfo>{
 async function run(command:string[]){
  const proc=Bun.spawn(command,{stdout:'pipe',stderr:'pipe',env:{...process.env,JAVA_HOME:javaHome}});const timeout=setTimeout(()=>proc.kill(),30000);
  try{const [exit,stdout]=await Promise.all([proc.exited,new Response(proc.stdout).text(),new Response(proc.stderr).text()]);if(exit!==0)throw new Error('Official Android APK verification failed');return stdout;}finally{clearTimeout(timeout);}
 }
 const badging=await run([join(buildTools,'aapt'),'dump','badging',path]);
 const certificates=await run([join(buildTools,'apksigner'),'verify','--min-sdk-version','25','--max-sdk-version','25','--print-certs',path]);
 return parseApkInfo(badging,certificates);
}
export async function stageUpdate(options:{apk:string;baselineApk:string;installedProof:string;dir:string;buildTools:string;javaHome:string},inspect=inspectApk):Promise<AppUpdate>{
 const {dir}=options;await mkdir(dir,{recursive:true,mode:0o700});
 await mkdir(join(dir,'app-updates'),{recursive:true,mode:0o700});
 const release=await acquireLock(join(dir,'app-updates'));
 const stage=join(dir,`app-stage-${crypto.randomUUID()}`);
 try{
  await mkdir(stage,{mode:0o700});
  async function snapshot(input:string,name:string){const size=(await stat(input)).size;if(size<=0||size>MAX_APK_SIZE)throw new Error('APK size outside allowed limit');const bytes=await Bun.file(input).bytes();if(bytes.length!==size)throw new Error('APK changed during read');const path=join(stage,name);const handle=await open(path,'wx',0o600);try{await handle.writeFile(bytes);}finally{await handle.close();}return {path,bytes,sha256:createHash('sha256').update(bytes).digest('hex')};}
  const candidate=await snapshot(resolve(options.apk),'candidate.apk');const baseline=await snapshot(resolve(options.baselineApk),'baseline.apk');
  const proof=await Bun.file(options.installedProof).json();const old=await readAppUpdate(dir);
  const candidateInfo=await inspect(candidate.path,options.buildTools,options.javaHome);const baselineInfo=await inspect(baseline.path,options.buildTools,options.javaHome);
  checkStage(candidateInfo,baselineInfo,proof,baseline.sha256,old?.versionCode??0);
  const manifest:AppUpdate={version:1,packageName:APP_PACKAGE,versionCode:candidateInfo.versionCode,sha256:candidate.sha256,size:candidate.bytes.length,url:`/app-updates/${candidate.sha256}.apk`};
  await mkdir(join(dir,'app-updates'),{mode:0o700,recursive:true});await rename(candidate.path,join(dir,'app-updates',`${candidate.sha256}.apk`));
  const temp=join(stage,'app-update.json');const handle=await open(temp,'wx',0o600);try{await handle.writeFile(JSON.stringify(manifest));}finally{await handle.close();}
  await rename(temp,join(dir,'app-update.json'));return manifest;
 }finally{await rm(stage,{recursive:true,force:true});await release();}
}
