import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { WafarReconciliationComponent } from './wafar-reconciliation/wafar-reconciliation.component';
import { StudentPortalDashboardComponent as PortalDashboardComponent } from '../../student-portal/dashboard/dashboard.component';
import { ApprovalService } from '../service/approval.service';
import { LookupService } from '../service/lookup.service';
import { TranslationService } from '../service/translation.service';
import { SUPPRESS_ERROR_TOAST } from '../interceptors/http-context.tokens';

/**
 * Figures that could not be loaded say so, instead of showing 0 (QA B9, N7).
 *
 * <p>A zero is an answer — "no discrepancies", "nothing pending" — so a failed
 * load that left one on screen told the reader something false. The alert-case
 * band and the staff dashboard are covered next to their own components.
 */
describe('figures that could not be loaded', () => {

    const translation = {
        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
    };

    it('wafar reconciliation: the band says so, and retries', async () => {
        let fail = true;
        await TestBed.configureTestingModule({
            imports: [WafarReconciliationComponent],
            providers: [
                {
                    provide: HttpClient,
                    useValue: {
                        get: (url: string) => url.endsWith('/summary')
                            ? (fail ? throwError(() => ({ status: 500 })) : of({ matched: 2, discrepancy: 1, resolved: 0 }))
                            : of({ content: [], totalElements: 0 }),
                        post: () => of({})
                    }
                },
                { provide: LookupService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
                { provide: TranslationService, useValue: translation }
            ]
        }).compileComponents();
        const fixture = TestBed.createComponent(WafarReconciliationComponent);
        const component = fixture.componentInstance;

        component.loadSummary();
        expect(component.summaryFailed).toBeTrue();

        fail = false;
        component.loadSummary();
        expect(component.summaryFailed).toBeFalse();
        expect(component.summary.DISCREPANCY).toBe(1);
    });

    it('portal dashboard: a count that failed shows a dash, and no "needs attention"', () => {
        const api = new Proxy({
            getInfoRequests: () => throwError(() => ({ status: 500 })),
            getDocuments: () => of([{}, {}]),
            getMyChangeRequests: () => of([]),
            getInquiries: () => of({ content: [], totalElements: 0, totalPages: 0 })
        } as any, { get: (t, p: string) => p in t ? t[p] : () => of(null) });
        const toast = { warning: () => undefined, error: () => undefined } as any;
        const component = new PortalDashboardComponent(api, translation as any, toast);

        component.load();

        const cards = component.actionCards;
        const requests = cards.find(c => c.titleKey === 'INFO_REQUESTS')!;
        expect(requests.failed).withContext('the failed count read as 0').toBeTrue();
        expect(requests.attention).toBeFalse();
        expect(cards.find(c => c.titleKey === 'DOCUMENTS')!.failed).toBeFalse();
        expect(cards.find(c => c.titleKey === 'DOCUMENTS')!.count).toBe(2);
        expect(component.countsFailed).toBeTrue();
    });

    it('topbar approvals badge: a failed poll reaches the caller, silently', () => {
        TestBed.resetTestingModule();
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        const http = TestBed.inject(HttpTestingController);
        let failed = false;

        TestBed.inject(ApprovalService).countPending().subscribe({ error: () => failed = true });
        const req = http.expectOne(r => r.url.endsWith('/count/pending'));
        expect(req.request.context.get(SUPPRESS_ERROR_TOAST)).withContext('an outage would toast every minute').toBeTrue();
        req.flush({}, { status: 500, statusText: 'Server Error' });

        expect(failed).withContext('the failure became "0 pending"').toBeTrue();
        http.verify();
    });
});
