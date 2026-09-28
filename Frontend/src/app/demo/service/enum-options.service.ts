import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { ApiUrlService } from './api-url.service';
import { TranslationService } from './translation.service';
import { CodeListService } from './code-list.service';

export interface EnumOption {
  label: string;
  value: string;
  labelAr?: string;
  labelEn?: string;
  sortOrder?: number;
  uiSeverity?: string | null;
  icon?: string | null;
  isSystem?: boolean;
  active?: boolean;
  extra?: string | null;
}

/**
 * Dropdown options for a code list.
 *
 * Reads from the bulk map CodeListService loaded after login when that map
 * holds the list (no request at all), and only otherwise from
 * `/api/v1/enums/{name}` — the same rows, one list at a time.
 */
@Injectable({ providedIn: 'root' })
export class EnumOptionsService {
  private readonly cache = new Map<string, EnumOption[]>();

  constructor(
    private http: HttpClient,
    private apiUrl: ApiUrlService,
    private translationService: TranslationService,
    private codeLists: CodeListService
  ) {
    this.translationService.currentLanguage$.subscribe(() => this.cache.clear());
  }

  getOptions(enumName: string): Observable<EnumOption[]> {
    const lang = this.translationService.getCurrentLanguage();
    if (this.codeLists.has(enumName)) {
      return of(this.codeLists.active(enumName).map(row => ({
        ...row,
        label: this.codeLists.label(enumName, row.value, lang)
      })));
    }
    const cacheKey = `${enumName}:${lang}`;
    const cached = this.cache.get(cacheKey);
    if (cached) {
      return of(cached);
    }
    return this.http
      .get<EnumOption[]>(this.apiUrl.apiPath(`/api/v1/enums/${enumName}`), { params: { lang } })
      .pipe(
        map(rows => (Array.isArray(rows) ? rows : [])),
        tap(rows => this.cache.set(cacheKey, rows)),
        catchError(() => of([]))
      );
  }

  /** Convenience for p-dropdown / nz-select: { label, value } */
  getDropdownOptions(enumName: string): Observable<Array<{ label: string; value: string }>> {
    return this.getOptions(enumName).pipe(
      map(rows => rows.map(r => ({ label: r.label, value: r.value })))
    );
  }
}
