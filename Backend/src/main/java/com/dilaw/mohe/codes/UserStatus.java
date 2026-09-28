package com.dilaw.mohe.codes;

/** LU_USER_STATUS: UMSCORE.Users.Status (UmsUserService lock and activate). */
@CodeList(value = "ums-user-status", mode = CodeList.Mode.EXACT)
public final class UserStatus {

    private UserStatus() {
    }

    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String LOCKED = "LOCKED";
}
