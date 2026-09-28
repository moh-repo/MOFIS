package com.dilaw.mohe.dto.dashboard;

import com.dilaw.mohe.dto.SimpleDataPointDto;

import java.util.List;

/**
 * The Students section of the landing page, in the caller's country scope.
 *
 * @param total         non-deleted students
 * @param active        students whose study status CODE is ACTIVE
 * @param newStatus     students whose study status CODE is NEW (a status, not an intake figure)
 * @param suspended     students whose study status CODE is SUSPENDED
 * @param notActive     {@code total - active}
 * @param newThisMonth  students whose record was created this month (intake)
 * @param monthly       intake per month over the {@code DASHBOARD.MONTHS_WINDOW} parameter, oldest first
 * @param byDelegationYear students per delegation year over the DASHBOARD.YEARS_WINDOW
 *                         parameter, oldest first, ending at the newest MOHE_QUARTER year
 * @param byStatus      students per study status, with the status row's names and colour
 */
public record DashboardStudentsDto(
        long total,
        long active,
        long newStatus,
        long suspended,
        long notActive,
        long newThisMonth,
        List<MonthPointDto> monthly,
        List<SimpleDataPointDto> byDelegationYear,
        List<CodeCountDto> byStatus) {
}
