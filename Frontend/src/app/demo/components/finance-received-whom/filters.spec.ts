import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { FinanceReceivedWhomComponent } from './finance-received-whom.component';
import { FinanceReceivedWhomModule } from './finance-received-whom.module';
import { FinanceReceivedWhomService } from '../../service/finance-received-whom.service';
import { DocumentTypeService } from '../../service/document-type.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * The recipients page had no filters at all: the only way to find someone in
 * the list was to page through it. Both filters added here run against the
 * records already in memory — loadAll() returns the whole array and the table
 * paginates it client-side — so neither costs a request.
 *
 * <p>These are behavioural rather than geometric. The shared layout is covered
 * once, centrally, in src/app/filter-bar-shared.spec.ts; what is worth pinning
 * here is that the controls are wired to the table at all, which is new code.
 * In particular the filter bar renders before &lt;p-table&gt; in the template,
 * so the table is reached through &#64;ViewChild rather than a forward template
 * reference — if that stopped resolving, every one of these would fail.
 *
 * <p>They run in fakeAsync because PrimeNG's Table.filter() does not filter
 * when called: it stores the constraint and defers the pass by filterDelay,
 * 300ms by default. filterGlobal() is a thin wrapper over the same method, so
 * the text filter is deferred too.
 */
describe('FinanceReceivedWhom filters', () => {

    let fixture: ComponentFixture<FinanceReceivedWhomComponent>;
    let component: FinanceReceivedWhomComponent;

    /** Longer than PrimeNG's default filterDelay. */
    const FILTER_DELAY = 350;

    const records = [
        {
            id: 1, studentForDelegationId: 10, finReceivedName: 'Ahmed Ali',
            finReceivedPassportNumber: 'A111', finReceivedRelationship: 'Father',
            finRecievedYearMonth: '2026-01', finRecievedAmount: 100, finRecievedStatus: 1
        },
        {
            id: 2, studentForDelegationId: 11, finReceivedName: 'Sara Omar',
            finReceivedPassportNumber: 'B222', finReceivedRelationship: 'Sister',
            finRecievedYearMonth: '2026-02', finRecievedAmount: 200, finRecievedStatus: 2
        },
        {
            id: 3, studentForDelegationId: 12, finReceivedName: 'Ahmed Nasser',
            finReceivedPassportNumber: 'C333', finReceivedRelationship: 'Brother',
            finRecievedYearMonth: '2026-03', finRecievedAmount: 300, finRecievedStatus: 2
        }
    ];

    /** Any method returns an empty observable. Silences the calls this page
     *  fires on init that have nothing to do with filtering. */
    const quiet = () => new Proxy({} as any, {
        get: () => () => of([])
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FinanceReceivedWhomModule],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: FinanceReceivedWhomService, useValue: { getAll: () => of(records) } },
                { provide: DocumentTypeService, useValue: quiet() },
                { provide: LookupService, useValue: quiet() },
                {
                    provide: UmsPermissionService,
                    useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] }
                },
                { provide: HttpClient, useValue: { get: () => of({ content: [] }) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(FinanceReceivedWhomComponent);
        component = fixture.componentInstance;

        fixture.detectChanges();
        await fixture.whenStable();

        // Set after the first pass, not before: ngOnInit calls loadLookups(),
        // which would overwrite anything assigned ahead of it.
        component.studyFinReceivedStatuses = [
            { id: 1, studyFinReceivedStatusName: 'Received' },
            { id: 2, studyFinReceivedStatusName: 'Pending' }
        ] as any;

        fixture.detectChanges();
    });

    /** The rows actually rendered in the table body. */
    const shown = () =>
        Array.from(fixture.nativeElement.querySelectorAll('tbody tr'))
            .map(row => (row as HTMLElement).textContent ?? '')
            .filter(text => text.trim().length > 0);

    const matching = (needle: string) => shown().filter(t => t.includes(needle)).length;

    /** Lets the deferred filter pass run, then re-renders. */
    const settle = () => {
        tick(FILTER_DELAY);
        fixture.detectChanges();
    };

    it('finds the table through ViewChild', () => {
        // The filter bar renders before <p-table>, so a template reference
        // would have been a forward one. Everything below depends on this.
        expect(component.table).toBeTruthy();
    });

    it('starts with every record on screen', () => {
        expect(shown().length).withContext(`rows: ${JSON.stringify(shown())}`).toBe(3);
    });

    it('narrows the list by free text', fakeAsync(() => {
        component.applyTextFilter('Ahmed');
        settle();

        expect(shown().length).toBe(2);
        expect(matching('Sara')).toBe(0);
    }));

    it('searches the passport and the relationship, not just the name', fakeAsync(() => {
        // All four fields are named in globalFilterFields; a search that only
        // covered the name would still pass the test above.
        component.applyTextFilter('B222');
        settle();
        expect(shown().length).toBe(1);

        component.applyTextFilter('Brother');
        settle();
        expect(shown().length).toBe(1);
        expect(matching('Ahmed Nasser')).toBe(1);
    }));

    it('narrows the list by status', fakeAsync(() => {
        component.filterStatus = 2;
        component.applyStatusFilter();
        settle();

        expect(shown().length).toBe(2);
        expect(matching('Ahmed Ali')).toBe(0);
    }));

    it('applies both filters together', fakeAsync(() => {
        component.applyTextFilter('Ahmed');
        settle();
        component.filterStatus = 2;
        component.applyStatusFilter();
        settle();

        // Ahmed Ali is status 1, so only Ahmed Nasser satisfies both.
        expect(shown().length).toBe(1);
        expect(matching('Ahmed Nasser')).toBe(1);
    }));

    it('puts everything back when the filters are cleared', fakeAsync(() => {
        component.applyTextFilter('Ahmed');
        settle();
        component.filterStatus = 2;
        component.applyStatusFilter();
        settle();
        expect(shown().length).toBeLessThan(3);

        component.clearFilters();
        settle();

        // Both constraints are reset individually rather than through the
        // table's clear(), which would also drop the sort the user chose.
        expect(shown().length).toBe(3);
        expect(component.filterText).toBe('');
        expect(component.filterStatus).toBeNull();
    }));

    it('offers each configured status as an option', () => {
        expect(component.statusFilterOptions).toEqual([
            { label: 'Received', value: 1 },
            { label: 'Pending', value: 2 }
        ]);
    });
});
