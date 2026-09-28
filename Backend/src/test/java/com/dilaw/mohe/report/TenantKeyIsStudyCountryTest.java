package com.dilaw.mohe.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The tenant key is the country a student studies in, never their nationality.
 *
 * <p>Six report views used to expose only STUDENT_COUNTRY_ID and their
 * repositories filtered the user's country scope against it. UMSCORE.UserCountries
 * holds STUDY_COUNTRY_IN ids, and the two are separate lookup tables whose ids
 * overlap — id 1 is Egypt as a study country and Yemen as a nationality, 1007 is
 * the United States and Jordan respectively. The live data hid the bug because
 * every student is home=1 and study=1, so the numbers coincided.
 *
 * <p>These tests fail if a view stops exposing the tenant key, or if the two
 * columns are ever made interchangeable again.
 */
@Tag("integration")
@SpringBootTest
class TenantKeyIsStudyCountryTest {

    @Autowired
    private JdbcTemplate jdbc;

    private static final List<String> SCOPED_VIEWS = List.of(
            "V_Student_Demographics",
            "V_Student_List_Detailed",
            "V_Academic_Performance",
            "V_Student_Status_Report",
            "V_Enrollment_Statistics",
            "V_Major_Distribution");

    @Test
    @DisplayName("every scoped report view exposes the tenant key")
    void scopedViews_exposeTheTenantKey() {
        assertThat(SCOPED_VIEWS).allSatisfy(view -> {
            Integer present = jdbc.queryForObject(
                    "SELECT CASE WHEN COL_LENGTH('MOHECORE.' + ?, 'study_country_in_id') "
                            + "IS NULL THEN 0 ELSE 1 END", Integer.class, view);
            assertThat(present)
                    .as("%s must expose study_country_in_id or it cannot be scoped correctly", view)
                    .isEqualTo(1);
        });
    }

    @Test
    @DisplayName("study country and nationality are genuinely different id spaces")
    void theTwoCountryColumns_areNotInterchangeable() {
        // If these ever became the same lookup the whole distinction would be
        // moot — and the original bug would be harmless. They are not the same.
        String studyName = jdbc.queryForObject(
                "SELECT STUDY_COUNTRY_IN_NAME_EN FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 1",
                String.class);
        String homeName = jdbc.queryForObject(
                "SELECT STUDENT_COUNTRY_NAME_EN FROM MOHECORE.STUDENT_COUNTRY WHERE STUDENT_COUNTRY_ID = 1",
                String.class);

        assertThat(studyName)
                .as("id 1 means different countries in the two tables; scoping on the "
                        + "wrong one silently shows the wrong tenant's students")
                .isNotEqualToIgnoringCase(homeName);
    }

    @Test
    @DisplayName("a scope narrows on where students study, not where they are from")
    void scope_filtersOnStudyCountry() {
        // This used to filter on 1004 and expect nothing, which held only while
        // every student on the database studied in Egypt. Students now exist
        // under Jordan and Saudi Arabia (DEC-23), so the question is put as the
        // rule it was always testing: the view counts by where a student
        // studies, and that is not the nationality column.
        Integer byNationality = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.V_Student_List_Detailed WHERE STUDENT_COUNTRY_ID = 1",
                Integer.class);
        assertThat(byNationality)
                .as("the data really does carry that nationality, so the columns are not both empty")
                .isGreaterThan(0);

        // Country by country, the view agrees with the table it is built from.
        // A view that had fallen back to nationality would disagree for every
        // country whose students study somewhere else.
        Integer mismatched = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ("
                + "  SELECT ISNULL(v.study_country_in_id, -1) AS c, COUNT(*) AS n"
                + "    FROM MOHECORE.V_Student_List_Detailed v GROUP BY v.study_country_in_id"
                + ") AS view_counts FULL JOIN ("
                + "  SELECT ISNULL(s.STUDY_COUNTRY_IN, -1) AS c, COUNT(*) AS n"
                + "    FROM MOHECORE.STUDENT_DATA s WHERE ISNULL(s.IS_DELETED,0) = 0"
                + "   GROUP BY s.STUDY_COUNTRY_IN"
                + ") AS table_counts ON table_counts.c = view_counts.c"
                + " WHERE ISNULL(view_counts.n, -1) <> ISNULL(table_counts.n, -1)",
                Integer.class);
        assertThat(mismatched)
                .as("the view's study country must agree with the table's, for every country")
                .isZero();

        // And a country nobody studies in still sees nothing -- chosen from the
        // data rather than written down, so seeding another country cannot
        // quietly turn this into a test of nothing.
        Integer emptyCountry = jdbc.queryForObject(
                "SELECT TOP 1 c.STUDY_COUNTRY_IN_ID FROM MOHECORE.STUDY_COUNTRY_IN c"
                + " WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_DATA s"
                + "                    WHERE s.STUDY_COUNTRY_IN = c.STUDY_COUNTRY_IN_ID"
                + "                      AND ISNULL(s.IS_DELETED,0) = 0)"
                + " ORDER BY c.STUDY_COUNTRY_IN_ID",
                Integer.class);
        assumeTrue(emptyCountry != null, "every country in the list has students");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.V_Student_List_Detailed WHERE study_country_in_id = ?",
                Integer.class, emptyCountry))
                .as("a scope for a country nobody studies in must see nothing")
                .isZero();
    }

    @Test
    @DisplayName("the reports that were dropping students now cover all of them")
    void groupedViews_coverEveryStudent() {
        // V_Enrollment_Statistics and V_Major_Distribution inner-joined
        // STUDENT_DEGREE, which is set on 4 of 941 students, so they reported
        // 3 students out of 941 until V030 made those joins LEFT.
        Integer students = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA WHERE ISNULL(IS_DELETED,0)=0", Integer.class);

        assertThat(jdbc.queryForObject(
                "SELECT SUM(student_count) FROM MOHECORE.V_Enrollment_Statistics", Integer.class))
                .as("enrollment statistics must not discard students with no recorded degree")
                .isEqualTo(students);
        assertThat(jdbc.queryForObject(
                "SELECT SUM(student_count) FROM MOHECORE.V_Major_Distribution", Integer.class))
                .as("major distribution must not discard students with no recorded degree")
                .isEqualTo(students);
    }
}
