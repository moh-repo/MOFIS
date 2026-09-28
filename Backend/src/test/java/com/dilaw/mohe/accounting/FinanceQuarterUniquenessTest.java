package com.dilaw.mohe.accounting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One finance row per student per quarter (V038).
 *
 * <p>{@code STUDENT_FINANCE} holds the current quarter; {@code STUDENT_FINANCE_ARCH}
 * holds history. Each quarter the live table is snapshotted into the archive and
 * the new quarter's changes are applied. The rule that makes that work is that
 * (student, year, quarter) never repeats.
 *
 * <p>Only one of the two tables enforced it. {@code STUDENT_FINANCE}'s primary
 * key is {@code STUDENT_ID} alone, so it physically cannot hold two quarters for
 * one student — stricter than the rule. {@code STUDENT_FINANCE_ARCH} had a
 * surrogate key and nothing else, so a rollover run twice would have doubled
 * every historical total, and the only symptom would have been figures that look
 * plausible and are wrong.
 *
 * <p>Rolled back.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class FinanceQuarterUniquenessTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("re-running the quarter rollover cannot double the archive")
    void doubledRolloverIsRefused() {
        Integer before = archiveRows();
        assertThat(before).as("the archive holds 941 students x 4 quarters of 2024").isPositive();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO MOHECORE.STUDENT_FINANCE_ARCH
                    (STUDENT_ID, STUDENT_FIN_NAME, STUDENT_FIN_RECIEVED_YEAR,
                     STUDENT_FIN_RECIEVED_MONTH, STUDENT_FIN_RECIEVED_YEAR_MONTH)
                SELECT STUDENT_ID, ISNULL(STUDENT_FIN_NAME, N'x'), STUDENT_FIN_RECIEVED_YEAR,
                       STUDENT_FIN_RECIEVED_MONTH, STUDENT_FIN_RECIEVED_YEAR_MONTH
                FROM MOHECORE.STUDENT_FINANCE_ARCH
                WHERE STUDENT_FIN_RECIEVED_YEAR = 2024 AND STUDENT_FIN_RECIEVED_MONTH = 1
                """))
                .as("""
                    archiving a quarter that is already archived must fail. Before V038 this \
                    succeeded, and every total for that quarter silently doubled.""")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(archiveRows()).isEqualTo(before);
    }

    @Test
    @DisplayName("both tables carry the uniqueness index, and both are unique")
    void bothTablesAreIndexed() {
        assertThat(uniqueIndexCount("STUDENT_FINANCE_ARCH", "UX_STUDENT_FINANCE_ARCH_STUDENT_QUARTER"))
                .as("the archive is the one that was genuinely unprotected")
                .isEqualTo(1);
        assertThat(uniqueIndexCount("STUDENT_FINANCE", "UX_STUDENT_FINANCE_STUDENT_QUARTER"))
                .as("""
                    on the live table this states a rule the primary key already implies. It is \
                    not redundant: it is what keeps the rule if that key is ever widened to let \
                    the table hold more than one quarter.""")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("no duplicate (student, year, quarter) exists in either table")
    void noDuplicatesExist() {
        assertThat(duplicateGroups("STUDENT_FINANCE")).isZero();
        assertThat(duplicateGroups("STUDENT_FINANCE_ARCH")).isZero();
    }

    /**
     * The quarter column is called {@code ..._MONTH}, which invites a month.
     *
     * <p>No CHECK forbids it, and that is deliberate rather than an oversight:
     * {@code StudentFinanceService.INSERT_FINANCE_FROM_STUDENT_SQL} derives the
     * quarter as {@code ISNULL(STUDENT_ADDED_QUARTER, 0) % 10}, and 938 of 941
     * students have {@code STUDENT_ADDED_QUARTER = 0} — so a constraint would
     * break onboarding for almost every student. This test watches the value
     * instead, so the day the onboarding default is fixed the constraint can go on.
     */
    @Test
    @DisplayName("quarter values are within 1-4 today, though nothing yet forbids otherwise")
    void quarterValuesAreSane() {
        assertThat(outOfRangeQuarters("STUDENT_FINANCE")).isZero();
        assertThat(outOfRangeQuarters("STUDENT_FINANCE_ARCH")).isZero();

        Integer wouldOnboardToQuarterZero = jdbc.queryForObject("""
                SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA
                WHERE ISNULL(IS_DELETED, 0) = 0
                  AND ISNULL(STUDENT_ADDED_QUARTER, 0) % 10 NOT BETWEEN 1 AND 4
                """, Integer.class);
        assertThat(wouldOnboardToQuarterZero)
                .as("""
                    Recorded, not asserted to be zero: this is the reason no CHECK constraint \
                    exists yet. Onboarding any of these students writes year 0, quarter 0.""")
                .isNotNull();
    }

    @Test
    @DisplayName("the three quarter columns agree with each other")
    void yearMonthMatchesYearAndQuarter() {
        assertThat(inconsistentYearMonth("STUDENT_FINANCE")).isZero();
        assertThat(inconsistentYearMonth("STUDENT_FINANCE_ARCH"))
                .as("YEAR_MONTH must always be year*10 + quarter, e.g. 20242")
                .isZero();
    }

    private Integer archiveRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE_ARCH", Integer.class);
    }

    private Integer uniqueIndexCount(String table, String indexName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.indexes
                WHERE object_id = OBJECT_ID('MOHECORE.' + ?) AND name = ? AND is_unique = 1
                """, Integer.class, table, indexName);
    }

    private Integer duplicateGroups(String table) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT 1 AS x FROM MOHECORE.%s
                    GROUP BY STUDENT_ID, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH
                    HAVING COUNT(*) > 1) d
                """.formatted(table), Integer.class);
    }

    private Integer outOfRangeQuarters(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.%s WHERE STUDENT_FIN_RECIEVED_MONTH NOT BETWEEN 1 AND 4"
                        .formatted(table), Integer.class);
    }

    private Integer inconsistentYearMonth(String table) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM MOHECORE.%s
                WHERE STUDENT_FIN_RECIEVED_YEAR_MONTH
                      <> STUDENT_FIN_RECIEVED_YEAR * 10 + STUDENT_FIN_RECIEVED_MONTH
                """.formatted(table), Integer.class);
    }
}
