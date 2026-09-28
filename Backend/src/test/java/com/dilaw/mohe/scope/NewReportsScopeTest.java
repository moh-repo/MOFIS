package com.dilaw.mohe.scope;

import com.dilaw.mohe.dto.AccountingPositionRowDto;
import com.dilaw.mohe.dto.ApprovalsReportRowDto;
import com.dilaw.mohe.dto.AttacheSummaryRowDto;
import com.dilaw.mohe.dto.StudentEntitlementRowDto;
import com.dilaw.mohe.service.AccountingPositionReportService;
import com.dilaw.mohe.service.ApprovalsReportService;
import com.dilaw.mohe.service.AttacheSummaryReportService;
import com.dilaw.mohe.service.StudentEntitlementsReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The accounting, entitlements, approvals and attaché-summary reports.
 *
 * <p>All four read through services that already apply country scope, so what is
 * worth asserting is that the report layer did not widen it — and that each one
 * still returns something for the ministry, so a report that is simply broken
 * cannot pass as a working filter.
 */
@Tag("integration")
@SpringBootTest
class NewReportsScopeTest {

    @Autowired private AccountingPositionReportService accounting;
    @Autowired private StudentEntitlementsReportService entitlements;
    @Autowired private ApprovalsReportService approvals;
    @Autowired private AttacheSummaryReportService attacheSummary;
    @Autowired private JdbcTemplate jdbc;

    private static final int EGYPT = 1;

    // ── Accounting position ──────────────────────────────────────────────────

    @Test
    @DisplayName("the accounting position needs a fiscal year, and says so")
    void accountingRequiresAFiscalYear() {
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN,
                () -> accounting.rows(null, null, null, "en")))
                .as("without a year the report would add unrelated years together")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fiscal year is required");
    }

    @Test
    @DisplayName("a country officer sees only its own attaché's position")
    void accountingIsScoped() {
        Integer yearId = jdbc.queryForList(
                        "SELECT TOP 1 FISCAL_YEAR_ID FROM FINCORE.FISCAL_YEAR ORDER BY FISCAL_YEAR_ID DESC",
                        Integer.class)
                .stream().findFirst().orElse(null);
        assumeTrue(yearId != null, "no fiscal year seeded");

        List<AccountingPositionRowDto> ministry = AsUser.call(AsUser.SUPERADMIN,
                () -> accounting.rows(yearId, null, null, "en"));
        List<AccountingPositionRowDto> jordan = AsUser.call(AsUser.JO_ADMIN,
                () -> accounting.rows(yearId, null, null, "en"));

        assertThat(jordan.size()).isLessThanOrEqualTo(ministry.size());
        assertThat(jordan).allSatisfy(r ->
                assertThat(r.getStudyCountryInId()).isNotEqualTo(EGYPT));
    }

    // ── Entitlements ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("entitlements roll three tables into one list, and stay scoped")
    void entitlementsAreScoped() {
        List<StudentEntitlementRowDto> ministry = AsUser.call(AsUser.SUPERADMIN,
                () -> entitlements.rows(null, null, null, null, "en"));
        assumeTrue(!ministry.isEmpty(), "no entitlements seeded");

        long kinds = ministry.stream().map(StudentEntitlementRowDto::getKind).distinct().count();
        assertThat(kinds)
                .as("the point of this report is that it covers more than one kind")
                .isGreaterThan(1L);

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> entitlements.rows(null, null, null, null, "en")))
                .as("every student is Egyptian, so a Jordan officer sees none of them")
                .isEmpty();
    }

    @Test
    @DisplayName("a status prints as a name, never as its lookup id")
    void entitlementStatusIsANameNotAnId() {
        List<StudentEntitlementRowDto> rows = AsUser.call(AsUser.SUPERADMIN,
                () -> entitlements.rows(null, null, "LOAN", null, "en"));
        assumeTrue(!rows.isEmpty(), "no loans seeded");

        assertThat(rows)
                .filteredOn(r -> r.getStatus() != null)
                .as("""
                    LOAN_STATUS is a lookup id. Printing "2" is the RAW_ID defect the report \
                    audit exists to catch; it misses this one only because the column is not \
                    named *Id.""")
                .allSatisfy(r -> assertThat(r.getStatus()).doesNotMatch("^\\d+$"));
    }

    // ── Approvals ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the approvals queue is scoped and sorted oldest first")
    void approvalsAreScopedAndOrdered() {
        List<ApprovalsReportRowDto> ministry = AsUser.call(AsUser.SUPERADMIN,
                () -> approvals.rows(null, null, null, "en"));
        assumeTrue(!ministry.isEmpty(), "no approval requests seeded");

        assertThat(ministry)
                .as("a queue report is read from the top; the oldest item belongs there")
                .isSortedAccordingTo((a, b) -> Long.compare(b.getAgeDays(), a.getAgeDays()));

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> approvals.rows(null, null, null, "en")).size())
                .isLessThanOrEqualTo(ministry.size());
    }

    @Test
    @DisplayName("the approvals report names its codes in the report's language (W3-07)")
    void approvalsNameTheirCodes() {
        List<ApprovalsReportRowDto> en = AsUser.call(AsUser.SUPERADMIN, () -> approvals.rows(null, null, null, "en"));
        List<ApprovalsReportRowDto> ar = AsUser.call(AsUser.SUPERADMIN, () -> approvals.rows(null, null, null, "ar"));
        assumeTrue(!en.isEmpty(), "no approval requests seeded");

        java.util.function.Predicate<String> arabic = s -> s != null
                && s.codePoints().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC);
        assertThat(en).allSatisfy(r -> {
            assertThat(r.getStatusName()).as("status of request " + r.getId()).isNotBlank().isNotEqualTo(r.getStatus());
            assertThat(r.getRequestTypeName()).as("operation of request " + r.getId()).isNotBlank()
                    .isNotEqualTo(r.getRequestType());
            assertThat(arabic.test(r.getStatusName())).as("English report, English status").isFalse();
        });
        assertThat(ar).allSatisfy(r -> assertThat(arabic.test(r.getStatusName()))
                .as("Arabic report, Arabic status: " + r.getStatusName()).isTrue());
        assertThat(approvals.countsByStatus(en).keySet()).as("the footer counts by name")
                .allSatisfy(k -> assertThat(k).isNotIn("PENDING", "IN_PROGRESS", "APPROVED", "REJECTED", "EXECUTED"));
    }

    // ── Attaché summary ──────────────────────────────────────────────────────

    @Test
    @DisplayName("the attaché summary counts every attaché the caller may see")
    void attacheSummaryCoversTheCallersAttaches() {
        List<AttacheSummaryRowDto> ministry = AsUser.call(AsUser.SUPERADMIN,
                () -> attacheSummary.rows(null, null, "en"));
        Integer countries = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDY_COUNTRY_IN", Integer.class);

        assertThat(ministry).hasSize(countries == null ? 0 : countries);

        List<AttacheSummaryRowDto> jordan = AsUser.call(AsUser.JO_ADMIN,
                () -> attacheSummary.rows(null, null, "en"));
        assertThat(jordan)
                .as("a country officer's summary is its own attachés, not the ministry's view")
                .hasSize(1);
    }

    @Test
    @DisplayName("the summary's student count matches the students table")
    void attacheSummaryCountsAgreeWithTheData() {
        List<AttacheSummaryRowDto> rows = AsUser.call(AsUser.SUPERADMIN,
                () -> attacheSummary.rows(null, null, "en"));
        Integer egyptStudents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN = ?",
                Integer.class, EGYPT);
        assumeTrue(egyptStudents != null && egyptStudents > 0, "no Egyptian students");

        assertThat(rows)
                .filteredOn(r -> EGYPT == (r.getStudyCountryInId() == null ? -1 : r.getStudyCountryInId()))
                .singleElement()
                .satisfies(r -> assertThat(r.getStudentCount()).isEqualTo(egyptStudents.longValue()));
    }
}
