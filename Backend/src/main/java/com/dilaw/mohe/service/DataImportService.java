package com.dilaw.mohe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Transactional
@Service
public class DataImportService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse uploaded file and return preview data
     */
    public Map<String, Object> parseFile(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        List<String> headers;
        List<List<String>> rows;

        if (filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            Map<String, Object> excelData = parseExcel(file);
            headers = (List<String>) excelData.get("headers");
            rows = (List<List<String>>) excelData.get("rows");
        } else if (filename != null && filename.endsWith(".csv")) {
            Map<String, Object> csvData = parseCsv(file);
            headers = (List<String>) csvData.get("headers");
            rows = (List<List<String>>) csvData.get("rows");
        } else {
            throw new RuntimeException("Unsupported file type. Only CSV, XLS, and XLSX files are allowed.");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("filename", filename);
        result.put("headers", headers);
        result.put("totalRows", rows.size());
        result.put("previewRows", rows.subList(0, Math.min(10, rows.size()))); // First 10 rows for preview
        result.put("allRows", rows);

        return result;
    }

    /**
     * Parse Excel file
     */
    private Map<String, Object> parseExcel(MultipartFile file) throws Exception {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        Workbook workbook;
        String filename = file.getOriginalFilename();

        if (filename != null && filename.endsWith(".xlsx")) {
            workbook = new XSSFWorkbook(file.getInputStream());
        } else {
            workbook = new HSSFWorkbook(file.getInputStream());
        }

        Sheet sheet = workbook.getSheetAt(0);
        boolean isFirstRow = true;

        for (Row row : sheet) {
            List<String> rowData = new ArrayList<>();
            for (Cell cell : row) {
                rowData.add(getCellValue(cell));
            }

            if (isFirstRow) {
                headers = rowData;
                isFirstRow = false;
            } else {
                rows.add(rowData);
            }
        }

        workbook.close();

        Map<String, Object> result = new HashMap<>();
        result.put("headers", headers);
        result.put("rows", rows);
        return result;
    }

    /**
     * Parse CSV file
     */
    private Map<String, Object> parseCsv(MultipartFile file) throws Exception {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstRow = true;

            while ((line = reader.readLine()) != null) {
                List<String> rowData = Arrays.asList(line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1))
                        .stream()
                        .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                        .collect(Collectors.toList());

                if (isFirstRow) {
                    headers = rowData;
                    isFirstRow = false;
                } else {
                    rows.add(rowData);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("headers", headers);
        result.put("rows", rows);
        return result;
    }

    /**
     * Import Excel file into QuarterlyBaseStaging for later ETL. First sheet only; each row stored as JSON.
     */
    public Map<String, Object> importToQuarterlyBaseStaging(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.xlsx";
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new RuntimeException("Only Excel files (.xlsx, .xls) are allowed for quarterly base import.");
        }

        Workbook workbook = filename.endsWith(".xlsx")
                ? new XSSFWorkbook(file.getInputStream())
                : new HSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);
        String sheetName = sheet.getSheetName();

        List<String> headers = new ArrayList<>();
        int rowIndex = 0;
        boolean isFirstRow = true;
        int inserted = 0;

        String insertSql = "INSERT INTO MOHECORE.QuarterlyBaseStaging (SourceFileName, SheetName, RowIndex, RowData, ImportedAt) VALUES (?, ?, ?, ?, GETUTCDATE())";

        for (Row row : sheet) {
            List<String> cellValues = new ArrayList<>();
            for (Cell cell : row) {
                cellValues.add(getCellValue(cell));
            }
            if (isFirstRow) {
                headers = cellValues;
                isFirstRow = false;
                rowIndex++;
                continue;
            }
            Map<String, String> rowMap = new LinkedHashMap<>();
            int colCount = Math.max(headers.size(), cellValues.size());
            for (int i = 0; i < colCount; i++) {
                String key = "column_" + (i + 1);
                String value = i < cellValues.size() ? cellValues.get(i) : "";
                rowMap.put(key, value);
            }
            String rowJson = objectMapper.writeValueAsString(rowMap);
            jdbcTemplate.update(insertSql, filename, sheetName, rowIndex, rowJson);
            inserted++;
            rowIndex++;
        }
        workbook.close();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("filename", filename);
        result.put("sheetName", sheetName);
        result.put("rowsImported", inserted);
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }

    /**
     * Get staging data from QuarterlyBaseStaging (optional filter by sourceFileName, paginated).
     */
    public Map<String, Object> getQuarterlyBaseStaging(String sourceFileName, int page, int size) {
        int offset = page * size;
        String countSql = "SELECT COUNT(*) FROM MOHECORE.QuarterlyBaseStaging WHERE 1=1";
        String dataSql = "SELECT Id, SourceFileName, SheetName, RowIndex, RowData, ImportedAt FROM MOHECORE.QuarterlyBaseStaging WHERE 1=1";
        List<Object> countParams = new ArrayList<>();
        List<Object> dataParams = new ArrayList<>();
        if (sourceFileName != null && !sourceFileName.isBlank()) {
            countSql += " AND SourceFileName = ?";
            dataSql += " AND SourceFileName = ?";
            countParams.add(sourceFileName);
            dataParams.add(sourceFileName);
        }
        dataSql += " ORDER BY ImportedAt DESC, RowIndex OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        dataParams.add(offset);
        dataParams.add(size);

        Integer total = countParams.isEmpty()
                ? jdbcTemplate.queryForObject(countSql, Integer.class)
                : jdbcTemplate.queryForObject(countSql, countParams.toArray(), Integer.class);
        if (total == null) total = 0;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, dataParams.toArray()).stream()
                .map(row -> {
                    Map<String, Object> map = new HashMap<>(row);
                    if (map.get("ImportedAt") != null && map.get("ImportedAt") instanceof java.sql.Timestamp ts) {
                        map.put("ImportedAt", ts.toLocalDateTime().toString());
                    }
                    return map;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", rows);
        result.put("totalElements", total);
        result.put("totalPages", size > 0 ? (int) Math.ceil((double) total / size) : 0);
        result.put("number", page);
        result.put("size", size);
        return result;
    }

    /**
     * Get available target tables for import
     */
    public List<Map<String, Object>> getAvailableTables() {
        String sql = "SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_SCHEMA, TABLE_NAME";

        return jdbcTemplate.queryForList(sql).stream()
                .map(row -> {
                    Map<String, Object> table = new HashMap<>();
                    table.put("schema", row.get("TABLE_SCHEMA"));
                    table.put("name", row.get("TABLE_NAME"));
                    table.put("fullName", row.get("TABLE_SCHEMA") + "." + row.get("TABLE_NAME"));
                    return table;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get columns for a specific table
     */
    public List<Map<String, Object>> getTableColumns(String schema, String tableName) {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";

        return jdbcTemplate.queryForList(sql, schema, tableName).stream()
                .map(row -> {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", row.get("COLUMN_NAME"));
                    column.put("type", row.get("DATA_TYPE"));
                    column.put("nullable", "YES".equals(row.get("IS_NULLABLE")));
                    column.put("hasDefault", row.get("COLUMN_DEFAULT") != null);
                    return column;
                })
                .collect(Collectors.toList());
    }

    /**
     * Resolves a caller-supplied table name to the one this database actually has,
     * and returns its schema and name.
     *
     * <p>Identifiers cannot be bound as parameters, so the statement below has to
     * concatenate them. An allow-list is therefore the control: the name must
     * match a row of {@code INFORMATION_SCHEMA.TABLES} exactly — the same list
     * {@code /tables} offered the caller — and the match is what gets
     * concatenated, never the caller's string.
     */
    private String[] resolveTargetTable(String targetTable) {
        if (targetTable == null || targetTable.isBlank()) {
            throw new IllegalArgumentException("A target table is required.");
        }
        String requested = targetTable.trim();
        for (Map<String, Object> table : getAvailableTables()) {
            if (requested.equalsIgnoreCase(String.valueOf(table.get("fullName")))) {
                return new String[] { String.valueOf(table.get("schema")), String.valueOf(table.get("name")) };
            }
        }
        throw new IllegalArgumentException("Unknown target table: " + requested);
    }

    /** Same rule for column names, checked against the resolved table's own columns. */
    private List<String> resolveTargetColumns(String schema, String tableName, List<String> targetColumns) {
        if (targetColumns == null || targetColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one target column is required.");
        }
        Map<String, String> known = new LinkedHashMap<>();
        for (Map<String, Object> column : getTableColumns(schema, tableName)) {
            String name = String.valueOf(column.get("name"));
            known.put(name.toLowerCase(Locale.ROOT), name);
        }
        List<String> resolved = new ArrayList<>();
        for (String requested : targetColumns) {
            String match = requested == null ? null : known.get(requested.trim().toLowerCase(Locale.ROOT));
            if (match == null) {
                throw new IllegalArgumentException(
                        "Unknown column '" + requested + "' on " + schema + "." + tableName);
            }
            resolved.add(match);
        }
        return resolved;
    }

    /**
     * The mapping contract: {@code sourceColumns[j]} feeds {@code targetColumns[j]}, and each
     * row's cells are in that same order — position j of the row is the value for target j.
     *
     * <p>This used to be {@code sourceColumns.indexOf(sourceColumns.get(j))}, a lookup of a
     * value in the list it was just read from, which is {@code j} — unless two source headers
     * share a name, when it is the earlier one's index and the wrong cell is written. Stating
     * the contract and checking it is what the lookup was pretending to do.
     */
    static void requirePairwiseMapping(List<String> sourceColumns, List<String> targetColumns) {
        if (sourceColumns == null || targetColumns == null) {
            throw new IllegalArgumentException("Source and target column lists are required.");
        }
        if (sourceColumns.size() != targetColumns.size()) {
            throw new IllegalArgumentException("Source columns (" + sourceColumns.size()
                    + ") and target columns (" + targetColumns.size() + ") must pair up one-to-one.");
        }
    }

    /** The first {@code columnCount} cells of a row, null-padded when the row is short. */
    static Object[] rowValues(List<String> row, int columnCount) {
        Object[] values = new Object[columnCount];
        for (int j = 0; j < columnCount; j++) {
            values[j] = j < row.size() ? row.get(j) : null;
        }
        return values;
    }

    /**
     * Execute data import
     */
    public Map<String, Object> executeImport(String targetTable, List<String> sourceColumns,
            List<String> targetColumns, List<List<String>> rows) {
        requirePairwiseMapping(sourceColumns, targetColumns);
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        String[] resolvedTable = resolveTargetTable(targetTable);
        String schema = resolvedTable[0];
        String tableName = resolvedTable[1];
        targetColumns = resolveTargetColumns(schema, tableName, targetColumns);
        targetTable = "[" + schema + "].[" + tableName + "]";

        // Build INSERT SQL
        String columns = targetColumns.stream().map(c -> "[" + c + "]").collect(Collectors.joining(", "));
        String placeholders = targetColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", targetTable, columns, placeholders);

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            try {
                Object[] values = rowValues(row, targetColumns.size());
                jdbcTemplate.update(sql, values);
                successCount++;
            } catch (Exception e) {
                failCount++;
                if (errors.size() < 10) { // Keep first 10 errors
                    errors.add("Row " + (i + 1) + ": " + e.getMessage());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", failCount == 0);
        result.put("totalRows", rows.size());
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("errors", errors);
        result.put("timestamp", LocalDateTime.now().toString());

        return result;
    }

    // Helper: Get cell value as string
    private String getCellValue(Cell cell) {
        if (cell == null)
            return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }
}
