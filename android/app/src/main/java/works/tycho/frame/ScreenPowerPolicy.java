package works.tycho.frame;

/** No display-off transition is allowed without a tested, armed recovery path. */
final class ScreenPowerPolicy {
 static String block(long now,long wakeAt,boolean verified,boolean granted,boolean secure){
  if(!verified||wakeAt<=now)return "alarm";
  if(!granted)return "missing_policy";
  if(secure)return "secure_keyguard";
  return "none";
 }
}
