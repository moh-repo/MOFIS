package com.dilaw.mohe.report;

import com.dilaw.mohe.dto.EnrollmentStatisticsRowDto;
import com.dilaw.mohe.dto.QuarterlyFinancialReportDto;
import com.dilaw.mohe.dto.QuarterlyFinancialReportDto.EntityFinancialSummary;
import com.dilaw.mohe.model.BankTransaction;
import com.dilaw.mohe.model.CustomFilterReport;
import com.dilaw.mohe.model.ExportManager;
import com.dilaw.mohe.model.FinancialAudit;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.service.BankTransactionService;
import com.dilaw.mohe.service.CustomFilterReportService;
import com.dilaw.mohe.service.EnrollmentStatisticsService;
import com.dilaw.mohe.service.ExportManagerService;
import com.dilaw.mohe.service.FinancialAuditService;
import com.dilaw.mohe.service.QuarterlyFinancialReportService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * The six Jasper reports QA found printing Arabic values under English headings
 * (QA B6 / N4, live QA #12) print English values for {@code lang=en}, and the same
 * Arabic values as before for {@code lang=ar}.
 *
 * <p>Each is rendered through {@code JasperReportService} -- the real fill path,
 * where {@code BilingualRows} swaps a row's names for their English siblings --
 * and read back as a spreadsheet, because an XLSX can be read cell by cell and a
 * PDF cannot. The rows come from the live database. Evidence is every name whose
 * two languages differ: the English sheet must print its English form and not its
 * Arabic one, and the Arabic sheet the other way round. Texts the masthead also
 * prints (the bilingual titles, the column heads) prove nothing either way and are
 * left out.
 *
 * <p>Country scope is stubbed to the ministry, as in the other report tests.
 */
@Tag("integration")
@SpringBootTest
class ReportValuesLanguageTest {

    @Autowired private JasperReportService jasper;
    @Autowired private ReportParamBuilder reportParamBuilder;
    @Autowired private CustomFilterReportService customFilterService;
    @Autowired private QuarterlyFinancialReportService quarterlyService;
    @Autowired private EnrollmentStatisticsService enrollmentService;
    @Autowired private FinancialAuditService auditService;
    @Autowired private BankTransactionService bankService;
    @Autowired private ExportManagerService exportManagerService;

    @MockBean private CountryScopeService countryScopeService;

    @BeforeEach
    void allowAllCountries() {
        when(countryScopeService.canAccessCountry(anyInt())).thenReturn(true);
        when(countryScopeService.getScope()).thenReturn(
                new CountryScopeService.CountryScope(1L, true, true, List.of()));
    }

    /** One name in its two languages. */
    private record Name(String ar, String en) {
        static Name of(String ar, String en) {
            return new Name(ar == null ? null : ar.trim(), en == null ? null : en.trim());
        }
    }

    /** Every name of every row, as the given accessors pair them. */
    @SafeVarargs
    private static <R> List<Name> names(Collection<R> rows, Function<R, Name>... pairs) {
        return rows.stream().flatMap(r -> Stream.of(pairs).map(p -> p.apply(r))).toList();
    }

    private static final DataFormatter CELLS = new DataFormatter();

    /** Every non-blank cell the template printed, trimmed. */
    private static Set<String> cells(byte[] xlsx) throws Exception {
        Set<String> out = new HashSet<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (Sheet sheet : wb) {
                sheet.forEach(row -> row.forEach(cell -> {
                    String text = CELLS.formatCellValue(cell).trim();
                    if (!text.isEmpty()) out.add(text);
                }));
            }
        }
        return out;
    }

    /** The report's parameters in one language; built afresh per render, since the logo is a stream. */
    private Map<String, Object> params(String reportCode, String lang, Map<String, Object> extra) {
        Map<String, Object> p = reportParamBuilder.build(reportCode, lang);
        p.putAll(extra);
        return p;
    }

    private void assertValuesFollowLanguage(String template, String reportCode, Collection<?> rows,
                                            Map<String, Object> extra, List<Name> names) throws Exception {
        assertThat(rows).as("%s: the database is expected to hold rows", template).isNotEmpty();

        Set<String> masthead = new HashSet<>();
        for (String lang : List.of("ar", "en")) {
            params(reportCode, lang, extra).values().stream()
                    .filter(String.class::isInstance).map(v -> ((String) v).trim()).forEach(masthead::add);
        }
        // A lookup with no English name prints its Arabic one in the English report
        // by design (V104's fallback), so an Arabic text that is also some row's
        // English value is not evidence of a missed swap -- and the reverse.
        Set<String> allEn = new HashSet<>();
        Set<String> allAr = new HashSet<>();
        names.forEach(n -> { allEn.add(n.en()); allAr.add(n.ar()); });
        Set<Name> evidence = new LinkedHashSet<>();
        for (Name n : names) {
            if (n.ar() != null && n.en() != null && !n.ar().isEmpty() && !n.en().isEmpty()
                    && !n.ar().equals(n.en()) && !masthead.contains(n.ar()) && !masthead.contains(n.en())
                    && !allEn.contains(n.ar()) && !allAr.contains(n.en())) {
                evidence.add(n);
            }
        }
        assertThat(evidence)
                .as("%s: some row must carry a name that differs between Arabic and English, "
                    + "or this test proves nothing", template)
                .isNotEmpty();

        Set<String> english = cells(jasper.generateXlsx(template, params(reportCode, "en", extra), rows));
        Set<String> arabic = cells(jasper.generateXlsx(template, params(reportCode, "ar", extra), rows));

        List<String> en = evidence.stream().map(Name::en).distinct().toList();
        List<String> ar = evidence.stream().map(Name::ar).distinct().toList();
        assertThat(en).as("%s?lang=en prints the English names", template)
                .allMatch(v -> printed(english, v));
        assertThat(ar).as("%s?lang=en prints no Arabic name that has an English one", template)
                .noneMatch(v -> printed(english, v));
        assertThat(ar).as("%s?lang=ar prints the Arabic names, as before", template)
                .allMatch(v -> printed(arabic, v));
        assertThat(en).as("%s?lang=ar prints no English name", template)
                .noneMatch(v -> printed(arabic, v));
    }

    /**
     * Whether the sheet prints {@code value}: as a whole cell, or clipped by a
     * column too narrow for it -- Jasper cuts a long name at a word boundary
     * ("Curriculum and Teaching" for "Curriculum and Teaching Methods"), which
     * is a layout matter, not a language one.
     */
    private static boolean printed(Set<String> cells, String value) {
        return cells.contains(value)
                || cells.stream().anyMatch(c -> c.length() >= 4 && value.startsWith(c + " "));
    }

    @Test
    @DisplayName("custom filter report: English names for lang=en, Arabic for lang=ar")
    void customFilter() throws Exception {
        List<CustomFilterReport> rows = customFilterService.getAllReports(null);
        assertValuesFollowLanguage("custom-filter-report", "CUSTOM_FILTER_REPORT", rows, Map.of(), names(rows,
                r -> Name.of(r.getStudyStatusName(), r.getStudyStatusNameEn()),
                r -> Name.of(r.getDegreeLevelName(), r.getDegreeLevelNameEn()),
                r -> Name.of(r.getUniversityName(), r.getUniversityNameEn())));
    }

    @Test
    @DisplayName("quarterly financial: English entity names for lang=en, Arabic for lang=ar")
    void quarterlyFinancial() throws Exception {
        QuarterlyFinancialReportDto dto = quarterlyService.getQuarterlyReport(null, 1, 2024);
        List<EntityFinancialSummary> rows = dto.getEntities();

        // The parameters the controller supplies around the rows.
        Map<String, Object> extra = new HashMap<>();
        extra.put("FILTER_YEAR", String.valueOf(dto.getYear()));
        extra.put("FILTER_QUARTER", String.valueOf(dto.getQuarter()));
        extra.put("QUARTER_NAME", "Q" + dto.getQuarter());
        extra.put("PERIOD_LABEL", "Q" + dto.getQuarter() + " " + dto.getYear());
        extra.put("TOTAL_ENTITIES", rows.size());
        extra.put("TOTAL_RECEIVED_AMOUNT", dto.getTotalReceivedAmount());
        extra.put("TOTAL_RECEIVED_COUNT", dto.getTotalReceivedCount());
        extra.put("TOTAL_DISBURSED_AMOUNT", dto.getTotalDisbursedAmount());
        extra.put("TOTAL_DISBURSED_COUNT", dto.getTotalDisbursedCount());
        extra.put("TOTAL_REMAINING_AMOUNT", dto.getTotalRemainingAmount());
        extra.put("TOTAL_REMAINING_COUNT", dto.getTotalRemainingCount());
        extra.put("DISBURSEMENT_RATIO", dto.getDisbursementRatio());

        assertValuesFollowLanguage("quarterly-financial", "QUARTERLY_FINANCIAL", rows, extra,
                names(rows, r -> Name.of(r.getEntityName(), r.getEntityNameEn())));
    }

    @Test
    @DisplayName("enrollment statistics: the DTO carries the English names through to the report")
    void enrollmentStatistics() throws Exception {
        List<EnrollmentStatisticsRowDto> rows = enrollmentService.search(null, null, null, null, null, null, "en");

        Map<String, Object> extra = new HashMap<>();
        extra.put("TOTAL_STUDENTS", rows.stream().mapToLong(r -> r.getStudentCount() == null ? 0 : r.getStudentCount()).sum());
        extra.put("TOTAL_ROWS", rows.size());
        extra.put("TOTAL_COUNTRIES", (int) rows.stream().map(EnrollmentStatisticsRowDto::getStudentCountryId).distinct().count());
        extra.put("TOTAL_MAJORS", (int) rows.stream().map(EnrollmentStatisticsRowDto::getStudentMajorId).distinct().count());

        assertValuesFollowLanguage("enrollment-statistics", "ENROLLMENT_STATISTICS", rows, extra, names(rows,
                r -> Name.of(r.getStudentCountryName(), r.getStudentCountryNameEn()),
                r -> Name.of(r.getStudentMajorName(), r.getStudentMajorNameEn()),
                r -> Name.of(r.getStudentDegreeName(), r.getStudentDegreeNameEn()),
                r -> Name.of(r.getStudentStudyTypeName(), r.getStudentStudyTypeNameEn())));
    }

    @Test
    @DisplayName("financial audit: English statuses, categories and banks for lang=en, Arabic for lang=ar")
    void financialAudit() throws Exception {
        List<FinancialAudit> rows = auditService.getByFilters(null, null, null, null, null, null, null, null, null,
                null, null, null, PageRequest.of(0, 500, Sort.by("auditDate").descending())).getContent();
        assertValuesFollowLanguage("financial-audit", "FINANCIAL_AUDIT", rows, Map.of(), names(rows,
                r -> Name.of(r.getAuditStatus(), r.getAuditStatusEn()),
                r -> Name.of(r.getAuditCategory(), r.getAuditCategoryEn()),
                r -> Name.of(r.getAuditCurrency(), r.getAuditCurrencyEn()),
                r -> Name.of(r.getAuditorName(), r.getAuditorNameEn()),
                r -> Name.of(r.getBankName(), r.getBankNameEn())));
    }

    @Test
    @DisplayName("bank transactions: the transaction type is English for lang=en, Arabic for lang=ar")
    void bankTransaction() throws Exception {
        List<BankTransaction> rows = bankService.getByFilters(null, null, null, null, null, null, null, null, null,
                PageRequest.of(0, 500, Sort.by("transactionDate").descending())).getContent();
        // The type is the point: bank, branch and currency were already bilingual.
        assertThat(names(rows, r -> Name.of(r.getTransactionType(), r.getTransactionTypeEn())))
                .as("a received status must resolve to its English name")
                .anySatisfy(n -> assertThat(n.ar()).isNotEqualTo(n.en()));
        assertValuesFollowLanguage("bank-transaction", "BANK_TRANSACTION", rows, Map.of(), names(rows,
                r -> Name.of(r.getTransactionType(), r.getTransactionTypeEn()),
                r -> Name.of(r.getBankName(), r.getBankNameEn()),
                r -> Name.of(r.getBankBranch(), r.getBankBranchEn())));
    }

    @Test
    @DisplayName("export manager: V124's English report names for lang=en, Arabic for lang=ar")
    void exportManager() throws Exception {
        List<ExportManager> rows = exportManagerService.getAllExports();
        assertThat(rows).allSatisfy(r -> assertThat(r.getReportNameEn())
                .as("V124 falls back to the Arabic, so an English name is never blank").isNotBlank());
        assertValuesFollowLanguage("export-manager", "EXPORT_MANAGER", rows, Map.of(), names(rows,
                r -> Name.of(r.getReportName(), r.getReportNameEn())));
    }
}
