import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { ApprovalsModule } from './approvals/approvals.module';
import { ApprovalConfigsComponent } from './approvals/approval-configs/approval-configs.component';
import { PendingApprovalsComponent } from './approvals/pending-approvals/pending-approvals.component';
import { ApprovalHistoryComponent } from './approvals/approval-history/approval-history.component';
import { MyRequestsComponent } from './approvals/my-requests/my-requests.component';

import { ApprovalService } from '../service/approval.service';
import { StudentdataService } from '../service/studentdata.service';
import { StudentTravelTicketService } from '../service/student-travel-ticket.service';
import { EnumOptionsService } from '../service/enum-options.service';
import { LookupService } from '../service/lookup.service';
import { TranslationService } from '../service/translation.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { UmsAuthService } from '../service/ums-auth.service';
import { AuthService } from '../service/auth.service';

/**
 * The dialogs on the Approvals menu, and the design the three detail dialogs
 * share.
 *
 * <p>Approval rules (/approvals/configs) had two editor dialogs built as a bare
 * `p-fluid` column of `.field` blocks, with the required mark written into the
 * label as a literal asterisk and **Save disabled until the form was valid** —
 * which says that something is wrong without saying what, and leaves a dead
 * button as the only signal. Three of its strings were hard-coded English
 * (`e.g., STUDENT_DATA, LOOKUP_TABLE`, `e.g., Manager Approval, Director
 * Approval`, `Select required role`) and rendered untranslated in Arabic, and a
 * level's approving role was shown as a bare id.
 *
 * <p>The other three pages share one detail dialog design — the level track, the
 * info cards, the action timeline — and each carried the whole of it in its own
 * inline `styles` block: 43 rules, byte-identical across the three files. They
 * are in assets/layout/styles/_approval-detail.scss once now, which is also
 * where a p-dialog's own chrome has to be styled from: its content renders
 * outside the component that declares it, which is why the three copies needed
 * `::ng-deep` to reach their own dialog.
 */

const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

/**
 * An empty list that is also an empty page. These pages ask one service for a
 * page of requests and another for a plain list, and hand the first straight to
 * `.content` and the second straight to a table's `[value]`; a value that is
 * both keeps every call on the happy path. See page-headings.spec.ts.
 */
const page = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, getCurrentLanguage: () => 'en',
    translate: (key: string) => key.split('.').pop()!.replace(/_/g, ' ')
};

const providers = [
    MessageService,
    ConfirmationService,
    { provide: HttpClient, useValue: { get: () => page(), post: () => of({}), put: () => of({}), delete: () => of({}) } },
    { provide: ApprovalService, useValue: new Proxy({} as any, { get: () => () => page() }) },
    { provide: StudentdataService, useValue: { getPagingAll: () => page(), getAll: () => of([]), getPendingStudentIds: () => of([]) } },
    { provide: StudentTravelTicketService, useValue: quiet() },
    { provide: EnumOptionsService, useValue: quiet() },
    { provide: LookupService, useValue: quiet() },
    { provide: UmsAuthService, useValue: { getCurrentUser: () => ({ id: 1, username: 'tester' }) } },
    { provide: AuthService, useValue: { getCurrentUser: () => ({ id: 1 }) } },
    {
        provide: UmsPermissionService,
        useValue: new Proxy({} as any, {
            get: (_t, prop) => prop === 'getAllowedCountryIds' ? () => [] : () => true
        })
    },
    { provide: TranslationService, useValue: translation }
];

const build = async (component: any): Promise<any> => {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
        imports: [ApprovalsModule, RouterModule.forRoot([])],
        providers
    }).compileComponents();
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
};

describe('approval rules dialogs', () => {

    /** The two editors, and how many required fields each leaves blank when opened. */
    const EDITORS: Array<{ what: string; open: (c: any) => void; save: (c: any) => void; required: number }> = [
        { what: 'the workflow', open: c => c.openNewConfigDialog(), save: c => c.saveConfig(), required: 1 },
        { what: 'a level', open: c => c.openNewLevelDialog(), save: c => c.saveLevel(), required: 2 }
    ];

    EDITORS.forEach(({ what, open, save, required }) => {

        it(`adds ${what} through the shared dialog vocabulary`, async () => {
            const fixture = await build(ApprovalConfigsComponent);
            open(fixture.componentInstance);
            fixture.detectChanges();

            const dialogs = Array.from(fixture.nativeElement.querySelectorAll('.dv-dialog')) as HTMLElement[];
            const editor = dialogs.find(d => d.querySelector('.dv-fields'));
            expect(editor).withContext(`the ${what} dialog is not built from the shared vocabulary`).toBeTruthy();
            ['.dv-hero', '.dv-section', '.dv-footer'].forEach(part =>
                expect(editor!.querySelector(part))
                    .withContext(`the ${what} dialog has no ${part}`).not.toBeNull());
        });

        it(`says which fields are missing when ${what} is saved empty`, async () => {
            const fixture = await build(ApprovalConfigsComponent);
            open(fixture.componentInstance);
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelectorAll('.dv-dialog .dv-error').length)
                .withContext('the dialog opens already complaining').toBe(0);

            save(fixture.componentInstance);
            fixture.detectChanges();

            const errors = fixture.nativeElement.querySelectorAll('.dv-dialog .dv-error');
            expect(errors.length)
                .withContext(`Save on an empty form marked ${errors.length} fields; `
                    + 'a disabled button marked none of them')
                .toBeGreaterThanOrEqual(required);
        });

        it(`leaves Save pressable on ${what}`, async () => {
            // The point of the messages above: a dead button says something is
            // wrong without saying what, and cannot be pressed to find out.
            const fixture = await build(ApprovalConfigsComponent);
            open(fixture.componentInstance);
            fixture.detectChanges();

            const dialogs = Array.from(fixture.nativeElement.querySelectorAll('.dv-dialog')) as HTMLElement[];
            const editor = dialogs.find(d => d.querySelector('.dv-fields'))!;
            const buttons = Array.from(editor.querySelectorAll('.dv-footer button')) as HTMLButtonElement[];
            expect(buttons.length).withContext('no footer buttons').toBeGreaterThan(0);
            expect(buttons.some(b => !b.disabled))
                .withContext('every action in the footer is disabled on an empty form').toBeTrue();
        });
    });

    it('translates every placeholder it shows', async () => {
        const fixture = await build(ApprovalConfigsComponent);
        fixture.componentInstance.openNewConfigDialog();
        fixture.detectChanges();
        fixture.componentInstance.openNewLevelDialog();
        fixture.detectChanges();

        // The stub returns the last segment of the key, so a translated
        // placeholder cannot contain these. An untranslated one is the literal.
        const placeholders = (Array.from(fixture.nativeElement.querySelectorAll('[placeholder]')) as HTMLElement[])
            .map(el => el.getAttribute('placeholder') ?? '');
        expect(placeholders.filter(p => /^e\.g\.,|^Select required role$/.test(p)))
            .withContext('a hard-coded English placeholder renders untranslated in Arabic')
            .toEqual([]);
    });

    it('names the role a level needs rather than its id', async () => {
        const fixture = await build(ApprovalConfigsComponent);
        const c = fixture.componentInstance;
        c.roleOptions = [{ label: 'Finance officer', value: 7 }];
        expect(c.roleName(7)).toBe('Finance officer');
        // An id with no matching role renders as the id rather than as nothing:
        // a workflow pointing at a role that no longer exists should be visible.
        expect(c.roleName(999)).toBe('999');
    });
});

describe('the approval detail dialog design', () => {

    const PAGES = [
        { name: 'pending approvals', component: PendingApprovalsComponent },
        { name: 'approval history', component: ApprovalHistoryComponent },
        { name: 'my requests', component: MyRequestsComponent }
    ];

    /**
     * Read off the compiled stylesheet rather than off the source: what matters
     * is that the rules reach the dialog, and where they are declared is exactly
     * what changed.
     */
    const globalRule = (selector: string): boolean =>
        Array.from(document.styleSheets).some(sheet => {
            let rules: CSSRuleList;
            try { rules = sheet.cssRules; } catch { return false; }
            return Array.from(rules).some(r => (r as CSSStyleRule).selectorText === selector);
        });

    it('is defined once, globally, rather than in each page', () => {
        // A p-dialog renders its content outside the component that declares it,
        // so a component-scoped copy needs ::ng-deep to reach it — which is why
        // there were three copies to begin with.
        ['.level-node__circle', '.approval-section', '.approval-icon-badge',
         '.approval-detail-dialog .p-dialog-content'].forEach(selector =>
            expect(globalRule(selector))
                .withContext(`${selector} is not in the global stylesheet`).toBeTrue());
    });

    PAGES.forEach(({ name, component }) => {
        it(`${name} carries no copy of it`, async () => {
            const fixture = await build(component);
            const own = (component as any).ɵcmp?.styles ?? [];
            const text = own.join('\n');
            // What each page keeps is its own: the list's table padding, and the
            // two or three rules that genuinely differ between the three.
            ['.level-node__circle', '.approval-section {', '.approval-icon-badge',
             '.approval-info-card', '.steps-timeline'].forEach(selector =>
                expect(text.includes(selector))
                    .withContext(`${name} still declares ${selector} itself`).toBeFalse());
            expect(fixture.componentInstance).toBeTruthy();
        });
    });
});
