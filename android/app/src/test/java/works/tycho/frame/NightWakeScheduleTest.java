package works.tycho.frame;

import org.junit.Test;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class NightWakeScheduleTest {
    private static final String ZONE = "America/New_York";
    private long time(String utc) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        return format.parse(utc).getTime();
    }
    private void transition(String now, String expected, int start, int end) throws Exception {
        long result = NightWakeSchedule.nextWake(time(now), true, start, end, ZONE);
        assertEquals(time(expected), result);
        assertTrue(result > time(now));
        assertTrue(PresentationPolicy.isNight(result - 1, true, start, end, ZONE));
        assertFalse(PresentationPolicy.isNight(result, true, start, end, ZONE));
    }
    @Test public void overnightAndDaytimeSchedulesUseNextRealEnd() throws Exception {
        transition("2026-01-06 04:00", "2026-01-06 13:00", 22, 8); // 23:00 -> 08:00.
        transition("2026-01-06 15:00", "2026-01-07 13:00", 22, 8); // Already daytime.
        transition("2026-01-06 15:00", "2026-01-06 22:00", 9, 17);
        transition("2026-01-06 23:00", "2026-01-07 22:00", 9, 17);
    }
    @Test public void exactEndNeverReturnsPastOrCurrentBoundary() throws Exception {
        transition("2026-01-06 13:00", "2026-01-07 13:00", 22, 8);
        long boundary = time("2026-01-06 13:00");
        assertEquals(boundary, NightWakeSchedule.nextWake(boundary - 1, true, 22, 8, ZONE));
        assertEquals(time("2026-01-07 13:00"), NightWakeSchedule.nextWake(boundary + 1, true, 22, 8, ZONE));
    }
    @Test public void normalEndHoursStayLocalAcrossSpringAndFall() throws Exception {
        long springBefore = NightWakeSchedule.nextWake(time("2026-03-07 05:00"), true, 22, 8, ZONE);
        long springAfter = NightWakeSchedule.nextWake(time("2026-03-08 05:00"), true, 22, 8, ZONE);
        assertEquals(time("2026-03-08 12:00"), springAfter);
        assertEquals(23 * 3600000L, springAfter - springBefore);
        long fallBefore = NightWakeSchedule.nextWake(time("2026-10-31 04:00"), true, 22, 8, ZONE);
        long fallAfter = NightWakeSchedule.nextWake(time("2026-11-01 04:00"), true, 22, 8, ZONE);
        assertEquals(time("2026-11-01 13:00"), fallAfter);
        assertEquals(25 * 3600000L, fallAfter - fallBefore);
    }
    @Test public void skippedEndWakesAtFirstActualDaytime() throws Exception {
        transition("2026-03-08 06:00", "2026-03-08 07:00", 22, 2); // 01:00 -> 03:00.
        transition("2026-03-08 06:00", "2026-03-09 07:00", 2, 3); // Entire night interval skipped.
    }
    @Test public void repeatedEndUsesFirstOccurrenceAndDoesNotInventSecondWake() throws Exception {
        transition("2026-11-01 04:00", "2026-11-01 05:00", 22, 1); // First 01:00, still EDT.
        transition("2026-11-01 05:30", "2026-11-02 06:00", 22, 1); // Both 01:00 hours are daytime.
        transition("2026-11-01 04:00", "2026-11-01 07:00", 22, 2); // 02:00 is after fallback.
    }
    @Test public void invalidAndDisabledSchedulesReturnZero() {
        assertEquals(0, NightWakeSchedule.nextWake(0, false, 22, 8, ZONE));
        assertEquals(0, NightWakeSchedule.nextWake(0, true, 8, 8, ZONE));
        assertEquals(0, NightWakeSchedule.nextWake(0, true, -1, 8, ZONE));
        assertEquals(0, NightWakeSchedule.nextWake(0, true, 22, 24, ZONE));
        assertEquals(0, NightWakeSchedule.nextWake(0, true, 22, 8, "not-a-zone"));
        assertEquals(0, NightWakeSchedule.nextWake(0, true, 22, 8, null));
    }
}
