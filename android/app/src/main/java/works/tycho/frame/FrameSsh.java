package works.tycho.frame;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.security.MessageDigest;

/** Starts only the reviewed local SSH script, once per slideshow process. */
final class FrameSsh {
 private static final String PERMISSION="com.termux.permission.RUN_COMMAND";
 private static final String CERTIFICATE="228fb2cfe90831c1499ec3ccaf61e96e8e1ce70766b9474672ce427334d41c42";
 private static boolean requested;
 private FrameSsh(){}
 @SuppressWarnings("deprecation")
 static synchronized void start(Context context){
  if(requested||context.checkCallingOrSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED)return;
  try{
   PackageInfo termux=context.getPackageManager().getPackageInfo("com.termux",PackageManager.GET_SIGNATURES);
   if(termux.signatures==null||termux.signatures.length!=1)return;
   Signature signature=termux.signatures[0];
   StringBuilder digest=new StringBuilder();
   for(byte value:MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))digest.append(String.format(java.util.Locale.US,"%02x",value&255));
   if(!CERTIFICATE.equals(digest.toString()))return;
   Intent intent=new Intent("com.termux.RUN_COMMAND");
   intent.setComponent(new ComponentName("com.termux","com.termux.app.RunCommandService"));
   intent.putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/home/.termux/boot/nix-frame-sshd");
   intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND",true);
   if(context.startService(intent)!=null)requested=true;
  }catch(Exception ignored){/* The slideshow remains available if SSH cannot start. */}
 }
}
