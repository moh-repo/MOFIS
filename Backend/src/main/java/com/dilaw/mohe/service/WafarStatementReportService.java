package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.codes.WafarSpendingStatus;
import com.dilaw.mohe.dto.WafarReportMetadataDto;
import com.dilaw.mohe.dto.WafarStatementRowDto;
import com.dilaw.mohe.dto.WafarStatementSummaryDto;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import com.dilaw.mohe.wafar.dto.WafarSpendingCategoryDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingDto;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import com.dilaw.mohe.wafar.service.WafarSpendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The Wafar statement: what an attaché saved, what it spent, and on what.
 *
 * <p>Wafar is budget already transferred to one attaché and never disbursed, so
 * it stays there — Cairo's savings pay only Cairo. The module has had three
 * screens and no printable output since it was built; this is that output.
 *
 * <p><b>Scope is inherited, not re-implemented.</b> {@link WafarSpendingService}
 * and {@link WafarBalanceService} already narrow to the caller's attachés, and
 * this service reads through them rather than going to the repositories itself.
 * A second copy of a scope rule is a second thing to keep in step, and the two
 * would diverge exactly when it mattered. The one thing added here is the
 * optional per-country narrowing a report needs, applied through
 * {@link ReportCountryScopeHelper} so an out-of-scope {@code countryId} is
 * refused rather than silently ignored.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class WafarStatementReportService {

    private final WafarSpendingService spendingService;
    private final WafarBalanceService balanceService;
    private final CountryNameService countryNameService;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final CountryScopeService countryScopeService;
    private final CodeListService codeLists;

    private static final String ARABIC = "ar";
    /** The code list that names a Wafar spend's status (LU_WAFAR_SPENDING_STATUS). */
    static final String STATUS_LIST = "wafar-spending-status";

    // ── Metadata ─────────────────────────────────────────────────────────────

    public WafarReportMetadataDto metadata(String lang) {
        List<WafarSpendingCategoryDto> categories = spendingService.getActiveCategories();

        // Statuses present in the caller's own rows, not every value the column
        // permits: offering a filter that can only ever return nothing is worse
        // than not offering it.
        List<String> statuses = spendingService.getAll(null, null, null, null, null).stream()
                .map(WafarSpendingDto::getStatus)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        return WafarReportMetadataDto.builder()
                .categories(categories)
                .statuses(statuses)
                .countries(visibleCountries(lang))
                .build();
    }

    // ── Rows ─────────────────────────────────────────────────────────────────

    public List<WafarStatementRowDto> rows(Integer countryId, List<Integer> countryIds,
                                           Integer categoryId, String status,
                                           LocalDate dateFrom, LocalDate dateTo, String lang) {
        // Validates the requested country against the caller's scope; null means
        // "no further narrowing", which for a ministry user is every attaché.
        List<Integer> resolved = countryScopeHelper.resolveCountryIds(countryId, countryIds);

        List<WafarSpendingDto> spending =
                spendingService.getAll(categoryId, status, null, dateFrom, dateTo);

        Map<Integer, String> countryNames = countryNameMap(lang);

        return spending.stream()
                .filter(s -> matchesCountry(s, resolved))
                .map(s -> toRow(s, countryNames, lang))
                .sorted(Comparator.comparing(WafarStatementRowDto::getSpendingDate,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WafarStatementRowDto::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    // ── Summary ──────────────────────────────────────────────────────────────

    public WafarStatementSummaryDto summary(Integer countryId, List<Integer> countryIds,
                                            Integer categoryId, String status,
                                            LocalDate dateFrom, LocalDate dateTo, String lang) {
        var balance = balanceService.getSummary();
        List<WafarStatementRowDto> rows = rows(countryId, countryIds, categoryId, status, dateFrom, dateTo, lang);

        return WafarStatementSummaryDto.builder()
                .totalDeposits(balance.getTotalDeposits())
                .totalSpent(balance.getTotalSpent())
                .currentBalance(balance.getCurrentBalance())
                .pendingAmount(balance.getPendingAmount())
                .transactionCount(rows.size())
                .categoryTotals(categoryTotals(rows))
                .build();
    }

    private List<WafarStatementSummaryDto.CategoryTotal> categoryTotals(List<WafarStatementRowDto> rows) {
        Map<String, List<WafarStatementRowDto>> byCategory = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getCategoryName() == null ? "" : r.getCategoryName(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<WafarStatementSummaryDto.CategoryTotal> totals = new ArrayList<>();
        for (Map.Entry<String, List<WafarStatementRowDto>> e : byCategory.entrySet()) {
            List<WafarStatementRowDto> group = e.getValue();
            totals.add(WafarStatementSummaryDto.CategoryTotal.builder()
                    .categoryId(group.get(0).getCategoryId())
                    .categoryName(e.getKey())
                    .transactionCount(group.size())
                    .approvedAmount(sumWhere(group, WafarSpendingStatus.APPROVED))
                    .pendingAmount(sumWhere(group, WafarSpendingStatus.PENDING))
                    .totalAmount(group.stream()
                            .map(WafarStatementRowDto::getAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                    .build());
        }
        totals.sort(Comparator.comparing(WafarStatementSummaryDto.CategoryTotal::getCategoryName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return totals;
    }

    private BigDecimal sumWhere(List<WafarStatementRowDto> rows, String status) {
        return rows.stream()
                .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                .map(WafarStatementRowDto::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private WafarStatementRowDto toRow(WafarSpendingDto s, Map<Integer, String> countryNames, String lang) {
        boolean arabic = isArabic(lang);
        var category = s.getCategory();
        return WafarStatementRowDto.builder()
                .id(s.getId())
                .spendingDate(s.getSpendingDate())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category == null ? null
                        : pick(arabic, category.getCategoryNameAr(), category.getCategoryNameEn()))
                .referenceNumber(s.getReferenceNumber())
                .description(pick(arabic, s.getDescriptionAr(), s.getDescriptionEn()))
                .payeeName(pick(arabic, s.getPayeeNameAr(), s.getPayeeNameEn()))
                .studentId(s.getStudentId())
                .studentName(s.getStudentName())
                .beneficiaryType(s.getBeneficiaryType())
                .studyCountryInId(s.getStudyCountryInId())
                .studyCountryName(s.getStudyCountryInId() == null ? null
                        : countryNames.get(s.getStudyCountryInId()))
                .amount(s.getAmount())
                .status(s.getStatus())
                // The statement printed the raw code (APPROVED, PENDING…) in both
                // languages; the code stays for the totals, the label is printed.
                .statusLabel(s.getStatus() == null ? null
                        : codeLists.label(STATUS_LIST, s.getStatus(), arabic ? ARABIC : "en"))
                .build();
    }

    /**
     * A row with no country is not one attaché's, so it only belongs on a
     * statement that covers every attaché — the same rule
     * {@code CountryColumnSpec.countryScope} applies. Narrowing to a country
     * therefore drops it rather than including it everywhere.
     */
    private boolean matchesCountry(WafarSpendingDto s, List<Integer> resolved) {
        if (resolved == null) {
            return true;
        }
        return s.getStudyCountryInId() != null && resolved.contains(s.getStudyCountryInId());
    }

    private List<WafarReportMetadataDto.CountryOption> visibleCountries(String lang) {
        var scope = countryScopeService.getScope();
        return countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .map(c -> WafarReportMetadataDto.CountryOption.builder()
                        .id(c.id())
                        .name(countryName(c, lang))
                        .build())
                .sorted(Comparator.comparing(WafarReportMetadataDto.CountryOption::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Map<Integer, String> countryNameMap(String lang) {
        return countryNameService.all().stream()
                .collect(Collectors.toMap(CountryNameService.CountryRef::id, c -> countryName(c, lang),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String countryName(CountryNameService.CountryRef c, String lang) {
        return pick(isArabic(lang), c.nameAr(), c.nameEn());
    }

    private static boolean isArabic(String lang) {
        return lang == null || ARABIC.equalsIgnoreCase(lang);
    }

    /** Falls back to the other language rather than printing a blank cell. */
    private static String pick(boolean arabic, String ar, String en) {
        String first = arabic ? ar : en;
        String second = arabic ? en : ar;
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}
