package com.dilaw.mohe.codes;

/** LU_SCHEDULE_STATUS (V074): SCHEDULED_REPORT.SCHEDULE_STATUS — the scheduler runs ACTIVE schedules only. */
@CodeList(value = "schedule-status", mode = CodeList.Mode.EXACT)
public final class ScheduleStatus {

    private ScheduleStatus() {
    }

    public static final String ACTIVE = "ACTIVE";
    public static final String PAUSED = "PAUSED";
}
