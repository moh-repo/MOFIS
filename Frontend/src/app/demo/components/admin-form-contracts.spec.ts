import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';

import { SystemAdministrationModule } from './system-administration/system-administration.module';
import { SettingsModule } from './settings.module';
import { StudyWafarTypeComponent } from './system-administration/study-wafar-type/study-wafar-type.component';
import { StudyFinanceBankComponent } from './system-administration/study-finance-bank/study-finance-bank.component';
import { StudyFinanceBankBranchComponent } from './system-administration/study-finance-bank-branch/study-finance-bank-branch.component';
import { StudyYearsLookupComponent } from './study-years-lookup/study-years-lookup.component';
import { StudyAmountLookupComponent } from './study-amount-lookup/study-amount-lookup.component';

import { StudyWafarTypeService } from '../service/study-wafar-type.service';
import { StudyFinanceBankService } from '../service/study-finance-bank.service';
import { StudyFinanceBankBranchService } from '../service/study-finance-bank-branch.service';
import { StudyYearsLookupApiService } from '../service/study-years-lookup.service';
import { StudyAmountLookupApiService } from '../service/study-amount-lookup.service';
import { LookupService } from '../service/lookup.service';
import { TranslationService } from '../service/translation.service';

/**
 * What these dialogs send, against what the server will accept.
 *
 * <p>Three of them sent a body the server could not use as they assumed:
 *
 * <ul>
 *   <li><b>Wafar types</b> posted {@code id} and {@code swafarMidate}, neither of
 *   which is on {@code StudyWafarTypeRequest}, so both were dropped without a
 *   word — the MI-date picker on the dialog could never store anything — and the
 *   English name was never sent, while the server applied it unconditionally, so
 *   every save cleared {@code WAFAR_NAME_EN}.</li>
 *   <li><b>Banks and branches</b> did the same to their own English names, and
 *   carried MI-date pickers over columns mapped
 *   {@code insertable = false, updatable = false}.</li>
 *   <li><b>Study years and study amounts</b> chose create over update when the
 *   row had no id, so an edit in that state silently duplicated the row.</li>
 * </ul>
 *
 * <p>These check the payload rather than the rendering: what was wrong was the
 * shape of the request, and a screenshot would not have shown it.
 */

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

/** Captures what a component hands its service. */
const recorder = () => {
    const calls: { verb: string; id?: any; body: any }[] = [];
    return {
        calls,
        service: {
            getAll: () => of([]),
            create: (body: any) => { calls.push({ verb: 'create', body }); return of({ ...body, id: 1 }); },
            update: (id: any, body: any) => { calls.push({ verb: 'update', id, body }); return of({ ...body, id }); },
            delete: () => of({})
        }
    };
};

const build = async (component: any, providers: any[], module: any = SystemAdministrationModule) => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [module, RouterModule.forRoot([])],
        providers: [
            provideNoopAnimations(),
            MessageService,
            ConfirmationService,
            { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } },
            { provide: LookupService, useValue: quiet() },
            { provide: TranslationService, useValue: translation },
            ...providers
        ]
    }).compileComponents();
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.componentInstance as any;
};

describe('what the wafar type dialog sends', () => {

    it('sends the names the request DTO carries, and nothing it does not', async () => {
        const { calls, service } = recorder();
        const component = await build(StudyWafarTypeComponent,
            [{ provide: StudyWafarTypeService, useValue: service }]);

        component.openCreateDialog();
        component.wafarTypeForm.patchValue({ wafarName: 'وفار', wafarNameEn: 'Wafar', wafarNote1: 'note' });
        component.saveWafarType();

        expect(calls.length).withContext('nothing was sent').toBe(1);
        const body = calls[0].body;
        expect(body.wafarNameEn)
            .withContext('the English name was left out, and the server clears what it is not sent')
            .toBe('Wafar');
        expect(Object.keys(body))
            .withContext('swafarMidate and id are not on StudyWafarTypeRequest and were dropped in silence')
            .not.toContain('swafarMidate');
        expect(Object.keys(body)).not.toContain('id');
    });

    it('opens the create dialog on the controls the form actually has', async () => {
        const { service } = recorder();
        const component = await build(StudyWafarTypeComponent,
            [{ provide: StudyWafarTypeService, useValue: service }]);

        component.openCreateDialog();

        // The reset used to name four controls this form does not define, so
        // none of its defaults were ever applied.
        expect(Object.keys(component.wafarTypeForm.controls).sort())
            .toEqual(['id', 'wafarName', 'wafarNameEn', 'wafarNote1']);
        expect(component.wafarTypeForm.get('wafarName')!.value).toBe('');
    });
});

describe('what the banking dialogs send', () => {

    it('a bank keeps its English name and does not try to set the MI date', async () => {
        const { calls, service } = recorder();
        const component = await build(StudyFinanceBankComponent,
            [{ provide: StudyFinanceBankService, useValue: service }]);

        component.openCreateDialog();
        component.bankForm.patchValue({
            studyFinanceBankeName: 'بنك', studyFinanceBankeNameEn: 'Bank', studyCountryInId: 1
        });
        component.saveBank();

        expect(calls.length).toBe(1);
        expect(calls[0].body.studyFinanceBankeNameEn).toBe('Bank');
        expect(Object.keys(calls[0].body))
            .withContext('the column is insertable = false, updatable = false')
            .not.toContain('studyFinanceBankMidate');
    });

    it('a branch keeps its English name and does not try to set the MI date', async () => {
        const { calls, service } = recorder();
        const component = await build(StudyFinanceBankBranchComponent,
            [{ provide: StudyFinanceBankBranchService, useValue: service },
             { provide: StudyFinanceBankService, useValue: recorder().service }]);

        component.openCreateDialog();
        component.branchForm.patchValue({
            studyFinanceBankBranchName: 'فرع', studyFinanceBankBranchNameEn: 'Branch',
            studyFinanceBankeId: 1, studyCountryInId: 1
        });
        component.saveBranch();

        expect(calls.length).toBe(1);
        expect(calls[0].body.studyFinanceBankBranchNameEn).toBe('Branch');
        expect(Object.keys(calls[0].body)).not.toContain('studyFinanceBankBranchMidate');
    });
});

describe('an edit with no id', () => {

    ([['study years', StudyYearsLookupComponent, StudyYearsLookupApiService,
       { studentDegreeId: 1, studentMajorId: 2, studyYears: 4 }],
      ['study amounts', StudyAmountLookupComponent, StudyAmountLookupApiService,
       { studentDegreeId: 1, studentAgencyTypeId: 2, amount: 500 }]] as Array<[string, any, any, any]>)
        .forEach(([name, component, serviceToken, fields]) => {

            it(`${name}: is refused rather than saved as a new row`, async () => {
                const { calls, service } = recorder();
                const instance = await build(component, [{ provide: serviceToken, useValue: service }], SettingsModule);

                // The broken state: editing something that has no id.
                instance.editMode = true;
                instance.current = { ...fields };
                instance.save();

                expect(calls)
                    .withContext('the edit fell through to create and duplicated the row')
                    .toEqual([]);
            });

            it(`${name}: still updates when the id is there`, async () => {
                // The positive control, so the guard is not simply a wall.
                const { calls, service } = recorder();
                const instance = await build(component, [{ provide: serviceToken, useValue: service }], SettingsModule);

                instance.editMode = true;
                instance.current = { id: 42, ...fields };
                instance.save();

                expect(calls.map(c => c.verb)).toEqual(['update']);
                expect(calls[0].id).toBe(42);
            });
        });
});
