/*
 * `@embedpdf/snippet` is imported dynamically, in mountReportPdf, and this file
 * carries no value import of it at all — only the two types below, which erase at
 * compile time.
 *
 * The reason is where this file sits in the module graph. ReportViewerComponent is
 * declared by SharedModule, SharedModule is imported by AppModule, and AppModule is
 * the eager root: anything reachable from it lands in the initial bundle. A static
 * import here therefore put the whole PDF engine — the viewer, its plugin registry,
 * the pdfium bindings — on the critical path of the login page, for a component
 * nobody sees until they open a report. It is now a separate chunk the browser
 * fetches the first time a report is actually opened.
 *
 * Keep it that way: a plain `import ... from '@embedpdf/snippet'` anywhere in this
 * file, including one an editor adds to satisfy a type, puts it back in the shell.
 * Use `import type` if you need a type.
 */
type EmbedPdfModule = typeof import('@embedpdf/snippet');
type EmbedPdfViewer = Awaited<ReturnType<EmbedPdfModule['default']['init']>>;

/** Resolve WASM URL against the app base href (works in dev and deployed contexts). */
export function resolveEmbedPdfWasmUrl(): string {
    return new URL('assets/embedpdf/pdfium.wasm', document.baseURI).toString();
}

const REPORT_VIEWER_DISABLED_CATEGORIES = [
    'annotation',
    'redaction',
    'insert',
    'form',
    'document-open',
    'rotate',
];

/**
 * How long to wait for a fetched PDF to open in the engine before giving up. Generous
 * for a large report on a slow machine, short enough that a stuck open becomes an
 * error the user can act on rather than an indefinite splash.
 */
const OPEN_DOCUMENT_TIMEOUT_MS = 20000;

interface ContainerMountState {
    mountId: number;
    viewer: EmbedPdfViewer | null;
}

const mountStateByContainer = new WeakMap<HTMLElement, ContainerMountState>();

function toArrayBuffer(data: Uint8Array): ArrayBuffer {
    if (data.byteOffset === 0 && data.byteLength === data.buffer.byteLength) {
        return data.buffer as ArrayBuffer;
    }
    return data.slice().buffer;
}

/**
 * Result of the check below, per URL, for the life of the page.
 *
 * The check exists to turn a misconfigured deployment (the asset missing, or nginx
 * answering index.html for it) into a clear error instead of a viewer that never
 * starts. That answer cannot change while the tab is open, but the check was run on
 * every mount — so opening a report, switching a tab or changing the language each
 * fetched the whole .wasm again. The HTTP cache usually absorbed it; with
 * `Cache-Control: no-store` in front of the app, or a cold cache, it was megabytes
 * per tab click and a visible pause on the engine splash (review item P2).
 *
 * A failure is not remembered: it may be a transient network error, and the next
 * attempt should look again.
 */
const wasmChecks = new Map<string, Promise<void>>();

function assertWasmAvailable(wasmUrl: string): Promise<void> {
    const cached = wasmChecks.get(wasmUrl);
    if (cached) {
        return cached;
    }
    const check = (async () => {
        const response = await fetch(wasmUrl, { method: 'GET' });
        if (!response.ok) {
            throw new Error(`EmbedPDF WASM not found (${response.status}): ${wasmUrl}`);
        }
        const contentType = response.headers.get('content-type') ?? '';
        if (contentType.includes('text/html')) {
            throw new Error(`EmbedPDF WASM URL returned HTML instead of WASM: ${wasmUrl}`);
        }
    })();
    check.catch(() => wasmChecks.delete(wasmUrl));
    wasmChecks.set(wasmUrl, check);
    return check;
}

/**
 * Rejects if `promise` has not settled within `timeoutMs`.
 *
 * Every await in the mount path needs one: a step that never settles leaves the
 * viewer sitting on "Initializing PDF engine" with no error and no way out, which is
 * exactly what the client reported. The caller turns a rejection into the ordinary
 * "could not load" state, which at least offers a retry.
 */
function withTimeout<T>(promise: Promise<T>, timeoutMs: number, what: string): Promise<T> {
    let timer: number | undefined;
    const timeout = new Promise<never>((_resolve, reject) => {
        timer = window.setTimeout(() => reject(new Error(`${what} timed out after ${timeoutMs} ms`)), timeoutMs);
    });
    return Promise.race([promise, timeout]).finally(() => {
        if (timer != null) {
            window.clearTimeout(timer);
        }
    }) as Promise<T>;
}

/**
 * Resolve once the container has a real width, or after `timeoutMs`.
 *
 * The zoom plugin computes fit-width as `availableWidth / contentWidth` and
 * abandons the calculation outright when the viewport measures zero, leaving the
 * document at its default scale. Mounting on a fixed timer could therefore land
 * while a dialog was still animating open and lock the report to the wrong zoom.
 */
function waitForContainerWidth(target: HTMLElement, timeoutMs = 2000): Promise<void> {
    if (target.clientWidth > 0) {
        return Promise.resolve();
    }
    return new Promise<void>((resolve) => {
        const startedAt = Date.now();
        const poll = () => {
            if (target.clientWidth > 0 || Date.now() - startedAt >= timeoutMs) {
                resolve();
                return;
            }
            requestAnimationFrame(poll);
        };
        requestAnimationFrame(poll);
    });
}

async function waitForPlugins(
    registry: { pluginsReady(): Promise<void> },
    timeoutMs = 30000
): Promise<void> {
    await Promise.race([
        registry.pluginsReady(),
        new Promise<void>((_, reject) => {
            window.setTimeout(
                () => reject(new Error('EmbedPDF plugin initialization timed out')),
                timeoutMs
            );
        }),
    ]);
}

export function destroyEmbedViewer(target: HTMLElement): void {
    const state = mountStateByContainer.get(target);
    const viewer = state?.viewer as { destroy?: () => void } | null | undefined;
    if (viewer && typeof viewer.destroy === 'function') {
        try {
            viewer.destroy();
        } catch {
            // Best-effort cleanup; DOM reset below is the reliable fallback.
        }
    }
    mountStateByContainer.delete(target);
    target.replaceChildren();
}

export async function mountReportPdf(
    target: HTMLElement,
    pdfData: Uint8Array,
    filename: string,
    height?: string,
    mountId?: number
): Promise<void> {
    const current = mountStateByContainer.get(target);
    if (current && mountId != null && current.mountId === mountId) {
        return;
    }

    destroyEmbedViewer(target);
    if (height) {
        target.style.height = height;
        target.style.minHeight = height;
    }
    target.style.width = '100%';

    // EmbedPDF has no RTL support: its viewport, scroll and zoom plugins drive
    // horizontal position through raw scrollLeft, which browsers invert under
    // dir="rtl". Inheriting the Arabic layout direction therefore anchored the
    // page to the wrong side of the viewport and clipped it, leaving empty space
    // opposite. The Arabic text lives inside the PDF and is unaffected by the
    // direction of the element hosting the viewer.
    target.dir = 'ltr';
    target.style.direction = 'ltr';

    await waitForContainerWidth(target);

    const wasmUrl = resolveEmbedPdfWasmUrl();
    await assertWasmAvailable(wasmUrl);

    // The one place the PDF engine is loaded. See the note at the top of the file:
    // this await is what keeps it out of the application shell. The browser caches
    // the module, so only the first report opened in a session pays for the fetch.
    const { default: EmbedPDF, ZoomMode } = await import('@embedpdf/snippet');

    const viewer = EmbedPDF.init({
        type: 'container',
        target,
        wasmUrl,
        // Angular/webpack bundles worker chunks unreliably in dev; direct engine is stable.
        worker: false,
        tabBar: 'never',
        disabledCategories: REPORT_VIEWER_DISABLED_CATEGORIES,
        // Span the viewer's width, scrolling vertically for the rest of the page.
        // fit-page is a click away in the zoom toolbar for whole-page review.
        zoom: { defaultZoomLevel: ZoomMode.FitWidth },
        export: { defaultFileName: filename },
        // Avoid blocking init on external font CDNs (Google Fonts / jsDelivr).
        fontFallback: null,
        fonts: { ui: null, signature: null },
        // The stamp plugin loads its default stamp library from jsDelivr on start,
        // which the page's Content-Security-Policy (connect-src 'self') refuses: two
        // console errors on every viewer, first seen on the voucher page (round-6 QA
        // R6-08). Stamps are annotation tools, a category this viewer disables, so
        // the library is not wanted anyway.
        stamp: { manifests: [] },
    });

    if (!viewer) {
        throw new Error('EmbedPDF.init returned no viewer instance');
    }

    mountStateByContainer.set(target, {
        mountId: mountId ?? Date.now(),
        viewer,
    });

    const registry = await viewer.registry;
    await waitForPlugins(registry);

    const docManager = registry.getPlugin('document-manager')?.provides() as
        | {
              openDocumentBuffer: (opts: { buffer: ArrayBuffer; name: string; autoActivate: boolean }) => {
                  toPromise?: () => Promise<unknown>;
              } | void;
          }
        | undefined;
    if (!docManager) {
        throw new Error('EmbedPDF document-manager plugin is unavailable');
    }

    // openDocumentBuffer hands back a Task. It used to be called and forgotten, so a
    // document that failed to parse — or one whose open simply never settled — left
    // the engine splash on screen for good, with nothing logged and nothing to click.
    // Awaiting it means a failure ends as the viewer's "could not load" state.
    const opened = docManager.openDocumentBuffer({
        buffer: toArrayBuffer(pdfData),
        name: filename,
        autoActivate: true,
    });
    if (opened && typeof opened.toPromise === 'function') {
        await withTimeout(opened.toPromise(), OPEN_DOCUMENT_TIMEOUT_MS, 'Opening the PDF document');
    }
}

/**
 * Sends the PDF already mounted in `target` to the printer.
 *
 * Printing goes through the viewer rather than through a print stylesheet so that
 * what comes out of the printer is the Jasper PDF itself — same pagination, same
 * Arabic shaping, same header and signature blocks. The alternative the report
 * pages used to take, rebuilding an A4 page in HTML, could never match it.
 *
 * `print` is deliberately absent from REPORT_VIEWER_DISABLED_CATEGORIES, so the
 * plugin is present in every report viewer instance.
 */
export async function printMountedPdf(target: HTMLElement): Promise<void> {
    const state = mountStateByContainer.get(target);
    if (!state?.viewer) {
        throw new Error('No PDF is mounted in this container');
    }

    const registry = await state.viewer.registry;
    const printPlugin = registry.getPlugin('print')?.provides() as
        | { print: (options?: unknown) => unknown }
        | undefined;
    if (!printPlugin) {
        throw new Error('EmbedPDF print plugin is unavailable');
    }

    printPlugin.print();
}
