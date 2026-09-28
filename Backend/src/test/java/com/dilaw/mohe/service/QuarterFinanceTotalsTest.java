package com.dilaw.mohe.service;

import com.dilaw.mohe.service.QuarterFinanceTotalsService.QuarterTotals;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The MOHE Quarters screen's Amount column.
 *
 * <p>It used to read {@code MOHE_QUARTER.QUARTER_AMOUNT}, a hand-entered column
 * holding {@code 0} on all twelve rows, so quarters that moved a million and a
 * half showed nothing. It is now summed from the finance rows themselves.
 *
 * <p>The rule under test is which table each quarter is read from: the live
 * {@code STUDENT_FINANCE} where it has rows, and {@code STUDENT_FINANCE_ARCH}
 * otherwise. Getting that backwards is not a cosmetic error — the two tables
 * disagree about 2024 Q2 by 3,360, which is exactly the Wafar the archive does
 * not record.
 *
 * <p>Read-only, so nothing to roll back. Assertions are derived from the database
 * rather than hard-coded, so the test does not go stale when a quarter rolls over.
 */
@Tag("integration")
@SpringBootTest
class QuarterFinanceTotalsTest {

    @Autowired private QuarterFinanceTotalsService service;
    @Autowired private JdbcTemplate jdbc;

    /** Ministry scope: sums every country, which is what an unfiltered screen shows. */
    private static final CountryScope MINISTRY = new CountryScope(1L, true, List.of(1));

    @Test
    @DisplayName("a quarter still in the live table is summed from there, not from the archive")
    void liveWinsOverTheArchive() {
        List<Map<String, Object>> live = jdbc.queryForList("""
                SELECT STUDENT_FIN_RECIEVED_YEAR y, STUDENT_FIN_RECIEVED_MONTH q,
                       SUM(CAST(ISNULL(STUDENT_FIN_RECIEVED_AMOUNT, 0) AS bigint)) received
                FROM MOHECORE.STUDENT_FINANCE
                WHERE STUDENT_FIN_RECIEVED_YEAR IS NOT NULL AND STUDENT_FIN_RECIEVED_MONTH IS NOT NULL
                GROUP BY STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH
                """);
        assumeTrue(!live.isEmpty(), "no live finance rows to check");

        Map<Integer, QuarterTotals> totals = service.totalsByQuarter(MINISTRY);

        for (Map<String, Object> row : live) {
            int key = QuarterFinanceTotalsService.quarterKey(
                    ((Number) row.get("y")).intValue(), ((Number) row.get("q")).intValue());
            QuarterTotals t = totals.get(key);

            assertThat(t).as("quarter %d has live rows and must appear", key).isNotNull();
            assertThat(t.source())
                    .as("""
                        %d is still in STUDENT_FINANCE, so that is the record of it. The archive's \
                        copy of the same quarter is a snapshot taken before the quarter finished \
                        and does not carry the Wafar split.""", key)
                    .isEqualTo(QuarterTotals.Source.LIVE);
            assertThat(t.received())
                    .as("the figure must be the live table's own sum")
                    .isEqualTo(((Number) row.get("received")).longValue());
        }
    }

    @Test
    @DisplayName("a quarter that has rolled over is summed from the archive")
    void rolledOverQuartersComeFromTheArchive() {
        List<Map<String, Object>> archiveOnly = jdbc.queryForList("""
                SELECT a.STUDENT_FIN_RECIEVED_YEAR y, a.STUDENT_FIN_RECIEVED_MONTH q,
                       SUM(CAST(ISNULL(a.STUDENT_FIN_RECIEVED_AMOUNT, 0) AS bigint)) received
                FROM MOHECORE.STUDENT_FINANCE_ARCH a
                WHERE NOT EXISTS (
                        SELECT 1 FROM MOHECORE.STUDENT_FINANCE f
                        WHERE f.STUDENT_FIN_RECIEVED_YEAR = a.STUDENT_FIN_RECIEVED_YEAR
                          AND f.STUDENT_FIN_RECIEVED_MONTH = a.STUDENT_FIN_RECIEVED_MONTH)
                GROUP BY a.STUDENT_FIN_RECIEVED_YEAR, a.STUDENT_FIN_RECIEVED_MONTH
                """);
        assumeTrue(!archiveOnly.isEmpty(), "every archived quarter is also live");

        Map<Integer, QuarterTotals> totals = service.totalsByQuarter(MINISTRY);

        for (Map<String, Object> row : archiveOnly) {
            int key = QuarterFinanceTotalsService.quarterKey(
                    ((Number) row.get("y")).intValue(), ((Number) row.get("q")).intValue());
            QuarterTotals t = totals.get(key);

            assertThat(t).as("quarter %d exists only in the archive and must still show", key).isNotNull();
            assertThat(t.source()).isEqualTo(QuarterTotals.Source.ARCHIVE);
            assertThat(t.received()).isEqualTo(((Number) row.get("received")).longValue());
        }
    }

    @Test
    @DisplayName("net is what reached students: received minus Wafar")
    void netIsReceivedMinusWafar() {
        Map<Integer, QuarterTotals> totals = service.totalsByQuarter(MINISTRY);
        assumeTrue(!totals.isEmpty(), "no finance rows at all");

        assertThat(totals.values())
                .allSatisfy(t -> assertThat(t.net())
                        .as("NET_AMOUNT is a computed column; the three must agree or one of them is lying")
                        .isEqualTo(t.received() - t.wafar()));
    }

    @Test
    @DisplayName("a quarter with no finance rows has no figure, rather than a stale one")
    void quartersWithoutFinanceRowsAreAbsent() {
        Map<Integer, QuarterTotals> totals = service.totalsByQuarter(MINISTRY);

        List<Integer> unfunded = jdbc.queryForList("""
                SELECT (QUARTER_YEAR * 10 + QUARTER_NO)
                FROM MOHECORE.MOHE_QUARTER q
                WHERE QUARTER_YEAR IS NOT NULL AND QUARTER_NO IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE f
                                  WHERE f.STUDENT_FIN_RECIEVED_YEAR = q.QUARTER_YEAR
                                    AND f.STUDENT_FIN_RECIEVED_MONTH = q.QUARTER_NO)
                  AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE_ARCH a
                                  WHERE a.STUDENT_FIN_RECIEVED_YEAR = q.QUARTER_YEAR
                                    AND a.STUDENT_FIN_RECIEVED_MONTH = q.QUARTER_NO)
                """, Integer.class);
        assumeTrue(!unfunded.isEmpty(), "every quarter has finance rows");

        assertThat(unfunded).allSatisfy(key -> assertThat(totals)
                .as("""
                    quarter %s has been created but nothing paid against it. The screen shows zero \
                    for it — not the old hand-entered column value, which is what made this \
                    column wrong in the first place.""", key)
                .doesNotContainKey(key));
    }

    @Test
    @DisplayName("a user with no country scope is summed nothing, not everything")
    void noScopeSumsNothing() {
        assertThat(service.totalsByQuarter(new CountryScope(2L, false, List.of())))
                .as("""
                    Every MOHE_QUARTER row is global, so the list itself does not filter these out. \
                    If the sum were unscoped by default, a user with no attaché would read the \
                    ministry-wide total.""")
                .isEmpty();
    }

    @Test
    @DisplayName("an officer is summed only their own attaché's finance rows")
    void scopedUserSeesOnlyTheirOwnCountry() {
        Integer absent = jdbc.queryForObject("""
                SELECT MIN(c.STUDY_COUNTRY_IN_ID) FROM MOHECORE.STUDY_COUNTRY_IN c
                WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE f
                                  WHERE f.STUDY_COUNTRY_IN_ID = c.STUDY_COUNTRY_IN_ID)
                  AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_FINANCE_ARCH a
                                  WHERE a.STUDY_COUNTRY_IN_ID = c.STUDY_COUNTRY_IN_ID)
                """, Integer.class);
        assumeTrue(absent != null, "every country has finance rows");

        assertThat(service.totalsByQuarter(new CountryScope(2L, false, List.of(absent))))
                .as("country %d has no finance rows of its own and must therefore total nothing", absent)
                .isEmpty();
    }
}
