import {test,expect} from 'bun:test';
import {mkdtemp,rm,writeFile,chmod} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {parseEnv,configFromEnv,loadEnv} from '../src/config';
const valid={ALBUM_URL:'https://photos.google.com/share/fixture',FRAME_TOKEN:'a'.repeat(48),BIND_ADDRESS:'192.168.1.2',SERVER_PORT:'3130'};
test('env quoted secrets remain literal, including shell syntax; duplicate/malformed/control values fail',()=>{
 expect(parseEnv('FRAME_TOKEN="literal $HOME $(example) # \\"quoted\\""\nEMPTY=\n')).toEqual({FRAME_TOKEN:'literal $HOME $(example) # "quoted"',EMPTY:''});
 for(const text of ['A=one\nA=two','export A=x','A=unquoted space','A="broken','A="escaped\\nnewline"'])expect(()=>parseEnv(text)).toThrow();
});
test('invalid/missing security and runtime settings fail without leaking private values',()=>{
 const variants:Record<string,string>[]=[{ALBUM_URL:'http://photos.google.com/share/fixture'},{ALBUM_URL:'https://user:password@photos.google.com/share/fixture'},{ALBUM_URL:'https://evil.example/'},{FRAME_TOKEN:'short'},{BIND_ADDRESS:'0.0.0.0'},{BIND_ADDRESS:'192.168.invalid'},{SERVER_PORT:'3130junk'},{SERVER_PORT:'80'},{SLIDESHOW_INTERVAL_SECONDS:'0'},{SLIDESHOW_INTERVAL_SECONDS:'4'},{SLIDESHOW_INTERVAL_SECONDS:'3601'},{NIGHT_START_HOUR:'8',NIGHT_END_HOUR:'8'},{NIGHT_TIMEZONE:'Unknown/Zone'},{SYNC_HOUR:'24'},{SYNC_MINUTE:'60'},{FRAME_TOKEN:'REPLACE_WITH_GENERATED_PRIVATE_TOKEN'}];
 for(const variant of variants){try{configFromEnv({...valid,...variant});throw new Error('accepted invalid');}catch(error){expect(String(error)).not.toContain(valid.FRAME_TOKEN);expect(String(error)).not.toContain('accepted invalid');}}
 expect(()=>configFromEnv({})).toThrow('Invalid ALBUM_URL');
 expect(configFromEnv(valid)).toMatchObject({intervalSeconds:15,port:3130,bindAddress:'192.168.1.2'});
});
test('explicit env file is the sole value source; a missing file cannot fall back to process secrets',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'frame-env-'));try{
 const file=join(dir,'.env');await writeFile(file,'FRAME_TOKEN="fixture"\n',{mode:0o600});
 expect(await loadEnv(file)).toEqual({FRAME_TOKEN:'fixture'});
 await expect(loadEnv(join(dir,'missing'))).rejects.toThrow('Missing .env');
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('migration preserves private values and restrictive backup, refuses overwrite, and rollback detects edited configuration',async()=>{
 const {readFile,mkdir,copyFile,stat}=await import('node:fs/promises');const {projectRoot}=await import('../src/config');
 const dir=await mkdtemp(join(tmpdir(),'frame-migrate-'));
 async function command(action:string){const child=Bun.spawn([process.execPath,'run',join(dir,'scripts/configure.ts'),action],{cwd:dir,env:{...process.env,FRAME_ENV_FILE:join(dir,'.env')},stdout:'pipe',stderr:'pipe'});const output=await new Response(child.stdout).text()+await new Response(child.stderr).text();return {code:await child.exited,output};}
 try{
 for(const sub of ['src','scripts','runtime','android'])await mkdir(join(dir,sub));
 for(const path of ['src/config.ts','scripts/configure.ts','.env.example'])await copyFile(join(projectRoot,path),join(dir,path));
 const legacy={albumUrl:valid.ALBUM_URL,frameToken:valid.FRAME_TOKEN,bindAddress:valid.BIND_ADDRESS,port:3130,frameIp:'192.168.1.3',serverUrl:'http://192.168.1.2:3130'};
 await writeFile(join(dir,'runtime/config.json'),JSON.stringify(legacy));
 const first=await command('migrate');expect(first.code).toBe(0);expect(first.output).not.toContain(valid.FRAME_TOKEN);
 const migrated=parseEnv(await readFile(join(dir,'.env'),'utf8'));expect(migrated.FRAME_TOKEN).toBe(legacy.frameToken);expect(migrated.ALBUM_URL).toBe(legacy.albumUrl);expect(migrated.FRAME_SERVER_URL).toBe(legacy.serverUrl);
 expect((await stat(join(dir,'.env'))).mode&0o777).toBe(0o600);expect((await stat(join(dir,'runtime/config-migration/config.json'))).mode&0o777).toBe(0o600);
 expect(await Bun.file(join(dir,'runtime/config.json')).exists()).toBe(false);
 expect((await command('migrate')).code).toBe(1);
 const original=await readFile(join(dir,'.env'),'utf8');await writeFile(join(dir,'.env'),original+'# local edit\n');expect((await command('rollback')).code).toBe(1);
 await writeFile(join(dir,'.env'),original);await writeFile(join(dir,'runtime/config.json'),'changed');expect((await command('rollback')).code).toBe(1);expect(await readFile(join(dir,'runtime/config.json'),'utf8')).toBe('changed');await rm(join(dir,'runtime/config.json'));expect((await command('rollback')).code).toBe(0);expect(JSON.parse(await readFile(join(dir,'runtime/config.json'),'utf8'))).toEqual(legacy);
 }finally{await rm(dir,{recursive:true,force:true});}
});
test.skipIf(process.platform!=='darwin')('job status is read-only and removal refuses another project plist before changing files',async()=>{
 const {readFile,mkdir,stat}=await import('node:fs/promises');const {projectRoot}=await import('../src/config');
 const dir=await mkdtemp(join(tmpdir(),'frame-job-'));
 try{
  const home=join(dir,'home'),agents=join(home,'Library/LaunchAgents'),privateRuntime=join(dir,'not-created-runtime');await mkdir(agents,{recursive:true});
  const prefix='org.fixture.'+crypto.randomUUID().replaceAll('-','');
  const env=join(dir,'.env');await writeFile(env,Object.entries({...valid,LAUNCH_AGENT_PREFIX:prefix,RUNTIME_DIR:privateRuntime}).map(([k,v])=>k+'='+JSON.stringify(v)).join('\n'),{mode:0o600});
  const plist=join(agents,prefix+'.server.plist');const xml='<?xml version="1.0"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>Label</key><string>'+prefix+'.server</string><key>WorkingDirectory</key><string>/other/project</string></dict></plist>';await writeFile(plist,xml);
  async function run(flag:string){const child=Bun.spawn(['/usr/bin/python3',join(projectRoot,'scripts/install-agents.py'),flag],{env:{...process.env,HOME:home,FRAME_ENV_FILE:env},stdout:'pipe',stderr:'pipe'});await Promise.all([new Response(child.stdout).text(),new Response(child.stderr).text()]);return child.exited;}
  expect(await run('--status')).toBe(0);expect(await stat(join(privateRuntime,'logs')).then(()=>true,(error:any)=>{if(error.code==='ENOENT')return false;throw error;})).toBe(false);expect(await readFile(plist,'utf8')).toBe(xml);
  expect(await run('--remove')).toBe(1);expect(await readFile(plist,'utf8')).toBe(xml);expect(await stat(join(privateRuntime,'launchagent-backup')).then(()=>true,(error:any)=>{if(error.code==='ENOENT')return false;throw error;})).toBe(false);
 }finally{await rm(dir,{recursive:true,force:true});}
});
