package works.tycho.frame;

import android.graphics.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Fixed synthetic fixtures verify the device's actual decoder and EXIF implementation. */
final class NativeImageChecks {
 static void run(File parent)throws Exception{
  File directory=new File(parent,"image-checks");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Image checks failed");
  File source=new File(directory,"fixture.jpg"),output=new File(directory,"normalized.jpg");
  Bitmap original=null;
  try{
   if(!"2024-03".equals(PhotoNormalizer.captureMonth("2024:03:10 02:30:00")))throw new IOException("Image checks failed");
   original=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);
   int[] colors={Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW};
   for(int y=0;y<80;y++)for(int x=0;x<120;x++)original.setPixel(x,y,colors[(y>=40?2:0)+(x>=60?1:0)]);
   ByteArrayOutputStream jpeg=new ByteArrayOutputStream();if(!original.compress(Bitmap.CompressFormat.JPEG,100,jpeg))throw new IOException();original.recycle();original=null;
   byte[] bytes=jpeg.toByteArray();
   for(int orientation=1;orientation<=8;orientation++){
    try(FileOutputStream out=new FileOutputStream(source)){out.write(bytes,0,2);out.write(exif(orientation));out.write(bytes,2,bytes.length-2);}
    if(!"2020-02".equals(PhotoNormalizer.normalize(source,output)))throw new IOException("Image checks failed");
    Bitmap normalized=BitmapFactory.decodeFile(output.getPath());if(normalized==null)throw new IOException("Image checks failed");
    try{
     if(normalized.getWidth()!=(orientation>=5?80:120)||normalized.getHeight()!=(orientation>=5?120:80))throw new IOException("Image checks failed");
     // Independently specified source-quadrant -> destination-quadrant mappings.
     int[][] destinations={{0,1,2,3},{1,0,3,2},{3,2,1,0},{2,3,0,1},{0,2,1,3},{1,3,0,2},{3,1,2,0},{2,0,3,1}};
     for(int q=0;q<4;q++){
      int dest=destinations[orientation-1][q];int pixel=normalized.getPixel(((dest%2)*2+1)*normalized.getWidth()/4,((dest/2)*2+1)*normalized.getHeight()/4);
      int expected=colors[q];if(Math.abs(Color.red(pixel)-Color.red(expected))>45||Math.abs(Color.green(pixel)-Color.green(expected))>45||Math.abs(Color.blue(pixel)-Color.blue(expected))>45)throw new IOException("Image checks failed");
     }
    }finally{normalized.recycle();}
   }
   try(FileOutputStream out=new FileOutputStream(source)){out.write(bytes);}
   if(!PhotoNormalizer.normalize(source,output).isEmpty())throw new IOException("Image checks failed");
   checkCacheTransaction(directory);
   checkWholeSync(directory,bytes);
  }finally{if(original!=null)original.recycle();source.delete();output.delete();directory.delete();}
 }
 private static void checkWholeSync(File parent,byte[] jpeg)throws Exception{
  File directory=new File(parent,"transaction-check");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cache checks failed");
  try{
   SyntheticInputs inputs=new SyntheticInputs(jpeg);
   org.json.JSONObject initial=DirectAlbumSync.run(directory,"synthetic",15,()->true,count->{},inputs);
   File manifest=new File(directory,"manifest.json"),original=new File(directory,initial.getJSONArray("photos").getJSONObject(0).getString("sha256")+".jpg");
   String manifestHash=CacheStore.digest(manifest),imageHash=CacheStore.digest(original);
   byte[] manifestBytes=fixtureBytes(manifest);
   // Same ID/update metadata cannot hide corruption of the actual cached bytes.
   CacheStore.publish(original,new byte[]{1,2,3});inputs.downloads=0;
   DirectAlbumSync.run(directory,"synthetic",15,()->true,count->{},inputs);
   if(inputs.downloads!=1)throw new IOException("Cache checks failed");
   assertSnapshot(directory,manifest,original,manifestHash,imageHash,manifestBytes);
   // The first new image reaches the real content-addressed cache. The next download fails.
   inputs.mode=1;int[] completed={0};boolean failed=false;
   try{DirectAlbumSync.run(directory,"synthetic",15,()->true,count->completed[0]=count,inputs);}catch(DirectAlbumSync.Failure e){failed="download_timeout".equals(e.code);}
   if(!failed||completed[0]!=1)throw new IOException("Cache checks failed");
   assertSnapshot(directory,manifest,original,manifestHash,imageHash,manifestBytes);
   // Incomplete enumeration is rejected before downloading anything; empty results also fail.
   for(int mode=2;mode<=3;mode++){
    inputs.mode=mode;inputs.downloads=0;failed=false;
    try{DirectAlbumSync.run(directory,"synthetic",15,()->true,count->{},inputs);}catch(DirectAlbumSync.Failure e){failed=mode==2?"enumeration_timeout".equals(e.code):"enumeration".equals(e.code);}
    if(!failed||inputs.downloads!=0)throw new IOException("Cache checks failed");
    assertSnapshot(directory,manifest,original,manifestHash,imageHash,manifestBytes);
   }
   // A revised configuration cancels before another download and preserves the exact snapshot.
   inputs.mode=1;completed[0]=0;boolean[] current={true};failed=false;
   try{DirectAlbumSync.run(directory,"synthetic",15,()->current[0],count->{completed[0]=count;current[0]=false;},inputs);}catch(DirectAlbumSync.Failure e){failed="config".equals(e.code);}
   if(!failed||completed[0]!=1)throw new IOException("Cache checks failed");
   assertSnapshot(directory,manifest,original,manifestHash,imageHash,manifestBytes);
  }finally{File[] files=directory.listFiles();if(files!=null)for(File file:files)file.delete();directory.delete();}
 }
 private static void assertSnapshot(File directory,File manifest,File original,String manifestHash,String imageHash,byte[] manifestBytes)throws Exception{
  if(!original.isFile()||!imageHash.equals(CacheStore.digest(original))||!manifestHash.equals(CacheStore.digest(manifest))||!java.util.Arrays.equals(manifestBytes,fixtureBytes(manifest)))throw new IOException("Cache checks failed");
  File[] files=directory.listFiles();if(files==null||files.length!=2)throw new IOException("Cache checks failed");
  for(File file:files)if(!file.equals(manifest)&&!file.equals(original))throw new IOException("Cache checks failed");
 }
 private static byte[] fixtureBytes(File file)throws IOException{
  try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[1024];int read;while((read=in.read(buffer))!=-1){if(out.size()+read>16384)throw new IOException("Cache checks failed");out.write(buffer,0,read);}return out.toByteArray();
  }
 }
 private static final class SyntheticInputs implements DirectAlbumSync.Inputs {
  final byte[] jpeg;int mode,downloads;
  SyntheticInputs(byte[] jpeg){this.jpeg=jpeg;}
  public java.util.List<SharedAlbumClient.Item> enumerate(String ignored)throws Exception{
   java.util.List<SharedAlbumClient.Item> items=new java.util.ArrayList<>();
   if(mode==3)return items;
   items.add(new SharedAlbumClient.Item(mode==0?"baseline":"new-a",mode==0?"baseline":"new-a",120,80,false,1,1));
   if(mode==2)throw new AlbumHttps.Failure(AlbumHttps.Code.timeout);
   if(mode==1)items.add(new SharedAlbumClient.Item("new-b","new-b",120,80,false,1,1));
   return items;
  }
  public void download(String url,File destination)throws Exception{
   downloads++;if(url.startsWith("new-b="))throw new AlbumHttps.Failure(AlbumHttps.Code.timeout);
   try(FileOutputStream out=new FileOutputStream(destination)){out.write(jpeg,0,2);out.write(exif(mode==0?1:6));out.write(jpeg,2,jpeg.length-2);}
  }
  public String normalize(File source,File destination)throws Exception{return PhotoNormalizer.normalize(source,destination);}
 }
 private static void checkCacheTransaction(File directory)throws Exception{
  File old=new File(directory,"old.jpg"),staged=new File(directory,"staged.jpg"),replacement=new File(directory,"new.jpg"),manifest=new File(directory,"manifest.json");
  try{
   CacheStore.publish(old,new byte[]{1,2,3});CacheStore.publish(manifest,"old.jpg".getBytes("UTF-8"));CacheDurability.syncDirectory(directory);
   CacheStore.publish(staged,new byte[]{4,5,6});String sha=CacheStore.digest(staged);
   boolean rejected=false;try{CacheStore.publishImage(staged,replacement,"invalid");}catch(IOException expected){rejected=true;}
   if(!rejected||replacement.exists()||!old.isFile()||!"old.jpg".equals(readFixture(manifest)))throw new IOException("Cache checks failed");
   CacheStore.publishImage(staged,replacement,sha);CacheDurability.syncDirectory(directory);
   if(!old.isFile()||!"old.jpg".equals(readFixture(manifest))||!sha.equals(CacheStore.digest(replacement)))throw new IOException("Cache checks failed");
   CacheStore.publish(manifest,"new.jpg".getBytes("UTF-8"));CacheDurability.syncDirectory(directory);
   if(!old.delete()||!"new.jpg".equals(readFixture(manifest))||!sha.equals(CacheStore.digest(replacement)))throw new IOException("Cache checks failed");
  }finally{old.delete();staged.delete();replacement.delete();manifest.delete();}
 }
 private static String readFixture(File file)throws IOException{
  byte[] bytes=new byte[7];try(FileInputStream in=new FileInputStream(file)){if(in.read(bytes)!=7||in.read()!=-1)throw new IOException("Cache checks failed");}return new String(bytes,"UTF-8");
 }
 private static byte[] exif(int orientation)throws IOException{
  ByteBuffer tiff=ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN);
  tiff.put((byte)'I').put((byte)'I').putShort((short)42).putInt(8);
  tiff.putShort((short)2);
  tiff.putShort((short)0x112).putShort((short)3).putInt(1).putShort((short)orientation).putShort((short)0);
  tiff.putShort((short)0x8769).putShort((short)4).putInt(1).putInt(38);tiff.putInt(0);
  tiff.putShort((short)1);tiff.putShort((short)0x9003).putShort((short)2).putInt(20).putInt(56);tiff.putInt(0);tiff.put("2020:02:29 12:34:56\0".getBytes("US-ASCII"));
  ByteArrayOutputStream segment=new ByteArrayOutputStream();segment.write(0xff);segment.write(0xe1);segment.write(0);segment.write(84);segment.write(new byte[]{'E','x','i','f',0,0});segment.write(tiff.array());return segment.toByteArray();
 }
}
