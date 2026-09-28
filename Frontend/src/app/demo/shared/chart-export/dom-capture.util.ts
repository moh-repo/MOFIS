/**
 * Rasterises DOM-rendered "charts" — PrimeNG organization charts, the Leaflet map —
 * which have no `<canvas>` of their own to read pixels from.
 *
 * html2canvas is loaded on demand so it stays out of the eagerly loaded bundles;
 * only the pages that export DOM cards ever pay for it.
 */

type Html2Canvas = (element: HTMLElement, options?: Record<string, unknown>) => Promise<HTMLCanvasElement>;

let html2canvasPromise: Promise<Html2Canvas> | null = null;

function loadHtml2Canvas(): Promise<Html2Canvas> {
    if (!html2canvasPromise) {
        html2canvasPromise = import('html2canvas').then((module) => (module.default ?? module) as unknown as Html2Canvas);
    }
    return html2canvasPromise;
}

export interface DomCaptureOptions {
    scale?: number;
    /** CSS selector for nodes to drop from the capture (buttons, tooltips, overlays). */
    ignoreSelector?: string;
}

const DEFAULT_IGNORE =
    '.p-button, button, .p-tooltip, .p-toast, .leaflet-control-container, .p-dropdown-panel, ' +
    '.loading-overlay, .vis-navigation, .vis-button';

/** Render `element` to a canvas that mirrors its on-screen appearance. */
export async function captureElement(
    element: HTMLElement,
    options: DomCaptureOptions = {}
): Promise<HTMLCanvasElement> {
    const html2canvas = await loadHtml2Canvas();
    const ignore = options.ignoreSelector ?? DEFAULT_IGNORE;

    return html2canvas(element, {
        scale: options.scale ?? 2,
        backgroundColor: '#ffffff',
        // Leaflet tiles are same-origin-safe only via CORS; useCORS keeps the canvas untainted.
        useCORS: true,
        allowTaint: false,
        logging: false,
        // The card may be taller than the viewport — capture its full box, not the visible slice.
        windowWidth: document.documentElement.scrollWidth,
        scrollX: 0,
        scrollY: 0,
        ignoreElements: (node: Element) => !!ignore && node.matches?.(ignore),
    });
}
