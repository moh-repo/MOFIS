package com.dilaw.mohe.dto.dashboard;

import java.util.List;

/**
 * The dashboard sections the caller may read, so the page renders exactly what
 * the section endpoints will serve.
 *
 * @param sections section codes ({@code DASHBOARD_STUDENTS}, …), in display order
 * @param all      the caller holds {@code DASHBOARD.VIEW} (or is an administrator)
 */
public record DashboardSectionsDto(List<String> sections, boolean all) {
}
