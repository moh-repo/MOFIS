import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { SettingsModule } from './settings.module';
import { LookupTablesModule } from './settings/lookuptables/lookuptables.module';
import { LookupTablesComponent } from './settings/lookuptables/lookuptables.component';
import { CodeListsComponent } from './settings/code-lists/code-lists.component';
import { SysParametersComponent } from './settings/sys-parameters/sys-parameters.component';
import { StudyYearsLookupComponent } from './study-years-lookup/study-years-lookup.component';
import { StudyAmountLookupComponent } from './study-amount-lookup/study-amount-lookup.component';

import { LookupTableService } from '../service/lookuptable.service';
import { LookupService } from '../service/lookup.service';
import { CodeListService } from '../service/code-list.service';
import { SysParameterService } from '../service/sys-parameter.service';
import { StudyYearsLookupApiService } from '../service/study-years-lookup.service';
import { StudyAmountLookupApiService } from '../service/study-amount-lookup.service';
import { TranslationService } from '../service/translation.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { codeListStub } from '../../testing/code-list.stub';

/**
 * The filter rows across the Settings list pages.
 *
 * <p>Lookup tables kept its search box in a `justify-content-between` row
 * beside New and Delete selected — the double mirror of styles.scss:163, and
 * "narrow the list" beside "change the data". Code lists had a list picker in
 * the header and nothing to search the chosen list with. System parameters,
 * study years and study amounts had no filter at all; the last two hold ids
 * and look their labels up, so they narrow by the lists they are keyed on
 * rather than by a text search that would find nothing.
 *
 * <p>The layout itself is covered once, centrally, in
 * src/app/filter-bar-shared.spec.ts. This file pins that each page uses it.
 * Lookup tables renders its table template into six tabs, so its bar can be
 * in the DOM six times; every check here reads the first.
 */

const quiet = () => new Proxy({} as any, { get: () => () => of([]) });
const page = () => of({ content: [], totalElements: 0 });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

interface Case {
    name: string;
    component: any;
    imports: any[];
    providers?: any[];
    /** How many filter controls the page offers. */
    fields: number;
    /** How many buttons sit in the action row. */
    actions: number;
}

/** The code-list service stub, plus the one call the code-lists page makes on load. */
function withNames(service: CodeListService): CodeListService {
    (service as any).getNames = () => of([]);
    return service;
}

const CASES: Case[] = [
    {
        name: 'lookup tables', component: LookupTablesComponent,
        imports: [LookupTablesModule, RouterModule.forRoot([])], fields: 1, actions: 1
    },
    {
        name: 'code lists', component: CodeListsComponent,
        imports: [SettingsModule, RouterModule.forRoot([])],
        providers: [{ provide: CodeListService, useValue: withNames(codeListStub()) }],
        fields: 2, actions: 1
    },
    { name: 'system parameters', component: SysParametersComponent, imports: [SettingsModule, RouterModule.forRoot([])], fields: 2, actions: 1 },
    { name: 'study years', component: StudyYearsLookupComponent, imports: [SettingsModule, RouterModule.forRoot([])], fields: 2, actions: 1 },
    { name: 'study amounts', component: StudyAmountLookupComponent, imports: [SettingsModule, RouterModule.forRoot([])], fields: 2, actions: 1 }
];

CASES.forEach(({ name, component, imports, providers, fields, actions }) => {

    describe(`filter bar on ${name}`, () => {

        let fixture: any;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports,
                providers: [
                    MessageService,
                    ConfirmationService,
                    { provide: HttpClient, useValue: { get: () => page(), post: () => of({}), put: () => of({}), delete: () => of({}) } },
                    { provide: LookupTableService, useValue: quiet() },
                    { provide: LookupService, useValue: quiet() },
                    { provide: CodeListService, useValue: codeListStub() },
                    { provide: SysParameterService, useValue: quiet() },
                    { provide: StudyYearsLookupApiService, useValue: quiet() },
                    { provide: StudyAmountLookupApiService, useValue: quiet() },
                    { provide: TranslationService, useValue: translation },
                    { provide: UmsAuthService, useValue: new Proxy({ permissions$: of([]) } as any, { get: (t, p) => p in t ? t[p] : () => true }) },
                    { provide: UmsPermissionService, useValue: new Proxy({} as any, { get: (_t, prop) => prop === 'getAllowedCountryIds' ? () => [] : () => true }) },
                    ...(providers ?? [])
                ]
            }).compileComponents();

            fixture = TestBed.createComponent(component);
            fixture.detectChanges();
            await fixture.whenStable();

            document.body.appendChild(fixture.nativeElement);
            fixture.detectChanges();
        });

        afterEach(() => fixture.nativeElement.remove());

        /** The first bar on the page, and everything inside it. */
        const bar = () => fixture.nativeElement.querySelector('.filter-bar') as HTMLElement;
        const el = (selector: string) => bar().querySelector(selector) as HTMLElement;
        const all = (selector: string) => Array.from(bar().querySelectorAll(selector)) as HTMLElement[];

        it('uses the shared filter bar', () => {
            expect(bar()).withContext('no shared filter bar on the page').toBeTruthy();
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
            const actionsRow = el('.filter-bar__actions');

            expect(actionsRow).withContext('no action row').toBeTruthy();
            expect(actionsRow.querySelectorAll('button').length).toBe(actions);
            expect(el('.filter-bar__grid').contains(actionsRow))
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

        it('keeps the data-changing buttons out of the bar', () => {
            // New and Delete selected used to sit beside the search box on
            // lookup tables; Add beside the list picker on code lists. Both
            // belong to the header, where the page's own actions live.
            const text = (b: Element) => (b.textContent ?? '').trim();
            all('button').forEach(b => {
                expect(['COMMON.NEW', 'COMMON.ADD', 'COMMON.DELETE_SELECTED'])
                    .withContext(`"${text(b)}" is a data action, not a filter action`)
                    .not.toContain(text(b));
            });
        });
    });
});
