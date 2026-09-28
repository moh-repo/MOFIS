package com.dilaw.mohe.report.chart;

/**
 * One line of a chart report, flattened for Jasper.
 *
 * <p>Jasper reads a bean collection through fixed field names, so a report whose
 * shape varies — three columns on one page, five on the next — has to be flattened
 * to a fixed width. Every row therefore carries the whole chart's identity
 * ({@code chartIndex}, title, image) alongside its own values, and the report groups
 * on {@code chartIndex}: the group header draws the chart and its column headings,
 * the detail band draws the rows.
 *
 * <p>Repeating the image on every row of a group looks wasteful and is not: Jasper
 * evaluates a group header against the first row of the group, so the bytes are read
 * once per chart. Carrying it on the row rather than as a report parameter is what
 * lets one run of the report hold several charts, which is what these pages need —
 * the alternative was one PDF per card and no page numbering across them.
 *
 * <p>The header fields ({@code h0}-{@code h6}) are on the row for the same reason:
 * each chart names its own columns, so the headings cannot be report parameters.
 */
public class ChartReportRow {

    private final Integer chartIndex;
    private final String chartTitle;
    private final String chartSubtitle;
    private final byte[] chartImage;
    private final String[] headers;
    private final String[] values;

    public ChartReportRow(int chartIndex, String chartTitle, String chartSubtitle, byte[] chartImage,
                          String[] headers, String[] values) {
        this.chartIndex = chartIndex;
        this.chartTitle = chartTitle;
        this.chartSubtitle = chartSubtitle;
        this.chartImage = chartImage;
        this.headers = headers;
        this.values = values;
    }

    public Integer getChartIndex() { return chartIndex; }
    public String getChartTitle() { return chartTitle; }
    public String getChartSubtitle() { return chartSubtitle; }
    public byte[] getChartImage() { return chartImage; }

    /** True while this group still has rows to show; a chart with no data prints the image alone. */
    public Boolean getHasData() { return values.length > 0 && !isAllBlank(); }

    public String getH0() { return header(0); }
    public String getH1() { return header(1); }
    public String getH2() { return header(2); }
    public String getH3() { return header(3); }
    public String getH4() { return header(4); }
    public String getH5() { return header(5); }
    public String getH6() { return header(6); }

    public String getC0() { return value(0); }
    public String getC1() { return value(1); }
    public String getC2() { return value(2); }
    public String getC3() { return value(3); }
    public String getC4() { return value(4); }
    public String getC5() { return value(5); }
    public String getC6() { return value(6); }

    private String header(int i) { return i < headers.length ? nvl(headers[i]) : ""; }
    private String value(int i) { return i < values.length ? nvl(values[i]) : ""; }

    private boolean isAllBlank() {
        for (String v : values) {
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
