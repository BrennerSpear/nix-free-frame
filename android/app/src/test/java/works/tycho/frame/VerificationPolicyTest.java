package works.tycho.frame;
import org.junit.Test;
import static org.junit.Assert.*;
public class VerificationPolicyTest {
 @Test public void acceptsOnlyFixedActionsAndBoundedNonCapabilityIds(){
  for(String action:new String[]{"alarm_probe","power_cycle","wifi_cycle","reboot","day_preview"})assertTrue(VerificationPolicy.valid("fixture-1",action));
  for(String action:new String[]{"shell","https://example.test","wipe",null,""})assertFalse(VerificationPolicy.valid("fixture-1",action));
  for(String id:new String[]{null,"","secret/url","id with spaces",new String(new char[65]).replace('\0','a')})assertFalse(VerificationPolicy.valid(id,"alarm_probe"));
 }
}
