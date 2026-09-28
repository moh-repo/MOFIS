package com.dilaw.mohe.report.jasper;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders a table whose columns are not known until the query has run.
 *
 * <p>Every other report in this application has a JRXML naming its own fields,
 * because every other report knows its own shape. A query builder result does
 * not, so {@code query-result.jrxml} declares the masthead, footer and
 * signatory bands — the official layout, inherited unchanged from the other
 * landscape reports — and leaves the field list, column header and detail band
 * as markers for this class to fill in.
 *
 * <p>The bands are produced as JRXML text rather than through the design API.
 * That looks like the cruder option and is the deliberate one: {@code
 * runDirection}, which is what makes Arabic lay out right-to-left, has no setter
 * on {@code JRDesignTextElement} in 6.21.3, so building the bands in Java would
 * have meant a report that renders Arabic left-to-right. Generating the same
 * XML the hand-written reports use keeps one way of describing a band.
 *
 * <p>What is substituted in is bounded: field names this class generates
 * ({@code c0}…{@code cN}), integers it computes, and column labels, which are
 * XML-escaped. Cell values never reach the XML at all — they are formatted into
 * the data source.
 */
@Service
public class DynamicTableReportService {

    /**
     * Columns a landscape page can carry and still be read.
     *
     * <p>802pt across fifteen columns is about 53pt each — roughly seven
     * characters at 6pt. Past that the report is not a smaller report but an
     * unreadable one, so the request is refused with a message saying so rather
     * than producing a page of truncated text.
     */
    public static final int MAX_PRINT_COLUMNS = 15;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JasperFontRegistrar fontRegistrar;

    /**
     * Compiled designs, keyed by the shape of the table rather than the data.
     *
     * <p>Compilation is the expensive part and depends only on the column labels
     * and the direction, so two runs of the same query — or the same query with
     * different filters — compile once. Cleared wholesale when it grows, which
     * is all the eviction a handful of report shapes needs.
     */
    private final Map<String, JasperReport> cache = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 64;

    private volatile String template;


    /** S-07: row cap and export line; optional so a hand-built service in a unit test needs nothing. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dilaw.mohe.report.ExportGuard exportGuard;

    public DynamicTableReportService(JasperFontRegistrar fontRegistrar) {
        this.fontRegistrar = fontRegistrar;
    }

    /**
     * Renders rows under the given column labels.
     *
     * @param labels column headings, in order; also the keys read from each row
     * @param rows   result rows, keyed by the same labels
     * @param params the standard report parameters, from {@code ReportParamBuilder}
     */
    public byte[] render(List<String> labels, List<Map<String, Object>> rows,
                         Map<String, Object> params) {
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("A report needs at least one column");
        }
        if (labels.size() > MAX_PRINT_COLUMNS) {
            throw new IllegalArgumentException(
                    "A printed report can show at most " + MAX_PRINT_COLUMNS + " columns; "
                            + labels.size() + " were selected. Narrow the column list, or export "
                            + "the result instead.");
        }

        if (exportGuard != null) exportGuard.enforce(rows == null ? 0 : rows.size());
        boolean rtl = Boolean.TRUE.equals(params.get("IS_RTL"));
        JasperReport report = cache.computeIfAbsent(
                signature(labels, rtl), key -> compile(labels, rtl));

        try {
            JasperReportsContext ctx = fontRegistrar.getReportsContext();
            JasperPrint print = JasperFillManager.getInstance(ctx)
                    .fillReport(report, new LinkedHashMap<>(params), new JRMapCollectionDataSource(toRows(labels, rows)));
            byte[] pdf = JasperExportManager.getInstance(ctx).exportReportToPdf(print);
            return exportGuard == null ? pdf : com.dilaw.mohe.report.ExportWatermark.pdf(pdf, exportGuard.watermark());
        } catch (JRException e) {
            // Was an IllegalStateException, which the global handler answers with 409
            // CONFLICT and the raw English sentence — a fill failure is neither (P7).
            throw new com.dilaw.mohe.report.ReportException(
                    com.dilaw.mohe.report.ReportException.Code.REPORT_FILL_FAILED,
                    "Failed to render the query result report", e);
        }
    }

    // ------------------------------------------------------------------ design

    private JasperReport compile(List<String> labels, boolean rtl) {
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        int[] widths = widths(labels.size());
        int[] xs = positions(widths, rtl);
        int fontSize = fontSize(labels.size());
        String direction = rtl ? "RTL" : "LTR";

        String xml = template()
                .replace("<!-- @FIELDS@ -->", fields(labels.size()))
                .replace("<!-- @COLUMN_HEADER@ -->", columnHeader(labels, widths, xs, fontSize, direction))
                .replace("<!-- @DETAIL@ -->", detail(labels.size(), widths, xs, fontSize, direction));

        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            JasperReportsContext ctx = fontRegistrar.getReportsContext();
            JasperDesign design = JRXmlLoader.load(is);
            return JasperCompileManager.getInstance(ctx).compile(design);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compile the query result report", e);
        }
    }

    private String template() {
        String loaded = template;
        if (loaded == null) {
            try (InputStream is = DynamicTableReportService.class
                    .getResourceAsStream("/reports/query-result.jrxml")) {
                if (is == null) {
                    throw new IllegalStateException("query-result.jrxml is not on the classpath");
                }
                loaded = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                template = loaded;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to read query-result.jrxml", e);
            }
        }
        return loaded;
    }

    private static String fields(int count) {
        StringBuilder xml = new StringBuilder();
        for (int i = 0; i < count; i++) {
            // Values are formatted before they reach the data source, so every
            // column is a String and the cell expression stays trivial.
            xml.append("  <field name=\"c").append(i).append("\" class=\"java.lang.String\"/>\n");
        }
        return xml.toString();
    }

    private static String columnHeader(List<String> labels, int[] widths, int[] xs,
                                       int fontSize, String direction) {
        int total = total(widths);
        StringBuilder xml = new StringBuilder("<columnHeader>\n    <band height=\"24\" splitType=\"Stretch\">\n");
        xml.append("      <rectangle><reportElement x=\"0\" y=\"0\" width=\"").append(total)
                .append("\" height=\"24\" backcolor=\"#722030\" forecolor=\"#722030\"/>")
                .append("<graphicElement><pen lineWidth=\"0.0\"/></graphicElement></rectangle>\n");
        xml.append("      <line><reportElement x=\"0\" y=\"23\" width=\"").append(total)
                .append("\" height=\"1\" forecolor=\"#c4922a\"/></line>\n");

        for (int i = 0; i < labels.size(); i++) {
            xml.append("      <staticText>\n")
                    .append("        <reportElement x=\"").append(xs[i]).append("\" y=\"0\" width=\"")
                    .append(widths[i]).append("\" height=\"24\" forecolor=\"#FFFFFF\"/>\n")
                    .append("        <textElement textAlignment=\"Center\" verticalAlignment=\"Middle\" runDirection=\"")
                    .append(direction).append("\"><font fontName=\"Cairo\" size=\"").append(fontSize)
                    .append("\" isBold=\"true\"/></textElement>\n")
                    .append("        <text><![CDATA[").append(cdata(labels.get(i))).append("]]></text>\n")
                    .append("      </staticText>\n");
            if (i > 0) {
                xml.append("      <line><reportElement x=\"").append(xs[i])
                        .append("\" y=\"0\" width=\"1\" height=\"24\" forecolor=\"#5a1a2a\"/></line>\n");
            }
        }
        return xml.append("    </band>\n  </columnHeader>").toString();
    }

    private static String detail(int count, int[] widths, int[] xs, int fontSize, String direction) {
        int total = total(widths);
        StringBuilder xml = new StringBuilder("<detail>\n    <band height=\"18\" splitType=\"Stretch\">\n");
        xml.append("      <rectangle><reportElement x=\"0\" y=\"0\" width=\"").append(total)
                .append("\" height=\"18\" backcolor=\"#fdf4f6\" forecolor=\"#fdf4f6\">")
                .append("<printWhenExpression><![CDATA[$V{REPORT_COUNT} % 2 == 0]]></printWhenExpression>")
                .append("</reportElement><graphicElement><pen lineWidth=\"0.0\"/></graphicElement></rectangle>\n");
        xml.append("      <line><reportElement x=\"0\" y=\"17\" width=\"").append(total)
                .append("\" height=\"1\" forecolor=\"#e8c0c8\"/></line>\n");

        for (int i = 0; i < count; i++) {
            xml.append("      <textField textAdjust=\"StretchHeight\">\n")
                    .append("        <reportElement x=\"").append(xs[i]).append("\" y=\"0\" width=\"")
                    .append(widths[i]).append("\" height=\"18\" isPrintWhenDetailOverflows=\"true\"/>\n")
                    .append("        <textElement textAlignment=\"Center\" verticalAlignment=\"Middle\" runDirection=\"")
                    .append(direction).append("\"><font fontName=\"Cairo\" size=\"").append(fontSize)
                    .append("\"/></textElement>\n")
                    .append("        <textFieldExpression><![CDATA[$F{c").append(i)
                    .append("}]]></textFieldExpression>\n")
                    .append("      </textField>\n");
            if (i > 0) {
                xml.append("      <line><reportElement x=\"").append(xs[i])
                        .append("\" y=\"0\" width=\"1\" height=\"18\" forecolor=\"#e8c0c8\"/></line>\n");
            }
        }
        return xml.append("    </band>\n  </detail>").toString();
    }

    // ------------------------------------------------------------------ layout

    /** Even split of the printable width, with the remainder given to the first columns. */
    public static int[] widths(int count) {
        int available = 802;
        int[] widths = new int[count];
        int base = available / count;
        int remainder = available % count;
        for (int i = 0; i < count; i++) {
            widths[i] = base + (i < remainder ? 1 : 0);
        }
        return widths;
    }

    /**
     * Left edges, laid out in reading order.
     *
     * <p>Right-to-left is a mirror rather than a reversal of the list: the first
     * column sits at the right edge, so the table reads in the same order as the
     * Arabic around it. Computing this here is the whole reason the bands are
     * generated — a JRXML cannot derive an element's x from a parameter, which is
     * why the fixed reports resort to palindromic column widths.
     */
    public static int[] positions(int[] widths, boolean rtl) {
        int total = total(widths);
        int[] xs = new int[widths.length];
        int cursor = 0;
        for (int i = 0; i < widths.length; i++) {
            xs[i] = rtl ? total - cursor - widths[i] : cursor;
            cursor += widths[i];
        }
        return xs;
    }

    private static int total(int[] widths) {
        int total = 0;
        for (int width : widths) {
            total += width;
        }
        return total;
    }

    /** Shrinks the type as columns are added, so a wide table stays on one page. */
    public static int fontSize(int columns) {
        if (columns <= 6) return 8;
        if (columns <= 9) return 7;
        return 6;
    }

    /**
     * Identifies the table shape: direction, column count, and every label
     * length-prefixed so no choice of delimiter can make two different
     * column lists collide on one compiled design.
     */
    private String signature(List<String> labels, boolean rtl) {
        StringBuilder key = new StringBuilder(rtl ? "rtl" : "ltr").append(labels.size());
        for (String label : labels) {
            key.append(':').append(label.length()).append('.').append(label);
        }
        return key.toString();
    }

    // -------------------------------------------------------------------- data

    /** Re-keys each row to the generated field names, formatting as it goes. */
    private static List<Map<String, ?>> toRows(List<String> labels, List<Map<String, Object>> rows) {
        List<Map<String, ?>> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (int i = 0; i < labels.size(); i++) {
                mapped.put("c" + i, format(row.get(labels.get(i))));
            }
            out.add(mapped);
        }
        return out;
    }

    /**
     * Renders a value the way a printed report should show it.
     *
     * <p>Done here rather than in the template because {@code String.valueOf} on
     * a {@code Timestamp} prints {@code 2024-02-01 00:00:00.0}, which is not a
     * date anyone wants on a ministry report.
     */
    public static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Timestamp timestamp) {
            return format(timestamp.toLocalDateTime());
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().format(DATE);
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalTime().toSecondOfDay() == 0
                    ? dateTime.toLocalDate().format(DATE)
                    : dateTime.format(DATE_TIME);
        }
        if (value instanceof LocalDate date) {
            return date.format(DATE);
        }
        if (value instanceof BigDecimal number) {
            // Trailing zeros carry no information on a report, but a scale of
            // zero must not become scientific notation.
            BigDecimal trimmed = number.stripTrailingZeros();
            return trimmed.scale() < 0 ? trimmed.setScale(0).toPlainString() : trimmed.toPlainString();
        }
        if (value instanceof byte[]) {
            return "(binary)";
        }
        return String.valueOf(value);
    }

    /** Keeps a label from ending the CDATA section it is placed in. */
    private static String cdata(String text) {
        return text == null ? "" : text.replace("]]>", "]]&gt;");
    }
}
