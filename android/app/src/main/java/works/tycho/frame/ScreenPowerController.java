package works.tycho.frame;

import android.app.*;
import android.app.admin.*;
import android.content.*;
import android.os.*;
import android.view.WindowManager;

/** Fixed screen transitions. Wake is armed before sleep; absent policy keeps the black fallback. */
final class ScreenPowerController {
 static final String WAKE="works.tycho.frame.SCHEDULED_WAKE",MAINTAIN="works.tycho.frame.POWER_MAINTENANCE",PROBE="works.tycho.frame.POWER_PROBE";
 static SharedPreferences state(Context c){return c.getSharedPreferences("screenPower",Context.MODE_PRIVATE);}
 static SharedPreferences settings(Context c){return c.getSharedPreferences("FrameActivity",Context.MODE_PRIVATE);}
 static boolean enabled(Context c){return state(c).getBoolean("nightOffEnabled",true);}
 static boolean secure(Context c){return ((KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardSecure();}
 static boolean interactive(Context c){return ((PowerManager)c.getSystemService(Context.POWER_SERVICE)).isInteractive();}
 static boolean granted(Context c){try{return ((DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE)).hasGrantedPolicy(new ComponentName(c,FrameAdminReceiver.class),DeviceAdminInfo.USES_POLICY_FORCE_LOCK);}catch(Exception e){return false;}}
 static boolean temporaryDay(Context c){return BuildConfig.DEBUG&&state(c).getLong("previewDayUntil",0)>System.currentTimeMillis();}
 static boolean night(Context c){SharedPreferences s=settings(c);return !temporaryDay(c)&&PresentationPolicy.isNight(System.currentTimeMillis(),s.getBoolean("nightEnabled",true),s.getInt("nightStart",22),s.getInt("nightEnd",8),s.getString("nightTimezone","America/New_York"));}
 private static PendingIntent pending(Context c,String action,int code){return PendingIntent.getBroadcast(c,code,new Intent(c,ScreenPowerReceiver.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));}
 private static void alarm(Context c,String action,int code,long at){AlarmManager manager=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(Build.VERSION.SDK_INT>=23)manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pending(c,action,code));else manager.setExact(AlarmManager.RTC_WAKEUP,at,pending(c,action,code));}
 static boolean schedule(Context c){
  try{
   SharedPreferences s=settings(c);long wake=NightWakeSchedule.nextWake(System.currentTimeMillis(),s.getBoolean("nightEnabled",true),s.getInt("nightStart",22),s.getInt("nightEnd",8),s.getString("nightTimezone","America/New_York"));
   if(wake>0)alarm(c,WAKE,701,wake);else ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pending(c,WAKE,701));
   // Do not slide the existing maintenance deadline on every foreground tick.
   long maintenance=state(c).getLong("maintenanceAt",0),now=System.currentTimeMillis();
   if(maintenance<=now||maintenance>now+16*60*1000L){maintenance=now+15*60*1000L;alarm(c,MAINTAIN,702,maintenance);state(c).edit().putLong("maintenanceAt",maintenance).apply();}
   state(c).edit().putLong("nextWakeAt",wake).apply();return true;
  }catch(Exception e){state(c).edit().putString("sleepCode","alarm").apply();return false;}
 }
 static void reconcile(Context c,boolean effectiveNight){
  if(!schedule(c))return;
  if(!effectiveNight||!enabled(c))return;
  sleep(c,state(c).getLong("nextWakeAt",0));
 }
 private static boolean sleep(Context c,long wakeAt){
  String failure=ScreenPowerPolicy.block(System.currentTimeMillis(),wakeAt,state(c).getBoolean("wakeVerified",false),granted(c),secure(c));
  if(!"none".equals(failure)){state(c).edit().putString("sleepCode",failure).apply();return false;}
  if(!interactive(c))return true;
  try{
   PowerManager.WakeLock observation=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"frame:sleep-observation");observation.acquire(30000);
   if(c instanceof Activity)((Activity)c).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
   ((DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE)).lockNow();
   state(c).edit().putLong("lastSleepAt",System.currentTimeMillis()).putString("sleepCode","locked").commit();
   new Handler(Looper.getMainLooper()).postDelayed(()->{
    if(interactive(c)){state(c).edit().putString("sleepCode","timeout").apply();if(c instanceof Activity)((Activity)c).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    FrameEvidence.report(c);
   },2000);
   return true;
  }catch(Exception e){state(c).edit().putString("sleepCode","unsupported").apply();if(c instanceof Activity)((Activity)c).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);return false;}
 }
 static void wake(Context c,boolean probe){
  if(probe&&BuildConfig.DEBUG)state(c).edit().putLong("previewDayUntil",System.currentTimeMillis()+60000).commit();
  PowerManager.WakeLock lock=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK|PowerManager.ACQUIRE_CAUSES_WAKEUP,"frame:scheduled-wake");
  try{
   lock.acquire(15000);
   state(c).edit().putLong("lastWakeAt",System.currentTimeMillis()).putLong("wakeCount",state(c).getLong("wakeCount",0)+1).commit();
   c.startActivity(new Intent(c,FrameActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
   if(probe&&BuildConfig.DEBUG)new Handler(Looper.getMainLooper()).postDelayed(()->{
    android.view.Display display=((android.hardware.display.DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(android.view.Display.DEFAULT_DISPLAY);
    if(interactive(c)&&display!=null&&display.getState()==android.view.Display.STATE_ON&&FrameActivity.isResumedForEvidence())state(c).edit().putBoolean("wakeVerified",true).commit();
    FrameEvidence.report(c);
   },5000);
  }catch(Exception e){state(c).edit().putString("sleepCode","unsupported").apply();}
  // Timeout is intentional: the resumed HOME's KEEP_SCREEN_ON takes over.
 }
 static void resetSchedule(Context c){state(c).edit().putLong("maintenanceAt",0).apply();schedule(c);}
 static void addEvidence(Context c,org.json.JSONObject value)throws org.json.JSONException{
  SharedPreferences s=state(c);
  value.put("powerWakeVerified",s.getBoolean("wakeVerified",false));value.put("powerInteractive",interactive(c));value.put("powerForceLockGranted",granted(c));value.put("powerKeyguardSecure",secure(c));value.put("powerNightOffEnabled",enabled(c));
  android.view.Display display=((android.hardware.display.DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(android.view.Display.DEFAULT_DISPLAY);
  int mode=display==null?android.view.Display.STATE_UNKNOWN:display.getState();String name=mode==android.view.Display.STATE_OFF?"off":mode==android.view.Display.STATE_ON?"on":mode==android.view.Display.STATE_DOZE||mode==android.view.Display.STATE_DOZE_SUSPEND?"doze":"unknown";value.put("powerDisplayState",name);
  value.put("powerNextWakeAt",s.getLong("nextWakeAt",0));value.put("powerLastSleepAt",s.getLong("lastSleepAt",0));value.put("powerLastWakeAt",s.getLong("lastWakeAt",0));value.put("powerWakeCount",s.getLong("wakeCount",0));value.put("powerPresentationCount",s.getLong("presentationCount",0));
  int timeout=-1;try{timeout=android.provider.Settings.System.getInt(c.getContentResolver(),android.provider.Settings.System.SCREEN_OFF_TIMEOUT,-1);}catch(Exception ignored){}value.put("powerScreenTimeout",Math.max(-1,timeout));
  int plugged=-1;try{plugged=android.provider.Settings.Global.getInt(c.getContentResolver(),android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN,-1);}catch(Exception ignored){}value.put("powerStayOnPlugged",plugged>=0&&plugged<=7?plugged:-1);value.put("powerSleepCode",s.getString("sleepCode","none"));
 }
 static boolean requestDayPreview(Context c){
  if(!BuildConfig.DEBUG||secure(c))return false;
  state(c).edit().putLong("previewDayUntil",System.currentTimeMillis()+90000).commit();wake(c,false);return true;
 }
 static boolean requestAlarmProbe(Context c){
  if(!BuildConfig.DEBUG)return false;
  try{long at=System.currentTimeMillis()+90000;alarm(c,PROBE,703,at);state(c).edit().putLong("probeWakeAt",at).commit();return true;}catch(Exception e){state(c).edit().putString("sleepCode","alarm").apply();return false;}
 }
 static boolean requestSleepCycle(Context c){
  if(!BuildConfig.DEBUG)return false;
  if(!state(c).getBoolean("wakeVerified",false)){state(c).edit().putString("sleepCode","alarm").apply();return false;}
  if(secure(c)){state(c).edit().putString("sleepCode","secure_keyguard").apply();return false;}
  if(!granted(c)){state(c).edit().putString("sleepCode","missing_policy").apply();return false;}
  if(!requestAlarmProbe(c))return false;
  return sleep(c,state(c).getLong("probeWakeAt",0));
 }
}
