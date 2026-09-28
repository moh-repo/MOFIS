package com.dilaw.mohe.report.chart;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.report.ReportExports;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The report behind the eight visualisation pages.
 *
 * <p>Those pages — bar, pie, line and hierarchy charts, the geographic map, the
 * comprehensive dashboards, the student graph and the student tracer — were the only
 * reports in the system that never reached Jasper. Preview and Download built a PDF
 * in the browser by pasting canvas images onto pages, and Print opened a second
 * window and called {@code window.print()} on a copy of the page's own HTML. That is
 * note H50: "the print button prints the page itself and the formatting is completely
 * wrong". It could not have been anything else — a browser printing its own markup
 * has no access to the masthead, the ministry logo, the signatories or the footer
 * sentence, all of which live in {@code REPORT_TYPE_SETTINGS} and only the server's
 * rendering reads.
 *
 * <p>They now go the way the other forty-seven reports go: the page posts what only
 * it can produce — the rendered cards, and the numbers under them — and gets back a
 * PDF or a spreadsheet built from {@code chart-report.jrxml}, carrying the same
 * masthead, export line, row cap and access-log row as every other report.
 *
 * <p>A POST because a rendered chart is far past what a query string can carry. It is
 * the only report endpoint that renders bytes the client supplied, which is why
 * {@link ChartReportRows#decodeImage} checks the PNG signature rather than trusting
 * the content type.
 */
@RestController
@RequestMapping("/api/v1/charts/report")
@PreAuthorize(MoheSecurityExpressions.REPORT_OTHER)
@RequiredArgsConstructor
public class ChartReportController {

    /** Seeded by V111; the masthead, footer and signatories come from that row. */
    private static final String REPORT_CODE = "CHART_REPORT";
    private static final String TEMPLATE = "chart-report";

    private final ReportParamBuilder reportParamBuilder;
    private final ReportExports reportExports;

    /**
     * The alias decides the format, exactly as it does on every other report: the
     * spreadsheet paths are recognised inside {@link ReportExports}, so one method
     * serves both and they cannot drift apart.
     */
    @PostMapping({"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> render(
            @Valid @RequestBody ChartReportRequest request,
            @RequestParam(value = "disposition", required = false) String disposition) {

        String lang = "en".equalsIgnoreCase(request.lang()) ? "en" : "ar";
        List<ChartReportRow> rows = ChartReportRows.flatten(request.charts());

        Map<String, Object> params = reportParamBuilder.build(REPORT_CODE, lang);
        // The page's own heading wins over the settings row: one template serves eight
        // pages, so "Charts and Visualizations" from the database would name none of them.
        if (request.title() != null && !request.title().isBlank()) {
            params.put("REPORT_TITLE", request.title().trim());
        }

        return reportExports.render(TEMPLATE, params, rows, ChartReportRows.baseName(request.title()), disposition);
    }
}
