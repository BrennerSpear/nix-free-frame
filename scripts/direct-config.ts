import {mkdir,open,rename,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {loadEnv,runtimeDirectory} from '../src/config';
import {validateDirectConfig} from '../src/direct-config';

const args=process.argv.slice(2);
if(args.includes('--help')||!args.length){
 console.log('Usage: bun run scripts/direct-config.ts direct|host\nExplicitly stage private direct-album configuration, or restore host mode, using existing .env. Never accepts secret URLs/tokens in arguments. Only use when changing this frame configuration is authorized. The frame imports through its authenticated maintenance connection; the album capability is encrypted to its private key. This maintenance path retains the existing trusted-LAN bearer-token boundary.');
}else{
 try{
  if(args.length!==1||!['direct','host'].includes(args[0]))throw new Error();
  const env=await loadEnv(),dir=await runtimeDirectory();
  const value=validateDirectConfig({version:1,revision:crypto.randomUUID(),mode:args[0],...(args[0]==='direct'?{albumUrl:env.ALBUM_URL}:{}),syncHour:Number(env.SYNC_HOUR??9),syncMinute:Number(env.SYNC_MINUTE??0),syncTimezone:env.NIGHT_TIMEZONE??'America/New_York',intervalSeconds:Number(env.SLIDESHOW_INTERVAL_SECONDS??15)});
  await mkdir(dir,{recursive:true,mode:0o700});const temp=join(dir,`direct-config-${crypto.randomUUID()}.tmp`);
  try{const file=await open(temp,'wx',0o600);try{await file.writeFile(JSON.stringify(value));await file.sync();}finally{await file.close();}await rename(temp,join(dir,'direct-config.json'));}finally{await rm(temp,{force:true});}
  console.log(JSON.stringify({staged:true,mode:args[0]}));
 }catch{console.error('Private configuration staging failed. Previous configuration retained; inspect local settings without printing secrets.');process.exitCode=1;}
}
