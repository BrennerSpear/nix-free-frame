package works.tycho.frame;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenPowerPolicyTest {
 @Test public void everyMissingRecoveryPrerequisitePreventsSleep(){
  for(boolean armed:new boolean[]{false,true})for(boolean verified:new boolean[]{false,true})for(boolean granted:new boolean[]{false,true})for(boolean secure:new boolean[]{false,true}){
   boolean maySleep=armed&&verified&&granted&&!secure;
   assertEquals(maySleep,"none".equals(ScreenPowerPolicy.block(100,armed?101:100,verified,granted,secure)));
  }
 }
 @Test public void elapsedAlarmCannotAuthorizeSleep(){
  assertEquals("alarm",ScreenPowerPolicy.block(100,99,true,true,false));
  assertEquals("alarm",ScreenPowerPolicy.block(100,100,true,true,false));
  assertEquals("none",ScreenPowerPolicy.block(100,101,true,true,false));
 }
 @Test public void refusalExplainsRequiredPermissionOrSecureLock(){
  assertEquals("missing_policy",ScreenPowerPolicy.block(100,200,true,false,false));
  assertEquals("secure_keyguard",ScreenPowerPolicy.block(100,200,true,true,true));
 }
}
