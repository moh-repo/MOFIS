package com.dilaw.mohe.service;

import com.dilaw.mohe.accounting.model.AttacheAdvanceBalance;
import com.dilaw.mohe.accounting.model.AttacheAllocation;
import com.dilaw.mohe.accounting.model.FiscalYear;
import com.dilaw.mohe.accounting.repository.AttacheAllocationRepository;
import com.dilaw.mohe.accounting.service.AdvanceBalanceService;
import com.dilaw.mohe.accounting.service.FiscalPeriodService;
import com.dilaw.mohe.dto.AccountingPositionRowDto;
import com.dilaw.mohe.dto.AccountingReportMetadataDto;
import com.dilaw.mohe.model.StudyCurrencyIn;
import com.dilaw.mohe.repository.StudyCurrencyInRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The accounting position, by attaché and currency, for one fiscal year.
 *
 * <p>The accounting module was built over several rounds and has never had a
 * printable statement. {@code ATTACHE_ADVANCE_BALANCE} already holds the shape
 * of one — opening, transferred, disbursed, returned, Wafar earmarked and spent,
 * and an {@code available} column SQL Server computes — so this report reads that
 * rather than recomputing it. A report that derives its own totals is a second
 * opinion about the ledger, and the ledger should only have one.
 *
 * <p>Allocations are joined on for context: how much was raised against the year
 * versus how much actually moved. Scope comes from
 * {@link AdvanceBalanceService#forYear} and the allocation repository is filtered
 * to the same attachés here.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class AccountingPositionReportService {

    private final AdvanceBalanceService advanceBalanceService;
    private final FiscalPeriodService fiscalPeriodService;
    private final AttacheAllocationRepository allocationRepository;
    private final CountryNameService countryNameService;
    private final StudyCurrencyInRepository studyCurrencyInRepository;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final CountryScopeService countryScopeService;

    public AccountingReportMetadataDto metadata(String lang) {
        List<FiscalYear> years = fiscalPeriodService.years();
        return AccountingReportMetadataDto.builder()
                .fiscalYears(years.stream()
                        .map(y -> AccountingReportMetadataDto.FiscalYearOption.builder()
                                .id(y.getId())
                                .name(fiscalYearName(y, lang))
                                .status(y.getStatus())
                                .build())
                        .toList())
                .countries(visibleCountries(lang))
                .build();
    }

    /**
     * @param fiscalYearId required — a position is always as at a year; without
     *        one the report would be adding unrelated years together
     */
    public List<AccountingPositionRowDto> rows(Integer fiscalYearId, Integer countryId,
                                               List<Integer> countryIds, String lang) {
        if (fiscalYearId == null) {
            throw new IllegalArgumentException("A fiscal year is required for the accounting position report.");
        }

        List<Integer> resolved = countryScopeHelper.resolveCountryIds(countryId, countryIds);

        List<AttacheAdvanceBalance> balances = advanceBalanceService.forYear(fiscalYearId).stream()
                .filter(b -> resolved == null || resolved.contains(b.getStudyCountryInId()))
                .toList();

        Map<String, Allocation> allocations = allocationTotals(fiscalYearId, resolved);
        Map<Integer, String> countryNames = countryNameMap(lang);
        Map<Integer, String> currencyNames = currencyNameMap(lang);
        String yearName = fiscalYearName(fiscalPeriodService.requireYear(fiscalYearId), lang);

        return balances.stream()
                .map(b -> {
                    Allocation a = allocations.getOrDefault(key(b.getStudyCountryInId(), b.getCurrencyId()),
                            Allocation.EMPTY);
                    return AccountingPositionRowDto.builder()
                            .fiscalYearId(fiscalYearId)
                            .fiscalYearName(yearName)
                            .studyCountryInId(b.getStudyCountryInId())
                            .studyCountryName(countryNames.get(b.getStudyCountryInId()))
                            .currencyId(b.getCurrencyId())
                            .currencyName(currencyNames.get(b.getCurrencyId()))
                            .openingBalance(zeroIfNull(b.getOpeningBalance()))
                            .transferred(zeroIfNull(b.getTransferred()))
                            .disbursed(zeroIfNull(b.getDisbursed()))
                            .returned(zeroIfNull(b.getReturned()))
                            .wafarEarmarked(zeroIfNull(b.getWafarEarmarked()))
                            .wafarSpent(zeroIfNull(b.getWafarSpent()))
                            .wafarBalance(zeroIfNull(b.getWafarBalance()))
                            .available(zeroIfNull(b.getAvailable()))
                            .allocationCount(a.count())
                            .allocatedAmount(a.amount())
                            .build();
                })
                .sorted(Comparator
                        .comparing(AccountingPositionRowDto::getStudyCountryName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(AccountingPositionRowDto::getCurrencyName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /** Allocation count and total per attaché-and-currency, for the year. */
    private Map<String, Allocation> allocationTotals(Integer fiscalYearId, List<Integer> resolved) {
        var scope = countryScopeService.getScope();

        List<AttacheAllocation> all = countryNameService.allIds().stream()
                .filter(id -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(id))
                .filter(id -> resolved == null || resolved.contains(id))
                .flatMap(id -> allocationRepository
                        .findByFiscalYearIdAndStudyCountryInId(fiscalYearId, id).stream())
                .toList();

        Map<String, Allocation> out = new LinkedHashMap<>();
        for (AttacheAllocation a : all) {
            String k = key(a.getStudyCountryInId(), a.getCurrencyId());
            Allocation prev = out.getOrDefault(k, Allocation.EMPTY);
            out.put(k, new Allocation(prev.count() + 1,
                    prev.amount().add(zeroIfNull(a.getAmount()))));
        }
        return out;
    }

    private static String key(Integer countryId, Integer currencyId) {
        return countryId + "/" + currencyId;
    }

    private record Allocation(long count, BigDecimal amount) {
        static final Allocation EMPTY = new Allocation(0L, BigDecimal.ZERO);
    }

    private static BigDecimal zeroIfNull(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String fiscalYearName(FiscalYear y, String lang) {
        if (y == null) {
            return null;
        }
        String name = pick(isArabic(lang), y.getNameAr(), y.getNameEn());
        return name != null && !name.isBlank() ? name : String.valueOf(y.getYearNo());
    }

    private List<AccountingReportMetadataDto.CountryOption> visibleCountries(String lang) {
        var scope = countryScopeService.getScope();
        return countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .map(c -> AccountingReportMetadataDto.CountryOption.builder()
                        .id(c.id())
                        .name(pick(isArabic(lang), c.nameAr(), c.nameEn()))
                        .build())
                .sorted(Comparator.comparing(AccountingReportMetadataDto.CountryOption::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Map<Integer, String> countryNameMap(String lang) {
        return countryNameService.all().stream()
                .collect(Collectors.toMap(CountryNameService.CountryRef::id,
                        c -> pick(isArabic(lang), c.nameAr(), c.nameEn()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Integer, String> currencyNameMap(String lang) {
        return studyCurrencyInRepository.findAll().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(StudyCurrencyIn::getId,
                        c -> pick(isArabic(lang), c.getStudyCurrencyInName(), c.getStudyCurrencyInNameEn()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private static boolean isArabic(String lang) {
        return lang == null || "ar".equalsIgnoreCase(lang);
    }

    private static String pick(boolean arabic, String ar, String en) {
        String first = arabic ? ar : en;
        String second = arabic ? en : ar;
        return first != null && !first.isBlank() ? first : second;
    }

    /** Column totals for the report footer, per currency so nothing is added across them. */
    public Map<String, BigDecimal> totalsByCurrency(List<AccountingPositionRowDto> rows) {
        return rows.stream()
                .filter(r -> r.getCurrencyName() != null)
                .collect(Collectors.groupingBy(AccountingPositionRowDto::getCurrencyName,
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> Objects.requireNonNullElse(r.getAvailable(), BigDecimal.ZERO),
                                BigDecimal::add)));
    }
}
