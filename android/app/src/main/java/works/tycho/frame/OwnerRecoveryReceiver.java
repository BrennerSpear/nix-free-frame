package works.tycho.frame;
import android.app.admin.DevicePolicyManager;
import android.content.*;

/** Manifest DUMP permission restricts recovery to authorized shell/system callers. */
public class OwnerRecoveryReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  if(!"works.tycho.frame.CLEAR_OWNER".equals(intent.getAction()))return;
  try{
   DevicePolicyManager dpm=(DevicePolicyManager)context.getSystemService(Context.DEVICE_POLICY_SERVICE);
   if(dpm.isDeviceOwnerApp(context.getPackageName()))dpm.clearDeviceOwnerApp(context.getPackageName());
   setResultCode(dpm.isDeviceOwnerApp(context.getPackageName())?1:0);
   setResultData(dpm.isDeviceOwnerApp(context.getPackageName())?"owner-still-active":"owner-cleared-or-absent");
  }catch(Exception e){setResultCode(1);setResultData("owner-clear-failed");}
 }
}
