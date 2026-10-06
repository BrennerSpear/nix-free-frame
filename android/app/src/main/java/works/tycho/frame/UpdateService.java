package works.tycho.frame;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** A bounded, authenticated self-update worker; never executes commands or opens install UI. */
public class UpdateService extends Service {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private int latestStart;
 private int pendingTasks;private boolean checking;
 private SharedPreferences state;
 private URL base;private String token;private PowerManager.WakeLock cpu;
 public void onCreate(){super.onCreate();state=getSharedPreferences("updates",MODE_PRIVATE);cpu=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"frame:bounded-maintenance");cpu.acquire(120000);}
 public IBinder onBind(Intent intent){return null;}
 public int onStartCommand(Intent intent,int flags,int startId){
  latestStart=startId;
  final Intent request=intent;
  final boolean check=request==null||!"works.tycho.frame.INSTALL_RESULT".equals(request.getAction());
  if(check&&checking)return START_NOT_STICKY;
  if(check)checking=true;pendingTasks++;
  worker.execute(()->{try{run(request);}catch(Exception ignored){}finally{new Handler(getMainLooper()).post(()->{if(check)checking=false;if(--pendingTasks==0)stopSelf(latestStart);});}});
  return START_NOT_STICKY;
 }
 public void onDestroy(){worker.shutdown();if(cpu!=null&&cpu.isHeld())cpu.release();super.onDestroy();}
 private void run(Intent request)throws Exception{
  SharedPreferences config=getSharedPreferences("FrameActivity",MODE_PRIVATE);
  token=config.getString("token",BuildConfig.TOKEN);
  String endpoint=config.getString("server",BuildConfig.SERVER_URL);
  base=new URL(endpoint.endsWith("/")?endpoint:endpoint+"/");
  UpdatePolicy.sameOrigin(base,base);
  if(token.isEmpty())return;
  flushStatus();
  int installed=installed().versionCode;
  int pending=state.getInt("pendingVersion",0);
  if(pending>0&&installed==pending){record(pending,"installed",null);clearPending();}
  if(request!=null&&"works.tycho.frame.INSTALL_RESULT".equals(request.getAction())){
   int session=state.getInt("session",-1);
   if(session<0||request.getIntExtra("session",-2)!=session)return;
   int status=request.getIntExtra("status",PackageInstaller.STATUS_FAILURE);
   if(status==PackageInstaller.STATUS_SUCCESS&&installed==pending)record(pending,"installed",null);
   else{abandon(session);record(pending,"failed",status==PackageInstaller.STATUS_PENDING_USER_ACTION?"permission":"install");}
   clearPending();return;
  }
  pending=state.getInt("pendingVersion",0);
  if(pending>0){
   if(System.currentTimeMillis()-state.getLong("committedAt",0)<10*60*1000L)return;
   abandon(state.getInt("session",-1));record(pending,"failed","install");clearPending();
  }
  long now=SystemClock.elapsedRealtime();
  long checked=state.getLong("checkedElapsed",-60000);
  if(now>=checked&&now-checked<60000)return;
  state.edit().putLong("checkedElapsed",now).apply();
  try{java.security.KeyPair provisioningKey=DirectConfig.keyPair(getSharedPreferences("directConfig-private",MODE_PRIVATE));HttpURLConnection privateConfig=connection(new URL(base,"direct-config"));
   privateConfig.setRequestProperty("X-Frame-Config-Key",android.util.Base64.encodeToString(provisioningKey.getPublic().getEncoded(),android.util.Base64.NO_WRAP));
   try{if(privateConfig.getResponseCode()==200)DirectConfig.apply(getSharedPreferences("directConfig",MODE_PRIVATE),DirectConfig.decrypt(read(privateConfig.getInputStream(),32768),provisioningKey.getPrivate()));}finally{privateConfig.disconnect();}
  }catch(Exception ignored){} // Optional maintenance failure never blocks updates or offline playback.
  VerificationController.runPending(this);
  HttpURLConnection manifest=connection(new URL(base,"app-update.json"));
  JSONObject value;
  try{
   int code=manifest.getResponseCode();if(code==404)return;if(code!=200)throw new IOException();
   value=new JSONObject(new String(read(manifest.getInputStream(),16384),"UTF-8"));
  }finally{manifest.disconnect();}
  int version=number(value,"versionCode");
  if(version==installed)return;
  File apk=new File(getFilesDir(),"self-update.apk");
  int sessionId=-1;boolean committed=false;
  try{
   Object rawSize=value.get("size");if(!(rawSize instanceof Number)||((Number)rawSize).doubleValue()!=((Number)rawSize).longValue())throw new UpdatePolicy.Failure("download");
   long size=((Number)rawSize).longValue();String sha=value.getString("sha256");
   URL url=UpdatePolicy.manifest(number(value,"version"),value.getString("packageName"),version,size,sha,value.getString("url"),base,installed);
   if(!isOwner())throw new UpdatePolicy.Failure("permission");
   HttpURLConnection download=connection(url);
   try{
    if(download.getResponseCode()!=200)throw new UpdatePolicy.Failure("download");
    try(InputStream in=download.getInputStream()){UpdatePolicy.copy(in,apk,size,sha);}
   }finally{download.disconnect();}
   PackageInfo current=installed(),archive=getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
   if(archive==null)throw new UpdatePolicy.Failure("package");
   UpdatePolicy.archive(archive.packageName,archive.versionCode,version,current.versionCode,signatures(archive),signatures(current));
   if(Build.VERSION.SDK_INT>=24&&archive.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new UpdatePolicy.Failure("package");
   record(version,"downloaded",null);
   if(!isOwner())throw new UpdatePolicy.Failure("permission");
   PackageInstaller installer=getPackageManager().getPackageInstaller();
   PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
   params.setAppPackageName(UpdatePolicy.PACKAGE);params.setSize(size);
   sessionId=installer.createSession(params);
   try(PackageInstaller.Session session=installer.openSession(sessionId)){
    try(InputStream in=new FileInputStream(apk);OutputStream out=session.openWrite("base.apk",0,size)){
     byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);session.fsync(out);
    }
    if(!state.edit().putInt("pendingVersion",version).putInt("session",sessionId).putLong("committedAt",System.currentTimeMillis()).commit())throw new UpdatePolicy.Failure("install");
    record(version,"installing",null);
    Intent result=new Intent(this,UpdateReceiver.class).setAction("works.tycho.frame.INSTALL_RESULT");
    int piFlags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)piFlags|=PendingIntent.FLAG_MUTABLE;
    PendingIntent callback=PendingIntent.getBroadcast(this,sessionId,result,piFlags);
    if(!isOwner())throw new UpdatePolicy.Failure("permission");
    session.commit(callback.getIntentSender());committed=true;
   }
  }catch(Exception e){
   if(sessionId>=0)abandon(sessionId);clearPending();
   record(version,"failed",e instanceof UpdatePolicy.Failure?((UpdatePolicy.Failure)e).code:"download");
  }finally{apk.delete();if(!committed&&sessionId>=0)abandon(sessionId);}
 }
 private boolean isOwner(){return ((DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE)).isDeviceOwnerApp(UpdatePolicy.PACKAGE);}
 private PackageInfo installed()throws PackageManager.NameNotFoundException{return getPackageManager().getPackageInfo(UpdatePolicy.PACKAGE,PackageManager.GET_SIGNATURES);}
 private static String[] signatures(PackageInfo info){
  if(info.signatures==null)return null;String[] values=new String[info.signatures.length];
  for(int i=0;i<values.length;i++)values[i]=info.signatures[i].toCharsString();return values;
 }
 private static int number(JSONObject value,String field)throws Exception{
  Object number=value.get(field);if(!(number instanceof Number)||((Number)number).doubleValue()!=((Number)number).intValue())throw new UpdatePolicy.Failure("version");return ((Number)number).intValue();
 }
 private HttpURLConnection connection(URL url)throws Exception{
  UpdatePolicy.sameOrigin(base,url);HttpURLConnection c=(HttpURLConnection)url.openConnection();
  c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(15000);c.setRequestProperty("Authorization","Bearer "+token);return c;
 }
 private static byte[] read(InputStream input,int max)throws IOException{
  try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   long deadline=System.nanoTime()+30L*1000000000;byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(System.nanoTime()>deadline||out.size()+n>max)throw new IOException();out.write(b,0,n);}return out.toByteArray();
  }
 }
 private void clearPending(){state.edit().remove("pendingVersion").remove("session").remove("committedAt").commit();}
 private void abandon(int session){if(session>=0)try{getPackageManager().getPackageInstaller().abandonSession(session);}catch(Exception ignored){}}
 private void record(int version,String status,String error)throws Exception{
  JSONObject body=new JSONObject().put("versionCode",version).put("state",status);if(error!=null)body.put("errorCode",error);
  state.edit().putString("outbox",body.toString()).commit();flushStatus();
 }
 private void flushStatus(){
  String body=state.getString("outbox",null);if(body==null)return;
  HttpURLConnection c=null;
  try{
   c=connection(new URL(base,"app-update-status"));c.setReadTimeout(5000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
   byte[] bytes=body.getBytes("UTF-8");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}
   if(c.getResponseCode()>=200&&c.getResponseCode()<300)state.edit().remove("outbox").commit();
  }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
 }
}
