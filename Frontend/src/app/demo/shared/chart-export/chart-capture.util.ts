/**
 * Captures on-screen chart cards (Chart.js / ECharts `<canvas>` elements) into standalone
 * raster images that mirror what the user sees: card heading, description and the summary
 * figures printed under the chart.
 *
 * Text is drawn with the Canvas 2D API, so Arabic shaping and bidi come from the browser
 * and no font has to be embedded downstream.
 */

export interface ChartCardStat {
    label: string;
    value: string;
}

export interface ChartCardCapture {
    title: string;
    subtitle: string;
    stats: ChartCardStat[];
    canvas: HTMLCanvasElement;
    /**
     * Layout size for `canvas` in CSS pixels. Needed for offscreen canvases (e.g. an
     * html2canvas snapshot), whose `clientWidth` is 0 and whose pixel size is scaled up.
     */
    displayWidth?: number;
    displayHeight?: number;
}

export interface ChartRenderOptions {
    /** Banner text repeated at the top of every captured card (usually the page title). */
    pageTitle?: string;
    rtl?: boolean;
    /** Raster multiplier; 2 keeps text crisp when the PDF is zoomed. */
    scale?: number;
    locale?: string;
}

const CARD_SELECTOR = '.chart-card, .surface-card, .card, .stat-panel';
const HEADING_SELECTOR = 'h1, h2, h3, h4, h5, h6';
const STATS_BLOCK_SELECTOR = '[class*="border-top"], .chart-card__footer, .chart-stats';

/** Smallest content box in the export, so a compact card still fills a readable page. */
const MIN_CONTENT_WIDTH = 480;
const MIN_CONTENT_HEIGHT = 240;

const FONT_STACK = '"Segoe UI", Tahoma, Arial, "Helvetica Neue", sans-serif';
const COLOR_TEXT = '#1f2937';
const COLOR_MUTED = '#6b7280';
const COLOR_BORDER = '#e5e7eb';
const COLOR_ACCENT = '#6b2737';

function text(node: Element | null | undefined): string {
    return (node?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

function isVisible(el: HTMLElement): boolean {
    if (!el.isConnected) {
        return false;
    }
    const rect = el.getBoundingClientRect();
    return rect.width > 1 && rect.height > 1;
}

function readStats(cardRoot: Element): ChartCardStat[] {
    const block = cardRoot.querySelector(STATS_BLOCK_SELECTOR);
    if (!block) {
        return [];
    }
    const cells = Array.from(block.querySelectorAll('.text-center, .stat-item, .stat-card'));
    const stats: ChartCardStat[] = [];
    for (const cell of cells) {
        const label = text(cell.querySelector('span, .stat-label, [class*="__label"]'));
        const valueNode = cell.querySelector('div:last-child, .stat-value, [class*="__value"]');
        const value = text(valueNode);
        if (label && value && label !== value) {
            stats.push({ label, value });
        }
    }
    return stats;
}

/** Pair a chart canvas with the heading and summary figures of the card that owns it. */
export function captureChartCanvas(canvas: HTMLCanvasElement): ChartCardCapture {
    const cardRoot = canvas.closest(CARD_SELECTOR) ?? canvas.parentElement;
    const heading = cardRoot?.querySelector(HEADING_SELECTOR) ?? null;
    const subtitleNode = heading?.parentElement?.querySelector('p') ?? null;

    return {
        title: text(heading),
        subtitle: text(subtitleNode),
        stats: cardRoot ? readStats(cardRoot) : [],
        canvas,
    };
}

/**
 * Find the DOM cards to rasterise inside `container`, in document order.
 * Nested matches are dropped so a card is never exported twice.
 */
export function collectDomCards(container: HTMLElement, selector: string): HTMLElement[] {
    const matches = Array.from(container.querySelectorAll<HTMLElement>(selector)).filter(isVisible);
    return matches.filter((el) => !matches.some((other) => other !== el && other.contains(el)));
}

/** Read the heading of a DOM card, for filenames and log messages. */
export function readCardTitle(card: Element): string {
    return text(card.querySelector(HEADING_SELECTOR));
}

/** Find every rendered chart canvas inside `container`, in document order. */
export function collectChartCards(container: HTMLElement): ChartCardCapture[] {
    return Array.from(container.querySelectorAll<HTMLCanvasElement>('canvas'))
        .filter(isVisible)
        .map(captureChartCanvas);
}

function drawLine(ctx: CanvasRenderingContext2D, x1: number, y: number, x2: number): void {
    ctx.strokeStyle = COLOR_BORDER;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(x1, y + 0.5);
    ctx.lineTo(x2, y + 0.5);
    ctx.stroke();
}

/** Draw `value` clipped to `maxWidth`, appending an ellipsis when it overflows. */
function drawClamped(ctx: CanvasRenderingContext2D, value: string, x: number, y: number, maxWidth: number): void {
    let candidate = value;
    if (ctx.measureText(candidate).width > maxWidth) {
        while (candidate.length > 1 && ctx.measureText(`${candidate}…`).width > maxWidth) {
            candidate = candidate.slice(0, -1);
        }
        candidate = `${candidate}…`;
    }
    ctx.fillText(candidate, x, y);
}

/**
 * Compose one card capture onto a fresh white canvas laid out like the on-screen card.
 */
export function renderChartCard(capture: ChartCardCapture, options: ChartRenderOptions = {}): HTMLCanvasElement {
    const scale = options.scale ?? 2;
    const rtl = options.rtl ?? false;

    const sourceWidth = capture.displayWidth || capture.canvas.clientWidth || capture.canvas.width;
    const sourceHeight = capture.displayHeight || capture.canvas.clientHeight || capture.canvas.height;
    // Enlarge small cards uniformly — clamping each axis on its own would distort the chart.
    const growth = Math.max(1, MIN_CONTENT_WIDTH / sourceWidth, MIN_CONTENT_HEIGHT / sourceHeight);
    const chartWidth = Math.round(sourceWidth * growth);
    const chartHeight = Math.round(sourceHeight * growth);

    const padding = 28;
    const bannerHeight = options.pageTitle ? 52 : 0;
    const titleHeight = capture.title ? 30 : 0;
    const subtitleHeight = capture.subtitle ? 22 : 0;
    const statsHeight = capture.stats.length ? 78 : 0;

    const width = chartWidth + padding * 2;
    const height = padding * 2 + bannerHeight + titleHeight + subtitleHeight + chartHeight + statsHeight + 16;

    const out = document.createElement('canvas');
    out.width = Math.round(width * scale);
    out.height = Math.round(height * scale);
    const ctx = out.getContext('2d');
    if (!ctx) {
        throw new Error('renderChartCard: 2D canvas context unavailable');
    }
    ctx.scale(scale, scale);

    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, width, height);

    const left = padding;
    const right = width - padding;
    const anchor = rtl ? right : left;
    ctx.textAlign = rtl ? 'right' : 'left';
    ctx.textBaseline = 'alphabetic';
    ctx.direction = rtl ? 'rtl' : 'ltr';

    let y = padding;

    if (options.pageTitle) {
        ctx.fillStyle = COLOR_ACCENT;
        ctx.font = `600 17px ${FONT_STACK}`;
        drawClamped(ctx, options.pageTitle, anchor, y + 16, right - left - 160);

        const stamp = new Date().toLocaleString(options.locale || undefined);
        ctx.fillStyle = COLOR_MUTED;
        ctx.font = `12px ${FONT_STACK}`;
        ctx.textAlign = rtl ? 'left' : 'right';
        ctx.fillText(stamp, rtl ? left : right, y + 16);
        ctx.textAlign = rtl ? 'right' : 'left';

        drawLine(ctx, left, y + 30, right);
        y += bannerHeight;
    }

    if (capture.title) {
        ctx.fillStyle = COLOR_TEXT;
        ctx.font = `600 20px ${FONT_STACK}`;
        drawClamped(ctx, capture.title, anchor, y + 20, right - left);
        y += titleHeight;
    }

    if (capture.subtitle) {
        ctx.fillStyle = COLOR_MUTED;
        ctx.font = `13px ${FONT_STACK}`;
        drawClamped(ctx, capture.subtitle, anchor, y + 14, right - left);
        y += subtitleHeight;
    }

    y += 8;
    // White backdrop first: chart canvases are transparent and JPEG has no alpha channel.
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(left, y, chartWidth, chartHeight);
    ctx.drawImage(capture.canvas, left, y, chartWidth, chartHeight);
    y += chartHeight + 8;

    if (capture.stats.length) {
        drawLine(ctx, left, y, right);
        const cellWidth = (right - left) / capture.stats.length;
        capture.stats.forEach((stat, index) => {
            const cellStart = rtl ? right - cellWidth * (index + 1) : left + cellWidth * index;
            const center = cellStart + cellWidth / 2;
            ctx.textAlign = 'center';
            ctx.fillStyle = COLOR_MUTED;
            ctx.font = `12px ${FONT_STACK}`;
            drawClamped(ctx, stat.label, center, y + 26, cellWidth - 12);
            ctx.fillStyle = COLOR_TEXT;
            ctx.font = `600 18px ${FONT_STACK}`;
            drawClamped(ctx, stat.value, center, y + 52, cellWidth - 12);
        });
    }

    return out;
}

export function canvasToJpegDataUrl(canvas: HTMLCanvasElement, quality = 0.92): string {
    return canvas.toDataURL('image/jpeg', quality);
}

export function canvasToPngDataUrl(canvas: HTMLCanvasElement): string {
    return canvas.toDataURL('image/png');
}
