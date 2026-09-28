package com.dilaw.mohe.service;

import com.dilaw.mohe.model.GrowthAnalysis;
import com.dilaw.mohe.repository.GrowthAnalysisRepository;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class GrowthAnalysisService {

    @Autowired
    private GrowthAnalysisRepository growthAnalysisRepository;

    @Autowired
    private ReportCountryScopeHelper countryScopeHelper;

    @Autowired
    private com.dilaw.mohe.repository.LuTrendClassRepository trendClasses;

    @Autowired
    private org.springframework.context.MessageSource messages;

    /**
     * The view's status words, read as the GROWTH_STATUS trend classes (the page's own
     * mapping since live QA L1).
     */
    private static final java.util.Map<String, String> STATUS_CLASS = java.util.Map.of(
            "growing", "GROWTH", "growth", "GROWTH",
            "declining", "DECLINE", "decline", "DECLINE",
            "stable", "STABLE");

    /**
     * The export's rows with the category and the status named in {@code lang} (FIX-PLAN
     * P5-10: the PDF printed {@code total_students} and {@code growing}). The status is
     * named from the GROWTH_STATUS trend classes; the category from the message key
     * {@code report.growth.category.<code>}; a value with no name prints as itself.
     */
    public java.util.List<com.dilaw.mohe.dto.GrowthAnalysisReportRow> reportRows(List<GrowthAnalysis> items, String lang) {
        boolean english = "en".equalsIgnoreCase(lang);
        java.util.Locale locale = english ? java.util.Locale.ENGLISH : java.util.Locale.forLanguageTag("ar");
        java.util.Map<String, String> statusNames = new java.util.HashMap<>();
        for (com.dilaw.mohe.model.LuTrendClass c : trendClasses.findByCategoryAndIsDeletedFalseOrderBySortOrderAsc("GROWTH_STATUS")) {
            String name = english ? firstNonBlank(c.getNameEn(), c.getNameAr()) : firstNonBlank(c.getNameAr(), c.getNameEn());
            if (c.getCode() != null && name != null) statusNames.put(c.getCode().toUpperCase(java.util.Locale.ROOT), name);
        }
        return items.stream().map(i -> {
            String status = i.getGrowthStatus();
            String statusClass = status == null ? null : STATUS_CLASS.get(status.trim().toLowerCase(java.util.Locale.ROOT));
            // A status with no trend class (RPT-07's 'baseline', the first year, which has
            // nothing to be compared with) is named from report.growth.status.<code>.
            String statusLabel = statusClass != null ? statusNames.getOrDefault(statusClass, status)
                    : status == null ? null
                    : messages.getMessage("report.growth.status." + status.trim().toLowerCase(java.util.Locale.ROOT),
                            null, status, locale);
            String category = i.getGrowthCategory();
            String categoryLabel = category == null ? null
                    : messages.getMessage("report.growth.category." + category.trim().toLowerCase(java.util.Locale.ROOT),
                            null, category, locale);
            return new com.dilaw.mohe.dto.GrowthAnalysisReportRow(category, categoryLabel, i.getAnalysisYear(),
                    i.getGrowthRate(), status, statusLabel);
        }).toList();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b != null && !b.isBlank() ? b : null;
    }

    public List<GrowthAnalysis> getAllGrowthAnalysis(Integer countryId, List<Integer> requestCountryIds) {
        return search(countryId, requestCountryIds, null, null, null, null, null);
    }

    public List<GrowthAnalysis> getGrowthByCategory(Integer countryId, String category) {
        return search(countryId, null, category, null, null, null, null);
    }

    public List<GrowthAnalysis> getGrowthByStatus(Integer countryId, String status) {
        return search(countryId, null, null, status, null, null, null);
    }

    public List<GrowthAnalysis> getGrowthByYearRange(Integer countryId, Integer startYear, Integer endYear) {
        return search(countryId, null, null, null, startYear, endYear, null);
    }

    public List<GrowthAnalysis> getGrowthByMinimumRate(Integer countryId, BigDecimal minRate) {
        return search(countryId, null, null, null, null, null, minRate);
    }

    /**
     * Growth categories, derived from the rows the report actually returns.
     *
     * <p>This used to read {@code V_Growth_Analysis}, the legacy view that the
     * growth rows no longer come from — they come from the native year-over-year
     * query in {@link com.dilaw.mohe.repository.GrowthAnalysisRepository}. The
     * two had drifted, so the filter listed categories no row could match and
     * choosing one emptied the report.
     */
    public List<String> getAllGrowthCategories(Integer countryId) {
        return getAllGrowthAnalysis(countryId, null).stream()
                .map(GrowthAnalysis::getGrowthCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private List<GrowthAnalysis> search(Integer countryId,
                                        List<Integer> requestCountryIds,
                                        String category,
                                        String status,
                                        Integer startYear,
                                        Integer endYear,
                                        BigDecimal minRate) {
        countryScopeHelper.ensureScopeOrThrow();
        List<Integer> countryIds = countryScopeHelper.resolveCountryIds(countryId, requestCountryIds);
        boolean applyCountry = countryScopeHelper.shouldApplyCountryFilter(countryIds);
        return growthAnalysisRepository.search(
                applyCountry,
                countryScopeHelper.safeList(countryIds),
                category,
                status,
                startYear,
                endYear,
                minRate);
    }
}
