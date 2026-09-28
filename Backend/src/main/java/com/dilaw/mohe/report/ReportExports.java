package com.dilaw.mohe.report;

import com.dilaw.mohe.report.excel.ExcelResponses;
import com.dilaw.mohe.report.jasper.JasperReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Map;

/**
 * Renders one filled report as whichever format the caller asked for.
 *
 * <p>Every report controller exposes a single export endpoint under several
 * aliases, and the alias chooses the format: {@code /export-report}, {@code
 * /report-pdf} and {@code /doc} return the PDF the viewer previews and prints,
 * while {@code /export-excel} and {@code /report-xlsx} return the same report as
 * a spreadsheet. Both come from the same JRXML through {@link
 * JasperReportService}, so the Excel a user downloads can never drift from the
 * PDF they were just looking at.
 *
 * <p>Resolving the format from the request path rather than from another
 * {@code @RequestParam} keeps the controllers unchanged apart from their alias
 * list: their existing parameters, filters and country scoping all still apply
 * to both formats, because there is only one code path to keep in step.
 */
@Component
@RequiredArgsConstructor
public class ReportExports {

    private final JasperReportService jasperReportService;
    private final com.dilaw.mohe.audit.ReportAccessLogService reportAccessLog;

    /**
     * @param template  JRXML name, without the extension
     * @param params    Jasper parameters, normally from {@code ReportParamBuilder}
     * @param data      report rows
     * @param baseName  download file name, without the extension
     * @param disposition {@code attachment} forces a download; anything else previews inline
     */
    public ResponseEntity<byte[]> render(String template,
                                         Map<String, Object> params,
                                         Collection<?> data,
                                         String baseName,
                                         String disposition) {
        boolean spreadsheet = wantsSpreadsheet();
        // The English-language resolution of lookup names lives in
        // JasperReportService.fill, which both branches below go through and which
        // the controllers calling generatePdf directly go through too.
        //
        // O-33: every export is a bulk read of personal data; the row says who, which
        // report, which filters and how many rows, in REPORT_ACCESS_LOG (never throws).
        // Logged after the file is generated rather than before, so the row can carry
        // its size — which is what the Export Manager shows. A generation that throws
        // is therefore not logged, which is the correct reading: nothing was exported.
        if (spreadsheet) {
            byte[] xlsx = jasperReportService.generateXlsx(template, params, data);
            logExport(baseName, "EXCEL", data, xlsx.length);
            return ExcelResponses.xlsx(xlsx, baseName + ".xlsx");
        }
        byte[] pdf = jasperReportService.generatePdf(template, params, data);
        logExport(baseName, "PDF", data, pdf.length);
        return com.dilaw.mohe.report.pdf.PdfResponses.pdf(
                pdf, baseName + ".pdf", com.dilaw.mohe.report.pdf.PdfResponses.isInlineDisposition(disposition));
    }

    /** One access-log row for a generated export, with what it turned out to contain. */
    private void logExport(String baseName, String format, Collection<?> data, int sizeBytes) {
        reportAccessLog.logExport(reportCode(baseName), format, filterSummary(data),
                data == null ? 0 : data.size(), (long) sizeBytes);
    }

    /** {@code attache-summary} becomes {@code ATTACHE_SUMMARY}, the shape the older log rows use. */
    public static String reportCode(String baseName) {
        return baseName == null ? "REPORT" : baseName.toUpperCase().replace('-', '_');
    }

    /** The request parameters (minus presentation ones) and the row count. */
    private static String filterSummary(Collection<?> data) {
        StringBuilder sb = new StringBuilder();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            attrs.getRequest().getParameterMap().forEach((k, v) -> {
                if ("disposition".equals(k) || "lang".equals(k)) return;
                if (sb.length() > 0) sb.append(',');
                sb.append(k).append('=').append(String.join("|", v));
            });
        }
        if (sb.length() > 0) sb.append(',');
        sb.append("rows=").append(data == null ? 0 : data.size());
        String s = sb.toString();
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    /** True when the request came in on one of the spreadsheet aliases. */
    private boolean wantsSpreadsheet() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            // Called outside a web request (tests, scheduled runs): PDF stays the default.
            return false;
        }
        String path = attrs.getRequest().getRequestURI();
        if (path == null) {
            return false;
        }
        return path.endsWith("/export-excel") || path.endsWith("/report-xlsx");
    }
}
