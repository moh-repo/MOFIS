import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';

import { SystemAdministrationModule } from '../system-administration.module';
import { StudyFinanceBankBranchComponent } from './study-finance-bank-branch.component';
import { StudyFinanceBankBranchService } from '../../../service/study-finance-bank-branch.service';
import { StudyFinanceBankService } from '../../../service/study-finance-bank.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { AdminTier } from '../../../api/ums';
import { StudyFinanceBankBranch } from '../../../api/study-finance-bank-branch';

/**
 * R5-07: the bank-branch buttons follow the server's rule, not SETTINGS.EDIT.
 *
 * <p>POST, PUT and DELETE on /studyFinanceBankBranch are gated by
 * COUNTRY_CONFIG_WRITE (SETTINGS.EDIT or the COUNTRY_ADMIN tier) and then by
 * CountryOwnedConfigPolicy: an attaché's branch is written by that attaché's own
 * accounts; ministry-level accounts only read it. The screen asked for
 * SETTINGS.EDIT alone, so the country admin, whom the server lets write, saw only
 * View, and the ministry admin, whom it refuses, was offered Add, Edit and Delete
 * and got 403 on save.
 *
 * <p>The real UmsPermissionService is used, so the super admin's "every code"
 * shortcut is the one the application has.
 */

const EGYPT = 1;
const JORDAN = 1004;
const SAUDI = 1005;
/** What the login answers for a ministry-level account: every attaché. */
const ALL = [EGYPT, JORDAN, SAUDI];

const MINISTRY_TIERS: AdminTier[] = ['SUPER_ADMIN', 'MINISTRY_ADMIN', 'MINISTRY_STAFF'];

interface Account {
    tier: AdminTier | null;
    codes: string[];
    countries: number[];
    /** The IsAdmin flag, for a session that predates the tier. */
    admin?: boolean;
}

const umsAuth = (a: Account) => {
    const ministry = a.tier !== null && MINISTRY_TIERS.includes(a.tier);
    return {
        adminTier: a.tier,
        isMinistryLevel: ministry,
        countryIds: a.countries,
        permissions: a.codes,
        permissions$: of(a.codes),
        // AuthService projects the shell user from these two in its constructor.
        profile$: of(),
        currentUser$: of(null),
        isLoggedIn: () => true,
        isSuperAdmin: () => a.tier === 'SUPER_ADMIN',
        isAdmin: () => a.admin ?? (a.tier === 'SUPER_ADMIN' || a.tier === 'MINISTRY_ADMIN' || a.tier === 'COUNTRY_ADMIN'),
        isMinistryStaff: () => a.tier === 'MINISTRY_STAFF'
    };
};

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

/** `countryId` may be null here although the model says number: the column is nullable. */
const branch = (id: number, countryId: number | null) => ({
    id, studyFinanceBankBranchName: `branch ${id}`, studyFinanceBankBranchNameEn: null,
    studyFinanceBankeId: 1, studyCountryInId: countryId, studyGovInId: null, studyFinanceBankBranchMidate: ''
}) as unknown as StudyFinanceBankBranch;

/** Jordan's branch, Egypt's, and one with no country (none exist today; the rule still covers it). */
const ROWS = [branch(20, JORDAN), branch(1, EGYPT), branch(30, null)];

const COUNTRIES = [
    { id: EGYPT, studyCountryInName: 'Egypt' },
    { id: JORDAN, studyCountryInName: 'Jordan' },
    { id: SAUDI, studyCountryInName: 'Saudi Arabia' }
];

const build = async (account: Account) => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [SystemAdministrationModule, RouterModule.forRoot([])],
        providers: [
            provideNoopAnimations(),
            MessageService,
            ConfirmationService,
            { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } },
            { provide: StudyFinanceBankBranchService, useValue: { getAll: () => of(ROWS) } },
            { provide: StudyFinanceBankService, useValue: { getAll: () => of([]) } },
            {
                provide: LookupService,
                useValue: { getAllStudyCountries: () => of(COUNTRIES), getAllStudyGovernments: () => of([]) }
            },
            { provide: TranslationService, useValue: translation },
            { provide: UmsAuthService, useValue: umsAuth(account) }
        ]
    }).compileComponents();
    const fixture = TestBed.createComponent(StudyFinanceBankBranchComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
};

type Fixture = ComponentFixture<StudyFinanceBankBranchComponent>;

/** What each row offers, by branch id: the icons of its action buttons. */
const offered = (fixture: Fixture) => {
    const out: Record<number, string[]> = {};
    (Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLElement[]).forEach(tr => {
        const id = Number(tr.querySelector('td')!.textContent!.trim());
        out[id] = (Array.from(tr.querySelectorAll('button[icon]')) as HTMLElement[])
            .map(b => b.getAttribute('icon')!.replace('pi pi-', ''));
    });
    return out;
};

const hasAdd = (fixture: Fixture) =>!!fixture.nativeElement.querySelector('button[icon="pi pi-plus"]');

describe('bank branch write buttons (R5-07)', () => {

    it('the owning country admin (jo_cadmin) adds, edits and deletes its own branches', async () => {
        // No SETTINGS.EDIT: the server lets it in through the COUNTRY_ADMIN tier.
        const fixture = await build({ tier: 'COUNTRY_ADMIN', codes: ['SETTINGS.VIEW'], countries: [JORDAN] });

        expect(hasAdd(fixture)).withContext('no Add for the attaché that owns the branches').toBeTrue();
        const rows = offered(fixture);
        expect(rows[20]).withContext('its own branch').toEqual(['eye', 'pencil', 'trash']);
        // The server would not list these for Jordan; the rule is per row all the same.
        expect(rows[1]).withContext("another attaché's branch").toEqual(['eye']);
        expect(rows[30]).withContext('a branch with no country is the ministry\'s').toEqual(['eye']);
    });

    it('the ministry admin (min_admin) only reads, although it holds SETTINGS.EDIT and every country', async () => {
        const fixture = await build({ tier: 'MINISTRY_ADMIN', codes: ['SETTINGS.VIEW', 'SETTINGS.EDIT'], countries: ALL });

        expect(hasAdd(fixture)).withContext('Add would be refused: a new branch always lands in an attaché').toBeFalse();
        const rows = offered(fixture);
        expect(rows[20]).toEqual(['eye']);
        expect(rows[1]).toEqual(['eye']);
        // Delete asks only about the stored country, and a null one is the ministry's.
        // Edit would save it into its bank's country, which the ministry may not write.
        expect(rows[30]).withContext('no-country branch: delete only').toEqual(['eye', 'trash']);
    });

    it('the super admin has no hatch: the same as the ministry admin', async () => {
        const fixture = await build({ tier: 'SUPER_ADMIN', codes: [], countries: ALL });

        expect(hasAdd(fixture)).toBeFalse();
        const rows = offered(fixture);
        expect(rows[20]).toEqual(['eye']);
        expect(rows[1]).toEqual(['eye']);
        expect(rows[30]).toEqual(['eye', 'trash']);
    });

    it('ministry staff read only', async () => {
        const fixture = await build({ tier: 'MINISTRY_STAFF', codes: ['SETTINGS.VIEW'], countries: ALL });

        expect(hasAdd(fixture)).toBeFalse();
        // No SETTINGS.EDIT and no country-admin tier: the endpoint gate refuses even the no-country row.
        expect(Object.values(offered(fixture))).toEqual([['eye'], ['eye'], ['eye']]);
    });

    it('country staff holding SETTINGS.EDIT write their own attaché\'s branches', async () => {
        const fixture = await build({ tier: 'COUNTRY_STAFF', codes: ['SETTINGS.VIEW', 'SETTINGS.EDIT'], countries: [JORDAN] });

        expect(hasAdd(fixture)).toBeTrue();
        const rows = offered(fixture);
        expect(rows[20]).toEqual(['eye', 'pencil', 'trash']);
        expect(rows[1]).toEqual(['eye']);
        expect(rows[30]).toEqual(['eye']);
    });

    it('country staff with only SETTINGS.VIEW see View and nothing else', async () => {
        const fixture = await build({ tier: 'COUNTRY_STAFF', codes: ['SETTINGS.VIEW'], countries: [JORDAN] });

        expect(hasAdd(fixture)).toBeFalse();
        expect(Object.values(offered(fixture))).toEqual([['eye'], ['eye'], ['eye']]);
    });

    it('a country admin with no attaché yet has nothing to add to', async () => {
        const fixture = await build({ tier: 'COUNTRY_ADMIN', codes: ['SETTINGS.VIEW'], countries: [] });

        expect(hasAdd(fixture)).toBeFalse();
    });

    it('a session from before the tier existed reads the flags, as the server does', async () => {
        const fixture = await build({ tier: null, admin: true, codes: ['SETTINGS.VIEW'], countries: [JORDAN] });

        expect(hasAdd(fixture)).toBeTrue();
        expect(offered(fixture)[20]).toEqual(['eye', 'pencil', 'trash']);
    });

    it("the view dialog's Edit follows the same rule", async () => {
        const fixture = await build({ tier: 'COUNTRY_ADMIN', codes: ['SETTINGS.VIEW'], countries: [JORDAN] });
        const component = fixture.componentInstance;
        const editInDialog = () => (Array.from(document.querySelectorAll('.p-dialog button')) as HTMLElement[])
            .some(b => b.textContent!.includes('COMMON.EDIT'));

        component.openViewDialog(ROWS[0]);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(editInDialog()).withContext('its own branch').toBeTrue();

        component.viewDialogVisible = false;
        fixture.detectChanges();
        await fixture.whenStable();

        component.openViewDialog(ROWS[1]);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(editInDialog()).withContext("another attaché's branch: the footer offered Edit to everyone").toBeFalse();

        fixture.destroy();
    });

    it("the dialog offers only the countries a branch may be saved into", async () => {
        const fixture = await build({ tier: 'COUNTRY_ADMIN', codes: ['SETTINGS.VIEW'], countries: [JORDAN] });
        const component = fixture.componentInstance;

        component.openCreateDialog();

        expect(component.formCountries.map(c => c.id)).toEqual([JORDAN]);
        // The filter bar still lists every country the page shows names for.
        expect(component.studyCountries.length).toBe(3);
    });
});
