import {test,expect} from 'bun:test';
import {capturedMonthFromJpeg} from '../src/captured-month';
import {syncAlbum} from '../src/core';
import {createHash} from 'node:crypto';
import {mkdtemp,mkdir,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
function jpegDate(date:string,little=true){
 const tiff=new Uint8Array(64);const v=new DataView(tiff.buffer);tiff.set(little?[73,73]:[77,77]);
 const u16=(o:number,n:number)=>v.setUint16(o,n,little),u32=(o:number,n:number)=>v.setUint32(o,n,little);
 u16(2,42);u32(4,8);u16(8,1);u16(10,0x8769);u16(12,4);u32(14,1);u32(18,26);
 u16(26,1);u16(28,0x9003);u16(30,2);u32(32,20);u32(36,44);tiff.set(new TextEncoder().encode(date+'\0'),44);
 const out=new Uint8Array(76);out.set([255,216,255,225,0,72,69,120,105,102,0,0]);out.set(tiff,12);return out;
}
test('reads capture month from both endian EXIF without timezone conversion',()=>{
 for(const little of [true,false])expect(capturedMonthFromJpeg(jpegDate('2024:02:29 23:59:59',little))).toBe('2024-02');
});
test('missing, truncated, bogus TIFF offsets and invalid dates remain unknown',()=>{
 for(const input of [new Uint8Array(),jpegDate('2023:02:29 12:00:00'),jpegDate('2024:13:01 12:00:00'),jpegDate('2024:01:01 25:00:00'),jpegDate('2024:01:01 12:00:00').slice(0,65)])expect(capturedMonthFromJpeg(input)).toBeUndefined();
 const bad=jpegDate('2024:01:01 12:00:00');bad.fill(255,50,54);expect(capturedMonthFromJpeg(bad)).toBeUndefined();
});
test('unchanged cache backfills real capture month and removes stale date for unknown photo without redownload',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'frame-month-'));try{
  await mkdir(join(dir,'photos'));const data=[jpegDate('2024:02:29 12:00:00'),new Uint8Array([255,216,255,217])];
  const items=['a','b'].map(uid=>({uid,url:'https://lh3.googleusercontent.com/a',posterUrl:'https://lh3.googleusercontent.com/a',videoUrl:null,isVideo:false,width:1,height:1,imageUpdateDate:1,albumAddDate:1}));
  const photos=[];for(let i=0;i<data.length;i++){const sha256=createHash('sha256').update(data[i]).digest('hex');await Bun.write(join(dir,'photos',sha256+'.jpg'),data[i]);photos.push({id:createHash('sha256').update(items[i].uid).digest('hex'),revision:1,sha256,url:'/photos/'+sha256+'.jpg',width:1,height:1,capturedMonth:'1900-01'});}
  await Bun.write(join(dir,'manifest.json'),JSON.stringify({version:1,intervalSeconds:15,syncedAt:'before',photos}));
  const result=await syncAlbum({albumUrl:'https://photos.google.com/share/test',frameToken:'test'},dir,{enumerate:async()=>items,download:async()=>{throw new Error('Must not redownload');}});
  expect(result.downloaded).toBe(0);const manifest=await Bun.file(join(dir,'manifest.json')).json();expect(manifest.photos[0].capturedMonth).toBe('2024-02');expect(manifest.photos[1]).not.toHaveProperty('capturedMonth');expect(manifest.intervalSeconds).toBe(15);
 }finally{await rm(dir,{recursive:true,force:true});}
});
