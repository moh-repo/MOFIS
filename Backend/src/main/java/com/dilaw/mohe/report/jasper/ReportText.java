package com.dilaw.mohe.report.jasper;

/**
 * Helpers called from JRXML expressions.
 *
 * <p>Fields declared {@code markup="styled"} are parsed as XML by JasperReports,
 * so a stored value containing {@code &}, {@code <} or {@code >} — for example
 * the real major name "BSc Electronics &amp; Communications" — makes the parse
 * fail. Jasper logs "Error parsing styled text" and the field does not render as
 * intended, which looks like missing data rather than a formatting fault. Data
 * interpolated into a styled field must therefore be escaped, while the
 * surrounding {@code <b>} tags the template itself supplies must not be.
 */
public final class ReportText {

    private ReportText() {
    }

    /**
     * Escapes the characters that are significant to the styled-text parser.
     *
     * <p>Returns {@code null} for a {@code null} input rather than an empty
     * string: template expressions null-check the value to choose a placeholder
     * ({@code $F{x} != null ? ... : "-"}), and collapsing null to "" would
     * silently take the non-null branch.
     */
    public static String esc(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        if (s.indexOf('&') < 0 && s.indexOf('<') < 0 && s.indexOf('>') < 0) {
            return s;
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
