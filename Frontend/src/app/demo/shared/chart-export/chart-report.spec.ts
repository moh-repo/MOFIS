import { Chart, registerables } from 'chart.js';
import { MessageService } from 'primeng/api';

import { readChartSeries } from './chart-series.util';
import { ChartPrintService } from 'src/app/demo/service/chart-print.service';
import { ReportPdfService } from 'src/app/demo/service/report-pdf.service';
import { TranslationService } from 'src/app/demo/service/translation.service';

/**
 * The visualisation pages, printed from Jasper instead of from the browser.
 *
 * <p>These eight pages were the last reports in the system that never reached the
 * server. Preview and Download pasted canvas images onto a PDF built in the browser,
 * and Print opened a second window holding a copy of the page's HTML and called
 * `window.print()` on it — note H50, "the print button prints the page itself and the
 * formatting is completely wrong". Nothing the browser prints can carry the masthead,
 * the ministry logo or the footer sentence, because those live in
 * REPORT_TYPE_SETTINGS and only the server's rendering reads them.
 *
 * <p>Two things are pinned here. The page asks the server for its document, on the
 * same endpoint and through the same viewer as the other forty-seven reports. And the
 * numbers go up with the picture: a printed chart nobody can read a figure out of is
 * a screenshot with a letterhead, so the table under each card is read back off the
 * chart instance that drew it.
 */
describe('Chart pages — Jasper documents', () => {

    const CHART_API = '/api/v1/charts/report/doc';

    beforeAll(() => Chart.register(...registerables));

    // ── the numbers behind a chart ───────────────────────────────────────────

    describe('readChartSeries', () => {

        let canvas: HTMLCanvasElement;
        let chart: Chart | undefined;

        beforeEach(() => {
            canvas = document.createElement('canvas');
            canvas.width = 240;
            canvas.height = 160;
            document.body.appendChild(canvas);
        });

        afterEach(() => {
            chart?.destroy();
            chart = undefined;
            canvas.remove();
        });

        const build = (config: any) => {
            chart = new Chart(canvas, { ...config, options: { ...config.options, animation: false, responsive: false } });
            return readChartSeries(canvas, 'Category');
        };

        it('reads one column per dataset, named after the dataset', () => {
            const table = build({
                type: 'bar',
                data: {
                    labels: ['Egypt', 'Jordan'],
                    datasets: [
                        { label: 'Enrolled', data: [412, 301] },
                        { label: 'Graduated', data: [88, 61] }
                    ]
                }
            });

            expect(table).withContext('a Chart.js canvas has series behind it').not.toBeNull();
            expect(table!.columns).toEqual(['Category', 'Enrolled', 'Graduated']);
            expect(table!.rows).toEqual([
                ['Egypt', '412', '88'],
                ['Jordan', '301', '61']
            ]);
        });

        it('groups thousands and keeps a fraction only where there is one', () => {
            const table = build({
                type: 'bar',
                data: { labels: ['2025'], datasets: [{ label: 'Amount', data: [1388150.5] }] }
            });

            expect(table!.rows[0][1]).toBe('1,388,150.50');
        });

        it('prints a missing reading as an empty cell, never as a zero', () => {
            // A gap in a series is not the same as a measurement of nothing, and a
            // report that turns one into the other is worse than one that omits it.
            const table = build({
                type: 'line',
                data: { labels: ['Q1', 'Q2', 'Q3'], datasets: [{ label: 'Spend', data: [10, null, 30] }] }
            });

            expect(table!.rows.map(r => r[1])).toEqual(['10', '', '30']);
        });

        it('takes the value from an {x, y} point', () => {
            const table = build({
                type: 'scatter',
                data: { labels: ['A'], datasets: [{ label: 'Points', data: [{ x: 3, y: 42 }] }] }
            });

            expect(table!.rows[0][1]).toBe('42');
        });

        it('joins a multi-line axis label into one cell', () => {
            const table = build({
                type: 'bar',
                data: { labels: [['2025', 'Q1']], datasets: [{ label: 'Total', data: [7] }] }
            });

            expect(table!.rows[0][0]).toBe('2025 Q1');
        });

        it('returns null for a canvas Chart.js never drew', () => {
            // The Leaflet map and the vis-network graph are exactly this: a picture
            // and no series. They go into the report as a picture, not as a fake table.
            const plain = document.createElement('canvas');
            expect(readChartSeries(plain, 'Category')).toBeNull();
        });
    });

    // ── the service the buttons call ─────────────────────────────────────────

    describe('ChartPrintService', () => {

        let reportPdf: jasmine.SpyObj<ReportPdfService>;
        let messages: jasmine.SpyObj<MessageService>;
        let service: ChartPrintService;
        let area: HTMLElement;
        let chart: Chart | undefined;

        const translation = {
            isRTL: () => true,
            getCurrentLanguage: () => 'ar',
            translate: (k: string) => k
        } as unknown as TranslationService;

        beforeEach(() => {
            reportPdf = jasmine.createSpyObj<ReportPdfService>('ReportPdfService',
                ['previewPdf', 'printPdf', 'downloadPdf', 'exportExcel']);
            messages = jasmine.createSpyObj<MessageService>('MessageService', ['add']);
            service = new ChartPrintService(reportPdf, messages, translation);

            area = document.createElement('div');
            area.id = 'test-print-area';
            document.body.appendChild(area);
        });

        afterEach(() => {
            chart?.destroy();
            chart = undefined;
            area.remove();
        });

        const withChart = () => {
            const card = document.createElement('div');
            card.className = 'chart-card';
            const heading = document.createElement('h5');
            heading.textContent = 'Enrolment by country';
            const canvas = document.createElement('canvas');
            canvas.width = 240;
            canvas.height = 160;
            card.append(heading, canvas);
            area.appendChild(card);

            chart = new Chart(canvas, {
                type: 'bar',
                data: { labels: ['Egypt'], datasets: [{ label: 'Enrolled', data: [412] }] },
                options: { animation: false, responsive: false }
            });
        };

        it('prints through the server, never through window.print', async () => {
            // The H50 regression. If this ever calls window.print again, the ministry
            // gets a screenshot of a web page instead of the ministry's own form.
            const browserPrint = spyOn(window, 'print');
            withChart();

            await service.printChartsPdf('test-print-area', 'Bar Charts');

            expect(browserPrint).not.toHaveBeenCalled();
            expect(reportPdf.printPdf).toHaveBeenCalledTimes(1);
            expect(reportPdf.printPdf.calls.mostRecent().args[0]).toBe(CHART_API);
        });

        it('sends the picture and the numbers under it', async () => {
            withChart();

            await service.previewChartsPdf('test-print-area', 'Bar Charts');

            const body = reportPdf.previewPdf.calls.mostRecent().args[3] as any;
            expect(body.title).toBe('Bar Charts');
            expect(body.lang).withContext('an Arabic UI asks for the Arabic document').toBe('ar');
            expect(body.charts.length).toBe(1);

            const card = body.charts[0];
            expect(card.title).toBe('Enrolment by country');
            expect(card.imageBase64).withContext('a PNG data URI the server can decode').toMatch(/^data:image\/png;base64,/);
            expect(card.columns).toEqual(['REPORTS.CHART_REPORT.CATEGORY', 'Enrolled']);
            expect(card.rows).toEqual([['Egypt', '412']]);
        });

        it('says so rather than printing an empty page', async () => {
            // The old code fell back to window.print() here, which is how a page with
            // nothing rendered on it still sent the browser's own markup to a printer.
            const browserPrint = spyOn(window, 'print');

            await service.printChartsPdf('test-print-area', 'Bar Charts');

            expect(reportPdf.printPdf).not.toHaveBeenCalled();
            expect(browserPrint).not.toHaveBeenCalled();
            expect(messages.add).toHaveBeenCalled();
            expect(messages.add.calls.mostRecent().args[0].severity).toBe('warn');
        });

        it('asks for a spreadsheet from the same endpoint', async () => {
            withChart();

            await service.exportChartsExcel('test-print-area', 'Bar Charts');

            expect(reportPdf.exportExcel).toHaveBeenCalledTimes(1);
            expect(reportPdf.exportExcel.calls.mostRecent().args[0]).toBe(CHART_API);
        });

        // ── R5-11: cards a page builds itself ────────────────────────────────

        const ownCard = () => {
            const picture = document.createElement('canvas');
            picture.width = 600;
            picture.height = 240;
            picture.getContext('2d')!.fillRect(10, 10, 100, 40);
            return {
                title: 'Academic Breakdown',
                subtitle: 'Majors by status and gender',
                stats: [],
                canvas: picture,
                displayWidth: 300,
                displayHeight: 120,
                columns: ['Segment', 'Students'],
                rows: [['Academic Distribution', '981'], ['    Human Medicine', '366']]
            };
        };

        it('sends the cards a page built from its data, without reading the page', async () => {
            // The print area is empty: no .chart-card, no canvas. The hierarchy page used
            // to rasterise its DOM here, which held the tab for minutes (R5-11).
            await service.downloadChartsPdf('test-print-area', 'Hierarchical Charts',
                { cards: () => [ownCard()], filename: 'hierarchical-charts.pdf' });

            expect(reportPdf.downloadPdf).toHaveBeenCalledTimes(1);
            const [path, , filename, body] = reportPdf.downloadPdf.calls.mostRecent().args as [string, unknown, string, any];
            expect(path).toBe(CHART_API);
            expect(filename).toBe('hierarchical-charts.pdf');
            expect(body.charts.length).toBe(1);
            expect(body.charts[0]).toEqual(jasmine.objectContaining({
                title: 'Academic Breakdown',
                subtitle: 'Majors by status and gender',
                columns: ['Segment', 'Students'],
                rows: ownCard().rows
            }));
            expect(body.charts[0].imageBase64).toMatch(/^data:image\/png;base64,/);
        });

        it('resolves a download only once the file has arrived, so the page can show progress', async () => {
            let finish!: (saved: boolean) => void;
            reportPdf.downloadPdf.and.returnValue(new Promise<boolean>((resolve) => (finish = resolve)));
            let settled = false;

            const running = service.downloadChartsPdf('test-print-area', 'Hierarchical Charts', { cards: () => [ownCard()] })
                .then(() => (settled = true));
            await new Promise((resolve) => setTimeout(resolve));
            expect(settled).withContext('still waiting for the server').toBeFalse();

            finish(true);
            await running;
            expect(settled).toBeTrue();
        });

        it('says the export failed instead of leaving it looking like it is still running', async () => {
            spyOn(console, 'error');

            await service.downloadChartsPdf('test-print-area', 'Hierarchical Charts', {
                cards: () => { throw new Error('canvas unavailable'); }
            });
            await service.downloadChartImages('test-print-area', 'Hierarchical Charts', {
                cards: () => { throw new Error('canvas unavailable'); }
            });

            expect(reportPdf.downloadPdf).not.toHaveBeenCalled();
            expect(messages.add).toHaveBeenCalledTimes(2);
            expect(messages.add.calls.allArgs().map(([m]) => m.severity)).toEqual(['error', 'error']);
        });
    });
});
