package com.dilaw.mohe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Says at startup which time zones the timestamps pass through (review G5,
 * plan W4-04 and WP-11).
 *
 * <p>An {@code Instant} column is written in UTC whatever the JVM's zone:
 * Hibernate binds it as a UTC timestamp ({@code InstantStoredAsUtcTest} pins
 * that). A {@code LocalDateTime} column is written as the wall-clock time it
 * holds, and a database default such as {@code SYSDATETIME()} as the SQL
 * Server's own clock. When the JVM, the database and {@code mohe.report.timezone}
 * disagree, the second and third kinds do not line up with the first, which is
 * how approval times once read three hours off (A3). Knowing the three zones is
 * the first step on any server; this writes them to the log once.
 *
 * <p>Never fails the boot: a database that cannot answer is reported as such.
 */
@Component
public class TimeZoneStartupReport {

    private static final Logger log = LoggerFactory.getLogger(TimeZoneStartupReport.class);

    private final JdbcTemplate jdbc;
    private final String reportZone;

    public TimeZoneStartupReport(JdbcTemplate jdbc,
                                 @Value("${mohe.report.timezone:Asia/Riyadh}") String reportZone) {
        this.jdbc = jdbc;
        this.reportZone = reportZone;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        ZoneId jvm = ZoneId.systemDefault();
        int jvmOffset = ZonedDateTime.now(jvm).getOffset().getTotalSeconds() / 60;
        Integer dbOffset = databaseOffsetMinutes();
        String db = dbOffset == null ? "unknown" : "UTC" + format(dbOffset);
        if (dbOffset != null && dbOffset != jvmOffset) {
            log.warn("Time zones: JVM {} (UTC{}), SQL Server {}, reports {}. The JVM and the database clocks "
                    + "differ, so LocalDateTime values and database-default timestamps do not line up; "
                    + "Instant values are stored in UTC either way.", jvm, format(jvmOffset), db, reportZone);
        } else {
            log.info("Time zones: JVM {} (UTC{}), SQL Server {}, reports {}. Instant values are stored in UTC.",
                    jvm, format(jvmOffset), db, reportZone);
        }
    }

    /** Minutes the SQL Server clock is ahead of UTC, or null when it cannot be read. */
    Integer databaseOffsetMinutes() {
        try {
            return jdbc.queryForObject("SELECT DATEDIFF(MINUTE, SYSUTCDATETIME(), SYSDATETIME())", Integer.class);
        } catch (RuntimeException e) {
            log.debug("Could not read the SQL Server time zone: {}", e.toString());
            return null;
        }
    }

    static String format(int minutes) {
        int abs = Math.abs(minutes);
        return (minutes < 0 ? "-" : "+") + String.format("%02d:%02d", abs / 60, abs % 60);
    }
}
