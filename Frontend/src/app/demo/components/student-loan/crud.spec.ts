import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { Observable, of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentLoanComponent } from './student-loan.component';
import { StudentLoanModule } from './student-loan.module';
import { StudentLoanService } from '../../service/student-loan.service';
import { MoheQuarterService } from '../../service/mohe-quarter.service';
import { LookupService } from '../../service/lookup.service';
import { CodeListService } from '../../service/code-list.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { ApprovalService } from '../../service/approval.service';

/**
 * Add, edit, view and delete on the student loans screen.
 *
 * <p>The page had no test of any kind, and Add could fail three ways, each
 * hidden behind the one before it: `loanId: 0` on an identity column (409),
 * `midate: null` on a NOT NULL column, and a default amount of `0` that
 * `CK_STUDENT_LOAN_AMOUNT_POSITIVE` refuses outright. The first two are payload
 * faults and are pinned down here; all three are held against the real database
 * by `StudentLoanCrudTest`.
 *
 * <p>The rest is the lookups. The student dropdown asked a list gated on
 * `STUDENT_PII` — a permission no finance role holds — so it was empty and the
 * table printed bare ids; the study year was a free-text box; and the status
 * colour was a `switch` on the literals 0 to 3 that disagreed with
 * `LU_LOAN_STATUS.UI_SEVERITY` on two of its four cases.
 */
describe('StudentLoan — the four operations and their lookups', () => {

    let fixture: ComponentFixture<StudentLoanComponent>;
    let component: StudentLoanComponent;
    let created: any[];
    let updated: { id: number; payload: any }[];
    let deleted: number[];
    let optionCalls: string[];

    const RECORDS = [
        { loanId: 1, studentId: 11, studyYear: '2023-2024', moheQuarterId: 3, loanAmount: 4500, currencyId: 1000, loanStatus: 1, repaymentNotes: 'first' },
        { loanId: 2, studentId: 12, studyYear: '2024-2025', moheQuarterId: null, loanAmount: 3800, currencyId: 1000, loanStatus: 0, repaymentNotes: null },
        { loanId: 3, studentId: 13, studyYear: '2023-2024', moheQuarterId: 3, loanAmount: 2900, currencyId: 1004, loanStatus: 2, repaymentNotes: 'repaid' }
    ];

    /** The four rows of LU_LOAN_STATUS, severities and all. */
    const LOAN_STATUS = [
        { value: '0', label: 'قيد المراجعة', labelAr: 'قيد المراجعة', labelEn: 'Pending', sortOrder: 1, uiSeverity: 'warn', icon: null, isSystem: false, active: true, extra: null },
        { value: '1', label: 'معتمد', labelAr: 'معتمد', labelEn: 'Approved', sortOrder: 2, uiSeverity: 'success', icon: null, isSystem: false, active: true, extra: null },
        { value: '2', label: 'مسدد', labelAr: 'مسدد', labelEn: 'Repaid', sortOrder: 3, uiSeverity: 'success', icon: null, isSystem: false, active: true, extra: null },
        { value: '3', label: 'ملغى', labelAr: 'ملغى', labelEn: 'Cancelled', sortOrder: 4, uiSeverity: 'danger', icon: null, isSystem: false, active: true, extra: null }
    ];

    /** LU_ACADEMIC_YEAR holds single years; this column stores spans. */
    const ACADEMIC_YEAR = ['2022', '2023', '2024'].map((year, i) => ({
        value: year, label: year, labelAr: year, labelEn: year, sortOrder: i + 1,
        uiSeverity: null, icon: null, isSystem: false, active: true, extra: null
    }));

    /** What a write answers now that the screen is behind the approval workflow. */
    const PENDING = { message: 'Request submitted for approval', status: 'PENDING' };

    /** The one record the workflow already has an undecided request for. */
    const LOCKED_ID = 2;
    const OPEN_REQUEST = {
        id: 7, entityType: 'STUDENT_LOAN', entityId: String(LOCKED_ID),
        requestType: 'UPDATE', status: 'PENDING', requestedAt: '2026-09-01T08:00:00Z',
        requesterFullName: 'Finance Officer'
    };

    const STUDENT_OPTIONS = [
        { studentId: 11, studentName: 'Ahmed Ali' },
        { studentId: 12, studentName: 'Sara Omar' },
        { studentId: 21, studentName: 'Nasser Said' }
    ];

    const http = {
        get: (url: string): Observable<any> => {
            if (url.includes('/code-lists')) {
                // What GET /api/v1/code-lists answers with. Loaded through the real
                // service rather than pushed into it, so the component's
                // whenLoaded() fires exactly as it does behind the route guard.
                return of({ 'loan-status': LOAN_STATUS, 'academic-year': ACADEMIC_YEAR });
            }
            if (url.includes('/studentdata/options')) {
                optionCalls.push(url);
                const ids = /[?&]ids=([^&]*)/.exec(url);
                if (ids) {
                    return of(decodeURIComponent(ids[1]).split(',').map(Number)
                        .map(id => ({ studentId: id, studentName: 'Student ' + id })));
                }
                const term = /[?&]search=([^&]*)/.exec(url);
                const search = term ? decodeURIComponent(term[1]) : '';
                return of(STUDENT_OPTIONS.filter(o =>
                    !search || o.studentName.includes(search) || String(o.studentId) === search));
            }
            return of([]);
        }
    };

    beforeEach(async () => {
        created = [];
        updated = [];
        deleted = [];
        optionCalls = [];

        await TestBed.configureTestingModule({
            imports: [StudentLoanModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: StudentLoanService,
                    useValue: {
                        getAll: () => of(RECORDS.map(r => ({ ...r }))),
                        // The screen is behind the approval workflow: a write opens a
                        // request and answers with that, never with a saved record.
                        create: (payload: any) => { created.push(payload); return of(PENDING); },
                        update: (id: number, payload: any) => { updated.push({ id, payload }); return of(PENDING); },
                        delete: (id: number) => { deleted.push(id); return of(PENDING); }
                    }
                },
                {
                    provide: MoheQuarterService,
                    useValue: { getAll: () => of([{ id: 3, quarterName: 'الربع الثاني 2024', quarterYear: 2024, quarterNumber: 2 }]) }
                },
                {
                    provide: LookupService,
                    useValue: { getStudyCurrencies: () => of([{ id: 1000, name: 'British Pound' }, { id: 1004, name: 'Euro' }]) }
                },
                {
                    provide: UmsPermissionService,
                    useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] }
                },
                {
                    provide: ApprovalService,
                    useValue: {
                        getPendingForEntity: (_type: string, id: number) =>
                            of(Number(id) === LOCKED_ID ? [OPEN_REQUEST] : [])
                    }
                },
                { provide: HttpClient, useValue: http },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        // The route guard warms the bulk payload before the page activates.
        TestBed.inject(CodeListService).reload().subscribe();

        fixture = TestBed.createComponent(StudentLoanComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    /** Fills the dialog the way the form does. */
    const fillForm = (values: Record<string, unknown>) => {
        component.form.patchValue(values);
        fixture.detectChanges();
    };

    // ── The payload ──────────────────────────────────────────────────────────

    it('sends neither loanId nor midate on create', () => {
        component.openCreateDialog();
        fillForm({ studentId: 11, studyYear: '2024-2025', loanAmount: 4000, currencyId: 1000, loanStatus: 0 });

        component.saveRecord();

        expect(created.length).toBe(1);
        // LOAN_ID is an identity column: a non-null id makes Spring Data take
        // merge(), and every Add answered 409.
        expect('loanId' in created[0]).withContext(JSON.stringify(created[0])).toBeFalse();
        // MIDATE is NOT NULL and stamped by the server; the column's default
        // cannot fire for a column the INSERT names.
        expect('midate' in created[0]).toBeFalse();
        expect(created[0].loanAmount).toBe(4000);
    });

    it('sends the id in the URL on edit, and still no midate', () => {
        component.openEditDialog(RECORDS[0] as any);
        fillForm({ loanAmount: 4750 });

        component.saveRecord();

        expect(updated.length).toBe(1);
        expect(updated[0].id).toBe(1);
        expect('midate' in updated[0].payload).toBeFalse();
        expect(updated[0].payload.loanAmount).toBe(4750);
    });

    it('carries the fields an edit does change', () => {
        component.openEditDialog(RECORDS[2] as any);
        fillForm({ loanStatus: 3, repaymentNotes: 'now cancelled' });

        component.saveRecord();

        expect(updated[0].payload.loanStatus).toBe(3);
        expect(updated[0].payload.repaymentNotes).toBe('now cancelled');
        expect(updated[0].payload.studentId).toBe(13);
    });

    it('refuses to save without a student', () => {
        component.openCreateDialog();
        fillForm({ studentId: null, loanAmount: 4000, currencyId: 1000 });

        component.saveRecord();

        expect(created.length).toBe(0);
        expect(component.form.invalid).toBeTrue();
    });

    it('refuses an amount of zero, which the database would not take', () => {
        // CK_STUDENT_LOAN_AMOUNT_POSITIVE refuses it — and zero used to be the
        // dialog's own default, with a validator of min(0) that allowed it.
        component.openCreateDialog();
        fillForm({ studentId: 11, currencyId: 1000, loanAmount: 0 });
        component.form.markAllAsTouched();

        component.saveRecord();

        expect(created.length).toBe(0);
        expect(component.amountIsNotPositive).toBeTrue();
    });

    it('refuses to save an amount without a currency', () => {
        component.openCreateDialog();
        fillForm({ studentId: 11, loanAmount: 4000, currencyId: null });

        component.saveRecord();

        expect(created.length).toBe(0);
    });

    it('deletes the record the confirmation accepted', () => {
        component.confirmDelete(RECORDS[1] as any);
        // The component provides its own ConfirmationService, so the dialog it
        // talks to is the one it declares.
        const dialog = fixture.debugElement.injector.get(ConfirmationService);
        (dialog as any).requireConfirmation$.subscribe();
        fixture.detectChanges();
        const accept = document.querySelector('.p-confirmdialog-accept-button') as HTMLElement;
        expect(accept).withContext('the confirm dialog rendered').toBeTruthy();
        accept.click();
        fixture.detectChanges();

        expect(deleted).toEqual([2]);
    });

    // ── The lookups ──────────────────────────────────────────────────────────

    it('takes the loan statuses from the code list', () => {
        expect(component.loanStatuses.map(s => s.id)).toEqual([0, 1, 2, 3]);
        expect(component.loanStatuses.map(s => s.name)).toEqual(['Pending', 'Approved', 'Repaid', 'Cancelled']);
    });

    it('paints a status the colour the database gives it', () => {
        // The switch said `'warning'` for Pending — not PrimeNG 21's word, so it
        // rendered as nothing — and `info` for Approved, where the table says
        // `success`.
        expect(component.getStatusSeverity(0)).toBe('warn');
        expect(component.getStatusSeverity(1)).toBe('success');
        expect(component.getStatusSeverity(2)).toBe('success');
        expect(component.getStatusSeverity(3)).toBe('danger');
        expect(component.getStatusSeverity(9)).toBe('secondary');
    });

    it('offers academic years as the spans this column stores', () => {
        // LU_ACADEMIC_YEAR lists single years; every row on the table holds a span.
        expect(component.studyYearOptions.map(o => o.value)).toEqual(['2024-2025', '2023-2024', '2022-2023']);
    });

    it('resolves the quarter and the currency rather than printing their ids', () => {
        expect(component.getQuarterName(3)).toBe('الربع الثاني 2024');
        expect(component.getCurrencyName(1000)).toBe('British Pound');
    });

    // ── The student picker ───────────────────────────────────────────────────

    it('fills the picker from the options endpoint', () => {
        // It used to ask the student list, which is gated on STUDENT_PII — a
        // permission none of the finance roles that run this screen hold.
        expect(optionCalls.some(u => u.includes('/studentdata/options'))).toBeTrue();
        expect(component.studentsForDropdown.length).toBeGreaterThan(0);
    });

    it('resolves the students the table is already showing, by id', () => {
        // The picker holds one page; the rows on screen name students that are
        // not on it, and they are asked for together rather than one at a time.
        const byId = optionCalls.filter(u => u.includes('ids='));
        expect(byId.length).withContext('one request for all of them').toBe(1);
        expect(byId[0]).toContain('13');
        expect(component.getStudentName(13)).toBe('Student 13');
    });

    it('searches on the server as the user types', fakeAsync(() => {
        component.onStudentFilter({ filter: 'Nasser' });
        tick(350);

        const searches = optionCalls.filter(u => u.includes('search='));
        expect(searches.length).toBe(1);
        expect(searches[0]).toContain('search=Nasser');
        expect(component.studentsForDropdown.map(o => o.id)).toContain(21);
    }));

    it('keeps the chosen student on screen when a search excludes them', fakeAsync(() => {
        component.openCreateDialog();
        fillForm({ studentId: 11 });

        component.onStudentFilter({ filter: 'Nasser' });
        tick(350);

        // A p-select renders the label of its selected value only while that
        // value is still one of its options.
        expect(component.studentsForDropdown.map(o => o.id)).toContain(11);
    }));

    // ── The approval workflow ────────────────────────────────────────────────

    it('shows the message the server sent, not "created successfully"', () => {
        // Nothing was written: the record appears only once an approver signs
        // the request off, so claiming it was created would be a lie.
        const own = fixture.debugElement.injector.get(MessageService);
        const raised = spyOn(own, 'add').and.callThrough();

        component.openCreateDialog();
        fillForm({ studentId: 11, studyYear: '2024-2025', loanAmount: 4000, currencyId: 1000, loanStatus: 0 });
        component.saveRecord();

        expect(created.length).toBe(1);
        expect(raised.calls.allArgs().map(a => a[0]).some(m => m.detail === PENDING.message)).toBeTrue();
    });

    it('locks a record that already has an undecided request', () => {
        // The workflow refuses a second request for the same record, so the form
        // says so on opening rather than letting the user fill it in and be
        // rejected on save.
        component.openEditDialog(RECORDS[1] as any);
        fixture.detectChanges();

        expect(component.pendingApproval).withContext('the open request').toBeTruthy();

        component.saveRecord();
        expect(updated.length).withContext('nothing was submitted').toBe(0);
    });

    it('leaves a record unlocked when nothing is awaiting a decision', () => {
        component.openEditDialog(RECORDS[2] as any);
        fixture.detectChanges();

        expect(component.pendingApproval).toBeNull();

        component.saveRecord();
        expect(updated.length).toBe(1);
    });
});
