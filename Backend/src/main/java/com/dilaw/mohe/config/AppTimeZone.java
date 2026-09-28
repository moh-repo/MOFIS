package com.dilaw.mohe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The one time zone the application reasons about calendar dates in.
 *
 * <p>Built from {@code mohe.report.timezone}, the property the report generators
 * already stamp their dates with. Anything that turns a {@code LocalDate} into an
 * {@code Instant} (a "from"/"to" filter, "today" for an expiry window, a birth date)
 * used {@code ZoneId.systemDefault()} instead, so a server started in UTC drew every
 * day boundary three hours away from the one printed on the reports. Inject this
 * rather than calling {@code ZoneId.systemDefault()} or {@code LocalDate.now()}.
 */
@Component
public class AppTimeZone {

    private final ZoneId zone;

    public AppTimeZone(@Value("${mohe.report.timezone:Asia/Riyadh}") String timezone) {
        this.zone = ZoneId.of(timezone);
    }

    public ZoneId zone() {
        return zone;
    }

    /** Today's date in the application zone. */
    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /** First instant of {@code date} in the application zone. */
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** Last instant (to the millisecond) of {@code date}, for inclusive "to" filters. */
    public Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1);
    }

    /** Calendar date of {@code instant} in the application zone; null in, null out. */
    public LocalDate toLocalDate(Instant instant) {
        return instant == null ? null : instant.atZone(zone).toLocalDate();
    }
}
