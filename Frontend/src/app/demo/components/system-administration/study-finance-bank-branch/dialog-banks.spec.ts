import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { By } from '@angular/platform-browser';
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

/**
 * The branch dialog offers only banks the branch can be saved with.
 *
 * <p>The server refuses a branch whose country is not its bank's (400). The
 * dialog offered every bank whatever country was chosen, so a country admin
 * holding two attachés could pair Jordan with a Saudi bank and be refused.
 */

const EGYPT = 1;
const JORDAN = 1004;
const SAUDI = 1005;

const BANKS = [
    { id: 1003, studyFinanceBankeName: 'الأهلي الأردني', studyFinanceBankeNameEn: 'Jordan Ahli Bank', studyCountryInId: JORDAN },
    { id: 1004, studyFinanceBankeName: 'العربي', studyFinanceBankeNameEn: 'Arab Bank', studyCountryInId: JORDAN },
    { id: 1000, studyFinanceBankeName: 'ساب', studyFinanceBankeNameEn: 'SABB', studyCountryInId: SAUDI },
    // Not this account's: the server would not list it; the dialog must not offer it either.
    { id: 1, studyFinanceBankeName: 'الأهلي المصري', studyFinanceBankeNameEn: 'National Bank of Egypt', studyCountryInId: EGYPT }
];

const JORDAN_BRANCH = {
    id: 20, studyFinanceBankBranchName: 'فرع', studyFinanceBankBranchNameEn: 'Branch',
    studyFinanceBankeId: 1004, studyCountryInId: JORDAN, studyGovInId: null, studyFinanceBankBranchMidate: ''
};

/** A country admin holding Jordan and Saudi Arabia. */
const umsAuth = {
    adminTier: 'COUNTRY_ADMIN',
    isMinistryLevel: false,
    countryIds: [JORDAN, SAUDI],
    permissions: ['SETTINGS.VIEW'],
    permissions$: of(['SETTINGS.VIEW']),
    profile$: of(),
    currentUser$: of(null),
    isLoggedIn: () => true,
    isSuperAdmin: () => false,
    isAdmin: () => true,
    isMinistryStaff: () => false
};

const build = async () => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [SystemAdministrationModule, RouterModule.forRoot([])],
        providers: [
            provideNoopAnimations(),
            MessageService,
            ConfirmationService,
            { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } },
            { provide: StudyFinanceBankBranchService, useValue: { getAll: () => of([JORDAN_BRANCH]) } },
            { provide: StudyFinanceBankService, useValue: { getAll: () => of(BANKS) } },
            {
                provide: LookupService,
                useValue: {
                    getAllStudyCountries: () => of([
                        { id: EGYPT, studyCountryInName: 'Egypt' },
                        { id: JORDAN, studyCountryInName: 'Jordan' },
                        { id: SAUDI, studyCountryInName: 'Saudi Arabia' }
                    ]),
                    getAllStudyGovernments: () => of([
                        { id: 5, studyCountryInId: JORDAN, studyGovInName: 'Amman' },
                        { id: 9, studyCountryInId: SAUDI, studyGovInName: 'Riyadh' }
                    ])
                }
            },
            {
                provide: TranslationService,
                useValue: {
                    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                }
            },
            { provide: UmsAuthService, useValue: umsAuth }
        ]
    }).compileComponents();
    const fixture = TestBed.createComponent(StudyFinanceBankBranchComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, component: fixture.componentInstance };
};

type Fixture = ComponentFixture<StudyFinanceBankBranchComponent>;

/** The bank ids the dialog's own bank select is bound to. */
const dialogBanks = (fixture: Fixture): number[] => {
    fixture.detectChanges();
    const select = fixture.debugElement.query(By.css('p-select#studyFinanceBankeId'));
    return (select.componentInstance.options as { id: number }[]).map(o => o.id);
};

describe('the bank list in the branch dialog', () => {

    it('before a country is chosen, offers the banks of the countries the account may write in', async () => {
        const { fixture, component } = await build();

        component.openCreateDialog();

        expect(dialogBanks(fixture)).toEqual([1003, 1004, 1000]);
    });

    it('once a country is chosen, offers only its banks', async () => {
        const { fixture, component } = await build();
        component.openCreateDialog();

        component.branchForm.get('studyCountryInId')!.setValue(SAUDI);
        component.onCountryChange();

        expect(dialogBanks(fixture)).toEqual([1000]);
    });

    it('a bank chosen first fills in its country, as the server would', async () => {
        const { fixture, component } = await build();
        component.openCreateDialog();

        component.branchForm.get('studyFinanceBankeId')!.setValue(1004);
        component.onBankChange();

        expect(component.branchForm.get('studyCountryInId')!.value).toBe(JORDAN);
        expect(dialogBanks(fixture)).toEqual([1003, 1004]);
        expect(component.studyGovernments.map(g => g.id)).withContext("the country's governments").toEqual([5]);
        expect(component.branchForm.get('studyFinanceBankeId')!.value).toBe(1004);
    });

    it('changing the country drops a bank of the previous one', async () => {
        const { component } = await build();
        component.openCreateDialog();
        component.branchForm.patchValue({ studyCountryInId: JORDAN, studyFinanceBankeId: 1004 });
        component.onCountryChange();

        component.branchForm.get('studyCountryInId')!.setValue(SAUDI);
        component.onCountryChange();

        expect(component.branchForm.get('studyFinanceBankeId')!.value)
            .withContext('Arab Bank is Jordanian; saving it under Saudi Arabia is refused')
            .toBeNull();
    });

    it("editing a branch offers its country's banks and keeps its bank", async () => {
        const { fixture, component } = await build();

        component.openEditDialog(component.branches[0]);

        expect(dialogBanks(fixture)).toEqual([1003, 1004]);
        expect(component.branchForm.get('studyFinanceBankeId')!.value).toBe(1004);
    });

    it('the filter bar still lists every bank the page shows', async () => {
        const { component } = await build();

        expect(component.bankOptions.map(b => b.id)).toEqual([1003, 1004, 1000, 1]);
    });
});
