package com.dilaw.mohe.scope;

import com.dilaw.mohe.dto.WafarStatementRowDto;
import com.dilaw.mohe.service.WafarStatementReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Wafar statement — the first report for a module that had none.
 *
 * <p>Wafar is budget already transferred to one attaché and never disbursed, so
 * it stays there: Cairo's savings pay only Cairo. A report that widened that
 * would be worse than no report, so every assertion about what a country officer
 * sees is paired with a ministry control on the same call.
 */
@Tag("integration")
@SpringBootTest
class WafarStatementReportTest {

    @Autowired private WafarStatementReportService reportService;
    @Autowired private JdbcTemplate jdbc;

    private static final int JORDAN = 1004;

    private List<WafarStatementRowDto> rowsAs(long userId) {
        return AsUser.call(userId, () -> reportService.rows(null, null, null, null, null, null, "en"));
    }

    @Test
    @DisplayName("the ministry sees every Wafar transaction")
    void ministrySeesEverything() {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.WAFAR_SPENDING", Integer.class);
        assumeTrue(total != null && total > 0, "no Wafar spending seeded");

        assertThat(rowsAs(AsUser.SUPERADMIN))
                .as("positive control: without this the scope assertions below prove nothing")
                .hasSize(total);
    }

    @Test
    @DisplayName("a country officer sees only its own attaché's transactions")
    void officerSeesOnlyItsOwn() {
        List<WafarStatementRowDto> ministry = rowsAs(AsUser.SUPERADMIN);
        assumeTrue(!ministry.isEmpty(), "no Wafar spending seeded");

        List<WafarStatementRowDto> jordan = rowsAs(AsUser.JO_ADMIN);

        assertThat(jordan.size())
                .as("an officer can never see more than the ministry")
                .isLessThanOrEqualTo(ministry.size());

        assertThat(jordan)
                .as("""
                    Wafar is budget already sitting with one attaché. A row belonging to \
                    another attaché on this statement would say Cairo's savings can pay Amman.""")
                .allSatisfy(row -> assertThat(row.getStudyCountryInId()).isEqualTo(JORDAN));
    }

    @Test
    @DisplayName("narrowing to an attaché the caller does not hold is refused, not ignored")
    void outOfScopeCountryIsRefused() {
        /*
         * PolicyDenied (a 403 ResponseStatusException, audited ACCESS_DENIED):
         * since phase 4 (R-17) CountryScopeService.validateCountryAccess asks the
         * policy evaluator, and that is what every report service propagates.
         */
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN,
                () -> reportService.rows(1, null, null, null, null, null, "en")))
                .as("silently returning Jordan's rows for a Cairo request would be worse than an error")
                .isInstanceOf(com.dilaw.mohe.security.policy.PolicyDenied.class);
    }

    @Test
    @DisplayName("an unattributed transaction belongs to no attaché, so narrowing drops it")
    void unattributedRowsAreMinistryOnly() {
        Integer unattributed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.WAFAR_SPENDING WHERE STUDY_COUNTRY_IN_ID IS NULL", Integer.class);
        assumeTrue(unattributed != null && unattributed > 0, "every transaction has an attaché");

        long ministryNulls = rowsAs(AsUser.SUPERADMIN).stream()
                .filter(r -> r.getStudyCountryInId() == null)
                .count();
        assertThat(ministryNulls)
                .as("the ministry must still see them, or they become unreachable by anyone")
                .isEqualTo(unattributed.longValue());

        List<WafarStatementRowDto> narrowed = AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.rows(1, null, null, null, null, null, "en"));
        assertThat(narrowed)
                .as("a row with no country is not Egypt's just because Egypt was asked for")
                .noneMatch(r -> r.getStudyCountryInId() == null);
    }

    @Test
    @DisplayName("every printed column resolves to a name, never a bare id")
    void namesAreResolvedNotIds() {
        List<WafarStatementRowDto> rows = rowsAs(AsUser.SUPERADMIN);
        assumeTrue(!rows.isEmpty(), "no Wafar spending seeded");

        assertThat(rows)
                .as("""
                    The template prints categoryName, not categoryId. A row whose category \
                    resolves to nothing prints a blank cell, which reads as a broken report \
                    rather than as missing data.""")
                .allSatisfy(row -> assertThat(row.getCategoryName()).isNotBlank());

        assertThat(rows)
                .filteredOn(r -> r.getStudyCountryInId() != null)
                .allSatisfy(row -> assertThat(row.getStudyCountryName()).isNotBlank());
    }

    @Test
    @DisplayName("the summary agrees with the rows it summarises")
    void summaryMatchesRows() {
        var summary = AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.summary(null, null, null, null, null, null, "en"));
        List<WafarStatementRowDto> rows = rowsAs(AsUser.SUPERADMIN);

        assertThat(summary.getTransactionCount()).isEqualTo(rows.size());

        BigDecimal rowTotal = rows.stream()
                .map(WafarStatementRowDto::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal categoryTotal = summary.getCategoryTotals().stream()
                .map(c -> c.getTotalAmount() == null ? BigDecimal.ZERO : c.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(categoryTotal)
                .as("the per-category breakdown must add up to the transactions listed above it")
                .isEqualByComparingTo(rowTotal);
    }

    @Test
    @DisplayName("the language chooses the names, and both languages return the same rows")
    void languageChangesNamesNotRows() {
        List<WafarStatementRowDto> en = rowsAs(AsUser.SUPERADMIN);
        List<WafarStatementRowDto> ar = AsUser.call(AsUser.SUPERADMIN,
                () -> reportService.rows(null, null, null, null, null, null, "ar"));
        assumeTrue(!en.isEmpty(), "no Wafar spending seeded");

        assertThat(ar).hasSameSizeAs(en);
        assertThat(ar.get(0).getId()).isEqualTo(en.get(0).getId());
    }
}
