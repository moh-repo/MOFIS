package com.dilaw.mohe.accounting.service;

import com.dilaw.mohe.accounting.model.FiscalPeriod;
import com.dilaw.mohe.accounting.model.FiscalYear;
import com.dilaw.mohe.accounting.repository.FiscalPeriodRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.accounting.repository.FiscalYearRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.dilaw.mohe.exception.LocalizedStatusException;

/**
 * Opening and closing accounting periods.
 *
 * <p>{@link #assertPeriodOpen} is the guard the ledger round will call before
 * every posting. Without it a correction entered today can land in a year whose
 * final account has already gone to COCA, and the figures that were reported
 * stop matching the figures in the database.
 *
 * <p>Closing is one-way. The database enforces that too
 * ({@code TR_FISCAL_PERIOD_CLOSE_RULES}), and reopening needs
 * {@code SESSION_CONTEXT('fincore_allow_reopen')} — which this service does not
 * set. Reopening a closed period is a deliberate administrative act, not
 * something a screen can do by accident.
 */
@Service
public class FiscalPeriodService {

    @Autowired private FiscalYearRepository yearRepository;
    @Autowired private FiscalPeriodRepository periodRepository;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * The fiscal calendar the caller may use.
     *
     * <p><b>A year with no country is shared with everyone</b>, and all three rows
     * in this database are like that — 2024 is 2024 in Cairo and in Amman. Scoping
     * these the way an advance balance is scoped would empty the fiscal-year
     * selector that every other accounting screen hangs off, and the chart of
     * accounts and advance balances pages would both go blank.
     *
     * <p>The column exists because the design allows an attaché-specific year if a
     * country ever runs one, and such a row is filtered normally.
     */
    public List<FiscalYear> years() {
        CountryScope scope = countryScopeService.getScope();
        List<FiscalYear> all = yearRepository.findAllByOrderByYearNoAsc();
        if (scope.isMinistryUser()) return all;
        List<Integer> allowed = scope.getAllowedCountryIds();
        return all.stream()
                .filter(y -> y.getStudyCountryInId() == null || allowed.contains(y.getStudyCountryInId()))
                .toList();
    }

    /**
     * The periods of one year. Scoped through the year rather than the period —
     * FISCAL_PERIOD carries no country of its own, deliberately: a period is a
     * subdivision of its year and inherits whatever that year is.
     */
    public List<FiscalPeriod> periodsOf(Integer fiscalYearId) {
        if (years().stream().noneMatch(y -> y.getId().equals(fiscalYearId))) {
            return List.of();
        }
        return periodRepository.findByFiscalYearIdOrderByPeriodNoAsc(fiscalYearId);
    }

    public FiscalYear requireYear(Integer id) {
        return yearRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fiscal year not found"));
    }

    /**
     * The period a transaction dated {@code on} belongs to.
     *
     * <p>Never returns the adjustment period: period 13 shares 31 December with
     * period 12, and an adjustment is something you choose, not something a date
     * falls into.
     */
    public FiscalPeriod periodFor(LocalDate on) {
        return periodRepository.findCalendarPeriodFor(on)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No fiscal period covers " + on + ". The calendar may not extend that far yet."));
    }

    /**
     * Refuses a posting into a period that is not open.
     *
     * <p>The ledger round calls this; nothing does yet, which is why it is worth
     * saying plainly that it exists to be called rather than to be admired.
     */
    public void assertPeriodOpen(LocalDate on) {
        FiscalPeriod period = periodFor(on);
        if (!FiscalPeriod.OPEN.equals(period.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Period " + period.getPeriodNo() + " is closed; nothing may be posted into " + on + ".");
        }
        FiscalYear year = requireYear(period.getFiscalYearId());
        if (!FiscalYear.OPEN.equals(year.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Fiscal year " + year.getYearNo() + " is " + year.getStatus().toLowerCase() + ".");
        }
    }

    /** How far ahead a year may be opened: a typing slip should not open 2207. */
    static final int MAX_YEARS_AHEAD = 5;

    /**
     * Opens a fiscal year: 1 January to 31 December, with twelve monthly periods
     * and the adjustment period 13, as V040 laid out the first three (review F11,
     * plan W4-01). The year is shared by every attaché, like those.
     *
     * <p>The chart of accounts is not copied: it is kept per year, and rolling it
     * forward is the chart's own decision. Allocations and batches need only the
     * year.
     */
    @Transactional
    public FiscalYear openYear(Integer yearNo, Integer userId) {
        int thisYear = LocalDate.now().getYear();
        if (yearNo == null || yearNo < 2000 || yearNo > thisYear + MAX_YEARS_AHEAD) {
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                    "A fiscal year must be between 2000 and " + (thisYear + MAX_YEARS_AHEAD) + ".",
                    "accounting.fiscalYear.range", 2000, thisYear + MAX_YEARS_AHEAD);
        }
        if (yearRepository.findByYearNo(yearNo).isPresent()) {
            throw new LocalizedStatusException(HttpStatus.CONFLICT,
                    "Fiscal year " + yearNo + " already exists.", "accounting.fiscalYear.exists", yearNo);
        }
        FiscalYear year = new FiscalYear();
        year.setYearNo(yearNo);
        year.setNameAr("السنة المالية " + yearNo);
        year.setNameEn("Fiscal Year " + yearNo);
        year.setStartDate(LocalDate.of(yearNo, 1, 1));
        year.setEndDate(LocalDate.of(yearNo, 12, 31));
        year.setStatus(FiscalYear.OPEN);
        year.setCreatedBy(userId);
        FiscalYear saved = yearRepository.save(year);

        List<FiscalPeriod> periods = new java.util.ArrayList<>(13);
        for (int month = 1; month <= 12; month++) {
            LocalDate start = LocalDate.of(yearNo, month, 1);
            periods.add(period(saved.getId(), month, "الفترة " + month + "/" + yearNo,
                    "Period " + month + "/" + yearNo, start, start.withDayOfMonth(start.lengthOfMonth()), false));
        }
        // Period 13 shares 31 December and carries no days of its own.
        LocalDate lastDay = LocalDate.of(yearNo, 12, 31);
        periods.add(period(saved.getId(), 13, "فترة التسويات " + yearNo, "Adjustment Period " + yearNo,
                lastDay, lastDay, true));
        periodRepository.saveAll(periods);
        return saved;
    }

    private static FiscalPeriod period(Integer yearId, int no, String nameAr, String nameEn,
                                       LocalDate start, LocalDate end, boolean adjustment) {
        FiscalPeriod p = new FiscalPeriod();
        p.setFiscalYearId(yearId);
        p.setPeriodNo((short) no);
        p.setNameAr(nameAr);
        p.setNameEn(nameEn);
        p.setStartDate(start);
        p.setEndDate(end);
        p.setStatus(FiscalPeriod.OPEN);
        p.setAdjustment(adjustment);
        return p;
    }

    /**
     * The fiscal year of a MOHE quarter (YYYYQ), or a refusal in the reader's
     * language that says which year to open (W4-01): it was raw English on the
     * allocation and batch screens.
     */
    public FiscalYear requireYearOfQuarter(Integer quarterKey) {
        int yearNo = quarterKey / 10;
        return yearRepository.findByYearNo(yearNo)
                .orElseThrow(() -> new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                        "No fiscal year covers quarter " + quarterKey + ".",
                        "accounting.fiscalYear.missing", yearNo, quarterKey % 10));
    }

    /**
     * Refuses (400) a quarter key with no row in {@code MOHECORE.MOHE_QUARTER} (round-5 QA
     * R5-20). An allocation or a payment batch for such a quarter reached the foreign key,
     * and the answer was the delete message, "still used by other records". Kept apart
     * from {@link #requireYearOfQuarter}: a fiscal year is opened before its MOHE quarters
     * are generated.
     */
    public void requireMoheQuarter(Integer quarterKey) {
        Integer rows = quarterKey == null ? Integer.valueOf(0) : jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.MOHE_QUARTER WHERE QUARTER = ?", Integer.class, quarterKey);
        if (rows == null || rows == 0) {
            String year = quarterKey == null ? "?" : String.valueOf(quarterKey / 10);
            String quarter = quarterKey == null ? "?" : String.valueOf(quarterKey % 10);
            throw new LocalizedStatusException(HttpStatus.BAD_REQUEST,
                    "Quarter " + quarter + " of " + year + " is not in the MOHE quarter list yet. Create it first.",
                    "accounting.quarter.missing", year, quarter);
        }
    }

    @Transactional
    public FiscalPeriod closePeriod(Integer periodId, Integer userId) {
        FiscalPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Period not found"));
        if (FiscalPeriod.CLOSED.equals(period.getStatus())) {
            return period;
        }
        period.setStatus(FiscalPeriod.CLOSED);
        period.setClosedBy(userId);
        period.setClosedAt(Instant.now());
        return periodRepository.save(period);
    }

    /**
     * Closes a year, and every period in it.
     *
     * <p>The periods have to go first: the database refuses to leave a period
     * open inside a closed year, so closing the year while a period is still open
     * would simply fail.
     */
    @Transactional
    public FiscalYear closeYear(Integer fiscalYearId, Integer userId) {
        List<FiscalPeriod> periods = periodsOf(fiscalYearId);
        for (FiscalPeriod period : periods) {
            if (FiscalPeriod.OPEN.equals(period.getStatus())) {
                period.setStatus(FiscalPeriod.CLOSED);
                period.setClosedBy(userId);
                period.setClosedAt(Instant.now());
            }
        }
        periodRepository.saveAll(periods);

        FiscalYear year = requireYear(fiscalYearId);
        year.setStatus(FiscalYear.CLOSED);
        year.setClosedBy(userId);
        year.setClosedAt(Instant.now());
        return yearRepository.save(year);
    }
}
