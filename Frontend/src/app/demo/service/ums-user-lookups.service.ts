import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CodeListService } from './code-list.service';
import { EnumOptionsService } from './enum-options.service';
import { LookupService } from './lookup.service';
import { TranslationService } from './translation.service';
import { UmsAuthService } from './ums-auth.service';

/** One choice of a staff-account dropdown: the code, and its name in the reader's language. */
export interface UmsOption {
    label: string;
    value: string;
}

/** An attaché (MOHECORE.STUDY_COUNTRY_IN) a staff account can be scoped to. */
export interface UmsCountryOption {
    id: number;
    name: string;
}

/**
 * The lists the staff-account pages choose from, every one read from the
 * database rather than typed into a template:
 *
 *   position      MOHECORE.LU_POSITION        (code list `position`, V093)
 *   status        MOHECORE.LU_USER_STATUS     (code list `ums-user-status`)
 *   account type  MOHECORE.LU_USER_PRIVILEGE  (code list `ums-user-privilege`)
 *   attachés      MOHECORE.STUDY_COUNTRY_IN   (GET /lookup/study-countries)
 *
 * The three code lists come out of the bulk map CodeListService warmed at
 * login, so they cost no request; the attachés are one request per page.
 * Shared by the list, the add/edit page, the account page and the quick-add
 * dialog, so the four cannot drift apart the way the creation wizard and the
 * list once did over which country table to read.
 */
@Injectable({ providedIn: 'root' })
export class UmsUserLookupsService {

    constructor(
        private codeLists: CodeListService,
        private enumOptions: EnumOptionsService,
        private lookupService: LookupService,
        private auth: UmsAuthService,
        private translation: TranslationService
    ) { }

    private get lang(): string {
        return this.translation.getCurrentLanguage();
    }

    /** Positions, active rows only, in the reader's language. Synchronous: the map is warm. */
    positionOptions(): UmsOption[] {
        return this.codeLists.options('position', this.lang);
    }

    /** Account statuses (ACTIVE, INACTIVE, LOCKED …) as the table names them. */
    statusOptions(): Observable<UmsOption[]> {
        return this.enumOptions.getDropdownOptions('ums-user-status').pipe(map(rows => this.asOptions(rows)));
    }

    /** Account types (REGULAR, ADMIN). The super-admin flag is not on the list: it is never set from a form. */
    privilegeOptions(): Observable<UmsOption[]> {
        return this.enumOptions.getDropdownOptions('ums-user-privilege').pipe(map(rows => this.asOptions(rows)));
    }

    /**
     * The attachés an account can be scoped to.
     *
     * STUDY_COUNTRY_IN, not STUDENT_COUNTRY: UserCountries.CountryId is read as
     * a STUDY_COUNTRY_IN id throughout the backend scope layer, and the two
     * tables disagree on which id is which country. Narrowed to the caller's
     * own attachés unless the caller is ministry-level (N-02.d): the server
     * refuses an attaché the caller does not hold, so the picker offers none.
     */
    countries(): Observable<UmsCountryOption[]> {
        return this.lookupService.getAllStudyCountries().pipe(
            map(rows => (Array.isArray(rows) ? rows : [])
                .map((c: any) => ({ id: Number(c.id ?? c.studyCountryInId), name: String(c.studyCountryInName ?? '') }))
                .filter(c => Number.isFinite(c.id) && c.id > 0)),
            map(rows => this.narrowToCaller(rows))
        );
    }

    positionLabel(code: string | null | undefined): string {
        return code ? (this.codeLists.label('position', code, this.lang) || code) : '';
    }

    /** The table's name for a status; the i18n key only for a code the table does not know. */
    statusLabel(code: string | null | undefined): string {
        if (!code) {
            return '';
        }
        return this.codeLists.find('ums-user-status', code)
            ? this.codeLists.label('ums-user-status', code, this.lang)
            : this.translation.translate('UMS.STATUS.' + code);
    }

    privilegeLabel(code: string | null | undefined): string {
        return code ? (this.codeLists.label('ums-user-privilege', code, this.lang) || code) : '';
    }

    private asOptions(rows: Array<{ label: string; value: string }> | null | undefined): UmsOption[] {
        return (Array.isArray(rows) ? rows : []).map(r => ({ label: r.label, value: r.value }));
    }

    private narrowToCaller<T extends { id: number }>(items: T[]): T[] {
        if (this.auth.isSuperAdmin() || this.auth.isMinistryUser === true) {
            return items;
        }
        const held = this.auth.countryIds;
        const allowed = new Set<number>(Array.isArray(held) ? held : []);
        return items.filter(item => allowed.has(item.id));
    }
}
