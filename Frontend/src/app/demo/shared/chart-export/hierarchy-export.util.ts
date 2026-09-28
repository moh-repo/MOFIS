/**
 * Exports a hierarchy chart from its data rather than from the page (R5-11).
 *
 * PrimeNG draws an organization chart as nested tables, one column per leaf. The
 * academic hierarchy has some two hundred leaves, so its chart is about 34,000 px wide
 * and 8,700 elements deep, inside a card as wide as the window. The export used to
 * rasterise that card with html2canvas, which clones the page and walks every element
 * of the card — the ones scrolled out of sight included — on the main thread. With the
 * real data that held the tab for minutes, and what came out at the end was only the
 * slice of the chart that happened to be scrolled into view, shrunk into the report's
 * 802 × 218 pt picture box.
 *
 * No picture of a tree that size is readable in that box, however it is made. So a
 * hierarchy goes into the report the way the other charts do — a picture with the
 * numbers under it — and each part does what it can:
 *
 * - the picture is an overview drawn straight onto a canvas: the root, and each further
 *   level for as long as the widest row still fits in eight boxes. When the root has
 *   more children than that, the smallest are folded into one "N more" box;
 * - the table is the whole tree, one row per node, indented by depth. Every figure on
 *   the screen is in the document, and Jasper paginates it like any other report.
 *
 * Both take milliseconds and neither reads the DOM, so how large the chart is on the
 * page no longer decides whether it can be exported.
 */

import { ChartSeriesTable, formatNumber } from './chart-series.util';

export interface HierarchyExportNode {
    label: string;
    value: number;
    children?: HierarchyExportNode[];
}

export interface HierarchyPicture {
    canvas: HTMLCanvasElement;
    /** Layout size in CSS pixels; the canvas holds `scale` times as many. */
    width: number;
    height: number;
}

export interface HierarchyPictureOptions {
    /** Text of the box that stands for the branches left out, given how many there are. */
    moreLabel: (count: number) => string;
    rtl?: boolean;
    /** Raster multiplier; 2 keeps text crisp when the PDF is zoomed. */
    scale?: number;
    /** Box colours, so each card keeps the tint it has on screen. */
    fill?: string;
    stroke?: string;
}

/** The widest row of boxes the overview draws. */
export const MAX_BOXES_PER_ROW = 8;

const BOX_WIDTH = 150;
const BOX_HEIGHT = 58;
const H_GAP = 18;
const V_GAP = 34;
const PADDING = 16;
/** The smallest picture, so a two-box tree is not blown up and blurred in the report. */
const MIN_WIDTH = 480;
const MIN_HEIGHT = 240;

const FONT_STACK = '"Segoe UI", Tahoma, Arial, "Helvetica Neue", sans-serif';
const COLOR_TEXT = '#1f2937';
const COLOR_MUTED = '#6b7280';
const COLOR_LINE = '#cbd5e1';

/**
 * Indentation per level in the table. No-break spaces, because Jasper keeps them, and
 * leading ones sit at the start edge in either direction — the right in the Arabic
 * document, the left in its mirrored English copy.
 */
const INDENT = ' '.repeat(4);

/** The whole tree as the table under the picture: one row per node, parents before children. */
export function hierarchyTable(root: HierarchyExportNode, labelHeading: string, valueHeading: string): ChartSeriesTable {
    const rows: string[][] = [];
    const visit = (node: HierarchyExportNode, depth: number): void => {
        rows.push([INDENT.repeat(depth) + (node.label ?? ''), formatNumber(node.value ?? 0)]);
        for (const child of node.children ?? []) {
            visit(child, depth + 1);
        }
    };
    visit(root, 0);
    return { columns: [labelHeading, valueHeading], rows };
}

interface Box {
    label: string;
    value: number;
    /** Stands for the root's children that did not fit. */
    folded: boolean;
    source?: HierarchyExportNode;
    children: Box[];
    depth: number;
    x: number;
}

function toBox(source: HierarchyExportNode, depth: number): Box {
    return { label: source.label ?? '', value: source.value ?? 0, folded: false, source, children: [], depth, x: 0 };
}

function byValue(nodes: HierarchyExportNode[] | undefined): HierarchyExportNode[] {
    return [...(nodes ?? [])].sort((a, b) => (b.value ?? 0) - (a.value ?? 0));
}

function countLeaves(box: Box): number {
    return box.children.length ? box.children.reduce((n, child) => n + countLeaves(child), 0) : 1;
}

/** Picks the boxes the overview shows; see the file comment for the rule. */
function overviewTree(root: HierarchyExportNode, moreLabel: (count: number) => string): { top: Box; depth: number } {
    const top = toBox(root, 0);
    const children = byValue(root.children);
    if (children.length > MAX_BOXES_PER_ROW) {
        const shown = children.slice(0, MAX_BOXES_PER_ROW - 1);
        const hidden = children.slice(MAX_BOXES_PER_ROW - 1);
        top.children = shown.map((child) => toBox(child, 1));
        top.children.push({
            label: moreLabel(hidden.length),
            value: hidden.reduce((sum, child) => sum + (child.value ?? 0), 0),
            folded: true,
            children: [],
            depth: 1,
            x: 0,
        });
    } else {
        top.children = children.map((child) => toBox(child, 1));
    }

    // Deeper levels come whole or not at all, and only while the widest row still fits.
    let depth = top.children.length ? 1 : 0;
    let frontier = top.children.filter((box) => !box.folded);
    while (frontier.length) {
        const next = frontier.map((box) => byValue(box.source?.children).map((child) => toBox(child, box.depth + 1)));
        const added = next.reduce((n, kids) => n + kids.length, 0);
        const parents = next.filter((kids) => kids.length).length;
        if (!added || countLeaves(top) - parents + added > MAX_BOXES_PER_ROW) {
            break;
        }
        frontier.forEach((box, i) => (box.children = next[i]));
        frontier = next.flat();
        depth += 1;
    }
    return { top, depth };
}

/** Leaves take consecutive slots; a parent sits centred over its children. */
function place(box: Box, slot: { next: number }): void {
    if (!box.children.length) {
        box.x = slot.next * (BOX_WIDTH + H_GAP) + BOX_WIDTH / 2;
        slot.next += 1;
        return;
    }
    box.children.forEach((child) => place(child, slot));
    box.x = (box.children[0].x + box.children[box.children.length - 1].x) / 2;
}

function eachBox(box: Box, visit: (box: Box) => void): void {
    visit(box);
    box.children.forEach((child) => eachBox(child, visit));
}

/** `text` shortened with an ellipsis until it fits `maxWidth`. */
function clamp(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string {
    if (ctx.measureText(text).width <= maxWidth) {
        return text;
    }
    let candidate = text;
    while (candidate.length > 1 && ctx.measureText(`${candidate}…`).width > maxWidth) {
        candidate = candidate.slice(0, -1);
    }
    return `${candidate}…`;
}

/** Word-wraps `text` into at most `maxLines` lines, the last one clamped. */
function wrap(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number): string[] {
    const lines: string[] = [];
    let line = '';
    for (const word of text.split(/\s+/).filter(Boolean)) {
        const candidate = line ? `${line} ${word}` : word;
        if (!line || ctx.measureText(candidate).width <= maxWidth) {
            line = candidate;
        } else {
            lines.push(line);
            line = word;
        }
    }
    if (line) {
        lines.push(line);
    }
    if (lines.length > maxLines) {
        lines.splice(maxLines - 1, lines.length, lines.slice(maxLines - 1).join(' '));
    }
    return lines.map((l) => clamp(ctx, l, maxWidth));
}

function roundedRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
}

/** Draws the overview of `root` described in the file comment. */
export function drawHierarchyPicture(root: HierarchyExportNode, options: HierarchyPictureOptions): HierarchyPicture {
    const scale = options.scale ?? 2;
    const rtl = options.rtl ?? false;
    const { top, depth } = overviewTree(root, options.moreLabel);

    const slot = { next: 0 };
    place(top, slot);
    const drawingWidth = slot.next * (BOX_WIDTH + H_GAP) - H_GAP;
    const drawingHeight = (depth + 1) * BOX_HEIGHT + depth * V_GAP;
    const width = Math.max(MIN_WIDTH, drawingWidth + PADDING * 2);
    const height = Math.max(MIN_HEIGHT, drawingHeight + PADDING * 2);
    const offsetX = (width - drawingWidth) / 2;
    const offsetY = (height - drawingHeight) / 2;

    // Right to left on an Arabic page, as the chart itself runs there.
    const centreX = (box: Box) => offsetX + (rtl ? drawingWidth - box.x : box.x);
    const topY = (box: Box) => offsetY + box.depth * (BOX_HEIGHT + V_GAP);

    const canvas = document.createElement('canvas');
    canvas.width = Math.round(width * scale);
    canvas.height = Math.round(height * scale);
    const ctx = canvas.getContext('2d');
    if (!ctx) {
        throw new Error('drawHierarchyPicture: 2D canvas context unavailable');
    }
    ctx.scale(scale, scale);
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, width, height);

    // Connectors first, so the boxes sit on top of them.
    ctx.strokeStyle = COLOR_LINE;
    ctx.lineWidth = 1.2;
    eachBox(top, (box) => {
        if (!box.children.length) {
            return;
        }
        const x = centreX(box);
        const bottom = topY(box) + BOX_HEIGHT;
        const mid = bottom + V_GAP / 2;
        const xs = box.children.map(centreX);
        ctx.beginPath();
        ctx.moveTo(x, bottom);
        ctx.lineTo(x, mid);
        ctx.moveTo(Math.min(...xs), mid);
        ctx.lineTo(Math.max(...xs), mid);
        xs.forEach((childX) => {
            ctx.moveTo(childX, mid);
            ctx.lineTo(childX, bottom + V_GAP);
        });
        ctx.stroke();
    });

    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.direction = rtl ? 'rtl' : 'ltr';
    eachBox(top, (box) => {
        const left = centreX(box) - BOX_WIDTH / 2;
        const y = topY(box);
        roundedRect(ctx, left, y, BOX_WIDTH, BOX_HEIGHT, 8);
        ctx.fillStyle = box.folded ? '#f9fafb' : options.fill ?? '#f8fafc';
        ctx.fill();
        ctx.setLineDash(box.folded ? [4, 3] : []);
        ctx.strokeStyle = box.folded ? COLOR_LINE : options.stroke ?? '#94a3b8';
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.setLineDash([]);

        ctx.font = `600 12px ${FONT_STACK}`;
        const lines = wrap(ctx, box.label, BOX_WIDTH - 16, 2);
        const lineHeight = 15;
        const blockTop = y + BOX_HEIGHT / 2 - ((lines.length + 1) * lineHeight) / 2 + lineHeight / 2;
        ctx.fillStyle = box.folded ? COLOR_MUTED : COLOR_TEXT;
        lines.forEach((line, i) => ctx.fillText(line, centreX(box), blockTop + i * lineHeight));

        ctx.font = `700 13px ${FONT_STACK}`;
        ctx.fillStyle = COLOR_MUTED;
        ctx.fillText(formatNumber(box.value), centreX(box), blockTop + lines.length * lineHeight);
    });

    return { canvas, width, height };
}
