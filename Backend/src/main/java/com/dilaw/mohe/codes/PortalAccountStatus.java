package com.dilaw.mohe.codes;

/** LU_STUDENT_PORTAL_ACCOUNT_STATUS (StudentUser, StudentUserAdminController). */
@CodeList(value = "portal-account-status", mode = CodeList.Mode.EXACT)
public final class PortalAccountStatus {

    private PortalAccountStatus() {
    }

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String LOCKED = "LOCKED";
}
