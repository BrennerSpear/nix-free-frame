import {readFile,stat} from 'node:fs/promises';
import {resolve} from 'node:path';
import {isIP} from 'node:net';
import {homedir} from 'node:os';
export const projectRoot=resolve(import.meta.dir,'..');
/** Deliberately no shell expansion, interpolation, export statements, or implicit process-env merging. */
export function parseEnv(text:string):Record<string,string>{
 const values:Record<string,string>={};
 for(const raw of text.split(/\r?\n/)){
  const line=raw.trim();if(!line||line.startsWith('#'))continue;
  const match=/^([A-Z][A-Z0-9_]*)\s*=\s*(.*)$/.exec(line);if(!match)throw new Error('Invalid .env syntax');
  const [,key,input]=match;if(Object.hasOwn(values,key))throw new Error('Duplicate .env key');
  let value=input;
  if(input.startsWith('"')){try{value=JSON.parse(input);}catch{throw new Error('Invalid .env quoted value');}if(typeof value!=='string')throw new Error('Invalid .env value');}
  else if(input.startsWith("'")){if(!input.endsWith("'")||input.length<2)throw new Error('Invalid .env quoted value');value=input.slice(1,-1);}
  else if(/[\s#]/.test(input))throw new Error('Quote .env values containing spaces or comments');
  if(/[\u0000\r\n]/.test(value))throw new Error('Invalid .env control character');values[key]=value;
 }return values;
}
export async function loadEnv(path=process.env.FRAME_ENV_FILE??resolve(projectRoot,'.env')):Promise<Record<string,string>>{
 try{if(((await stat(path)).mode&0o777)!==0o600)throw new Error('.env permissions must be 0600');return parseEnv(await readFile(path,'utf8'));}catch(error:any){if(error.code==='ENOENT')throw new Error('Missing .env; run configure or migrate first');throw error;}
}
export function configuredPath(value:string|undefined,fallback:string):string{const path=value||fallback;if(path.startsWith('~')&&path!=='~'&&!path.startsWith('~/'))throw new Error('Unsupported home-path expansion');return resolve(projectRoot,path==='~'?homedir():path.startsWith('~/')?resolve(homedir(),path.slice(2)):path);}
export const runtime=configuredPath((await loadEnv().catch(error=>{if(error.message.startsWith('Missing .env'))return {} as Record<string,string>;throw error;})).RUNTIME_DIR,'runtime');
export type Config={albumUrl:string;bindAddress?:string;port?:number;frameToken:string;intervalSeconds?:number};
export function configFromEnv(env:Record<string,string>):Config{
 if(!env.ALBUM_URL||env.ALBUM_URL.includes('REPLACE_WITH'))throw new Error('Invalid ALBUM_URL');
 let url:URL;try{url=new URL(env.ALBUM_URL);}catch{throw new Error('Invalid ALBUM_URL');}
 if(url.protocol!=='https:'||!['photos.app.goo.gl','photos.google.com'].includes(url.hostname)||url.username||url.password)throw new Error('Invalid ALBUM_URL');
 if(typeof env.FRAME_TOKEN!=='string'||env.FRAME_TOKEN.length<32||env.FRAME_TOKEN.includes('REPLACE_WITH')||/\s/.test(env.FRAME_TOKEN))throw new Error('FRAME_TOKEN requires at least 32 non-space characters');
 const integer=(name:string,fallback:number,min:number,max:number)=>{const text=env[name]??String(fallback);if(!/^\d+$/.test(text)||Number(text)<min||Number(text)>max)throw new Error(`Invalid ${name}`);return Number(text);};
 const address=env.BIND_ADDRESS??'127.0.0.1';
 if(isIP(address)!==4||!(/^(127\.|192\.168\.|10\.|172\.(1[6-9]|2\d|3[01])\.)/.test(address)))throw new Error('BIND_ADDRESS must be a loopback or private IPv4 address');
 integer('SYNC_HOUR',9,0,23);integer('SYNC_MINUTE',0,0,59);
 const start=integer('NIGHT_START_HOUR',22,0,23),end=integer('NIGHT_END_HOUR',8,0,23);if(start===end)throw new Error('Night hours must differ');
 try{new Intl.DateTimeFormat('en',{timeZone:env.NIGHT_TIMEZONE??'America/New_York'});}catch{throw new Error('Invalid NIGHT_TIMEZONE');}
 return {albumUrl:env.ALBUM_URL,frameToken:env.FRAME_TOKEN,bindAddress:address,port:integer('SERVER_PORT',3130,1024,65535),intervalSeconds:integer('SLIDESHOW_INTERVAL_SECONDS',15,5,3600)};
}
export async function readConfig():Promise<Config>{return configFromEnv(await loadEnv());}
// Resolve private storage from the file itself; all consumers use this same value.
export async function runtimeDirectory():Promise<string>{return configuredPath((await loadEnv()).RUNTIME_DIR,'runtime');}
