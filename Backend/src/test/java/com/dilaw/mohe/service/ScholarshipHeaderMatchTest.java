package com.dilaw.mohe.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Header captions are matched by containment, longest caption first (review M13). */
class ScholarshipHeaderMatchTest {

    private static String column(String caption) {
        return ScholarshipDataImportService.columnFor(ScholarshipDataImportService.normaliseArabic(caption));
    }

    @Test
    @DisplayName("an exact caption still matches")
    void exact() {
        assertThat(column("اسم الطالب")).isEqualTo("STUDENT_NAME");
        assertThat(column("الدرجه")).isEqualTo("DEGREE");
    }

    @Test
    @DisplayName("a caption with extra words matches the column it contains")
    void contains() {
        assertThat(column("اسم الطالب رباعي")).isEqualTo("STUDENT_NAME");
        assertThat(column("المبلغ بالدولار")).isEqualTo("AMOUNT");
        assertThat(column("تاريخ اول مصرف")).isEqualTo("FIRST_DISBURSEMENT");
    }

    @Test
    @DisplayName("notes and financial notes stay two columns: the longer caption wins")
    void longestWins() {
        assertThat(column("ملاحظات")).isEqualTo("NOTES");
        assertThat(column("ملاحظات المالية")).isEqualTo("FINANCIAL_NOTES");
        assertThat(column("ملاحظات الإدارة المالية")).isEqualTo("NOTES");
    }

    @Test
    @DisplayName("a one-letter caption matches only the whole cell")
    void shortCaptionsAreExact() {
        assertThat(column("م")).isEqualTo("SERIAL");
        assertThat(column("مكان الميلاد")).isNull();
        assertThat(column("")).isNull();
    }
}
