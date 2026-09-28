import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Table } from 'primeng/table';
import { BehaviorSubject, of } from 'rxjs';

import { SystemAdministrationModule } from '../system-administration.module';
import { StudyFinanceBankBranchComponent } from './study-finance-bank-branch.component';
import { StudyFinanceBankBranchService } from '../../../service/study-finance-bank-branch.service';
import { StudyFinanceBankService } from '../../../service/study-finance-bank.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';

/**
 * Every name on the bank branches page is in the current language, and follows
 * a language switch, which happens in place without a reload.
 *
 * <p>R5-07: the bank column and both bank lists named every bank in Arabic, in
 * English too; the admin endpoint returns both names and the screen read the
 * Arabic one. The branch-name column did the same with the branch's own names.
 * Country and government names were right on first load, because LookupService
 * names them in the language current when it fetches them, but a switch left
 * them in the previous language until the page was reopened.
 */

const JORDAN = 1004;
const AMMAN = 5;

const BANKS = [
    { id: 1003, studyFinanceBankeName: 'البنك الأهلي الأردني', studyFinanceBankeNameEn: 'Jordan Ahli Bank', studyCountryInId: JORDAN },
    { id: 1004, studyFinanceBankeName: 'البنك العربي', studyFinanceBankeNameEn: 'Arab Bank', studyCountryInId: JORDAN },
    { id: 7, studyFinanceBankeName: 'بنك بلا اسم إنجليزي', studyFinanceBankeNameEn: '  ', studyCountryInId: JORDAN }
];

/** Arabic order (أ before ب) is the reverse of English order (Amman before Zarqa). */
const ROWS = [
    { id: 20, ar: 'ب فرع عمّان', en: 'Amman branch', bank: 1003 },
    { id: 21, ar: 'أ فرع الزرقاء', en: 'Zarqa branch', bank: 1004 },
    { id: 22, ar: 'فرع بلا اسم إنجليزي', en: null, bank: 7 }
].map(r => ({
    id: r.id, studyFinanceBankBranchName: r.ar, studyFinanceBankBranchNameEn: r.en,
    studyFinanceBankeId: r.bank, studyCountryInId: JORDAN, studyGovInId: AMMAN, studyFinanceBankBranchMidate: ''
}));

const build = async (language: 'en' | 'ar') => {
    const lang$ = new BehaviorSubject<string>(language);
    const english = () => lang$.value === 'en';
    // As the real LookupService: named in the language current when fetched.
    const lookups = {
        countryCalls: 0,
        getAllStudyCountries: () => {
            lookups.countryCalls++;
            return of([{ id: JORDAN, studyCountryInName: english() ? 'Jordan' : 'الأردن' }]);
        },
        getAllStudyGovernments: () =>
            of([{ id: AMMAN, studyCountryInId: JORDAN, studyGovInName: english() ? 'Amman' : 'عمّان' }])
    };
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [SystemAdministrationModule, RouterModule.forRoot([])],
        providers: [
            provideNoopAnimations(),
            MessageService,
            ConfirmationService,
            { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } },
            { provide: StudyFinanceBankBranchService, useValue: { getAll: () => of(ROWS) } },
            { provide: StudyFinanceBankService, useValue: { getAll: () => of(BANKS) } },
            { provide: LookupService, useValue: lookups },
            {
                provide: TranslationService,
                useValue: {
                    currentLanguage$: lang$.asObservable(), initialized$: of(true), isReady: () => true,
                    isRTL: () => lang$.value === 'ar', translate: (k: string) => k, getCurrentLanguage: () => lang$.value
                }
            },
            {
                provide: UmsAuthService,
                useValue: new Proxy({ permissions$: of([]), profile$: of(), currentUser$: of(null) } as any,
                    { get: (t, p) => p in t ? t[p] : () => true })
            }
        ]
    }).compileComponents();
    const fixture = TestBed.createComponent(StudyFinanceBankBranchComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const switchTo = (next: 'en' | 'ar') => {
        lang$.next(next);
        fixture.detectChanges();
    };
    return { fixture, component: fixture.componentInstance, switchTo, lookups };
};

type Fixture = ComponentFixture<StudyFinanceBankBranchComponent>;

/** One column of the table, row by row. */
const column = (fixture: Fixture, n: number) =>
    (Array.from(fixture.nativeElement.querySelectorAll(`tbody tr td:nth-child(${n})`)) as HTMLElement[])
        .map(td => td.textContent!.trim());
const nameColumn = (fixture: Fixture) => column(fixture, 2);
const bankColumn = (fixture: Fixture) => column(fixture, 3);

describe('bank names on the bank branches page (R5-07)', () => {

    it('are English on the English screen', async () => {
        const { fixture, component } = await build('en');

        expect(bankColumn(fixture)).toEqual(['Jordan Ahli Bank', 'Arab Bank', 'بنك بلا اسم إنجليزي']);
        // The filter-bar and dialog lists bind these same options.
        expect(component.bankOptions.map(o => o.label))
            .toEqual(['Jordan Ahli Bank', 'Arab Bank', 'بنك بلا اسم إنجليزي']);
    });

    it('are Arabic on the Arabic screen', async () => {
        const { fixture, component } = await build('ar');

        expect(bankColumn(fixture)).toEqual(['البنك الأهلي الأردني', 'البنك العربي', 'بنك بلا اسم إنجليزي']);
        expect(component.bankOptions.map(o => o.label))
            .toEqual(['البنك الأهلي الأردني', 'البنك العربي', 'بنك بلا اسم إنجليزي']);
    });

    it('follow a language switch without reloading the page', async () => {
        const { fixture, switchTo } = await build('ar');

        switchTo('en');

        expect(bankColumn(fixture)).toEqual(['Jordan Ahli Bank', 'Arab Bank', 'بنك بلا اسم إنجليزي']);
    });

    it('show in the filter list under the name of the current language', async () => {
        const { fixture, component } = await build('en');

        component.filterBank = 1004;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const select = fixture.nativeElement.querySelector('p-select[inputid="bbBank"]') as HTMLElement;
        expect(select.textContent).toContain('Arab Bank');
        expect(select.textContent).not.toContain('البنك العربي');
    });
});

describe('branch names on the bank branches page', () => {

    it('are English on the English screen, Arabic where a branch has no English name', async () => {
        const { fixture } = await build('en');

        expect(nameColumn(fixture)).toEqual(['Amman branch', 'Zarqa branch', 'فرع بلا اسم إنجليزي']);
    });

    it('are Arabic on the Arabic screen', async () => {
        const { fixture } = await build('ar');

        expect(nameColumn(fixture)).toEqual(['ب فرع عمّان', 'أ فرع الزرقاء', 'فرع بلا اسم إنجليزي']);
    });

    it('follow a language switch', async () => {
        const { fixture, switchTo } = await build('ar');

        switchTo('en');

        expect(nameColumn(fixture)).toEqual(['Amman branch', 'Zarqa branch', 'فرع بلا اسم إنجليزي']);
    });

    it('sort by the name shown, not by the Arabic one', async () => {
        const { fixture } = await build('en');

        (fixture.nativeElement.querySelector('th[psortablecolumn="displayName"]') as HTMLElement).click();
        fixture.detectChanges();

        const names = nameColumn(fixture);
        // By the Arabic names the Zarqa branch (أ) would come first.
        expect(names.indexOf('Amman branch')).toBeLessThan(names.indexOf('Zarqa branch'));
    });

    it('are found by the search in either language', async () => {
        const { fixture } = await build('ar');
        const table = fixture.debugElement.query(By.directive(Table)).componentInstance as Table;

        table.filterGlobal('zarqa', 'contains');
        // PrimeNG applies a global filter after its filterDelay.
        await new Promise(resolve => setTimeout(resolve, table.filterDelay + 50));
        fixture.detectChanges();

        expect(nameColumn(fixture)).toEqual(['أ فرع الزرقاء']);
    });

    it("head the view dialog in the current language, which lists both", async () => {
        const { fixture, component } = await build('en');

        component.openViewDialog(component.branches[0]);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(document.querySelector('.p-dialog .dv-hero__title')!.textContent!.trim()).toBe('Amman branch');
        const details = document.querySelector('.p-dialog .dv-dl')!.textContent!;
        expect(details).toContain('ب فرع عمّان');
        expect(details).toContain('Amman branch');

        fixture.destroy();
    });
});

describe('country and government names on the bank branches page', () => {

    it('follow a language switch instead of waiting for the page to be reopened', async () => {
        const { fixture, switchTo, lookups } = await build('ar');
        expect(column(fixture, 4)[0]).toBe('الأردن');
        expect(column(fixture, 5)[0]).toBe('عمّان');
        expect(lookups.countryCalls).withContext('fetched once on load, not again for the first language').toBe(1);

        switchTo('en');

        expect(column(fixture, 4)[0]).toBe('Jordan');
        expect(column(fixture, 5)[0]).toBe('Amman');
    });

    it("an open dialog's government list follows too", async () => {
        const { component, switchTo } = await build('ar');

        component.openEditDialog(component.branches[0]);
        expect(component.studyGovernments.map(g => g.studyGovInName)).toEqual(['عمّان']);

        switchTo('en');

        expect(component.studyGovernments.map(g => g.studyGovInName)).toEqual(['Amman']);
        expect(component.branchForm.get('studyGovInId')!.value).withContext('the choice is kept').toBe(AMMAN);
    });
});
