import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient, HttpParams } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Observable, of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Table } from 'primeng/table';

import { StudentAdminModule } from './student-admin/student-admin.module';
import { StudentUsersComponent } from './student-admin/student-users/student-users.component';
import { StudentLoanModule } from './student-loan/student-loan.module';
import { StudentLoanComponent } from './student-loan/student-loan.component';

import { StudentLoanService } from '../service/student-loan.service';
import { MoheQuarterService } from '../service/mohe-quarter.service';
import { LookupService } from '../service/lookup.service';
import { CodeListService } from '../service/code-list.service';
import { TranslationService } from '../service/translation.service';
import { UmsPermissionService } from '../service/ums-permission.service';
import { ApprovalService } from '../service/approval.service';

/**
 * A filter change puts the table back on page one — the table, not only the
 * component (QA B8, N9).
 *
 * <p>The screens reset their own page number and asked the server for page
 * one, but the p-table kept its own `first`, which no one had bound. So the
 * paginator stayed on page three over a two-row result and read "Showing 21 to
 * 2 of 2", and the next click asked for page four of it. A client-side table
 * did the same with no server involved: the filtered array shrank under a
 * `first` that pointed past its end.
 *
 * <p>One server-paged screen and one client-side screen stand for the rest;
 * the arithmetic is src/app/demo/service/table-paging.util.spec.ts.
 */

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

/** The page's p-table, as PrimeNG holds it. */
const tableOf = (fixture: ComponentFixture<unknown>) =>
    fixture.debugElement.query(By.directive(Table)).componentInstance as Table;

/** The paginator's "Showing … of …" line. */
const reportOf = (fixture: ComponentFixture<unknown>) =>
    (fixture.nativeElement.querySelector('.p-paginator-current') as HTMLElement | null)?.textContent?.trim();

describe('Paging after a filter change — a server-paged table (student portal users)', () => {

    let fixture: ComponentFixture<StudentUsersComponent>;
    let component: StudentUsersComponent;
    let requests: HttpParams[];
    /** What the server says the current filter matches. */
    let total: number;

    beforeEach(async () => {
        requests = [];
        total = 100;
        const http = {
            get: (url: string, options?: { params?: HttpParams }): Observable<any> => {
                if (url.includes('/groups')) return of([]);
                requests.push(options?.params ?? new HttpParams());
                return of({ content: [], totalElements: total });
            },
            post: () => of({}), put: () => of({}), delete: () => of({})
        };

        await TestBed.configureTestingModule({
            imports: [StudentAdminModule, RouterModule.forRoot([])],
            providers: [
                { provide: HttpClient, useValue: http },
                { provide: LookupService, useValue: { getStudentPortalAccountStatuses: () => of([]) } },
                { provide: TranslationService, useValue: translation }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentUsersComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    /** The page number the most recent list request asked for. */
    const lastPage = () => String(requests[requests.length - 1].get('page'));

    /** What a click on page three of the paginator does. */
    const goToPageThree = () => {
        tableOf(fixture).onPageChange({ first: 40, rows: 20 });
        fixture.detectChanges();
    };

    it('follows the paginator, so the component knows the page the table is on', () => {
        goToPageThree();

        expect(component.page).toBe(2);
        expect(component.first).toBe(40);
        expect(lastPage()).toBe('2');
    });

    it('asks for page one and moves the table there on a search', async () => {
        goToPageThree();

        total = 2;
        component.searchTerm = 'ahmed';
        component.onSearch();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(lastPage()).toBe('0');
        expect(component.page).toBe(0);
        expect(component.first).toBe(0);
        expect(tableOf(fixture).first).withContext('the paginator stayed on page three').toBe(0);
        // "Showing 21 to 2 of 2" is what the stale `first` printed, once the
        // paginator had stepped back the one page it steps back by itself.
        expect(reportOf(fixture)).toBe('1 - 2 / 2');
    });

    it('moves the table back on a filter and on clear, not only on search', () => {
        goToPageThree();
        component.onFilter();
        fixture.detectChanges();
        expect(tableOf(fixture).first).withContext('after a status filter').toBe(0);

        goToPageThree();
        component.clearFilters();
        fixture.detectChanges();
        expect(tableOf(fixture).first).withContext('after clear').toBe(0);
        expect(lastPage()).toBe('0');
    });
});

describe('Paging after a filter change — a client-side table (student loans)', () => {

    let fixture: ComponentFixture<StudentLoanComponent>;
    let component: StudentLoanComponent;

    /** 25 loans, three pages at 10 a page; only loans 24 and 25 are cancelled (status 3). */
    const RECORDS = Array.from({ length: 25 }, (_, i) => ({
        loanId: i + 1, studentId: 11, studyYear: '2024-2025', moheQuarterId: null,
        loanAmount: 1000 + i, currencyId: 1000, loanStatus: i >= 23 ? 3 : 1, repaymentNotes: null
    }));

    const LOAN_STATUS = ['Pending', 'Approved', 'Repaid', 'Cancelled'].map((label, i) => ({
        value: String(i), label, labelAr: label, labelEn: label, sortOrder: i + 1,
        uiSeverity: null, icon: null, isSystem: false, active: true, extra: null
    }));

    const http = {
        get: (url: string): Observable<any> => {
            if (url.includes('/code-lists')) return of({ 'loan-status': LOAN_STATUS, 'academic-year': [] });
            if (url.includes('/studentdata/options')) return of([{ studentId: 11, studentName: 'Ahmed Ali' }]);
            return of([]);
        }
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentLoanModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: StudentLoanService, useValue: { getAll: () => of(RECORDS.map(r => ({ ...r }))) } },
                { provide: MoheQuarterService, useValue: { getAll: () => of([]) } },
                { provide: LookupService, useValue: { getStudyCurrencies: () => of([{ id: 1000, name: 'British Pound' }]) } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] } },
                { provide: ApprovalService, useValue: { getPendingForEntity: () => of([]) } },
                { provide: HttpClient, useValue: http },
                { provide: TranslationService, useValue: translation }
            ]
        }).compileComponents();

        // The route guard warms the bulk payload before the page activates.
        TestBed.inject(CodeListService).reload().subscribe();

        fixture = TestBed.createComponent(StudentLoanComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    /** The loan ids in the rows on screen; the empty-message row has none. */
    const shownIds = () =>
        (Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLElement[])
            .filter(tr => !tr.querySelector('td[colspan]'))
            .map(tr => Number(tr.querySelector('td')?.textContent?.trim()));

    const goToPageThree = () => {
        tableOf(fixture).onPageChange({ first: 20, rows: 10 });
        fixture.detectChanges();
    };

    it('shows the filtered rows from page one, not an empty page three', async () => {
        goToPageThree();
        expect(shownIds()).toEqual([21, 22, 23, 24, 25]);
        expect(component.first).withContext('the component follows the paginator').toBe(20);

        component.filters.status = 3;
        component.onFiltersChanged();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(component.first).toBe(0);
        expect(tableOf(fixture).first).withContext('the table stayed past the end of the list').toBe(0);
        expect(shownIds()).toEqual([24, 25]);
    });

    it('starts from page one when the filters are cleared', () => {
        component.filters.status = 1;
        component.onFiltersChanged();
        fixture.detectChanges();
        goToPageThree();

        component.clearFilters();
        fixture.detectChanges();

        expect(tableOf(fixture).first).toBe(0);
        expect(shownIds().length).toBe(10);
    });

    it('keeps the page on a reload that is not a filter change', () => {
        // A save or a delete reloads the list; the user stays on the page they
        // were working on.
        goToPageThree();

        component.loadRecords();
        fixture.detectChanges();

        expect(tableOf(fixture).first).toBe(20);
        expect(shownIds()).toEqual([21, 22, 23, 24, 25]);
    });
});
