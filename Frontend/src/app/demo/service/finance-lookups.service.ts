import { Injectable } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

import { LookupService, LookupItem } from './lookup.service';
import { StudyWafarTypeService } from './study-wafar-type.service';
import { TranslationService } from './translation.service';

import { StudyCountryIn } from '../api/study-country-in';
import { StudyGovIn } from '../api/study-gov-in';
import { StudyFinanceBank } from '../api/study-finance-bank';
import { StudyFinanceBankBranch } from '../api/study-finance-bank-branch';
import { StudyFinanceDof } from '../api/study-finance-dof';
import { StudyFinReceivedStatus } from '../api/study-fin-received-status';
import { StudyReceivedWhomStatus } from '../api/study-received-whom-status';
import { StudyFinReceivedWhom } from '../api/study-fin-received-whom';
import { StudyWafarType } from '../api/study-wafar-type';
import { localizeField, pickLanguage } from '../pipes/in-language.pipe';

/**
 * Every lookup list the finance pages bind to. All lists are loaded in full
 * (not filtered by a parent selection) so that display names always resolve —
 * the list and view pages have to render a bank/branch/governorate name without
 * the user having picked a country first.
 */
export interface FinanceLookups {
  studyCountries: StudyCountryIn[];
  studyGovernments: StudyGovIn[];
  studyFinanceBanks: StudyFinanceBank[];
  studyFinanceBankBranches: StudyFinanceBankBranch[];
  studyFinanceDofs: StudyFinanceDof[];
  finReceivedStatuses: StudyFinReceivedStatus[];
  receivedWhomStatuses: StudyReceivedWhomStatus[];
  finReceivedWhoms: StudyFinReceivedWhom[];
  wafarTypes: StudyWafarType[];
  currencies: LookupItem[];
}

export function emptyFinanceLookups(): FinanceLookups {
  return {
    studyCountries: [],
    studyGovernments: [],
    studyFinanceBanks: [],
    studyFinanceBankBranches: [],
    studyFinanceDofs: [],
    finReceivedStatuses: [],
    receivedWhomStatuses: [],
    finReceivedWhoms: [],
    wafarTypes: [],
    currencies: []
  };
}

/**
 * Loads and resolves the lookup tables behind the finance screens.
 *
 * Sources (all database-backed, no hardcoded option lists):
 *   STUDY_COUNTRY_IN, STUDY_GOV_IN, STUDY_FINANCE_BANK, STUDY_FINANCE_BANK_BRANCH,
 *   STUDY_FINANCE_DOF, STUDY_FIN_RECEIVED_STATUS, STUDY_RECEIVED_WHOM_STATUS,
 *   STUDY_FIN_RECEIVED_WHOM, STUDY_WAFAR_TYPE, STUDY_CURRENCY_IN
 */
@Injectable({ providedIn: 'root' })
export class FinanceLookupsService {

  constructor(
    private lookupService: LookupService,
    private studyWafarTypeService: StudyWafarTypeService,
    private translationService: TranslationService
  ) { }

  /**
   * Loads every finance lookup in one pass. A failing list degrades to empty
   * rather than taking the whole page down with it.
   */
  loadAll(): Observable<FinanceLookups> {
    const safe = <T>(source: Observable<T[]>): Observable<T[]> =>
      source.pipe(catchError(() => of([] as T[])));

    return forkJoin({
      studyCountries: safe(this.lookupService.getAllStudyCountries()),
      studyGovernments: safe(this.lookupService.getAllStudyGovernments()),
      studyFinanceBanks: safe(this.lookupService.getAllStudyFinanceBanks()),
      studyFinanceBankBranches: safe(this.lookupService.getAllStudyFinanceBankBranches()),
      studyFinanceDofs: safe(this.lookupService.getAllStudyFinanceDofs()),
      finReceivedStatuses: safe(this.lookupService.getAllStudyFinReceivedStatuses()),
      receivedWhomStatuses: safe(this.lookupService.getAllStudyReceivedWhomStatuses()),
      finReceivedWhoms: safe(this.lookupService.getAllStudyFinReceivedWhoms()),
      // wafarName is the Arabic column; an English form printed it anyway (QA B2, FIN-006).
      wafarTypes: safe(this.studyWafarTypeService.getAll()).pipe(
        map(rows => localizeField(rows, 'wafarName', this.translationService.getCurrentLanguage()))),
      currencies: safe(this.lookupService.getStudyCurrencies())
    });
  }

  /** Reloads just the recipient list, e.g. after one is created inline. */
  reloadReceivedWhoms(): Observable<StudyFinReceivedWhom[]> {
    return this.lookupService.getAllStudyFinReceivedWhoms().pipe(catchError(() => of([])));
  }

  /**
   * Looks a display name up by id. Returns the translated "not specified"
   * placeholder when the id is unset or unknown — never a raw `Bank ID: 12`.
   */
  resolveName(list: any[] | undefined, id: number | null | undefined, nameField: string): string {
    const notSpecified = this.translationService.translate('COMMON.NOT_SPECIFIED');
    if (id === null || id === undefined || Number(id) <= 0) {
      return notSpecified;
    }
    const match = (list || []).find(item => Number(item?.id) === Number(id));
    // `<field>En` when the reader works in English and the row has one: the
    // finance view printed every name in Arabic (QA B2).
    const name = pickLanguage(match, nameField, this.translationService.getCurrentLanguage());
    return name.trim() ? name : notSpecified;
  }

  /** Currency lists come back as LookupItem (`name`), not as an entity. */
  resolveCurrencyName(currencies: LookupItem[] | undefined, id: number | null | undefined): string {
    if (id === null || id === undefined || Number(id) <= 0) {
      return '';
    }
    const match = (currencies || []).find(c => Number(c.id) === Number(id));
    return match?.name ? String(match.name) : '';
  }
}
