import {test,expect} from 'bun:test';
import {mkdtemp,rm,stat} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {handleRequest} from '../src/core';
import {validateFrameEvidence} from '../src/frame-evidence';
const evidence={versionCode:8,cacheCount:178,resumed:true,home:true,owner:true,runCommandGranted:true,termuxExempt:true,nightEnabled:true,nightActive:false,nightStart:22,nightEnd:8,apkSha256:'a'.repeat(64),settingsSha256:'b'.repeat(64),manifestSha256:'c'.repeat(64)};
test('private evidence rejects arbitrary values and sensitive fields',()=>{
 expect(validateFrameEvidence(evidence)).toEqual(evidence);
 for(const value of [null,[],{...evidence,token:'private'},{...evidence,cacheCount:5001},{...evidence,nightStart:24},{...evidence,resumed:'yes'},{...evidence,apkSha256:'x'},{...evidence,versionCode:0},{...evidence,apkSha256:['a'.repeat(64)]}])expect(()=>validateFrameEvidence(value)).toThrow();
});
test('direct evidence carries bounded state and generic failure without capabilities',()=>{
 const direct={...evidence,mode:'direct',directLastSuccess:1,directLastAttempt:2,directSyncState:'failed',directFailureCode:'sync',directPhotoCount:178,directDatedCount:120,imageChecksPassed:true,directManifestSha256:'d'.repeat(64),configRevision:'fixture-1',syncHour:9,syncMinute:0,syncTimezone:'America/New_York'};
 expect(validateFrameEvidence(direct)).toEqual(direct);
 expect(validateFrameEvidence({...direct,directDownloadedCount:1,directAttemptRevision:'fixture-1',directSuccessRevision:'fixture-1'}).directDownloadedCount).toBe(1);
 for(const value of [{...direct,directDownloadedCount:-1},{...direct,directAttemptRevision:'secret/link',directSuccessRevision:''},{...direct,directAttemptRevision:'fixture-1'}])expect(()=>validateFrameEvidence(value)).toThrow();
 for(const value of [{...direct,albumUrl:'secret'},{...direct,directFailureCode:'network https://private'},{...direct,configRevision:'secret/link'},{...direct,directLastAttempt:-1},{...direct,syncMinute:60},{...direct,syncTimezone:'Bogus/Zone'}])expect(()=>validateFrameEvidence(value)).toThrow();
});
test('receipt authenticates, bounds payload and writes privately without exposing file',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'frame-evidence-'));
 try{
  const req=(body:unknown,token='test')=>new Request('http://localhost/frame-status',{method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},body:JSON.stringify(body)});
  expect((await handleRequest(req(evidence,'wrong'),dir,'test')).status).toBe(401);
  expect((await handleRequest(req({...evidence,extra:'x'.repeat(3000)}),dir,'test')).status).toBe(400);
  expect((await handleRequest(req(evidence),dir,'test')).status).toBe(204);
  expect((await stat(join(dir,'frame-status.json'))).mode&0o777).toBe(0o600);
  const saved=await Bun.file(join(dir,'frame-status.json')).json();expect(saved.cacheCount).toBe(178);expect(saved.receivedAt).toBeString();
  expect((await handleRequest(new Request('http://localhost/frame-status.json',{headers:{authorization:'Bearer test'}}),dir,'test')).status).toBe(404);
 }finally{await rm(dir,{recursive:true,force:true});}
});

test('power evidence preserves unknown values and rejects partial or arbitrary diagnostic fields',()=>{
 const power={powerInteractive:false,powerWakeVerified:false,powerForceLockGranted:false,powerKeyguardSecure:false,powerNightOffEnabled:true,powerDisplayState:'off',powerNextWakeAt:1,powerLastSleepAt:0,powerLastWakeAt:0,powerWakeCount:0,powerPresentationCount:0,powerScreenTimeout:-1,powerStayOnPlugged:-1,powerSleepCode:'missing_policy'};
 const verification={verificationId:'fixture-request',verificationAction:'wifi_cycle',verificationState:'started',verificationCode:'none',verificationStartedAt:1,verificationCompletedAt:0,verificationWifiState:'unknown',verificationRestoreAt:2,verificationWifiOffAt:0,verificationWifiOnAt:0,verificationOfflinePresentations:0,verificationOfflineBacklight:-1};
 expect(validateFrameEvidence({...evidence,...power,...verification}).powerScreenTimeout).toBe(-1);
 for(const value of [{...evidence,powerInteractive:false},{...evidence,...power,powerSleepCode:'secret/url'},{...evidence,...power,powerStayOnPlugged:8},{...evidence,...power,...verification,verificationAction:'shell'},{...evidence,...power,...verification,verificationId:'secret/url'},{...evidence,...power,...verification,verificationStartedAt:-1}])expect(()=>validateFrameEvidence(value)).toThrow();
});
