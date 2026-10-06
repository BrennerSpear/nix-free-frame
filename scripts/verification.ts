import {open,rename,rm,readFile} from 'node:fs/promises';
import {join} from 'node:path';
import {runtimeDirectory} from '../src/config';
import {validateDirectConfig} from '../src/direct-config';
const actions=['alarm_probe','power_cycle','wifi_cycle','reboot','day_preview'];
const args=process.argv.slice(2);
if(args.includes('--help')||!args.length){
 console.log('Usage: bun run scripts/verification.ts alarm_probe|power_cycle|wifi_cycle|reboot|day_preview\nStage one explicit, fixed DEBUG verification action through private encrypted maintenance. No commands, URLs, durations, or secret arguments. Power cycle requires confirmed policy and wake probe. Wi-Fi cycle first proves its recovery receiver, then disables radio for 90 seconds with persisted timed/boot restore. Reboot is a real software reboot after a 45-second persistence wait; it is not a cold-power test. Use only for authorized device verification.');
}else{
 try{
  if(args.length!==1||!actions.includes(args[0]))throw new Error();
  const dir=await runtimeDirectory(),path=join(dir,'direct-config.json');
  const value=validateDirectConfig({...JSON.parse(await readFile(path,'utf8')),verification:{version:1,id:crypto.randomUUID(),action:args[0]}});
  const temp=join(dir,`verification-${crypto.randomUUID()}.tmp`);
  try{const file=await open(temp,'wx',0o600);try{await file.writeFile(JSON.stringify(value));await file.sync();}finally{await file.close();}await rename(temp,path);}finally{await rm(temp,{force:true});}
  console.log(JSON.stringify({staged:true,action:args[0]}));
 }catch{console.error('Verification staging failed; previous private configuration retained.');process.exitCode=1;}
}
