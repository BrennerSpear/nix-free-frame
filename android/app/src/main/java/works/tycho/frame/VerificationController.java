package works.tycho.frame;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.*;
import java.io.*;
import org.json.JSONObject;

/** Explicit debug verification. One-shot ledger, fixed actions, and persisted radio recovery. */
final class VerificationController {
 static final String PROBE="works.tycho.frame.VERIFY_WIFI_PROBE",RESTORE="works.tycho.frame.VERIFY_WIFI_RESTORE",REPORT="works.tycho.frame.VERIFY_REPORT",FINISH="works.tycho.frame.VERIFY_FINISH",REBOOT="works.tycho.frame.VERIFY_REBOOT",SAMPLE="works.tycho.frame.VERIFY_SAMPLE";
 private static PowerManager.WakeLock lease;
 static SharedPreferences state(Context c){return c.getSharedPreferences("verification",Context.MODE_PRIVATE);}
 private static WifiManager wifi(Context c){return (WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);}
 private static synchronized void cpu(Context c){if(lease!=null&&lease.isHeld())lease.release();lease=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"frame:bounded-verification");lease.acquire(120000);}
 private static void alarm(Context c,String action,int code,long at){
  PendingIntent pending=PendingIntent.getBroadcast(c,code,new Intent(c,VerificationReceiver.class).setAction(action).putExtra("verificationId",state(c).getString("id","")),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
  AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
  if(Build.VERSION.SDK_INT>=23)alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending);else alarms.setExact(AlarmManager.RTC_WAKEUP,at,pending);
 }
 private static void fail(Context c,String code){state(c).edit().putString("state","failed").putString("code",code).putLong("completedAt",System.currentTimeMillis()).commit();FrameEvidence.report(c);}
 private static void complete(Context c){state(c).edit().putString("state","complete").putString("code","none").putLong("completedAt",System.currentTimeMillis()).commit();FrameEvidence.report(c);}
 static synchronized void runPending(Context c){
  if(!BuildConfig.DEBUG)return;maintain(c);
  SharedPreferences config=c.getSharedPreferences("directConfig",0),s=state(c);String id=config.getString("verificationId",""),action=config.getString("verificationAction","");
  if(!VerificationPolicy.valid(id,action)||id.equals(s.getString("id",""))||s.getLong("restoreAt",0)>0||("started".equals(s.getString("state",""))&&System.currentTimeMillis()-s.getLong("startedAt",0)<180000))return;
  if(!s.edit().putString("id",id).putString("action",action).putString("state","started").putString("code","none").putLong("startedAt",System.currentTimeMillis()).putLong("completedAt",0).putLong("wifiOffAt",0).putLong("wifiOnAt",0).putInt("offlinePresentations",0).putInt("offlineBacklight",-1).putInt("restoreRetries",0).putLong("presentationStart",ScreenPowerController.state(c).getLong("presentationCount",0)).commit())return;
  cpu(c);
  try{
   if("alarm_probe".equals(action)){if(!ScreenPowerController.requestAlarmProbe(c)){fail(c,"alarm");return;}s.edit().putLong("targetWakeCount",ScreenPowerController.state(c).getLong("wakeCount",0)+1).commit();s.edit().putLong("finishAt",System.currentTimeMillis()+105000).commit();alarm(c,FINISH,715,s.getLong("finishAt",0));}
   else if("power_cycle".equals(action)){if(!ScreenPowerController.requestSleepCycle(c)){fail(c,ScreenPowerController.secure(c)?"secure_keyguard":!ScreenPowerController.granted(c)?"policy":"alarm");return;}s.edit().putLong("targetWakeCount",ScreenPowerController.state(c).getLong("wakeCount",0)+1).commit();s.edit().putLong("finishAt",System.currentTimeMillis()+105000).commit();alarm(c,FINISH,715,s.getLong("finishAt",0));}
   else if("day_preview".equals(action)){if(!ScreenPowerController.requestDayPreview(c)){fail(c,"secure_keyguard");return;}complete(c);}
   else if("wifi_cycle".equals(action)){
    if(c.checkCallingOrSelfPermission("android.permission.CHANGE_WIFI_STATE")!=PackageManager.PERMISSION_GRANTED||c.checkCallingOrSelfPermission("android.permission.ACCESS_WIFI_STATE")!=PackageManager.PERMISSION_GRANTED){fail(c,"permission");return;}
    if(!wifi(c).isWifiEnabled()||!ScreenPowerController.state(c).getBoolean("wakeVerified",false)){fail(c,"alarm");return;}
    // First deliver this exact receiver with the radio unchanged, then prearm restoration.
    s.edit().putString("wifiStage","probe").commit();alarm(c,PROBE,711,System.currentTimeMillis()+30000);
   }else if("reboot".equals(action)){
    DevicePolicyManager owner=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);
    if(Build.VERSION.SDK_INT<24||!owner.isDeviceOwnerApp(c.getPackageName())){fail(c,"unsupported");return;}
    s.edit().putString("rebootPhase","queued").putLong("rebootAt",System.currentTimeMillis()+45000).commit();FrameEvidence.report(c);alarm(c,REBOOT,716,s.getLong("rebootAt",0));
   }
   FrameEvidence.report(c);
  }catch(Exception e){if(s.getLong("restoreAt",0)>0)restoreWifi(c);else fail(c,"internal");}
 }
 static void finishProbe(Context c){SharedPreferences s=state(c);if(!"started".equals(s.getString("state","")))return;String action=s.getString("action","");if(!"alarm_probe".equals(action)&&!"power_cycle".equals(action))return;if(ScreenPowerController.state(c).getLong("wakeCount",0)>=s.getLong("targetWakeCount",Long.MAX_VALUE)&&ScreenPowerController.state(c).getBoolean("wakeVerified",false))complete(c);else fail(c,"alarm");}
 static synchronized void beginWifiCycle(Context c){
  SharedPreferences s=state(c);if(!BuildConfig.DEBUG||!"wifi_cycle".equals(s.getString("action",""))||!"started".equals(s.getString("state",""))||!"probe".equals(s.getString("wifiStage","")))return;
  cpu(c);
  try{
   if(!wifi(c).isWifiEnabled()||!ScreenPowerController.requestDayPreview(c)){fail(c,"secure_keyguard");return;}
   long restore=System.currentTimeMillis()+90000;alarm(c,RESTORE,712,restore);
   if(!s.edit().putBoolean("wasWifiEnabled",true).putLong("restoreAt",restore).putString("wifiStage","off").commit())throw new IOException();
   if(!wifi(c).setWifiEnabled(false)){restoreWifi(c);return;}
   alarm(c,SAMPLE,717,System.currentTimeMillis()+25000);
  }catch(Exception e){if(s.getLong("restoreAt",0)>0)restoreWifi(c);else fail(c,"alarm");}
 }
 static synchronized void restoreWifi(Context c){
  SharedPreferences s=state(c);if(s.getLong("restoreAt",0)<=0)return;cpu(c);
  try{
   int presentations=(int)Math.max(0,ScreenPowerController.state(c).getLong("presentationCount",0)-s.getLong("presentationStart",ScreenPowerController.state(c).getLong("presentationCount",0)));
   s.edit().putInt("offlinePresentations",presentations).commit();
   if(s.getBoolean("wasWifiEnabled",false)&&!wifi(c).setWifiEnabled(true)){retryRestore(c);return;}
   s.edit().putString("wifiStage","restoring").commit();retryRestore(c);
   alarm(c,REPORT,713,System.currentTimeMillis()+20000);
   if(wifi(c).getWifiState()==WifiManager.WIFI_STATE_ENABLED)wifiChanged(c,WifiManager.WIFI_STATE_ENABLED);
  }catch(Exception e){try{retryRestore(c);}catch(Exception ignored){}s.edit().putString("code","network").commit();}
 }
 static synchronized void wifiChanged(Context c,int value){
  SharedPreferences s=state(c);if(!"wifi_cycle".equals(s.getString("action","")))return;
  if(value==WifiManager.WIFI_STATE_DISABLED&&"off".equals(s.getString("wifiStage",""))){s.edit().putLong("wifiOffAt",System.currentTimeMillis()).putLong("presentationStart",ScreenPowerController.state(c).getLong("presentationCount",0)).commit();}
  else if(value==WifiManager.WIFI_STATE_ENABLED&&"restoring".equals(s.getString("wifiStage",""))){s.edit().putLong("wifiOnAt",System.currentTimeMillis()).putLong("restoreAt",0).putString("wifiStage","done").commit();if(s.getLong("wifiOffAt",0)>0&&s.getLong("wifiOnAt",0)>s.getLong("wifiOffAt",0)&&s.getInt("offlinePresentations",0)>0&&s.getInt("offlineBacklight",-1)>0)complete(c);else fail(c,"network");}
 }
 private static int bootCount(Context c){try{return android.provider.Settings.Global.getInt(c.getContentResolver(),"boot_count",-1);}catch(Exception ignored){return -1;}}
 static void recover(Context c){
  SharedPreferences s=state(c);
  if(s.getLong("restoreAt",0)>0)restoreWifi(c);
  else if("reboot".equals(s.getString("action",""))&&"started".equals(s.getString("state",""))){
   long before=s.getLong("rebootUptime",-1);int prior=s.getInt("rebootBootCount",-1),now=bootCount(c);
   if("invoked".equals(s.getString("rebootPhase",""))&&((before>0&&SystemClock.elapsedRealtime()<before)||(prior>=0&&now>prior)))complete(c);else fail(c,"internal");
  }else maintain(c);
 }
 static void maintain(Context c){
  SharedPreferences s=state(c);long now=System.currentTimeMillis();
  if(s.getLong("restoreAt",0)>0&&now>=s.getLong("restoreAt",0))restoreWifi(c);
  else if("started".equals(s.getString("state",""))&&("alarm_probe".equals(s.getString("action",""))||"power_cycle".equals(s.getString("action","")))&&s.getLong("finishAt",Long.MAX_VALUE)<=now)finishProbe(c);
 }
 static boolean matches(Context c,Intent intent){String id=intent.getStringExtra("verificationId");return id!=null&&id.equals(state(c).getString("id",""));}
 static void invokeReboot(Context c){
  SharedPreferences s=state(c);if(!BuildConfig.DEBUG||!"reboot".equals(s.getString("action",""))||!"started".equals(s.getString("state",""))||!"queued".equals(s.getString("rebootPhase","")))return;
  try{
   DevicePolicyManager owner=(DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE);if(Build.VERSION.SDK_INT<24||!owner.isDeviceOwnerApp(c.getPackageName())){fail(c,"unsupported");return;}
   if(!s.edit().putString("rebootPhase","invoked").putLong("rebootUptime",SystemClock.elapsedRealtime()).putInt("rebootBootCount",bootCount(c)).commit()){fail(c,"internal");return;}
   if(Build.VERSION.SDK_INT>=24)owner.reboot(new ComponentName(c,FrameAdminReceiver.class));
  }catch(Exception e){fail(c,"internal");}
 }
 static void sampleOffline(Context c){if(state(c).getLong("restoreAt",0)>0&&wifi(c).getWifiState()==WifiManager.WIFI_STATE_DISABLED)state(c).edit().putInt("offlineBacklight",backlight()).commit();}
 private static void retryRestore(Context c){SharedPreferences s=state(c);int retries=Math.min(4,s.getInt("restoreRetries",0));s.edit().putInt("restoreRetries",retries+1).commit();alarm(c,RESTORE,712,System.currentTimeMillis()+Math.min(300000,30000L*(1L<<retries)));}
 private static int backlight(){try(BufferedReader r=new BufferedReader(new FileReader("/sys/class/backlight/rk28_bl/actual_brightness"))){int value=Integer.parseInt(r.readLine().trim());return value>=0&&value<=255?value:-1;}catch(Exception e){return -1;}}
 static void addEvidence(Context c,JSONObject value)throws Exception{
  SharedPreferences s=state(c);String radio="unknown";try{int state=wifi(c).getWifiState();radio=state==WifiManager.WIFI_STATE_ENABLED?"on":state==WifiManager.WIFI_STATE_DISABLED?"off":state==WifiManager.WIFI_STATE_UNKNOWN?"unknown":"changing";}catch(Exception ignored){}
  value.put("verificationId",s.getString("id",""));value.put("verificationAction",s.getString("action","none"));value.put("verificationState",s.getString("state","idle"));value.put("verificationCode",s.getString("code","none"));value.put("verificationStartedAt",s.getLong("startedAt",0));value.put("verificationCompletedAt",s.getLong("completedAt",0));value.put("verificationWifiState",radio);value.put("verificationRestoreAt",s.getLong("restoreAt",0));value.put("verificationWifiOffAt",s.getLong("wifiOffAt",0));value.put("verificationWifiOnAt",s.getLong("wifiOnAt",0));value.put("verificationOfflinePresentations",s.getInt("offlinePresentations",0));value.put("verificationOfflineBacklight",s.getInt("offlineBacklight",-1));
 }
}
