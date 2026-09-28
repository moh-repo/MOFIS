import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';

import { StudentAdminModule } from './student-admin/student-admin.module';
import { StudentUsersComponent } from './student-admin/student-users/student-users.component';
import { StudentRolesComponent } from './student-admin/student-roles/student-roles.component';
import { StudentGroupsComponent } from './student-admin/student-groups/student-groups.component';
import { StudentPermissionsComponent } from './student-admin/student-permissions/student-permissions.component';
import { StudentSessionsComponent } from './student-admin/student-sessions/student-sessions.component';

import { LookupService } from '../service/lookup.service';
import { TranslationService } from '../service/translation.service';

/**
 * The filter rows across the Student portal access menu.
 *
 * <p>Two of its five pages had a filter row, both built by hand: users put a
 * search box at `min-width: 240px` and a status list at `160px` in a wrapping
 * flex row with the Search and Refresh buttons as further items in it;
 * sessions put a number box at `160px` beside a toggle, with a clear button
 * that appeared and disappeared. Roles, groups and permissions had no search
 * at all — a list of twenty roles was read top to bottom.
 *
 * <p>The layout itself is covered once, centrally, in
 * src/app/filter-bar-shared.spec.ts. What this file pins is that each page
 * actually uses it, and the properties that were wrong or missing on every
 * one of them: every control labelled, and the buttons out of the field flow.
 */

const page = () => of({ content: [], totalElements: 0 });

/** A paged GET, for users and sessions. */
const pagedHttp = { get: () => page(), post: () => of({}), put: () => of({}), delete: () => of({}) };

/** A list GET, for the pages that hand the response straight to the table. */
const listHttp = { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) };

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

interface Case {
    name: string;
    component: any;
    http: object;
    /** How many filter controls the page offers. */
    fields: number;
    /** How many buttons sit in the action row. */
    actions: number;
}

const CASES: Case[] = [
    { name: 'student users', component: StudentUsersComponent, http: pagedHttp, fields: 2, actions: 2 },
    { name: 'student sessions', component: StudentSessionsComponent, http: pagedHttp, fields: 2, actions: 2 },
    { name: 'student roles', component: StudentRolesComponent, http: listHttp, fields: 2, actions: 1 },
    { name: 'student groups', component: StudentGroupsComponent, http: listHttp, fields: 2, actions: 1 },
    { name: 'student permissions', component: StudentPermissionsComponent, http: listHttp, fields: 2, actions: 1 }
];

CASES.forEach(({ name, component, http, fields, actions }) => {

    describe(`filter bar on ${name}`, () => {

        let fixture: any;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [StudentAdminModule, RouterModule.forRoot([])],
                providers: [
                    { provide: HttpClient, useValue: http },
                    { provide: LookupService, useValue: { getStudentPortalAccountStatuses: () => of([]) } },
                    { provide: TranslationService, useValue: translation }
                ]
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

        it('uses the shared filter bar', () => {
            expect(el('.filter-bar')).withContext('no shared filter bar on the page').toBeTruthy();
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
            all('.filter-bar__field input, .filter-bar__field p-select, .filter-bar__field p-inputnumber').forEach((control, i) => {
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

        it('keeps the page header clear of filters', () => {
            // Refresh used to sit in the users filter row; the header is where
            // the page's own actions live, and the bar is for narrowing.
            const header = el('.card > .flex');
            expect(header.querySelector('.filter-bar')).toBeNull();
            expect(header.querySelector('input, p-select')).toBeNull();
        });
    });
});
