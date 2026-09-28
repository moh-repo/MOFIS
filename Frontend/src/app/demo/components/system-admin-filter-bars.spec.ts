import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { SystemAdministrationModule } from './system-administration/system-administration.module';
import { DocumentTypesComponent } from './system-administration/document-types/document-types.component';
import { StudyFinanceBankComponent } from './system-administration/study-finance-bank/study-finance-bank.component';
import { StudyFinanceBankBranchComponent } from './system-administration/study-finance-bank-branch/study-finance-bank-branch.component';
import { StudyWafarTypeComponent } from './system-administration/study-wafar-type/study-wafar-type.component';

import { DocumentTypeService } from '../service/document-type.service';
import { StudyFinanceBankService } from '../service/study-finance-bank.service';
import { StudyFinanceBankBranchService } from '../service/study-finance-bank-branch.service';
import { StudyWafarTypeService } from '../service/study-wafar-type.service';
import { LookupService } from '../service/lookup.service';
import { TranslationService } from '../service/translation.service';
import { UmsAuthService } from '../service/ums-auth.service';

/**
 * The filter rows across the System administration pages.
 *
 * <p>Four pages off one copied template, and none of them had a filter row:
 * each kept a search box in the table's caption, in a
 * `justify-content-between` row with the record count — the double mirror
 * of styles.scss:163 once more, under a `dir` root. On document types the
 * search did nothing at all: the `#dt` template reference sat on the input,
 * and `dt.filterGlobal` was being called on an input element.
 *
 * <p>The layout itself is covered once, centrally, in
 * src/app/filter-bar-shared.spec.ts. This file pins that each page uses it,
 * and — because that is what was broken — that typing in the search box
 * narrows the table.
 */

const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

interface Case {
    name: string;
    component: any;
    /** How many filter controls the page offers. */
    fields: number;
    /** How many buttons sit in the action row. */
    actions: number;
}

const CASES: Case[] = [
    { name: 'document types', component: DocumentTypesComponent, fields: 2, actions: 1 },
    { name: 'finance banks', component: StudyFinanceBankComponent, fields: 2, actions: 1 },
    { name: 'finance bank branches', component: StudyFinanceBankBranchComponent, fields: 3, actions: 1 },
    { name: 'wafar types', component: StudyWafarTypeComponent, fields: 1, actions: 1 }
];

const providers = [
    MessageService,
    ConfirmationService,
    { provide: DocumentTypeService, useValue: quiet() },
    { provide: StudyFinanceBankService, useValue: quiet() },
    { provide: StudyFinanceBankBranchService, useValue: quiet() },
    { provide: StudyWafarTypeService, useValue: quiet() },
    { provide: LookupService, useValue: quiet() },
    { provide: TranslationService, useValue: translation },
    {
        provide: UmsAuthService,
        // profile$ and currentUser$ are named explicitly: AuthService projects the shell
        // user from them in its constructor, and the proxy's fallback hands back a
        // function, which has no subscribe.
        useValue: new Proxy({ permissions$: of([]), profile$: of(), currentUser$: of(null) } as any,
            { get: (t, p) => p in t ? t[p] : () => true })
    }
];

CASES.forEach(({ name, component, fields, actions }) => {

    describe(`filter bar on ${name}`, () => {

        let fixture: any;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [SystemAdministrationModule, RouterModule.forRoot([])],
                providers
            }).compileComponents();

            fixture = TestBed.createComponent(component);
            fixture.detectChanges();
            await fixture.whenStable();

            document.body.appendChild(fixture.nativeElement);
            fixture.detectChanges();
        });

        afterEach(() => fixture.nativeElement.remove());

        const el = (selector: string) =>
            fixture.nativeElement.querySelector(selector) as HTMLElement;
        const all = (selector: string) =>
            Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];

        it('uses the shared filter bar, and the table has no caption', () => {
            expect(el('.filter-bar')).withContext('no shared filter bar on the page').toBeTruthy();
            expect(el('.p-datatable-header')).withContext('the caption row is still there').toBeNull();
            expect(all('.filter-bar__field').length).toBe(fields);
        });

        it('declares a column count that its fields divide into', () => {
            const grid = el('.filter-bar__grid');
            const wide = all('.filter-bar__field--wide').length;
            const declared = Array.from(grid.classList)
                .map(c => /^filter-bar__grid--cols-(\d+)$/.exec(c))
                .find(Boolean);

            expect(declared).withContext(`no cols-N on: ${grid.className}`).toBeTruthy();
            expect(Number(declared![1])).toBe(fields + wide);
        });

        it('labels every control, bound to it', () => {
            all('.filter-bar__field').forEach((field, i) => {
                const label = field.querySelector('label') as HTMLLabelElement;
                expect(label).withContext(`field ${i} has no label`).toBeTruthy();
                expect(label.htmlFor).withContext(`field ${i} label is not bound`).not.toBe('');
                expect(field.querySelector(`#${CSS.escape(label.htmlFor)}`))
                    .withContext(`field ${i} label points at nothing in its own field`)
                    .toBeTruthy();
            });
        });

        it('keeps the buttons out of the field flow', () => {
            const bar = el('.filter-bar__actions');

            expect(bar).withContext('no action row').toBeTruthy();
            expect(bar.querySelectorAll('button').length).toBe(actions);
            expect(el('.filter-bar__grid').contains(bar))
                .withContext('the buttons are back inside the field grid').toBeFalse();
        });

        it('lets every control fill its column instead of a fixed width', () => {
            all('.filter-bar__field input, .filter-bar__field p-select').forEach((control, i) => {
                const inline = control.getAttribute('style') ?? '';
                expect(/width/.test(inline))
                    .withContext(`control ${i} still carries an inline width: ${inline}`)
                    .toBeFalse();

                const cls = control.className ?? '';
                expect(/\bw-\d/.test(cls))
                    .withContext(`control ${i} still carries a width class: ${cls}`)
                    .toBeFalse();
            });
        });
    });
});

describe('the search on document types', () => {

    let fixture: any;
    let component: DocumentTypesComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SystemAdministrationModule, RouterModule.forRoot([])],
            providers
        }).compileComponents();

        fixture = TestBed.createComponent(DocumentTypesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        document.body.appendChild(fixture.nativeElement);
    });

    afterEach(() => fixture.nativeElement.remove());

    const rows = () => fixture.nativeElement.querySelectorAll('tbody tr').length;

    it('narrows the table as it is typed', fakeAsync(() => {
        // `#dt` used to sit on the <input>, so `dt.filterGlobal` was called on
        // an input element and typing changed nothing.
        component.documentTypes = [
            { documentTypeId: 1, documentTypeName: 'Passport', isActive: true } as any,
            { documentTypeId: 2, documentTypeName: 'Transcript', isActive: true } as any
        ];
        fixture.detectChanges();
        expect(rows()).toBe(2);

        const input = fixture.nativeElement.querySelector('#dtSearch') as HTMLInputElement;
        input.value = 'pass';
        input.dispatchEvent(new Event('input'));
        // PrimeNG applies a global filter after its filterDelay.
        tick(400);
        fixture.detectChanges();

        expect(rows()).withContext('one row should match "pass"').toBe(1);
        expect(component.hasFilters).toBeTrue();
    }));
});
