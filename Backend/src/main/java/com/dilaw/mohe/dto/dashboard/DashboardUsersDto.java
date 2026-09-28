package com.dilaw.mohe.dto.dashboard;

import java.util.List;

/**
 * The Users section of the landing page.
 *
 * @param total        non-deleted accounts — of the caller's countries when {@code scoped},
 *                     otherwise every account in the system (ministry callers)
 * @param newThisMonth accounts created this month, same rule
 * @param monthly      registrations per month over the {@code DASHBOARD.MONTHS_WINDOW} parameter, oldest first
 * @param scoped       {@code false} means the ministry-wide figure; the page labels it so
 */
public record DashboardUsersDto(
        long total,
        long newThisMonth,
        List<MonthPointDto> monthly,
        boolean scoped) {
}
