package works.tycho.frame;

import java.util.Calendar;
import java.util.TimeZone;

/** The next actual night-to-day transition, including skipped and repeated local hours. */
public final class NightWakeSchedule {
    private NightWakeSchedule() { }

    public static long nextWake(long now, boolean enabled, int startHour, int endHour, String timezone) {
        if (!enabled || startHour == endHour || startHour < 0 || startHour > 23
                || endHour < 0 || endHour > 23 || !PresentationPolicy.validTimezone(timezone)) return 0;
        Calendar local = Calendar.getInstance(TimeZone.getTimeZone(timezone));
        local.setTimeInMillis(now);
        boolean previousNight = isNight(local.get(Calendar.HOUR_OF_DAY), startHour, endHour);
        // Traverse real minutes, not ambiguous civil timestamps. End hours are minute-aligned;
        // the first daylight instant also covers an end hour skipped by a clock change.
        long remainder = now % 60000L;
        if (remainder < 0) remainder += 60000L;
        long candidate = now - remainder;
        // Three real days also cover a timezone that skips an entire civil date.
        for (int minute = 0; minute < 3 * 24 * 60; minute++) {
            if (candidate > Long.MAX_VALUE - 60000L) return 0;
            candidate += 60000L;
            local.setTimeInMillis(candidate);
            boolean night = isNight(local.get(Calendar.HOUR_OF_DAY), startHour, endHour);
            if (previousNight && !night) return candidate;
            previousNight = night;
        }
        return 0;
    }

    private static boolean isNight(int hour, int startHour, int endHour) {
        return startHour < endHour ? hour >= startHour && hour < endHour
                : hour >= startHour || hour < endHour;
    }
}
