package com.dilaw.mohe.codes;

/** FINCORE.FEE_TYPE.CODE (V042): the fee types the accounting services and the entitlements report name. */
@CodeList(value = "fee-type", mode = CodeList.Mode.SUBSET)
public final class FeeTypeCode {

    private FeeTypeCode() {
    }

    public static final String STIPEND = "STIPEND";
    public static final String BOOKS = "BOOKS";
    public static final String TRAVEL = "TRAVEL";
    public static final String UNIVERSITY_FEE = "UNIVERSITY_FEE";
    public static final String OTHER = "OTHER";
}
