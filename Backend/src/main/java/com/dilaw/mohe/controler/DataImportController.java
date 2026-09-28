package com.dilaw.mohe.controler;

import com.dilaw.mohe.service.DataImportService;
import com.dilaw.mohe.service.ScholarshipDataImportService;
import com.dilaw.mohe.service.ScholarshipEtlService;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.util.SpreadsheetUploadValidator;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.dilaw.mohe.exception.LocalizedMessage;
import org.springframework.context.i18n.LocaleContextHolder;

@RestController
@RequestMapping("api/v1/import")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class DataImportController {

    private static final Logger log = LoggerFactory.getLogger(DataImportController.class);

    @Autowired
    private DataImportService dataImportService;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private ScholarshipDataImportService scholarshipDataImportService;

    /** For a keyed refusal in the reader's language (W3-08). */
    @Autowired(required = false)
    private org.springframework.context.MessageSource messageSource;

    @Autowired
    private ScholarshipEtlService scholarshipEtlService;

    /**
     * Ministry-only, and deliberately not expressed as a {@code @PreAuthorize}
     * permission: this tool writes straight into any table in the database, so
     * the gate is membership of the ministry rather than a grantable permission
     * an administrator could hand to an attaché by mistake.
     *
     * <p>{@code SETTINGS.VIEW} — which the class-level annotation accepts — is
     * held by the country administrators, so without this every attaché admin
     * could insert into {@code UMSCORE.Users}.
     *
     * <p>Asks for the <em>user's</em> scope, not {@code getScope()}. The two
     * differ whenever the topbar country selector is set: {@code getScope()}
     * narrows to the chosen country and sets {@code ministryUser=false} to make
     * the JPA country specs filter. That false is a mechanism for narrowing a
     * query, not a statement about who is calling — and reading it as one made
     * this whole screen answer 403 to a ministry user simply because they had a
     * country selected, superadmin included.
     */
    private void requireMinistry(String action) {
        boolean ministryUser = countryScopeService.getCurrentUserId()
                .map(countryScopeService::getScopeForUser)
                .map(CountryScopeService.CountryScope::isMinistryUser)
                .orElse(false);
        if (!ministryUser) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    action + " writes across every attaché and is ministry-only.");
        }
    }

    /**
     * Upload and preview file contents
     */
    @PostMapping("/preview")
    public ResponseEntity<?> previewFile(@RequestParam("file") MultipartFile file) {
        requireMinistry("Data import");
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "No file uploaded"));
            }

            Map<String, Object> result = dataImportService.parseFile(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to parse import file", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to parse file: " + e.getMessage()));
        }
    }

    /**
     * Get available tables for import target
     */
    @GetMapping("/tables")
    public ResponseEntity<?> getAvailableTables() {
        requireMinistry("Listing import targets");
        try {
            List<Map<String, Object>> tables = dataImportService.getAvailableTables();
            return ResponseEntity.ok(tables);
        } catch (Exception e) {
            log.error("Failed to get import tables", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to get tables: " + e.getMessage()));
        }
    }

    /**
     * Get columns for a specific table
     */
    @GetMapping("/tables/{schema}/{tableName}/columns")
    public ResponseEntity<?> getTableColumns(@PathVariable String schema, @PathVariable String tableName) {
        requireMinistry("Listing import targets");
        try {
            List<Map<String, Object>> columns = dataImportService.getTableColumns(schema, tableName);
            return ResponseEntity.ok(columns);
        } catch (Exception e) {
            log.error("Failed to get columns for {}.{}", schema, tableName, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to get columns: " + e.getMessage()));
        }
    }

    /**
     * Get staging data (QuarterlyBaseStaging) with optional filter and pagination.
     */
    @GetMapping("/staging/quarterly-base")
    public ResponseEntity<?> getQuarterlyBaseStaging(
            @RequestParam(required = false) String sourceFileName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        requireMinistry("Staging data");
        try {
            Map<String, Object> result = dataImportService.getQuarterlyBaseStaging(sourceFileName, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to load quarterly-base staging data", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to load staging data: " + e.getMessage()));
        }
    }

    /**
     * Import Excel into QuarterlyBaseStaging table for later ETL (e.g. quarterly base data).
     */
    @PostMapping("/staging/quarterly-base")
    public ResponseEntity<?> importQuarterlyBaseStaging(@RequestParam("file") MultipartFile file) {
        requireMinistry("Quarterly base import");
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "No file uploaded"));
            }
            Map<String, Object> result = dataImportService.importToQuarterlyBaseStaging(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Quarterly-base staging import failed", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Import failed: " + e.getMessage()));
        }
    }

    /**
     * Preview a quarterly scholarship workbook without writing anything.
     *
     * <p>Answers the three questions the screen has to put to the user before
     * they commit: which row did we take as the header, what did we read out of
     * the block above it, and how many rows will land.
     */
    @PostMapping("/scholarship-data/preview")
    public ResponseEntity<?> previewScholarshipData(@RequestParam("file") MultipartFile file) {
        requireMinistry("Scholarship data import");
        try {
            SpreadsheetUploadValidator.validate(file);
            return ResponseEntity.ok(scholarshipDataImportService.preview(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                    String.valueOf(LocalizedMessage.resolve(messageSource, e, LocaleContextHolder.getLocale()))));
        } catch (Exception e) {
            log.error("Failed to preview scholarship import file", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to read the workbook: " + e.getMessage()));
        }
    }

    /**
     * Import a quarterly scholarship workbook for one country.
     *
     * <p>{@code countryId} is required and is checked against the caller's scope
     * rather than taken on trust: today only ministry users reach this endpoint,
     * so the check always passes, but it is the line that has to already be here
     * when the screen is opened to country administrators.
     */
    @PostMapping("/scholarship-data")
    public ResponseEntity<?> importScholarshipData(
            @RequestParam("file") MultipartFile file,
            @RequestParam("countryId") Integer countryId,
            @RequestParam(defaultValue = "false") boolean deleteCountryData,
            @RequestParam(defaultValue = "false") boolean deleteAllData) {
        requireMinistry("Scholarship data import");
        try {
            SpreadsheetUploadValidator.validate(file);
            if (countryId == null || countryId <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Choose the country this workbook belongs to."));
            }
            countryScopeService.validateCountryAccess(countryId);

            Long userId = SecurityRequestUtils.currentUserId().orElse(null);
            /*
             * Both flags default to false, so a request that says nothing appends —
             * the behaviour every existing caller already relies on. Deleting is
             * only ever reachable through this endpoint, which means it can only
             * happen as part of an import that has a readable workbook behind it;
             * there is deliberately no way to ask for the delete on its own.
             */
            ScholarshipDataImportService.DeleteMode deleteMode =
                    ScholarshipDataImportService.DeleteMode.of(deleteCountryData, deleteAllData);
            return ResponseEntity.ok(
                    scholarshipDataImportService.importFile(file, countryId, userId, deleteMode));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                    String.valueOf(LocalizedMessage.resolve(messageSource, e, LocaleContextHolder.getLocale()))));
        } catch (Exception e) {
            log.error("Scholarship data import failed for country {}", countryId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Import failed: " + e.getMessage()));
        }
    }

    /**
     * How many rows a delete would take: for the chosen country, and in total.
     *
     * <p>Feeds the confirmation shown before an import that replaces data. The
     * numbers are the point — "this will delete 842 rows for مصر" is something a
     * person can check, and a generic warning is something they click past.
     */
    @GetMapping("/scholarship-data/counts")
    public ResponseEntity<?> getScholarshipDataCounts(@RequestParam(required = false) Integer countryId) {
        requireMinistry("Scholarship data");
        try {
            countryScopeService.validateCountryForRead(countryId);
            return ResponseEntity.ok(scholarshipDataImportService.counts(countryId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to count scholarship import rows", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to count rows: " + e.getMessage()));
        }
    }

    /**
     * One imported row in full, for the record view.
     *
     * <p>The id is constrained to digits so this cannot shadow the sibling
     * literal routes under the same prefix — {@code /scholarship-data/counts}
     * would otherwise be a candidate for {@code {id}}.
     */
    @GetMapping("/scholarship-data/{id:[0-9]+}")
    public ResponseEntity<?> getScholarshipRow(@PathVariable int id) {
        requireMinistry("Scholarship data");
        try {
            Map<String, Object> row = scholarshipDataImportService.findById(id);
            if (row == null) {
                return ResponseEntity.notFound().build();
            }
            // The row carries the country it was filed under; a caller who may
            // not see that country may not see the row either.
            Object countryId = row.get("COUNTRY_ID");
            if (countryId instanceof Number n) {
                countryScopeService.validateCountryForRead(n.intValue());
            }
            return ResponseEntity.ok(row);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load scholarship import row {}", id, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to load the record: " + e.getMessage()));
        }
    }

    /** Imported scholarship rows, newest batch first. */
    @GetMapping("/scholarship-data")
    public ResponseEntity<?> getScholarshipData(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) MultiValueMap<String, String> allParams) {
        requireMinistry("Scholarship data");
        try {
            countryScopeService.validateCountryForRead(countryId);
            ScholarshipDataImportService.ListQuery query =
                    new ScholarshipDataImportService.ListQuery(
                            sortBy, sortDir, search, columnFilters(allParams));
            return ResponseEntity.ok(scholarshipDataImportService.list(countryId, page, size, query));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load scholarship import data", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to load data: " + e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────── scholarship -> staging ──

    /**
     * What the ETL would do, without doing it.
     *
     * <p>A POST rather than a GET because the caller is asking the server to
     * work something out, not to fetch a resource — but it is the one endpoint
     * here that writes nothing.
     */
    @PostMapping("/etl/staging/preview")
    public ResponseEntity<?> previewEtl(@RequestParam Integer countryId) {
        requireMinistry("ETL staging");
        try {
            countryScopeService.validateCountryAccess(countryId);
            return ResponseEntity.ok(scholarshipEtlService.preview(countryId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                    String.valueOf(LocalizedMessage.resolve(messageSource, e, LocaleContextHolder.getLocale()))));
        } catch (Exception e) {
            log.error("ETL preview failed for country {}", countryId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Preview failed: " + e.getMessage()));
        }
    }

    /**
     * Runs the load: this country's staging rows are replaced from the landing
     * table.
     *
     * <p>Replacing rather than appending is the whole point — STUDENT_DATA_STAGING
     * is keyed on the landing row's id, so a second run of the same batch would
     * otherwise collide on the primary key rather than refresh anything.
     */
    @PostMapping("/etl/staging/run")
    public ResponseEntity<?> runEtl(@RequestParam Integer countryId) {
        requireMinistry("ETL staging");
        try {
            countryScopeService.validateCountryAccess(countryId);
            Long userId = SecurityRequestUtils.currentUserId().orElse(null);
            return ResponseEntity.ok(scholarshipEtlService.run(countryId, userId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                    String.valueOf(LocalizedMessage.resolve(messageSource, e, LocaleContextHolder.getLocale()))));
        } catch (Exception e) {
            log.error("ETL run failed for country {}", countryId, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Staging failed: " + e.getMessage()));
        }
    }

    /** How many rows are on each side, for the confirmation before a run. */
    @GetMapping("/etl/staging/counts")
    public ResponseEntity<?> etlCounts(@RequestParam(required = false) Integer countryId) {
        requireMinistry("ETL staging");
        try {
            countryScopeService.validateCountryForRead(countryId);
            return ResponseEntity.ok(scholarshipEtlService.counts(countryId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to count ETL rows", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Failed to load counts: " + e.getMessage()));
        }
    }

    /** The staged rows, with the lookups joined back to their names. */
    @GetMapping("/etl/staging/data")
    public ResponseEntity<?> etlStagedData(
            @RequestParam(required = false) Integer countryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean unresolvedOnly,
            @RequestParam(required = false) MultiValueMap<String, String> allParams) {
        requireMinistry("ETL staging");
        try {
            countryScopeService.validateCountryForRead(countryId);
            ScholarshipEtlService.StagedQuery query = new ScholarshipEtlService.StagedQuery(
                    sortBy, sortDir, search, columnFilters(allParams), unresolvedOnly);
            return ResponseEntity.ok(scholarshipEtlService.listStaged(countryId, page, size, query));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load staged rows", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Failed to load data: " + e.getMessage()));
        }
    }

    /**
     * One staged row in full.
     *
     * <p>The id is constrained to digits so this cannot shadow the sibling
     * {@code /etl/staging/run}, {@code /preview}, {@code /counts} and
     * {@code /data} paths — Spring would otherwise have to guess between a
     * literal segment and a variable one.
     */
    @GetMapping("/etl/staging/{id:[0-9]+}")
    public ResponseEntity<?> etlStagedRecord(@PathVariable int id) {
        requireMinistry("ETL staging");
        try {
            Map<String, Object> row = scholarshipEtlService.findStaged(id);
            if (row == null) {
                return ResponseEntity.notFound().build();
            }
            /*
             * Scope is checked against the row, not against a parameter the
             * caller supplied: an id is guessable, and without this a country
             * admin could walk the staging table one integer at a time.
             */
            Object countryId = row.get("STUDY_COUNTRY_IN");
            countryScopeService.validateCountryForRead(
                    countryId instanceof Number n ? n.intValue() : null);
            return ResponseEntity.ok(row);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load staged row {}", id, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Failed to load record: " + e.getMessage()));
        }
    }

    /**
     * The values behind each staged-table column filter.
     *
     * <p>Distinct from {@code /etl/staging/lookups}: that one offers every row
     * of every lookup because the edit form has to be able to set any of them,
     * while this one offers only what the staged rows actually use, so a filter
     * cannot be set to something that returns nothing.
     */
    @GetMapping("/etl/staging/filters")
    public ResponseEntity<?> etlStagedFilters(@RequestParam(required = false) Integer countryId) {
        requireMinistry("ETL staging");
        try {
            countryScopeService.validateCountryForRead(countryId);
            return ResponseEntity.ok(scholarshipEtlService.stagedFilterOptions(countryId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load staged filter options", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Failed to load filters: " + e.getMessage()));
        }
    }

    /** Every dropdown the staged-row edit form needs, in one response. */
    @GetMapping("/etl/staging/lookups")
    public ResponseEntity<?> etlEditLookups() {
        requireMinistry("ETL staging");
        try {
            return ResponseEntity.ok(scholarshipEtlService.editLookups());
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load staging edit lookups", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Failed to load lookups: " + e.getMessage()));
        }
    }

    /**
     * Writes corrections onto one staged row.
     *
     * <p>The body is a loose map because the form sends only what changed. The
     * service holds the whitelist of what may be written; nothing is validated
     * against the request here beyond the caller's right to touch this row at
     * all, which is decided by the row's own country and not by anything the
     * caller supplied.
     */
    @PutMapping("/etl/staging/{id:[0-9]+}")
    public ResponseEntity<?> updateEtlStagedRecord(
            @PathVariable int id, @RequestBody Map<String, Object> changes) {
        requireMinistry("ETL staging");
        try {
            Map<String, Object> current = scholarshipEtlService.findStaged(id);
            if (current == null) {
                return ResponseEntity.notFound().build();
            }
            Object countryId = current.get("STUDY_COUNTRY_IN");
            countryScopeService.validateCountryAccess(
                    countryId instanceof Number n ? n.intValue() : null);

            Long userId = SecurityRequestUtils.currentUserId().orElse(null);
            return ResponseEntity.ok(scholarshipEtlService.updateStaged(id, changes, userId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message",
                    String.valueOf(LocalizedMessage.resolve(messageSource, e, LocaleContextHolder.getLocale()))));
        } catch (Exception e) {
            log.error("Failed to update staged row {}", id, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Failed to save: " + e.getMessage()));
        }
    }

    /**
     * Hands a corrected row back to the ETL, so the next run replaces it.
     *
     * <p>The counterpart to editing: without it a row corrected once would sit
     * outside the ETL's reach forever.
     */
    @DeleteMapping("/etl/staging/{id:[0-9]+}/edit")
    public ResponseEntity<?> clearEtlStagedEdit(@PathVariable int id) {
        requireMinistry("ETL staging");
        try {
            Map<String, Object> current = scholarshipEtlService.findStaged(id);
            if (current == null) {
                return ResponseEntity.notFound().build();
            }
            Object countryId = current.get("STUDY_COUNTRY_IN");
            countryScopeService.validateCountryAccess(
                    countryId instanceof Number n ? n.intValue() : null);
            return ResponseEntity.ok(scholarshipEtlService.clearStagedEdit(id));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to clear the edit flag on staged row {}", id, e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false, "message", "Failed to reset: " + e.getMessage()));
        }
    }

    /**
     * The values behind each column filter, so the dropdowns offer what is
     * actually in the table rather than a fixed list that goes stale.
     */
    @GetMapping("/scholarship-data/filters")
    public ResponseEntity<?> getScholarshipFilters(@RequestParam(required = false) Integer countryId) {
        requireMinistry("Scholarship data");
        try {
            countryScopeService.validateCountryForRead(countryId);
            return ResponseEntity.ok(scholarshipDataImportService.filterOptions(countryId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to load scholarship filter options", e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to load filter options: " + e.getMessage()));
        }
    }

    /**
     * Pulls the {@code filter.COLUMN=a&filter.COLUMN=b} parameters out of the
     * query string.
     *
     * <p>They arrive as loose parameters because the number of them depends on
     * how many dropdowns the user has open, which is not something a fixed
     * method signature can express. Nothing is validated here on purpose — the
     * service holds the whitelist of filterable columns, and a key it does not
     * recognise is dropped there rather than trusted here.
     */
    private static Map<String, List<String>> columnFilters(MultiValueMap<String, String> params) {
        if (params == null) {
            return Map.of();
        }
        Map<String, List<String>> filters = new LinkedHashMap<>();
        params.forEach((key, values) -> {
            if (key != null && key.startsWith(FILTER_PREFIX) && key.length() > FILTER_PREFIX.length()) {
                filters.put(key.substring(FILTER_PREFIX.length()), values);
            }
        });
        return filters;
    }

    private static final String FILTER_PREFIX = "filter.";

    /**
     * Execute data import
     */
    @PostMapping("/execute")
    public ResponseEntity<?> executeImport(@Valid @RequestBody ImportRequest request) {
        requireMinistry("Data import");
        try {
            Map<String, Object> result = dataImportService.executeImport(
                    request.getTargetTable(),
                    request.getSourceColumns(),
                    request.getTargetColumns(),
                    request.getRows());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            // A table or column the caller invented: their mistake, not ours.
            log.warn("Rejected import request for table={}: {}", request.getTargetTable(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Data import execution failed for table={}", request.getTargetTable(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Import failed: " + e.getMessage()));
        }
    }

    // Request DTO for import execution
    public static class ImportRequest {
        private String targetTable;
        private List<String> sourceColumns;
        private List<String> targetColumns;
        private List<List<String>> rows;

        public String getTargetTable() {
            return targetTable;
        }

        public void setTargetTable(String targetTable) {
            this.targetTable = targetTable;
        }

        public List<String> getSourceColumns() {
            return sourceColumns;
        }

        public void setSourceColumns(List<String> sourceColumns) {
            this.sourceColumns = sourceColumns;
        }

        public List<String> getTargetColumns() {
            return targetColumns;
        }

        public void setTargetColumns(List<String> targetColumns) {
            this.targetColumns = targetColumns;
        }

        public List<List<String>> getRows() {
            return rows;
        }

        public void setRows(List<List<String>> rows) {
            this.rows = rows;
        }
    }
}
