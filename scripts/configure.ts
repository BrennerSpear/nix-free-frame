import {mkdir,readFile,copyFile,open,rename,rm,stat} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {homedir} from 'node:os';
import {createHash} from 'node:crypto';
import {projectRoot,parseEnv,configFromEnv,loadEnv,configuredPath} from '../src/config';
const target=resolve(process.env.FRAME_ENV_FILE??join(projectRoot,'.env'));
const command=process.argv[2];
const backup=join(projectRoot,'runtime','config-migration');
async function exists(path:string){try{await stat(path);return true;}catch(error:any){if(error.code==='ENOENT')return false;throw error;}}
async function atomic(path:string,text:string){const temp=path+`.${crypto.randomUUID()}.tmp`;const file=await open(temp,'wx',0o600);try{await file.writeFile(text);await file.close();await rename(temp,path);}finally{await file.close().catch(()=>{});await rm(temp,{force:true});}}
function encode(env:Record<string,string>){return Object.entries(env).map(([key,value])=>`${key}=${JSON.stringify(value)}`).join('\n')+'\n';}
try{
 if(command==='--help'||!command){console.log('Usage: bun run scripts/configure.ts configure|migrate|doctor|rollback\nconfigure creates a private .env from .env.example with a generated token. Edit album/addresses privately, then doctor. migrate backs up existing JSON/properties and preserves live values; refuses an existing .env. rollback restores the legacy sources and removes only the migrated .env; stop/revert jobs first. No device changes.');}
 else if(command==='configure'){
  if(await exists(target))throw new Error('.env already exists');
  const env=parseEnv(await readFile(join(projectRoot,'.env.example'),'utf8'));env.FRAME_TOKEN=crypto.randomUUID().replaceAll('-','')+crypto.randomUUID().replaceAll('-','');
  await atomic(target,encode(env));console.log('Created private .env (mode 0600). Edit placeholders, then run doctor.');
 }else if(command==='migrate'){
  if(await exists(target)||await exists(backup))throw new Error('Migration destination already exists');
  const legacy=JSON.parse(await readFile(join(projectRoot,'runtime/config.json'),'utf8'));
  const env=parseEnv(await readFile(join(projectRoot,'.env.example'),'utf8'));
  Object.assign(env,{ALBUM_URL:legacy.albumUrl,FRAME_TOKEN:legacy.frameToken,BIND_ADDRESS:legacy.bindAddress,SERVER_PORT:String(legacy.port??3130),FRAME_SERVER_URL:legacy.serverUrl??`http://${legacy.bindAddress}:${legacy.port??3130}`,FRAME_SSH_ADDRESS:legacy.frameIp??'',FRAME_KEYSTORE_PATH:join(homedir(),'.android/debug.keystore')});
  // Discover legacy label from this exact project's existing job, never a personal default.
  const probe=Bun.spawn(['/usr/bin/python3','-c',`import pathlib,plistlib,json
root=${JSON.stringify(projectRoot)}
values=[]
for p in (pathlib.Path.home()/'Library/LaunchAgents').glob('*.plist'):
 try:
  d=plistlib.loads(p.read_bytes())
  if d.get('WorkingDirectory')==root and any(a==root+'/src/server.ts' for a in d.get('ProgramArguments',[])):
   values.append(d['Label'].removesuffix('.server'))
 except Exception: pass
print(json.dumps(values))`],{stdout:'pipe',stderr:'pipe'});
  const prefixes=JSON.parse(await new Response(probe.stdout).text());if(await probe.exited!==0||prefixes.length>1)throw new Error('Ambiguous existing job labels');
  if(prefixes.length===1)env.LAUNCH_AGENT_PREFIX=prefixes[0];
  env.JAVA_HOME=await exists(join(projectRoot,'android/.tools/jdk-17.0.20.1+1/Contents/Home'))?'android/.tools/jdk-17.0.20.1+1/Contents/Home':env.JAVA_HOME;
  env.FRAME_BASELINE_APK=await exists(join(projectRoot,'runtime/app-code7-installed.apk'))?'runtime/app-code7-installed.apk':env.FRAME_BASELINE_APK;
  env.FRAME_KEYSTORE_PASSWORD='android';env.FRAME_KEY_ALIAS='AndroidDebugKey';env.FRAME_KEY_PASSWORD='android';
  const sshKeys=join(projectRoot,'runtime/ssh/nix-frame-mini-ed25519');
  if(await exists(sshKeys)){
   env.FRAME_SSH_PRIVATE_KEY_PATH=sshKeys;env.FRAME_SSH_PUBLIC_KEYS_FILE=sshKeys+'.pub';env.FRAME_SSH_HOST_KEYS_FILE=join(projectRoot,'runtime/ssh/known_hosts');
   const ssh=Bun.spawn(['/usr/bin/ssh','-G',env.FRAME_SSH_ALIAS],{stdout:'pipe',stderr:'pipe'});
   const output=await new Response(ssh.stdout).text();if(await ssh.exited!==0)throw new Error('Existing SSH alias inspection failed');
   const fields=Object.fromEntries(output.split('\n').map(line=>{const at=line.indexOf(' ');return [line.slice(0,at),line.slice(at+1)];}));
   if(fields.hostname!==env.FRAME_SSH_ADDRESS)throw new Error('Legacy SSH address sources disagree');
   env.FRAME_SSH_USER=fields.user;env.FRAME_SSH_PORT=fields.port;
  }

  const properties=join(projectRoot,'android/config.properties');
  if(await exists(properties)){
   const props=Object.fromEntries((await readFile(properties,'utf8')).split(/\r?\n/).filter(line=>line&&!line.startsWith('#')&&line.includes('=')).map(line=>{const at=line.indexOf('=');return [line.slice(0,at).trim(),line.slice(at+1).trim()];}));
   if(props.token&&props.token!==env.FRAME_TOKEN)throw new Error('Legacy token sources disagree');
   if(props.serverUrl&&props.serverUrl!==env.FRAME_SERVER_URL)throw new Error('Legacy server sources disagree');
  }
  for(const [key,value] of Object.entries(env))if(typeof value!=='string')throw new Error(`Missing legacy field for ${key}`);
  configFromEnv(env);
  await mkdir(backup,{mode:0o700,recursive:true});
  await copyFile(join(projectRoot,'runtime/config.json'),join(backup,'config.json'));await (await open(join(backup,'config.json'),'r')).close();
  const {chmod}=await import('node:fs/promises');await chmod(join(backup,'config.json'),0o600);
  if(await exists(properties)){await copyFile(properties,join(backup,'config.properties'));await chmod(join(backup,'config.properties'),0o600);}
  await atomic(target,encode(env));
  await atomic(join(backup,'migrated-env.sha256'),createHash('sha256').update(await readFile(target)).digest('hex'));
  await rm(join(projectRoot,'runtime/config.json'));if(await exists(properties))await rm(properties);
  // Legacy inputs now exist only in the restrictive rollback directory.
  console.log('Migrated private values into .env (0600); legacy backup retained. Run doctor, then install-agents.py --update.');
 }else if(command==='doctor'){
  const env=await loadEnv(target);configFromEnv(env);const mode=(await stat(target)).mode&0o777;if(mode!==0o600)throw new Error('.env permissions must be 0600');
  const dir=configuredPath(env.RUNTIME_DIR,'runtime');const manifest=join(dir,'manifest.json');
  let frameReceipt=null;try{const receipt=JSON.parse(await readFile(join(dir,'frame-status.json'),'utf8'));frameReceipt={versionCode:receipt.versionCode,cacheCount:receipt.cacheCount,resumed:receipt.resumed,home:receipt.home,owner:receipt.owner,nightEnabled:receipt.nightEnabled,receivedAt:receipt.receivedAt};}catch(error:any){if(error.code!=='ENOENT')throw error;}
  console.log(JSON.stringify({frameReceipt,configuration:'valid',privateFileMode:'0600',runtimeExists:await exists(dir),cachedManifestExists:await exists(manifest),platform:process.platform,supportedHost:process.platform==='darwin',legacyBackupExists:await exists(backup)}));
 }else if(command==='rollback'){
  if(!await exists(join(backup,'config.json')))throw new Error('No migration backup');
  if(createHash('sha256').update(await readFile(target)).digest('hex')!==(await readFile(join(backup,'migrated-env.sha256'),'utf8')))throw new Error('Migrated .env has changed; refusing automatic rollback');
  for(const [saved,current] of [[join(backup,'config.json'),join(projectRoot,'runtime/config.json')],[join(backup,'config.properties'),join(projectRoot,'android/config.properties')]])if(await exists(current)&&(!await exists(saved)||!Buffer.from(await readFile(current)).equals(await readFile(saved))))throw new Error('Legacy destination changed; refusing rollback overwrite');
  await copyFile(join(backup,'config.json'),join(projectRoot,'runtime/config.json'));
  if(await exists(join(backup,'config.properties')))await copyFile(join(backup,'config.properties'),join(projectRoot,'android/config.properties'));
  await rename(target,join(backup,`rolled-back-${Date.now()}.env`));console.log('Restored private legacy files and retained migrated .env in backup. Restore previous service code/jobs before starting legacy consumers.');
 }else throw new Error('Unknown command');
}catch(error){const allowed=['.env already exists','Migration destination already exists','Legacy token sources disagree','Legacy server sources disagree','No migration backup','Legacy destination changed; refusing rollback overwrite','Existing SSH alias inspection failed','Legacy SSH address sources disagree','Ambiguous existing job labels','Migrated .env has changed; refusing automatic rollback','Unknown command','.env permissions must be 0600'];console.error(error instanceof Error&&(allowed.includes(error.message)||/^Invalid |^FRAME_TOKEN |^BIND_ADDRESS |^Night hours|^Missing .env|^Duplicate .env|^Quote .env/.test(error.message))?error.message:'Configuration operation failed; inspect private inputs locally.');process.exitCode=1;}
