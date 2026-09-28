import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService, ToastMessageOptions } from 'primeng/api';
import { of, throwError } from 'rxjs';

import { SystemAdministrationModule } from '../system-administration.module';
import { StudyFinanceBankBranchComponent } from './study-finance-bank-branch.component';
import { StudyFinanceBankBranchService } from '../../../service/study-finance-bank-branch.service';
import { StudyFinanceBankService } from '../../../service/study-finance-bank.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';

/**
 * A refused save or delete says why, in one toast.
 *
 * <p>It said "Failed to save branch" whatever the reason, with the interceptor's
 * own toast stacked on top of it. The server words most refusals for the reader,
 * in their language (a duplicate name, a branch still in use); those are shown.
 * Its access-policy refusal is English only, so the page words that one itself.
 * Anything without a structured body (offline, a proxy's page) keeps the page's
 * own words: a proxy's page names hosts and paths.
 */

const MESSAGES = 'SYSTEM_ADMIN.STUDY_FINANCE_BANK_BRANCH.MESSAGES';

const BRANCH = {
    id: 20, studyFinanceBankBranchName: 'فرع', studyFinanceBankBranchNameEn: 'Branch',
    studyFinanceBankeId: 1004, studyCountryInId: 1004, studyGovInId: null, studyFinanceBankBranchMidate: ''
};

const refusal = (status: number, error: unknown) => throwError(() => new HttpErrorResponse({ status, error }));

const build = async (failure: ReturnType<typeof refusal>) => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [SystemAdministrationModule, RouterModule.forRoot([])],
        providers: [
            provideNoopAnimations(),
            MessageService,
            ConfirmationService,
            { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } },
            {
                provide: StudyFinanceBankBranchService,
                useValue: { getAll: () => of([BRANCH]), create: () => failure, update: () => failure, delete: () => failure }
            },
            { provide: StudyFinanceBankService, useValue: { getAll: () => of([]) } },
            {
                provide: LookupService,
                useValue: {
                    getAllStudyCountries: () => of([]), getAllStudyGovernments: () => of([]),
                    getStudyGovernmentsByCountry: () => of([])
                }
            },
            {
                provide: TranslationService,
                useValue: {
                    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
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
    // The page provides its own MessageService; the toasts go there.
    const toasts = spyOn(fixture.debugElement.injector.get(MessageService), 'add');
    return { component: fixture.componentInstance, toasts };
};

type Component = ComponentFixture<StudyFinanceBankBranchComponent>['componentInstance'];

const save = (component: Component) => {
    component.openEditDialog(component.branches[0]);
    component.saveBranch();
};

/** Delete without the confirmation dialog: the prompt is not what is under test. */
const remove = (component: Component) =>
    (component as unknown as { deleteBranch(b: unknown): void }).deleteBranch(component.branches[0]);

const only = (toasts: jasmine.Spy): ToastMessageOptions => {
    expect(toasts.calls.count()).withContext('one toast per failure').toBe(1);
    return toasts.calls.mostRecent().args[0];
};

describe('a refused branch save or delete', () => {

    it("shows the server's reason for a duplicate name, in the reader's language", async () => {
        const { component, toasts } = await build(refusal(409,
            { message: 'A row named "فرع" already exists in this list.', details: 'uri=/mohe/api/v1/studyFinanceBankBranch/20' }));

        save(component);

        const toast = only(toasts);
        expect(toast.summary).toBe(`${MESSAGES}.SAVE_ERROR`);
        expect(toast.detail).toBe('A row named "فرع" already exists in this list.');
    });

    it("shows the server's reason for a branch still in use", async () => {
        const { component, toasts } = await build(refusal(409,
            { message: 'This record is still used by other records, so it cannot be removed. Switch it off instead.' }));

        remove(component);

        const toast = only(toasts);
        expect(toast.summary).toBe(`${MESSAGES}.DELETE_ERROR`);
        expect(toast.detail).toContain('still used by other records');
    });

    it('words an access refusal itself: the server sends that one in English only', async () => {
        const { component, toasts } = await build(refusal(403,
            { message: "An attaché's bank branch is changed by the attaché; ministry accounts may only view it." }));

        save(component);

        const toast = only(toasts);
        expect(toast.summary).toBe(`${MESSAGES}.SAVE_ERROR`);
        expect(toast.detail).toBe(`${MESSAGES}.FORBIDDEN`);
    });

    it("never echoes a proxy's page", async () => {
        const { component, toasts } = await build(refusal(400,
            'Error occurred while trying to proxy: localhost:4200/mohe/api/v1/studyFinanceBankBranch/20'));

        save(component);

        const toast = only(toasts);
        expect(toast.summary).toBe('COMMON.ERROR');
        expect(toast.detail).toBe(`${MESSAGES}.SAVE_ERROR`);
    });

    it("keeps the page's own words when the server cannot be reached", async () => {
        const { component, toasts } = await build(refusal(0, new ProgressEvent('error')));

        remove(component);

        const toast = only(toasts);
        expect(toast.summary).toBe('COMMON.ERROR');
        expect(toast.detail).toBe(`${MESSAGES}.DELETE_ERROR`);
    });

    it("keeps the page's own words for a refusal with no body", async () => {
        const { component, toasts } = await build(refusal(409, null));

        save(component);

        // Angular's own text ("Http failure response for …") is not a reason.
        expect(only(toasts).detail).toBe(`${MESSAGES}.SAVE_ERROR`);
    });
});
