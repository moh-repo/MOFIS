import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpParams } from '@angular/common/http';
import { of } from 'rxjs';
import { WafarReconciliationComponent } from './wafar-reconciliation.component';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';

/**
 * QA FIN-002, the page side: the summary cards showed 0 / 0 / 0 because the API
 * answers in lower case, and a header click flipped its arrow without asking the
 * server for a different order.
 */
describe('WafarReconciliation data', () => {

    let component: WafarReconciliationComponent;
    const lastParams: { value: HttpParams | null } = { value: null };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [WafarReconciliationComponent],
            providers: [
                {
                    provide: HttpClient,
                    useValue: {
                        get: (url: string, opts?: { params?: HttpParams }) => {
                            if (url.endsWith('/summary')) {
                                return of({ total: 941, pending: 0, matched: 21, discrepancy: 920, resolved: 0 });
                            }
                            lastParams.value = opts?.params ?? null;
                            return of({ content: [], totalElements: 0 });
                        },
                        post: () => of({})
                    }
                },
                { provide: LookupService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();
        component = TestBed.createComponent(WafarReconciliationComponent).componentInstance;
    });

    it('reads the counts the API sends', () => {
        component.loadSummary();
        expect(component.summary).toEqual({ MATCHED: 21, DISCREPANCY: 920, RESOLVED: 0 });
    });

    it('asks the server for the order a header click chose, from the first page', () => {
        component.page = 3;
        component.onSort('delta', 'descend');

        expect(component.page).toBe(0);
        expect(lastParams.value?.get('sortBy')).toBe('delta');
        expect(lastParams.value?.get('sortDir')).toBe('desc');
        expect(component.sortOrderOf('delta')).toBe('descend');
        expect(component.sortOrderOf('studentId')).toBeNull();
    });

    it('drops the order when the header is cleared', () => {
        component.onSort('delta', 'descend');
        component.onSort('delta', null);

        expect(lastParams.value?.has('sortBy')).toBeFalse();
    });
});
