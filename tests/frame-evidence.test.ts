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
