package com.dilaw.mohe.service;

/**
 * A scheduled report that cannot run, and will not run on the next tick either.
 *
 * <p>The scheduler's failure handling was written for the transient case — the
 * SMTP host is down, the database is briefly unreachable — where leaving
 * {@code NEXT_RUN_DATE} alone and trying again in fifteen minutes is exactly
 * right. It is wrong for the other kind. A schedule whose owner has been deleted,
 * whose owner has no country scope, or that names a report type the scheduler
 * cannot run, will fail in the same way every fifteen minutes for as long as the
 * application is up.
 *
 * <p>That is not hypothetical: {@code SCHEDULED_REPORT} 220 on the development
 * database is owned by {@code SAMPLE_DATA}, a user that does not exist. Its
 * {@code NEXT_RUN_DATE} of 30 August 2026 is permanently in the past, so every
 * tick since has claimed it, failed, written a full stack trace, and left the
 * date exactly where it was.
 *
 * <p>Thrown by the configuration checks, this tells the scheduler that a retry
 * cannot help: the outcome is still recorded as {@code FAILED} — an administrator
 * must see that the schedule is broken — but the next run advances by the
 * schedule's own frequency, so it complains once a week rather than ninety-six
 * times a day, and it is logged as a warning with its message rather than as an
 * error with a stack trace nobody needs to read twice.
 */
public class ScheduleConfigurationException extends IllegalStateException {

    public ScheduleConfigurationException(String message) {
        super(message);
    }
}
