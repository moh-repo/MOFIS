import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { ApprovalsModule } from './approvals/approvals.module';
import { PendingApprovalsComponent } from './approvals/pending-approvals/pending-approvals.component';
import { ApprovalHistoryComponent } from './approvals/approval-history/approval-history.component';
import { MyRequestsComponent } from './approvals/my-requests/my-requests.component';
import { ApprovalConfigsComponent } from './approvals/approval-configs/approval-configs.component';
import { StudentInfoRequestsModule } from './student-info-requests/student-info-requests.module';
import { StudentInfoRequestsComponent } from './student-info-requests/student-info-requests.component';
import { StudentChangeRequestsModule } from './student-change-requests/student-change-requests.module';
import { StudentChangeRequestsComponent } from './student-change-requests/student-change-requests.component';

import { ApprovalService } from '../service/approval.service';
import { StudentInfoRequestService } from '../service/student-info-request.service';
import { StudentdataService } from '../service/studentdata.service';
import { StudentTravelTicketService } from '../service/student-travel-ticket.service';
import { EnumOptionsService } from '../service/enum-options.service';
import { LookupService } from '../service/lookup.service';
import { TranslationService } from '../service/translation.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { AuthService } from '../service/auth.service';

/**
 * The filter rows across the Approvals and Requests menu.
 *
 * <p>Five of its six pages had a filter row and all five were built by hand.
 * Three of them (pending, history, my requests) were literally the same markup
 * copied about: a wrapping flex row, each control carrying its own rem width
 * (w-12rem, w-14rem), no labels at all — the placeholder did that job — and the
 * two buttons as further items in the same wrapping row, so they landed
 * wherever there happened to be space. Student info requests put its three
 * boxes in a span beside the Create button with their labels as loose text
 * nodes; student change requests used inline pixel widths of 160, 200 and 130.
 *
 * <p>The layout itself is covered once, centrally, in
 * src/app/filter-bar-shared.spec.ts. What this file pins is that each page
 * actually uses it, and the two properties that were wrong on every one of
 * them: every control labelled, and the buttons out of the field flow.
 *
 * <p>The sixth page, approval rules (/approvals/configs), had no filter row at
 * all: a list of configurations with an editor dialog, which was tolerable at
 * five workflows and is not at twelve. It has one now, and narrows the list in
 * hand rather than asking the server — the controller offers no filtering.
 */

/** Any method returns an empty observable; enough to get a page rendered. */
const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

const page = () => of({ content: [], totalElements: 0 });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

const commonProviders = [
    MessageService,
    ConfirmationService,
    { provide: HttpClient, useValue: { get: () => page(), post: () => of({}), put: () => of({}) } },
    { provide: ApprovalService, useValue: new Proxy({} as any, { get: () => () => page() }) },
    { provide: StudentInfoRequestService, useValue: new Proxy({} as any, { get: () => () => page() }) },
    { provide: StudentdataService, useValue: { getPagingAll: () => page(), getAll: () => of([]), getPendingStudentIds: () => of([]) } },
    { provide: StudentTravelTicketService, useValue: quiet() },
    { provide: EnumOptionsService, useValue: quiet() },
    { provide: LookupService, useValue: quiet() },
    { provide: UmsAuthService, useValue: { getCurrentUser: () => ({ id: 1, username: 'tester' }) } },
    { provide: AuthService, useValue: { getCurrentUser: () => ({ id: 1 }) } },
    {
        // A permissive proxy rather than a fixed set of methods: these pages
        // ask several different questions of the permission service and the
        // list differs per page, which is noise for a layout test.
        provide: UmsPermissionService,
        useValue: new Proxy({} as any, {
            get: (_t, prop) => prop === 'getAllowedCountryIds' ? () => [] : () => true
        })
    },
    { provide: TranslationService, useValue: translation }
];

interface Case {
    name: string;
    component: any;
    imports: any[];
    /** How many filter controls the page offers. */
    fields: number;
    /** How many buttons sit in the action row. */
    actions: number;
}

const CASES: Case[] = [
    {
        name: 'pending approvals', component: PendingApprovalsComponent,
        imports: [ApprovalsModule, RouterModule.forRoot([])], fields: 2, actions: 2
    },
    {
        name: 'approval history', component: ApprovalHistoryComponent,
        imports: [ApprovalsModule, RouterModule.forRoot([])], fields: 3, actions: 2
    },
    {
        name: 'my requests', component: MyRequestsComponent,
        imports: [ApprovalsModule, RouterModule.forRoot([])], fields: 3, actions: 2
    },
    {
        name: 'student info requests', component: StudentInfoRequestsComponent,
        // Was the only page of the six with no Clear button beside its Search.
        imports: [StudentInfoRequestsModule, RouterModule.forRoot([])], fields: 3, actions: 2
    },
    {
        name: 'student change requests', component: StudentChangeRequestsComponent,
        imports: [StudentChangeRequestsModule, RouterModule.forRoot([])], fields: 3, actions: 2
    },
    {
        // Filtering is client-side here, so there is nothing for a Search
        // button to trigger: the table narrows as the boxes change.
        name: 'approval configs', component: ApprovalConfigsComponent,
        imports: [ApprovalsModule, RouterModule.forRoot([])], fields: 3, actions: 1
    }
];

CASES.forEach(({ name, component, imports, fields, actions }) => {

    describe(`filter bar on ${name}`, () => {

        let fixture: any;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports,
                providers: commonProviders
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
            // Never auto-fit: it picks a count from the available width and
            // strands whatever does not divide into it.
            const grid = el('.filter-bar__grid');
            const wide = all('.filter-bar__field--wide').length;
            const declared = Array.from(grid.classList)
                .map(c => /^filter-bar__grid--cols-(\d+)$/.exec(c))
                .find(Boolean);

            expect(declared).withContext(`no cols-N on: ${grid.className}`).toBeTruthy();
            expect(Number(declared![1])).toBe(fields + wide);
        });

        it('labels every control, bound to it', () => {
            // None of these pages had labels; the placeholder was doing the
            // job, which disappears the moment anything is typed.
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
