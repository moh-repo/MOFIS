import { ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { BehaviorSubject, of } from 'rxjs';

import { HierarchicalChartsComponent } from './hierarchical-charts.component';
import { ChartDataService } from 'src/app/demo/service/chart-data.service';
import { ChartExportOptions, ChartPrintService } from 'src/app/demo/service/chart-print.service';
import { TranslationService } from 'src/app/demo/service/translation.service';

/**
 * R5-11: Download PDF on this page hung the tab for minutes, because the export
 * rasterised the organization charts' DOM — ~10,000 elements with the real data.
 * The page now hands the export its own cards, built from the hierarchies it loaded.
 */
describe('HierarchicalChartsComponent export (R5-11)', () => {

    const organization = { label: 'Scholarship Agencies', value: 5, children: [{ label: 'Higher Education', value: 5 }] };
    const location = { label: 'All Countries', value: 5, children: [{ label: 'Egypt', value: 5 }] };
    const academic = {
        label: 'Academic Distribution',
        value: 5,
        children: Array.from({ length: 180 }, (_, i) => ({ label: `Major ${i}`, value: 1, children: [{ label: 'Active', value: 1 }] }))
    };

    let print: jasmine.SpyObj<ChartPrintService>;
    let component: HierarchicalChartsComponent;

    beforeEach(() => {
        const data = {
            getOrganizationHierarchy: () => of(organization),
            getLocationHierarchy: () => of(location),
            getAcademicHierarchy: () => of(academic),
            selectedCountryId$: new BehaviorSubject<number | null>(null)
        } as unknown as ChartDataService;
        const translation = {
            translate: (key: string) => key,
            isRTL: () => false,
            currentLanguage$: new BehaviorSubject('en')
        } as unknown as TranslationService;
        const cdr = jasmine.createSpyObj<ChangeDetectorRef>('ChangeDetectorRef', ['markForCheck']);
        print = jasmine.createSpyObj<ChartPrintService>('ChartPrintService',
            ['downloadChartsPdf', 'previewChartsPdf', 'printChartsPdf', 'downloadChartImages']);

        component = new HierarchicalChartsComponent(data, print, {} as Router, translation, new MessageService(), cdr);
        component.ngOnInit();
    });

    afterEach(() => component.ngOnDestroy());

    const lastOptions = (spy: jasmine.Spy) => spy.calls.mostRecent().args[2] as ChartExportOptions;

    it('exports its own cards, in page order, instead of rasterising the charts', () => {
        print.downloadChartsPdf.and.resolveTo();

        component.downloadChartsPdf();

        const options = lastOptions(print.downloadChartsPdf);
        expect(options.domCardSelector).withContext('no DOM capture').toBeUndefined();
        const cards = options.cards!();
        expect(cards.map((c) => c.title)).toEqual([
            'HIERARCHY_CHARTS.SECTIONS.ORGANIZATION_TITLE',
            'HIERARCHY_CHARTS.SECTIONS.LOCATION_TITLE',
            'HIERARCHY_CHARTS.SECTIONS.ACADEMIC_TITLE'
        ]);
        // The whole academic tree is in the table: root + 180 majors + 180 statuses.
        expect(cards[2].rows.length).toBe(361);
        expect(cards[2].columns).toEqual(['HIERARCHY_CHARTS.EXPORT.SEGMENT', 'HIERARCHY_CHARTS.EXPORT.STUDENTS']);
    });

    it('marks the running export and lets no second one start until it ends', async () => {
        let finish!: () => void;
        print.downloadChartsPdf.and.returnValue(new Promise<void>((resolve) => (finish = resolve)));

        component.downloadChartsPdf();
        component.downloadChartImages();

        expect(component.exportAction).toBe('pdf');
        expect(print.downloadChartImages).not.toHaveBeenCalled();

        finish();
        await new Promise((resolve) => setTimeout(resolve));
        expect(component.exportAction).toBeNull();
    });

    it('clears the busy state even when the export fails', async () => {
        spyOn(console, 'error');
        print.previewChartsPdf.and.rejectWith(new Error('boom'));

        component.previewChartsPdf();
        await new Promise((resolve) => setTimeout(resolve));

        expect(component.exportAction).toBeNull();
    });
});
