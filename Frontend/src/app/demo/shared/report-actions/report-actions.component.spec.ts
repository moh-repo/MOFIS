import { TestBed } from '@angular/core/testing';
import { ButtonModule } from 'primeng/button';
import { ReportActionsComponent } from './report-actions.component';
import { ReportPdfService } from 'src/app/demo/service/report-pdf.service';
import { TranslatePipe } from 'src/app/demo/pipes/translate.pipe';

/**
 * The point of this component is that every report offers the same four actions
 * against the same Jasper endpoint. These tests pin exactly that: four buttons,
 * each routed to its own service call, all carrying the page's current query.
 *
 * Report pages used to wire the actions up individually, which is how a quarter
 * of them ended up with no Print at all and how "export" came to mean four
 * different things — including a raw JSON dump of the loaded rows.
 */
describe('ReportActionsComponent', () => {
    let service: jasmine.SpyObj<ReportPdfService>;

    const query = { year: 2026, countryId: 3 };

    const build = () => {
        const fixture = TestBed.createComponent(ReportActionsComponent);
        const c = fixture.componentInstance;
        c.pdfApiPath = '/api/periodic-reports/monthly/export-report';
        c.query = query;
        c.baseFilename = 'monthly-report';
        fixture.detectChanges();
        return fixture;
    };

    beforeEach(async () => {
        service = jasmine.createSpyObj<ReportPdfService>('ReportPdfService',
            ['previewPdf', 'downloadPdf', 'printPdf', 'exportExcel', 'toExcelApiPath', 'toastPdfError']);
        service.toExcelApiPath.and.callFake((p: string) =>
            p.replace(/\/(export-report|report-pdf|doc)$/, '/export-excel'));

        await TestBed.configureTestingModule({
            declarations: [ReportActionsComponent],
            imports: [ButtonModule, TranslatePipe],
            providers: [{ provide: ReportPdfService, useValue: service }]
        }).compileComponents();
    });

    it('renders all four actions', () => {
        const fixture = build();
        const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('button');

        expect(buttons.length).withContext('preview, download, print, export').toBe(4);
        const icons = Array.from(buttons).map(b => b.querySelector('.pi')?.className ?? '');
        expect(icons.some(c => c.includes('pi-eye'))).withContext('preview icon').toBeTrue();
        expect(icons.some(c => c.includes('pi-download'))).withContext('download icon').toBeTrue();
        expect(icons.some(c => c.includes('pi-print'))).withContext('print icon').toBeTrue();
        expect(icons.some(c => c.includes('pi-file-excel'))).withContext('export icon').toBeTrue();
    });

    it('leaves out Excel for a report with no spreadsheet form (QA N17)', () => {
        const fixture = TestBed.createComponent(ReportActionsComponent);
        fixture.componentInstance.pdfApiPath = '/api/reports/individual-student/7/id-card/export-report';
        fixture.componentInstance.showExcel = false;
        fixture.detectChanges();

        const icons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button .pi'))
            .map(i => i.className);
        expect(icons.length).toBe(3);
        expect(icons.some(c => c.includes('pi-file-excel'))).toBeFalse();
    });

    it('sends every action to the report endpoint with the page query', () => {
        const c = build().componentInstance;

        c.preview();
        expect(service.previewPdf).toHaveBeenCalledWith(
            '/api/periodic-reports/monthly/export-report', query, 'monthly-report.pdf');

        c.download();
        expect(service.downloadPdf).toHaveBeenCalledWith(
            '/api/periodic-reports/monthly/export-report', query, 'monthly-report.pdf');

        c.print();
        expect(service.printPdf).toHaveBeenCalledWith(
            '/api/periodic-reports/monthly/export-report', query, 'monthly-report.pdf');
    });

    it('exports from the endpoint spreadsheet alias', () => {
        const c = build().componentInstance;

        c.exportExcel();

        // The .xlsx comes from the same report, so the page configures one path only.
        expect(service.exportExcel).toHaveBeenCalledWith(
            '/api/periodic-reports/monthly/export-excel', query, 'monthly-report.xlsx');
    });

    it('honours an explicit excelApiPath for reports whose Excel lives elsewhere', () => {
        const fixture = build();
        const c = fixture.componentInstance;
        c.excelApiPath = '/api/custom/somewhere-else/export-excel';

        c.exportExcel();

        expect(service.exportExcel).toHaveBeenCalledWith(
            '/api/custom/somewhere-else/export-excel', query, 'monthly-report.xlsx');
    });

    it('falls back to an empty query rather than sending null', () => {
        const fixture = build();
        fixture.componentInstance.query = null;

        fixture.componentInstance.preview();

        expect(service.previewPdf).toHaveBeenCalledWith(
            '/api/periodic-reports/monthly/export-report', {}, 'monthly-report.pdf');
    });
});
