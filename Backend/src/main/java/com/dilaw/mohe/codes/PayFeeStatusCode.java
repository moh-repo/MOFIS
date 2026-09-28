package com.dilaw.mohe.codes;

/** STUDY_PAY_FEE_IN_STATUS.CODE (V072): the cancelled fee status StudyUniversityFeeService excludes. */
@CodeList(value = "pay-fee-status", mode = CodeList.Mode.SUBSET)
public final class PayFeeStatusCode {

    private PayFeeStatusCode() {
    }

    public static final String CANCELLED = "CANCELLED";
}
