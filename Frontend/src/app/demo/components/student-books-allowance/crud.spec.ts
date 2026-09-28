import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { Observable, of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { StudentBooksAllowanceComponent } from './student-books-allowance.component';
import { StudentBooksAllowanceModule } from './student-books-allowance.module';
import { StudentBooksAllowanceService } from '../../service/student-books-allowance.service';
import { MoheQuarterService } from '../../service/mohe-quarter.service';
import { CodeListService } from '../../service/code-list.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { ApprovalService } from '../../service/approval.service';
import { academicYearSpans } from '../../service/academic-year-spans';

/**
 * Add, edit, view and delete on the books allowance screen.
 *
 * <p>The page had no test of any kind, and two of its four operations could not
 * work. The one this pins down here is the payload: both dialogs sent a literal
 * `midate: null`, and `MIDATE` is `NOT NULL` — so the column's own
 * `sysutcdatetime()` default never fired, because a default only applies to a
 * column the INSERT leaves out. (Create had a second fault, `booksAllowId: 0`,
 * which sent Spring Data down `merge()` and answered 409 on every Add; that one
 * is held by `StudentBooksAllowanceCrudTest` against the real database.)
 *
 * <p>The rest is the lookups. Two of the four were empty for the finance roles
 * that run this screen, and the status colour was a `switch` on the literals 0,
 * 1 and 2 while `LU_BOOKS_ALLOWANCE_PAY_STATUS.UI_SEVERITY` held the answer.
 */
describe('StudentBooksAllowance — the four operations and their lookups', () => {

    let fixture: ComponentFixture<StudentBooksAllowanceComponent>;
    let component: StudentBooksAllowanceComponent;
    let created: any[];
    let updated: { id: number; payload: any }[];
    let deleted: number[];
    let optionCalls: string[];

    const RECORDS = [
        { booksAllowId: 1, studentId: 11, studyYear: '2023-2024', moheQuarterId: 3, allowanceAmount: 450, payStatus: 1, receiptDate: null, notes: 'paid' },
        { booksAllowId: 2, studentId: 12, studyYear: '2024-2025', moheQuarterId: null, allowanceAmount: 380, payStatus: 0, receiptDate: null, notes: null },
        { booksAllowId: 3, studentId: 13, studyYear: '2023-2024', moheQuarterId: 3, allowanceAmount: 290, payStatus: 2, receiptDate: null, notes: 'cancelled' }
    ];

    /** The three rows of LU_BOOKS_ALLOWANCE_PAY_STATUS, severities and all. */
    const PAY_STATUS = [
        { value: '0', label: 'قيد الانتظار', labelAr: 'قيد الانتظار', labelEn: 'Pending', sortOrder: 1, uiSeverity: 'warn', icon: null, isSystem: false, active: true, extra: null },
        { value: '1', label: 'مدفوع', labelAr: 'مدفوع', labelEn: 'Paid', sortOrder: 2, uiSeverity: 'success', icon: null, isSystem: false, active: true, extra: null },
        { value: '2', label: 'ملغى', labelAr: 'ملغى', labelEn: 'Cancelled', sortOrder: 3, uiSeverity: 'danger', icon: null, isSystem: false, active: true, extra: null }
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
        id: 7, entityType: 'STUDENT_BOOKS_ALLOWANCE', entityId: String(LOCKED_ID),
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
                return of({
                    'books-allowance-pay-status': PAY_STATUS,
                    'academic-year': ACADEMIC_YEAR
                });
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
            imports: [StudentBooksAllowanceModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: StudentBooksAllowanceService,
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

        fixture = TestBed.createComponent(StudentBooksAllowanceComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    /** Fills the add dialog the way the form does. */
    const fillForm = (values: Record<string, unknown>) => {
        component.form.patchValue(values);
        fixture.detectChanges();
    };

    // ── The payload ──────────────────────────────────────────────────────────

    it('never sends midate on create', () => {
        component.openCreateDialog();
        fillForm({ studentId: 11, studyYear: '2024-2025', allowanceAmount: 400, payStatus: 0 });

        component.saveRecord();

        expect(created.length).toBe(1);
        // MIDATE is NOT NULL and stamped by the server. Sending it as null is what
        // made every Add fail — the database default cannot fire for a column the
        // INSERT names.
        expect('midate' in created[0]).withContext(JSON.stringify(created[0])).toBeFalse();
    });

    it('never sends midate on edit either', () => {
        component.openEditDialog(RECORDS[0] as any);
        fillForm({ allowanceAmount: 475 });

        component.saveRecord();

        expect(updated.length).toBe(1);
        expect(updated[0].id).toBe(1);
        expect('midate' in updated[0].payload).toBeFalse();
        expect(updated[0].payload.allowanceAmount).toBe(475);
    });

    it('carries the fields an edit does change', () => {
        component.openEditDialog(RECORDS[2] as any);
        fillForm({ payStatus: 1, notes: 'now paid' });

        component.saveRecord();

        expect(updated[0].payload.payStatus).toBe(1);
        expect(updated[0].payload.notes).toBe('now paid');
        expect(updated[0].payload.studentId).toBe(13);
    });

    it('refuses to save without a student', () => {
        component.openCreateDialog();
        fillForm({ studentId: null, allowanceAmount: 400 });

        component.saveRecord();

        expect(created.length).toBe(0);
        expect(component.form.invalid).toBeTrue();
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

    it('takes the pay statuses from the code list', () => {
        expect(component.payStatuses.map(s => s.id)).toEqual([0, 1, 2]);
        expect(component.payStatuses.map(s => s.name)).toEqual(['Pending', 'Paid', 'Cancelled']);
    });

    it('paints a status the colour the database gives it', () => {
        // It used to be `case 0: 'warning'` — which is not even PrimeNG 21's word
        // for it — and every status outside 0, 1, 2 went grey by construction.
        expect(component.getStatusSeverity(0)).toBe('warn');
        expect(component.getStatusSeverity(1)).toBe('success');
        expect(component.getStatusSeverity(2)).toBe('danger');
        expect(component.getStatusSeverity(9)).toBe('secondary');
    });

    it('offers academic years as the spans this column stores', () => {
        // LU_ACADEMIC_YEAR lists single years; every row on the table holds a span.
        expect(component.studyYearOptions.map(o => o.value)).toEqual(['2024-2025', '2023-2024', '2022-2023']);
    });

    it('derives a span from each year, most recent first', () => {
        const spans = academicYearSpans([
            { value: '2019', active: true } as any,
            { value: '2021', active: true } as any,
            { value: 'not-a-year', active: true } as any
        ]);
        expect(spans).toEqual([
            { label: '2021-2022', value: '2021-2022' },
            { label: '2019-2020', value: '2019-2020' }
        ]);
    });

    it('resolves the quarter name rather than printing its id', () => {
        expect(component.getQuarterName(3)).toBe('الربع الثاني 2024');
    });

    // ── The student picker ───────────────────────────────────────────────────

    it('fills the picker from the options endpoint', () => {
        // It used to ask the student list, which is gated on STUDENT_PII — a
        // permission none of the three finance roles that run this screen hold.
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
        fillForm({ studentId: 11, studyYear: '2024-2025', allowanceAmount: 400, payStatus: 0 });
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
