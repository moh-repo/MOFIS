import { Injectable } from '@angular/core';
import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { PaymentAuth } from '../api/payment-auth';

export interface PagedPaymentAuth {
  content: PaymentAuth[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface AmountCalculation {
  studentId: number;
  financeAmount: number;
  wafarAmount: number;
  universityFeeAmount: number;
  totalAmount: number;
  sources: string[];
}

@Injectable({ providedIn: 'root' })
export class PaymentAuthService {
  private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/paymentAuth`;

  constructor(private http: HttpClient) {}

  getAll(page = 0, size = 10, sortBy = 'id', sortDir = 'desc',
         searchTerm?: string, status?: string, countryId?: number | null): Observable<PagedPaymentAuth> {
    let params = new HttpParams()
      .set('page', page).set('size', size)
      .set('sortBy', sortBy).set('sortDir', sortDir);
    if (searchTerm) params = params.set('searchTerm', searchTerm);
    if (status) params = params.set('status', status);
    if (countryId != null) params = params.set('countryId', String(countryId));
    return this.http.get<PagedPaymentAuth>(`${this.baseUrl}/`, { params });
  }

  getById(id: number, context?: HttpContext): Observable<PaymentAuth> {
    return this.http.get<PaymentAuth>(`${this.baseUrl}/${id}`, { context });
  }

  getByStudent(studentId: number): Observable<PaymentAuth[]> {
    return this.http.get<PaymentAuth[]>(`${this.baseUrl}/by-student/${studentId}`);
  }

  calculateAmount(studentId: number, sources: string[]): Observable<AmountCalculation> {
    const params = new HttpParams()
      .set('studentId', studentId)
      .set('sources', sources.join(','));
    return this.http.get<AmountCalculation>(`${this.baseUrl}/calculate-amount`, { params });
  }

  create(payload: PaymentAuth): Observable<PaymentAuth> {
    return this.http.post<PaymentAuth>(`${this.baseUrl}/`, payload);
  }

  update(id: number, payload: PaymentAuth): Observable<PaymentAuth> {
    return this.http.put<PaymentAuth>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Releases one draft: DRAFT to APPROVED. A row the server could not release comes back under `failed`. */
  authorize(id: number): Observable<{ authorized: number; failed: { id: number; reason: string }[] }> {
    return this.http.post<{ authorized: number; failed: { id: number; reason: string }[] }>(
      `${this.baseUrl}/bulk-authorize`, { paymentIds: [id] });
  }

  /** A released payment was paid out: APPROVED to PAID. */
  markPaid(id: number): Observable<PaymentAuth> {
    return this.http.post<PaymentAuth>(`${this.baseUrl}/${id}/mark-paid`, {});
  }
}
