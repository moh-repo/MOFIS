package com.dilaw.mohe.report;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintFrame;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.util.List;
import java.util.Map;

/**
 * Fills a report template and returns everything it printed as plain text.
 *
 * <p>Asserting on bytes only proves a PDF was produced; asserting on the text
 * proves the values actually reached the page, which is what catches a column
 * bound to the wrong field or a parameter that was never supplied.
 */
final class ReportRenderSupport {

    private ReportRenderSupport() {
    }

    /** Uses the default context; callers holding a font-registered context should pass it. */
    static String renderToText(String template, Map<String, Object> params, List<?> rows) throws Exception {
        return renderToText(null, template, params, rows);
    }

    static String renderToText(JasperReportsContext ctx, String template,
                               Map<String, Object> params, List<?> rows) throws Exception {
        JasperReport compiled;
        String resource = "/reports/" + template + ".jrxml";
        try (var is = ReportRenderSupport.class.getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalArgumentException("template not on the classpath: " + resource);
            }
            compiled = ctx == null
                    ? JasperCompileManager.compileReport(is)
                    : JasperCompileManager.getInstance(ctx).compile(is);
        }

        JasperPrint print = ctx == null
                ? JasperFillManager.fillReport(compiled, params, new JRBeanCollectionDataSource(rows))
                : JasperFillManager.getInstance(ctx).fillReport(compiled, params, new JRBeanCollectionDataSource(rows));

        StringBuilder sb = new StringBuilder();
        for (var page : print.getPages()) {
            collect(page.getElements(), sb);
        }
        return sb.toString();
    }

    /**
     * Printed text mapped to its x position, for asserting on layout rather than
     * content — column order, mirrored bands, elements that overlap.
     *
     * <p>Where the same text is printed more than once the first occurrence wins,
     * which keeps a repeating column header anchored to its column.
     */
    static Map<String, Integer> textPositions(String template, Map<String, Object> params,
                                              List<?> rows) throws Exception {
        JasperReport compiled;
        try (var is = ReportRenderSupport.class.getResourceAsStream("/reports/" + template + ".jrxml")) {
            compiled = JasperCompileManager.compileReport(is);
        }
        JasperPrint print = JasperFillManager.fillReport(compiled, params, new JRBeanCollectionDataSource(rows));

        Map<String, Integer> positions = new java.util.LinkedHashMap<>();
        for (var page : print.getPages()) {
            collectPositions(page.getElements(), positions);
        }
        return positions;
    }

    private static void collectPositions(List<JRPrintElement> elements, Map<String, Integer> out) {
        for (JRPrintElement el : elements) {
            if (el instanceof JRPrintText t) {
                String text = t.getFullText();
                if (text != null && !text.isBlank()) {
                    out.putIfAbsent(text.trim(), t.getX());
                }
            } else if (el instanceof JRPrintFrame f) {
                collectPositions(f.getElements(), out);
            }
        }
    }

    private static void collect(List<JRPrintElement> elements, StringBuilder sb) {
        for (JRPrintElement el : elements) {
            if (el instanceof JRPrintText t) {
                sb.append(t.getFullText()).append('\n');
            } else if (el instanceof JRPrintFrame f) {
                collect(f.getElements(), sb);
            }
        }
    }
}
