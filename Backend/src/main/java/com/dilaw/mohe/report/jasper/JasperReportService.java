package com.dilaw.mohe.report.jasper;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class JasperReportService {

    static {
        JasperReportsContextConfigurer.apply(DefaultJasperReportsContext.getInstance());
    }

    private final JasperFontRegistrar fontRegistrar;
    private final Map<String, JasperReport> cache = new ConcurrentHashMap<>();

    /** S-07: row cap and export line; optional so a hand-built service in a unit test needs nothing. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dilaw.mohe.report.ExportGuard exportGuard;

    /**
     * G49: bounded concurrency and a per-render timeout. Optional for the same
     * reason as the guard above — a unit test that news up this service renders
     * inline, exactly as it did before.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dilaw.mohe.report.ReportRenderExecutor renderExecutor;

    /** W5-03: the fields this caller may not see. Optional for the same reason as the guard. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dilaw.mohe.security.field.ReportFieldMask fieldMask;

    public byte[] generatePdf(String templateName, Map<String, Object> params, Collection<?> data) {
        // Both guard calls stay on the request thread: they read the signed-in
        // user from RequestContextHolder, which does not exist on a pool thread.
        if (exportGuard != null) exportGuard.enforce(data == null ? 0 : data.size());
        String watermark = exportGuard == null ? null : exportGuard.watermark();
        java.util.Set<String> hidden = hiddenFields(data);

        byte[] pdf = onRenderThread(templateName, () -> {
            try {
                JasperReportsContext ctx = fontRegistrar.getReportsContext();
                JasperPrint print = fill(ctx, templateName, params, data, hidden);
                return JasperExportManager.getInstance(ctx).exportReportToPdf(print);
            } catch (JRException e) {
                throw new com.dilaw.mohe.report.ReportException(
                        com.dilaw.mohe.report.ReportException.Code.REPORT_FILL_FAILED,
                        "Failed to generate Jasper report: " + templateName, e);
            }
        });

        return watermark == null ? pdf : com.dilaw.mohe.report.ExportWatermark.pdf(pdf, watermark);
    }

    public byte[] generatePdf(String templateName, Map<String, Object> params) {
        return generatePdf(templateName, params, java.util.Collections.emptyList());
    }

    /**
     * Exports the same filled report as a spreadsheet, so the Excel a user downloads
     * and the PDF they preview are the one template rather than two drifting layouts.
     *
     * <p>The configuration below is what makes the output usable as data instead of a
     * picture of a page: the report is one continuous sheet rather than a sheet per
     * printed page, the blank space Jasper leaves between bands is dropped, and cell
     * types are detected so numbers and dates arrive as numbers and dates.
     */
    public byte[] generateXlsx(String templateName, Map<String, Object> params, Collection<?> data) {
        if (exportGuard != null) exportGuard.enforce(data == null ? 0 : data.size());
        String watermark = exportGuard == null ? null : exportGuard.watermark();
        java.util.Set<String> hidden = hiddenFields(data);

        byte[] xlsx = onRenderThread(templateName, () -> {
            try {
                JasperReportsContext ctx = fontRegistrar.getReportsContext();
                JasperPrint print = fill(ctx, templateName, params, data, hidden);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                JRXlsxExporter exporter = new JRXlsxExporter(ctx);
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

                SimpleXlsxReportConfiguration config = new SimpleXlsxReportConfiguration();
                config.setOnePagePerSheet(false);
                config.setRemoveEmptySpaceBetweenRows(true);
                config.setRemoveEmptySpaceBetweenColumns(true);
                config.setDetectCellType(true);
                config.setWhitePageBackground(false);
                config.setCollapseRowSpan(true);
                config.setIgnoreGraphics(true);
                exporter.setConfiguration(config);

                exporter.exportReport();
                return out.toByteArray();
            } catch (JRException e) {
                throw new com.dilaw.mohe.report.ReportException(
                        com.dilaw.mohe.report.ReportException.Code.REPORT_FILL_FAILED,
                        "Failed to generate Jasper spreadsheet: " + templateName, e);
            }
        });

        return watermark == null ? xlsx : com.dilaw.mohe.report.ExportWatermark.xlsx(xlsx, watermark);
    }

    /**
     * Runs a render under the executor's limits, or inline when there is none.
     *
     * <p>The executor is absent only outside Spring — a unit test that constructs
     * this service directly. In the application it is always present, so every
     * report the ministry can reach is bounded in both concurrency and time.
     */
    private byte[] onRenderThread(String templateName, java.util.concurrent.Callable<byte[]> work) {
        if (renderExecutor != null) {
            return renderExecutor.render(templateName, work);
        }
        try {
            return work.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render report: " + templateName, e);
        }
    }

    public byte[] generateXlsx(String templateName, Map<String, Object> params) {
        return generateXlsx(templateName, params, java.util.Collections.emptyList());
    }

    /**
     * The properties of these rows the signed-in caller may not see (W5-03).
     *
     * <p>Read here, on the request thread, and carried into the render: the
     * security context does not follow onto a {@code ReportRenderExecutor}
     * thread, and an empty context holds no permission codes, which would mask
     * every sensitive column of every report for everyone.
     */
    private java.util.Set<String> hiddenFields(Collection<?> data) {
        if (fieldMask == null || data == null || data.isEmpty()) return java.util.Set.of();
        Class<?> rowType = com.dilaw.mohe.security.field.ReportFieldMask.rowTypeOf(data);
        return rowType == null ? java.util.Set.of() : fieldMask.hiddenOn(rowType);
    }

    /** Shared fill step, so the two exports can never diverge on template or data. */
    private JasperPrint fill(JasperReportsContext ctx, String templateName, Map<String, Object> params,
                             Collection<?> data, java.util.Set<String> hidden) throws JRException {
        JasperReport compiled = cache.computeIfAbsent(cacheKey(templateName, params),
                key -> compile(templateName, key.endsWith(LTR_SUFFIX)));
        // An English report prints English data, not English headings over Arabic
        // values. This is the right place for it rather than ReportExports: several
        // controllers -- the individual student documents, the status report, the
        // custom filter report -- call generatePdf directly, and resolving here is
        // the only way none of them can be left behind.
        Collection<?> rows = com.dilaw.mohe.report.BilingualRows.forLanguage(data, params.get("LANG"));
        // The same argument applies to the mask, and to the same set of controllers.
        JRBeanCollectionDataSource ds = MaskedBeanDataSource.of(rows, hidden);
        return JasperFillManager.getInstance(ctx).fillReport(compiled, params, ds);
    }

    private static final String LTR_SUFFIX = "|ltr";

    /**
     * A prescribed right-to-left form has to be compiled twice — once as designed and
     * once mirrored — so the two live under different keys. Every other template
     * compiles once and ignores the direction entirely.
     */
    private static String cacheKey(String templateName, Map<String, Object> params) {
        return Boolean.FALSE.equals(params.get("IS_RTL")) ? templateName + LTR_SUFFIX : templateName;
    }

    private JasperReport compile(String name, boolean ltr) {
        String path = "/reports/" + name + ".jrxml";
        try (InputStream is = JasperReportService.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("JRXML template not found on classpath: " + path);
            }
            String xml = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            JasperDesign design = load(xml);

            if (ltr && MirroredDesign.isMirrorable(design)) {
                // Loaded a second time from the direction-flipped source: runDirection
                // is an XML attribute with no setter, so it cannot be changed on the
                // design that has already been read. Once per template per direction,
                // behind the cache.
                design = MirroredDesign.mirror(load(MirroredDesign.flipRunDirection(xml)));
            }
            return JasperCompileManager.getInstance(fontRegistrar.getReportsContext()).compile(design);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile JRXML template: " + path, e);
        }
    }

    private static JasperDesign load(String xml) throws JRException {
        return JRXmlLoader.load(new java.io.ByteArrayInputStream(
                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** Clears compiled template cache (e.g. after JRXML resource updates in dev). */
    public void clearCache() {
        cache.clear();
    }
}
