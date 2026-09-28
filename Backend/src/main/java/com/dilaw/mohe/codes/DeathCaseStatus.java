package com.dilaw.mohe.codes;

/** LU_DEATH_CASE_STATUS (V074): STUDENT_DEATH_CASE.CASE_STATUS (StudentDeathService). */
@CodeList(value = "death-case-status", mode = CodeList.Mode.EXACT)
public final class DeathCaseStatus {

    private DeathCaseStatus() {
    }

    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";
}
