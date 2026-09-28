package com.dilaw.mohe.codes;

/** LU_ALLOCATION_TYPE (V073): AttacheAllocation.allocationType. */
@CodeList(value = "allocation-type", mode = CodeList.Mode.EXACT)
public final class AllocationType {

    private AllocationType() {
    }

    public static final String REGULAR = "REGULAR";
    public static final String ADDITIONAL = "ADDITIONAL";
}
