package works.tycho.frame;

import org.json.*;
import java.io.*;
import java.util.*;

/** Complete enumeration and all verified images precede the single atomic manifest swap. */
final class DirectAlbumSync {
 interface Progress { void downloaded(int count); }
 interface Inputs {
  List<SharedAlbumClient.Item> enumerate(String album)throws Exception;
  void download(String url,File destination)throws Exception;
  String normalize(File source,File destination)throws Exception;
 }
 private static final Inputs NATIVE=new Inputs(){
  public List<SharedAlbumClient.Item> enumerate(String album)throws Exception{return new SharedAlbumClient().fetch(album);}
  public void download(String url,File destination)throws Exception{AlbumHttps.downloadPhoto(url,destination);}
  public String normalize(File source,File destination)throws Exception{return PhotoNormalizer.normalize(source,destination);}
 };
 static final class Failure extends IOException {
  final String code;
  Failure(String stage,Exception failure){super(safeCode(stage,failure));code=safeCode(stage,failure);}
 }
 static String safeCode(String stage,Exception failure){
  if(!stage.matches("enumeration|download|normalize|cache|config"))return "sync";
  if((stage.equals("enumeration")||stage.equals("download"))&&failure instanceof AlbumHttps.Failure){String network=((AlbumHttps.Failure)failure).code;if(network.matches("tls|timeout|http|limit|redirect|network"))return stage+"_"+network;}
  return stage;
 }
 static JSONObject run(File directory,String album,int interval,java.util.concurrent.Callable<Boolean> stillCurrent,Progress progress)throws Exception{
  return run(directory,album,interval,stillCurrent,progress,NATIVE);
 }
 static JSONObject run(File directory,String album,int interval,java.util.concurrent.Callable<Boolean> stillCurrent,Progress progress,Inputs inputs)throws Exception{
  String stage="cache";
  try{
  CacheDurability.syncDirectory(directory.getParentFile());
  long deadline=System.nanoTime()+30L*60*1000000000;
  stage="enumeration";
  List<SharedAlbumClient.Item> items=inputs.enumerate(album);
  if(items.isEmpty()||items.size()>5000)throw new IOException("Invalid album count");
  stage="cache";
  int downloaded=0;
  JSONObject previous=new JSONObject();File manifestFile=new File(directory,"manifest.json");
  if(manifestFile.isFile()&&manifestFile.length()<4*1024*1024)try(FileInputStream in=new FileInputStream(manifestFile)){byte[] bytes=new byte[(int)manifestFile.length()];int p=0,n;while(p<bytes.length&&(n=in.read(bytes,p,bytes.length-p))>0)p+=n;previous=new JSONObject(new String(bytes,"UTF-8"));}catch(Exception ignored){}
  Map<String,JSONObject> known=new HashMap<>();JSONArray old=previous.optJSONArray("photos");
  if(old!=null&&"direct".equals(previous.optString("source")))for(int i=0;i<old.length();i++){JSONObject photo=old.getJSONObject(i);known.put(photo.optString("id"),photo);}
  long retainedBytes=0;File[] retainedFiles=directory.listFiles();if(retainedFiles!=null)for(File file:retainedFiles)if(file.getName().matches("[a-f0-9]{64}\\.jpg"))retainedBytes+=file.length();
  JSONArray photos=new JSONArray();List<File> added=new ArrayList<>();boolean published=false;long total=0;
  File raw=new File(directory,"direct-download.part"),normalized=new File(directory,"direct-normalized.part");
  try{
   for(SharedAlbumClient.Item item:items){
    stage="config";
    if(!stillCurrent.call())throw new IOException("Configuration changed");
    stage="cache";
    if(System.nanoTime()>deadline)throw new IOException("Sync time limit");
    if(item.isVideo)continue;
    JSONObject photo=known.get(item.id);File cached=null;
    if(photo!=null&&photo.optLong("imageUpdateDate",-1)==item.imageUpdateDate&&photo.optString("sha256").matches("[a-f0-9]{64}")){cached=new File(directory,photo.getString("sha256")+".jpg");if(!cached.isFile()||!photo.getString("sha256").equals(CacheStore.digest(cached)))cached=null;}
    if(cached==null){
     if(directory.getUsableSpace()<96L*1024*1024)throw new IOException("Insufficient cache space");
     stage="download";// Match the existing host rendition: bounded JPEG-compatible image with retained EXIF.
     inputs.download(item.url+"=w1920-h1920",raw);progress.downloaded(++downloaded);
     stage="normalize";String month=inputs.normalize(raw,normalized);raw.delete();
     stage="cache";
     String sha=CacheStore.digest(normalized);cached=new File(directory,sha+".jpg");
     boolean existed=cached.isFile();long nextRetained=retainedBytes-(existed?cached.length():0)+normalized.length();if(nextRetained>1024L*1024*1024)throw new IOException("Cache size limit");
     CacheStore.publishImage(normalized,cached,sha);retainedBytes=nextRetained;if(!existed)added.add(cached);
     photo=new JSONObject().put("id",item.id).put("sha256",sha).put("capturedMonth",month).put("imageUpdateDate",item.imageUpdateDate);
    }
    total+=cached.length();if(total>1024L*1024*1024)throw new IOException("Cache size limit");photos.put(photo);
   }
   if(photos.length()==0)throw new IOException("No readable photos");
   JSONObject manifest=new JSONObject().put("version",1).put("source","direct").put("intervalSeconds",interval).put("photos",photos);
   byte[] bytes=manifest.toString().getBytes("UTF-8");if(bytes.length>4*1024*1024)throw new IOException("Manifest size limit");
   stage="config";
   if(!stillCurrent.call())throw new IOException("Configuration changed");
   stage="cache";
   // Images were individually fsynced by normalization; persist all image names first.
   CacheDurability.syncDirectory(directory);
   CacheStore.publish(manifestFile,bytes);published=true;
   // Set published before this barrier: failure must preserve the new referenced images too.
   CacheDurability.syncDirectory(directory);
   Set<String> keep=new HashSet<>();for(int i=0;i<photos.length();i++)keep.add(photos.getJSONObject(i).getString("sha256")+".jpg");
   File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.getName().matches("[a-f0-9]{64}\\.jpg")&&!keep.contains(file.getName()))file.delete();
   return manifest;
  }finally{raw.delete();normalized.delete();if(!published)for(File file:added)file.delete();}
  }catch(Exception failure){throw new Failure(stage,failure);}
 }
}
