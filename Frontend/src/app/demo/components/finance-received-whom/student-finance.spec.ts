import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpClient } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { FinanceReceivedWhomComponent } from './finance-received-whom.component';
import { FinanceReceivedWhomModule } from './finance-received-whom.module';
import { FinanceReceivedWhomService } from '../../service/finance-received-whom.service';
import { DocumentTypeService } from '../../service/document-type.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * Picking a student brings that student's money with them — and wafar stays
 * with wafar.
 *
 * <p>STUDENT_FINANCE is keyed by STUDENT_ID: one row per student, holding the
 * entitlement, the part held back as wafar, and NET_AMOUNT, the only figure
 * that may actually be handed to whoever collects it. This screen used to know
 * none of that. The amount was a blank box, and the live table showed what came
 * of it: recipient 1 was handed the 500 that belonged to wafar, recipient 2 was
 * paid 500 more than the student was ever entitled to, and recipient 4
 * collected 900 against a net of zero.
 *
 * <p>The fixtures below are those three rows.
 */
describe('FinanceReceivedWhom — the student\'s finance record', () => {

    let fixture: ComponentFixture<FinanceReceivedWhomComponent>;
    let component: FinanceReceivedWhomComponent;
    let created: any[];
    let patched: string[];

    /** Student 1: owed 1,500, 500 held as wafar, 1,000 payable. */
    const FINANCE_WITH_WAFAR = {
        id: 1, studentFinRecievedAmount: 1500, wafarAmount: 500, netAmount: 1000,
        wafarTypeId: 1, studentFinRecievedYear: 2024, studentFinRecievedMonth: 2,
        studyCountryInId: 7, studyGovInId: 3, studyFinanceBankeId: 11,
        studyFinanceBankBranchId: 21, studyFinanceDofId: 4
    };

    /** Student 4: the whole entitlement is wafar, so nothing is payable. */
    const FINANCE_ALL_WAFAR = {
        id: 4, studentFinRecievedAmount: 1500, wafarAmount: 1500, netAmount: 0,
        wafarTypeId: 1, studentFinRecievedYear: 2024, studentFinRecievedMonth: 2,
        studyCountryInId: 7
    };

    /** Student 5: no wafar at all — the whole entitlement is payable. */
    const FINANCE_NO_WAFAR = {
        id: 5, studentFinRecievedAmount: 1500, wafarAmount: 0, netAmount: 1500,
        studentFinRecievedYear: 2024, studentFinRecievedMonth: 2,
        studyCountryInId: 7
    };

    /** Keyed by student id; anything else answers 404, as the server does. */
    const financeRows: Record<number, any> = {
        1: FINANCE_WITH_WAFAR,
        4: FINANCE_ALL_WAFAR,
        5: FINANCE_NO_WAFAR
    };

    const notFound = () => throwError(() => ({ status: 404 }));

    /** What a write answers now that the screen is behind the approval workflow. */
    const PENDING = { message: 'Request submitted for approval', status: 'PENDING' };

    /** What /studentdata/options answers with — an id and a name, nothing else. */
    const STUDENT_OPTIONS = [
        { studentId: 1, studentName: 'Ahmed Ali' },
        { studentId: 4, studentName: 'Sara Omar' },
        { studentId: 5, studentName: 'Nasser Said' }
    ];

    let optionCalls: string[];

    const http = {
        get: (url: string): Observable<any> => {
            const finance = /\/studentFinance\/(\d+)$/.exec(url);
            if (finance) {
                const row = financeRows[Number(finance[1])];
                return row ? of(row) : notFound();
            }
            const wafar = /\/studentFinance\/(\d+)\/wafar$/.exec(url);
            if (wafar) {
                const row = financeRows[Number(wafar[1])];
                return row && row.wafarAmount > 0
                    ? of({ id: row.id, wafarTypeId: row.wafarTypeId })
                    : notFound();
            }
            if (url.includes('/studentdata/options')) {
                optionCalls.push(url);
                const term = /[?&]search=([^&]*)/.exec(url);
                const ids = /[?&]ids=([^&]*)/.exec(url);
                if (ids) {
                    return of(decodeURIComponent(ids[1]).split(',')
                        .map(id => Number(id))
                        .map(id => ({ studentId: id, studentName: 'Student ' + id })));
                }
                const search = term ? decodeURIComponent(term[1]) : '';
                return of(STUDENT_OPTIONS.filter(o =>
                    !search || o.studentName.includes(search) || String(o.studentId) === search));
            }
            return of({ content: [] });
        },
        patch: (url: string) => { patched.push(url); return of({}); }
    };

    /** Any LookupService method answers with an empty list unless named here. */
    const lookups = (overrides: Record<string, any>) => new Proxy(overrides as any, {
        get: (target, name: string) => name in target ? target[name] : () => of([])
    });

    beforeEach(async () => {
        created = [];
        patched = [];
        optionCalls = [];

        await TestBed.configureTestingModule({
            imports: [FinanceReceivedWhomModule],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: FinanceReceivedWhomService,
                    useValue: {
                        getAll: () => of([]),
                        getDocuments: () => of([]),
                        // The screen is behind the approval workflow: a write opens a
                        // request and answers with that, never with a saved record.
                        create: (payload: any) => { created.push(payload); return of(PENDING); },
                        update: (_id: number, _payload: any) => of(PENDING)
                    }
                },
                { provide: DocumentTypeService, useValue: { getAllActiveDocumentTypes: () => of([]) } },
                {
                    provide: LookupService,
                    useValue: lookups({
                        getLuFamilyRelationshipsForUi: () => of([
                            { label: 'Parent', value: 'PARENT' },
                            { label: 'Sibling', value: 'SIBLING' }
                        ]),
                        getFinReceivedReasonsForUi: () => of([{ label: 'Sick student', value: 'SICK' }]),
                        getWafarTypes: () => of([{ id: 1, name: 'Savings' }]),
                        getAllStudyFinReceivedStatuses: () => of([
                            { id: 1, studyFinReceivedStatusName: 'Received', uiSeverity: 'success' },
                            { id: 103, studyFinReceivedStatusName: 'Rejected', uiSeverity: 'danger' },
                            { id: 101, studyFinReceivedStatusName: 'Partially Disbursed', uiSeverity: 'warn' }
                        ])
                    })
                },
                {
                    provide: UmsPermissionService,
                    useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] }
                },
                { provide: HttpClient, useValue: http },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, getCurrentLanguage: () => 'en',
                        // Only ABOVE_NET carries placeholders, and the point of the
                        // guard is the figures it names, so it is translated for
                        // real here rather than echoed back as its key.
                        translate: (k: string) => k === 'FIN_RECEIVED_WHOM.ABOVE_NET'
                            ? 'Only {payable} may be handed to the recipient. {wafar} is held as wafar ({type}) and cannot be paid out.'
                            : k
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(FinanceReceivedWhomComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    /** Opens the add dialog and picks a student, as the dropdown does. */
    const pickStudent = (studentId: number) => {
        component.openNew();
        component.form.studentForDelegationId = studentId;
        component.onStudentChange();
        fixture.detectChanges();
    };

    /**
     * Watches the toasts the component actually raises.
     *
     * <p>The component declares `providers: [MessageService, ConfirmationService]`,
     * so it holds its own instances. A spy on the TestBed-level MessageService
     * would never see a single message.
     */
    const errors = () => {
        const own = fixture.debugElement.injector.get(MessageService);
        const spy = spyOn(own, 'add').and.callThrough();
        return () => spy.calls.allArgs().map(a => a[0]).filter(m => m.severity === 'error');
    };

    /** Every toast the component raises, watched the same way as {@link errors}. */
    const messages = () => {
        const own = fixture.debugElement.injector.get(MessageService);
        const spy = spyOn(own, 'add').and.callThrough();
        return () => spy.calls.allArgs().map(a => a[0]);
    };

    // ── Bringing the student's financial data ────────────────────────────────

    it('reads the finance record when a student is chosen', () => {
        pickStudent(1);

        expect(component.studentFinance).toBeTruthy();
        expect(component.entitlementAmount).toBe(1500);
        expect(component.wafarAmount).toBe(500);
        expect(component.payableAmount).toBe(1000);
        expect(component.hasWafar).toBeTrue();
    });

    it('prefills the net, not the entitlement', () => {
        pickStudent(1);

        // 1,500 is what the student is owed. 1,000 is what this recipient may be
        // handed. Prefilling the first is how recipient 1 was overpaid.
        expect(component.form.finRecievedAmount).toBe(1000);
    });

    it('prefills the banking details from that record', () => {
        pickStudent(1);

        expect(component.form.financeCountryId).toBe(7);
        expect(component.form.financeGovId).toBe(3);
        expect(component.form.financeBankeId).toBe(11);
        expect(component.form.financeBankBranchId).toBe(21);
        expect(component.form.financeDofId).toBe(4);
    });

    it('does not copy the finance quarter into the recipient\'s month', () => {
        pickStudent(1);

        // STUDENT_FINANCE holds year and *quarter* (2024, 2 → 20242); this table
        // holds year and *month* (2024, 3 → 202403). Copying one into the other
        // would file a March payment as the second quarter.
        expect(component.form.finRecievedYear).toBe(0);
        expect(component.form.finRecievedMonth).toBe(0);
        expect(component.financeQuarterLabel).toBe('2024 / Q2');
    });

    it('names which wafar the money is held against', () => {
        pickStudent(1);
        expect(component.wafarTypeName).toBe('Savings');
    });

    it('leaves the form alone when the student has no finance record', () => {
        component.openNew();
        component.form.finRecievedAmount = 750;
        component.form.studentForDelegationId = 404;
        component.onStudentChange();

        expect(component.noFinanceRecord).toBeTrue();
        expect(component.studentFinance).toBeNull();
        expect(component.form.finRecievedAmount).toBe(750);
    });

    it('reads the record on edit without overwriting what is stored', () => {
        component.openEdit({
            id: 7, studentForDelegationId: 1, finReceivedName: 'Ahmed',
            finRecievedAmount: 1500, financeCountryId: 2, financeBankeId: 9
        } as any);

        // The split is on screen so the guard can work and the reviewer can see it…
        expect(component.payableAmount).toBe(1000);
        // …but this row is a record of what happened, not a form to refill.
        expect(component.form.finRecievedAmount).toBe(1500);
        expect(component.form.financeCountryId).toBe(2);
        expect(component.form.financeBankeId).toBe(9);
    });

    // ── If there is wafar, it goes to wafar ──────────────────────────────────

    it('refuses to hand over more than the net', () => {
        const errs = errors();
        pickStudent(1);
        component.form.finReceivedName = 'Collector';
        component.form.finRecievedAmount = 1500;

        component.save();

        expect(created.length).withContext('nothing may be written').toBe(0);
        expect(component.formVisible).withContext('the dialog stays open').toBeTrue();
        expect(errs().length).toBe(1);
        expect(errs()[0].detail).toContain('1,000');
        expect(errs()[0].detail).toContain('500');
        expect(errs()[0].detail).toContain('Savings');
    });

    it('refuses any payment at all when the whole entitlement is wafar', () => {
        pickStudent(4);
        component.form.finReceivedName = 'Collector';

        expect(component.payableAmount).toBe(0);
        expect(component.form.finRecievedAmount).toBe(0);

        component.form.finRecievedAmount = 1;
        component.save();
        expect(created.length).toBe(0);
    });

    it('lets the net through', () => {
        pickStudent(1);
        component.form.finReceivedName = 'Collector';
        component.form.finRecievedAmount = 1000;

        component.save();

        expect(created.length).toBe(1);
        expect(created[0].finRecievedAmount).toBe(1000);
    });

    it('does not stand in the way when there is no wafar', () => {
        pickStudent(5);
        component.form.finReceivedName = 'Collector';

        expect(component.hasWafar).toBeFalse();
        expect(component.form.finRecievedAmount).toBe(1500);

        component.save();
        expect(created.length).toBe(1);
    });

    it('never guards a record it has no finance row for', () => {
        component.openNew();
        component.form.finReceivedName = 'Collector';
        component.form.financeCountryId = 3;
        component.form.finRecievedAmount = 99999;

        component.save();

        // No student, no entitlement to measure against — the screen has nothing
        // to say, and refusing here would block every recipient not tied to one.
        expect(created.length).toBe(1);
    });

    // ── Linking the finance record back ──────────────────────────────────────

    it('submits the link when editing a recipient that already exists', () => {
        // STUDY_RECEIVED_WHOM_ID is 0 on all 941 finance rows because nothing
        // ever made this call.
        component.openEdit({ id: 42, finReceivedName: 'Collector', financeCountryId: 3 } as any);
        component.form.studentForDelegationId = 1;
        component.onStudentChange();
        fixture.detectChanges();

        component.save();

        expect(patched.length).toBe(1);
        expect(patched[0]).toContain('/studentFinance/1/receivedWhom');
        expect(patched[0]).toContain('whomId=42');
    });

    it('cannot link a brand-new recipient, and says so rather than linking nothing', () => {
        // The screen is behind the approval workflow now, so a new recipient has
        // no id until an approver signs the request off — there is nothing for
        // the finance record to point at yet. Linking `undefined` would have put
        // a whomId of nothing at all on the finance row.
        pickStudent(1);
        component.form.finReceivedName = 'Collector';
        const raised = messages();

        component.save();

        expect(patched.length).toBe(0);
        expect(raised().some(t => t.detail === 'FIN_RECEIVED_WHOM.LINK_AFTER_APPROVAL')).toBeTrue();
    });

    it('shows the server\'s "submitted for approval" message, not "saved"', () => {
        pickStudent(1);
        component.form.finReceivedName = 'Collector';
        const raised = messages();

        component.save();

        expect(created.length).toBe(1);
        expect(raised().some(t => t.detail === PENDING.message)).toBeTrue();
    });

    it('does not link when no student was chosen', () => {
        component.openNew();
        component.form.finReceivedName = 'Collector';
        component.form.financeCountryId = 3;

        component.save();

        expect(patched.length).toBe(0);
    });

    // ── Lookups that come from the database ──────────────────────────────────

    it('offers the relationship catalogue instead of a blank text box', () => {
        expect(component.relationshipOptions.map(o => o.value)).toEqual(['PARENT', 'SIBLING']);
        expect(component.reasonOptions.map(o => o.value)).toEqual(['SICK']);
    });

    it('shows text stored before the lookup existed as it was typed', () => {
        // The seven rows on the live table hold free text that matches no code.
        // A strict dropdown would blank them the moment someone opened the row.
        expect(component.getRelationshipLabel('ولي أمر')).toBe('ولي أمر');
        expect(component.getRelationshipLabel('PARENT')).toBe('Parent');
        expect(component.getRelationshipLabel('')).toBe('—');
    });

    it('paints a status the colour the database gives it', () => {
        // It used to be `id === 1 ? success : secondary`, so eight of the nine
        // statuses were the same grey and Rejected looked like Not Received.
        expect(component.getStatusSeverity(1)).toBe('success');
        expect(component.getStatusSeverity(103)).toBe('danger');
        expect(component.getStatusSeverity(101)).toBe('warn');
        expect(component.getStatusSeverity(9999)).toBe('secondary');
    });

    // ── The student picker ───────────────────────────────────────────────────

    it('fills the picker from the options endpoint', () => {
        // It used to ask `/studentdata/?page=0&size=5000` and read `res.content`.
        // That endpoint returns a bare array and ignores paging, so `content` was
        // always undefined and the dropdown was empty for everybody.
        expect(optionCalls.some(u => u.includes('/studentdata/options'))).toBeTrue();
        expect(component.studentsForDropdown.length).toBe(3);
        expect(component.studentsForDropdown[0].label).toContain('Ahmed Ali');
    });

    it('searches on the server as the user types', fakeAsync(() => {
        component.onStudentFilter({ filter: 'Sara' });
        tick(350);

        const searches = optionCalls.filter(u => u.includes('search='));
        expect(searches.length).toBe(1);
        expect(searches[0]).toContain('search=Sara');
        // Only one page is ever held, so filtering in the browser could only have
        // searched that page.
        expect(component.studentsForDropdown.map(o => o.id)).toEqual([4]);
    }));

    it('debounces the typing into one request', fakeAsync(() => {
        component.onStudentFilter({ filter: 'S' });
        component.onStudentFilter({ filter: 'Sa' });
        component.onStudentFilter({ filter: 'Sar' });
        tick(350);

        expect(optionCalls.filter(u => u.includes('search=')).length).toBe(1);
    }));

    it('keeps the chosen student on screen when a search excludes them', fakeAsync(() => {
        pickStudent(1);
        expect(component.form.studentForDelegationId).toBe(1);

        component.onStudentFilter({ filter: 'Nasser' });
        tick(350);

        // A p-select renders the label of its selected value only while that value
        // is still one of its options.
        expect(component.studentsForDropdown.map(o => o.id)).toContain(1);
        expect(component.getStudentName(1)).toBe('Ahmed Ali');
    }));

    it('resolves the names the list is already showing, by id', () => {
        // A saved record's student is almost never in the picker's one page.
        component.records = [{ id: 1, studentForDelegationId: 77 } as any];
        (component as any).loadStudentsOfRecords();

        expect(optionCalls.some(u => u.includes('ids=77'))).toBeTrue();
        expect(component.getStudentName(77)).toBe('Student 77');
    });

    // ── One confirm dialog ───────────────────────────────────────────────────

    it('declares exactly one confirm dialog', () => {
        // PrimeNG's selector is 'p-confirmDialog, p-confirmdialog,
        // p-confirm-dialog', so a bare lowercase tag alongside the styled one is
        // a second live dialog listening on the same service, not inert markup.
        expect(fixture.debugElement.queryAll(By.directive(ConfirmDialog)).length).toBe(1);
    });
});
