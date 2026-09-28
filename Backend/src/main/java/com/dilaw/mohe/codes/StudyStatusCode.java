package com.dilaw.mohe.codes;

/** STUDENT_STUDY_STATUS.CODE values the counts and reports key on (V028 added the column). */
@CodeList(value = "study-status", mode = CodeList.Mode.SUBSET)
public final class StudyStatusCode {

    private StudyStatusCode() {
    }

    public static final String ACTIVE = "ACTIVE";
    public static final String NEW = "NEW";
    public static final String SUSPENDED = "SUSPENDED";
}
