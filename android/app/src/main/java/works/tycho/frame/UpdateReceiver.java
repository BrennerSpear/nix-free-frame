package works.tycho.frame;
import android.content.*;
import android.content.pm.PackageInstaller;

public class UpdateReceiver extends BroadcastReceiver {
 public void onReceive(Context context,Intent intent){
  Intent work=new Intent(context,UpdateService.class);
  if("works.tycho.frame.INSTALL_RESULT".equals(intent.getAction())){
   work.setAction(intent.getAction());
   work.putExtra("status",intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE));
   work.putExtra("session",intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1));
  }else if(!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;
  context.startService(work);
  if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){ScreenPowerController.resetSchedule(context);context.startActivity(new Intent(context,FrameActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
 }
}
