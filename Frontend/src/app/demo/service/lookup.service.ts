import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, Observable, of } from 'rxjs';
import { map, catchError, switchMap, tap } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { TranslationService } from './translation.service';
import { ApiUrlService } from './api-url.service';
import { silentErrors } from '../interceptors/http-context.tokens';

import { StudyCountryIn } from '../api/study-country-in';

/** Cache TTL in milliseconds for rarely changing lookups (default 5 min). */
const CACHE_TTL_MS = 5 * 60 * 1000;

/** What one page of a lookup table can hold: `LookupTablePageUtil.MAX_PAGE_SIZE`. */
const LOOKUP_MAX_PAGE_SIZE = 500;

interface CacheEntry<T> {
  data: T;
  expires: number;
}
import { StudyGovIn } from '../api/study-gov-in';
import { StudyFinanceBank } from '../api/study-finance-bank';
import { StudyFinanceBankBranch } from '../api/study-finance-bank-branch';
import { StudyFinanceDof } from '../api/study-finance-dof';
import { StudyFinReceivedStatus } from '../api/study-fin-received-status';
import { StudyReceivedWhomStatus } from '../api/study-received-whom-status';
import { StudyFinReceivedWhom } from '../api/study-fin-received-whom';
import { StudentCountry } from '../api/student-country';

export interface LookupItem {
  id: number;
  name: string;
  label?: string;
  value?: any;
}

@Injectable({
  providedIn: 'root'
})
export class LookupService {

  private readonly cache = new Map<string, CacheEntry<unknown>>();

  constructor(
    private httpClient: HttpClient,
    private messageService: MessageService,
    private translationService: TranslationService,
    private apiUrl: ApiUrlService
  ) {
    // Clear lookup cache whenever the UI language changes so dropdowns reload in the new language.
    this.translationService.currentLanguage$.subscribe(() => this.clearCache());
  }

  /** Return cached value if present and not expired. */
  private getCached<T>(key: string): T | null {
    const entry = this.cache.get(key) as CacheEntry<T> | undefined;
    if (!entry || Date.now() > entry.expires) return null;
    return entry.data;
  }

  /** Store value in cache with TTL. */
  private setCache<T>(key: string, data: T): void {
    this.cache.set(key, { data, expires: Date.now() + CACHE_TTL_MS });
  }

  /** Clear all cached lookups (e.g. when admin updates lookup tables). */
  clearCache(): void {
    this.cache.clear();
  }

  /** Show user-facing message when a lookup request fails. */
  private showLookupError(): void {
    this.messageService.add({
      severity: 'error',
      summary: this.translationService.translate('COMMON.ERROR'),
      detail: this.translationService.translate('COMMON.LOAD_ERROR')
    });
  }

  // Helper function to map API response to LookupItem format.
  // nameFieldEn: optional English field name; used when UI language is 'en'.
  /**
   * Returns false only when a lookup record is explicitly inactive.
   * Supports the different "active" conventions across lookup tables:
   *   - isActive (most legacy + study/finance tables)
   *   - active   (LU_TRAVEL_TYPE, FIN_RECEIVED_REASON)
   *   - isDeleted (LU_* status tables — active = not deleted)
   * Records with no flag are treated as active.
   */
  private isItemActive(item: any): boolean {
    if (!item) return true;
    if (item.isActive === false || item.isActive === 0) return false;
    if (item.active === false || item.active === 0) return false;
    if (item.isDeleted === true || item.isDeleted === 1) return false;
    return true;
  }

  /**
   * Every row of a lookup table, not the first page of it.
   *
   * <p>The `/api/lookup-tables/*` endpoints are paged and default to
   * `size=10` (LookupTableController), so a dropdown that asked for a list got
   * ten rows and no sign that the rest existed. On the study-years screen that
   * meant ten majors out of 444: all 49 rules printed a raw major id instead of
   * a name, 42 of them a raw degree id, and the add dialog could only offer the
   * ten majors it had. The one earlier sighting of this was patched in place
   * with `&size=1000` on a single call.
   *
   * <p>`LookupTablePageUtil.MAX_PAGE_SIZE` caps a page at 500, so asking for a
   * larger size does not help — a table above the cap has to be read page by
   * page, and `totalElements` is what says whether it is.
   */
  private fetchAllLookupRows(path: string): Observable<any[]> {
    const base = `${this.apiUrl.apiPath('/api/lookup-tables/')}${path}`;
    const pageUrl = (page: number) =>
      `${base}${base.includes('?') ? '&' : '?'}page=${page}&size=${LOOKUP_MAX_PAGE_SIZE}`;

    return this.httpClient.get<any>(pageUrl(0)).pipe(
      switchMap(first => {
        const rows: any[] = first?.content ?? first ?? [];
        if (!Array.isArray(rows)) return of([]);
        const total: number = first?.totalElements ?? rows.length;
        if (rows.length >= total) return of(rows);

        const rest: Observable<any[]>[] = [];
        for (let page = 1; page * LOOKUP_MAX_PAGE_SIZE < total; page++) {
          rest.push(this.httpClient.get<any>(pageUrl(page)).pipe(map(r => r?.content ?? [])));
        }
        return forkJoin(rest).pipe(map(pages => rows.concat(...pages)));
      })
    );
  }

  private mapToLookupItems(response: any, idField: string = 'id', nameField: string = 'name', nameFieldEn?: string): LookupItem[] {
    const raw = response?.content || response;
    if (!Array.isArray(raw)) return [];
    // Exclude inactive records from dropdown lists.
    const data = raw.filter((item: any) => this.isItemActive(item));
    const sample = data[0] || {};
    const isEnglish = this.translationService.getCurrentLanguage() === 'en';

    const resolvedIdField = Object.prototype.hasOwnProperty.call(sample, idField)
      ? idField
      : Object.keys(sample).find(k => /(^id$|_id$|Id$|ID$)/.test(k)) || 'id';

    const resolvedNameField = Object.prototype.hasOwnProperty.call(sample, nameField)
      ? nameField
      : Object.keys(sample).find(k => /name$/i.test(k)) || 'name';

    const resolvedNameFieldEn = nameFieldEn && Object.prototype.hasOwnProperty.call(sample, nameFieldEn)
      ? nameFieldEn
      : undefined;

    return data.map((item: any) => {
      const rawId = item[resolvedIdField];
      const numericId = typeof rawId === 'string' ? parseInt(rawId, 10) : rawId;
      // Prefer English name when in English UI; fall back to Arabic name if English value is null/empty.
      const displayName = (isEnglish && resolvedNameFieldEn && item[resolvedNameFieldEn])
        ? item[resolvedNameFieldEn]
        : item[resolvedNameField];
      return {
        id: numericId,
        name: displayName,
        label: displayName,
        value: numericId
      } as LookupItem;
    });
  }

  /**
   * Localize raw lookup entities in-place for display: set the Arabic name property
   * to the value matching the current UI language (English when UI is English and an
   * English value exists, otherwise Arabic). These lists are display-only in dropdowns
   * (optionValue is the id), so overriding the display name field is safe.
   */
  private localizeNames(list: any[], arField: string, enField: string): any[] {
    if (!Array.isArray(list)) return list;
    const isEnglish = this.translationService.getCurrentLanguage() === 'en';
    return list.filter((item: any) => this.isItemActive(item)).map((item: any) => {
      if (!item) return item;
      const ar = item[arField];
      const en = item[enField];
      const chosen = isEnglish ? (en ?? ar) : (ar ?? en);
      return { ...item, [arField]: chosen };
    });
  }

  // Study Country methods
  getAllStudyCountries(): Observable<StudyCountryIn[]> {
    return this.httpClient.get<StudyCountryIn[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-countries`).pipe(
      map(data => this.localizeNames(data, 'studyCountryInName', 'studyCountryInNameEn'))
    );
  }

  /** Study countries present in STUDENT_ACCOUNT (for main system login country selector). */
  getStudyCountriesFromStudentAccount(): Observable<StudyCountryIn[]> {
    return this.httpClient.get<StudyCountryIn[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-countries-from-student-account`).pipe(
      map(data => this.localizeNames(data, 'studyCountryInName', 'studyCountryInNameEn'))
    );
  }

  /** Study countries referenced in active UMS user-privilege assignments (for login country selector). */
  getStudyCountriesForLogin(): Observable<StudyCountryIn[]> {
    return this.httpClient.get<StudyCountryIn[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-countries-for-login`).pipe(
      map(data => this.localizeNames(data, 'studyCountryInName', 'studyCountryInNameEn'))
    );
  }

  // Study Government methods
  getAllStudyGovernments(): Observable<StudyGovIn[]> {
    return this.httpClient.get<StudyGovIn[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-governments`).pipe(
      map(data => this.localizeNames(data, 'studyGovInName', 'studyGovInNameEn'))
    );
  }

  getStudyGovernmentsByCountry(countryId: number): Observable<StudyGovIn[]> {
    return this.httpClient.get<StudyGovIn[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-governments/by-country/${countryId}`).pipe(
      map(data => this.localizeNames(data, 'studyGovInName', 'studyGovInNameEn'))
    );
  }

  // Study Finance Bank methods
  getAllStudyFinanceBanks(): Observable<StudyFinanceBank[]> {
    return this.httpClient.get<StudyFinanceBank[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-finance-banks`).pipe(
      map(data => this.localizeNames(data, 'studyFinanceBankeName', 'studyFinanceBankeNameEn'))
    );
  }

  getStudyFinanceBanksByCountry(countryId: number): Observable<StudyFinanceBank[]> {
    return this.httpClient.get<StudyFinanceBank[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-finance-banks/by-country/${countryId}`).pipe(
      map(data => this.localizeNames(data, 'studyFinanceBankeName', 'studyFinanceBankeNameEn'))
    );
  }

  // Study Finance Bank Branch methods
  getAllStudyFinanceBankBranches(): Observable<StudyFinanceBankBranch[]> {
    return this.httpClient.get<StudyFinanceBankBranch[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-finance-bank-branches`).pipe(
      map(data => this.localizeNames(data, 'studyFinanceBankBranchName', 'studyFinanceBankBranchNameEn'))
    );
  }

  getStudyFinanceBankBranchesByBank(bankId: number): Observable<StudyFinanceBankBranch[]> {
    return this.httpClient.get<StudyFinanceBankBranch[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-finance-bank-branches/by-bank/${bankId}`).pipe(
      map(data => this.localizeNames(data, 'studyFinanceBankBranchName', 'studyFinanceBankBranchNameEn'))
    );
  }

  // Study Finance DOF methods
  getAllStudyFinanceDofs(): Observable<StudyFinanceDof[]> {
    return this.httpClient.get<StudyFinanceDof[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-finance-dofs`).pipe(
      map(data => this.localizeNames(data, 'studyFinanceDofName', 'studyFinanceDofNameEn'))
    );
  }

  // Study Finance Received Status methods
  getAllStudyFinReceivedStatuses(): Observable<StudyFinReceivedStatus[]> {
    return this.httpClient.get<StudyFinReceivedStatus[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-fin-received-statuses`).pipe(
      map(data => this.localizeNames(data, 'studyFinReceivedStatusName', 'studyFinReceivedStatusNameEn'))
    );
  }

  // Study Received Whom Status methods
  getAllStudyReceivedWhomStatuses(): Observable<StudyReceivedWhomStatus[]> {
    return this.httpClient.get<StudyReceivedWhomStatus[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-received-whom-statuses`).pipe(
      map(data => this.localizeNames(data, 'studyReceivedWhomStatusName', 'studyReceivedWhomStatusNameEn'))
    );
  }

  // Study Fin Received Whom methods
  getAllStudyFinReceivedWhoms(): Observable<StudyFinReceivedWhom[]> {
    return this.httpClient.get<StudyFinReceivedWhom[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/study-fin-received-whoms`);
  }

  // Student Country methods
  getAllStudentCountries(): Observable<StudentCountry[]> {
    return this.httpClient.get<StudentCountry[]>(`${this.apiUrl.apiPath('/api/v1/lookup')}/student-countries`).pipe(
      map(data => this.localizeNames(data, 'studentCountryName', 'studentCountryNameEn'))
    );
  }

  // Legacy methods for backward compatibility with existing components
  // These methods will use the existing LookupTableController endpoints
  
  getStudentGenders(): Observable<LookupItem[]> {
    const cached = this.getCached<LookupItem[]>('student-gender');
    if (cached) return of(cached);
    return this.fetchAllLookupRows('student-gender').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentGenderName', 'studentGenderNameEn')),
      tap(data => this.setCache('student-gender', data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getCountries(): Observable<LookupItem[]> {
    const cached = this.getCached<LookupItem[]>('country');
    if (cached) return of(cached);
    return this.fetchAllLookupRows('country').pipe(
      map(response => this.mapToLookupItems(response, 'studentCountryId', 'studentCountryName', 'studentCountryNameEn')),
      tap(data => this.setCache('country', data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  /** All world countries from STUDENT_COUNTRY_ALL — used for birth place & passport issue country. Yemen = ID 1. */
  getCountriesAll(): Observable<LookupItem[]> {
    const cached = this.getCached<LookupItem[]>('country-all');
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}country-all`).pipe(
      map((rows: any[]) => {
        const isEn = this.translationService.getCurrentLanguage() === 'en';
        return (rows || []).map(r => ({
          value: r.id,
          id: r.id,
          name: isEn
            ? (r.studentCountryAllNameEn || r.studentCountryAllName || '')
            : (r.studentCountryAllName || r.studentCountryAllNameEn || ''),
          label: isEn
            ? (r.studentCountryAllNameEn || r.studentCountryAllName || '')
            : (r.studentCountryAllName || r.studentCountryAllNameEn || '')
        }));
      }),
      tap(data => this.setCache('country-all', data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getStudentDegrees(): Observable<LookupItem[]> {
    const cached = this.getCached<LookupItem[]>('student-degree');
    if (cached) return of(cached);
    return this.fetchAllLookupRows('student-degree').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentDegreeName', 'studentDegreeNameEn')),
      tap(data => this.setCache('student-degree', data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getStudentMajors(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('student-major').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentMajorName', 'studentMajorNameEn'))
    );
  }

  getCurrentLevels(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('student-current-level').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentCurrentLevelName', 'studentCurrentLevelNameEn'))
    );
  }

  getStudyStatuses(): Observable<LookupItem[]> {
    const cached = this.getCached<LookupItem[]>('study-status');
    if (cached) return of(cached);
    return this.fetchAllLookupRows('study-status').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studyStatusName', 'statusNameEn')),
      tap(data => this.setCache('study-status', data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getStudentStudyTypes(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('student-study-type').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentStudyTypeName', 'studentStudyTypeNameEn'))
    );
  }

  getUniversityTypes(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('student-university-type').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentUniversityTypeName', 'studentUniversityTypeNameEn'))
    );
  }

  getUniversityTypesByAgency(agencyTypeId: number): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('student-university-type').pipe(
      map((response: any) => {
        const data = response.content || response;
        const filtered = data.filter((item: any) => Number(item.studentAgencyTypeId) === Number(agencyTypeId));
        return this.mapToLookupItems({ content: filtered }, 'id', 'studentUniversityTypeName', 'studentUniversityTypeNameEn');
      })
    );
  }

  getAgencyTypes(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('student-agency-type').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentAgencyTypeName', 'studentAgencyTypeNameEn'))
    );
  }

  getMinistryTypes(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('student-ministry-type').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentMinistryTypeName', 'studentMinistryTypeNameEn'))
    );
  }

  getMinistryTypesByAgency(agencyTypeId: number): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('student-ministry-type').pipe(
      map((response: any) => {
        const data = response.content || response;
        const filtered = data.filter((item: any) => Number(item.studentAgencyTypeId) === Number(agencyTypeId));
        return this.mapToLookupItems({ content: filtered }, 'id', 'studentMinistryTypeName', 'studentMinistryTypeNameEn');
      })
    );
  }

  getGovernments(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('governorate').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentGovName', 'studentGovNameEn'))
    );
  }

  // Filtered governments by country (client-side filter based on API payload fields)
  getGovernmentsByCountry(countryId: number): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('governorate').pipe(
      map((response: any) => {
        const data = response.content || response;
        const filtered = data.filter((item: any) => Number(item.studentCountryId) === Number(countryId));
        return this.mapToLookupItems({ content: filtered }, 'id', 'studentGovName', 'studentGovNameEn');
      })
    );
  }

  getMunicipalities(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('municipality').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentMudName', 'studentMudNameEn'))
    );
  }

  getMunicipalitiesByGovernment(governmentId: number, countryId?: number): Observable<LookupItem[]> {
    let path = `municipality?govId=${governmentId}`;
    if (countryId) path += `&countryId=${countryId}`;
    return this.fetchAllLookupRows(path).pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentMudName', 'studentMudNameEn'))
    );
  }

  

  getStudyGovsIn(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-gov-in').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studyGovInName', 'studyGovInNameEn'))
    );
  }

  getStudyUniversitiesIn(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-university-in').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studyUniversityInName', 'studyUniversityInNameEn'))
    );
  }

  getStudyCollegesIn(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-college-in').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studyCollegeInName', 'studyCollegeInNameEn'))
    );
  }



  getStudyMajorsIn(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-major-in').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studyMajorName', 'studyMajorNameEn'))
    );
  }

  // Study Currency (for amounts without symbols)
  getStudyCurrencies(): Observable<LookupItem[]> {
    const cached = this.getCached<LookupItem[]>('study-currency');
    if (cached) return of(cached);
    // There is no second source. The fallback that used to sit here called
    // /api/v1/lookup/study-currency-in, which the backend does not serve — that
    // prefix carries 28 paths and this is not one of them, so it answered 404.
    // It only ran when the primary came back empty, which turned "no currencies
    // are configured" into the generic lookup error toast.
    const primary$ = this.fetchAllLookupRows('study-currency-in');

    const mapResponse = (response: any): LookupItem[] => {
      const data = response?.content || response;
      if (!Array.isArray(data) || data.length === 0) return [];
      const sample = data[0] || {};
      const idKey = Object.prototype.hasOwnProperty.call(sample, 'STUDY_CURRENCY_IN_ID') ? 'STUDY_CURRENCY_IN_ID'
                  : Object.prototype.hasOwnProperty.call(sample, 'studyCurrencyInId') ? 'studyCurrencyInId'
                  : Object.prototype.hasOwnProperty.call(sample, 'id') ? 'id' : 'id';
      const nameKey = Object.prototype.hasOwnProperty.call(sample, 'STUDY_CURRENCY_IN_NAME') ? 'STUDY_CURRENCY_IN_NAME'
                    : Object.prototype.hasOwnProperty.call(sample, 'studyCurrencyInName') ? 'studyCurrencyInName'
                    : Object.prototype.hasOwnProperty.call(sample, 'name') ? 'name' : 'name';
      const nameEnKey = Object.prototype.hasOwnProperty.call(sample, 'STUDY_CURRENCY_IN_NAME_EN') ? 'STUDY_CURRENCY_IN_NAME_EN'
                      : Object.prototype.hasOwnProperty.call(sample, 'studyCurrencyInNameEn') ? 'studyCurrencyInNameEn'
                      : undefined;
      return this.mapToLookupItems({ content: data }, idKey, nameKey, nameEnKey);
    };

    return primary$.pipe(
      map(mapResponse),
      tap(list => { if (list?.length) this.setCache('study-currency', list); }),
      catchError(() => {
        this.showLookupError();
        return of([] as LookupItem[]);
      })
    );
  }

  /**
   * The id of the active STUDY_CURRENCY_IN row with this ISO 4217 code (V039 added
   * ISO_CODE), or null when no row carries it.
   */
  getCurrencyIdByIso(iso: string): Observable<number | null> {
    const code = iso.toUpperCase();
    const key = `study-currency-iso-${code}`;
    const cached = this.getCached<number>(key);
    if (cached) return of(cached);
    return this.fetchAllLookupRows('study-currency-in').pipe(
      map(rows => {
        const row = rows.find(r => this.isItemActive(r)
          && String(r?.isoCode ?? r?.ISO_CODE ?? '').trim().toUpperCase() === code);
        const id = Number(row?.id ?? row?.studyCurrencyInId ?? row?.STUDY_CURRENCY_IN_ID);
        return Number.isFinite(id) && id > 0 ? id : null;
      }),
      tap(id => { if (id) this.setCache(key, id); }),
      catchError(() => of(null))
    );
  }

  /**
   * The system's default currency: the STUDY_CURRENCY_IN row flagged
   * IS_DEFAULT (the entity field is `isDefault`). When the payload carries no
   * such flag on any row the first row is taken — and that is a data gap to
   * fix in the table, not a rule the screen should learn.
   */
  getDefaultCurrency(): Observable<LookupItem | null> {
    const key = 'study-currency-default';
    const cached = this.getCached<LookupItem | null>(key);
    if (cached) return of(cached);
    return this.fetchAllLookupRows('study-currency-in').pipe(
      map(rows => {
        const active = rows.filter(r => this.isItemActive(r));
        const flagged = active.find(r => r?.isDefault === true || r?.isDefault === 1 || r?.IS_DEFAULT === true || r?.IS_DEFAULT === 1);
        const row = flagged ?? active[0];
        if (!row) return null;
        const isEn = this.translationService.getCurrentLanguage() === 'en';
        const name = (isEn ? (row.studyCurrencyInNameEn ?? row.STUDY_CURRENCY_IN_NAME_EN) : null)
          ?? row.studyCurrencyInName ?? row.STUDY_CURRENCY_IN_NAME ?? row.name ?? '';
        const id = row.id ?? row.studyCurrencyInId ?? row.STUDY_CURRENCY_IN_ID;
        return { id: Number(id), name: String(name), label: String(name), value: id } as LookupItem;
      }),
      tap(item => { if (item) this.setCache(key, item); }),
      catchError(() => of(null))
    );
  }

  // ===== Study Location Lookups =====
  getStudyCountriesIn(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-country-in')
      .pipe(
        map((res: any) => this.mapDynamic(res, ['STUDY_COUNTRY_IN_ID', 'id'], ['STUDY_COUNTRY_IN_NAME', 'name'], ['STUDY_COUNTRY_IN_NAME_EN', 'studyCountryInNameEn']))
      );
  }

  getStudyGovernoratesInByCountry(countryId: number): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-gov-in')
      .pipe(
        map((res: any) => {
          const raw = (res?.content || res) as any[];
          const list = this.mapDynamic(
            res,
            ['STUDY_GOVERNORATE_IN_ID', 'STUDY_GOV_IN_ID', 'id'],
            ['STUDY_GOVERNORATE_IN_NAME', 'name'],
            ['STUDY_GOV_IN_NAME_EN', 'studyGovInNameEn']
          );
          const fkCandidates = ['STUDY_COUNTRY_IN_ID', 'studyCountryInId', 'countryId'];
          const hasFk = Array.isArray(raw) && raw.some(r => fkCandidates.some(k => Object.prototype.hasOwnProperty.call(r, k)));
          if (!hasFk) {
            return list; // cannot reliably filter → return all to avoid empty UI
          }
          const filtered = list.filter((_, idx) => {
            const rawItem = raw?.[idx] || {};
            const fkVal = fkCandidates.map(k => rawItem?.[k]).find(v => v !== undefined);
            return Number(fkVal) === Number(countryId);
          });
          // Nothing matched is an answer (live QA M10): falling back to every country's
          // rows offered another attaché's places.
          return filtered;
        })
      );
  }

  /**
   * The universities of one study country, narrowed to a governorate when one is given;
   * rows naming no country or governorate ("Not Specified") come with every one. Filtered
   * on the server (live QA M10): the wizard used to fetch them all and, when nothing
   * matched the governorate, showed every country's universities.
   */
  getStudyUniversitiesOfCountry(countryId: number, governorateId?: number | null): Observable<LookupItem[]> {
    const gov = governorateId ? `&govId=${governorateId}` : '';
    return this.fetchAllLookupRows(`study-university-in?countryId=${countryId}${gov}`)
      .pipe(
        map((res: any) => this.mapDynamic(res, ['STUDY_UNIVERSITY_IN_ID', 'id'], ['STUDY_UNIVERSITY_IN_NAME', 'name'], ['STUDY_UNIVERSITY_IN_NAME_EN', 'studyUniversityInNameEn'])),
        catchError(() => of([]))
      );
  }

  getStudyEducationsIn(): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-education-in')
      .pipe(
        map((res: any) => this.mapDynamic(res, ['STUDY_EDUCATION_IN_ID', 'id'], ['STUDY_EDUCATION_IN_NAME', 'name'], ['STUDY_EDUCTION_NAME_EN', 'studyEductionNameEn']))
      );
  }

  getStudyEducationsInByCollege(collegeInId: number): Observable<LookupItem[]> {
    return this.fetchAllLookupRows(`study-education-in?collegeInId=${collegeInId}`)
      .pipe(
        map((res: any) => {
          if (Array.isArray(res)) {
            return this.mapDynamic({ content: res }, ['STUDY_EDUCATION_IN_ID', 'id'], ['STUDY_EDUCATION_IN_NAME', 'studyEductionName', 'name'], ['STUDY_EDUCTION_NAME_EN', 'studyEductionNameEn']);
          }
          return this.mapDynamic(res, ['STUDY_EDUCATION_IN_ID', 'id'], ['STUDY_EDUCATION_IN_NAME', 'studyEductionName', 'name'], ['STUDY_EDUCTION_NAME_EN', 'studyEductionNameEn']);
        })
      );
  }

  /**
   * How many years this degree and major take at a given attaché.
   *
   * `countryId` is the *student's* study country, not the caller's — each attaché
   * may set its own rule, and a row with no country is the shared default that
   * applies where none has been set. Omitting it falls back to the caller's own
   * attaché when they serve exactly one, and to the default otherwise.
   *
   * This and `getStudyAmount` only pre-fill fields the user can type in, so a failure
   * (a database missing the per-country column, a refused permission) leaves the field
   * as it was without a toast: an error there would read as if choosing the major or
   * the agency type had failed.
   */
  getStudyYears(degreeId: number, majorId: number, countryId?: number | null): Observable<number | null> {
    const country = countryId ? `&countryId=${countryId}` : '';
    return this.httpClient
      .get<any>(`${this.apiUrl.apiPath('/api/v1/lookup/study-years')}?degreeId=${degreeId}&majorId=${majorId}${country}`,
        { context: silentErrors() })
      .pipe(
        map((res: any) => {
          if (res && typeof res.studyYears === 'number') return res.studyYears;
          if (res && typeof res.STUDY_YEARS === 'number') return res.STUDY_YEARS;
          return null;
        }),
        catchError(() => of(null))
      );
  }

  /** The finance amount for this degree and agency type at a given attaché. See `getStudyYears`. */
  getStudyAmount(degreeId: number, agencyTypeId: number, countryId?: number | null): Observable<number | null> {
    const country = countryId ? `&countryId=${countryId}` : '';
    return this.httpClient
      .get<any>(`${this.apiUrl.apiPath('/api/v1/lookup/study-amount')}?degreeId=${degreeId}&agencyTypeId=${agencyTypeId}${country}`,
        { context: silentErrors() })
      .pipe(
        map((res: any) => {
          if (res && typeof res.amount === 'number') return res.amount;
          if (res && typeof res.AMOUNT === 'number') return res.AMOUNT;
          return null;
        }),
        catchError(() => of(null))
      );
  }

  getStudyCollegesInByEducation(educationInId: number, universityInId?: number): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-college-in')
      .pipe(
        map((res: any) => {
          const data = (res?.content || res) as any[];
          const list = this.mapDynamic(res, ['STUDY_COLLEGE_IN_ID', 'id'], ['STUDY_COLLEGE_IN_NAME', 'name'], ['STUDY_COLLEGE_IN_NAME_EN', 'studyCollegeInNameEn']);
          const eduKeys = ['STUDY_EDUCATION_IN_ID', 'studyEducationInId', 'educationInId'];
          const uniKeys = ['STUDY_UNIVERSITY_IN_ID', 'studyUniversityInId', 'universityInId'];
          const hasEduFk = Array.isArray(data) && data.some(r => eduKeys.some(k => Object.prototype.hasOwnProperty.call(r, k)));
          const hasUniFk = Array.isArray(data) && data.some(r => uniKeys.some(k => Object.prototype.hasOwnProperty.call(r, k)));
          if (!hasEduFk) {
            return list; // cannot reliably filter → return all
          }
          const filtered = list.filter((_, idx) => {
            const raw = data?.[idx] || {};
            const eduFk = eduKeys.map(k => raw?.[k]).find(v => v !== undefined);
            const uniFk = uniKeys.map(k => raw?.[k]).find(v => v !== undefined);
            const eduOk = Number(eduFk) === Number(educationInId);
            const uniOk = universityInId && hasUniFk ? Number(uniFk) === Number(universityInId) : true;
            return eduOk && uniOk;
          });
          return filtered; // nothing matched is an answer, not "every college" (M10)
        })
      );
  }

  getStudyCollegesByUniversity(universityInId: number): Observable<LookupItem[]> {
    return this.fetchAllLookupRows('study-college-in')
      .pipe(
        map((res: any) => {
          const data = (res?.content || res) as any[];
          const list = this.mapDynamic(res, ['STUDY_COLLEGE_IN_ID', 'id'], ['STUDY_COLLEGE_IN_NAME', 'name'], ['STUDY_COLLEGE_IN_NAME_EN', 'studyCollegeInNameEn']);
          const uniKeys = ['STUDY_UNIVERSITY_IN_ID', 'studyUniversityInId', 'universityInId'];
          const hasUniFk = Array.isArray(data) && data.some(r => uniKeys.some(k => Object.prototype.hasOwnProperty.call(r, k)));
          if (!hasUniFk) {
            return list; // cannot reliably filter → return all
          }
          const filtered = list.filter((_, idx) => {
            const raw = data?.[idx] || {};
            const uniFk = uniKeys.map(k => raw?.[k]).find(v => v !== undefined);
            return Number(uniFk) === Number(universityInId);
          });
          return filtered; // nothing matched is an answer, not "every college" (M10)
        })
      );
  }

  getStudyMajorsInByEducation(educationInId: number): Observable<LookupItem[]> {
    return this.fetchAllLookupRows(`study-major-in?educationId=${educationInId}`)
      .pipe(
        map((rows: any[]) =>
          this.mapDynamic(
            rows,
            ['STUDY_MAJOR_ID', 'id'],
            ['STUDY_MAJOR_NAME', 'studyMajorName', 'name'],
            ['STUDY_MAJOR_NAME_EN', 'studyMajorNameEn']
          )
        ),
        catchError(() => of([]))
      );
  }

  private mapDynamic(response: any, idCandidates: string[], nameCandidates: string[], nameEnCandidates?: string[]): LookupItem[] {
    const data = response?.content || response;
    if (!Array.isArray(data)) return [];
    const sample = data[0] || {};
    const idKey = idCandidates.find(k => Object.prototype.hasOwnProperty.call(sample, k)) || idCandidates[idCandidates.length - 1];
    const nameKey = nameCandidates.find(k => Object.prototype.hasOwnProperty.call(sample, k)) || nameCandidates[nameCandidates.length - 1];
    const nameEnKey = nameEnCandidates?.find(k => Object.prototype.hasOwnProperty.call(sample, k));
    return this.mapToLookupItems({ content: data }, idKey, nameKey, nameEnKey);
  }

  getStudentStudyStatuses(): Observable<LookupItem[]> {
    const cached = this.getCached<LookupItem[]>('student-study-status');
    if (cached) return of(cached);
    return this.fetchAllLookupRows('student-study-status').pipe(
      map(response => this.mapToLookupItems(response, 'id', 'studentStudyStatusName', 'studentStudyStatusNameEn')),
      tap(data => this.setCache('student-study-status', data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getYesNoOptions(): Observable<LookupItem[]> {
    const key = 'yes-no';
    const cached = this.getCached<LookupItem[]>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}yes-no`).pipe(
      map(rows => {
        const isEn = this.translationService.getCurrentLanguage() === 'en';
        return (rows || []).filter(r => this.isItemActive(r)).map((r, idx) => {
          const label = String((isEn ? r?.nameEn : r?.nameAr) ?? r?.nameEn ?? r?.nameAr ?? '').trim();
          const code = String(r?.code ?? '').trim();
          const boolVal = code === 'true';
          return { id: idx + 1, name: label, label, value: boolVal } as LookupItem;
        });
      }),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  /** Map LU tables with numeric CODE to { label, value: number } dropdown options. */
  private mapNumericCodeLookup(rows: any[]): Array<{ label: string; value: number }> {
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return (rows || [])
      .filter(r => this.isItemActive(r))
      .map(r => ({
        label: String((isEn ? r?.nameEn : r?.nameAr) ?? r?.nameEn ?? r?.nameAr ?? r?.code ?? '').trim(),
        value: parseInt(String(r?.code ?? ''), 10)
      }))
      .filter(o => !isNaN(o.value));
  }

  /** Map LU tables with string CODE to { label, value: string } dropdown options. */
  private mapStringCodeLookup(rows: any[]): Array<{ label: string; value: string }> {
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return (rows || [])
      .filter(r => this.isItemActive(r))
      .map(r => ({
        label: String((isEn ? r?.nameEn : r?.nameAr) ?? r?.nameEn ?? r?.nameAr ?? r?.code ?? '').trim(),
        value: String(r?.code ?? '').trim()
      }))
      .filter(o => o.value.length > 0);
  }

  getUniversityFeeStatuses(): Observable<Array<{ label: string; value: number }>> {
    const key = 'university-fee-statuses';
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}university-fee-statuses`).pipe(
      map(rows => this.mapNumericCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getFeeSources(): Observable<Array<{ label: string; value: number }>> {
    const key = 'fee-sources';
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}fee-sources`).pipe(
      map(rows => this.mapNumericCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getFeeCheckPayors(): Observable<Array<{ label: string; value: number }>> {
    const key = 'fee-check-payors';
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}fee-check-payors`).pipe(
      map(rows => this.mapNumericCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getFeeScopes(scopeType: 'SEMESTER' | 'YEAR'): Observable<Array<{ label: string; value: number }>> {
    const key = `fee-scopes-${scopeType}`;
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}fee-scopes`, { params: { scopeType } }).pipe(
      map(rows => this.mapNumericCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getAlertStatuses(): Observable<Array<{ label: string; value: string }>> {
    const key = 'alert-statuses';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}alert-statuses`).pipe(
      map(rows => this.mapStringCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getAlertSeverities(): Observable<Array<{ label: string; value: string }>> {
    const key = 'alert-severities';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}alert-severities`).pipe(
      map(rows => this.mapStringCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getStatusActiveOptions(): Observable<Array<{ label: string; value: number }>> {
    const key = 'status-active';
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}status-active`).pipe(
      map(rows => this.mapNumericCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getAcademicYears(): Observable<Array<{ label: string; value: string }>> {
    const key = 'academic-years';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}academic-years`).pipe(
      map(rows => {
        const isEn = this.translationService.getCurrentLanguage() === 'en';
        return (rows || []).filter(r => this.isItemActive(r)).map(r => {
          const code = String(r?.code ?? '').trim();
          const label = String((isEn ? r?.nameEn : r?.nameAr) ?? code).trim();
          return { label, value: code };
        }).filter(o => o.value.length > 0);
      }),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getSemesterNumbers(): Observable<Array<{ label: string; value: number }>> {
    const key = 'semester-numbers';
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}semester-numbers`).pipe(
      map(rows => this.mapNumericCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getBooksAllowancePayStatuses(): Observable<Array<{ label: string; value: number }>> {
    const key = 'books-allowance-pay-statuses';
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}books-allowance-pay-statuses`).pipe(
      map(rows => this.mapNumericCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getLoanStatuses(): Observable<Array<{ label: string; value: number }>> {
    const key = 'loan-statuses';
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}loan-statuses`).pipe(
      map(rows => this.mapNumericCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getReportDimensions(groupCode = 'STUDENT_REPORT_GROUPBY'): Observable<Array<{ label: string; value: string }>> {
    const key = `report-dimensions-${groupCode}`;
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}report-dimensions`, {
      params: { groupCode }
    }).pipe(
      map(rows => this.mapStringCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getTrendClasses(category: string): Observable<Array<{ label: string; value: string }>> {
    const key = `trend-classes-${category}`;
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}trend-classes`, {
      params: { category }
    }).pipe(
      map(rows => this.mapStringCodeLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  /** Trend class options for report filters (value = English name to match API data). */
  getTrendClassFilterOptions(category: string): Observable<Array<{ label: string; value: string }>> {
    const key = `trend-class-filter-${category}`;
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}trend-classes`, {
      params: { category }
    }).pipe(
      map(rows => (rows || [])
        .filter(r => this.isItemActive(r))
        .map(r => ({
          label: String((isEn ? r?.nameEn : r?.nameAr) ?? r?.nameEn ?? '').trim(),
          value: String(r?.nameEn ?? '').trim()
        }))
        .filter(o => o.value.length > 0)),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  /** Shared report PDF language toggle options (i18n-driven). */
  getReportLangOptions(): Array<{ label: string; value: string }> {
    return [
      { label: this.translationService.translate('COMMON.LANG_AR'), value: 'ar' },
      { label: this.translationService.translate('COMMON.LANG_EN'), value: 'en' }
    ];
  }

  /** DB-driven LU_TRAVEL_TYPE (label = TYPE_NAME, value = TYPE_CODE on tickets). */
  getLuTravelTypesForUi(): Observable<Array<{ label: string; value: string }>> {
    const cacheKey = 'ref-travel-types-ui';
    const cached = this.getCached<Array<{ label: string; value: string }>>(cacheKey);
    if (cached) {
      return of(cached);
    }
    const url = `${this.apiUrl.apiPath('/api/v1/ref-data/travel-types')}`;
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return this.httpClient.get<any[]>(url).pipe(
      map(rows => {
        const list = Array.isArray(rows) ? rows : [];
        return list
          .filter(r => this.isItemActive(r))
          .map(r => {
            const en = r?.typeName ?? r?.TYPE_NAME;
            const ar = r?.typeNameAr ?? r?.TYPE_NAME_AR;
            const label = isEn ? (en ?? ar) : (ar ?? en);
            return {
              label: String(label ?? r?.typeCode ?? r?.TYPE_CODE ?? '').trim(),
              value: String(r?.typeCode ?? r?.TYPE_CODE ?? '').trim()
            };
          })
          .filter(o => o.value.length > 0);
      }),
      tap(data => this.setCache(cacheKey, data)),
      catchError(() => {
        return of([]);
      })
    );
  }

  /** DB-driven LU_FAMILY_RELATIONSHIP (REL_NAME / REL_CODE). */
  getLuFamilyRelationshipsForUi(): Observable<Array<{ label: string; value: string }>> {
    const cacheKey = 'ref-family-rel-ui';
    const cached = this.getCached<Array<{ label: string; value: string }>>(cacheKey);
    if (cached) {
      return of(cached);
    }
    const url = `${this.apiUrl.apiPath('/api/v1/ref-data/family-relationships')}`;
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return this.httpClient.get<any[]>(url).pipe(
      map(rows => {
        const list = Array.isArray(rows) ? rows : [];
        return list
          .filter(r => this.isItemActive(r))
          .map(r => {
            const en = r?.relName ?? r?.REL_NAME;
            const ar = r?.relNameAr ?? r?.REL_NAME_AR;
            const label = isEn ? (en ?? ar) : (ar ?? en);
            return {
              label: String(label ?? r?.relCode ?? r?.REL_CODE ?? '').trim(),
              value: String(r?.relCode ?? r?.REL_CODE ?? '').trim()
            };
          })
          .filter(o => o.value.length > 0);
      }),
      tap(data => this.setCache(cacheKey, data)),
      catchError(() => {
        return of([]);
      })
    );
  }

  /** LU_FAMILY_RELATIONSHIP with numeric REL_ID for student parent/relative forms. */
  getFamilyRelationshipLookupItems(): Observable<LookupItem[]> {
    const cacheKey = 'ref-family-rel-lookup-items';
    const cached = this.getCached<LookupItem[]>(cacheKey);
    if (cached) {
      return of(cached);
    }
    const url = `${this.apiUrl.apiPath('/api/v1/ref-data/family-relationships')}`;
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return this.httpClient.get<any[]>(url).pipe(
      map(rows => {
        const list = Array.isArray(rows) ? rows : [];
        return list
          .filter(r => this.isItemActive(r))
          .map(r => {
            const id = Number(r?.id ?? r?.relId ?? r?.REL_ID ?? 0);
            const en = r?.relName ?? r?.REL_NAME;
            const ar = r?.relNameAr ?? r?.REL_NAME_AR;
            const name = String(isEn ? (en ?? ar) : (ar ?? en)).trim();
            return { id, name, label: name, value: id };
          })
          .filter(o => o.id > 0 && o.name.length > 0);
      }),
      tap(data => this.setCache(cacheKey, data)),
      catchError(() => of([]))
    );
  }

  /** DB-driven STUDENT_INFO_REQUEST_KIND (DISPLAY_NAME / KIND_CODE). */
  getStudentInfoRequestKindsForUi(): Observable<Array<{ label: string; value: string }>> {
    const cacheKey = 'ref-info-request-kinds-ui';
    const cached = this.getCached<Array<{ label: string; value: string }>>(cacheKey);
    if (cached) {
      return of(cached);
    }
    const url = `${this.apiUrl.apiPath('/api/v1/ref-data/student-info-request-kinds')}`;
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return this.httpClient.get<any[]>(url).pipe(
      map(rows => {
        const list = Array.isArray(rows) ? rows : [];
        return list
          .filter(r => this.isItemActive(r))
          .map(r => {
            const en = r?.displayName ?? r?.DISPLAY_NAME;
            const ar = r?.displayNameAr ?? r?.DISPLAY_NAME_AR;
            const label = isEn ? (en ?? ar) : (ar ?? en);
            return {
              label: String(label ?? r?.kindCode ?? r?.KIND_CODE ?? '').trim(),
              value: String(r?.kindCode ?? r?.KIND_CODE ?? '').trim()
            };
          })
          .filter(o => o.value.length > 0);
      }),
      tap(data => this.setCache(cacheKey, data)),
      catchError(() => {
        return of([]);
      })
    );
  }

  /** DB-driven FIN_RECEIVED_REASON (REASON_NAME / REASON_CODE). */
  getFinReceivedReasonsForUi(): Observable<Array<{ label: string; value: string }>> {
    const cacheKey = 'ref-fin-received-reasons-ui';
    const cached = this.getCached<Array<{ label: string; value: string }>>(cacheKey);
    if (cached) {
      return of(cached);
    }
    const url = `${this.apiUrl.apiPath('/api/v1/ref-data/fin-received-reasons')}`;
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return this.httpClient.get<any[]>(url).pipe(
      map(rows => {
        const list = Array.isArray(rows) ? rows : [];
        return list
          .filter(r => this.isItemActive(r))
          .map(r => {
            const en = r?.reasonName ?? r?.REASON_NAME;
            const ar = r?.reasonNameAr ?? r?.REASON_NAME_AR;
            const label = isEn ? (en ?? ar) : (ar ?? en);
            return {
              label: String(label ?? r?.reasonCode ?? r?.REASON_CODE ?? '').trim(),
              value: String(r?.reasonCode ?? r?.REASON_CODE ?? '').trim()
            };
          })
          .filter(o => o.value.length > 0);
      }),
      tap(data => this.setCache(cacheKey, data)),
      catchError(() => {
        return of([]);
      })
    );
  }

  // ── New status/type lookups (LU_ tables, bilingual) ──────────────────────

  private mapStatusLookup(rows: any[]): Array<{ label: string; value: string }> {
    const isEn = this.translationService.getCurrentLanguage() === 'en';
    return (rows || []).filter(r => this.isItemActive(r)).map(r => ({
      label: String((isEn ? r?.nameEn : r?.nameAr) ?? r?.nameEn ?? r?.nameAr ?? r?.code ?? '').trim(),
      value: String(r?.code ?? '').trim()
    })).filter(o => o.value.length > 0);
  }

  getWafarSpendingStatuses(): Observable<Array<{ label: string; value: string }>> {
    const key = 'wafar-spending-statuses';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}wafar-spending-statuses`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getWafarReconciliationStatuses(): Observable<Array<{ label: string; value: string }>> {
    const key = 'wafar-reconciliation-statuses';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}wafar-reconciliation-statuses`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getPaymentAuthStatuses(): Observable<Array<{ label: string; value: string | null }>> {
    const key = 'payment-auth-statuses';
    const cached = this.getCached<Array<{ label: string; value: string | null }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}payment-auth-statuses`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getPaymentAmountSources(): Observable<Array<{ label: string; value: string }>> {
    const key = 'payment-amount-sources';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}payment-amount-sources`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getChangeRequestStatuses(): Observable<Array<{ label: string; value: string | null }>> {
    const key = 'change-request-statuses';
    const cached = this.getCached<Array<{ label: string; value: string | null }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}change-request-statuses`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getStudentPortalAccountStatuses(): Observable<Array<{ label: string; value: string | null }>> {
    const key = 'student-portal-account-statuses';
    const cached = this.getCached<Array<{ label: string; value: string | null }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}student-portal-account-statuses`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getNotificationChannels(): Observable<Array<{ label: string; value: string }>> {
    const key = 'notification-channels';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}notification-channels`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getNotificationRecipientTypes(): Observable<Array<{ label: string; value: string }>> {
    const key = 'notification-recipient-types';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}notification-recipient-types`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getNotificationScopes(): Observable<Array<{ label: string; value: string }>> {
    const key = 'notification-scopes';
    const cached = this.getCached<Array<{ label: string; value: string }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}notification-scopes`).pipe(
      map(rows => this.mapStatusLookup(Array.isArray(rows) ? rows : [])),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }

  getWafarTypes(): Observable<LookupItem[]> {
    const key = 'wafar-types';
    const cached = this.getCached<LookupItem[]>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/v1/studyWafarType/')}`).pipe(
      map(rows => (rows || []).map(r => ({ id: r.id, name: r.wafarName ?? r.name ?? String(r.id) } as LookupItem))),
      tap(data => this.setCache(key, data)),
      catchError(() => of([]))
    );
  }

  getQuarterNumbers(): Observable<Array<{ label: string; value: number }>> {
    const key = 'quarter-numbers';
    const cached = this.getCached<Array<{ label: string; value: number }>>(key);
    if (cached) return of(cached);
    return this.httpClient.get<any[]>(`${this.apiUrl.apiPath('/api/lookup-tables/')}quarter-numbers`).pipe(
      map(rows => {
        const isEn = this.translationService.getCurrentLanguage() === 'en';
        return (rows || []).map(r => ({
          label: String((isEn ? r?.nameEn : r?.nameAr) ?? r?.nameEn ?? r?.nameAr ?? r?.code ?? '').trim(),
          value: parseInt(r?.code, 10)
        })).filter(o => !isNaN(o.value));
      }),
      tap(data => this.setCache(key, data)),
      catchError(() => { this.showLookupError(); return of([]); })
    );
  }
}