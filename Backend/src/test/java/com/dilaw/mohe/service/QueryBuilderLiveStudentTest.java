package com.dilaw.mohe.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX-PLAN P5-11 (26 Sep 2026): the query builder excluded deleted students only from
 * STUDENT_DATA and STUDENT_FINANCE. Every other table or view with a student key (loans,
 * extensions, payments, the student views) returned deleted students, and a LEFT JOIN from
 * such a table to STUDENT_DATA brought them back with empty student columns. The archives
 * stay whole: they are the record of what was paid and held (decision of 26 Sep 2026).
 */
class QueryBuilderLiveStudentTest {

    @Test
    @DisplayName("the two base tables keep their own conditions")
    void baseTables() {
        assertThat(QueryExecutionService.liveStudentCondition("STUDENT_DATA", "t0", List.of("STUDENT_ID")))
                .isEqualTo("ISNULL(t0.IS_DELETED, 0) = 0");
        assertThat(QueryExecutionService.liveStudentCondition("STUDENT_FINANCE", "t1", List.of("STUDENT_ID")))
                .contains("live_sd.STUDENT_ID = t1.STUDENT_ID").contains("IS_DELETED");
    }

    @Test
    @DisplayName("any source with a student key excludes deleted students and keeps rows that name no student")
    void studentKeyedSources() {
        String loans = QueryExecutionService.liveStudentCondition("STUDENT_LOAN", "t0", List.of("LOAN_ID", "STUDENT_ID"));
        assertThat(loans).startsWith("(t0.[STUDENT_ID] IS NULL OR EXISTS").contains("ISNULL(live_sd.IS_DELETED, 0) = 0");

        String inquiries = QueryExecutionService.liveStudentCondition("StudentInquiry", "t2", List.of("Id", "StudentId"));
        assertThat(inquiries).contains("t2.[StudentId]");

        assertThat(QueryExecutionService.liveStudentCondition("V_Payment_Summary", "t0", List.of("student_id", "amount")))
                .as("a view with a student key").isNotNull();
    }

    @Test
    @DisplayName("the archives and sources with no student are left alone")
    void archivesAndOthers() {
        assertThat(QueryExecutionService.liveStudentCondition("STUDENT_FINANCE_ARCH", "t0", List.of("STUDENT_ID"))).isNull();
        assertThat(QueryExecutionService.liveStudentCondition("STUDENT_DATA_ARCH", "t0", List.of("STUDENT_ID"))).isNull();
        assertThat(QueryExecutionService.liveStudentCondition("STUDY_COUNTRY_IN", "t0", List.of("STUDY_COUNTRY_IN_ID"))).isNull();
    }
}
