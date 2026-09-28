import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { StudentInfoRequestsComponent } from './student-info-requests.component';
import { StudentInfoRequestsModule } from './student-info-requests.module';
import { StudentInfoRequestService } from '../../service/student-info-request.service';
import { StudentdataService } from '../../service/studentdata.service';
import { LookupService } from '../../service/lookup.service';
import { CodeListService } from '../../service/code-list.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * The filter bar on the information-requests screen.
 *
 * <p>Status and type are enumerations — `LU_INFO_REQUEST_STATUS` and
 * `STUDENT_INFO_REQUEST_KIND` — and the backend filters them with
 * `cb.equal(...)`. They were two free-text boxes, so the only way to use them
 * was to know and type the stored code exactly: `STATUS_CERT`, `RESPONDED`.
 * Anything else returned an empty table, which reads as "there is nothing"
 * rather than "you typed it wrong".
 *
 * <p>And the page index was never reset. With 948 rows at 20 a page, a user who
 * filters from page 3 asks the server for page 3 of a result that has one page,
 * and is told nothing matched.
 *
 * <p>The stored types include three the catalogue does not hold — `RESULTS`,
 * `ACADEMIC_PROGRESS` and `DOCUMENT` — so the selects stay editable: a plain
 * dropdown could never search for a value that is really on the table.
 */
describe('StudentInfoRequests — the filter bar', () => {

    let fixture: ComponentFixture<StudentInfoRequestsComponent>;
    let component: StudentInfoRequestsComponent;
    let listCalls: any[];

    /** The four rows of LU_INFO_REQUEST_STATUS. */
    const STATUS_LIST = [
        { value: 'PENDING', label: 'قيد الاستجابة', labelAr: 'قيد الاستجابة', labelEn: 'Pending', sortOrder: 1, uiSeverity: 'warn', icon: 'pi-clock', isSystem: true, active: true, extra: null },
        { value: 'OVERDUE', label: 'متأخر', labelAr: 'متأخر', labelEn: 'Overdue', sortOrder: 2, uiSeverity: 'danger', icon: 'pi-exclamation-triangle', isSystem: true, active: true, extra: null },
        { value: 'RESPONDED', label: 'تم الرد', labelAr: 'تم الرد', labelEn: 'Responded', sortOrder: 3, uiSeverity: 'success', icon: 'pi-check', isSystem: true, active: true, extra: null },
        { value: 'CANCELLED', label: 'ملغى', labelAr: 'ملغى', labelEn: 'Cancelled', sortOrder: 4, uiSeverity: 'secondary', icon: 'pi-ban', isSystem: true, active: true, extra: null }
    ];

    /** What /ref-data/student-info-request-kinds answers with, already mapped. */
    const KINDS = [
        { label: 'Status certificate', value: 'STATUS_CERT' },
        { label: 'Bank cover letter', value: 'BANK_COVER_LETTER' },
        { label: 'Other', value: 'OTHER' }
    ];

    const http = {
        get: (url: string) => {
            if (url.includes('/code-lists')) {
                return of({ 'info-request-status': STATUS_LIST });
            }
            return of([]);
        }
    };

    beforeEach(async () => {
        listCalls = [];

        await TestBed.configureTestingModule({
            imports: [StudentInfoRequestsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                {
                    provide: StudentInfoRequestService,
                    useValue: {
                        list: (params: any) => {
                            listCalls.push(params);
                            return of({ content: [], totalElements: 948, totalPages: 48 });
                        }
                    }
                },
                { provide: StudentdataService, useValue: { getPagingAll: () => of({ content: [] }) } },
                {
                    provide: LookupService,
                    useValue: { getCountries: () => of([{ id: 1, name: 'Yemen' }, { id: 2, name: 'Egypt' }]),
                    getStudentStudyStatuses: () => of([{ id: 5, name: 'Enrolled' }]), getStudentInfoRequestKindsForUi: () => of(KINDS) }
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
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        // The route guard warms the bulk code-list payload before the page activates.
        TestBed.inject(CodeListService).reload().subscribe();

        fixture = TestBed.createComponent(StudentInfoRequestsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    /** The params of the most recent list call. */
    const lastCall = () => listCalls[listCalls.length - 1];

    // ── The two enumerations ─────────────────────────────────────────────────

    it('offers the statuses the database holds, not a text box', () => {
        expect(component.statusOptions.map(o => o.value))
            .toEqual(['PENDING', 'OVERDUE', 'RESPONDED', 'CANCELLED']);
        expect(component.statusOptions.map(o => o.label))
            .toEqual(['Pending', 'Overdue', 'Responded', 'Cancelled']);
    });

    it('offers the request kinds the database holds', () => {
        expect(component.requestTypes.map(o => o.value))
            .toEqual(['STATUS_CERT', 'BANK_COVER_LETTER', 'OTHER']);
    });

    it('still searches for a type the catalogue does not list', () => {
        // 943 of the 948 stored rows are STATUS_CERT, but RESULTS,
        // ACADEMIC_PROGRESS and DOCUMENT are on the table too and are in no
        // catalogue. An uneditable dropdown could never ask for them.
        component.filterRequestType = 'ACADEMIC_PROGRESS';
        component.applyFilters();

        expect(lastCall().requestType).toBe('ACADEMIC_PROGRESS');
    });

    // ── Searching ────────────────────────────────────────────────────────────

    it('goes back to the first page when the filters change', () => {
        // 948 rows at 20 a page. Filtering from page 3 used to ask for page 3 of
        // a one-page result and come back empty, which reads as "nothing
        // matched" rather than "you are past the end".
        component.page = 3;
        component.filterStatus = 'RESPONDED';

        component.applyFilters();

        expect(component.page).toBe(0);
        expect(lastCall().page).toBe(0);
    });

    it('sends only the filters that were filled in', () => {
        component.filterStatus = 'RESPONDED';
        component.applyFilters();

        const call = lastCall();
        expect(call.status).toBe('RESPONDED');
        expect('requestType' in call).toBeFalse();
        expect('studentId' in call).toBeFalse();
    });

    it('does not send a filter that is only whitespace', () => {
        // The backend compares with cb.equal, so " RESPONDED" matches nothing at
        // all — and an all-spaces box is not a filter the user asked for.
        component.filterStatus = '   ';
        component.filterRequestType = '  STATUS_CERT  ';
        component.applyFilters();

        const call = lastCall();
        expect('status' in call).toBeFalse();
        expect(call.requestType).toBe('STATUS_CERT');
    });

    it('keeps paging within the filtered result', () => {
        component.filterStatus = 'PENDING';
        component.applyFilters();
        component.nextPage();

        expect(lastCall().page).toBe(1);
        expect(lastCall().status).toBe('PENDING');
    });

    // ── Clearing ─────────────────────────────────────────────────────────────

    it('knows whether anything is filtered', () => {
        expect(component.hasFilters).toBeFalse();

        component.filterStudentId = 12;
        expect(component.hasFilters).toBeTrue();
    });

    it('clears every filter and reloads from the first page', () => {
        component.filterStatus = 'PENDING';
        component.filterRequestType = 'STATUS_CERT';
        component.filterStudentId = 12;
        component.page = 2;

        component.clearFilters();

        expect(component.filterStatus).toBe('');
        expect(component.filterRequestType).toBe('');
        expect(component.filterStudentId).toBeNull();
        expect(component.page).toBe(0);

        const call = lastCall();
        expect('status' in call).toBeFalse();
        expect('requestType' in call).toBeFalse();
        expect('studentId' in call).toBeFalse();
    });

    // ── Reading the table ────────────────────────────────────────────────────

    it('names a status rather than printing its code', () => {
        // The column showed `PENDING`, so the value a user had to type into the
        // filter was never on screen anywhere.
        expect(component.statusLabel('RESPONDED')).toBe('Responded');
        expect(component.statusLabel('CANCELLED')).toBe('Cancelled');
    });

    it('shows a stored status the catalogue does not know, as it is', () => {
        expect(component.statusLabel('SOMETHING_ELSE')).toBe('SOMETHING_ELSE');
        expect(component.statusLabel(null)).toBe('—');
    });

    it('names a request type rather than printing its code', () => {
        expect(component.requestTypeLabel('STATUS_CERT')).toBe('Status certificate');
        // One of the three stored types that is in no catalogue.
        expect(component.requestTypeLabel('ACADEMIC_PROGRESS')).toBe('ACADEMIC_PROGRESS');
    });
});
