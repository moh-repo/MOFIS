package com.dilaw.mohe.report.chart;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One visualisation page, as the browser sees it, sent up to be made into a report.
 *
 * <p>The chart pages are the one place where the server cannot render the report on
 * its own. A Chart.js bar chart, a Leaflet map and a vis-network graph are all drawn
 * by the browser from data the server sent as numbers; there is no server-side
 * equivalent that would produce the same picture. So the picture comes back up —
 * {@link Chart#imageBase64()} is the rendered card as a PNG — and the numbers behind
 * it come with it, in {@link Chart#columns()} and {@link Chart#rows()}, read straight
 * off the chart's own datasets.
 *
 * <p>Sending both is what makes the result a report rather than a screenshot. The
 * image is what the reader recognises from the screen; the table underneath is what
 * they can check a figure in, search, or paste into a letter. A printed picture of a
 * chart answers no questions.
 *
 * <p>The limits are deliberately generous for real pages and firm against abuse: this
 * endpoint takes bytes from the client and renders them, so it is the one report
 * input that is not derived from the database.
 */
public record ChartReportRequest(

        /** Page heading, already in the reader's language. */
        @Size(max = 300) String title,

        /** {@code ar} or {@code en}; anything else is read as Arabic, as everywhere else. */
        @Size(max = 8) String lang,

        @NotEmpty @Size(max = ChartReportRequest.MAX_CHARTS) List<Chart> charts) {

    /** More cards than any page in the system shows, with room to spare. */
    public static final int MAX_CHARTS = 16;

    /** Roughly a 2x-scaled A4 card as PNG; larger means something is wrong. */
    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    /** Per chart. The export row cap in {@link com.dilaw.mohe.report.ExportGuard} still applies to the total. */
    public static final int MAX_ROWS_PER_CHART = 5000;

    /** Label column plus six series — wider than any chart on any of these pages. */
    public static final int MAX_COLUMNS = 7;

    public record Chart(
            @Size(max = 300) String title,
            @Size(max = 300) String subtitle,

            /** The rendered card, PNG, base64, with or without a {@code data:} prefix. */
            String imageBase64,

            /** Header texts, first one naming the label column. */
            @Size(max = ChartReportRequest.MAX_COLUMNS) List<String> columns,

            /** One entry per category; each is as wide as {@link #columns()}. */
            @Size(max = ChartReportRequest.MAX_ROWS_PER_CHART) List<List<String>> rows) {
    }
}
