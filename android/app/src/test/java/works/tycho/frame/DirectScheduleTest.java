package works.tycho.frame;
import org.junit.Test;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import static org.junit.Assert.*;
public class DirectScheduleTest {
 private long time(String v)throws Exception{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm");f.setTimeZone(TimeZone.getTimeZone("America/New_York"));return f.parse(v).getTime();}
 @Test public void springAndFallUseLocalNine()throws Exception{
  assertEquals(23*3600000L,DirectSchedule.latestSlot(time("2026-03-08 10:00"),9,0,"America/New_York")-DirectSchedule.latestSlot(time("2026-03-07 10:00"),9,0,"America/New_York"));
  assertEquals(25*3600000L,DirectSchedule.latestSlot(time("2026-11-01 10:00"),9,0,"America/New_York")-DirectSchedule.latestSlot(time("2026-10-31 10:00"),9,0,"America/New_York"));
 }
 @Test public void beforeSpringGapUsesPreviousDaysRequestedHour()throws Exception{assertEquals(time("2026-03-07 02:30"),DirectSchedule.latestSlot(time("2026-03-08 01:00"),2,30,"America/New_York"));}
 @Test public void catchesMissedDayAndBoundsRetries()throws Exception{
  long now=time("2026-03-08 08:00");assertTrue(DirectSchedule.due(now,0,0,0,9,0,"America/New_York"));
  assertFalse(DirectSchedule.due(now,0,now-60000,1,9,0,"America/New_York"));
  assertTrue(DirectSchedule.due(now,0,now-6*3600000L,16,9,0,"America/New_York"));
  assertFalse(DirectSchedule.due(now,time("2026-03-07 09:01"),0,0,9,0,"America/New_York"));
 }
 @Test public void actualCaptureDatesOnly(){
  assertEquals("2024-02",PhotoNormalizer.captureMonth("2024:02:29 12:04:05"));
  assertEquals("",PhotoNormalizer.captureMonth("2023:02:29 12:04:05"));assertEquals("",PhotoNormalizer.captureMonth(null));assertEquals("",PhotoNormalizer.captureMonth("2024-02-29"));
 }
 @Test public void captureMonthDoesNotApplyDeviceDstRules(){
  TimeZone original=TimeZone.getDefault();
  try{
   TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
   assertEquals("2024-03",PhotoNormalizer.captureMonth("2024:03:10 02:30:00"));
   assertEquals("",PhotoNormalizer.captureMonth("2024:02:30 02:30:00"));
  }finally{TimeZone.setDefault(original);}
 }
 @Test public void allExifOrientationsTransformAsExpected(){
  float[][] expected={{2,3},{-2,3},{-2,-3},{2,-3},{3,2},{-3,2},{-3,-2},{3,-2}};
  for(int orientation=1;orientation<=8;orientation++){float[] m=PhotoNormalizer.orientationMatrix(orientation);assertEquals(expected[orientation-1][0],m[0]*2+m[1]*3,0);assertEquals(expected[orientation-1][1],m[3]*2+m[4]*3,0);}
 }
}
