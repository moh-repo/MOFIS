package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.QuarterlyFinancialReportDto;
import com.dilaw.mohe.dto.QuarterlyFinancialReportDto.EntityFinancialSummary;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import com.dilaw.mohe.audit.ReportAccessLogService;
import com.dilaw.mohe.report.excel.ExcelExportService;
import com.dilaw.mohe.report.excel.ExcelResponses;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.QuarterlyFinancialReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports/quarterly-financial")
@PreAuthorize(MoheSecurityExpressions.REPORT_FINANCE)
public class QuarterlyFinancialReportController {

    @Autowired
    private QuarterlyFinancialReportService service;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private ReportAccessLogService reportAccessLogService;

    @Autowired
    private com.dilaw.mohe.service.codelist.CodeListService codeLists;

    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getDistinctYears(
            @RequestParam(required = false) Integer countryId) {
        return ResponseEntity.ok(service.getDistinctYears(countryId));
    }

    @GetMapping({"", "/"})
    public ResponseEntity<QuarterlyFinancialReportDto> getQuarterlyReport(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer year) {
        int resolvedYear = year != null ? year : java.time.LocalDate.now().getYear();
        int resolvedQuarter = quarter != null ? quarter : ((java.time.LocalDate.now().getMonthValue() - 1) / 3 + 1);
        return ResponseEntity.ok(service.getQuarterlyReport(countryId, resolvedQuarter, resolvedYear));
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "ar") String lang) {

        int resolvedYear = year != null ? year : java.time.LocalDate.now().getYear();
        int resolvedQuarter = quarter != null ? quarter : ((java.time.LocalDate.now().getMonthValue() - 1) / 3 + 1);
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(countryId, resolvedQuarter, resolvedYear);
        List<EntityFinancialSummary> entities = dto.getEntities() != null
                ? dto.getEntities() : Collections.emptyList();

        // Label the document with the period the figures came from. dto.getYear() /
        // getQuarter() are the effective period, which differs from what was asked
        // for whenever the service fell back to the latest period holding data.
        int shownYear    = dto.getYear()    != null ? dto.getYear()    : resolvedYear;
        int shownQuarter = dto.getQuarter() != null ? dto.getQuarter() : resolvedQuarter;

        Map<String, Object> params = reportParamBuilder.build("QUARTERLY_FINANCIAL", lang);
        params.put("FILTER_YEAR",    String.valueOf(shownYear));
        params.put("FILTER_QUARTER", String.valueOf(shownQuarter));
        params.put("QUARTER_NAME",   quarterLabel(shownQuarter, lang));
        params.put("PERIOD_LABEL",   periodLabel(shownQuarter, shownYear, lang));
        params.put("TOTAL_ENTITIES", entities.size());
        if (dto.isPeriodSubstituted()) {
            params.put("PERIOD_NOTE", periodSubstitutionNote(resolvedQuarter, resolvedYear, lang));
        }
        params.put("TOTAL_RECEIVED_AMOUNT",  nvl(dto.getTotalReceivedAmount()));
        params.put("TOTAL_RECEIVED_COUNT",   nvl(dto.getTotalReceivedCount()));
        params.put("TOTAL_DISBURSED_AMOUNT", nvl(dto.getTotalDisbursedAmount()));
        params.put("TOTAL_DISBURSED_COUNT",  nvl(dto.getTotalDisbursedCount()));
        params.put("TOTAL_REMAINING_AMOUNT", nvl(dto.getTotalRemainingAmount()));
        params.put("TOTAL_REMAINING_COUNT",  nvl(dto.getTotalRemainingCount()));
        params.put("DISBURSEMENT_RATIO",     dto.getDisbursementRatio() != null ? dto.getDisbursementRatio() : 0.0);

        byte[] pdf = jasperReportService.generatePdf("quarterly-financial", params, entities);
        String filename = exportName(dto, shownQuarter, shownYear, resolvedQuarter, resolvedYear, "pdf");
        reportAccessLogService.logExport("QUARTERLY_FINANCIAL", "PDF",
                "year=" + shownYear + ",quarter=" + shownQuarter
                        + ",requested=" + resolvedYear + "Q" + resolvedQuarter + ",country=" + countryId);
        return PdfResponses.pdf(pdf, filename, PdfResponses.isInlineDisposition(disposition));
    }

    @GetMapping("/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "ar") String lang) throws java.io.IOException {

        int resolvedYear = year != null ? year : java.time.LocalDate.now().getYear();
        int resolvedQuarter = quarter != null ? quarter : ((java.time.LocalDate.now().getMonthValue() - 1) / 3 + 1);
        QuarterlyFinancialReportDto dto = service.getQuarterlyReport(countryId, resolvedQuarter, resolvedYear);
        List<EntityFinancialSummary> entities = dto.getEntities() != null ? dto.getEntities() : Collections.emptyList();

        // As with the PDF: title the workbook with the period the rows came from.
        int shownYear    = dto.getYear()    != null ? dto.getYear()    : resolvedYear;
        int shownQuarter = dto.getQuarter() != null ? dto.getQuarter() : resolvedQuarter;

        // Title, headers and entity names in the report's language (QA B6 / live QA #12).
        String[] headers = {"report.excel.col.entity", "report.excel.col.received-count",
                "report.excel.col.received-amount", "report.excel.col.disbursed-count",
                "report.excel.col.disbursed-amount", "report.excel.col.remaining-count",
                "report.excel.col.remaining-amount"};
        String[] fields  = {"entityName", "receivedCount", "receivedAmount", "disbursedCount", "disbursedAmount", "remainingCount", "remainingAmount"};

        byte[] xlsx = excelExportService.buildWorkbook(lang, "report.excel.quarterly-financial.title",
                new Object[]{periodLabel(shownQuarter, shownYear, lang)}, headers, fields, entities);
        String filename = exportName(dto, shownQuarter, shownYear, resolvedQuarter, resolvedYear, "xlsx");
        reportAccessLogService.logExport("QUARTERLY_FINANCIAL", "EXCEL",
                "year=" + shownYear + ",quarter=" + shownQuarter
                        + ",requested=" + resolvedYear + "Q" + resolvedQuarter + ",country=" + countryId);
        return ExcelResponses.xlsx(xlsx, filename);
    }

    /**
     * The download name, which says which period the figures are actually from.
     *
     * <p>Live test #23 read a file called {@code q3-2026} after asking for
     * quarter 4 and called it a naming bug. It is not: the service falls back to
     * the latest period that holds data, and naming the file after the period
     * that was asked for would put the wrong label on the right figures. The
     * document already carries a note saying the period was substituted; this
     * puts the same fact in the file name, so the two agree before it is opened.
     */
    private static String exportName(QuarterlyFinancialReportDto dto, int shownQuarter, int shownYear,
                                     int requestedQuarter, int requestedYear, String extension) {
        String base = "quarterly-financial-q" + shownQuarter + "-" + shownYear;
        if (dto.isPeriodSubstituted() && (shownQuarter != requestedQuarter || shownYear != requestedYear)) {
            base += "-requested-q" + requestedQuarter + "-" + requestedYear;
        }
        return base + "." + extension;
    }

    private static Long nvl(Long v)    { return v != null ? v : 0L; }
    private static Integer nvl(Integer v) { return v != null ? v : 0; }

    /** The quarter's name from LU_QUARTER_NUMBER in the report's language. */
    private String quarterLabel(int q, String lang) {
        return codeLists.label("quarter-number", String.valueOf(q), lang);
    }

    /**
     * The period the report covers, e.g. "الربع الأول 2024" / "Quarter 1 2024".
     *
     * <p>The year is wrapped in LRM marks so the Arabic run does not reorder its
     * digits, the same guard the report dates use.
     */
    private String periodLabel(int q, int year, String lang) {
        boolean ar = !"en".equalsIgnoreCase(lang);
        return ar
                ? quarterLabel(q, lang) + " ‎" + year + "‎"
                : quarterLabel(q, lang) + " " + year;
    }

    /**
     * Printed when the requested quarter held no finance rows and the figures come
     * from a different period. Without it the document would silently present one
     * quarter's money under another quarter's name.
     */
    private String periodSubstitutionNote(int requestedQuarter, int requestedYear, String lang) {
        boolean ar = !"en".equalsIgnoreCase(lang);
        String requested = periodLabel(requestedQuarter, requestedYear, lang);
        return ar
                ? "لا توجد حركات مالية في " + requested + "، وتعرض الأرقام أدناه أحدث ربع يحتوي على بيانات."
                : "No financial transactions in " + requested
                  + "; the figures below are for the most recent quarter holding data.";
    }
}
