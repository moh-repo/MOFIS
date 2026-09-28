import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ReportsModule } from '../reports.module';
import { GeographicMapsComponent } from './geographic-maps.component';
import { ChartDataService } from '../../../service/chart-data.service';
import { ChartPrintService } from '../../../service/chart-print.service';
import { TranslationService } from '../../../service/translation.service';

/**
 * Which side of the ranking rows their rank lands on (round-5 Phase 7).
 *
 * <p>The page wrapper carries `direction: rtl` in Arabic, so each `.list li` already
 * runs rank, name, bar and value from the right. The wrapper's RTL block then said
 * `flex-direction: row-reverse` on the rows, the double mirror the page hero lost
 * earlier: in Arabic the rank sat at the left end and the figure at the right.
 *
 * <p>The map itself is not built here (`initializeMap` is stubbed out); the rows are
 * plain template over `locations` and the governorate breakdown.
 */
describe('Geographic maps ranking rows in both reading directions', () => {

    let fixture: ComponentFixture<GeographicMapsComponent>;
    let frame: HTMLElement;

    const build = async (dir: 'ltr' | 'rtl') => {
        const lang = dir === 'rtl' ? 'ar' : 'en';
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [ReportsModule, RouterModule.forRoot([])],
            providers: [
                {
                    provide: ChartDataService,
                    useValue: new Proxy({ selectedCountryId$: of(null) } as any, {
                        get: (target, prop) => prop in target ? target[prop] : () => of([])
                    })
                },
                { provide: ChartPrintService, useValue: new Proxy({} as any, { get: () => () => undefined }) },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of(lang), initialized$: of(true), isReady: () => true,
                        isRTL: () => dir === 'rtl', translate: (k: string) => k.split('.').pop()!, getCurrentLanguage: () => lang
                    }
                }
            ]
        }).compileComponents();

        spyOn(GeographicMapsComponent.prototype, 'initializeMap');
        spyOn(GeographicMapsComponent.prototype, 'loadGeographicData');
        fixture = TestBed.createComponent(GeographicMapsComponent);
        const page = fixture.componentInstance;
        page.locations = [
            { country: 'مصر', studentCount: 120, governorates: [] },
            { country: 'الأردن', studentCount: 40, governorates: [] }
        ] as any;
        page.filteredGovernorates = [{ name: 'القاهرة', value: 80 }, { name: 'الجيزة', value: 40 }] as any;

        frame = document.createElement('div');
        frame.setAttribute('dir', dir);
        frame.style.width = '1400px';
        if (dir === 'rtl') { frame.className = 'rtl'; }
        document.body.appendChild(frame);
        frame.appendChild(fixture.nativeElement);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => frame?.remove());

    const el = (selector: string): HTMLElement => {
        const found = (fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement;
        expect(found).withContext(`nothing matches ${selector}`).toBeTruthy();
        return found;
    };

    /** `first` sits on the reading-start side of `second`: the right in Arabic, the left in English. */
    const expectReadFirst = (first: string, second: string, dir: 'ltr' | 'rtl') => {
        const a = el(first).getBoundingClientRect();
        const b = el(second).getBoundingClientRect();
        if (dir === 'ltr') {
            expect(a.right).withContext(`${first} ends at ${Math.round(a.right)}, ${second} starts at ${Math.round(b.left)}`)
                .toBeLessThanOrEqual(b.left + 1);
        } else {
            expect(a.left).withContext(`${first} starts at ${Math.round(a.left)}, ${second} ends at ${Math.round(b.right)}`)
                .toBeGreaterThanOrEqual(b.right - 1);
        }
    };

    (['ltr', 'rtl'] as const).forEach(dir => {

        it(`leads each country row with its rank and ends it with the figure in ${dir}`, async () => {
            await build(dir);

            expectReadFirst('.country-list li .rank', '.country-list li .name', dir);
            expectReadFirst('.country-list li .name', '.country-list li .value', dir);
            if (dir === 'rtl') {
                expect(getComputedStyle(el('.country-list li')).flexDirection)
                    .withContext('reversed a second time on top of direction: rtl').not.toBe('row-reverse');
            }
        });

        it(`leads each governorate row with its name in ${dir}`, async () => {
            await build(dir);

            expectReadFirst('.gov-list li .name', '.gov-list li .value', dir);
        });
    });
});
