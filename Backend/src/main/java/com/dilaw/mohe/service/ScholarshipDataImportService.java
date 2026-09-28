package com.dilaw.mohe.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.dilaw.mohe.exception.LocalizedArgumentException;

/**
 * Reads the attachés' quarterly financial-aid workbooks into
 * {@code MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT}, and keeps the workbook.
 *
 * <p>These sheets are printed forms, not exports, and the shape they arrive in is
 * the whole difficulty:
 *
 * <ul>
 *   <li>The grid's header is <em>not</em> the first row — eight rows of republic,
 *       ministry, quarter and policy notes sit above it. The header row is
 *       therefore searched for rather than assumed, and everything above it is
 *       parsed as the header block.</li>
 *   <li>The last row of the grid is a totals row, which must not become a
 *       student. It is excluded by requiring a serial number in the {@code م}
 *       column, which the totals row does not have.</li>
 *   <li>Numbers are numbers. Reading them through {@code String.valueOf(
 *       cell.getNumericCellValue())} — as the generic importer does — turns 2017
 *       into {@code "2017.0"}, which no {@code INT} column will take.</li>
 * </ul>
 *
 * <p>A single bad cell never fails an upload: it lands as NULL and is counted as
 * a warning. A missing grid column does fail it, because a sheet whose columns
 * cannot be identified is not this form and importing it would silently file
 * students' money under the wrong headings.
 */
@Service
public class ScholarshipDataImportService {

    private static final Logger log = LoggerFactory.getLogger(ScholarshipDataImportService.class);

    /** Rows scanned from the top before giving up on finding the grid header. */
    private static final int HEADER_SEARCH_LIMIT = 40;

    /** Columns scanned across when reading the header block. */
    private static final int HEADER_BLOCK_WIDTH = 30;

    private static final String TABLE = "MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT";

    /**
     * Grid columns, in the order the sheet lays them out. The key is the database
     * column; the values are the Arabic captions accepted for it, compared after
     * {@link #normaliseArabic} so spelling variants (الدرجه/الدرجة) and stray
     * tatweel need no separate entry.
     */
    private static final Map<String, List<String>> GRID_COLUMNS = new LinkedHashMap<>();
    static {
        GRID_COLUMNS.put("SERIAL", List.of("م", "رقم"));
        GRID_COLUMNS.put("STUDENT_NAME", List.of("اسم الطالب", "الاسم", "اسم الطالبة"));
        GRID_COLUMNS.put("ENTITY", List.of("الجهة"));
        GRID_COLUMNS.put("COUNTRY", List.of("الدولة"));
        GRID_COLUMNS.put("SCHOLARSHIP_YEAR", List.of("عام الايفاد", "سنة الايفاد"));
        GRID_COLUMNS.put("DEGREE", List.of("الدرجة", "الدرجة العلمية"));
        GRID_COLUMNS.put("SPECIALIZATION", List.of("التخصص"));
        GRID_COLUMNS.put("DECISION_NO", List.of("رقم القرار"));
        GRID_COLUMNS.put("STUDY_DURATION", List.of("مدة الدراسة"));
        GRID_COLUMNS.put("CURRENT_LEVEL", List.of("المستوى الحالي"));
        GRID_COLUMNS.put("FIRST_DISBURSEMENT", List.of("اول مصرف", "أول مصرف"));
        GRID_COLUMNS.put("AMOUNT", List.of("المبلغ"));
        GRID_COLUMNS.put("STUDENT_SIGNATURE", List.of("توقيع الطالب"));
        GRID_COLUMNS.put("NOTES", List.of("ملاحظات"));
        GRID_COLUMNS.put("FINANCIAL_NOTES", List.of("ملاحظات المالية", "ملاحظات مالية"));
    }

    /** Without this column the sheet is not the form we think it is. */
    private static final String REQUIRED_COLUMN = "STUDENT_NAME";


    /*
     * Header-block captions, held in their normalised form.
     *
     * They have to be normalised on both sides of the comparison, not just the
     * sheet's: normalisation folds ة into ه, so a literal "الجهة" written here
     * would never match the "الجهه" that comes back out of normaliseArabic.
     */
    private static final String LABEL_QUARTER = normaliseArabic("الربع");
    private static final String LABEL_YEAR = normaliseArabic("العام");
    private static final String LABEL_ENTITY = normaliseArabic("الجهة");
    private static final String LABEL_NOTES = normaliseArabic("ملاحظات");

    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("(\\d{4})");
    private static final Pattern DATE_LIKE = Pattern.compile("\\d{1,2}[./\\-]\\d{1,2}[./\\-]\\d{2,4}");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CountryFileStorageService countryFileStorage;

    @Autowired
    private FileEncryptionService fileEncryptionService;

    @Autowired
    private com.dilaw.mohe.service.codelist.CodeListService codeLists;

    // ────────────────────────────────────────────────────────────── parsing ──

    /** The header block above the grid: per-file facts, repeated onto every row. */
    public static class SheetHeader {
        public String republic;
        public String ministry;
        public String department;
        public String reportTitle;
        public String entity;
        public String quarter;
        public Integer quarterNo;
        public String year;
        public Integer yearNo;
        public String statementDate;
        /** Up to four; a fifth or later is folded into the fourth rather than dropped. */
        public final String[] notes = new String[4];
    }

    /**
     * What to clear out before a batch is written.
     *
     * <p>The table appends by default: each upload is another batch, told apart
     * by {@code SOURCE_FILE} and {@code IMPORTED_AT}. These are the two ways of
     * saying "and drop what was already there", and they are chosen per import
     * rather than being a standing setting, because that is the only point at
     * which anyone is looking at what they are replacing.
     */
    public enum DeleteMode {
        /** Add to what is already there. */
        NONE,
        /** Drop every row for the country being imported, whatever quarter it came from. */
        COUNTRY,
        /** Drop the whole table, every country. */
        ALL;

        /**
         * ALL contains COUNTRY, so a request for both is a request for ALL — one
         * statement, not two. Resolving it here keeps the caller from having to.
         */
        public static DeleteMode of(boolean deleteCountryData, boolean deleteAllData) {
            if (deleteAllData) {
                return ALL;
            }
            return deleteCountryData ? COUNTRY : NONE;
        }
    }

    /** One workbook, read. */
    public static class ParsedSheet {
        public String sheetName;
        /** 1-based, as a person counting rows in Excel would say it. */
        public int headerRowNumber;
        public SheetHeader header = new SheetHeader();
        /** Database column → 0-based sheet column, for the captions that were found. */
        public Map<String, Integer> columnIndex = new LinkedHashMap<>();
        /** Database column → value, plus {@code _row} carrying the 1-based sheet row. */
        public List<Map<String, Object>> rows = new ArrayList<>();
        public int skippedRows;
        public final List<String> warnings = new ArrayList<>();
    }

    public ParsedSheet parse(MultipartFile file) throws IOException {
        try (Workbook workbook = open(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("The workbook has no sheets.");
            }

            ParsedSheet parsed = new ParsedSheet();
            parsed.sheetName = sheet.getSheetName();

            int headerRow = findHeaderRow(sheet);
            if (headerRow < 0) {
                // Review M13: say what is looked for, so the sheet can be fixed.
                // In the reader's language (W3-08): this was Arabic on an English screen.
                // The captions stay as the workbook spells them, in Arabic.
                String nameCaptions = String.join("، ", GRID_COLUMNS.get(REQUIRED_COLUMN));
                throw new LocalizedArgumentException(
                        "No header row in the first " + HEADER_SEARCH_LIMIT + " rows; the student-name column is one of: "
                                + nameCaptions + ". Other columns: " + expectedCaptions() + ".",
                        "import.header.notFound", String.valueOf(HEADER_SEARCH_LIMIT), nameCaptions, expectedCaptions());
            }
            parsed.headerRowNumber = headerRow + 1;
            parsed.columnIndex = mapColumns(sheet.getRow(headerRow), parsed.warnings);
            parsed.header = readHeaderBlock(sheet, headerRow);
            readGrid(sheet, headerRow, parsed);
            return parsed;
        }
    }

    private Workbook open(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        boolean legacy = name != null && name.toLowerCase(Locale.ROOT).endsWith(".xls");
        try (InputStream in = file.getInputStream()) {
            return legacy ? new HSSFWorkbook(in) : new XSSFWorkbook(in);
        }
    }

    /**
     * The grid header: the first row with a student-name column and at least one
     * other grid caption, else the first with a student-name column at all.
     *
     * <p>Review M13: captions used to be matched exactly, so "اسم الطالب رباعي"
     * or "المبلغ بالدولار" in a sheet that was otherwise the right form meant
     * "header not found". A caption now also matches a cell that contains it
     * (see {@link #columnFor}); asking for a second column keeps a title line
     * that happens to mention a student's name from being taken for the header.
     */
    private int findHeaderRow(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_LIMIT);
        int fallback = -1;
        for (int r = sheet.getFirstRowNum(); r <= limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            java.util.Set<String> found = new java.util.HashSet<>();
            for (Cell cell : row) {
                String column = columnFor(normaliseArabic(text(cell)));
                if (column != null) {
                    found.add(column);
                }
            }
            if (found.contains(REQUIRED_COLUMN)) {
                if (found.size() >= 2) {
                    return r;
                }
                if (fallback < 0) {
                    fallback = r;
                }
            }
        }
        return fallback;
    }

    /** Captions this short ("م") must match the whole cell: nearly every caption contains them. */
    private static final int MIN_CONTAINED_CAPTION = 3;

    /**
     * The grid column a normalised header cell names, or null. An exact caption
     * wins; otherwise the longest caption the cell contains, so "ملاحظات المالية"
     * is FINANCIAL_NOTES and not NOTES, which it also contains.
     */
    static String columnFor(String caption) {
        if (caption == null || caption.isEmpty()) {
            return null;
        }
        String best = null;
        int bestLength = 0;
        for (Map.Entry<String, List<String>> entry : GRID_COLUMNS.entrySet()) {
            for (String raw : entry.getValue()) {
                String wanted = normaliseArabic(raw);
                if (caption.equals(wanted)) {
                    return entry.getKey();
                }
                if (wanted.length() >= MIN_CONTAINED_CAPTION && wanted.length() > bestLength
                        && caption.contains(wanted)) {
                    best = entry.getKey();
                    bestLength = wanted.length();
                }
            }
        }
        return best;
    }

    /** Every column's first caption, for the "header not found" message. */
    private static String expectedCaptions() {
        return GRID_COLUMNS.values().stream().map(list -> list.get(0))
                .collect(java.util.stream.Collectors.joining("، "));
    }

    private Map<String, Integer> mapColumns(Row headerRow, List<String> warnings) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String caption = normaliseArabic(text(cell));
            // Exact caption, else the longest one the cell contains (review M13):
            // 'ملاحظات' and 'ملاحظات المالية' stay two columns because the longer
            // caption wins.
            String column = columnFor(caption);
            if (column != null && !index.containsKey(column)) {
                index.put(column, cell.getColumnIndex());
            }
        }

        if (!index.containsKey(REQUIRED_COLUMN)) {
            throw new LocalizedArgumentException("The sheet has no 'اسم الطالب' column.", "import.header.noStudentName");
        }
        for (String column : GRID_COLUMNS.keySet()) {
            if (!index.containsKey(column)) {
                warnings.add("Column not found in the sheet, will be left empty: " + column);
            }
        }
        return index;
    }

    /**
     * Everything above the grid.
     *
     * <p>Read by label and by position rather than by row number: the grid header
     * is found by search, so this block's own rows are wherever they happen to be.
     * The left-hand column carries the republic, the ministry and the department
     * in that order, then the notes; the quarter, year and entity are values sitting
     * to the right of their captions.
     */
    private SheetHeader readHeaderBlock(Sheet sheet, int headerRow) {
        SheetHeader header = new SheetHeader();
        List<String> leftColumn = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        boolean notesStarted = false;

        for (int r = sheet.getFirstRowNum(); r < headerRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }

            for (int c = 0; c < HEADER_BLOCK_WIDTH; c++) {
                String value = text(row.getCell(c));
                if (value.isEmpty()) {
                    continue;
                }
                String key = normaliseArabic(value);

                if (c == 0) {
                    if (key.startsWith(LABEL_NOTES)) {
                        // 'ملاحظات هامة:' is the caption for what follows, not a note.
                        notesStarted = true;
                    } else if (notesStarted) {
                        notes.add(value);
                    } else {
                        leftColumn.add(value);
                    }
                    continue;
                }

                if (key.startsWith(LABEL_QUARTER) && header.quarter == null) {
                    header.quarter = valueRightOf(row, c);
                } else if (key.startsWith(LABEL_YEAR) && header.year == null) {
                    header.year = valueRightOf(row, c);
                } else if (key.startsWith(LABEL_ENTITY) && header.entity == null) {
                    header.entity = valueRightOf(row, c);
                } else if (header.reportTitle == null && r == sheet.getFirstRowNum()) {
                    // The banner on the right of the first row.
                    header.reportTitle = value;
                } else if (header.statementDate == null && DATE_LIKE.matcher(value).matches()) {
                    header.statementDate = value;
                }
            }
        }

        // A note may also be wrapped across the left column with no caption above
        // it; anything past the third left-column line is a note either way.
        if (notes.isEmpty() && leftColumn.size() > 3) {
            notes.addAll(leftColumn.subList(3, leftColumn.size()));
            leftColumn = leftColumn.subList(0, 3);
        }

        header.republic = at(leftColumn, 0);
        header.ministry = at(leftColumn, 1);
        header.department = at(leftColumn, 2);
        header.quarterNo = quarterNumber(header.quarter);
        header.yearNo = firstFourDigitNumber(header.year);

        for (int i = 0; i < notes.size(); i++) {
            if (i < header.notes.length - 1) {
                header.notes[i] = notes.get(i);
            } else {
                // Beyond the fourth slot, append rather than drop.
                header.notes[header.notes.length - 1] =
                        header.notes[header.notes.length - 1] == null
                                ? notes.get(i)
                                : header.notes[header.notes.length - 1] + "\n" + notes.get(i);
            }
        }
        return header;
    }

    /** The first non-empty cell after {@code col} on the same row. */
    private String valueRightOf(Row row, int col) {
        for (int c = col + 1; c < HEADER_BLOCK_WIDTH; c++) {
            String value = text(row.getCell(c));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void readGrid(Sheet sheet, int headerRow, ParsedSheet parsed) {
        Integer serialCol = parsed.columnIndex.get("SERIAL");
        int nameCol = parsed.columnIndex.get(REQUIRED_COLUMN);
        int badNumbers = 0;

        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String name = text(row.getCell(nameCol));

            /*
             * The totals row is the reason this test exists. It carries the word
             * الإجمالي and the summed amount but no serial number, so requiring a
             * serial keeps it out — along with the blank rows that trail the grid.
             * Where a sheet has no serial column at all, a name is the fallback,
             * and the الإجمالي check below catches the total either way (it is
             * written with tatweel padding, which normalising removes).
             */
            boolean isDataRow = serialCol != null
                    ? asInteger(row.getCell(serialCol)) != null
                    : !name.isEmpty();
            if (!isDataRow || isTotalsRow(name)) {
                if (hasAnyValue(row)) {
                    parsed.skippedRows++;
                }
                continue;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("_row", r + 1);
            values.put("STUDENT_NAME", name.isEmpty() ? null : name);
            values.put("ENTITY", stringAt(row, parsed, "ENTITY"));
            values.put("COUNTRY", stringAt(row, parsed, "COUNTRY"));
            values.put("SCHOLARSHIP_YEAR", intAt(row, parsed, "SCHOLARSHIP_YEAR"));
            values.put("DEGREE", stringAt(row, parsed, "DEGREE"));
            values.put("SPECIALIZATION", stringAt(row, parsed, "SPECIALIZATION"));
            values.put("DECISION_NO", stringAt(row, parsed, "DECISION_NO"));
            values.put("STUDY_DURATION", intAt(row, parsed, "STUDY_DURATION"));
            values.put("CURRENT_LEVEL", intAt(row, parsed, "CURRENT_LEVEL"));
            values.put("FIRST_DISBURSEMENT", intAt(row, parsed, "FIRST_DISBURSEMENT"));
            values.put("AMOUNT", decimalAt(row, parsed, "AMOUNT"));
            values.put("STUDENT_SIGNATURE", stringAt(row, parsed, "STUDENT_SIGNATURE"));
            values.put("NOTES", stringAt(row, parsed, "NOTES"));
            values.put("FINANCIAL_NOTES", stringAt(row, parsed, "FINANCIAL_NOTES"));

            // A number that would not parse is worth reporting, but not worth
            // refusing the row over: the rest of the line is still true.
            for (String numeric : List.of("SCHOLARSHIP_YEAR", "STUDY_DURATION", "CURRENT_LEVEL",
                    "FIRST_DISBURSEMENT", "AMOUNT")) {
                Integer col = parsed.columnIndex.get(numeric);
                if (col != null && values.get(numeric) == null && !text(row.getCell(col)).isEmpty()) {
                    badNumbers++;
                }
            }

            parsed.rows.add(values);
        }

        if (badNumbers > 0) {
            parsed.warnings.add(badNumbers + " numeric cell(s) could not be read and were left empty.");
        }
    }

    private boolean isTotalsRow(String name) {
        String key = normaliseArabic(name);
        return key.startsWith("الاجمالي") || key.startsWith("اجمالي") || key.startsWith("المجموع");
    }

    private boolean hasAnyValue(Row row) {
        for (Cell cell : row) {
            if (!text(cell).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String stringAt(Row row, ParsedSheet parsed, String column) {
        Integer col = parsed.columnIndex.get(column);
        if (col == null) {
            return null;
        }
        String value = text(row.getCell(col));
        return value.isEmpty() ? null : value;
    }

    private Integer intAt(Row row, ParsedSheet parsed, String column) {
        Integer col = parsed.columnIndex.get(column);
        return col == null ? null : asInteger(row.getCell(col));
    }

    private BigDecimal decimalAt(Row row, ParsedSheet parsed, String column) {
        Integer col = parsed.columnIndex.get(column);
        return col == null ? null : asDecimal(row.getCell(col));
    }

    // ─────────────────────────────────────────────────────────── cell values ──

    /**
     * A cell as text.
     *
     * <p>Numerics are formatted through {@link BigDecimal} rather than
     * {@code String.valueOf(double)} so that 2017 stays "2017" instead of
     * becoming "2017.0" — the defect that makes the generic importer unusable
     * for this sheet.
     */
    private String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        switch (type) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                return BigDecimal.valueOf(cell.getNumericCellValue())
                        .stripTrailingZeros().toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private Integer asInteger(Cell cell) {
        BigDecimal value = asDecimal(cell);
        if (value == null) {
            return null;
        }
        try {
            return value.setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private BigDecimal asDecimal(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String raw = text(cell);
        if (raw.isEmpty()) {
            return null;
        }
        // Sheets typed by hand carry Arabic-Indic digits, thousands separators and
        // the odd stray letter; keep the number and drop the decoration.
        String cleaned = toWesternDigits(raw).replaceAll("[,\\s\\u066C\\u066B]", "");
        cleaned = cleaned.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Arabic-Indic (٠-٩) and Extended Arabic-Indic (۰-۹) digits to 0-9. */
    private static String toWesternDigits(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c >= '٠' && c <= '٩') {
                out.append((char) ('0' + (c - '٠')));
            } else if (c >= '۰' && c <= '۹') {
                out.append((char) ('0' + (c - '۰')));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Arabic text reduced to what two spellings of the same word have in common:
     * tatweel and diacritics removed, alef and ya and ta-marbuta unified,
     * whitespace collapsed.
     *
     * <p>This is what lets 'الدرجه' match 'الدرجة' and — the case that matters —
     * lets the totals row's 'الإجـــــمـــــالي' be recognised as 'الاجمالي'.
     */
    static String normaliseArabic(String value) {
        if (value == null) {
            return "";
        }
        String out = value.replace("ـ", "")            // tatweel
                .replaceAll("[ً-ْ]", "")          // harakat
                .replaceAll("[أإآٱ]", "ا") // أ إ آ ٱ -> ا
                .replace('ة', 'ه')                // ة -> ه
                .replace('ى', 'ي')                // ى -> ي
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        // Captions are punctuated inconsistently ('الربع:' / 'الربع').
        return out.replaceAll("[:：]+$", "").trim();
    }

    private static String at(List<String> list, int index) {
        return index < list.size() ? list.get(index) : null;
    }

    /**
     * {@code الربع: الثالث} — the quarter is written as an ordinal word, not a
     * digit. The words are the {@code LBL_QUARTER_ORDINAL_n} rows of
     * {@code LU_REPORT_LABEL} (V076), compared in normalised form on both sides.
     */
    private Integer quarterNumber(String quarter) {
        if (quarter == null) {
            return null;
        }
        String wanted = normaliseArabic(quarter);
        for (int n = 1; n <= 4; n++) {
            String word = codeLists.label("report-label", com.dilaw.mohe.codes.ReportLabelCode.quarterOrdinal(n), "ar");
            if (word != null && normaliseArabic(word).equals(wanted)) {
                return n;
            }
        }
        String digits = toWesternDigits(quarter).replaceAll("[^0-9]", "");
        if (digits.length() == 1) {
            int n = Integer.parseInt(digits);
            return n >= 1 && n <= 4 ? n : null;
        }
        return null;
    }

    private static Integer firstFourDigitNumber(String value) {
        if (value == null) {
            return null;
        }
        Matcher m = FOUR_DIGIT_YEAR.matcher(toWesternDigits(value));
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    // ─────────────────────────────────────────────────────────────── preview ──

    /** What the screen shows before anything is written. */
    public Map<String, Object> preview(MultipartFile file) throws IOException {
        ParsedSheet parsed = parse(file);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("filename", file.getOriginalFilename());
        result.put("sheetName", parsed.sheetName);
        result.put("headerRow", parsed.headerRowNumber);
        result.put("header", headerAsMap(parsed.header));
        result.put("columns", parsed.columnIndex);
        result.put("totalRows", parsed.rows.size());
        result.put("skippedRows", parsed.skippedRows);
        result.put("warnings", parsed.warnings);
        result.put("previewRows", parsed.rows.subList(0, Math.min(10, parsed.rows.size())));
        return result;
    }

    private Map<String, Object> headerAsMap(SheetHeader header) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("republic", header.republic);
        map.put("ministry", header.ministry);
        map.put("department", header.department);
        map.put("reportTitle", header.reportTitle);
        map.put("entity", header.entity);
        map.put("quarter", header.quarter);
        map.put("quarterNo", header.quarterNo);
        map.put("year", header.year);
        map.put("yearNo", header.yearNo);
        map.put("statementDate", header.statementDate);
        map.put("notes", List.of(
                header.notes[0] == null ? "" : header.notes[0],
                header.notes[1] == null ? "" : header.notes[1],
                header.notes[2] == null ? "" : header.notes[2],
                header.notes[3] == null ? "" : header.notes[3]));
        return map;
    }

    // ──────────────────────────────────────────────────────────────── import ──

    private static final String INSERT_SQL = "INSERT INTO " + TABLE + " ("
            + "STUDENT_NAME, ENTITY, COUNTRY, SCHOLARSHIP_YEAR, DEGREE, SPECIALIZATION, DECISION_NO, "
            + "STUDY_DURATION, CURRENT_LEVEL, FIRST_DISBURSEMENT, AMOUNT, STUDENT_SIGNATURE, NOTES, FINANCIAL_NOTES, "
            + "HDR_REPUBLIC, HDR_MINISTRY, HDR_DEPARTMENT, HDR_REPORT_TITLE, HDR_ENTITY, "
            + "HDR_QUARTER, HDR_QUARTER_NO, HDR_YEAR, HDR_YEAR_NO, HDR_STATEMENT_DATE, "
            + "HDR_NOTE_1, HDR_NOTE_2, HDR_NOTE_3, HDR_NOTE_4, "
            + "COUNTRY_ID, SOURCE_FILE, SOURCE_SHEET, SOURCE_ROW, IMPORTED_AT, IMPORTED_BY"
            + ") VALUES (" + "?,".repeat(33) + "?)";

    /**
     * Archives the workbook under the country's folder and writes its rows,
     * optionally clearing what was there first.
     *
     * <p>The order is deliberate and is most of the safety of this method:
     *
     * <ol>
     *   <li><b>Parse before deleting.</b> A workbook that is not this form, or
     *       that has no data rows, throws here — before any {@code DELETE} has
     *       run. Deleting first and discovering afterwards that the replacement
     *       is unreadable is how you lose a quarter's data.</li>
     *   <li><b>Delete and insert share this transaction.</b> If the insert fails
     *       halfway the delete goes back with it, so the table is never left
     *       emptied by an import that did not finish.</li>
     *   <li><b>The file is archived between the two</b> because its path is
     *       recorded on every row. It is removed again if the insert throws, so a
     *       rolled-back import leaves no file that no row refers to. The archive
     *       is deliberately <em>not</em> touched by {@code deleteMode}: the
     *       workbooks are the record of what the attaché actually sent, and they
     *       are what makes a mistaken delete recoverable.</li>
     * </ol>
     */
    @Transactional
    public Map<String, Object> importFile(MultipartFile file, Integer countryId, Long userId,
            DeleteMode deleteMode) throws IOException {
        // Settled once, here: an absent mode means append. Leaving it null would
        // make every later use of it a null check, and the one that got missed
        // would be the one that decides whether to delete.
        DeleteMode mode = deleteMode == null ? DeleteMode.NONE : deleteMode;
        ParsedSheet parsed = parse(file);
        if (parsed.rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "No data rows were found below the header on row " + parsed.headerRowNumber + ".");
        }

        int rowsDeleted = applyDeleteMode(mode, countryId);

        LocalDateTime importedAt = LocalDateTime.now();
        Path dir = countryFileStorage.sectionDir(countryId, CountryFileStorageService.SECTION_DATA_IMPORT_XLS);
        Path stored = dir.resolve(countryFileStorage.buildFilename(file.getOriginalFilename(), userId, importedAt));
        fileEncryptionService.encryptMultipartFile(file, stored);
        String relativePath = countryFileStorage.relativise(stored);

        try {
            insertRows(parsed, countryId, relativePath, importedAt, userId);
        } catch (RuntimeException e) {
            deleteQuietly(stored);
            throw e;
        }

        log.info("Imported {} scholarship rows for country {} from {} (deleteMode={}, rowsDeleted={})",
                parsed.rows.size(), countryId, relativePath, mode, rowsDeleted);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("filename", file.getOriginalFilename());
        result.put("storedFile", relativePath);
        result.put("sheetName", parsed.sheetName);
        result.put("headerRow", parsed.headerRowNumber);
        result.put("header", headerAsMap(parsed.header));
        result.put("countryId", countryId);
        result.put("rowsImported", parsed.rows.size());
        result.put("deleteMode", mode.name());
        result.put("rowsDeleted", rowsDeleted);
        result.put("skippedRows", parsed.skippedRows);
        result.put("warnings", parsed.warnings);
        result.put("importedAt", importedAt.toString());
        return result;
    }

    /**
     * Clears the rows the chosen mode calls for, and returns how many went.
     *
     * <p>{@code COUNTRY} is bound to the id and can only ever narrow. The
     * unfiltered statement is reachable from {@code ALL} alone — kept as two
     * separate literals rather than one string with an optional WHERE, so that a
     * null slipping into the parameter cannot quietly turn one into the other.
     */
    private int applyDeleteMode(DeleteMode deleteMode, Integer countryId) {
        if (deleteMode == null || deleteMode == DeleteMode.NONE) {
            return 0;
        }
        if (deleteMode == DeleteMode.ALL) {
            return jdbcTemplate.update("DELETE FROM " + TABLE);
        }
        if (countryId == null || countryId <= 0) {
            throw new IllegalArgumentException("A country is required to replace that country's data.");
        }
        return jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE COUNTRY_ID = ?", countryId);
    }

    /**
     * How much a delete would take, for the confirmation the screen shows before
     * running one. A warning that cannot say how many rows it is about is not
     * worth reading.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> counts(Integer countryId) {
        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        Integer forCountry = countryId == null ? null : jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE COUNTRY_ID = ?", Integer.class, countryId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("countryId", countryId);
        result.put("countryRows", forCountry == null ? 0 : forCountry);
        result.put("totalRows", total == null ? 0 : total);
        return result;
    }

    private void insertRows(ParsedSheet parsed, Integer countryId, String relativePath,
            LocalDateTime importedAt, Long userId) {
        SheetHeader h = parsed.header;
        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map<String, Object> row = parsed.rows.get(i);
                int p = 0;
                setString(ps, ++p, row.get("STUDENT_NAME"));
                setString(ps, ++p, row.get("ENTITY"));
                setString(ps, ++p, row.get("COUNTRY"));
                setInt(ps, ++p, row.get("SCHOLARSHIP_YEAR"));
                setString(ps, ++p, row.get("DEGREE"));
                setString(ps, ++p, row.get("SPECIALIZATION"));
                setString(ps, ++p, row.get("DECISION_NO"));
                setInt(ps, ++p, row.get("STUDY_DURATION"));
                setInt(ps, ++p, row.get("CURRENT_LEVEL"));
                setInt(ps, ++p, row.get("FIRST_DISBURSEMENT"));
                setDecimal(ps, ++p, row.get("AMOUNT"));
                setString(ps, ++p, row.get("STUDENT_SIGNATURE"));
                setString(ps, ++p, row.get("NOTES"));
                setString(ps, ++p, row.get("FINANCIAL_NOTES"));

                setString(ps, ++p, h.republic);
                setString(ps, ++p, h.ministry);
                setString(ps, ++p, h.department);
                setString(ps, ++p, h.reportTitle);
                setString(ps, ++p, h.entity);
                setString(ps, ++p, h.quarter);
                setInt(ps, ++p, h.quarterNo);
                setString(ps, ++p, h.year);
                setInt(ps, ++p, h.yearNo);
                setString(ps, ++p, h.statementDate);
                setString(ps, ++p, h.notes[0]);
                setString(ps, ++p, h.notes[1]);
                setString(ps, ++p, h.notes[2]);
                setString(ps, ++p, h.notes[3]);

                setInt(ps, ++p, countryId);
                setString(ps, ++p, relativePath);
                setString(ps, ++p, parsed.sheetName);
                setInt(ps, ++p, row.get("_row"));
                ps.setTimestamp(++p, java.sql.Timestamp.valueOf(importedAt));
                setInt(ps, ++p, userId == null ? null : userId.intValue());
            }

            @Override
            public int getBatchSize() {
                return parsed.rows.size();
            }
        });
    }

    private static void setString(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NVARCHAR);
        } else {
            ps.setNString(index, String.valueOf(value));
        }
    }

    private static void setInt(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, ((Number) value).intValue());
        }
    }

    private static void setDecimal(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DECIMAL);
        } else {
            ps.setBigDecimal(index, (BigDecimal) value);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove the archived import after a failed insert: {}", path, e);
        }
    }

    // ───────────────────────────────────────────────────────────────── reads ──

    /**
     * What the client may sort by, and the column each choice really orders on.
     *
     * <p>This map is the security boundary for the ORDER BY clause. A sort
     * column cannot be a bind parameter, so it has to be concatenated into the
     * SQL; the only safe way to do that is to never concatenate anything the
     * client sent. A key that is not in here is not "rejected", it is simply
     * absent, and the query falls back to the default ordering.
     *
     * <p>Two of them deliberately order on a different column than they show.
     * The quarter reads "الثالث" and the year "2025م" — sorting those as text
     * puts the fourth quarter before the first. The parser already resolved
     * both to numbers, so the sort uses those and the reader sees the words.
     */
    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("ID", "ID"),
            Map.entry("STUDENT_NAME", "STUDENT_NAME"),
            Map.entry("COUNTRY", "COUNTRY"),
            Map.entry("ENTITY", "ENTITY"),
            Map.entry("DEGREE", "DEGREE"),
            Map.entry("SPECIALIZATION", "SPECIALIZATION"),
            Map.entry("SCHOLARSHIP_YEAR", "SCHOLARSHIP_YEAR"),
            Map.entry("AMOUNT", "AMOUNT"),
            Map.entry("HDR_QUARTER", "HDR_QUARTER_NO"),
            Map.entry("HDR_YEAR", "HDR_YEAR_NO"),
            Map.entry("IMPORTED_AT", "IMPORTED_AT"));

    /** Columns offered as a checkbox filter, on the same whitelist footing. */
    private static final Map<String, String> FILTERABLE = Map.of(
            "COUNTRY", "COUNTRY",
            "DEGREE", "DEGREE",
            "ENTITY", "ENTITY",
            "HDR_QUARTER", "HDR_QUARTER",
            "HDR_YEAR", "HDR_YEAR");

    /** Columns the single search box looks in. */
    private static final List<String> SEARCH_COLUMNS = List.of(
            "STUDENT_NAME", "COUNTRY", "DEGREE", "SPECIALIZATION", "DECISION_NO", "ENTITY");

    /** A search of more words than this is a paste, not a query. */
    private static final int MAX_SEARCH_TERMS = 5;

    /** Enough values to choose from without turning a dropdown into a scroll. */
    private static final int MAX_FILTER_OPTIONS = 200;

    /** Guards against a caller pinning the server with a thousand-value IN list. */
    private static final int MAX_FILTER_VALUES = 200;

    /**
     * How the caller wants the list sliced.
     *
     * <p>Every field is untrusted input and every field is optional; the empty
     * query is the plain "newest batch first" list this endpoint always
     * returned.
     */
    public record ListQuery(String sortBy, String sortDir, String search,
                            Map<String, List<String>> filters) {

        public static final ListQuery DEFAULT = new ListQuery(null, null, null, Map.of());

        public ListQuery {
            filters = filters == null ? Map.of() : filters;
        }
    }

    /**
     * One imported row, every column of it.
     *
     * <p>Separate from {@link #list} on purpose: the table needs eight columns
     * per row and the record view needs all thirty-odd, including four notes of
     * policy text repeated onto every row of a batch. Selecting those in the
     * list would put several hundred kilobytes of identical prose into every
     * page fetch to serve a panel that is usually closed.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> findById(int id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + TABLE + " WHERE ID = ?", id);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        if (row.get("IMPORTED_AT") instanceof java.sql.Timestamp ts) {
            row.put("IMPORTED_AT", ts.toLocalDateTime().toString());
        }
        return row;
    }

    /** Imported rows, newest batch first, optionally for one country. */
    @Transactional(readOnly = true)
    public Map<String, Object> list(Integer countryId, int page, int size) {
        return list(countryId, page, size, ListQuery.DEFAULT);
    }

    /** Imported rows, sorted, filtered and searched the way the caller asked. */
    @Transactional(readOnly = true)
    public Map<String, Object> list(Integer countryId, int page, int size, ListQuery query) {
        ListQuery q = query == null ? ListQuery.DEFAULT : query;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (countryId != null) {
            where.append(" AND COUNTRY_ID = ?");
            params.add(countryId);
        }
        appendFilters(where, params, q);
        appendSearch(where, params, q);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + where, Integer.class, params.toArray());
        if (total == null) {
            total = 0;
        }

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(page * size);
        pageParams.add(size);
        List<Map<String, Object>> content = jdbcTemplate.queryForList(
                "SELECT ID, STUDENT_NAME, ENTITY, COUNTRY, SCHOLARSHIP_YEAR, DEGREE, SPECIALIZATION, DECISION_NO, "
                        + "STUDY_DURATION, CURRENT_LEVEL, FIRST_DISBURSEMENT, AMOUNT, STUDENT_SIGNATURE, NOTES, "
                        + "FINANCIAL_NOTES, HDR_QUARTER, HDR_QUARTER_NO, HDR_YEAR, HDR_YEAR_NO, COUNTRY_ID, "
                        + "SOURCE_FILE, SOURCE_ROW, IMPORTED_AT, IMPORTED_BY FROM " + TABLE + where
                        + orderBy(q) + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                pageParams.toArray());

        for (Map<String, Object> row : content) {
            if (row.get("IMPORTED_AT") instanceof java.sql.Timestamp ts) {
                row.put("IMPORTED_AT", ts.toLocalDateTime().toString());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("totalElements", total);
        result.put("totalPages", size > 0 ? (int) Math.ceil((double) total / size) : 0);
        result.put("number", page);
        result.put("size", size);
        return result;
    }

    /**
     * The values behind each checkbox filter, for the country in view.
     *
     * <p>Scoped to the country on purpose: offering every degree in the table
     * when the user is looking at one country gives them a list where most
     * choices return nothing.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> filterOptions(Integer countryId) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        // Only the columns the table actually offers a dropdown on: each one
        // costs a DISTINCT over the table, and an option list nothing can
        // select from is a scan for nothing.
        for (String key : List.of("COUNTRY", "DEGREE", "HDR_QUARTER", "HDR_YEAR")) {
            options.put(key, distinctValues(FILTERABLE.get(key), countryId));
        }
        return options;
    }

    private List<String> distinctValues(String column, Integer countryId) {
        String sql = "SELECT DISTINCT TOP (" + MAX_FILTER_OPTIONS + ") " + column + " AS V FROM " + TABLE
                + " WHERE " + column + " IS NOT NULL AND LTRIM(RTRIM(" + column + ")) <> \'\'"
                + (countryId == null ? "" : " AND COUNTRY_ID = ?")
                + " ORDER BY V";
        List<Map<String, Object>> rows = countryId == null
                ? jdbcTemplate.queryForList(sql)
                : jdbcTemplate.queryForList(sql, countryId);
        List<String> values = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            values.add(String.valueOf(row.get("V")));
        }
        return values;
    }

    /**
     * The ORDER BY.
     *
     * <p>ID is always the last term. Without it a page boundary that lands in
     * the middle of a run of equal values — and a quarterly statement is full
     * of those — is free to shuffle between two requests, so a row can appear
     * on both page 2 and page 3, or on neither.
     */
    private String orderBy(ListQuery q) {
        String column = q.sortBy() == null ? null
                : SORTABLE.get(q.sortBy().trim().toUpperCase(Locale.ROOT));
        if (column == null) {
            return " ORDER BY IMPORTED_AT DESC, ID";
        }
        String dir = q.sortDir() == null ? "" : q.sortDir().trim().toLowerCase(Locale.ROOT);
        boolean descending = dir.startsWith("desc");
        return " ORDER BY " + column + (descending ? " DESC" : " ASC") + ", ID";
    }

    private void appendFilters(StringBuilder where, List<Object> params, ListQuery q) {
        for (Map.Entry<String, List<String>> entry : q.filters().entrySet()) {
            String column = entry.getKey() == null ? null
                    : FILTERABLE.get(entry.getKey().trim().toUpperCase(Locale.ROOT));
            if (column == null || entry.getValue() == null) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (String value : entry.getValue()) {
                if (value != null && !value.isBlank() && values.size() < MAX_FILTER_VALUES) {
                    values.add(value);
                }
            }
            if (values.isEmpty()) {
                continue;
            }
            where.append(" AND ").append(column).append(" IN (");
            for (int i = 0; i < values.size(); i++) {
                where.append(i == 0 ? "?" : ",?");
            }
            where.append(")");
            params.addAll(values);
        }
    }

    /**
     * The search box.
     *
     * <p>Words are ANDed and each one may land in any searched column, so
     * "احمد طب" finds the medical students called Ahmed rather than nothing at
     * all. A name in these sheets is four or five words and nobody types all of
     * them in order.
     *
     * <p>Note for whoever reads this next: matching is only as forgiving as the
     * column collation. The sheets spell the same name أحمد, احمد and آحمد, and
     * whether those match each other here is decided by the database, not by
     * this method — the rows are stored exactly as the workbook had them.
     */
    private void appendSearch(StringBuilder where, List<Object> params, ListQuery q) {
        for (String term : searchTerms(q.search())) {
            where.append(" AND (");
            for (int i = 0; i < SEARCH_COLUMNS.size(); i++) {
                where.append(i == 0 ? "" : " OR ")
                        .append(SEARCH_COLUMNS.get(i)).append(" LIKE ? ESCAPE \'\\\'");
                params.add("%" + escapeLike(term) + "%");
            }
            where.append(")");
        }
    }

    private static List<String> searchTerms(String search) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String part : search.trim().split("\\s+")) {
            if (!part.isEmpty() && terms.size() < MAX_SEARCH_TERMS) {
                terms.add(part);
            }
        }
        return terms;
    }

    /**
     * Wildcards a user typed are text, not syntax.
     *
     * <p>Unescaped, a lone "%" matches every row and "[a-z]" is a character
     * class; both turn a search into a table scan that returns the wrong thing.
     */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
    }
}
