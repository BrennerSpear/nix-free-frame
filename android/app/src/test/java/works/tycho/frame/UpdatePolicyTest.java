package works.tycho.frame;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;

public class UpdatePolicyTest {
 private final String sha="a".repeat(64);
 private interface Checked {void run()throws Exception;}
 private void rejected(String code,Checked action)throws Exception{
  try{action.run();fail("accepted invalid update");}catch(UpdatePolicy.Failure failure){assertEquals(code,failure.code);}
 }
 @Test public void restrictsMetadataOriginSizeAndDowngrades()throws Exception{
  URL base=new URL("http://192.0.2.20:3130/");
  assertEquals("/app-updates/"+sha+".apk",UpdatePolicy.manifest(1,UpdatePolicy.PACKAGE,4,1024,sha,"/app-updates/"+sha+".apk",base,3).getPath());
  rejected("package",()->UpdatePolicy.manifest(1,"another.app",4,1024,sha,"/app-updates/"+sha+".apk",base,3));
  rejected("version",()->UpdatePolicy.manifest(1,UpdatePolicy.PACKAGE,3,1024,sha,"/app-updates/"+sha+".apk",base,3));
  rejected("download",()->UpdatePolicy.manifest(1,UpdatePolicy.PACKAGE,4,UpdatePolicy.MAX_BYTES+1,sha,"/app-updates/"+sha+".apk",base,3));
  rejected("download",()->UpdatePolicy.manifest(1,UpdatePolicy.PACKAGE,4,1024,sha,"http://evil.example/app.apk",base,3));
  rejected("download",()->UpdatePolicy.sameOrigin(base,new URL("http://192.0.2.20:3131/")));
  rejected("download",()->UpdatePolicy.sameOrigin(base,new URL("http://user:password@192.0.2.20:3130/")));
 }
 @Test public void rejectsWrongPackageCertificateAndArchiveVersion()throws Exception{
  UpdatePolicy.archive(UpdatePolicy.PACKAGE,4,4,3,new String[]{"own-cert"},new String[]{"own-cert"});
  rejected("package",()->UpdatePolicy.archive("other.package",4,4,3,new String[]{"own-cert"},new String[]{"own-cert"}));
  rejected("signature",()->UpdatePolicy.archive(UpdatePolicy.PACKAGE,4,4,3,new String[]{"attacker-cert"},new String[]{"own-cert"}));
  rejected("signature",()->UpdatePolicy.archive(UpdatePolicy.PACKAGE,4,4,3,null,new String[]{"own-cert"}));
  rejected("version",()->UpdatePolicy.archive(UpdatePolicy.PACKAGE,5,4,3,new String[]{"own-cert"},new String[]{"own-cert"}));
 }
 @Test public void corruptOrTruncatedDownloadIsDeletedBeforeInstallation()throws Exception{
  File file=File.createTempFile("frame-update", ".apk");
  try{
   rejected("hash",()->UpdatePolicy.copy(new ByteArrayInputStream(new byte[]{1,2}),file,2,sha));assertFalse(file.exists());
   rejected("download",()->UpdatePolicy.copy(new ByteArrayInputStream(new byte[]{1}),file,2,sha));assertFalse(file.exists());
   rejected("download",()->UpdatePolicy.copy(new ByteArrayInputStream(new byte[]{1,2,3}),file,2,sha));assertFalse(file.exists());
   try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2});}
   String valid=CacheStore.digest(file);
   UpdatePolicy.copy(new ByteArrayInputStream(new byte[]{1,2}),file,2,valid);assertEquals(valid,CacheStore.digest(file));
  }finally{file.delete();}
 }
}
