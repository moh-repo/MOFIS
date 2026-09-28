import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StudentFinanceService } from './studentfinance.service';
import { SUPPRESS_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

describe('StudentFinanceService', () => {
  let service: StudentFinanceService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
    imports: [],
    providers: [StudentFinanceService, provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()]
});
    service = TestBed.inject(StudentFinanceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /*
   * Most students have no STUDENT_FINANCE_WAFAR row, so this probe 404s as a
   * matter of course. Without the opt-out the global ErrorInterceptor showed a
   * "Not Found" toast on every finance record without wafar.
   */
  it('marks the wafar probe as an expected failure so no error toast is shown', () => {
    service.getWafar(1).subscribe({ error: () => { /* handled by the caller */ } });

    const req = httpMock.expectOne(r => r.url.endsWith('/studentFinance/1/wafar'));
    expect(req.request.method).toBe('GET');
    expect(req.request.context.get(SUPPRESS_ERROR_TOAST)).toBe(true);

    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  /*
   * Wafar and recipient reach the record only through the approval executor now.
   * A client-side write path here would put them in the table before anyone
   * approved the change — and leave them there if it was rejected.
   */
  it('exposes no direct write path for the wafar or recipient sub-records', () => {
    const writeMethods = ['saveWafar', 'deleteWafar', 'updateReceivedWhom'];
    for (const method of writeMethods) {
      expect((service as any)[method]).toBeUndefined(`${method} must not bypass the approval workflow`);
    }
  });

  it('silences only the wafar probe, not the approval-gated writes', () => {
    service.Update({ id: 1 } as any).subscribe({ error: () => { } });
    const put = httpMock.expectOne(r => r.method === 'PUT');
    // Update is silenced because the caller renders the backend's own message.
    expect(put.request.context.get(SUPPRESS_ERROR_TOAST)).toBe(true);
    expect(put.request.url.endsWith('/studentFinance/1')).toBe(true);
    put.flush({ message: 'ok', status: 'PENDING' });
  });

  it('still surfaces errors for the main record fetch', () => {
    service.getById(1).subscribe({ error: () => { } });

    const req = httpMock.expectOne(r => r.url.endsWith('/studentFinance/1'));
    expect(req.request.context.get(SUPPRESS_ERROR_TOAST)).toBe(false);
    req.flush(null, { status: 404, statusText: 'Not Found' });
  });
});
