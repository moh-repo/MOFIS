package com.dilaw.mohe.codes;

/** LU_CHANGE_REQUEST_STATUS: the codes StudentChangeRequestService writes; MODIFIED, IN_PROGRESS and EXECUTED exist in the table but no Java depends on them. */
@CodeList(value = "change-request-status", mode = CodeList.Mode.EXACT)
public final class ChangeRequestStatus {

    private ChangeRequestStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
}
