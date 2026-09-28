package com.dilaw.mohe.codes;

/** LU_INQUIRY_STATUS (V073): the portal inquiry states the controllers write. */
@CodeList(value = "inquiry-status", mode = CodeList.Mode.SUBSET)
public final class InquiryStatus {

    private InquiryStatus() {
    }

    public static final String PENDING = "PENDING";
    public static final String RESPONDED = "RESPONDED";
}
