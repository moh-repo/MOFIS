package com.dilaw.mohe.codes;

/** LU_ALLOCATION_STATUS (V073): AttacheAllocation.status, the V042 CHECK constraint. */
@CodeList(value = "allocation-status", mode = CodeList.Mode.EXACT)
public final class AllocationStatus {

    private AllocationStatus() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String TRANSFERRED = "TRANSFERRED";
    public static final String CANCELLED = "CANCELLED";
}
