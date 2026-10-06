package works.tycho.frame;
import android.app.admin.DeviceAdminReceiver;
import android.content.*;

/** Only screen locking is requested; no password, wipe, or restriction policies. */
public class FrameAdminReceiver extends DeviceAdminReceiver {
 public void onEnabled(Context context,Intent intent){ScreenPowerController.resetSchedule(context);FrameEvidence.report(context);}
}
