package works.tycho.frame;

import java.util.Calendar;
import java.util.TimeZone;

/** Presentation rules that do not depend on the frame's configured timezone. */
public final class PresentationPolicy {
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    public static String monthLabel(String value) {
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])") || value.startsWith("0000")) return "";
        return MONTHS[Integer.parseInt(value.substring(5)) - 1] + " " + value.substring(0, 4);
    }

    public static boolean isNight(long now, boolean enabled, int startHour, int endHour) {
        return isNight(now, enabled, startHour, endHour, "America/New_York");
    }

    public static boolean validTimezone(String timezone) {
        if (timezone == null) return false;
        for (String id : TimeZone.getAvailableIDs()) if (id.equals(timezone)) return true;
        return false;
    }

    public static boolean isNight(long now, boolean enabled, int startHour, int endHour, String timezone) {
        if (!validTimezone(timezone)) return false;
        if (!enabled || startHour == endHour || startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23) return false;
        Calendar local = Calendar.getInstance(TimeZone.getTimeZone(timezone));
        local.setTimeInMillis(now);
        int hour = local.get(Calendar.HOUR_OF_DAY);
        return startHour < endHour ? hour >= startHour && hour < endHour : hour >= startHour || hour < endHour;
    }
}
