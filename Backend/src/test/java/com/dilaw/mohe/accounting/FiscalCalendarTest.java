package com.dilaw.mohe.accounting;

import com.dilaw.mohe.accounting.model.FiscalPeriod;
import com.dilaw.mohe.accounting.model.FiscalYear;
import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The fiscal calendar: periods, and the fact that closing one means something.
 *
 * <p>Rolled back — {@link #closingAYearClosesItsPeriods} really does close a year.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class FiscalCalendarTest {

    @Autowired private FiscalPeriodService calendar;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.dilaw.mohe.accounting.controller.FundChainController fundChain;

    /** The furthest year that may be opened; no database has it yet. */
    private static int yearAhead() {
        return LocalDate.now().getYear() + 5;
    }

    @Test
    @DisplayName("a new fiscal year opens with its twelve months and the adjustment period (W4-01)")
    void openingAYearLaysOutItsPeriods() {
        FiscalYear year = calendar.openYear(yearAhead(), 1);

        assertThat(year.getStartDate()).isEqualTo(LocalDate.of(yearAhead(), 1, 1));
        assertThat(year.getEndDate()).isEqualTo(LocalDate.of(yearAhead(), 12, 31));
        assertThat(year.getStatus()).isEqualTo(FiscalYear.OPEN);
        List<FiscalPeriod> periods = calendar.periodsOf(year.getId());
        assertThat(periods).hasSize(13);
        assertThat(periods.get(1).getEndDate()).as("February ends on its own last day")
                .isEqualTo(LocalDate.of(yearAhead(), 2, 1).withDayOfMonth(LocalDate.of(yearAhead(), 2, 1).lengthOfMonth()));
        assertThat(periods.get(12).getAdjustment()).isTrue();
        assertThat(calendar.requireYearOfQuarter(yearAhead() * 10 + 2).getId())
                .as("its quarters now have a year").isEqualTo(year.getId());
    }

    @Test
    @DisplayName("a year that exists, or one far off, is refused in the reader's language")
    void openingAYearIsChecked() {
        int existing = calendar.years().get(0).getYearNo();
        assertThatThrownBy(() -> calendar.openYear(existing, 1))
                .isInstanceOf(com.dilaw.mohe.exception.LocalizedStatusException.class)
                .satisfies(e -> assertThat(((com.dilaw.mohe.exception.LocalizedMessage) e).messageKey())
                        .isEqualTo("accounting.fiscalYear.exists"))
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(409));
        assertThatThrownBy(() -> calendar.openYear(yearAhead() + 1, 1))
                .satisfies(e -> assertThat(((com.dilaw.mohe.exception.LocalizedMessage) e).messageKey())
                        .isEqualTo("accounting.fiscalYear.range"));
        assertThatThrownBy(() -> calendar.openYear(null, 1))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a quarter with no fiscal year is refused with a message that says which year to open")
    void aQuarterWithoutAYearIsExplained() {
        assertThatThrownBy(() -> calendar.requireYearOfQuarter(20991))
                .isInstanceOf(com.dilaw.mohe.exception.LocalizedStatusException.class)
                .satisfies(e -> {
                    com.dilaw.mohe.exception.LocalizedMessage m = (com.dilaw.mohe.exception.LocalizedMessage) e;
                    assertThat(m.messageKey()).isEqualTo("accounting.fiscalYear.missing");
                    assertThat(m.messageArgs()).containsExactly(2099, 1);
                });
    }

    @Test
    @DisplayName("opening a year needs FISCAL_YEAR.ADD, not just the accounting pages")
    void openingAYearIsGated() {
        assertThatThrownBy(() -> com.dilaw.mohe.scope.AsUser.callWithAuthorities(com.dilaw.mohe.scope.AsUser.SUPERADMIN,
                List.of("ACCOUNTING.VIEW", "ACCOUNTING.EDIT"),
                () -> fundChain.openYear(java.util.Map.of("yearNo", yearAhead()))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        var created = com.dilaw.mohe.scope.AsUser.callWithAuthorities(com.dilaw.mohe.scope.AsUser.SUPERADMIN,
                List.of("ACCOUNTING.VIEW", "FISCAL_YEAR.ADD"),
                () -> fundChain.openYear(java.util.Map.of("yearNo", yearAhead())));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().getYearNo()).isEqualTo(yearAhead());
    }

    @Test
    @DisplayName("each year has twelve months plus an adjustment period")
    void thirteenPeriodsPerYear() {
        for (FiscalYear year : calendar.years()) {
            List<FiscalPeriod> periods = calendar.periodsOf(year.getId());

            assertThat(periods)
                    .as("year %s", year.getYearNo())
                    .hasSize(13);
            assertThat(periods.stream().filter(p -> Boolean.TRUE.equals(p.getAdjustment())).toList())
                    .as("""
                        exactly one adjustment period (13) for قيود التسوية. Without it, an \
                        adjustment agreed in March against last year has nowhere to go except \
                        December, where it changes a month that was already reported.""")
                    .hasSize(1);
        }
    }

    @Test
    @DisplayName("the twelve months tile the year with no gap and no overlap")
    void monthsAreContiguous() {
        FiscalYear year = calendar.years().get(0);
        List<FiscalPeriod> months = calendar.periodsOf(year.getId()).stream()
                .filter(p -> p.getPeriodNo() <= 12)
                .toList();

        assertThat(months.get(0).getStartDate()).isEqualTo(year.getStartDate());
        assertThat(months.get(11).getEndDate()).isEqualTo(year.getEndDate());

        for (int i = 0; i < months.size() - 1; i++) {
            assertThat(months.get(i).getEndDate().plusDays(1))
                    .as("period %d must end the day before period %d begins",
                            months.get(i).getPeriodNo(), months.get(i + 1).getPeriodNo())
                    .isEqualTo(months.get(i + 1).getStartDate());
        }
    }

    @Test
    @DisplayName("a date resolves to its month, never to the adjustment period")
    void datesResolveToCalendarPeriods() {
        FiscalPeriod march = calendar.periodFor(LocalDate.of(2026, 3, 15));
        assertThat(march.getPeriodNo()).isEqualTo((short) 3);
        assertThat(march.getAdjustment()).isFalse();

        // Period 13 shares 31 December with period 12, so a naive date lookup
        // would find two rows for that one day.
        FiscalPeriod newYearsEve = calendar.periodFor(LocalDate.of(2026, 12, 31));
        assertThat(newYearsEve.getPeriodNo())
                .as("an adjustment is chosen deliberately, never fallen into by date")
                .isEqualTo((short) 12);
    }

    @Test
    @DisplayName("nothing may be posted into a closed period")
    void closedPeriodsRefusePostings() {
        FiscalYear year = calendar.years().stream()
                .filter(y -> y.getYearNo() == 2026).findFirst().orElseThrow();
        FiscalPeriod march = calendar.periodsOf(year.getId()).stream()
                .filter(p -> p.getPeriodNo() == 3).findFirst().orElseThrow();

        calendar.assertPeriodOpen(LocalDate.of(2026, 3, 15));   // open: no exception

        calendar.closePeriod(march.getId(), 1);

        assertThatThrownBy(() -> calendar.assertPeriodOpen(LocalDate.of(2026, 3, 15)))
                .as("""
                    this is the guard the ledger round calls before every posting. Without it a \
                    correction entered today can land in a year whose final account has already \
                    gone to COCA.""")
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("closing a year closes every period in it")
    void closingAYearClosesItsPeriods() {
        FiscalYear year = calendar.years().stream()
                .filter(y -> y.getYearNo() == 2024).findFirst().orElseThrow();

        calendar.closeYear(year.getId(), 1);

        assertThat(calendar.periodsOf(year.getId()))
                .as("""
                    the periods must go first — the database refuses to leave one open inside a \
                    closed year, so closing the year with a period still open would simply fail.""")
                .allSatisfy(p -> assertThat(p.getStatus()).isEqualTo(FiscalPeriod.CLOSED));
        assertThat(calendar.requireYear(year.getId()).getStatus()).isEqualTo(FiscalYear.CLOSED);
    }

    @Test
    @DisplayName("reopening a closed period needs a deliberate override")
    void reopeningIsNotAnOrdinaryUpdate() {
        FiscalYear year = calendar.years().stream()
                .filter(y -> y.getYearNo() == 2025).findFirst().orElseThrow();
        FiscalPeriod period = calendar.periodsOf(year.getId()).stream()
                .filter(p -> p.getPeriodNo() == 5).findFirst().orElseThrow();

        // Closed through JDBC rather than the service: the trigger compares the
        // row's stored status against the new one, so the close has to have
        // reached the database first. Closing via JPA inside this transaction
        // leaves it in the persistence context, and the reopen below then finds
        // a row that is still OPEN and succeeds — which is what this test did on
        // its first run, reporting the rule as broken when it was the test that
        // was wrong.
        jdbc.update("""
                UPDATE FINCORE.FISCAL_PERIOD
                SET STATUS = 'CLOSED', CLOSED_AT = SYSUTCDATETIME()
                WHERE FISCAL_PERIOD_ID = ?
                """, period.getId());

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE FINCORE.FISCAL_PERIOD SET STATUS = 'OPEN' WHERE FISCAL_PERIOD_ID = ?", period.getId()))
                .as("""
                    closing is one-way. Reopening needs SESSION_CONTEXT('fincore_allow_reopen'), \
                    which no service sets — so it is an explicit administrative act rather than \
                    an UPDATE that happens to succeed.""")
                .isInstanceOf(Exception.class);
    }
}
