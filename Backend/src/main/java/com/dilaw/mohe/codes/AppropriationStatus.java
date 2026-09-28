package com.dilaw.mohe.codes;

/** LU_APPROPRIATION_STATUS (V073): BudgetAppropriation.status, the V042 CHECK constraint. */
@CodeList(value = "appropriation-status", mode = CodeList.Mode.EXACT)
public final class AppropriationStatus {

    private AppropriationStatus() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String REVISED = "REVISED";
}
