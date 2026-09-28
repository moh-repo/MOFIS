package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.AttacheSummaryRowDto;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.RequiredArgsConstructor;
import com.dilaw.mohe.util.LiveStudentSql;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * One page per attaché: students, money, banks and the rules it has set itself.
 *
 * <p>This is the natural readout of the country-scope rounds — the question
 * "what does each attaché actually hold" had no single answer anywhere.
 *
 * <p><b>Read this with the data in mind.</b> All 941 students are currently in
 * Egypt, so one row carries every figure and the others are zeros. The zeros are
 * useful — they are how you see that Jordan and Saudi have banks but no branches
 * — but nobody should expect a comparison from this report yet.
 *
 * <p>Counted with one grouped query per fact rather than by loading entities.
 * These are counts over whole tables; fetching 941 students to size a list would
 * be slower and would say nothing extra.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class AttacheSummaryReportService {

    private final JdbcTemplate jdbc;
    private final CountryNameService countryNameService;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final CountryScopeService countryScopeService;

    public List<AttacheSummaryRowDto> rows(Integer countryId, List<Integer> countryIds, String lang) {
        List<Integer> resolved = countryScopeHelper.resolveCountryIds(countryId, countryIds);
        var scope = countryScopeService.getScope();

        // Deleted students are not counted (live QA H7: Egypt showed 942 with one deleted).
        Map<Integer, Long> students = countBy(
                "SELECT sd.STUDY_COUNTRY_IN AS k, COUNT(*) AS n FROM MOHECORE.STUDENT_DATA sd "
                        + "WHERE sd.STUDY_COUNTRY_IN IS NOT NULL AND " + LiveStudentSql.notDeleted("sd")
                        + " GROUP BY sd.STUDY_COUNTRY_IN");
        Map<Integer, Long> activeStudents = countBy(
                "SELECT s.STUDY_COUNTRY_IN AS k, COUNT(*) AS n FROM MOHECORE.STUDENT_DATA s "
                        + "JOIN MOHECORE.STUDENT_STUDY_STATUS st ON st.STUDENT_STUDY_STATUS_ID = s.STUDENT_STUDY_STATUS_ID "
                        + "WHERE s.STUDY_COUNTRY_IN IS NOT NULL AND ISNULL(st.IS_ACTIVE, 1) = 1 "
                        + "AND " + LiveStudentSql.notDeleted("s") + " GROUP BY s.STUDY_COUNTRY_IN");
        Map<Integer, Long> financeCounts = countBy(
                "SELECT s.STUDY_COUNTRY_IN AS k, COUNT(*) AS n FROM MOHECORE.STUDENT_FINANCE f "
                        + "JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = f.STUDENT_ID "
                        + "WHERE s.STUDY_COUNTRY_IN IS NOT NULL AND " + LiveStudentSql.notDeleted("s")
                        + " GROUP BY s.STUDY_COUNTRY_IN");
        Map<Integer, BigDecimal> financeTotals = sumBy(
                "SELECT s.STUDY_COUNTRY_IN AS k, SUM(CAST(ISNULL(f.STUDENT_FIN_RECIEVED_AMOUNT,0) AS decimal(18,2))) AS n "
                        + "FROM MOHECORE.STUDENT_FINANCE f "
                        + "JOIN MOHECORE.STUDENT_DATA s ON s.STUDENT_ID = f.STUDENT_ID "
                        + "WHERE s.STUDY_COUNTRY_IN IS NOT NULL AND " + LiveStudentSql.notDeleted("s")
                        + " GROUP BY s.STUDY_COUNTRY_IN");
        Map<Integer, Long> banks = countBy(
                "SELECT STUDY_COUNTRY_IN_ID AS k, COUNT(*) AS n FROM MOHECORE.STUDY_FINANCE_BANK "
                        + "WHERE STUDY_COUNTRY_IN_ID IS NOT NULL GROUP BY STUDY_COUNTRY_IN_ID");
        Map<Integer, Long> branches = countBy(
                "SELECT STUDY_COUNTRY_IN_ID AS k, COUNT(*) AS n FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH "
                        + "WHERE STUDY_COUNTRY_IN_ID IS NOT NULL GROUP BY STUDY_COUNTRY_IN_ID");
        Map<Integer, Long> yearRules = countBy(
                "SELECT STUDY_COUNTRY_IN_ID AS k, COUNT(*) AS n FROM MOHECORE.STUDY_YEARS_LOOKUP "
                        + "WHERE STUDY_COUNTRY_IN_ID IS NOT NULL GROUP BY STUDY_COUNTRY_IN_ID");
        Map<Integer, Long> amountRules = countBy(
                "SELECT STUDY_COUNTRY_IN_ID AS k, COUNT(*) AS n FROM MOHECORE.STUDY_AMOUNT_LOOKUP "
                        + "WHERE STUDY_COUNTRY_IN_ID IS NOT NULL GROUP BY STUDY_COUNTRY_IN_ID");
        Map<Integer, Long> staff = countBy(
                "SELECT uc.CountryId AS k, COUNT(DISTINCT uc.UserId) AS n FROM UMSCORE.UserCountries uc "
                        + "JOIN UMSCORE.Users u ON u.Id = uc.UserId "
                        + "WHERE ISNULL(u.IsDeleted,0) = 0 AND ISNULL(uc.IsActive,1) = 1 "
                        + "GROUP BY uc.CountryId");

        boolean arabic = lang == null || "ar".equalsIgnoreCase(lang);

        return countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .filter(c -> resolved == null || resolved.contains(c.id()))
                .map(c -> AttacheSummaryRowDto.builder()
                        .studyCountryInId(c.id())
                        .studyCountryName(name(c, arabic))
                        .studentCount(zero(students.get(c.id())))
                        .activeStudentCount(zero(activeStudents.get(c.id())))
                        .financeRecordCount(zero(financeCounts.get(c.id())))
                        .financeTotal(financeTotals.getOrDefault(c.id(), BigDecimal.ZERO))
                        .bankCount(zero(banks.get(c.id())))
                        .bankBranchCount(zero(branches.get(c.id())))
                        .studyYearsOverrides(zero(yearRules.get(c.id())))
                        .studyAmountOverrides(zero(amountRules.get(c.id())))
                        .staffCount(zero(staff.get(c.id())))
                        .build())
                .sorted(Comparator
                        .comparingLong(AttacheSummaryRowDto::getStudentCount).reversed()
                        .thenComparing(AttacheSummaryRowDto::getStudyCountryName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Map<Integer, Long> countBy(String sql) {
        return jdbc.query(sql, rs -> {
            Map<Integer, Long> out = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                out.put(rs.getInt("k"), rs.getLong("n"));
            }
            return out;
        });
    }

    private Map<Integer, BigDecimal> sumBy(String sql) {
        return jdbc.query(sql, rs -> {
            Map<Integer, BigDecimal> out = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                BigDecimal v = rs.getBigDecimal("n");
                out.put(rs.getInt("k"), v == null ? BigDecimal.ZERO : v);
            }
            return out;
        });
    }

    private static long zero(Long v) {
        return v == null ? 0L : v;
    }

    private static String name(CountryNameService.CountryRef c, boolean arabic) {
        String first = arabic ? c.nameAr() : c.nameEn();
        String second = arabic ? c.nameEn() : c.nameAr();
        return first != null && !first.isBlank() ? first : second;
    }
}
