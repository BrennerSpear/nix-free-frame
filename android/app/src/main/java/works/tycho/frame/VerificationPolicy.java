package works.tycho.frame;

/** Fixed debug operations, never shell commands, URLs, or caller-provided durations. */
final class VerificationPolicy {
 static boolean valid(String id,String action){
  return id!=null&&id.matches("[A-Za-z0-9_-]{1,64}")&&("alarm_probe".equals(action)||"power_cycle".equals(action)||"wifi_cycle".equals(action)||"reboot".equals(action)||"day_preview".equals(action));
 }
}
