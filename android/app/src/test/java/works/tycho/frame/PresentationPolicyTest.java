package works.tycho.frame;

import org.junit.Test;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import static org.junit.Assert.*;

public class PresentationPolicyTest {
    private long utc(String value) throws Exception {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        return parser.parse(value).getTime();
    }
    @Test public void onlyActualValidMonthsHaveLabels() {
        assertEquals("September 2021", PresentationPolicy.monthLabel("2021-09"));
        for (String missing : new String[]{null, "", "unknown", "2021-13", "2021-00", "0000-09", "2021-9", "2021-09-01"}) assertEquals("", PresentationPolicy.monthLabel(missing));
    }
    @Test public void nightBoundariesUseNewYorkAndResumeAtEight() throws Exception {
        assertFalse(PresentationPolicy.isNight(utc("2026-10-03 01:59"), true, 22, 8));
        assertTrue(PresentationPolicy.isNight(utc("2026-10-03 02:00"), true, 22, 8));
        assertTrue(PresentationPolicy.isNight(utc("2026-10-03 11:59"), true, 22, 8));
        assertFalse(PresentationPolicy.isNight(utc("2026-10-03 12:00"), true, 22, 8));
    }
    @Test public void daylightSavingDoesNotStrandMorningWake() throws Exception {
        assertTrue(PresentationPolicy.isNight(utc("2026-03-08 06:59"), true, 22, 8));
        assertTrue(PresentationPolicy.isNight(utc("2026-03-08 07:00"), true, 22, 8));
        assertFalse(PresentationPolicy.isNight(utc("2026-03-08 12:00"), true, 22, 8));
        assertFalse(PresentationPolicy.isNight(utc("2026-12-01 13:00"), true, 22, 8));
    }
    @Test public void configuredTimezoneAndInvalidZoneAreExplicit() throws Exception {
        long time = utc("2026-10-03 02:00");
        assertTrue(PresentationPolicy.isNight(time,true,22,8,"America/New_York"));
        assertFalse(PresentationPolicy.isNight(time,true,22,8,"America/Los_Angeles"));
        assertFalse(PresentationPolicy.isNight(time,true,22,8,"Invalid/Place"));
        assertTrue(PresentationPolicy.validTimezone("UTC"));
    }
    @Test public void disabledAndSameHourSchedulesRemainVisible() throws Exception {
        assertFalse(PresentationPolicy.isNight(utc("2026-10-03 03:00"), false, 22, 8));
        assertFalse(PresentationPolicy.isNight(utc("2026-10-03 03:00"), true, 8, 8));
        assertTrue(PresentationPolicy.isNight(utc("2026-10-03 16:00"), true, 10, 17));
    }
}
