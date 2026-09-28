package com.dilaw.mohe.codes;

/** LU_INFO_REQUEST_STATUS (V073): StudentInfoRequest.status. */
@CodeList(value = "info-request-status", mode = CodeList.Mode.EXACT)
public final class InfoRequestStatus {

    private InfoRequestStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String OVERDUE = "OVERDUE";
    public static final String RESPONDED = "RESPONDED";
    public static final String CANCELLED = "CANCELLED";
}
