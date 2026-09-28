import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, defer } from 'rxjs';
import { map, repeat, retry, takeWhile } from 'rxjs/operators';
import { silentErrors } from '../interceptors/http-context.tokens';
import { environment } from 'src/environments/environment';

export interface MoheQuarter {
  id?: number;
  id1?: number;
  /**
   * The YYYYQ period key — 20254 is 2025 Q4. Read-only: the server derives it from
   * the year and the number, so sending one has no effect. This, not `id`, is how a
   * quarter is identified everywhere else in the system.
   */
  quarter?: number | null;
  quarterYear?: number | null;
  quarterNumber?: number | null;
  quarterName?: string;
  /**
   * Read-only. Summed by the server from STUDENT_FINANCE for a quarter still being
   * paid, and from STUDENT_FINANCE_ARCH for one that has rolled over. Sending it
   * back on a save has no effect — the server ignores it.
   */
  quarterAmount?: number;
  /** Of quarterAmount, the part that was not disbursed and became Wafar. Derived. */
  quarterWafarAmount?: number | null;
  /** quarterAmount − quarterWafarAmount: what actually reached students. Derived. */
  quarterNetAmount?: number | null;
  /** Finance rows behind the derived figures. */
  quarterStudentCount?: number | null;
  /** 'LIVE' | 'ARCHIVE', or null when the quarter has no finance rows yet. */
  quarterAmountSource?: 'LIVE' | 'ARCHIVE' | null;
  quarterPayDate?: string | null;
  quarterRecivedDate?: string | null;
  insertedDate?: string | null;
  notes?: string;
  quarterClosed?: boolean;
  /** Null = global quarter visible to all. Non-null = country-specific. */
  countryId?: number | null;
}

export interface MoheQuarterCloseResult {
  studentsArchived?: number;
  financeArchived?: number;
  paymentsArchived?: number;
  skipped?: boolean;
  message?: string;
  [key: string]: unknown;
}

/**
 * What the Add dialog opens with: the quarter that follows the latest one on record,
 * named the way the existing rows are named. Defaults only — every field is editable,
 * and the server still accepts a later quarter.
 */
export interface MoheQuarterDefaults {
  quarter?: number;
  quarterYear?: number;
  quarterNumber?: number;
  quarterName?: string;
  quarterNameEn?: string;
}

/** Summary returned by the create-with-snapshot endpoint. */
export interface MoheQuarterCreateResult {
  quarterId?: number;
  quarterYear?: number;
  quarterNumber?: number;
  yyyyQ?: number;
  countryId?: number | null;
  studentSnapshotsArchived?: number;
  financeSnapshotsArchived?: number;
  [key: string]: unknown;
}

/**
 * A quarter creation running on the server (review Q4). Creating a quarter archives
 * every student with a profile snapshot, which outlived the request timeout, so the
 * server now runs it as a job and the screen polls for it.
 */
export interface MoheQuarterCreateJob {
  jobId: string;
  state: 'RUNNING' | 'DONE' | 'FAILED';
  /** Students in scope; null until the server has counted them. */
  total: number | null;
  done: number;
  startedBy?: number | null;
  startedAt?: string;
  finishedAt?: string | null;
  summary?: MoheQuarterCreateResult | null;
  error?: string | null;
}

@Injectable({ providedIn: 'root' })
export class MoheQuarterService {
  private readonly baseUrl = `${environment.apiUrl}/mohe/api/v1/moheQuarter`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<MoheQuarter[]> {
    return this.http.get<MoheQuarter[]>(`${this.baseUrl}/`);
  }

  /** Defaults for a new quarter. Reserves nothing and writes nothing. */
  next(): Observable<MoheQuarterDefaults> {
    return this.http.get<MoheQuarterDefaults>(`${this.baseUrl}/next`);
  }

  /** Starts creating the quarter; the answer is the job, not the quarter. See `watchCreateJob`. */
  create(payload: MoheQuarter): Observable<MoheQuarterCreateJob> {
    return this.http.post<MoheQuarterCreateJob>(`${this.baseUrl}/`, payload);
  }

  /** One poll of a creation. Silent: a missed poll is retried, not toasted (W4-02). */
  createJob(jobId: string): Observable<MoheQuarterCreateJob> {
    return this.http.get<MoheQuarterCreateJob>(`${this.baseUrl}/create-jobs/${encodeURIComponent(jobId)}`,
      { context: silentErrors() });
  }

  /**
   * The creation running now, or null — so a page opened mid-run can show it.
   * Silent: it is a background check, and a view-only user answered 403 with an
   * "Access denied" toast on every visit (review G1, W4-02).
   */
  currentCreateJob(): Observable<MoheQuarterCreateJob | null> {
    return this.http.get<MoheQuarterCreateJob>(`${this.baseUrl}/create-jobs/current`,
      { observe: 'response', context: silentErrors() })
      .pipe(map(res => res.status === 204 ? null : res.body));
  }

  /**
   * Polls a creation every two seconds, emitting each state, and completes once it has finished.
   * A failed poll is tried again up to {@link POLL_RETRIES} times before the watch gives up:
   * one dropped request used to end the progress bar while the job ran on (W4-02).
   */
  watchCreateJob(jobId: string): Observable<MoheQuarterCreateJob> {
    // One request per poll, retried, then asked again two seconds after it answered. A
    // timer with switchMap would cancel a retry still waiting when the next tick came.
    return defer(() => this.createJob(jobId)).pipe(
      retry({ count: MoheQuarterService.POLL_RETRIES, delay: 2000 }),
      repeat({ delay: 2000 }),
      takeWhile(job => job.state === 'RUNNING', true)
    );
  }

  static readonly POLL_RETRIES = 3;

  update(id: number, payload: MoheQuarter): Observable<MoheQuarter> {
    return this.http.put<MoheQuarter>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  close(id: number): Observable<MoheQuarterCloseResult> {
    return this.http.post<MoheQuarterCloseResult>(`${this.baseUrl}/${id}/close`, {});
  }
}
