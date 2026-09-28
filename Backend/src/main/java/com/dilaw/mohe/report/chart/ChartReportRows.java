package com.dilaw.mohe.report.chart;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Turns a posted visualisation page into the fixed-width rows Jasper reads.
 *
 * <p>Separate from the controller because this is the part with decisions in it —
 * what a chart with no series becomes, how wide a row is allowed to be, what counts
 * as an acceptable image — and those decisions are worth testing without standing up
 * an application context to reach them.
 */
public final class ChartReportRows {

    private ChartReportRows() {
    }

    /**
     * @param charts the cards as the browser rendered them
     * @return one row per data point, or a single image-only row for a card that has
     *         no series behind it
     */
    public static List<ChartReportRow> flatten(List<ChartReportRequest.Chart> charts) {
        List<ChartReportRow> rows = new ArrayList<>();
        int index = 0;

        for (ChartReportRequest.Chart chart : charts) {
            byte[] image = decodeImage(chart.imageBase64());
            String[] headers = toArray(chart.columns());
            List<List<String>> data = chart.rows() == null ? List.of() : chart.rows();

            if (data.size() > ChartReportRequest.MAX_ROWS_PER_CHART) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "A chart may carry at most " + ChartReportRequest.MAX_ROWS_PER_CHART + " rows.");
            }

            if (data.isEmpty()) {
                // The map and the network graph are a picture and nothing else. Dropping
                // them because they have no table would lose the only thing they show.
                rows.add(new ChartReportRow(index, chart.title(), chart.subtitle(), image, headers, new String[0]));
            } else {
                for (List<String> row : data) {
                    rows.add(new ChartReportRow(index, chart.title(), chart.subtitle(), image, headers, toArray(row)));
                }
            }
            index++;
        }
        return rows;
    }

    /**
     * Reads the card image, tolerating the {@code data:image/png;base64,} prefix a
     * canvas produces, and refusing anything that is not actually a PNG.
     *
     * <p>The magic-number check is the point. This is the only report input in the
     * system that comes from the client rather than from the database, and Jasper
     * will attempt to decode whatever it is handed.
     */
    public static byte[] decodeImage(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        String payload = base64;
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:") && comma > 0) {
            payload = payload.substring(comma + 1);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chart image is not valid base64.");
        }

        if (bytes.length > ChartReportRequest.MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Chart image is too large.");
        }
        if (!isPng(bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chart image must be a PNG.");
        }
        return bytes;
    }

    /** The eight-byte PNG signature. */
    private static boolean isPng(byte[] b) {
        return b.length > 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
    }

    /** Caps the width at what the template can draw; a wider row is truncated, not refused. */
    private static String[] toArray(List<String> values) {
        if (values == null) {
            return new String[0];
        }
        int width = Math.min(values.size(), ChartReportRequest.MAX_COLUMNS);
        String[] out = new String[width];
        for (int i = 0; i < width; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    /** A file name from the page heading, so a downloaded chart report is recognisable. */
    public static String baseName(String title) {
        if (title == null || title.isBlank()) {
            return "chart-report";
        }
        String slug = title.trim().toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "chart-report" : slug.substring(0, Math.min(slug.length(), 60));
    }
}
