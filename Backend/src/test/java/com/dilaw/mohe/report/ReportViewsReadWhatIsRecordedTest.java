package com.dilaw.mohe.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V075: the eleven report views that used to print literal columns now
 * compute what a real column supports and no longer carry what nothing
 * records. These assert the views' shape and arithmetic, not their text.
 */
@Tag("integration")
@SpringBootTest
class ReportViewsReadWhatIsRecordedTest {

    private static final List<String> VIEWS = List.of(
            "V_Annual_Report", "V_Monthly_Report", "V_Quarterly_Report", "V_Program_Catalog",
            "V_Student_Distribution_University", "V_University_Agreement", "V_University_Partnership",
            "V_University_Performance", "V_Academic_Performance", "V_Student_List_Detailed",
            "VW_STUDENT_ENROLLMENT_BY_COUNTRY");

    private static final List<String> REMOVED = List.of(
            "graduated_students", "total_graduated", "graduation_rate", "is_graduated", "GPA",
            "program_status", "program_start_date", "last_enrollment_date", "scholarship_slots",
            "agreement_start_date", "last_activity_date", "agreement_duration_years",
            "scholarship_allocations", "total_financial_commitment", "agreement_status",
            "operational_status", "partnership_start_date", "partnership_duration_years", "GraduatedStudents");

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("no view carries a column that nothing in this database records")
    void unsourcedColumnsAreGone() {
        String inViews = String.join(",", VIEWS.stream().map(v -> "'" + v + "'").toList());
        String inRemoved = String.join(",", REMOVED.stream().map(c -> "'" + c + "'").toList());
        List<Map<String, Object>> present = jdbc.queryForList(
                "SELECT v.name AS v, c.name AS c FROM sys.views v JOIN sys.columns c ON c.object_id = v.object_id "
                        + "WHERE v.schema_id = SCHEMA_ID('MOHECORE') AND v.name IN (" + inViews + ") "
                        + "AND c.name IN (" + inRemoved + ")");
        assertThat(present).as("literal or NULL-by-construction columns still exposed").isEmpty();
    }

    @Test
    @DisplayName("scholarship + regular never exceeds the total, and degree levels never exceed the total")
    void splitsAddUp() {
        for (String view : List.of("V_Annual_Report", "V_Quarterly_Report", "V_University_Partnership",
                "V_Student_Distribution_University")) {
            Integer over = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM MOHECORE." + view + " WHERE scholarship_students + regular_students > total_students",
                    Integer.class);
            assertThat(over).as(view).isZero();
        }
        Integer overLevels = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.V_Student_Distribution_University "
                        + "WHERE bachelors_students + masters_students + phd_students > total_students", Integer.class);
        assertThat(overLevels).isZero();
        Integer overRetention = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.V_University_Performance WHERE retention_rate > 100 OR retention_rate < 0",
                Integer.class);
        assertThat(overRetention).isZero();
    }

    @Test
    @DisplayName("new enrollments and active students are counted by status CODE, so they are bounded by the total")
    void statusCountsAreBounded() {
        for (String view : List.of("V_Annual_Report", "V_Monthly_Report", "V_Quarterly_Report")) {
            Integer over = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM MOHECORE." + view
                            + " WHERE new_enrollments > total_students OR active_students > total_students", Integer.class);
            assertThat(over).as(view).isZero();
        }
        Integer genderOver = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.V_Student_Distribution_University WHERE male_students + female_students > total_students",
                Integer.class);
        assertThat(genderOver).isZero();
        // The gender split reads STUDENT_GENDER.CODE, seeded by V075 from the English names.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.STUDENT_GENDER WHERE CODE IS NULL", Integer.class)).isZero();
    }

    @Test
    @DisplayName("the enrollment-by-country chart view still counts active students, by CODE")
    void enrollmentByCountryCountsByCode() {
        Integer over = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.VW_STUDENT_ENROLLMENT_BY_COUNTRY WHERE ActiveStudents > StudentCount", Integer.class);
        assertThat(over).isZero();
        Long activeByCode = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA sd JOIN MOHECORE.STUDENT_STUDY_STATUS s "
                        + "ON s.STUDENT_STUDY_STATUS_ID = sd.STUDENT_STUDY_STATUS_ID WHERE s.CODE = 'ACTIVE' AND ISNULL(sd.IS_DELETED,0) = 0",
                Long.class);
        Long activeInView = jdbc.queryForObject(
                "SELECT ISNULL(SUM(ActiveStudents), 0) FROM MOHECORE.VW_STUDENT_ENROLLMENT_BY_COUNTRY", Long.class);
        assertThat(activeInView).isEqualTo(activeByCode);
    }
}
