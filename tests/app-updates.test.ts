import {test,expect} from 'bun:test';
import {mkdtemp,rm,stat} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {createHash} from 'node:crypto';
import {handleRequest} from '../src/core';
import {APP_PACKAGE,MAX_APK_SIZE,validateUpdateStatus} from '../src/app-updates';
import {checkStage,parseApkInfo,stageUpdate,type ApkInfo} from '../src/stage-update';
const signer='a'.repeat(64),base:ApkInfo={packageName:APP_PACKAGE,versionCode:3,minSdk:21,signerSha256:signer,nativeAbis:[]};
const proof={packageName:APP_PACKAGE,versionCode:3,sha256:'b'.repeat(64),verifiedAt:'2026-10-03T00:00:00Z'};
const candidate={...base,versionCode:4};
test('stage rejects wrong package, signer, incompatible SDK, downgrade and unverified baseline',()=>{
 expect(()=>checkStage(candidate,base,proof,proof.sha256)).not.toThrow();
 expect(()=>checkStage({...candidate,nativeAbis:['armeabi-v7a','arm64-v8a']},base,proof,proof.sha256)).not.toThrow();
 const parsed=parseApkInfo("package: name='works.tycho.frame' versionCode='4'\nsdkVersion:'21'\nnative-code: 'arm64-v8a'",`Signer #1 certificate SHA-256 digest: ${signer}`);expect(parsed.nativeAbis).toEqual(['arm64-v8a']);expect(()=>checkStage(parsed,base,proof,proof.sha256)).toThrow('ARM32');
 for(const change of [{packageName:'someone.else'},{signerSha256:'c'.repeat(64)},{minSdk:26},{versionCode:3},{nativeAbis:['arm64-v8a']}])expect(()=>checkStage({...candidate,...change},base,proof,proof.sha256)).toThrow();
 expect(()=>checkStage(candidate,base,{...proof,sha256:'d'.repeat(64)},proof.sha256)).toThrow();
 expect(()=>checkStage(candidate,base,proof,proof.sha256,4)).toThrow();
 expect(()=>parseApkInfo("package: name='works.tycho.frame' versionCode='4'\nsdkVersion:'21'",`Signer #1 certificate SHA-256 digest: ${signer}\nSigner #2 certificate SHA-256 digest: ${signer}`)).toThrow();
});
test('stage snapshots and publishes only fully verified APK, preserves existing update on failure',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'frame-stage-'));try{
  const apk=join(dir,'candidate.apk'),baseline=join(dir,'baseline.apk'),proofFile=join(dir,'proof.json');await Bun.write(apk,'candidate binary');await Bun.write(baseline,'baseline binary');const baselineHash=createHash('sha256').update('baseline binary').digest('hex');await Bun.write(proofFile,JSON.stringify({...proof,sha256:baselineHash}));
  const options={apk,baselineApk:baseline,installedProof:proofFile,dir,buildTools:'unused',javaHome:'unused'};
  const inspector=async(path:string)=>path.endsWith('candidate.apk')?candidate:base;
  const update=await stageUpdate(options,inspector);expect(update.versionCode).toBe(4);expect(update.size).toBe(16);expect(await Bun.file(join(dir,'app-updates',update.sha256+'.apk')).text()).toBe('candidate binary');
  expect((await stat(join(dir,'app-update.json'))).mode&0o777).toBe(0o600);
  const before=await Bun.file(join(dir,'app-update.json')).text();
  await expect(stageUpdate(options,async()=>{throw new Error('signature invalid');})).rejects.toThrow();expect(await Bun.file(join(dir,'app-update.json')).text()).toBe(before);
  await expect(stageUpdate(options,inspector)).rejects.toThrow('newer');expect(await Bun.file(join(dir,'app-update.json')).text()).toBe(before);
  await Bun.write(apk,new Uint8Array(MAX_APK_SIZE+1));await expect(stageUpdate(options,inspector)).rejects.toThrow('size');
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('update endpoints authenticate, expose current APK only, reject private paths and malformed status',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'frame-update-routes-'));try{
  const req=(path:string,body?:unknown)=>new Request('http://localhost'+path,{method:body===undefined?'GET':'POST',headers:{authorization:'Bearer test','content-type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});
  expect((await handleRequest(new Request('http://localhost/app-update.json'),dir,'test')).status).toBe(401);
  expect((await handleRequest(req('/app-update.json'),dir,'test')).status).toBe(404);
  const sha256=createHash('sha256').update('apk').digest('hex');await Bun.write(join(dir,'app-updates',sha256+'.apk'),'apk');await Bun.write(join(dir,'app-update.json'),JSON.stringify({version:1,packageName:APP_PACKAGE,versionCode:4,sha256,size:3,url:'/app-updates/'+sha256+'.apk'}));
  expect((await handleRequest(req('/app-update.json'),dir,'test')).status).toBe(200);expect((await handleRequest(req('/app-updates/'+sha256+'.apk'),dir,'test')).status).toBe(200);
  expect((await handleRequest(new Request('http://localhost/app-updates/'+sha256+'.apk'),dir,'test')).status).toBe(401);
  for(const path of ['/app-updates/'+'f'.repeat(64)+'.apk','/app-updates/%2e%2e/config.json','/app-update-status.json'])expect((await handleRequest(req(path),dir,'test')).status).toBe(404);
  for(const payload of [{versionCode:4,state:'installed',url:'https://private'},{versionCode:4,state:'failed',errorCode:'secret traceback'},{versionCode:0,state:'installed'},{versionCode:4,state:'installed',extra:'x'.repeat(600)}])expect((await handleRequest(req('/app-update-status',payload),dir,'test')).status).toBe(400);
  expect((await handleRequest(req('/app-update-status',{versionCode:4,state:'installed'}),dir,'test')).status).toBe(204);expect((await stat(join(dir,'app-update-status.json'))).mode&0o777).toBe(0o600);
  const status=await Bun.file(join(dir,'app-update-status.json')).json();expect(status.versionCode).toBe(4);expect(Object.keys(status).sort()).toEqual(['receivedAt','state','versionCode']);
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('status has fixed values and cannot carry arbitrary commands or private fields',()=>{
 expect(validateUpdateStatus({versionCode:4,state:'failed',errorCode:'hash'})).toEqual({versionCode:4,state:'failed',errorCode:'hash'});
 for(const value of [null,[],{versionCode:4,state:'installed',errorCode:'hash'},{versionCode:4,state:'shell'},{versionCode:4,state:'installed',token:'private'}])expect(()=>validateUpdateStatus(value)).toThrow();
});
