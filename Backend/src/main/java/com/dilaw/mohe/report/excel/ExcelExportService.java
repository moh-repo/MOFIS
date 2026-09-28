package com.dilaw.mohe.report.excel;

import com.dilaw.mohe.report.BilingualRows;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Generates .xlsx files from typed data lists using Apache POI.
 * Column definitions are provided as parallel arrays (headers + field names).
 * Falls back to toString() for all cell values.
 */
@Service
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    private static final short HEADER_COLOR = IndexedColors.DARK_RED.getIndex();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Above this many rows, autoSizeColumn (O(rows) per column) gives way to header-based widths. */
    private static final int AUTOSIZE_ROW_LIMIT = 1000;

    /** S-07: row cap and export line; optional so a hand-built service in a unit test needs nothing. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dilaw.mohe.report.ExportGuard exportGuard;

    /** The localized title and headers; optional for the same reason. A missing bundle prints the key. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MessageSource messageSource;

    /** W5-03: the columns the caller may not see. Optional for the same reason. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dilaw.mohe.security.field.ReportFieldMask fieldMask;

    /**
     * The workbook in the language the export was asked for (QA B6 / live QA #12).
     *
     * <p>The hand-built sheets had English headers and Arabic lookup names in both
     * languages. Here the title and headers are message keys resolved in
     * {@code lang}, and an English export's rows go through {@link BilingualRows}
     * first -- the same swap {@code JasperReportService} gives every Jasper report
     * -- so English headers sit over English names. Anything but {@code "en"} is
     * Arabic, as in {@code ReportParamBuilder}; the Arabic rows pass through untouched.
     *
     * @param titleArgs arguments for the title message; pass strings, not numbers,
     *                  or MessageFormat prints a year as "2,025"
     */
    public <T> byte[] buildWorkbook(String lang, String titleKey, Object[] titleArgs,
                                    String[] headerKeys, String[] fieldNames, List<T> data) throws IOException {
        Locale locale = "en".equalsIgnoreCase(lang) ? Locale.ENGLISH : Locale.forLanguageTag("ar");
        String[] headers = new String[headerKeys.length];
        for (int c = 0; c < headerKeys.length; c++) {
            headers[c] = message(headerKeys[c], null, locale);
        }
        List<Object> rows = new ArrayList<>(BilingualRows.forLanguage(data, lang));
        return buildWorkbook(message(titleKey, titleArgs, locale), headers, fieldNames, rows);
    }

    private String message(String key, Object[] args, Locale locale) {
        return messageSource == null ? key : messageSource.getMessage(key, args, key, locale);
    }

    /**
     * Build an XLSX workbook for a flat list of beans.
     *
     * @param sheetTitle  displayed as a merged title row
     * @param headers     column header labels (same order as fieldNames)
     * @param fieldNames  Java field/getter names to read from each data item via reflection
     * @param data        list of data items
     * @return XLSX bytes ready for HTTP response
     */
    public <T> byte[] buildWorkbook(String sheetTitle, String[] headers, String[] fieldNames, List<T> data)
            throws IOException {
        return buildWorkbook(sheetTitle, headers, fieldNames, data,
                fieldMask == null ? null : fieldMask.viewer());
    }

    /**
     * The workbook as {@code viewer} may see it (W5-03).
     *
     * <p>A column naming a field that viewer may not read is written empty. The
     * accessors here read the declared field directly, so neither the Jackson
     * mask nor a masking getter would have covered this path — the national
     * number went into every one of these spreadsheets.
     *
     * <p>The overload exists for the scheduled exports: they run on a
     * {@code @Scheduled} thread with no security context, where the signed-in
     * caller is nobody and every sensitive column would be blanked. They pass
     * the schedule's owner instead, as they already do for country scope.
     */
    public <T> byte[] buildWorkbook(String sheetTitle, String[] headers, String[] fieldNames, List<T> data,
                                    com.dilaw.mohe.security.field.ReportFieldMask.Viewer viewer)
            throws IOException {
        if (exportGuard != null) exportGuard.enforce(data == null ? 0 : data.size());
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("Report");
            sheet.setRightToLeft(false);

            CellStyle titleStyle = buildTitleStyle(wb);
            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle evenStyle = buildEvenRowStyle(wb);
            CellStyle oddStyle = buildCellStyle(wb);

            int rowIdx = 0;

            // Title row
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(sheetTitle + "  —  " + LocalDate.now().format(DATE_FMT));
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(0, headers.length - 1)));
            titleRow.setHeightInPoints(24);

            // Blank separator
            sheet.createRow(rowIdx++);

            // Header row
            Row headerRow = sheet.createRow(rowIdx++);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = headerRow.createCell(c);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows. Accessors are resolved once per column, not once per
            // cell — readField used to re-walk the class hierarchy and throw/catch
            // NoSuchFieldException as control flow for every single cell.
            Class<?> itemType = data.stream().filter(Objects::nonNull).findFirst()
                    .<Class<?>>map(Object::getClass).orElse(null);
            java.util.Set<String> hidden = fieldMask == null || itemType == null || viewer == null
                    ? java.util.Set.of()
                    : fieldMask.hiddenOn(itemType, viewer);
            List<Function<Object, Object>> accessors = buildAccessors(itemType, fieldNames, hidden);
            for (T item : data) {
                Row row = sheet.createRow(rowIdx);
                CellStyle style = (rowIdx % 2 == 0) ? evenStyle : oddStyle;
                for (int c = 0; c < fieldNames.length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellStyle(style);
                    Object val = item != null ? accessors.get(c).apply(item) : null;
                    if (val instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(val != null ? val.toString() : "");
                    }
                }
                rowIdx++;
            }

            // Column widths. autoSizeColumn scans every row and is the usual POI
            // hot spot; beyond AUTOSIZE_ROW_LIMIT rows the width comes from the
            // header text instead.
            if (data.size() <= AUTOSIZE_ROW_LIMIT) {
                for (int c = 0; c < headers.length; c++) {
                    sheet.autoSizeColumn(c);
                    int width = sheet.getColumnWidth(c);
                    sheet.setColumnWidth(c, Math.min(width + 512, 20000));
                }
            } else {
                for (int c = 0; c < headers.length; c++) {
                    int approx = (headers[c] != null ? headers[c].length() : 8) * 256 + 1024;
                    sheet.setColumnWidth(c, Math.min(Math.max(approx, 3000), 20000));
                }
            }

            if (exportGuard != null) {
                com.dilaw.mohe.report.ExportWatermark.stampSheet(wb, sheet, exportGuard.watermark());
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * One accessor per column, resolved up front: the declared field anywhere in
     * the hierarchy first (any visibility), then a public {@code getXxx} — the
     * same resolution order the per-cell {@code readField} used. A name matching
     * neither yields a blank column and says so once in the log, instead of
     * failing silently on every cell.
     *
     * <p>A column in {@code hidden} is never read at all: the mask is a missing
     * accessor rather than a filtered value, so there is no path by which the
     * value reaches a cell (W5-03).
     */
    private static List<Function<Object, Object>> buildAccessors(Class<?> type, String[] fieldNames,
                                                                 java.util.Set<String> hidden) {
        List<Function<Object, Object>> accessors = new ArrayList<>(fieldNames.length);
        for (String name : fieldNames) {
            accessors.add(hidden.contains(name) ? o -> null : resolveAccessor(type, name));
        }
        return accessors;
    }

    private static Function<Object, Object> resolveAccessor(Class<?> type, String name) {
        if (type == null || name == null || name.isEmpty()) {
            return o -> null;
        }
        Field f = findField(type, name);
        if (f != null) {
            f.setAccessible(true);
            return o -> {
                try { return f.get(o); } catch (Exception e) { return null; }
            };
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method m = type.getMethod(getter);
            return o -> {
                try { return m.invoke(o); } catch (Exception e) { return null; }
            };
        } catch (NoSuchMethodException e) {
            log.debug("No field or getter '{}' on {}; the column will be blank", name, type.getName());
            return o -> null;
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private CellStyle buildTitleStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 13);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        return s;
    }

    private CellStyle buildHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 107, (byte) 39, (byte) 55}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    private CellStyle buildCellStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        setBorder(s);
        return s;
    }

    private CellStyle buildEvenRowStyle(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 253, (byte) 240, (byte) 242}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(s);
        return s;
    }

    private static void setBorder(XSSFCellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }
}
