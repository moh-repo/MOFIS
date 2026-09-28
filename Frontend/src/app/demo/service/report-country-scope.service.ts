import { Injectable } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CountrySelectionService } from './country-selection.service';
import { UmsAuthService } from './ums-auth.service';

/**
 * Topbar-driven study-country scope for reports, charts, dashboard, and tracer.
 * Ministry users may have null selectedCountryId (all countries); staff always have an assigned id.
 */
@Injectable({ providedIn: 'root' })
export class ReportCountryScopeService {

    constructor(
        private countrySelectionService: CountrySelectionService,
        private umsAuthService: UmsAuthService
    ) {}

    get selectedCountryId$(): Observable<number | null> {
        return this.countrySelectionService.selectedCountryId$;
    }

    get selectedCountryId(): number | null {
        return this.countrySelectionService.selectedCountryId;
    }

    get allowedCountryIds(): number[] {
        return this.umsAuthService.countryIds || [];
    }

    get isMinistryUser(): boolean {
        return this.umsAuthService.isMinistryUser;
    }

    /**
     * True when the user can see 2 or more countries (multi-country staff or ministry).
     * Used to decide whether to show a per-report country multi-select.
     */
    hasMultiCountryAccess(): boolean {
        return this.isMinistryUser || this.allowedCountryIds.length > 1;
    }

    /** Effective countryId for API query params (from topbar). */
    countryIdParam(): number | null {
        return this.selectedCountryId;
    }

    appendCountryParam(params: HttpParams = new HttpParams()): HttpParams {
        const id = this.countryIdParam();
        if (id != null) {
            return params.set('countryId', String(id));
        }
        return params;
    }

    /**
     * Build params for a multi-country selection inside a report page.
     * - selectedIds empty/null → omit filter (all allowed countries, resolved on backend)
     * - selectedIds is single item → use countryId=X (backward-compat)
     * - selectedIds is multiple → use countryId repeated (Spring MVC List<Integer>)
     */
    appendMultiCountryParams(selectedIds: number[] | null, params: HttpParams = new HttpParams()): HttpParams {
        if (!selectedIds || selectedIds.length === 0) {
            return params;
        }
        if (selectedIds.length === 1) {
            return params.set('countryId', String(selectedIds[0]));
        }
        let p = params;
        selectedIds.forEach(id => { p = p.append('countryIds', String(id)); });
        return p;
    }

    appendCountryToUrl(baseUrl: string): string {
        const id = this.countryIdParam();
        if (id == null) {
            return baseUrl;
        }
        const sep = baseUrl.includes('?') ? '&' : '?';
        return `${baseUrl}${sep}countryId=${id}`;
    }

    filterToAllowedStudyCountries<T extends { id: number }>(items: T[]): T[] {
        return this.umsAuthService.filterToAllowedStudyCountries(items);
    }
}
