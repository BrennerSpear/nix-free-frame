package works.tycho.frame;

import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.os.PowerManager;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;

/** Optional private verification receipt. No settings values, paths, photos or commands leave the app. */
final class FrameEvidence {
 private static final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
 static void report(FrameActivity activity, boolean night) {
  try {
   SharedPreferences settings=activity.getPreferences(0);
   final String endpoint=settings.getString("server",BuildConfig.SERVER_URL), token=settings.getString("token",BuildConfig.TOKEN);
   if(token.isEmpty())return;
   JSONObject value=new JSONObject();
   PackageManager pm=activity.getPackageManager();
   value.put("versionCode",pm.getPackageInfo(activity.getPackageName(),0).versionCode);
   value.put("resumed",true);
   Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
   ResolveInfo resolved=pm.resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
   value.put("home",resolved!=null&&resolved.activityInfo!=null&&activity.getPackageName().equals(resolved.activityInfo.packageName));
   value.put("owner",((DevicePolicyManager)activity.getSystemService(Context.DEVICE_POLICY_SERVICE)).isDeviceOwnerApp(activity.getPackageName()));
   value.put("runCommandGranted",pm.checkPermission("com.termux.permission.RUN_COMMAND",activity.getPackageName())==PackageManager.PERMISSION_GRANTED);
   value.put("termuxExempt",android.os.Build.VERSION.SDK_INT>=23&&((PowerManager)activity.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations("com.termux"));
   value.put("nightEnabled",settings.getBoolean("nightEnabled",true));
   value.put("nightStart",settings.getInt("nightStart",22));value.put("nightEnd",settings.getInt("nightEnd",8));value.put("nightActive",night);
   File photos=new File(activity.getFilesDir(),"photos");int count=0;File[] files=photos.listFiles();
   if(files!=null)for(File file:files)if(file.getName().matches("[a-f0-9]{64}\\.jpg")&&file.isFile())count++;
   value.put("cacheCount",count);
   value.put("manifestSha256",hash(new File(photos,"manifest.json")));
   value.put("apkSha256",hash(new File(activity.getApplicationInfo().sourceDir)));
   value.put("settingsSha256",hash(new File(activity.getApplicationInfo().dataDir,"shared_prefs/FrameActivity.xml")));
   final byte[] bytes=value.toString().getBytes("UTF-8");
   worker.execute(()->{
    HttpURLConnection connection=null;
    try {
     URL base=new URL(endpoint.endsWith("/")?endpoint:endpoint+"/");URL url=new URL(base,"frame-status");UpdatePolicy.sameOrigin(base,url);
     connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);
     connection.setRequestMethod("POST");connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Content-Type","application/json");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(bytes.length);
     try(OutputStream out=connection.getOutputStream()){out.write(bytes);}connection.getResponseCode();
    }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
   });
  }catch(Exception ignored){} // Evidence failure cannot interrupt the slideshow or update recovery.
 }
 private static String hash(File file)throws Exception {
  MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[8192];
  try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);}return hex(digest.digest());
 }
 private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
}
