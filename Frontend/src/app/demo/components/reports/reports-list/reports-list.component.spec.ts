import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterModule } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { ReportsListComponent } from './reports-list.component';
import { ReportsModule } from '../reports.module';
import { ReportCatalogService } from 'src/app/demo/service/report-catalog.service';
import { ReportHubCard } from 'src/app/demo/service/report-hub-permission.service';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { UmsPermissionService } from 'src/app/demo/service/ums-permission.service';
import { UmsAuthService } from 'src/app/demo/service/ums-auth.service';
import { StudentReportsComponent } from '../student-reports/student-reports.component';

/**
 * The landing page and one hub read their cards from the catalogue: names in
 * the reader's language, order and visibility as the catalogue and the
 * permission filter decide, nothing listed in the component. The landing page
 * also lists every report under its hub, searchable (review item P1).
 */
function catalogStub(cards: ReportHubCard[], groups: unknown[], loadFailed = false) {
    return {
        landingCards: () => of(cards),
        allReports: () => of(groups),
        reload: () => undefined,
        loadFailed
    };
}

describe('ReportsListComponent', () => {
    let fixture: ComponentFixture<ReportsListComponent>;
    let language$: BehaviorSubject<string>;

    const report = (code: string, title: string, titleAr: string, available = true) => ({
        reportCode: code, title, titleAr, description: '', icon: 'pi-file', color: 'blue',
        route: '/reports/x/' + code, queryParams: null, implemented: available, available
    });

    const hub = (code: string) => ({
        reportCode: code, nameAr: code, nameEn: code, icon: 'pi-users', color: 'blue-600',
        route: '/reports/' + code.toLowerCase()
    });

    const groups = [
        {
            hub: hub('STUDENT'),
            hubTitle: 'Student Reports',
            reports: [report('A', 'Enrolment statistics', 'إحصائيات التسجيل'),
                      report('B', 'Student list', 'قائمة الطلاب')]
        },
        {
            hub: hub('FIN'),
            hubTitle: 'Financial Reports',
            reports: [report('C', 'Payment summary', 'ملخص المدفوعات', false)]
        }
    ];

    const cards: ReportHubCard[] = [
        { nameAr: 'تقارير الطلاب', nameEn: 'Student Reports', descAr: 'وصف', descEn: 'Desc', icon: 'pi-users', color: 'blue-600', route: '/reports/student', pageCode: 'REPORT_ACADEMIC' },
        { nameAr: 'نموذج مالي', nameEn: 'Financial form', icon: 'pi-file', color: 'green-600', route: '/reports/financial-data-form', queryParams: { v: '2' } }
    ];

    beforeEach(async () => {
        language$ = new BehaviorSubject<string>('en');
        await TestBed.configureTestingModule({
            imports: [ReportsModule, RouterModule.forRoot([])],
            providers: [
                { provide: ReportCatalogService, useValue: catalogStub(cards, groups) },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: language$.asObservable(), initialized$: of(true), isReady: () => true,
                        isRTL: () => language$.value === 'ar', translate: (k: string) => k, getCurrentLanguage: () => language$.value
                    }
                },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, canAccessPage: () => true } },
                { provide: UmsAuthService, useValue: { permissions$: of([]), permissions: [], isAdmin: () => true, isSuperAdmin: () => true, isLoggedIn: () => true } }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(ReportsListComponent);
        fixture.detectChanges();
    });

    const names = () => Array.from(fixture.nativeElement.querySelectorAll('.report-hub-card__name')).map((e: any) => e.textContent.trim());

    it('shows the catalogue\'s landing cards, named in the current language', () => {
        expect(names()).toEqual(['Student Reports', 'Financial form']);
        expect(fixture.nativeElement.querySelector('.report-hub-card__desc').textContent.trim()).toBe('Desc');
        expect(fixture.nativeElement.querySelector('.pi-users')).not.toBeNull();
    });

    it('re-labels on a language switch', () => {
        language$.next('ar');
        fixture.detectChanges();
        expect(names()).toEqual(['تقارير الطلاب', 'نموذج مالي']);
    });

    it('carries the card\'s query parameters into the navigation', () => {
        const router = TestBed.inject(Router);
        const navigate = spyOn(router, 'navigateByUrl').and.resolveTo(true);
        const links = fixture.nativeElement.querySelectorAll('.report-hub-card') as NodeListOf<HTMLElement>;
        links[1].click();
        expect(navigate).toHaveBeenCalled();
        expect(String(navigate.calls.mostRecent().args[0])).toContain('v=2');
        expect(String(navigate.calls.mostRecent().args[0])).toContain('/reports/financial-data-form');
    });

    it('lists every report the reader may open, grouped by hub (P1)', () => {
        const items = Array.from(fixture.nativeElement.querySelectorAll('.all-reports__item'))
            .map((e: any) => e.textContent.trim());
        expect(items.length).toBe(3);
        expect(items[0]).toContain('Enrolment statistics');
        expect(Array.from(fixture.nativeElement.querySelectorAll('.all-reports__hub'))
            .map((e: any) => e.textContent.trim())).toEqual(['Student Reports', 'Financial Reports']);
        expect(fixture.nativeElement.querySelector('.all-reports__count').textContent.trim()).toBe('3 / 3');
    });

    it('a report not yet built is listed but not a link', () => {
        const links = Array.from(fixture.nativeElement.querySelectorAll('a.all-reports__item'))
            .map((e: any) => e.textContent.trim());
        expect(links.length).toBe(2);
        expect(fixture.nativeElement.textContent).toContain('REPORTS.REPORT_SUBPAGES.COMING_SOON');
    });

    it('searches names in both languages, ignoring hamza and diacritics', () => {
        const component = fixture.componentInstance;

        component.search = 'payment';
        component.applySearch();
        expect(component.matchCount).toBe(1);
        expect(component.filteredGroups[0].hubTitle).toBe('Financial Reports');

        // typed without the hamza the catalogue spells with
        component.search = 'احصائيات';
        component.applySearch();
        expect(component.matchCount).toBe(1);
        expect(component.filteredGroups[0].reports[0].reportCode).toBe('A');

        // every word must match, and the hub's own name counts as one
        component.search = 'student list';
        component.applySearch();
        expect(component.matchCount).toBe(1);

        component.search = 'nothing here';
        component.applySearch();
        expect(component.matchCount).toBe(0);

        component.clearSearch();
        expect(component.matchCount).toBe(3);
    });

    it('offers a retry when the catalogue could not be loaded, rather than "no access"', () => {
        TestBed.resetTestingModule();
        const stub = catalogStub([], [], true);
        const reload = spyOn(stub, 'reload');
        TestBed.configureTestingModule({
            imports: [ReportsModule, RouterModule.forRoot([])],
            providers: [
                { provide: ReportCatalogService, useValue: stub },
                { provide: TranslationService, useValue: { currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en' } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, canAccessPage: () => true } },
                { provide: UmsAuthService, useValue: { permissions$: of([]), permissions: [], isAdmin: () => true, isSuperAdmin: () => true, isLoggedIn: () => true } }
            ]
        });
        const failed = TestBed.createComponent(ReportsListComponent);
        failed.detectChanges();
        expect(failed.nativeElement.textContent).toContain('REPORTS_LIST.LOAD_FAILED');
        expect(failed.nativeElement.textContent).not.toContain('REPORTS_LIST.NO_ACCESS');
        failed.componentInstance.retry();
        expect(reload).toHaveBeenCalled();
    });

    it('says so when nothing is open to the reader', () => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({
            imports: [ReportsModule, RouterModule.forRoot([])],
            providers: [
                { provide: ReportCatalogService, useValue: catalogStub([], []) },
                { provide: TranslationService, useValue: { currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en' } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, canAccessPage: () => true } },
                { provide: UmsAuthService, useValue: { permissions$: of([]), permissions: [], isAdmin: () => true, isSuperAdmin: () => true, isLoggedIn: () => true } }
            ]
        });
        const empty = TestBed.createComponent(ReportsListComponent);
        empty.detectChanges();
        expect(empty.nativeElement.textContent).toContain('REPORTS_LIST.NO_ACCESS');
    });
});

describe('StudentReportsComponent (a hub)', () => {

    it('reads its cards from the catalogue by its own route', () => {
        const hubCards = jasmine.createSpy('hubCards').and.returnValue(of([
            { reportCode: 'A', title: 'Enrolment', titleAr: 'التسجيل', description: 'd', icon: 'pi-chart-line', color: 'blue', route: '/reports/student/enrollment-statistics', queryParams: null, implemented: true, available: true },
            { reportCode: 'B', title: 'Soon', titleAr: 'قريبا', description: 'd', icon: 'pi-clock', color: 'gray', route: '/reports/student/x', queryParams: null, implemented: false, available: false }
        ]));
        TestBed.configureTestingModule({
            imports: [ReportsModule, RouterModule.forRoot([])],
            providers: [
                { provide: ReportCatalogService, useValue: { hubCards } },
                { provide: TranslationService, useValue: { currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en' } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, canAccessPage: () => true } },
                { provide: UmsAuthService, useValue: { permissions$: of([]), permissions: [], isAdmin: () => true, isSuperAdmin: () => true, isLoggedIn: () => true } }
            ]
        });
        const hub = TestBed.createComponent(StudentReportsComponent);
        hub.detectChanges();

        expect(hubCards).toHaveBeenCalled();
        expect(hubCards.calls.mostRecent().args[0]).withContext('the hub identifies itself by the router url').toBe('/');
        const text = hub.nativeElement.textContent;
        expect(text).toContain('Enrolment');
        expect(text).toContain('REPORTS.REPORT_SUBPAGES.VIEW_REPORT');
        expect(text).toContain('REPORTS.REPORT_SUBPAGES.COMING_SOON');
    });
});
