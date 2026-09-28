package com.dilaw.mohe.service;

/**
 * The sections of the landing page, in display order. Each is a page code the
 * UMS knows as {@code <code>.VIEW} (seeded by V070) and a gate constant in
 * {@code MoheSecurityExpressions}; {@code DASHBOARD.VIEW} opens all of them.
 *
 * <p>This enum is the single list that {@code DashboardController}, the
 * {@code /sections} answer and {@code DashboardScopeTest} share — a section added
 * here without a controller handler guarded by its authority fails the test.
 */
public enum DashboardSection {
    STUDENTS("DASHBOARD_STUDENTS"),
    FINANCE("DASHBOARD_FINANCE"),
    APPROVALS("DASHBOARD_APPROVALS"),
    ALERTS("DASHBOARD_ALERTS"),
    COUNTRIES("DASHBOARD_COUNTRIES"),
    USERS("DASHBOARD_USERS"),
    QUARTER("DASHBOARD_QUARTER"),
    NOTICES("DASHBOARD_NOTICES");

    /** The whole-dashboard authority. */
    public static final String ALL_AUTHORITY = "DASHBOARD.VIEW";

    private final String code;

    DashboardSection(String code) {
        this.code = code;
    }

    /** The page code the frontend guards with and the UMS groups by. */
    public String code() {
        return code;
    }

    /** The permission code that opens this section on its own. */
    public String viewAuthority() {
        return code + ".VIEW";
    }
}
