package com.dilaw.mohe.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The V077 and V079 seeds of LU_REPORT_LABEL, read from the migration scripts
 * themselves, for the render tests that run without Spring or a database. The
 * scripts are the source the database is loaded from, so a template test that
 * takes its labels from here cannot drift from what production prints — and
 * {@code ReportLabelsAreSeededTest} asserts the scripts and the database agree.
 *
 * <p>Parses the {@code (N'CODE', N'AR', N'EN', n),} rows of each MERGE; a name
 * may be a concatenation of {@code N'…'} pieces and {@code NCHAR(10)}.
 */
public final class SeededReportLabels {

    /**
     * In order, because a later script's row for the same code is the current
     * wording — V081 corrects LBL_AMOUNT_CUR, which V077 seeded with the currency
     * written into it.
     *
     * <p>Only the first {@code USING (VALUES} block of each script is read, so a
     * script may carry other MERGEs after its label rows — V108 seeds
     * LU_REPORT_DIMENSION in the same file.
     */
    static final List<String> SCRIPTS = List.of(
            "V077__report_labels_from_the_templates.sql",
            "V079__inline_template_labels_to_the_table.sql",
            "V081__payment_voucher_currency_and_amount_in_words.sql",
            "V108__report_label_and_dimension_for_usage_and_charts.sql");

    public record Label(String code, String ar, String en, int sortOrder) {
        String text(boolean rtl) {
            return rtl ? ar : en;
        }
    }

    private static final Pattern ROW = Pattern.compile(
            "^\\s*\\(\\s*N'((?:[^']|'')*)'\\s*,\\s*(.+?)\\s*,\\s*(.+?)\\s*,\\s*(\\d+)\\s*\\),?\\s*$");
    private static final Pattern PIECE = Pattern.compile("N'((?:[^']|'')*)'|NCHAR\\((\\d+)\\)");

    private static Map<String, Label> cache;

    private SeededReportLabels() {
    }

    /** Every row of the seeds, base and {@code CODE@REPORT} overrides alike, by code. */
    public static synchronized Map<String, Label> load() {
        if (cache == null) {
            Map<String, Label> all = new LinkedHashMap<>();
            for (String script : SCRIPTS) {
                parse(scriptPath(script)).forEach((code, label) -> {
                    if (all.putIfAbsent(code, label) != null) {
                        throw new IllegalStateException(code + " is seeded by more than one script");
                    }
                });
            }
            cache = all;
        }
        return cache;
    }

    /**
     * Puts what {@code ReportParamBuilder.addReportLabels} would put for the
     * report: every base COL_/LBL_ row in the language, then the report's own
     * overrides.
     */
    public static Map<String, Object> putAll(Map<String, Object> p, String reportCode, boolean rtl) {
        String suffix = "@" + reportCode.toUpperCase(Locale.ROOT);
        Map<String, String> overrides = new LinkedHashMap<>();
        for (Label l : load().values()) {
            if (!(l.code().startsWith("COL_") || l.code().startsWith("LBL_"))) {
                continue;
            }
            int at = l.code().indexOf('@');
            if (at < 0) {
                p.put(l.code(), l.text(rtl));
            } else if (l.code().substring(at).equalsIgnoreCase(suffix)) {
                overrides.put(l.code().substring(0, at), l.text(rtl));
            }
        }
        p.putAll(overrides);
        return p;
    }

    /** Surefire runs with Backend/ as the working directory; the repo root is tolerated too. */
    static Path scriptPath(String script) {
        for (Path candidate : List.of(Path.of("docs", "scripts", script), Path.of("Backend", "docs", "scripts", script))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Cannot find " + script + " from " + Path.of("").toAbsolutePath());
    }

    static Map<String, Label> parse(Path script) {
        List<String> lines;
        try {
            lines = Files.readAllLines(script, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + script, e);
        }
        Map<String, Label> out = new LinkedHashMap<>();
        boolean inValues = false;
        for (String line : lines) {
            if (line.startsWith("USING (VALUES")) {
                inValues = true;
                continue;
            }
            if (!inValues) {
                continue;
            }
            if (line.startsWith(") AS src")) {
                break;
            }
            Matcher m = ROW.matcher(line);
            if (!m.matches()) {
                throw new IllegalStateException("Unparseable seed row in " + script.getFileName() + ": " + line);
            }
            String code = m.group(1).replace("''", "'");
            out.put(code, new Label(code, expression(m.group(2)), expression(m.group(3)), Integer.parseInt(m.group(4))));
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("No seed rows found in " + script);
        }
        return out;
    }

    private static String expression(String expr) {
        StringBuilder sb = new StringBuilder();
        Matcher m = PIECE.matcher(expr);
        int consumed = 0;
        while (m.find()) {
            String between = expr.substring(consumed, m.start()).trim();
            if (!between.isEmpty() && !between.equals("+")) {
                throw new IllegalStateException("Unexpected SQL in seed name: " + expr);
            }
            if (m.group(1) != null) {
                sb.append(m.group(1).replace("''", "'"));
            } else {
                sb.append((char) Integer.parseInt(m.group(2)));
            }
            consumed = m.end();
        }
        if (consumed == 0) {
            throw new IllegalStateException("Not a string expression: " + expr);
        }
        return sb.toString();
    }
}
