package works.tycho.frame;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context context, Intent intent) {
  if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {VerificationController.recover(context);ScreenPowerController.resetSchedule(context);context.startActivity(new Intent(context, FrameActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
 }
}
