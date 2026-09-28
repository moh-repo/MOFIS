package com.dilaw.mohe.codes;

/** LU_WAFAR_BENEFICIARY_TYPE (V073): who a wafar spending transaction pays (WafarSpendingService). */
@CodeList(value = "wafar-beneficiary-type", mode = CodeList.Mode.EXACT)
public final class WafarBeneficiaryType {

    private WafarBeneficiaryType() {
    }

    public static final String STUDENT = "STUDENT";
    public static final String MINISTRY = "MINISTRY";
    public static final String CULTURAL_AFFAIRS = "CULTURAL_AFFAIRS";
    public static final String OTHER = "OTHER";
}
