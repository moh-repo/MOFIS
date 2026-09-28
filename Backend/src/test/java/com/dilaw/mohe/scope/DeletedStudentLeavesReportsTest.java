package com.dilaw.mohe.scope;

import com.dilaw.mohe.dto.AttacheSummaryRowDto;
import com.dilaw.mohe.service.AttacheSummaryReportService;
import com.dilaw.mohe.service.ChartDataService;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentFinanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deleted student leaves the reports (live QA H7, 25 Sep 2026; decision D5-a).
 *
 * <p>Student 10116 was deleted and still counted: Attaché Summary Egypt 942 instead
 * of 941, its finance row in the Finance Records list, and its received year 20251
 * on the dashboard's financial trend. Rolled back; runs only against a copy of MOHE.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class DeletedStudentLeavesReportsTest {

    private static final int EGYPT = 1;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AttacheSummaryReportService attacheSummary;
    @Autowired private ChartDataService charts;
    @Autowired private StudentFinanceService finance;
    @Autowired private StudentDataService students;

    /** A live Egyptian student with a finance row that carries money. */
    private Map<String, Object> liveStudentWithFinance() {
        return jdbc.queryForMap("""
                SELECT TOP 1 f.STUDENT_ID AS id, CAST(ISNULL(f.NET_AMOUNT, 0) AS float) AS net
                FROM MOHECORE.STUDENT_FINANCE f
                JOIN MOHECORE.STUDENT_DATA sd ON sd.STUDENT_ID = f.STUDENT_ID
                WHERE ISNULL(sd.IS_DELETED, 0) = 0 AND sd.STUDY_COUNTRY_IN = ? AND f.NET_AMOUNT > 0
                ORDER BY f.STUDENT_ID
                """, EGYPT);
    }

    private AttacheSummaryRowDto egypt() {
        List<AttacheSummaryRowDto> rows = attacheSummary.rows(EGYPT, null, "en");
        return rows.stream().filter(r -> Integer.valueOf(EGYPT).equals(r.getStudyCountryInId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("a deleted student's leftover finance row is not counted anywhere (the filter)")
    void leftoverFinanceIsFiltered() {
        AsUser.call(AsUser.SUPERADMIN, () -> {
            Map<String, Object> s = liveStudentWithFinance();
            int id = ((Number) s.get("id")).intValue();
            double net = ((Number) s.get("net")).doubleValue();

            AttacheSummaryRowDto before = egypt();
            double totalBefore = charts.financeTotals(null).totalDisbursed();
            long listBefore = finance.getAll(PageRequest.of(0, 1)).getTotalElements();

            // As student 10116: deleted, its finance row left behind.
            jdbc.update("UPDATE MOHECORE.STUDENT_DATA SET IS_DELETED = 1 WHERE STUDENT_ID = ?", id);

            AttacheSummaryRowDto after = egypt();
            assertThat(after.getStudentCount()).isEqualTo(before.getStudentCount() - 1);
            assertThat(after.getFinanceRecordCount()).isEqualTo(before.getFinanceRecordCount() - 1);
            assertThat(charts.financeTotals(null).totalDisbursed()).isCloseTo(totalBefore - net, org.assertj.core.data.Offset.offset(0.01));
            assertThat(finance.getAll(PageRequest.of(0, 1)).getTotalElements()).isEqualTo(listBefore - 1);
            return null;
        });
    }

    @Test
    @DisplayName("deleting a student removes its finance row and deactivates its portal account (the cascade)")
    void deleteCascades() {
        AsUser.call(AsUser.SUPERADMIN, () -> {
            int id = ((Number) liveStudentWithFinance().get("id")).intValue();

            students.delete(id);

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE WHERE STUDENT_ID = ?",
                    Integer.class, id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE_PAYMENT WHERE STUDENT_ID = ?",
                    Integer.class, id)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM STUDENTCORE.student_users
                    WHERE student_id = ? AND is_deleted = 0 AND status = 'ACTIVE'
                    """, Integer.class, id)).isZero();
            return null;
        });
    }
}
