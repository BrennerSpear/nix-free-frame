import {test,expect} from 'bun:test';
import {mkdtemp,rm,writeFile,chmod} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {createDecipheriv,generateKeyPairSync,privateDecrypt,constants} from 'node:crypto';
import {handleRequest} from '../src/core';
import {validateDirectConfig} from '../src/direct-config';
const config={version:1,revision:'fixture-1',mode:'direct',albumUrl:'https://photos.google.com/share/synthetic?key=fixture',syncHour:9,syncMinute:0,syncTimezone:'America/New_York',intervalSeconds:15};
test('private direct config validates capability and explicit host rollback',()=>{
 expect(validateDirectConfig(config)).toEqual(config);
 const {albumUrl,...host}=config;expect(validateDirectConfig({...host,mode:'host'}).mode).toBe('host');
 for(const value of [{...config,mode:'host'},{...config,albumUrl:'http://photos.google.com/share/x'},{...config,albumUrl:'https://photos.google.com.evil.test/x'},{...config,albumUrl:'https://user@photos.google.com/x'},{...config,albumUrl:'https://photos.google.com:8443/x'},{...config,syncHour:24},{...config,syncTimezone:'Unknown/Zone'},{...config,revision:'secret/link'},{...config,token:'unexpected'}])expect(()=>validateDirectConfig(value)).toThrow();
});
test('config is opt-in, authenticated, private and never served by filename',async()=>{
 const keys=generateKeyPairSync('rsa',{modulusLength:2048});const recipient=keys.publicKey.export({type:'spki',format:'der'}).toString('base64');
 const dir=await mkdtemp(join(tmpdir(),'direct-config-'));const request=(path='/direct-config',token='test')=>new Request('http://localhost'+path,{headers:{authorization:'Bearer '+token,'x-frame-config-key':recipient}});
 try{
  expect((await handleRequest(request(),dir,'test')).status).toBe(404);
  await writeFile(join(dir,'direct-config.json'),JSON.stringify(config),{mode:0o600});
  expect((await handleRequest(request('/direct-config','wrong'),dir,'test')).status).toBe(401);
  const response=await handleRequest(request(),dir,'test');expect(response.status).toBe(200);expect(response.headers.get('cache-control')).toBe('no-store');
  const wire=await response.text();expect(wire).not.toContain(config.albumUrl);const sealed=JSON.parse(wire);
  const bytes=Buffer.from(sealed.ciphertext,'base64');const key=privateDecrypt({key:keys.privateKey,padding:constants.RSA_PKCS1_OAEP_PADDING,oaepHash:'sha1'},Buffer.from(sealed.wrappedKey,'base64'));
  const open=(data=bytes)=>{const cipher=createDecipheriv('aes-256-gcm',key,Buffer.from(sealed.nonce,'base64'));cipher.setAAD(Buffer.from('nix-free-frame-direct-config-v1'));cipher.setAuthTag(data.subarray(-16));return JSON.parse(Buffer.concat([cipher.update(data.subarray(0,-16)),cipher.final()]).toString());};
  expect(open()).toEqual(config);const damaged=Buffer.from(bytes);damaged[0]^=1;expect(()=>open(damaged)).toThrow();
  const other=generateKeyPairSync('rsa',{modulusLength:2048});expect(()=>privateDecrypt({key:other.privateKey,padding:constants.RSA_PKCS1_OAEP_PADDING,oaepHash:'sha1'},Buffer.from(sealed.wrappedKey,'base64'))).toThrow();
  expect((await handleRequest(request('/direct-config.json'),dir,'test')).status).toBe(404);
  await chmod(join(dir,'direct-config.json'),0o644);expect((await handleRequest(request(),dir,'test')).status).toBe(503);
 }finally{await rm(dir,{recursive:true,force:true});}
});

test('verification accepts only explicit fixed one-shot tasks without parameters or commands',()=>{
 const verification={version:1,id:'fixture-request',action:'alarm_probe'};
 expect(validateDirectConfig({...config,verification}).verification).toEqual(verification);
 for(const task of [{...verification,action:'shell'},{...verification,command:'anything'},{...verification,delaySeconds:9999},{...verification,id:'secret/url'},{...verification,version:2}])expect(()=>validateDirectConfig({...config,verification:task})).toThrow();
});
