package works.tycho.frame;
import android.content.*;
/** Shell-only optional check; regular checks come from the active slideshow. */
public class UpdateCheckReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){if("works.tycho.frame.CHECK_UPDATE".equals(intent.getAction()))context.startService(new Intent(context,UpdateService.class));}
}
