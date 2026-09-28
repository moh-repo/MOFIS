import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable, forkJoin, of } from 'rxjs';
import { catchError, map, tap, timeout } from 'rxjs/operators';
import { environment } from '../../environments/environment';

/** `name` is the Arabic, `nameEn` the English; pick with `inLanguage` / `pickLanguage` (QA SP-004). */
export interface PortalLookupItem { id: number; name: string; nameEn?: string; }

export interface PortalLookups {
  genders: PortalLookupItem[];
  studentCountries: PortalLookupItem[];
  studentGovernments: PortalLookupItem[];
  studentMunicipalities: PortalLookupItem[];
  degrees: PortalLookupItem[];
  currentLevels: PortalLookupItem[];
  studyTypes: PortalLookupItem[];
  studyStatuses: PortalLookupItem[];
  studyCountries: PortalLookupItem[];
  studyGovernments: PortalLookupItem[];
  studyUniversities: PortalLookupItem[];
  studyColleges: PortalLookupItem[];
  studyEducations: PortalLookupItem[];
  studyMajors: PortalLookupItem[];
  agencyTypes: PortalLookupItem[];
  ministryTypes: PortalLookupItem[];
  universityTypes: PortalLookupItem[];
}

/**
 * The portal's session token, and the only thing the portal puts in browser
 * storage.
 *
 * <p>Namespaced, and deliberately nothing like the ministry side's keys. The two
 * are separate authentications of separate people: a student signs in with
 * `X-Student-Portal-Token`, staff with a UMS bearer JWT, and neither may read,
 * refresh or clear the other's session. `UmsAuthService` walks its own key list
 * on sign-out and this key is not on it; the portal clears only the keys below.
 * The one place they used to meet — the interceptor attaching the staff JWT to
 * portal requests — is already closed (see AuthInterceptor).
 */
const TOKEN_KEY = 'mohe.portal.token';

/**
 * What earlier builds called it. Carried over once so a signed-in student is not
 * thrown out by the deploy, then removed.
 */
const LEGACY_TOKEN_KEYS = ['studentPortalToken'] as const;

const BASE = `${environment.apiUrl}/mohe/api/v1/student-portal`;

/**
 * Storage access that cannot throw, owned by the portal.
 *
 * <p>A private window, blocked site data or an embedded webview makes
 * `localStorage` throw on access rather than return null — and these are called
 * from a route guard, where an exception is a portal that will not open at all
 * rather than one that asks for a sign-in. A student on a locked-down phone
 * browser is exactly the case that must degrade to "please sign in".
 */
function readKey(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeKey(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch { /* storage unavailable: the session lasts as long as the page */ }
}

/** Removed from both stores: an older build may have left a copy in either. */
function dropKey(key: string): void {
  try { localStorage.removeItem(key); } catch { /* already unreachable */ }
  try { sessionStorage.removeItem(key); } catch { /* already unreachable */ }
}

/**
 * One option in the login country selector.
 *
 * <p>The id field is `id`, not `studyCountryInId`: StudyCountryIn maps
 * STUDY_COUNTRY_IN_ID onto a property called `id`, so that is what the JSON
 * carries. The main system login binds `optionValue="id"` for the same reason.
 *
 * <p>Not the shared `StudyCountryIn` class from demo/api, which predates the
 * English name column and so has no studyCountryInNameEn — the field this
 * selector needs to label itself in English.
 */
export interface LoginCountry {
  id: number;
  studyCountryInName?: string;
  studyCountryInNameEn?: string;
}

export interface LoginResponse {
  /** S-11: OK carries the token; VERIFY_EMAIL / TOTP_REQUIRED ask for a code and carry only a message. */
  status?: 'OK' | 'VERIFY_EMAIL' | 'TOTP_REQUIRED';
  message?: string;
  token: string;
  studentId: number;
  studentName: string;
  permissions?: string[];
  /** SEC-001 (D9-a): the account has never had a password of the student's own choosing. */
  mustChangePassword?: boolean;
}

/** The 403 code of a session held to the password change until the student chooses one (SEC-001). */
export const PORTAL_PASSWORD_CHANGE_REQUIRED = 'PORTAL_PASSWORD_CHANGE_REQUIRED';

/** S-11: the extra codes a sign-in may need. */
export interface LoginCodes {
  emailCode?: string;
  totpCode?: string;
}

export interface PortalSecurityStatus {
  totpEnabled: boolean;
  totpEnrolledAt: string | null;
  emailVerifiedAt: string | null;
  locked: boolean;
}

export interface PortalTotpStart {
  secret: string;
  otpauthUri: string;
  qrPng: string;
}

export interface StudentChangeRequest {
  requestId: number;
  studentId: number;
  studentUserId: number;
  submissionId: string;
  fieldName: string;
  fieldLabel?: string;
  oldValue?: string;
  newValue: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'MODIFIED';
  rejectionReason?: string;
  employeeComment?: string;
  appliedValue?: string;
  reviewedBy?: number;
  reviewedAt?: string;
  appliedAt?: string;
  createdAt: string;
}

export interface FieldChangeInput {
  fieldName: string;
  fieldLabel: string;
  newValue: string;
}

export interface SubmitChangeResponse {
  message: string;
  submissionId: string;
  count: number;
}

export interface Profile {
  studentId: number;
  studentName: string;
}

/** Student view payload from GET /me/data (mirrors main system view; all optional for partial backend response). */
export interface PortalStudent {
  studentId?: number;
  studentName?: string;
  studentFinId?: number | string;
  studentEductionName?: string;
  studentGender?: number;
  studentBirthdate?: string;
  studentBirthdatePlace?: number | string;
  studentPassportNumber?: string;
  studentPassportIssueDate?: string;
  studentPassportExpireDate?: string;
  studentPassportIssueCountryId?: number;
  studentCountryId?: number;
  studentGovId?: number;
  studentMudId?: number;
  studentDegreeId?: number;
  studentMajorId?: number;
  /** The major's names from STUDENT_MAJOR when the id resolves (QA SP-004); pick with `pickLanguage`. */
  studentMajorName?: string;
  studentMajorNameEn?: string;
  studentDelegateDecisionNo?: number;
  studentStudyNoOfYears?: number;
  studentCurrentLevelId?: number;
  studentStudyTypeId?: number;
  studentStudyStatusId?: number;
  studentAmount?: number;
  studentAmountCurrencyId?: number;
  amountCurrency?: number;
  studentFirstReceiptOfDue?: number;
  studentDelegationDecisionYear?: number;
  studentAddedQuarter?: number;
  studentLastQuarter?: number;
  studyUniversityFeeId?: number;
  studyUniversityFeeStatusId?: number;
  studentMidate?: string;
  studentAgencyTypeId?: number;
  studentMinistryTypeId?: number;
  studentUniversityTypeId?: number;
  studyCountryIn?: number;
  syudyGovIn?: number;
  studyUniversityIn?: number;
  studyCollegeIn?: number;
  studyEducationIn?: number;
  studyMajorIn?: number;
  studentNote1?: string;
  studentNote2?: string;
  studentGenderName?: string;
  studentBirthdatePlaceName?: string;
  studentPassportIssueCountryName?: string;
  studentCountryName?: string;
  studentGovName?: string;
  studentMudName?: string;
  studentDegreeName?: string;
  studentCurrentLevelName?: string;
  studentStudyTypeName?: string;
  studentStudyStatusName?: string;
  studentAmountCurrencyName?: string;
  /** Named on the server, both languages (QA SP-004); pick with `pickLanguage`. */
  amountCurrencyName?: string;
  amountCurrencyNameEn?: string;
  studyUniversityFeeStatusName?: string;
  studyUniversityFeeStatusNameEn?: string;
  studentAgencyTypeName?: string;
  studentMinistryTypeName?: string;
  studentUniversityTypeName?: string;
  studyCountryInName?: string;
  syudyGovInName?: string;
  studyUniversityInName?: string;
  studyCollegeInName?: string;
  studyEducationInName?: string;
  studyMajorInName?: string;
}

/** Finance view payload from GET /me/data. */
export interface PortalFinance {
  id?: number;
  studentFinId?: number | string;
  studentFinName?: string;
  studentFinRecievedAmount?: number;
  studentFinRecievedYear?: number;
  studentFinRecievedMonth?: number;
  studentFinRecievedYearMonth?: number;
  studyCheckDate?: string;
  studyFinRecievedStatus?: number;
  studyCountryInId?: number;
  studyGovInId?: number;
  /** Resolved by the server: the status's code, both names, and whether the money is received. */
  studyFinRecievedStatusCode?: string;
  studyFinRecievedStatusName?: string;
  studyFinRecievedStatusNameEn?: string;
  received?: boolean;
  studyCountryInName?: string;
  studyGovInName?: string;
}

export interface MyData {
  student: PortalStudent | null;
  finance: PortalFinance | null;
}

/** Academic progress (current year, progress %). Same shape as main system GET /api/v1/studentdata/{id}/progress. */
export interface StudentProgressDto {
  studentId: number;
  currentYearLabel: string;
  currentYearLabelEn?: string;
  currentYearOrder: number;
  totalYears: number;
  progressPercent: number;
}

export interface StudentYearResultDto {
  id?: number;
  studentId?: number;
  yearNumber: number;
  academicYear?: string;
  passed: boolean;
  grade?: string;
  notes?: string;
  updatedAt?: string;
  /** False for a seeded placeholder: shown as "not recorded" (FIX-PLAN P5-8). */
  recorded?: boolean;
}

export interface StudentDocument {
  documentId: number;
  studentId: number;
  documentName: string;
  documentType?: string;
  originalFilename?: string;
  fileSize?: number;
  uploadDate?: string;
}

export interface StudentInquiry {
  id: number;
  studentId: number;
  type: string;
  title: string;
  description?: string;
  status: string;
  response?: string;
  respondedAt?: string;
  respondedBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface StudentInfoRequest {
  id: number;
  studentId: number;
  requestType: string;
  title: string;
  description?: string;
  createdBy: string;
  createdAt: string;
  dueDate?: string;
  status: string;
  responseText?: string;
  responseAt?: string;
  responseDocumentId?: number;
  reviewStatus?: string;
  reviewedBy?: number;
  reviewedAt?: string;
  rejectionReason?: string;
}

@Injectable({ providedIn: 'root' })
export class StudentPortalApiService {

  /**
   * The signed-in student, in memory only.
   *
   * <p>The portal has never written the student's name or number to browser
   * storage and does not start now: the token is the whole of what survives a
   * page load, and everything else is read back from the server. This snapshot
   * exists so that the guard's check and the shell's identity block are one
   * request rather than two.
   */
  private studentSubject = new BehaviorSubject<Profile | null>(null);
  readonly student$ = this.studentSubject.asObservable();

  /**
   * Whether the stored token has been checked against the server on this page
   * load. Cleared on sign-out; set by a sign-in, which the server has just
   * validated by definition.
   */
  private sessionChecked = false;

  /**
   * The signed-in account must choose its own password before anything else opens
   * (QA SEC-001, D9-a). Set by the sign-in answer and by the server's 403
   * `PORTAL_PASSWORD_CHANGE_REQUIRED`; cleared by a successful change.
   */
  mustChangePassword = false;

  constructor(private http: HttpClient) {
    StudentPortalApiService.migrateLegacyToken();
  }

  /** Moves a token stored under an older key name, then removes the old name. */
  private static migrateLegacyToken(): void {
    for (const legacy of LEGACY_TOKEN_KEYS) {
      const stale = readKey(legacy);
      if (stale !== null && readKey(TOKEN_KEY) === null) {
        writeKey(TOKEN_KEY, stale);
      }
      dropKey(legacy);
    }
  }

  getToken(): string | null {
    return readKey(TOKEN_KEY);
  }

  setToken(token: string): void {
    writeKey(TOKEN_KEY, token);
    this.sessionChecked = true;
  }

  /** Forgets the session completely: the token under every name it has had, and the student. */
  clearToken(): void {
    for (const key of [TOKEN_KEY, ...LEGACY_TOKEN_KEYS]) {
      dropKey(key);
    }
    this.sessionChecked = false;
    this.mustChangePassword = false;
    this.studentSubject.next(null);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  /** The student this page load already knows about, or null before the first read. */
  get currentStudent(): Profile | null {
    return this.studentSubject.value;
  }

  /**
   * Turns a stored token back into a session, once per page load.
   *
   * <p>The portal decided "signed in" by the presence of a string in storage,
   * which is true of a token the server expired an hour ago: the student reached
   * a page, every request on it came back 401, and the shell reported a load
   * failure instead of asking them to sign in. The guard now settles that with
   * the server before opening the page.
   *
   * <p>Emits false only when the server has actually refused the token — a 401 or
   * 403, after which it is cleared. A server that could not answer at all leaves
   * the session alone and lets the page open: an outage must not sign a student
   * out, and the pages inside report their own failures.
   *
   * <p>Portal-owned end to end. It deliberately does not go near
   * `UmsAuthService.restoreSession`, which does the same job for staff from an
   * `APP_INITIALIZER`; a student's page load must not touch the ministry session,
   * or a shared browser would have each sign-in interfering with the other.
   */
  restoreSession(): Observable<boolean> {
    if (!this.isLoggedIn()) {
      return of(false);
    }
    if (this.sessionChecked) {
      return of(true);
    }
    return this.getProfile().pipe(
      map(() => {
        this.sessionChecked = true;
        return true;
      }),
      catchError((error: { status?: number; error?: { code?: string } }) => {
        // A session held to the password change is alive: it must not be dropped.
        const holding = error?.status === 403 && error?.error?.code === PORTAL_PASSWORD_CHANGE_REQUIRED;
        if (error?.status === 401 || (error?.status === 403 && !holding)) {
          this.clearToken();
          return of(false);
        }
        // Not an answer about the token: leave the session in place and try again
        // on the next navigation.
        return of(true);
      })
    );
  }

  private headers(): { [key: string]: string } {
    const token = this.getToken();
    return token ? { 'X-Student-Portal-Token': token } : {};
  }

  /**
   * Study countries a student can pick from on the login screen.
   *
   * <p>Anonymous by necessity — the caller has not logged in yet — and that is
   * exactly why it has to be the portal's own endpoint. This used to call
   * `/api/v1/lookup/study-countries-from-student-account` on the grounds that the
   * list is the same one the ministry side uses. It was reachable (that endpoint
   * is `permitAll`) and it always answered `[]`: the list is read from
   * STUDENT_DATA, which is under Row-Level Security, and an anonymous caller has
   * no country scope for the predicate to match. The selector was left with
   * nothing to offer, disabled, and the sign-in could not be completed at all.
   * Everything under the portal auth prefix runs as system, so this one returns
   * the real list. See the handler for why elevating inside the shared service
   * does not work.
   */
  loginCountries(): Observable<LoginCountry[]> {
    return this.http.get<LoginCountry[]>(`${BASE}/auth/study-countries`);
  }

  /**
   * A fresh security-code challenge: the id to send back with the credentials,
   * and an object URL for the image to show.
   *
   * <p>The image is a plain PNG and the id travels in `X-Captcha-Id`, so the
   * response has to be read whole rather than as a body. The caller owns the
   * object URL and must revoke it — see `releaseCaptchaImage` in the login
   * component.
   */
  captcha(): Observable<{ id: string; imageUrl: string }> {
    return this.http.get(`${BASE}/auth/captcha`, { observe: 'response', responseType: 'blob' })
      .pipe(map(response => ({
        id: response.headers.get('X-Captcha-Id') || '',
        imageUrl: URL.createObjectURL(response.body as Blob)
      })));
  }

  /**
   * The country is required and is verified against the student's record; a
   * mismatch comes back as the same 401 as a wrong password, so the UI must not
   * try to tell the two apart.
   *
   * <p>The challenge is required on every call, the second one carrying an
   * e-mail or authenticator code included: the server checks the password before
   * it looks at either code, so a call exempted from the challenge would be a
   * free password oracle. A spent id is gone, so the caller fetches a new one
   * whenever the server asks for a code.
   */
  login(username: string, password: string, studyCountryInId: number,
        captchaId: string, captchaAnswer: string, codes: LoginCodes = {}): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${BASE}/auth/login`,
      { username, password, studyCountryInId, captchaId, captchaAnswer, ...codes });
  }

  // S-11: account security (e-mail verification status, optional authenticator app)
  securityStatus(): Observable<PortalSecurityStatus> {
    return this.http.get<PortalSecurityStatus>(`${BASE}/me/security`, { headers: this.headers() });
  }

  totpStart(): Observable<PortalTotpStart> {
    return this.http.post<PortalTotpStart>(`${BASE}/me/security/totp/start`, {}, { headers: this.headers() });
  }

  totpConfirm(code: string): Observable<{ totpEnabled: boolean }> {
    return this.http.post<{ totpEnabled: boolean }>(`${BASE}/me/security/totp/confirm`, { code }, { headers: this.headers() });
  }

  totpDisable(code: string): Observable<{ totpEnabled: boolean }> {
    return this.http.post<{ totpEnabled: boolean }>(`${BASE}/me/security/totp/disable`, { code }, { headers: this.headers() });
  }

  /** Challenged like the sign-in: it is the one anonymous call that sends mail. */
  forgotPassword(username: string, captchaId: string, captchaAnswer: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${BASE}/auth/forgot-password`,
      { loginKey: username, captchaId, captchaAnswer });
  }

  resetPassword(username: string, otp: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${BASE}/auth/reset-password`, { loginKey: username, otp, newPassword });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${BASE}/me/change-password`,
      { currentPassword, newPassword }, { headers: this.headers() });
  }

  getMyChangeRequests(): Observable<StudentChangeRequest[]> {
    return this.http.get<StudentChangeRequest[]>(`${BASE}/change-requests`, { headers: this.headers() });
  }

  submitChangeRequest(changes: FieldChangeInput[]): Observable<SubmitChangeResponse> {
    return this.http.post<SubmitChangeResponse>(`${BASE}/change-requests`, { changes }, { headers: this.headers() });
  }

  cancelChangeRequest(id: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${BASE}/change-requests/${id}`, { headers: this.headers() });
  }

  /** The fields a student may change, and each one's DATA_TYPE (TEXT, DATE, LOOKUP, EMAIL, PHONE). */
  getAllowedChangeFields(): Observable<{ fields: string[]; types?: Record<string, string> }> {
    return this.http.get<{ fields: string[]; types?: Record<string, string> }>(
      `${BASE}/change-requests/allowed-fields`, { headers: this.headers() });
  }

  logout(): Observable<void> {
    const token = this.getToken();
    return this.http.post<void>(`${BASE}/auth/logout`, {}, {
      headers: token ? { 'X-Student-Portal-Token': token } : {}
    });
  }

  getProfile(): Observable<Profile> {
    return this.http.get<Profile>(`${BASE}/me`, { headers: this.headers() })
      .pipe(tap(profile => this.studentSubject.next(profile ?? null)));
  }

  getMyData(): Observable<MyData> {
    return this.http.get<MyData>(`${BASE}/me/data`, { headers: this.headers() });
  }

  getProgress(): Observable<StudentProgressDto> {
    return this.http.get<StudentProgressDto>(`${BASE}/me/progress`, { headers: this.headers() });
  }

  getYearResults(): Observable<StudentYearResultDto[]> {
    return this.http.get<StudentYearResultDto[]>(`${BASE}/me/year-results`, { headers: this.headers() });
  }

  getDocuments(): Observable<StudentDocument[]> {
    return this.http.get<StudentDocument[]>(`${BASE}/me/documents`, { headers: this.headers() });
  }

  downloadDocument(id: number): Observable<Blob> {
    return this.http.get(`${BASE}/me/documents/${id}/download`, {
      headers: this.headers(),
      responseType: 'blob'
    });
  }

  /**
   * A document with the name it was uploaded under, read from the answer's
   * Content-Disposition (L12, P15: a reply attachment was saved as "response-document",
   * with no extension). `fallback` when the server names none.
   */
  downloadNamedDocument(id: number, fallback = 'document'): Observable<{ blob: Blob; filename: string }> {
    return this.http.get(`${BASE}/me/documents/${id}/download`, {
      headers: this.headers(),
      responseType: 'blob',
      observe: 'response'
    }).pipe(map(res => ({
      blob: res.body as Blob,
      filename: filenameFromDisposition(res.headers.get('Content-Disposition')) || fallback
    })));
  }

  /** The document types a student may upload under (live QA #15). */
  getDocumentTypes(): Observable<PortalLookupItem[]> {
    return this.http.get<PortalLookupItem[]>(`${BASE}/me/document-types`, { headers: this.headers() });
  }

  uploadDocument(file: File, documentName?: string, documentType?: string): Observable<{ id: number; message: string }> {
    const form = new FormData();
    form.append('file', file);
    if (documentName) form.append('documentName', documentName);
    if (documentType) form.append('documentType', documentType);
    return this.http.post<{ id: number; message: string }>(`${BASE}/me/documents`, form, { headers: this.headers() });
  }

  getInquiries(page = 0, size = 10): Observable<{ content: StudentInquiry[]; totalElements: number; totalPages: number }> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<{ content: StudentInquiry[]; totalElements: number; totalPages: number }>(
      `${BASE}/me/inquiries`,
      { headers: this.headers(), params }
    );
  }

  getInquiry(id: number): Observable<StudentInquiry> {
    return this.http.get<StudentInquiry>(`${BASE}/me/inquiries/${id}`, { headers: this.headers() });
  }

  createInquiry(type: string, title: string, description?: string): Observable<StudentInquiry> {
    return this.http.post<StudentInquiry>(`${BASE}/me/inquiries`, { type, title, description }, { headers: this.headers() });
  }

  getInfoRequests(page = 0, size = 10, status?: string, requestType?: string): Observable<{ content: StudentInfoRequest[]; totalElements: number; totalPages: number }> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size));
    if (status) params = params.set('status', status);
    if (requestType) params = params.set('requestType', requestType);
    return this.http.get<{ content: StudentInfoRequest[]; totalElements: number; totalPages: number }>(
      `${BASE}/me/info-requests`,
      { headers: this.headers(), params }
    );
  }

  getInfoRequest(id: number): Observable<StudentInfoRequest> {
    return this.http.get<StudentInfoRequest>(`${BASE}/me/info-requests/${id}`, { headers: this.headers() });
  }

  respondToInfoRequest(
    id: number,
    responseText?: string,
    file?: File,
    proposedStudentData?: object,
    proposedFinanceData?: object
  ): Observable<StudentInfoRequest> {
    const form = new FormData();
    if (responseText != null) form.append('responseText', responseText);
    if (file) form.append('file', file);
    if (proposedStudentData != null) form.append('proposedStudentData', JSON.stringify(proposedStudentData));
    if (proposedFinanceData != null) form.append('proposedFinanceData', JSON.stringify(proposedFinanceData));
    return this.http.post<StudentInfoRequest>(`${BASE}/me/info-requests/${id}/respond`, form, { headers: this.headers() });
  }

  /** Travel tickets for the logged-in student (read-only). Backend: GET /mohe/api/v1/student-portal/me/travel-tickets */
  getTravelTickets(): Observable<PortalTravelTicket[]> {
    return this.http.get<PortalTravelTicket[]>(`${BASE}/me/travel-tickets`, { headers: this.headers() });
  }

  /** Extensions for the logged-in student (read-only). Backend: GET /mohe/api/v1/student-portal/me/extensions */
  /** The PDF of one of the student's own tickets (review T3). */
  getTravelTicketDocument(ticketId: number): Observable<Blob> {
    return this.http.get(`${BASE}/me/travel-tickets/${ticketId}/document`, { headers: this.headers(), responseType: 'blob' });
  }

  getExtensions(): Observable<PortalExtension[]> {
    return this.http.get<PortalExtension[]>(`${BASE}/me/extensions`, { headers: this.headers() });
  }

  private lookupsCache: PortalLookups | null = null;

  /** Fetch all reference/lookup data needed by student portal pages. Results are cached per session. */
  getLookups(): Observable<PortalLookups> {
    if (this.lookupsCache) return of(this.lookupsCache);

    const lookupBase = `${environment.apiUrl}/mohe/api/lookup-tables`;
    /*
     * One full page per list. The endpoints are paged with a default of ten rows, so
     * every id past the tenth printed raw on My Data and the change-request dialog
     * offered ten options per list (live QA N8 / SP-004). 500 is the server's page
     * cap; the largest list read here (study majors) holds about 330.
     */
    const all = (path: string) => `${lookupBase}${path}?page=0&size=500`;
    const safe = (obs: Observable<any>) => obs.pipe(timeout(8000), catchError(() => of([])));
    const ext = (res: any, idCands: string[], nameCands: string[]): PortalLookupItem[] => {
      const arr: any[] = res?.content ?? (Array.isArray(res) ? res : []);
      if (!arr.length) return [];
      const s = arr[0];
      const idKey = idCands.find(k => k in s) ?? idCands[idCands.length - 1];
      const nameKey = nameCands.find(k => k in s) ?? nameCands[nameCands.length - 1];
      // The English twin of the name column, in either spelling the tables use.
      const enKey = [nameKey + 'En', nameKey + '_EN'].find(k => k in s);
      return arr
        // Switched-off rows are not offered: the gender list held an inactive "test" (live #29).
        .filter(x => x?.isActive !== false && x?.IS_ACTIVE !== false)
        .map(x => ({ id: Number(x[idKey]), name: String(x[nameKey] ?? ''), nameEn: enKey ? String(x[enKey] ?? '') : undefined }))
        .filter(x => !isNaN(x.id));
    };

    return forkJoin({
      genders:             safe(this.http.get(all('/student-gender')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentGenderName', 'name']))),
      studentCountries:    safe(this.http.get(all('/country')))
                             .pipe(map((r: any) => ext(r, ['studentCountryId', 'id'], ['studentCountryName', 'name']))),
      studentGovernments:  safe(this.http.get(all('/governorate')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentGovName', 'name']))),
      studentMunicipalities: safe(this.http.get(all('/municipality')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentMudName', 'name']))),
      degrees:             safe(this.http.get(all('/student-degree')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentDegreeName', 'name']))),
      currentLevels:       safe(this.http.get(all('/student-current-level')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentCurrentLevelName', 'name']))),
      studyTypes:          safe(this.http.get(all('/student-study-type')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentStudyTypeName', 'name']))),
      studyStatuses:       safe(this.http.get(all('/student-study-status')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentStudyStatusName', 'name']))),
      studyCountries:      safe(this.http.get(all('/study-country-in')))
                             .pipe(map((r: any) => ext(r, ['STUDY_COUNTRY_IN_ID', 'id'], ['STUDY_COUNTRY_IN_NAME', 'studyCountryInName', 'name']))),
      studyGovernments:    safe(this.http.get(all('/study-gov-in')))
                             .pipe(map((r: any) => ext(r, ['STUDY_GOVERNORATE_IN_ID', 'STUDY_GOV_IN_ID', 'id'], ['STUDY_GOVERNORATE_IN_NAME', 'studyGovInName', 'name']))),
      studyUniversities:   safe(this.http.get(all('/study-university-in')))
                             .pipe(map((r: any) => ext(r, ['STUDY_UNIVERSITY_IN_ID', 'id'], ['STUDY_UNIVERSITY_IN_NAME', 'studyUniversityInName', 'name']))),
      studyColleges:       safe(this.http.get(all('/study-college-in')))
                             .pipe(map((r: any) => ext(r, ['STUDY_COLLEGE_IN_ID', 'id'], ['STUDY_COLLEGE_IN_NAME', 'studyCollegeInName', 'name']))),
      studyEducations:     safe(this.http.get(all('/study-education-in')))
                             .pipe(map((r: any) => ext(r, ['STUDY_EDUCATION_IN_ID', 'id'], ['STUDY_EDUCATION_IN_NAME', 'studyEductionName', 'name']))),
      studyMajors:         safe(this.http.get(all('/study-major-in')))
                             .pipe(map((r: any) => ext(r, ['STUDY_MAJOR_IN_ID', 'id'], ['STUDY_MAJOR_IN_NAME', 'studyMajorName', 'name']))),
      agencyTypes:         safe(this.http.get(all('/student-agency-type')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentAgencyTypeName', 'name']))),
      ministryTypes:       safe(this.http.get(all('/student-ministry-type')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentMinistryTypeName', 'name']))),
      universityTypes:     safe(this.http.get(all('/student-university-type')))
                             .pipe(map((r: any) => ext(r, ['id'], ['studentUniversityTypeName', 'name']))),
    }).pipe(tap(result => { this.lookupsCache = result; }));
  }
}

export interface PortalTravelTicketFamilyMember {
  memberName?: string;
  relationship?: string;
  birthdate?: string;
  passportNumber?: string;
  passportExpiryDate?: string;
  notes?: string;
  [key: string]: unknown;
}

export interface PortalTravelTicket {
  travelTicketId?: number;
  studentId?: number;
  travelType?: string;
  travelDate?: string;
  departureCountry?: string;
  destinationCountry?: string;
  departureCity?: string;
  destinationCity?: string;
  airline?: string;
  flightNumber?: string;
  ticketNumber?: string;
  ticketCost?: number;
  bookingDate?: string;
  travelPurpose?: string;
  notes?: string;
  createdDate?: string;
  /** The ticket PDF, when one is attached (review T3). */
  documentId?: number | null;
  familyMembers?: PortalTravelTicketFamilyMember[];
}

export interface PortalExtension {
  extensionId: number;
  studentId?: number;
  extensionType?: string;
  /** EXTENSION_TYPE_CODE as the server resolved it from the stored value; null when no row matches (QA SP-009). */
  extensionTypeCode?: string | null;
  /** The type named in the request language by the server, for a client whose code lists did not load. */
  extensionTypeLabel?: string;
  extensionQuarters?: number | null;
  extensionStartDate?: string | null;
  extensionEndDate?: string | null;
  extensionReason?: string | null;
  extensionReasonDetails?: string | null;
  extensionStatus?: string | null;
  /** EXTENSION_STATUS_CODE resolved from the stored value, an English name on most rows (QA SP-009). */
  extensionStatusCode?: string | null;
  /** The status named in the request language by the server. */
  extensionStatusLabel?: string | null;
  extensionDecisionNo?: string | null;
  extensionDecisionDate?: string | null;
  approvedBy?: string | null;
  notes?: string | null;
  createdDate?: string | null;
}

/**
 * The file name in a Content-Disposition header: the UTF-8 `filename*` form first (an
 * Arabic name), then the plain `filename`. Empty when there is none.
 */
export function filenameFromDisposition(header: string | null | undefined): string {
    if (!header) return '';
    const star = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header);
    if (star) {
        try { return decodeURIComponent(star[1].trim().replace(/^"|"$/g, '')); } catch { /* fall through */ }
    }
    const plain = /filename\s*=\s*"?([^";]+)"?/.exec(header);
    return plain ? plain[1].trim() : '';
}
