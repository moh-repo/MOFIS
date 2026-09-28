import { Chart } from 'chart.js';

/** A chart's numbers, shaped as the table that goes under it in the report. */
export interface ChartSeriesTable {
    /** Header texts; the first names the category column. */
    columns: string[];
    /** One entry per category, each as wide as `columns`. */
    rows: string[][];
}

/** The most columns `chart-report.jrxml` can draw: a label plus six series. */
const MAX_COLUMNS = 7;

/**
 * Reads the numbers behind a rendered chart.
 *
 * <p>A printed picture of a chart answers no questions — a reader who wants to know
 * what the third bar actually was, or to quote a figure in a letter, needs the
 * numbers. They are already on the page: Chart.js keeps the data it drew from on the
 * instance bound to the canvas, so the table under each card in the report is read
 * back out of the chart itself rather than re-fetched or re-derived. That also means
 * the table can never disagree with the picture above it, which a second request to
 * the server could not guarantee — the reader may have changed a filter since.
 *
 * <p>Returns `null` for a canvas that is not a Chart.js chart. The geographic map and
 * the network graph are exactly that: Leaflet and vis-network draw their own canvases
 * and have no series behind them, so those cards go into the report as a picture
 * alone, which is what they are on screen too.
 */
export function readChartSeries(canvas: HTMLCanvasElement, categoryLabel: string): ChartSeriesTable | null {
    const chart = safeGetChart(canvas);
    if (!chart) {
        return null;
    }

    const labels = (chart.data?.labels ?? []) as unknown[];
    const datasets = (chart.data?.datasets ?? []) as Array<{ label?: string; data?: unknown[] }>;
    if (!labels.length || !datasets.length) {
        return null;
    }

    // One column per dataset, capped at what the template can draw. A chart with
    // more series than that is not a table anyone reads anyway.
    const used = datasets.slice(0, MAX_COLUMNS - 1);
    const columns = [categoryLabel, ...used.map((d, i) => d.label?.trim() || `${categoryLabel} ${i + 1}`)];

    const rows = labels.map((label, index) => [
        stringify(label),
        ...used.map((d) => formatValue(d.data?.[index]))
    ]);

    return { columns, rows };
}

/**
 * `Chart.getChart` throws on a canvas Chart.js has never seen in some builds and
 * returns undefined in others; the report must not fail either way.
 */
function safeGetChart(canvas: HTMLCanvasElement): Chart | undefined {
    try {
        return Chart.getChart(canvas);
    } catch {
        return undefined;
    }
}

/**
 * One cell.
 *
 * <p>Line and scatter datasets hold `{x, y}` points rather than bare numbers, and a
 * chart with a missing reading holds `null` — which must print as an empty cell, not
 * as "null" or as a zero it never measured.
 */
function formatValue(value: unknown): string {
    if (value === null || value === undefined) {
        return '';
    }
    if (typeof value === 'object') {
        const point = value as { y?: unknown; x?: unknown };
        return formatValue(point.y ?? point.x);
    }
    if (typeof value === 'number') {
        return Number.isFinite(value) ? formatNumber(value) : '';
    }
    return String(value);
}

/**
 * Grouped, and no more precise than the figure is.
 *
 * <p>Deliberately not the reader's locale: an Arabic locale renders Arabic-Indic
 * digits, and the rest of the ministry's reports print Western digits in their
 * numeric columns whichever language the page is in. The hierarchy export uses it
 * too, so its table reads like every other chart's.
 */
export function formatNumber(value: number): string {
    const decimals = Number.isInteger(value) ? 0 : 2;
    return value.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

function stringify(value: unknown): string {
    if (value === null || value === undefined) {
        return '';
    }
    // A grouped axis label is an array of lines ("2025", "Q1"); the table wants one cell.
    return Array.isArray(value) ? value.map(stringify).join(' ') : String(value);
}
