import { Component, OnDestroy, OnInit } from '@angular/core';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { MessageService } from 'primeng/api';
import { StudentdataService } from 'src/app/demo/service/studentdata.service';
import { Studentdata } from 'src/app/demo/api/studentdata';
import { StudentProgressDto } from 'src/app/demo/api/student-progress';
import { StudentYearResultDto, YearOutcome, yearOutcome, YEAR_OUTCOME_KEY, YEAR_OUTCOME_SEVERITY } from 'src/app/demo/api/student-year-result';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportPdfService, ReportPdfQuery } from 'src/app/demo/service/report-pdf.service';
import { AuthService } from 'src/app/demo/service/auth.service';
import { UmsAuthService } from 'src/app/demo/service/ums-auth.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/environments/environment';
import { changesOnly } from 'src/app/demo/service/reload-streams.util';
import { LookupService, LookupItem } from 'src/app/demo/service/lookup.service';
import { ReportCatalogService } from 'src/app/demo/service/report-catalog.service';
import { UmsPermissionService } from 'src/app/demo/service/ums-permission.service';
import { pickLanguage } from '../../../../pipes/in-language.pipe';
import { uiReportLang } from '../../../../service/report-lang.util';

type ProfileMap = Record<string, unknown> | null;

/** URL segment of each official single-student document endpoint. */
type StudentDocKind = 'id-card' | 'registration-form' | 'enrollment-certificate' | 'financial-statement';

/** One official document card, named in the reader's language by the catalogue. */
interface OfficialDoc {
    kind: StudentDocKind;
    title: string;
    description: string;
    /** Full class, `pi pi-…`. */
    icon: string;
}

interface RegistrationRow {
    /** Raw key path, kept so the underlying column stays discoverable. */
    field: string;
    /** Readable form of `field`, used for display, print and export. */
    label: string;
    value: string;
}

@Component({
    selector: 'app-individual-student-reports',
    templateUrl: './individual-student-reports.component.html',
    styleUrls: ['./individual-student-reports.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class IndividualStudentReportsComponent implements OnInit, OnDestroy {
    selectedStudent: Studentdata | null = null;
    filteredStudents: Studentdata[] = [];

    studentDatum: Record<string, unknown> | null = null;
    profile: ProfileMap = null;
    progress: StudentProgressDto | null = null;
    yearResults: StudentYearResultDto[] = [];

    /** A seeded placeholder year reads "not recorded" (FIX-PLAN P5-8). */
    outcome(r: StudentYearResultDto): YearOutcome {
      return yearOutcome(r);
    }

    outcomeKey(r: StudentYearResultDto): string {
      return YEAR_OUTCOME_KEY[yearOutcome(r)];
    }

    outcomeSeverity(r: StudentYearResultDto): 'success' | 'danger' | 'secondary' {
      return YEAR_OUTCOME_SEVERITY[yearOutcome(r)];
    }

    registrationRows: RegistrationRow[] = [];
    loading = false;
    activeTabIndex = 0;

    // Lookups used to render FK columns as names on the ID-card tab.
    genderOptions: LookupItem[] = [];
    majorOptions: LookupItem[] = [];
    degreeOptions: LookupItem[] = [];
    levelOptions: LookupItem[] = [];
    studyStatusOptions: LookupItem[] = [];
    universityInOptions: LookupItem[] = [];
    countryInOptions: LookupItem[] = [];

    private readonly destroy$ = new Subject<void>();

    constructor(
        private studentdataService: StudentdataService,
        public translationService: TranslationService,
        private messageService: MessageService,
        private reportPdfService: ReportPdfService,
        private http: HttpClient,
        private authService: AuthService,
        private umsAuthService: UmsAuthService,
        private reportCountryScope: ReportCountryScopeService
    ,
        private lookupService: LookupService,
        private umsPermission: UmsPermissionService,
        private route: ActivatedRoute,
        private catalog: ReportCatalogService) {}

    ngOnInit(): void {
        // Arriving from a hub card for one specific document: remember which, so
        // it is highlighted and rendered as soon as a student is chosen.
        this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
            const doc = params.get('doc');
            this.focusedDoc = this.tabDocKinds.includes(doc as StudentDocKind)
                ? (doc as StudentDocKind)
                : null;
            // Open on the tab that shows this document, so the page and the
            // document agree from the first render.
            if (this.focusedDoc) {
                this.activeTabIndex = this.tabIndexForDoc(this.focusedDoc);
            }
        });
        this.loadLookups();
        this.catalog.documentCards(this.translationService.currentLanguage$)
            .pipe(takeUntil(this.destroy$))
            .subscribe(cards => {
                this.officialDocs = cards.map(c => ({
                    kind: c.kind as StudentDocKind,
                    title: c.title,
                    description: c.description,
                    icon: c.icon ? 'pi ' + c.icon : ''
                }));
            });
        this.reportCountryScope.selectedCountryId$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            if (this.selectedStudent) {
                this.onStudentClear();
            }
        });
        this.translationService.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            // Lookup names are language-specific, so they must be refetched too.
            this.loadLookups();
            if (this.studentDatum) {
                this.rebuildRegistrationRows();
            }
        });
    }

    private loadLookups(): void {
        forkJoin({
            genders: this.lookupService.getStudentGenders().pipe(catchError(() => of([] as LookupItem[]))),
            majors: this.lookupService.getStudentMajors().pipe(catchError(() => of([] as LookupItem[]))),
            degrees: this.lookupService.getStudentDegrees().pipe(catchError(() => of([] as LookupItem[]))),
            levels: this.lookupService.getCurrentLevels().pipe(catchError(() => of([] as LookupItem[]))),
            studyStatuses: this.lookupService.getStudentStudyStatuses().pipe(catchError(() => of([] as LookupItem[]))),
            universitiesIn: this.lookupService.getStudyUniversitiesIn().pipe(catchError(() => of([] as LookupItem[]))),
            countriesIn: this.lookupService.getStudyCountriesIn().pipe(catchError(() => of([] as LookupItem[])))
        })
            .pipe(takeUntil(this.destroy$))
            .subscribe((r) => {
                this.genderOptions = r.genders;
                this.majorOptions = r.majors;
                this.degreeOptions = r.degrees;
                this.levelOptions = r.levels;
                this.studyStatusOptions = r.studyStatuses;
                this.universityInOptions = r.universitiesIn;
                this.countryInOptions = r.countriesIn;
            });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    onStudentSearch(event: { query: string }): void {
        const q = String(event.query || '').trim();
        this.studentdataService
            .getPagingAll({ page: '0', size: '50', search: q || undefined })
            .subscribe({
                next: (res: { content?: Studentdata[] }) => {
                    this.filteredStudents = res?.content || [];
                },
                error: () => {
                    this.filteredStudents = [];
                }
            });
    }

    onStudentSelect(ev: Studentdata | { value?: Studentdata } | null): void {
        const raw = ev != null ? ((ev as { value?: Studentdata }).value ?? ev) : null;
        const s = raw as Studentdata;
        if (!s?.studentId) {
            return;
        }
        this.selectedStudent = s;
        this.renderedDocKey = null;
        this.loadStudent(Number(s.studentId));
        // Render the document belonging to the tab in view. When arrived at via
        // ?doc=, that tab is already selected, so this covers both cases.
        this.generateDoc(this.activeDocKind);
    }

    onStudentClear(): void {
        this.selectedStudent = null;
        this.clearData();
    }

    private clearData(): void {
        this.studentDatum = null;
        this.profile = null;
        this.progress = null;
        this.yearResults = [];
        this.registrationRows = [];
    }

    loadStudent(studentId: number): void {
        this.loading = true;
        this.clearData();
        forkJoin({
            datum: this.studentdataService.getSilent(studentId).pipe(
                catchError(() => {
                    this.toastLoadError('datum');
                    return of(null);
                })
            ),
            profile: this.studentdataService.getProfile(studentId, true).pipe(
                catchError(() => {
                    this.messageService.add({
                        severity: 'warn',
                        summary: this.translationService.translate('COMMON.WARNING'),
                        detail: this.translationService.translate(
                            'REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.PROFILE_PARTIAL'
                        )
                    });
                    return of(null);
                })
            ),
            progress: this.studentdataService.getProgress(studentId, true).pipe(catchError(() => of(null))),
            yearResults: this.studentdataService.getYearResults(studentId, true).pipe(catchError(() => of([])))
        }).subscribe({
            next: ({ datum, profile, progress, yearResults }) => {
                if (datum && typeof datum === 'object') {
                    this.studentDatum = datum as Record<string, unknown>;
                    this.rebuildRegistrationRows();
                }
                this.profile = profile as ProfileMap;
                this.progress = (progress as StudentProgressDto | null) ?? null;
                this.yearResults = (yearResults as StudentYearResultDto[]) ?? [];
                this.loading = false;
            },
            error: () => {
                this.loading = false;
            }
        });
    }

    private toastLoadError(_which: string): void {
        this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('COMMON.ERROR'),
            detail: this.translationService.translate(
                'REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.ERR_LOAD_STUDENT'
            )
        });
    }

    get hasStudent(): boolean {
        return !!(this.studentDatum && this.selectedStudent?.studentId);
    }

    get photoUrl(): string | null {
        const p = this.studentDatum?.['picturePath'];
        if (p == null || p === '') {
            return null;
        }
        const s = String(p);
        if (s.startsWith('http://') || s.startsWith('https://')) {
            return s;
        }
        return `${environment.apiUrl}/${s.replace(/^\/+/, '')}`;
    }

    get studentDisplayName(): string {
        const n = this.studentDatum?.['studentName'];
        return n != null ? String(n) : '';
    }

    get profileSections(): { key: string; value: unknown }[] {
        if (!this.profile) {
            return [];
        }
        const skip = new Set(['student', 'progress', 'yearResults']);
        return Object.keys(this.profile)
            .filter((k) => !skip.has(k))
            .map((k) => ({ key: k, value: this.profile![k] }));
    }

    profileSectionLabel(key: string): string {
        const k = `REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.SEC_${key.toUpperCase()}`;
        const t = this.translationService.translate(k);
        return t !== k ? t : this.humanizeKey(key);
    }

    isPlainObject(v: unknown): v is Record<string, unknown> {
        return v !== null && typeof v === 'object' && !Array.isArray(v);
    }

    objectEntries(v: Record<string, unknown>): { k: string; v: unknown }[] {
        return Object.keys(v)
            .sort()
            .map((k) => ({ k, v: v[k] }));
    }

    isNonEmptyArray(v: unknown): v is unknown[] {
        return Array.isArray(v) && v.length > 0;
    }

    arrayTableColumns(firstRow: Record<string, unknown>): string[] {
        return Object.keys(firstRow).slice(0, 12);
    }

    arrayTableColumnsForArray(arr: unknown[]): string[] {
        const row = arr.find((x) => this.isPlainObject(x)) as Record<string, unknown> | undefined;
        if (!row) {
            return ['value'];
        }
        return this.arrayTableColumns(row);
    }

    isScalarProfileValue(v: unknown): boolean {
        return v !== null && typeof v !== 'object';
    }

    /**
     * Rows and columns of whatever tab is open, shared by the CSV export so it
     * never silently exports the registration tab while another one is showing.
     */
    private currentTabExport(): {
        columns: { key: string; header: string }[];
        rows: object[];
    } {
        const tr = this.translationService.translate.bind(this.translationService);
        const fld = tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.COL_FIELD');
        const val = tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.COL_VALUE');
        const fieldValue = [
            { key: 'label', header: fld },
            { key: 'value', header: val }
        ];

        switch (this.activeTabIndex) {
            case 1:
                return { columns: fieldValue, rows: this.idCardRows() };
            case 2:
                return { columns: fieldValue, rows: this.profileRows() };
            case 3:
                return {
                    columns: [
                        { key: 'yearNumber', header: '#' },
                        { key: 'academicYear', header: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.COL_ACAD_YEAR') },
                        { key: 'passed', header: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.COL_PASSED') },
                        { key: 'grade', header: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.COL_GRADE') },
                        { key: 'notes', header: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.COL_NOTES') }
                    ],
                    rows: this.yearResultsForPrint()
                };
            default:
                return { columns: fieldValue, rows: this.registrationRows };
        }
    }

    /** True when the open tab has tabular rows worth exporting. */
    get canExportCurrentTab(): boolean {
        return this.hasStudent && this.currentTabExport().rows.length > 0;
    }

    /** The 360° profile flattened to field/value pairs for print and export. */
    private profileRows(): RegistrationRow[] {
        if (!this.profile) {
            return [];
        }
        return this.flattenRegistration(this.profile as Record<string, unknown>);
    }

    private yearResultsForPrint(): Record<string, string | number | boolean>[] {
        return this.yearResults.map((r) => ({
            yearNumber: r.yearNumber,
            academicYear: r.academicYear ?? '',
            passed: yearOutcome(r) === 'not-recorded'
                ? this.translationService.translate('STUDENT_QUICK_EDIT.YEAR_RESULTS.NOT_RECORDED')
                : r.passed,
            grade: r.grade ?? '',
            notes: r.notes ?? ''
        }));
    }

    private progressRows(): { label: string; value: string }[] {
        const tr = this.translationService.translate.bind(this.translationService);
        const p = this.progress;
        if (!p) {
            return [{ label: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.NO_PROGRESS'), value: '' }];
        }
        return [
            {
                label: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.PR_STUDENT_ID'),
                value: String(p.studentId ?? '')
            },
            {
                label: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.PR_YEAR_LABEL'),
                value: pickLanguage(p, 'currentYearLabel', this.translationService.getCurrentLanguage())
            },
            {
                label: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.PR_YEAR_ORDER'),
                value: String(p.currentYearOrder ?? '')
            },
            {
                label: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.PR_TOTAL_YEARS'),
                value: String(p.totalYears ?? '')
            },
            {
                label: tr('REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.PR_PROGRESS_PCT'),
                value: String(p.progressPercent ?? '')
            }
            // GPA and "graduated" are gone from StudentProgressDto (plan 3.1): no
            // table records either, so nothing is shown for them (owner decision O1).
        ];
    }

    idCardRows(): RegistrationRow[] {
        const d = this.studentDatum;
        if (!d) {
            return [];
        }
        const lbl = (i18nKey: string) =>
            this.translationService.translate(
                `REPORTS.INDIVIDUAL_REPORTS.INDIVIDUAL_STUDENT.${i18nKey}`
            );
        const val = (k: string): string =>
            d[k] != null && String(d[k]) !== '' ? String(d[k]) : '';
        /** Lookup FKs are shown as names, never as the raw id the API returns. */
        const name = (k: string, list: LookupItem[]): string => {
            const id = d[k];
            if (id == null || String(id) === '' || String(id) === '0') {
                return '';
            }
            const hit = list.find((o) => String(o.id) === String(id));
            return hit?.name ?? '';
        };
        const defs: [string, string][] = [
            ['ID_ROW_STUDENT_ID', val('studentId')],
            ['ID_ROW_STUDENT_NAME', val('studentName')],
            ['ID_ROW_PASSPORT', val('studentPassportNumber')],
            ['ID_ROW_BIRTHDATE', val('studentBirthdate').slice(0, 10)],
            ['ID_ROW_BIRTH_PLACE', val('studentBirthdatePlace')],
            ['ID_ROW_GENDER', name('studentGender', this.genderOptions)],
            ['ID_ROW_MAJOR', val('studentMajorName') || name('studentMajorId', this.majorOptions)],
            ['ID_ROW_STATUS_ID', name('studentStudyStatusId', this.studyStatusOptions)],
            ['ID_ROW_DEGREE_ID', name('studentDegreeId', this.degreeOptions)],
            ['ID_ROW_LEVEL_ID', name('studentCurrentLevelId', this.levelOptions)],
            ['ID_ROW_UNIVERSITY_IN', name('studyUniversityIn', this.universityInOptions)],
            ['ID_ROW_COUNTRY_IN', name('studyCountryIn', this.countryInOptions)],
            ['ID_ROW_DELEG_YEAR', val('studentDelegationDecisionYear')],
            ['ID_ROW_DELEG_NO', val('studentDelegateDecisionNo')],
            ['ID_ROW_AMOUNT', val('studentAmount')],
            ['ID_ROW_ADDED_Q', val('studentAddedQuarter')],
            ['ID_ROW_LAST_Q', val('studentLastQuarter')]
        ];
        return defs
            .map(([lk, v]) => ({ field: lk, label: lbl(lk), value: v }))
            .filter((r) => r.value !== '');
    }

    private escapeHtml(s: string): string {
        return s
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    private rebuildRegistrationRows(): void {
        if (!this.studentDatum) {
            this.registrationRows = [];
            return;
        }
        this.registrationRows = this.flattenRegistration(this.studentDatum);
    }

    private flattenRegistration(
        obj: Record<string, unknown>,
        prefix = '',
        depth = 0
    ): RegistrationRow[] {
        if (depth > 4) {
            const path = prefix || 'nested';
            return [{ field: path, label: this.humanizeKey(path), value: '[…]' }];
        }
        const rows: RegistrationRow[] = [];
        const row = (path: string, value: string): RegistrationRow => ({
            field: path,
            label: this.humanizeKey(path),
            value
        });
        for (const key of Object.keys(obj).sort()) {
            const v = obj[key];
            const path = prefix ? `${prefix}.${key}` : key;
            if (v === null || v === undefined) {
                rows.push(row(path, ''));
            } else if (v instanceof Date) {
                rows.push(row(path, v.toISOString().slice(0, 10)));
            } else if (typeof v === 'object' && !Array.isArray(v)) {
                rows.push(...this.flattenRegistration(v as Record<string, unknown>, path, depth + 1));
            } else if (Array.isArray(v)) {
                rows.push(row(path, '[' + v.length + ']'));
            } else if (typeof v === 'boolean' || typeof v === 'number') {
                rows.push(row(path, String(v)));
            } else {
                const s = String(v);
                if (
                    /^-?\d{4}-\d{2}-\d{2}/.test(s) &&
                    (s.includes('T') || s.length >= 10)
                ) {
                    rows.push(row(path, s.slice(0, 10)));
                } else {
                    rows.push(row(path, s));
                }
            }
        }
        return rows;
    }

    humanizeKey(key: string): string {
        return key
            .replace(/([A-Z])/g, ' $1')
            .replace(/\./g, ' · ')
            .replace(/^./, (s) => s.toUpperCase())
            .trim();
    }

    stringifyCell(v: unknown): string {
        if (v === null || v === undefined) {
            return '';
        }
        if (typeof v === 'object') {
            try {
                return JSON.stringify(v);
            } catch {
                return String(v);
            }
        }
        return String(v);
    }

    progressPercentForBar(): number {
        const n = Number(this.progress?.progressPercent);
        if (!Number.isFinite(n)) {
            return 0;
        }
        return Math.max(0, Math.min(100, n));
    }

    reportLoading = false;
    pdfApiPath = '';
    pdfQuery: ReportPdfQuery | null = null;
    loadTrigger = 0;
    selectedLang = uiReportLang(this.translationService);

    // These three follow the selected tab rather than always rendering the
    // generic student-list export, so the PDF matches the view on screen.

    generateReport(): void {
        this.generateDoc(this.activeDocKind, true);
    }

    /**
     * The four actions follow the selected tab, so both the endpoint and the file
     * name are derived from it rather than fixed on the page. `docApiPath` returns
     * an empty string with no student selected; the bar is disabled in that state.
     */
    get currentDocApiPath(): string {
        const id = this.selectedStudent?.studentId;
        return id ? this.docApiPath(this.activeDocKind, id) : '';
    }

    get currentDocBaseFilename(): string {
        const id = this.selectedStudent?.studentId;
        return id ? `${this.activeDocKind}-${id}` : this.activeDocKind;
    }

    /** Filters for this run of the report; shared by all four actions. */
    reportQuery(): ReportPdfQuery {
        return { lang: this.selectedLang };
    }

    get reportLangOptions(): Array<{ label: string; value: string }> {
        return this.lookupService.getReportLangOptions();
    }

    // ── Official documents (server-rendered Jasper PDFs) ──────────────────────

    /**
     * The official single-student documents, from the report catalogue: the
     * cards under the student hub that deep-link here with `?doc=`. `kind` is
     * the URL segment on /api/reports/individual-student/{id}/{kind}/export-report.
     */
    officialDocs: OfficialDoc[] = [];

    /** Document named by ?doc= when navigated to from a hub card. */
    focusedDoc: StudentDocKind | null = null;

    /**
     * The official document each tab produces, in tab order. Every action on the
     * page — preview, download, the embedded viewer, the CSV export — follows the
     * selected tab, so what you print is always what you are looking at.
     *
     *   0 student form (all fields) → registration form
     *   1 ID summary               → student ID card
     *   2 full 360° profile        → financial statement (the profile is finance-led)
     *   3 academic history         → enrollment certificate
     */
    private readonly tabDocKinds: StudentDocKind[] = [
        'registration-form',
        'id-card',
        'financial-statement',
        'enrollment-certificate'
    ];

    /**
     * The financial statement is the only document gated on REPORT_FINANCE rather
     * than the page's REPORT_ACADEMIC, so an academic-only user would get a 403
     * simply by opening the profile tab. They are shown the registration form
     * instead, and the finance card is hidden from the grid below.
     */
    private get canSeeFinancialStatement(): boolean {
        return this.umsPermission.canAccessPage('REPORT_FINANCE');
    }

    get officialDocsAllowed(): OfficialDoc[] {
        return this.canSeeFinancialStatement
            ? this.officialDocs
            : this.officialDocs.filter((d) => d.kind !== 'financial-statement');
    }

    get activeDocKind(): StudentDocKind {
        const kind = this.tabDocKinds[this.activeTabIndex] ?? this.tabDocKinds[0];
        if (kind === 'financial-statement' && !this.canSeeFinancialStatement) {
            return 'registration-form';
        }
        return kind;
    }

    get activeDoc(): OfficialDoc {
        const kind = this.activeDocKind;
        return this.officialDocs.find((d) => d.kind === kind)
            ?? { kind, title: '', description: '', icon: '' };
    }

    /** Filename for whatever the current tab produces. */
    get activeDocFilename(): string {
        return `${this.activeDocKind}-${this.selectedStudent?.studentId ?? 'student'}.pdf`;
    }

    // p-tabs emits the selected tab's value; p-tabView emitted an { index } object.
    onTabChange(index: number): void {
        this.activeTabIndex = index;
        if (this.hasStudent) {
            this.generateDoc(this.activeDocKind);
        }
    }

    /** Tab showing the given document, so ?doc= opens the matching view. */
    private tabIndexForDoc(kind: StudentDocKind): number {
        const i = this.tabDocKinds.indexOf(kind);
        return i >= 0 ? i : 0;
    }

    private docApiPath(kind: StudentDocKind, id: number | string): string {
        return `/api/reports/individual-student/${id}/${kind}/export-report`;
    }

    /**
     * What the viewer is currently showing: student, document kind and language.
     * Rendering is a Jasper run on the server plus a PDF parse in the browser, and
     * every tab click asked for it again — including clicks back to a tab already
     * rendered, and clicks that map to the document already on screen (the finance
     * tab falls back to the registration form for academic-only users). Four tabs
     * therefore cost four renders per student, and the rate limit did the rest
     * (review item P2).
     */
    private renderedDocKey: string | null = null;

    /** Renders the document into the embedded viewer below, unless it is already there. */
    generateDoc(kind: StudentDocKind, force = false): void {
        const id = this.selectedStudent?.studentId;
        if (!id) return;
        const key = `${id}|${kind}|${this.selectedLang}`;
        if (!force && key === this.renderedDocKey) {
            return;
        }
        this.renderedDocKey = key;
        this.pdfApiPath = this.docApiPath(kind, id);
        this.pdfQuery = { lang: this.selectedLang };
        this.loadTrigger++;
    }

    previewDoc(kind: StudentDocKind): void {
        const id = this.selectedStudent?.studentId;
        if (!id) return;
        this.reportPdfService.previewPdf(this.docApiPath(kind, id), { lang: this.selectedLang });
    }

    downloadDoc(kind: StudentDocKind): void {
        const id = this.selectedStudent?.studentId;
        if (!id) return;
        this.reportPdfService.downloadPdf(
            this.docApiPath(kind, id),
            { lang: this.selectedLang },
            `${kind}-${id}.pdf`
        );
    }

}
