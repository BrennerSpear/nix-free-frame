package works.tycho.frame;

import android.content.*;
import android.os.*;

/** App-private fixed alarms; system time changes only rebuild the local schedule. */
public class ScreenPowerReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  String action=intent.getAction();
  if(!ScreenPowerController.WAKE.equals(action)&&!ScreenPowerController.PROBE.equals(action)&&!ScreenPowerController.MAINTAIN.equals(action)&&!Intent.ACTION_TIME_CHANGED.equals(action)&&!Intent.ACTION_TIMEZONE_CHANGED.equals(action))return;
  PowerManager.WakeLock work=((PowerManager)context.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"frame:power-maintenance");work.acquire(30000);
  if(ScreenPowerController.WAKE.equals(action)||ScreenPowerController.PROBE.equals(action)){
   ScreenPowerController.wake(context,ScreenPowerController.PROBE.equals(action));ScreenPowerController.resetSchedule(context);
  }else if(ScreenPowerController.MAINTAIN.equals(action)){
   ScreenPowerController.resetSchedule(context);
   context.startService(new Intent(context,UpdateService.class));
   if(ScreenPowerController.night(context))ScreenPowerController.reconcile(context,true);else if(!ScreenPowerController.interactive(context))ScreenPowerController.wake(context,false);
  }else if(Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)){
   ScreenPowerController.resetSchedule(context);
   if(!ScreenPowerController.night(context)&&!ScreenPowerController.interactive(context))ScreenPowerController.wake(context,false);
  }else return;
  FrameEvidence.report(context);
  // A bounded CPU lease covers asynchronous receipts and the maintenance service handoff.
 }
}
