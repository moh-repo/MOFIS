import { Component, OnDestroy, OnInit } from '@angular/core';
import { forkJoin, Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { TranslationService } from '../../demo/service/translation.service';
import { StudentPortalApiService, MyData, PortalStudent, PortalLookups, PortalLookupItem, StudentProgressDto, StudentYearResultDto } from '../student-portal-api.service';
import { YearOutcome, yearOutcome } from '../../demo/api/student-year-result';

const OUTCOME_COLOR: Record<YearOutcome, string> = { passed: 'success', failed: 'error', 'not-recorded': 'default' };
const OUTCOME_KEY: Record<YearOutcome, string> = {
  passed: 'STUDENT_QUICK_EDIT.YEAR_RESULTS.PASSED',
  failed: 'STUDENT_QUICK_EDIT.YEAR_RESULTS.FAILED',
  'not-recorded': 'STUDENT_QUICK_EDIT.YEAR_RESULTS.NOT_RECORDED'
};
import { pickLanguage } from '../../demo/pipes/in-language.pipe';
import { changesOnly } from '../../demo/service/reload-streams.util';
import { formatQuarterCode, formatYearMonthCode } from '../../demo/service/quarter-code.util';
import { financeStatusLabel } from '../student-portal-i18n.helper';

/** How a value should be rendered; the pipes stay in the template. */
export type FactType = 'text' | 'date' | 'datetime';

/** One label/value pair inside a section. */
export interface Fact {
  labelKey: string;
  value: string | number;
  type: FactType;
}

/** A titled run of facts. Sections with nothing in them are never built. */
export interface FactSection {
  id: string;
  icon: string;
  titleKey: string;
  facts: Fact[];
}

@Component({
    selector: 'app-student-portal-my-data',
    templateUrl: './my-data.component.html',
    styleUrls: ['./my-data.component.scss'],
    standalone: false
})
export class MyDataComponent implements OnInit, OnDestroy {

  /** Kept, so the looked-up names can be re-read when the language switches in place. */
  private lookups: PortalLookups | null = null;
  private languageSub?: Subscription;

  data: MyData | null = null;
  loading = true;
  errorKey = '';
  studentProgress: StudentProgressDto | null = null;
  progressLoading = true;
  yearResults: StudentYearResultDto[] = [];
  yearResultsLoading = true;

  constructor(
    private api: StudentPortalApiService,
    private translationService: TranslationService
  ) {}

  /** A seeded placeholder year reads "not recorded" (FIX-PLAN P5-8). */
  outcome(r: StudentYearResultDto): YearOutcome {
    return yearOutcome(r);
  }

  outcomeColor(r: StudentYearResultDto): string {
    return OUTCOME_COLOR[yearOutcome(r)];
  }

  outcomeKey(r: StudentYearResultDto): string {
    return OUTCOME_KEY[yearOutcome(r)];
  }

  yearResultTitle(r: StudentYearResultDto): string {
    const yearLabel = this.translationService.translate('STUDENT_PORTAL.YEAR_NUMBER');
    return `${yearLabel} ${r.yearNumber}`;
  }

  ngOnInit(): void {
    forkJoin({ data: this.api.getMyData(), lookups: this.api.getLookups() })
      .pipe(finalize(() => { this.loading = false; }))
      .subscribe({
        next: ({ data, lookups }) => {
          this.lookups = lookups;
          if (data.student) this.applyLookupNames(data.student, lookups);
          this.data = data;
        },
        error: () => {
          this.errorKey = 'STUDENT_PORTAL.LOAD_MY_DATA_ERROR';
        }
      });

    // The portal switches language in place: the looked-up names follow it.
    this.languageSub = this.translationService.currentLanguage$.pipe(changesOnly()).subscribe(() => {
      if (this.data?.student && this.lookups) this.applyLookupNames(this.data.student, this.lookups);
    });

    this.api.getProgress().subscribe({
      next: (p) => { this.studentProgress = p; this.progressLoading = false; },
      error: () => { this.studentProgress = null; this.progressLoading = false; }
    });
    this.api.getYearResults().subscribe({
      next: (list) => { this.yearResults = list || []; this.yearResultsLoading = false; },
      error: () => { this.yearResults = []; this.yearResultsLoading = false; }
    });
  }

  ngOnDestroy(): void {
    this.languageSub?.unsubscribe();
  }

  // ── Sections ────────────────────────────────────────────────
  //
  // The page used to be one nz-descriptions table per card and sixty-odd
  // `@if (field != null)` blocks in the template — about 250 lines of markup
  // that said the same thing over and over, and left the reader to work out
  // which of eight equally-weighted cards held the fact they wanted.
  //
  // Describing the sections here instead means the template is one loop, a
  // section with nothing in it is never built rather than rendered empty, and
  // the grouping is a list you can read in one screen and rearrange.

  /**
   * A fact, or null when there is nothing to show.
   *
   * <p>Null, undefined and the empty string are all "absent": the API returns
   * all three for a field the ministry has not filled in, and a row reading
   * "Major: —" tells the student nothing they did not already know.
   */
  private fact(labelKey: string, value: unknown, type: FactType = 'text'): Fact | null {
    if (value === null || value === undefined) {
      return null;
    }
    const text = typeof value === 'number' ? value : String(value).trim();
    if (text === '') {
      return null;
    }
    return { labelKey, value: text, type };
  }

  /**
   * A lookup-backed value: the resolved name, else the id — a number the office
   * can act on is better than a blank where a value is. Except 0: it is the "not
   * set" default of every lookup column on STUDENT_DATA and names no row, so it
   * is an absence, not a value (live QA M7 printed a finance status of 0).
   */
  private ref(name: unknown, id: unknown): unknown {
    if (name !== null && name !== undefined && String(name).trim() !== '') {
      return name;
    }
    return typeof id === 'number' && id <= 0 ? null : id;
  }

  private section(id: string, icon: string, titleKey: string, facts: (Fact | null)[]): FactSection | null {
    const present = facts.filter((f): f is Fact => f !== null);
    return present.length ? { id, icon, titleKey, facts: present } : null;
  }

  /** The fact sections, in reading order. Progress and Notes are their own blocks. */
  get sections(): FactSection[] {
    const s = this.data?.student;
    const f = this.data?.finance;
    if (!s && !f) {
      return [];
    }

    const yesNo = (v: unknown) =>
      this.translationService.translate(v ? 'COMMON.YES' : 'COMMON.NO');
    const lang = this.translationService.getCurrentLanguage();
    // Added / last quarter are YYYYQ codes: "2024 Q2", not "20242" (QA B3).
    const quarter = (code: unknown) => code == null || Number(code) <= 0 ? null
      : formatQuarterCode(code, this.translationService.translate('COMMON.QUARTER_SHORT'));

    const built: (FactSection | null)[] = [];

    if (s) {
      built.push(this.section('personal', 'idcard', 'STUDENT_PORTAL.STUDENT_INFO', [
        this.fact('STUDENT_PORTAL.NAME', s.studentName),
        this.fact('STUDENT_PORTAL.STUDENT_ID', s.studentId),
        this.fact('STUDENT_PORTAL.GENDER', this.ref(s.studentGenderName, s.studentGender)),
        this.fact('STUDENT_PORTAL.BIRTHDATE', s.studentBirthdate, 'date'),
        this.fact('STUDENT_PORTAL.BIRTHDATE_PLACE', this.ref(s.studentBirthdatePlaceName, s.studentBirthdatePlace)),
        this.fact('STUDENT_PORTAL.COUNTRY', this.ref(s.studentCountryName, s.studentCountryId)),
        this.fact('STUDENT_PORTAL.GOVERNMENT', this.ref(s.studentGovName, s.studentGovId)),
        this.fact('STUDENT_PORTAL.MUNICIPALITY', this.ref(s.studentMudName, s.studentMudId)),
        this.fact('STUDENT_PORTAL.EDUCATION', s.studentEductionName)
      ]));

      // Its own section rather than four rows lost among twenty: the expiry is
      // the one date on this page the student may have to act on.
      built.push(this.section('passport', 'profile', 'STUDENT_PORTAL.PASSPORT_SECTION', [
        this.fact('STUDENT_PORTAL.PASSPORT_NUMBER', s.studentPassportNumber),
        this.fact('STUDENT_PORTAL.PASSPORT_ISSUE_DATE', s.studentPassportIssueDate, 'date'),
        this.fact('STUDENT_PORTAL.PASSPORT_EXPIRE_DATE', s.studentPassportExpireDate, 'date'),
        this.fact('STUDENT_PORTAL.PASSPORT_ISSUE_COUNTRY',
          this.ref(s.studentPassportIssueCountryName, s.studentPassportIssueCountryId))
      ]));

      built.push(this.section('academic', 'read', 'STUDENT_PORTAL.ACADEMIC_DELEGATION', [
        this.fact('STUDENT_PORTAL.DEGREE', this.ref(s.studentDegreeName, s.studentDegreeId)),
        this.fact('STUDENT_PORTAL.MAJOR', this.ref(pickLanguage(s, 'studentMajorName', lang), s.studentMajorId)),
        this.fact('STUDENT_PORTAL.CURRENT_LEVEL', this.ref(s.studentCurrentLevelName, s.studentCurrentLevelId)),
        this.fact('STUDENT_PORTAL.STUDY_TYPE', this.ref(s.studentStudyTypeName, s.studentStudyTypeId)),
        this.fact('STUDENT_PORTAL.STUDY_STATUS', this.ref(s.studentStudyStatusName, s.studentStudyStatusId)),
        this.fact('STUDENT_PORTAL.STUDY_YEARS', s.studentStudyNoOfYears)
      ]));

      built.push(this.section('location', 'environment', 'STUDENT_PORTAL.STUDY_LOCATION', [
        this.fact('STUDENT_PORTAL.STUDY_COUNTRY_IN', this.ref(s.studyCountryInName, s.studyCountryIn)),
        this.fact('STUDENT_PORTAL.STUDY_GOV_IN', this.ref(s.syudyGovInName, s.syudyGovIn)),
        this.fact('STUDENT_PORTAL.STUDY_UNIVERSITY_IN', this.ref(s.studyUniversityInName, s.studyUniversityIn)),
        this.fact('STUDENT_PORTAL.STUDY_COLLEGE_IN', this.ref(s.studyCollegeInName, s.studyCollegeIn)),
        this.fact('STUDENT_PORTAL.STUDY_EDUCATION_IN', this.ref(s.studyEducationInName, s.studyEducationIn)),
        this.fact('STUDENT_PORTAL.STUDY_MAJOR_IN', this.ref(s.studyMajorInName, s.studyMajorIn))
      ]));

      // Split out of the old "Academic & Delegation" card, which ran fifteen
      // rows and mixed what the student studies with how it is paid for.
      built.push(this.section('delegation', 'flag', 'STUDENT_PORTAL.DELEGATION_SECTION', [
        this.fact('STUDENT_PORTAL.DELEGATION_DECISION_NO', s.studentDelegateDecisionNo),
        this.fact('STUDENT_PORTAL.DELEGATION_YEAR', s.studentDelegationDecisionYear),
        this.fact('STUDENT_PORTAL.AMOUNT', s.studentAmount == null ? null
          : `${s.studentAmount} ${pickLanguage(s, 'amountCurrencyName', lang) || s.studentAmountCurrencyName || ''}`.trim()),
        // A YYYYQ code like the two quarters below: "2017 Q3", not "20173" (live QA L10).
        this.fact('STUDENT_PORTAL.FIRST_RECEIPT', quarter(s.studentFirstReceiptOfDue)),
        this.fact('STUDENT_PORTAL.ADDED_QUARTER', quarter(s.studentAddedQuarter)),
        this.fact('STUDENT_PORTAL.LAST_QUARTER', quarter(s.studentLastQuarter)),
        this.fact('STUDENT_PORTAL.UNIVERSITY_FEE', s.studyUniversityFeeId == null ? null : yesNo(s.studyUniversityFeeId)),
        this.fact('STUDENT_PORTAL.UNIVERSITY_FEE_STATUS',
          s.studyUniversityFeeId ? (pickLanguage(s, 'studyUniversityFeeStatusName', lang) || null) : null)
      ]));

      built.push(this.section('management', 'team', 'STUDENT_PORTAL.MANAGEMENT', [
        this.fact('STUDENT_PORTAL.AGENCY_TYPE', this.ref(s.studentAgencyTypeName, s.studentAgencyTypeId)),
        this.fact('STUDENT_PORTAL.MINISTRY_TYPE', this.ref(s.studentMinistryTypeName, s.studentMinistryTypeId)),
        this.fact('STUDENT_PORTAL.UNIVERSITY_TYPE', this.ref(s.studentUniversityTypeName, s.studentUniversityTypeId)),
        this.fact('STUDENT_PORTAL.MODIFICATION_DATE', s.studentMidate, 'datetime')
      ]));
    }

    if (f) {
      built.push(this.section('finance', 'dollar', 'STUDENT_PORTAL.FINANCE_INFO', [
        this.fact('STUDENT_PORTAL.FINANCE_ID', f.studentFinId),
        this.fact('STUDENT_PORTAL.STUDENT_NAME', f.studentFinName),
        this.fact('STUDENT_PORTAL.STATUS', financeStatusLabel(f, lang, k => this.translationService.translate(k))),
        this.fact('STUDENT_PORTAL.RECEIVED_AMOUNT', f.studentFinRecievedAmount),
        this.fact('STUDENT_PORTAL.CHECK_DATE', f.studyCheckDate, 'date'),
        this.fact('STUDENT_PORTAL.RECEIVED_YEAR_MONTH',
          f.studentFinRecievedYear == null && f.studentFinRecievedMonth == null
            ? null : `${f.studentFinRecievedYear ?? '—'} / ${f.studentFinRecievedMonth ?? '—'}`),
        // The packed key, "2024 / 2" rather than "20242" (live QA L10).
        this.fact('STUDENT_PORTAL.YEAR_MONTH', formatYearMonthCode(f.studentFinRecievedYearMonth) || null),
        this.fact('STUDENT_PORTAL.STUDY_COUNTRY', f.studyCountryInName),
        this.fact('STUDENT_PORTAL.STUDY_GOVERNMENT', f.studyGovInName)
      ]));
    }

    return built.filter((x): x is FactSection => x !== null);
  }

  /** Whether the progress block has anything to draw. */
  get hasProgress(): boolean {
    return !!this.studentProgress || this.yearResults.length > 0;
  }

  get notes(): { labelKey: string; text: string }[] {
    const s = this.data?.student;
    const out: { labelKey: string; text: string }[] = [];
    if (s?.studentNote1) out.push({ labelKey: 'STUDENT_PORTAL.NOTE_1', text: s.studentNote1 });
    if (s?.studentNote2) out.push({ labelKey: 'STUDENT_PORTAL.NOTE_2', text: s.studentNote2 });
    return out;
  }

  /** Two words' worth of initials, the rule the shell and dashboard both use. */
  get studentInitials(): string {
    const name = (this.data?.student?.studentName || '').trim();
    if (!name) {
      return '—';
    }
    return name.split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase();
  }

  /** Scrolls to a section from the jump bar, without putting a hash in the URL. */
  goToSection(id: string): void {
    document.getElementById('md-' + id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  private applyLookupNames(s: PortalStudent, lookups: PortalLookups): void {
    const set = (nameField: string, id: number | string | undefined | null, arr: PortalLookupItem[]) => {
      if (id != null) {
        const found = arr.find(x => x.id === Number(id));
        if (found) (s as any)[nameField] = pickLanguage(found, 'name', this.translationService.getCurrentLanguage());
      }
    };
    set('studentGenderName',             s.studentGender,              lookups.genders);
    set('studentBirthdatePlaceName',     s.studentBirthdatePlace,      lookups.studentCountries);
    set('studentPassportIssueCountryName', s.studentPassportIssueCountryId, lookups.studentCountries);
    set('studentCountryName',            s.studentCountryId,           lookups.studentCountries);
    set('studentGovName',                s.studentGovId,               lookups.studentGovernments);
    set('studentMudName',                s.studentMudId,               lookups.studentMunicipalities);
    set('studentDegreeName',             s.studentDegreeId,            lookups.degrees);
    set('studentCurrentLevelName',       s.studentCurrentLevelId,      lookups.currentLevels);
    set('studentStudyTypeName',          s.studentStudyTypeId,         lookups.studyTypes);
    set('studentStudyStatusName',        s.studentStudyStatusId,       lookups.studyStatuses);
    set('studentAgencyTypeName',         s.studentAgencyTypeId,        lookups.agencyTypes);
    set('studentMinistryTypeName',       s.studentMinistryTypeId,      lookups.ministryTypes);
    set('studentUniversityTypeName',     s.studentUniversityTypeId,    lookups.universityTypes);
    set('studyCountryInName',            s.studyCountryIn,             lookups.studyCountries);
    set('syudyGovInName',                s.syudyGovIn,                 lookups.studyGovernments);
    set('studyUniversityInName',         s.studyUniversityIn,          lookups.studyUniversities);
    set('studyCollegeInName',            s.studyCollegeIn,             lookups.studyColleges);
    set('studyEducationInName',          s.studyEducationIn,           lookups.studyEducations);
    set('studyMajorInName',              s.studyMajorIn,               lookups.studyMajors);
  }
}
