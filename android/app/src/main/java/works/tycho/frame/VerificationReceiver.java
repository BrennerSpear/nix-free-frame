package works.tycho.frame;

import android.content.*;
import android.net.wifi.WifiManager;

/** Private fixed recovery alarms and the protected system Wi-Fi state broadcast. */
public class VerificationReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  String action=intent.getAction();
  if(!WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)&&!VerificationController.matches(context,intent))return;
  if(VerificationController.PROBE.equals(action))VerificationController.beginWifiCycle(context);
  else if(VerificationController.RESTORE.equals(action))VerificationController.restoreWifi(context);
  else if(VerificationController.FINISH.equals(action))VerificationController.finishProbe(context);
  else if(VerificationController.REBOOT.equals(action))VerificationController.invokeReboot(context);
  else if(VerificationController.SAMPLE.equals(action))VerificationController.sampleOffline(context);
  else if(VerificationController.REPORT.equals(action)){FrameEvidence.report(context);context.startService(new Intent(context,UpdateService.class));}
  else if(WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action))VerificationController.wifiChanged(context,intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,WifiManager.WIFI_STATE_UNKNOWN));
 }
}
