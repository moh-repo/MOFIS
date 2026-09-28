package com.dilaw.mohe.util;

/**
 * The one filter that keeps deleted students out of reports (live QA H7, decision D5-a).
 *
 * <p>A student delete is a soft delete: {@code STUDENT_DATA.IS_DELETED = 1}. JPA reads
 * of {@code StudentDatum} skip such rows through the entity's {@code @Where}; SQL
 * written by hand does not, and the Attaché Summary, the finance charts and the
 * finance reports kept counting a deleted student (Egypt 942 instead of 941, a
 * dashboard year "20251").
 *
 * <p>Every hand-written read of {@code MOHECORE.STUDENT_DATA} or
 * {@code MOHECORE.STUDENT_FINANCE} states the filter in the same statement, with
 * these fragments or the {@code IS_DELETED} predicate they spell;
 * {@code LiveStudentFilterArchTest} fails the build when one does not. Finance rows
 * of a deleted student are also removed when the student is deleted
 * ({@code StudentDeletionCascade}), so views over STUDENT_FINANCE, which Java cannot
 * filter, stay right too.
 */
public final class LiveStudentSql {

    private LiveStudentSql() {
    }

    /** For {@code @Query} and {@code @Subselect} text: the STUDENT_DATA alias {@code sd} is not deleted. */
    public static final String SD_NOT_DELETED = "ISNULL(sd.IS_DELETED, 0) = 0";

    /** For {@code @Query} and {@code @Subselect} text: the finance row {@code sf} belongs to a live student. */
    public static final String SF_OF_LIVE_STUDENT = "EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA live_sd "
            + "WHERE live_sd.STUDENT_ID = sf.STUDENT_ID AND ISNULL(live_sd.IS_DELETED, 0) = 0)";

    /** The STUDENT_DATA row under {@code studentAlias} is not deleted. */
    public static String notDeleted(String studentAlias) {
        return "ISNULL(" + studentAlias + ".IS_DELETED, 0) = 0";
    }

    /**
     * The row under {@code rowAlias} names no student, or a student that exists and is not
     * deleted; {@code column} is its student key as spelled there ({@code STUDENT_ID} or
     * {@code StudentId}). For sources whose student is optional (FIX-PLAN P5-11).
     */
    public static String noneOrLiveStudent(String rowAlias, String column) {
        String key = rowAlias + ".[" + column + "]";
        return "(" + key + " IS NULL OR EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA live_sd WHERE live_sd.STUDENT_ID = "
                + key + " AND ISNULL(live_sd.IS_DELETED, 0) = 0))";
    }

    /** The row under {@code rowAlias}, keyed by STUDENT_ID, belongs to a student that exists and is not deleted. */
    public static String ofLiveStudent(String rowAlias) {
        return "EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA live_sd WHERE live_sd.STUDENT_ID = "
                + rowAlias + ".STUDENT_ID AND ISNULL(live_sd.IS_DELETED, 0) = 0)";
    }
}
