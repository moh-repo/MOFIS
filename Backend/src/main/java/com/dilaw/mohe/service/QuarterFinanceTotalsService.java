package com.dilaw.mohe.service;

import com.dilaw.mohe.repository.StudentFinanceArchRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What each MOHE quarter is worth, summed from the student finance tables.
 *
 * <p>The figure used to come from {@code MOHE_QUARTER.QUARTER_AMOUNT}, a column
 * that was typed in by hand and held {@code 0} on all twelve rows — so the screen
 * showed nothing for quarters that had in fact moved millions. It is now derived,
 * every time it is read, from the rows the money actually lives in.
 *
 * <h2>Which table a quarter is read from</h2>
 *
 * {@code STUDENT_FINANCE} is the working table: it holds the quarter (or quarters)
 * currently being paid. When a quarter rolls over it is snapshotted into
 * {@code STUDENT_FINANCE_ARCH} and the live rows are advanced to the new quarter,
 * so history exists only in the archive.
 *
 * <p>The rule is therefore <b>live wins where live has rows</b>: a quarter present
 * in {@code STUDENT_FINANCE} is summed from there, and every other quarter falls
 * back to the archive. Stated as "the latest quarter comes from the live table and
 * older ones from the archive", which is the same rule whenever the live table
 * holds exactly one quarter — but this database holds two (2024 Q2 and 2025 Q1),
 * and picking only the maximum would push 2024 Q2 to the archive, where it is
 * recorded differently. See the note below.
 *
 * <p>This matters because the two disagree. For 2024 Q2 the live table has
 * 1,571,220 with 37,160 in Wafar; the archive has 1,574,580 with no Wafar at all.
 * The live row is the one carrying the Wafar split, so it is the one that wins.
 * The archive is also, in this database, a replicated snapshot rather than real
 * history — every student carries the identical amount in all four quarters of
 * 2024 — which is a known source-data problem
 * and not something this service tries to paper over.
 *
 * <h2>Country scope</h2>
 *
 * Every {@code MOHE_QUARTER} row is currently global ({@code COUNTRY_ID IS NULL}),
 * so without scoping here an officer at one attaché would read the ministry-wide
 * total — the row-level security policy that would otherwise catch this ships
 * {@code STATE = OFF}. The sums are therefore filtered to the caller's own
 * countries, and only a ministry user sums across all of them.
 */
@Transactional(readOnly = true)
@Service
public class QuarterFinanceTotalsService {

    @Autowired private StudentFinanceRepository financeRepository;
    @Autowired private StudentFinanceArchRepository financeArchRepository;
    @Autowired private com.dilaw.mohe.accounting.repository.AttacheAllocationRepository allocationRepository;

    /**
     * One quarter's totals.
     *
     * @param received     gross entitlement — {@code SUM(STUDENT_FIN_RECIEVED_AMOUNT)}
     * @param wafar        the part that was not paid out and became Wafar
     * @param net          {@code received - wafar}: what actually reached students
     * @param studentCount rows behind the figure
     * @param source       {@code LIVE} or {@code ARCHIVE} — which table it came from
     */
    public record QuarterTotals(long received, long wafar, long net, int studentCount, Source source) {
        public enum Source { LIVE, ARCHIVE }
    }

    /**
     * Totals keyed by {@code YYYYQ} (2024 Q2 → {@code 20242}), the same key
     * {@code MOHE_QUARTER.QUARTER} uses.
     *
     * <p>Read once per screen, not once per quarter: two grouped queries cover
     * every row the list will show.
     */
    public Map<Integer, QuarterTotals> totalsByQuarter(CountryScope scope) {
        // No scope means nothing is visible at all; the caller has no rows to
        // decorate, and an empty IN list is a SQL Server syntax error.
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            return Map.of();
        }
        int unscoped = scope.isMinistryUser() ? 1 : 0;
        // Never empty, and ignored entirely when unscoped = 1.
        List<Integer> countryIds = scope.isMinistryUser() ? List.of(-1) : scope.getAllowedCountryIds();

        Map<Integer, QuarterTotals> totals = new HashMap<>();
        // Archive first, then the live table over the top of it: live wins.
        collect(financeArchRepository.sumByQuarter(unscoped, countryIds), QuarterTotals.Source.ARCHIVE, totals);
        collect(financeRepository.sumByQuarter(unscoped, countryIds), QuarterTotals.Source.LIVE, totals);
        return totals;
    }

    private static void collect(List<Object[]> rows, QuarterTotals.Source source, Map<Integer, QuarterTotals> into) {
        for (Object[] r : rows) {
            int year = ((Number) r[0]).intValue();
            int quarter = ((Number) r[1]).intValue();
            into.put(quarterKey(year, quarter), new QuarterTotals(
                    ((Number) r[2]).longValue(),
                    ((Number) r[3]).longValue(),
                    ((Number) r[4]).longValue(),
                    ((Number) r[5]).intValue(),
                    source));
        }
    }

    /**
     * Budget transferred to the caller's attachés, keyed by {@code YYYYQ}.
     *
     * <p>{@code MOHE_QUARTER.BUDGET_AMOUNT} holds the ministry-wide total, written
     * by {@code AllocationService.rollUpToQuarter}. That figure is correct and
     * stays as it is — but showing it to a country officer answers a question
     * about every other attaché's budget. This recomputes it per scope.
     */
    public Map<Integer, java.math.BigDecimal> budgetByQuarter(CountryScope scope) {
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            return Map.of();
        }
        boolean unscoped = scope.isMinistryUser();
        List<Integer> countryIds = unscoped ? List.of(-1) : scope.getAllowedCountryIds();

        Map<Integer, java.math.BigDecimal> budgets = new HashMap<>();
        for (Object[] row : allocationRepository.sumTransferredByQuarter(unscoped, countryIds)) {
            budgets.put(((Number) row[0]).intValue(), (java.math.BigDecimal) row[1]);
        }
        return budgets;
    }

    /** {@code YYYYQ}, as used by {@code MOHE_QUARTER.QUARTER} and {@code STUDENT_FIN_RECIEVED_YEAR_MONTH}. */
    public static int quarterKey(int year, int quarter) {
        return year * 10 + quarter;
    }
}
