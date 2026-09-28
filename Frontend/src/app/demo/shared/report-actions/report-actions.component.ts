import { Component, Input } from '@angular/core';
import { ReportPdfQuery, ReportPdfService, buildReportPdfQuery } from 'src/app/demo/service/report-pdf.service';

/**
 * The four actions every report offers: preview, download, print, export.
 *
 * Each report page used to wire these up itself, which is how they drifted — a
 * quarter of them had no print at all, and "export" variously meant an .xlsx, a
 * client-side PDF, or a raw JSON dump of the loaded rows. Owning the buttons here
 * means a page supplies only *what* to render (`pdfApiPath` + `query`) and the
 * behaviour is identical everywhere: one Jasper template on the server, shown and
 * printed through EmbedPDF.
 *
 * Usage:
 *   <app-report-actions
 *     pdfApiPath="/api/periodic-reports/monthly/export-report"
 *     [query]="monthlyPdfQuery()"
 *     baseFilename="monthly-report">
 *   </app-report-actions>
 */
@Component({
    selector: 'app-report-actions',
    templateUrl: './report-actions.component.html',
    styleUrls: ['./report-actions.component.scss'],
    standalone: false
})
export class ReportActionsComponent {

    /** The report's PDF endpoint, e.g. `/api/.../export-report`. */
    @Input() pdfApiPath = '';

    /** Filters/parameters for this run of the report. */
    @Input() query: ReportPdfQuery | null = null;

    /** File name without extension; `.pdf` and `.xlsx` are appended per action. */
    @Input() baseFilename = 'report';

    /**
     * Spreadsheet endpoint. Defaults to the PDF path's `/export-excel` alias, which
     * is what every report controller exposes; set it only for the few reports whose
     * Excel lives elsewhere.
     */
    @Input() excelApiPath: string | null = null;

    /** Disables the whole bar while the page is still loading its data. */
    @Input() disabled = false;

    /**
     * Whether the report has a spreadsheet form. An official document (an ID card, a
     * certificate) does not, and offering the button there answered 404 (QA N17).
     */
    @Input() showExcel = true;

    constructor(private reportPdfService: ReportPdfService) {}

    preview(): void {
        this.reportPdfService.previewPdf(this.pdfApiPath, this.currentQuery(), `${this.baseFilename}.pdf`);
    }

    download(): void {
        this.reportPdfService.downloadPdf(this.pdfApiPath, this.currentQuery(), `${this.baseFilename}.pdf`);
    }

    print(): void {
        this.reportPdfService.printPdf(this.pdfApiPath, this.currentQuery(), `${this.baseFilename}.pdf`);
    }

    exportExcel(): void {
        const path = this.excelApiPath || this.reportPdfService.toExcelApiPath(this.pdfApiPath);
        this.reportPdfService.exportExcel(path, this.currentQuery(), `${this.baseFilename}.xlsx`);
    }

    private currentQuery(): ReportPdfQuery {
        return this.query ?? buildReportPdfQuery([]);
    }
}
