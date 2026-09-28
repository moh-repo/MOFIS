import { Injectable } from '@angular/core';
import {
  ChartCardCapture,
  canvasToPngDataUrl,
  captureChartCanvas,
  collectChartCards,
  collectDomCards,
  readCardTitle,
  renderChartCard,
} from 'src/app/demo/shared/chart-export/chart-capture.util';
import { readChartSeries } from 'src/app/demo/shared/chart-export/chart-series.util';
import { captureElement } from 'src/app/demo/shared/chart-export/dom-capture.util';
import { ReportPdfService, buildReportPdfQuery } from './report-pdf.service';
import { MessageService } from 'primeng/api';
import { TranslationService } from './translation.service';

/** A card the page composed itself: the picture, and the numbers that go under it. */
export interface ChartExportCard extends ChartCardCapture {
  columns: string[];
  rows: string[][];
}

export interface ChartExportOptions {
  /**
   * CSS selector for cards rendered as DOM rather than `<canvas>` (the Leaflet map, the
   * network graphs). When set, those cards are rasterised instead of the canvas scan.
   */
  domCardSelector?: string;
  /**
   * The page's cards, built from its data, in place of reading the page. Takes
   * precedence over both scans. For charts whose DOM is too large to rasterise — the
   * hierarchy charts (R5-11) — see hierarchy-export.util.ts.
   */
  cards?: () => ChartExportCard[];
  filename?: string;
}

/** One card on its way to the server: the picture, and the numbers under it. */
interface ChartPayload {
  title: string;
  subtitle: string;
  imageBase64: string;
  columns: string[];
  rows: string[][];
}

/** The chart report's endpoint; `/doc` is rewritten to `/api/pdf/...` like every other report. */
const CHART_REPORT_API = '/api/v1/charts/report/doc';

/**
 * Print and export for the eight visualisation pages.
 *
 * <p>These pages used to be the exception in the system. Every other report is built
 * by JasperReports on the server and shown through EmbedPDF; these built a PDF in the
 * browser by pasting canvas images onto pages, and Print did not produce a PDF at all
 * — it opened a second window containing a copy of the page's HTML and called
 * `window.print()` on it. Note H50 followed inevitably: "the print button prints the
 * page itself and the formatting is completely wrong". A browser printing its own
 * markup cannot reach the masthead, the ministry logo, the signatories or the footer
 * sentence, because those live in REPORT_TYPE_SETTINGS and only the server's rendering
 * reads them.
 *
 * <p>Now the same three actions post to `chart-report.jrxml`. What the browser sends
 * is only what the browser alone can produce — the rendered cards, because Chart.js,
 * Leaflet and vis-network draw in the client and have no server-side equivalent — plus
 * the numbers behind each card, read back off the chart instance so the table can
 * never disagree with the picture above it.
 *
 * <p>Downloading individual PNGs stays client-side, because a PNG of a chart is
 * exactly what the canvas already holds and a round-trip would add nothing.
 */
@Injectable({ providedIn: 'root' })
export class ChartPrintService {

  constructor(
    private reportPdfService: ReportPdfService,
    private messageService: MessageService,
    private translationService: TranslationService
  ) {}

  /** Open the page's charts as a Jasper PDF in the shared EmbedPDF modal. */
  async previewChartsPdf(elementId: string, title: string, options: ChartExportOptions = {}): Promise<void> {
    const body = await this.buildRequest(elementId, title, options);
    if (!body) {
      return;
    }
    this.reportPdfService.previewPdf(CHART_REPORT_API, this.query(), this.toFilename(title, 'pdf'), body);
  }

  /**
   * Print the report.
   *
   * <p>Opens the same viewer as the preview and raises the print dialog once the
   * document has rendered, so what reaches the printer is the Jasper PDF — the same
   * pages, masthead and footer the reader saw — instead of a screenshot of the page.
   */
  async printChartsPdf(elementId: string, title: string, options: ChartExportOptions = {}): Promise<void> {
    const body = await this.buildRequest(elementId, title, options);
    if (!body) {
      return;
    }
    this.reportPdfService.printPdf(CHART_REPORT_API, this.query(), this.toFilename(title, 'pdf'), body);
  }

  /**
   * Download the same document the preview shows. Resolves once the file has been
   * handed to the browser or the failure reported, so a page can show progress.
   */
  async downloadChartsPdf(elementId: string, title: string, options: ChartExportOptions = {}): Promise<void> {
    const body = await this.buildRequest(elementId, title, options);
    if (!body) {
      return;
    }
    await this.reportPdfService.downloadPdf(
      CHART_REPORT_API,
      this.query(),
      options.filename || this.toFilename(title, 'pdf'),
      body
    );
  }

  /** Download the report as a spreadsheet: the same cards' numbers, from the same template. */
  async exportChartsExcel(elementId: string, title: string, options: ChartExportOptions = {}): Promise<void> {
    const body = await this.buildRequest(elementId, title, options);
    if (!body) {
      return;
    }
    this.reportPdfService.exportExcel(CHART_REPORT_API, this.query(), this.toFilename(title, 'xlsx'), body);
  }

  /** Download one PNG per chart, matching the on-screen card layout. */
  async downloadChartImages(elementId: string, title: string, options: ChartExportOptions = {}): Promise<void> {
    let cards: { canvas: HTMLCanvasElement; label: string }[];
    try {
      cards = await this.renderCards(elementId, title, options);
    } catch (error) {
      this.toastExportFailed(error);
      return;
    }
    if (!cards.length) {
      this.toastNothingToExport();
      return;
    }
    const prefix = this.slug(options.filename ?? '') || this.toFilename(title, '');
    cards.forEach((card, index) => {
      // A lone card needs no disambiguating suffix.
      const suffix = cards.length === 1 ? '' : `-${this.slug(card.label) || `chart-${index + 1}`}`;
      this.saveDataUrl(canvasToPngDataUrl(card.canvas), `${prefix}${suffix}.png`);
    });
  }

  /** Download a single chart as PNG, including its heading and summary figures. */
  downloadChartAsPng(chartRef: any, filename: string): void {
    const canvas = this.resolveCanvas(chartRef);
    if (canvas) {
      const rendered = renderChartCard(captureChartCanvas(canvas), this.renderOptions());
      this.saveDataUrl(canvasToPngDataUrl(rendered), `${filename}.png`);
      return;
    }
    const base64: string | undefined = chartRef?.getBase64Image?.();
    if (base64) {
      this.saveDataUrl(base64, `${filename}.png`);
    }
  }

  /** Preview a single chart as a one-card Jasper report. */
  async previewChartPdf(chartRef: any, title: string, filename?: string): Promise<void> {
    const canvas = this.resolveCanvas(chartRef);
    if (!canvas) {
      this.toastNothingToExport();
      return;
    }
    const capture = captureChartCanvas(canvas);
    const body = {
      title,
      lang: this.lang(),
      charts: [this.toPayload(capture, title)]
    };
    this.reportPdfService.previewPdf(CHART_REPORT_API, this.query(), filename || this.toFilename(title, 'pdf'), body);
  }

  // ── building the request ──────────────────────────────────────────────────

  /**
   * Collects the page's cards, or tells the reader there is nothing to report.
   *
   * <p>Returning `null` on an empty page is deliberate: the old code fell back to
   * `window.print()` here, which is how a page with no rendered chart still managed
   * to send the browser's own rendering to the printer.
   */
  private async buildRequest(elementId: string, title: string, options: ChartExportOptions) {
    let charts: ChartPayload[];
    try {
      charts = await this.collectPayloads(elementId, title, options);
    } catch (error) {
      this.toastExportFailed(error);
      return null;
    }
    if (!charts.length) {
      this.toastNothingToExport();
      return null;
    }
    return { title, lang: this.lang(), charts };
  }

  private async collectPayloads(elementId: string, title: string, options: ChartExportOptions): Promise<ChartPayload[]> {
    if (options.cards) {
      return options.cards().map((card) => ({
        ...this.toPayload(card, title, false),
        columns: card.columns,
        rows: card.rows,
      }));
    }

    const container = document.getElementById(elementId);
    if (!container) {
      return [];
    }

    // DOM cards — the Leaflet map, the organisation chart — are rasterised whole.
    // They have no Chart.js instance and so no series: a picture is all they are on
    // screen too, and the report says so rather than inventing a table.
    if (options.domCardSelector) {
      const cards = collectDomCards(container, options.domCardSelector);
      const payloads: ChartPayload[] = [];
      for (const card of cards) {
        const box = card.getBoundingClientRect();
        const snapshot = await captureElement(card);
        const capture: ChartCardCapture = {
          title: readCardTitle(card),
          subtitle: '',
          stats: [],
          canvas: snapshot,
          displayWidth: box.width,
          displayHeight: box.height,
        };
        payloads.push(this.toPayload(capture, title, false));
      }
      return payloads;
    }

    return collectChartCards(container).map((capture) => this.toPayload(capture, title));
  }

  /**
   * One card as the server wants it.
   *
   * <p>The image is composed without its own heading, because the template prints the
   * heading itself — drawing it into the picture as well would set the same words
   * twice on every page. The summary figures under a chart stay in the image: they are
   * laid out relative to the card, and the template has no field for them.
   */
  private toPayload(capture: ChartCardCapture, pageTitle: string, readSeries = true): ChartPayload {
    const series = readSeries ? readChartSeries(capture.canvas, this.categoryLabel()) : null;
    const image = renderChartCard({ ...capture, title: '', subtitle: '' }, this.renderOptions(pageTitle));

    return {
      title: capture.title,
      subtitle: capture.subtitle,
      imageBase64: canvasToPngDataUrl(image),
      columns: series?.columns ?? [],
      rows: series?.rows ?? []
    };
  }

  private query() {
    // `lang` also reaches the server in the body; it is repeated here because
    // ReportExports reads the query string when it writes the access-log row.
    return buildReportPdfQuery([['lang', this.lang()]]);
  }

  private lang(): string {
    return this.translationService.isRTL() ? 'ar' : 'en';
  }

  private categoryLabel(): string {
    return this.translationService.translate('REPORTS.CHART_REPORT.CATEGORY') || 'Category';
  }

  private toastNothingToExport(): void {
    this.messageService.add({
      severity: 'warn',
      summary: this.translationService.translate('COMMON.WARNING') || 'Nothing to export',
      detail: this.translationService.translate('REPORTS.CHART_REPORT.NO_CHARTS')
        || 'This page has no rendered chart to put in a report yet.'
    });
  }

  /**
   * The charts could not be turned into cards. Said in the reader's language rather
   * than left as an unhandled rejection: the buttons fire and forget, so without this
   * a failed export looked exactly like one still running.
   */
  private toastExportFailed(error: unknown): void {
    console.error('Chart export failed', error);
    const detail = this.translationService.translate('REPORTS.CHART_REPORT.EXPORT_FAILED');
    this.messageService.add({
      severity: 'error',
      summary: this.translationService.translate('COMMON.ERROR') || 'Error',
      detail: detail && detail !== 'REPORTS.CHART_REPORT.EXPORT_FAILED'
        ? detail
        : 'The charts could not be exported. Please try again.',
      life: 10000
    });
  }

  // ── rendering helpers ─────────────────────────────────────────────────────

  /** Finished, print-ready canvases for the PNG export, which keeps their headings. */
  private async renderCards(elementId: string, title: string, options: ChartExportOptions) {
    const renderOptions = this.renderOptions(title);
    if (options.cards) {
      // A composed picture carries no heading of its own, so the PNG gets the card's.
      return options.cards().map((card) => ({ canvas: renderChartCard(card, renderOptions), label: card.title }));
    }

    const container = document.getElementById(elementId);
    if (!container) {
      return [];
    }

    if (options.domCardSelector) {
      const cards = collectDomCards(container, options.domCardSelector);
      const rendered: { canvas: HTMLCanvasElement; label: string }[] = [];
      for (const card of cards) {
        const box = card.getBoundingClientRect();
        const snapshot = await captureElement(card);
        const capture: ChartCardCapture = {
          title: '',
          subtitle: '',
          stats: [],
          canvas: snapshot,
          displayWidth: box.width,
          displayHeight: box.height,
        };
        rendered.push({ canvas: renderChartCard(capture, renderOptions), label: readCardTitle(card) });
      }
      return rendered;
    }

    return collectChartCards(container).map((capture) => ({
      canvas: renderChartCard(capture, renderOptions),
      label: capture.title,
    }));
  }

  private renderOptions(pageTitle?: string) {
    return {
      pageTitle,
      rtl: this.translationService.isRTL(),
      locale: this.translationService.getCurrentLanguage(),
      scale: 2,
    };
  }

  private resolveCanvas(chartRef: any): HTMLCanvasElement | null {
    const host = chartRef?.el?.nativeElement as HTMLElement | undefined;
    const canvas = host?.querySelector('canvas') ?? (chartRef?.canvas as HTMLCanvasElement | undefined);
    return canvas ?? null;
  }

  private saveDataUrl(dataUrl: string, filename: string): void {
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  private toFilename(title: string, extension: string): string {
    const base = this.slug(title) || 'charts';
    return extension ? `${base}.${extension}` : base;
  }

  /** ASCII slug; Arabic titles fall back to the caller-supplied base name. */
  private slug(value: string): string {
    return value
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 60);
  }
}
