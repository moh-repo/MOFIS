import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountingApiService } from './accounting-api.service';

/**
 * The wire shape of the round-2 accounting calls: which endpoint, which verb,
 * and which parameters — the things a typo silently breaks.
 */
describe('AccountingApiService (fund chain)', () => {
    let service: AccountingApiService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()]
        });
        service = TestBed.inject(AccountingApiService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('lists a quarter\'s allocations by quarter key', () => {
        service.allocations(20253).subscribe(rows => expect(rows.length).toBe(1));

        const req = http.expectOne(r => r.url.endsWith('/api/v1/accounting/allocations'));
        expect(req.request.method).toBe('GET');
        expect(req.request.params.get('quarterKey')).toBe('20253');
        req.flush([{ id: 1, status: 'DRAFT' }]);
    });

    it('marks an allocation transferred with the date and reference as query parameters', () => {
        service.transferAllocation(7, '2026-08-27', 'TRF-9').subscribe();

        const req = http.expectOne(r => r.url.endsWith('/allocations/7/transfer'));
        expect(req.request.method).toBe('POST');
        expect(req.request.params.get('transferDate')).toBe('2026-08-27');
        expect(req.request.params.get('reference')).toBe('TRF-9');
        req.flush({ id: 7, status: 'TRANSFERRED' });
    });

    it('omits the reference when none was given', () => {
        service.transferAllocation(7).subscribe();

        const req = http.expectOne(r => r.url.endsWith('/allocations/7/transfer'));
        expect(req.request.params.has('transferDate')).toBeFalse();
        expect(req.request.params.has('reference')).toBeFalse();
        req.flush({});
    });

    it('lists batches for a quarter, narrowing to one attaché only when asked', () => {
        service.paymentBatches(20253).subscribe();
        const all = http.expectOne(r => r.url.endsWith('/payment-batches'));
        expect(all.request.params.get('quarterKey')).toBe('20253');
        expect(all.request.params.has('countryId')).toBeFalse();
        all.flush([]);

        service.paymentBatches(20253, 1004).subscribe();
        const one = http.expectOne(r => r.url.endsWith('/payment-batches'));
        expect(one.request.params.get('countryId')).toBe('1004');
        one.flush([]);
    });

    it('posts the batch with its explicit lines', () => {
        const request = {
            moheQuarterKey: 20253, studyCountryInId: 1, currencyId: 1,
            lines: [{ studentId: 10, entitledAmount: 300 }, { studentId: 11, entitledAmount: 200, disbursedAmount: 150, heldReason: 'docs' }]
        };
        service.createPaymentBatch(request).subscribe(d => expect(d.batch.id).toBe(99));

        const req = http.expectOne(r => r.url.endsWith('/payment-batches'));
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(request);
        req.flush({ batch: { id: 99 }, lines: [] });
    });

    it('asks for candidates by quarter and attaché, and pays by POST', () => {
        service.paymentBatchCandidates(20253, 1).subscribe();
        const cand = http.expectOne(r => r.url.endsWith('/payment-batches/candidates'));
        expect(cand.request.params.get('countryId')).toBe('1');
        cand.flush([]);

        service.payPaymentBatch(5).subscribe();
        const pay = http.expectOne(r => r.url.endsWith('/payment-batches/5/pay'));
        expect(pay.request.method).toBe('POST');
        pay.flush({});
    });

    it('filters FX rates by pair and deletes a wrong one', () => {
        service.fxRates(1, 1012, '2026-02-15').subscribe();
        const list = http.expectOne(r => r.url.endsWith('/fx-rates'));
        expect(list.request.params.get('fromCurrencyId')).toBe('1');
        expect(list.request.params.get('toCurrencyId')).toBe('1012');
        expect(list.request.params.get('on')).toBe('2026-02-15');
        list.flush([]);

        service.deleteFxRate(3).subscribe();
        const del = http.expectOne(r => r.url.endsWith('/fx-rates/3'));
        expect(del.request.method).toBe('DELETE');
        del.flush(null);
    });

    it('reads appropriations by fiscal year and revises with PUT', () => {
        service.appropriations(3).subscribe();
        const list = http.expectOne(r => r.url.endsWith('/appropriations'));
        expect(list.request.params.get('fiscalYearId')).toBe('3');
        list.flush([]);

        service.updateAppropriation(8, { amount: 1200000 }).subscribe();
        const put = http.expectOne(r => r.url.endsWith('/appropriations/8'));
        expect(put.request.method).toBe('PUT');
        expect(put.request.body).toEqual({ amount: 1200000 });
        put.flush({});
    });
});
