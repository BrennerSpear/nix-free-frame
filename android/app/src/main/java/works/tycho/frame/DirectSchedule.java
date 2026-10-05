package works.tycho.frame;

import java.util.Calendar;
import java.util.TimeZone;

/** Compare local calendar slots rather than adding 24 hours across DST. */
public final class DirectSchedule {
 public static long latestSlot(long now,int hour,int minute,String zone){
  if(hour<0||hour>23||minute<0||minute>59||!PresentationPolicy.validTimezone(zone))throw new IllegalArgumentException("Invalid schedule");
  Calendar c=Calendar.getInstance(TimeZone.getTimeZone(zone));c.setTimeInMillis(now);
  c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
  if(c.getTimeInMillis()>now){c.setTimeInMillis(now);c.add(Calendar.DATE,-1);c.set(Calendar.HOUR_OF_DAY,hour);c.set(Calendar.MINUTE,minute);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);}
  return c.getTimeInMillis();
 }
 public static boolean due(long now,long success,long attempt,int failures,int hour,int minute,String zone){
  if(success>=latestSlot(now,hour,minute,zone)&&success<=now)return false;
  long retry=Math.min(6*60*60*1000L,5*60*1000L*(1L<<Math.min(7,Math.max(0,failures-1))));
  return attempt<=0||attempt>now||now-attempt>=retry;
 }
}
