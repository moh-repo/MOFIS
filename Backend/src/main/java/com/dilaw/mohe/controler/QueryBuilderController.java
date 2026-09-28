package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.dto.JoinSuggestionDto;
import com.dilaw.mohe.dto.QueryResultDto;
import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.model.QueryBuilder;
import com.dilaw.mohe.model.SavedQuery;
import com.dilaw.mohe.report.pdf.PdfResponses;
import com.dilaw.mohe.service.QueryBuilderService;
import com.dilaw.mohe.service.QueryExecutionService;
import com.dilaw.mohe.service.QueryReportTicketService;
import com.dilaw.mohe.service.SavedQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.dilaw.mohe.report.jasper.DynamicTableReportService;
import com.dilaw.mohe.report.jasper.JasperReportService;
import com.dilaw.mohe.report.jasper.ReportParamBuilder;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/custom-reports/query-builder")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class QueryBuilderController {

    @Autowired
    private QueryBuilderService service;

    @Autowired
    private QueryExecutionService queryExecutionService;

    @Autowired
    private SavedQueryService savedQueryService;


    @Autowired
    private QueryReportTicketService ticketService;

    @Autowired
    private JasperReportService jasperReportService;

    @Autowired
    private DynamicTableReportService dynamicTableReportService;

    @Autowired
    private ReportParamBuilder reportParamBuilder;

    @Autowired
    private com.dilaw.mohe.report.ReportExports reportExports;

    @GetMapping
    public ResponseEntity<List<QueryBuilder>> getAllQueryMetadata() {
        return ResponseEntity.ok(service.getAllQueryMetadata());
    }

    @GetMapping("/tables")
    public ResponseEntity<List<String>> getAllTableNames() {
        return ResponseEntity.ok(service.getAllTableNames());
    }

    @GetMapping("/columns/{tableName}")
    public ResponseEntity<List<QueryBuilder>> getColumnsByTable(@PathVariable String tableName) {
        return ResponseEntity.ok(service.getColumnsByTableName(tableName));
    }

    @GetMapping("/filterable-columns")
    public ResponseEntity<List<QueryBuilder>> getFilterableColumns() {
        return ResponseEntity.ok(service.getFilterableColumns());
    }

    /**
     * Ways the given object can be joined to another.
     *
     * <p>Suggestions for the join picker, so a user does not have to know the
     * schema's keys. The engine validates whatever comes back regardless.
     */
    @GetMapping("/joins/{tableName}")
    public ResponseEntity<List<JoinSuggestionDto>> getJoinSuggestions(@PathVariable String tableName) {
        return ResponseEntity.ok(service.getJoinSuggestions(tableName));
    }

    @GetMapping("/sortable-columns")
    public ResponseEntity<List<QueryBuilder>> getSortableColumns() {
        return ResponseEntity.ok(service.getSortableColumns());
    }

    /**
     * Runs a structurally described query.
     *
     * <p>POST because the spec is a document, not because it changes anything —
     * the engine composes SELECT only. The spec carries no SQL; see
     * {@link com.dilaw.mohe.service.QueryExecutionService} for what is validated.
     */
    @PostMapping("/execute")
    public ResponseEntity<QueryResultDto> execute(@RequestBody QuerySpecDto spec) {
        return ResponseEntity.ok(queryExecutionService.execute(spec));
    }

    // ── Saved queries ────────────────────────────────────────────────────────
    // A saved query is a stored spec, so it is re-validated on every run and the
    // scope applied is the scope of whoever runs it.

    @GetMapping("/saved")
    public ResponseEntity<List<SavedQuery>> listSaved() {
        return ResponseEntity.ok(savedQueryService.list());
    }

    @PostMapping("/saved")
    public ResponseEntity<SavedQuery> save(@RequestBody SaveQueryRequest request) {
        return ResponseEntity.ok(savedQueryService.save(
                request.getName(), request.getDescription(),
                Boolean.TRUE.equals(request.getShared()), request.getSpec()));
    }

    @GetMapping("/saved/{id}/spec")
    public ResponseEntity<QuerySpecDto> savedSpec(@PathVariable Long id) {
        return ResponseEntity.ok(savedQueryService.getSpec(id));
    }

    @PostMapping("/saved/{id}/run")
    public ResponseEntity<QueryResultDto> runSaved(
            @PathVariable Long id,
            @RequestParam(required = false) Integer countryId,
            @RequestParam(required = false) List<Integer> countryIds) {
        return ResponseEntity.ok(savedQueryService.run(id, countryId, countryIds));
    }

    @DeleteMapping("/saved/{id}")
    public ResponseEntity<Void> deleteSaved(@PathVariable Long id) {
        savedQueryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Body of a save request: the spec plus how it should be listed. */
    public static class SaveQueryRequest {
        private String name;
        private String description;
        private Boolean shared;
        private QuerySpecDto spec;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Boolean getShared() { return shared; }
        public void setShared(Boolean shared) { this.shared = shared; }
        public QuerySpecDto getSpec() { return spec; }
        public void setSpec(QuerySpecDto spec) { this.spec = spec; }
    }

    @GetMapping(value = {"/export-report", "/report-pdf", "/doc", "/export-excel", "/report-xlsx"})
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {
        List<QueryBuilder> items = service.getAllQueryMetadata();
        Map<String, Object> jasperParams = reportParamBuilder.build("QUERY_BUILDER", lang);
        return reportExports.render("query-builder", jasperParams, items, "query-builder", disposition);
    }

    // ── Reporting on a query's own result ────────────────────────────────────
    // Two steps, because the PDF endpoints are reached by iframe and direct
    // navigation and so must be GETs, while a spec with joins and filters is
    // too big for a query string. The page posts the spec, then puts the
    // returned handle in the PDF URL. See QueryReportTicketService.

    /** Stores a spec for rendering and returns the handle to render it by. */
    @PostMapping("/result-report/ticket")
    public ResponseEntity<Map<String, String>> issueResultReportTicket(@RequestBody QuerySpecDto spec) {
        return ResponseEntity.ok(Map.of("ticket", ticketService.issue(spec)));
    }

    /**
     * The result of a composed query, as an official PDF.
     *
     * <p>The spec is re-run here rather than the rows being carried over from
     * the page, so the document is produced from the database under the
     * requesting user's own country scope — a report can never show more than
     * the query itself would.
     */
    @GetMapping(value = {"/result-report/export-report", "/result-report/report-pdf", "/result-report/doc"})
    public ResponseEntity<byte[]> resultReport(
            @RequestParam String ticket,
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(defaultValue = "ar") String lang) {

        QuerySpecDto spec = ticketService.redeem(ticket);
        QueryResultDto result = queryExecutionService.execute(spec);

        List<String> labels = result.getColumns().stream()
                .map(QueryResultDto.Column::getName)
                .toList();

        Map<String, Object> params = reportParamBuilder.build("QUERY_BUILDER", lang);
        params.put("REPORT_TITLE", describe(spec));
        params.put("QUERY_SUMMARY", describe(spec) + "  —  " + result.getRowCount()
                + (result.isTruncated() ? "+" : "") + " rows");
        params.put("QUERY_NOTE", result.getScopeNote() == null ? "" : result.getScopeNote());

        byte[] pdf;
        try {
            pdf = dynamicTableReportService.render(labels, result.getRows(), params);
        } catch (IllegalArgumentException ex) {
            // Too many columns to print is the caller's problem to fix, and the
            // message says how; it must not surface as a server error.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return PdfResponses.pdf(pdf, "query-result.pdf", PdfResponses.isInlineDisposition(disposition));
    }

    /** A one-line description of what the query asked for, printed on the report. */
    private static String describe(QuerySpecDto spec) {
        StringBuilder text = new StringBuilder(spec.getSource() == null ? "" : spec.getSource());
        if (spec.getJoins() != null) {
            for (QuerySpecDto.Join join : spec.getJoins()) {
                if (join != null && join.getTable() != null) {
                    text.append(" + ").append(join.getTable());
                }
            }
        }
        int filters = spec.getFilters() == null ? 0 : spec.getFilters().size();
        if (filters > 0) {
            text.append("  (").append(filters).append(filters == 1 ? " filter)" : " filters)");
        }
        return text.toString();
    }

}