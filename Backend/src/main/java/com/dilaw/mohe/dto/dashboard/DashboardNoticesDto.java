package com.dilaw.mohe.dto.dashboard;

import java.util.List;

/**
 * System notices ({@code MOHE_ALERTS.alerts}) for today and yesterday, with
 * type, priority and status resolved through the code lists.
 *
 * <p>{@code countrySpecific} is always {@code false}: the table carries no country
 * column, so these are ministry-wide notices and the page says so. The
 * country-scoped student alert cases are a separate section.
 */
public record DashboardNoticesDto(
        List<NoticeDto> today,
        List<NoticeDto> yesterday,
        boolean countrySpecific) {
}
