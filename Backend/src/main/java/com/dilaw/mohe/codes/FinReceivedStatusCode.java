package com.dilaw.mohe.codes;

/** STUDY_FIN_RECEIVED_STATUS.CODE (V072): received = paid in the quarterly financial report. */
@CodeList(value = "fin-received-status", mode = CodeList.Mode.SUBSET)
public final class FinReceivedStatusCode {

    private FinReceivedStatusCode() {
    }

    public static final String RECEIVED = "RECEIVED";
    public static final String NOT_SPECIFIED = "NOT_SPECIFIED";
    /** Paid out to the student; the portal shows it as received, like {@link #RECEIVED}. */
    public static final String DISBURSED = "DISBURSED";
    /** Part of the quarter's money paid out: money moved, so a cheque date is required (live QA H2). */
    public static final String PARTIALLY_DISBURSED = "PARTIALLY_DISBURSED";
}
