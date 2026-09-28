import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA, Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpParams, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DividerModule } from 'primeng/divider';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';

import { TranslatePipe } from 'src/app/demo/pipes/translate.pipe';
import { TranslationService } from 'src/app/demo/service/translation.service';
import { ReportCountryScopeService } from 'src/app/demo/service/report-country-scope.service';
import { ReportPdfService } from 'src/app/demo/service/report-pdf.service';
import { LookupService } from 'src/app/demo/service/lookup.service';

import { StudentDemographicsComponent } from './student-demographics/student-demographics.component';
import { AcademicPerformanceComponent } from './academic-performance/academic-performance.component';
import { StudentStatusReportComponent } from './student-status-report/student-status-report.component';
import { MajorDistributionComponent } from './major-distribution/major-distribution.component';
import { StudentListDetailedComponent } from './student-list-detailed/student-list-detailed.component';

/**
 * Five student report pages threw NG0100 (ExpressionChangedAfterItHasBeenChecked)
 * on every change-detection tick once their data had arrived (live QA 23 Sep 2026,
 * finding M3).
 *
 * <p>The cause was an `@for` over a *method* that built fresh object literals on
 * each call, tracked by identity. On the dev-mode verification pass every item was
 * "new", so the repeater tore the rows down and rebuilt them after the parent had
 * already been checked, and the first binding in each rebuilt row threw. The
 * distributions are now fields computed where the data lands, tracked by their
 * label or row key.
 *
 * <p>Each case renders the page, answers its start-up requests with two rows that
 * differ in the field the page groups on, and runs one more tick. In dev mode
 * `ComponentFixture.detectChanges()` follows the check with `checkNoChanges`, so
 * the error surfaces synchronously.
 */
describe('student report pages after their data arrives (QA M3, NG0100)', () => {

    interface Case {
        name: string;
        component: Type<any>;
        dataUrl: string;
        rows: unknown[];
    }

    const CASES: Case[] = [
        {
            name: 'student demographics',
            component: StudentDemographicsComponent,
            dataUrl: '/mohe/api/reports/student-demographics/search',
            rows: [
                { studentId: 1, studentName: 'A', studentCountryId: 1, studentCountryName: 'Egypt',
                  studentGenderName: 'Male', studentAge: 22, studentCurrentLevelId: 1, studentCurrentLevelName: 'L1',
                  studentStudyTypeId: 1, studentStudyTypeName: 'Full', studentDegreeId: 1, studentDegreeName: 'BSc',
                  studentMajorId: 1, studentMajorName: 'Law', studentStudyStatusId: 1, studentStudyStatusName: 'Active' },
                { studentId: 2, studentName: 'B', studentCountryId: 1, studentCountryName: 'Egypt',
                  studentGenderName: 'Female', studentAge: 27, studentCurrentLevelId: 2, studentCurrentLevelName: 'L2',
                  studentStudyTypeId: 1, studentStudyTypeName: 'Full', studentDegreeId: 2, studentDegreeName: 'MSc',
                  studentMajorId: 2, studentMajorName: 'Medicine', studentStudyStatusId: 3, studentStudyStatusName: 'Withdrawn' }
            ]
        },
        {
            name: 'academic performance',
            component: AcademicPerformanceComponent,
            dataUrl: '/mohe/api/reports/academic-performance/search',
            rows: [
                { studentId: 1, studentName: 'A', studentMajorId: 1, studentMajorName: 'Law', studentCurrentLevelId: 1,
                  studentCurrentLevelName: 'L1', studentStudyNoOfYears: 2, studentStudyStatusId: 1, studentStudyStatusName: 'Active',
                  studentDegreeId: 1, studentDegreeName: 'BSc', studentCountryId: 1, studentCountryName: 'Egypt', yearsEnrolled: 2 },
                { studentId: 2, studentName: 'B', studentMajorId: 2, studentMajorName: 'Medicine', studentCurrentLevelId: 2,
                  studentCurrentLevelName: 'L2', studentStudyNoOfYears: 5, studentStudyStatusId: 4, studentStudyStatusName: 'Suspended',
                  studentDegreeId: 2, studentDegreeName: 'MSc', studentCountryId: 1, studentCountryName: 'Egypt', yearsEnrolled: 5 }
            ]
        },
        {
            name: 'student status report',
            component: StudentStatusReportComponent,
            dataUrl: '/mohe/api/reports/student-status/search',
            rows: [
                { studentStudyStatusId: 1, studentStudyStatusName: 'Active', studentCountryId: 1, studentCountryName: 'Egypt',
                  studentMajorId: 1, studentMajorName: 'Law', studentCount: 7, statusCategory: 'Active' },
                { studentStudyStatusId: 3, studentStudyStatusName: 'Withdrawn', studentCountryId: 1, studentCountryName: 'Egypt',
                  studentMajorId: 2, studentMajorName: 'Medicine', studentCount: 3, statusCategory: 'Withdrawn' }
            ]
        },
        {
            name: 'major distribution',
            component: MajorDistributionComponent,
            dataUrl: '/mohe/api/reports/major-distribution/search',
            rows: [
                { studentMajorId: 1, studentMajorName: 'Law', studentDegreeId: 1, studentDegreeName: 'BSc', studentCountryId: 1,
                  studentCountryName: 'Egypt', studentStudyTypeId: 1, studentStudyTypeName: 'Full', studentCount: 7, maleCount: 4, femaleCount: 3 },
                { studentMajorId: 2, studentMajorName: 'Medicine', studentDegreeId: 2, studentDegreeName: 'MSc', studentCountryId: 1,
                  studentCountryName: 'Egypt', studentStudyTypeId: 1, studentStudyTypeName: 'Full', studentCount: 3, maleCount: 1, femaleCount: 2 }
            ]
        },
        {
            name: 'student list detailed',
            component: StudentListDetailedComponent,
            dataUrl: '/mohe/api/reports/student-list/search',
            rows: [
                { studentId: 1, studentName: 'A', studentPassportNumber: 'P1', studentBirthdate: '2000-01-01', studentAge: 26,
                  studentCountryId: 1, studentCountryName: 'Egypt', studentGender: 1, studentGenderName: 'Male',
                  studentMajorId: 1, studentMajorName: 'Law', studentDegreeId: 1, studentDegreeName: 'BSc',
                  studentCurrentLevelId: 1, studentCurrentLevelName: 'L1', studentStudyTypeId: 1, studentStudyTypeName: 'Full',
                  studentStudyStatusId: 1, studentStudyStatusName: 'Active', studentUniversityTypeId: 1, studentUniversityTypeName: 'U',
                  studentAgencyTypeId: 1, studentAgencyTypeName: 'Ag', studentMinistryTypeId: 1, studentMinistryTypeName: 'M',
                  studentStudyNoOfYears: 2, studentDelegationDecisionYear: '2024', studentCreatedDate: '2024-01-01' },
                { studentId: 2, studentName: 'B', studentPassportNumber: 'P2', studentBirthdate: '1998-01-01', studentAge: 28,
                  studentCountryId: 1, studentCountryName: 'Egypt', studentGender: 2, studentGenderName: 'Female',
                  studentMajorId: 2, studentMajorName: 'Medicine', studentDegreeId: 2, studentDegreeName: 'MSc',
                  studentCurrentLevelId: 2, studentCurrentLevelName: 'L2', studentStudyTypeId: 1, studentStudyTypeName: 'Full',
                  studentStudyStatusId: 3, studentStudyStatusName: 'Withdrawn', studentUniversityTypeId: 1, studentUniversityTypeName: 'U',
                  studentAgencyTypeId: 1, studentAgencyTypeName: 'Ag', studentMinistryTypeId: 1, studentMinistryTypeName: 'M',
                  studentStudyNoOfYears: 4, studentDelegationDecisionYear: '2022', studentCreatedDate: '2022-01-01' }
            ]
        }
    ];

    /** Every list a metadata endpoint on these pages can carry, all empty. */
    const emptyMetadata = () => ({
        countries: [], genders: [], ageRanges: [], levels: [], studyTypes: [], degrees: [], majors: [],
        statuses: [], universityTypes: [], agencyTypes: [], ministryTypes: []
    });

    const translation = {
        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
    };

    const countryScope = {
        hasMultiCountryAccess: () => true,
        selectedCountryId$: of(null),
        selectedCountryId: null,
        appendCountryParam: () => new HttpParams(),
        appendMultiCountryParams: () => new HttpParams(),
        filterToAllowedStudyCountries: <T>(x: T[]) => x,
        countryIdParam: () => null,
        isMinistryUser: true,
        allowedCountryIds: [] as number[]
    };

    const lookup = {
        getReportDimensions: () => of([]),
        getReportLangOptions: () => [] as Array<{ label: string; value: string }>
    };

    let http: HttpTestingController;
    let pdf: jasmine.SpyObj<ReportPdfService>;

    const configure = async (component: Type<any>) => {
        pdf = jasmine.createSpyObj<ReportPdfService>('ReportPdfService',
            ['previewPdf', 'downloadPdf', 'printPdf', 'exportExcel', 'toExcelApiPath', 'toastPdfError']);

        await TestBed.configureTestingModule({
            declarations: [component],
            imports: [
                FormsModule, RouterTestingModule, TranslatePipe,
                TableModule, SelectModule, MultiSelectModule, ButtonModule, TagModule,
                CheckboxModule, DividerModule, SelectButtonModule, ToastModule
            ],
            // `app-report-actions` / `app-report-viewer` live in SharedModule and are
            // not what is under test here; leave them as unknown elements.
            schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
            providers: [
                MessageService,
                provideNoopAnimations(),
                { provide: TranslationService, useValue: translation },
                { provide: ReportCountryScopeService, useValue: countryScope },
                { provide: LookupService, useValue: lookup },
                { provide: ReportPdfService, useValue: pdf },
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting()
            ]
        }).compileComponents();

        http = TestBed.inject(HttpTestingController);
    };

    /**
     * Answers whatever the page asked for on start-up: the country list, its
     * metadata, and its data. Loops in case a response triggers another request.
     */
    const answerRequests = (c: Case) => {
        for (let round = 0; round < 5; round++) {
            const pending = http.match(() => true);
            if (pending.length === 0) { return; }
            for (const req of pending) {
                const url = req.request.url;
                if (url.includes('/lookup-tables/study-country-in')) {
                    req.flush([]);
                } else if (url.endsWith('/metadata')) {
                    req.flush(emptyMetadata());
                } else if (url === c.dataUrl) {
                    req.flush(c.rows);
                } else {
                    fail(`${c.name}: unexpected request ${req.request.method} ${url}`);
                    req.flush(null);
                }
            }
        }
    };

    /** Runs a tick and keeps its error, so a throw on one tick does not hide the next. */
    const tick = (fixture: ComponentFixture<unknown>, errors: string[], label: string) => {
        try {
            fixture.detectChanges();
        } catch (e) {
            errors.push(`${label}: ${e instanceof Error ? e.message : String(e)}`);
        }
    };

    for (const c of CASES) {
        describe(c.name, () => {
            beforeEach(async () => {
                await configure(c.component);
            });

            afterEach(() => {
                http.verify();
            });

            it('renders its distributions without NG0100 on the tick after the data lands', () => {
                const fixture = TestBed.createComponent(c.component);
                // The page provides its own MessageService for the toast; an error toast
                // would mean the load path failed and the assertion below was vacuous.
                const toasts = fixture.debugElement.injector.get(MessageService);
                spyOn(toasts, 'add').and.callThrough();
                const errors: string[] = [];

                tick(fixture, errors, 'first tick (ngOnInit, no data yet)');
                answerRequests(c);
                tick(fixture, errors, 'tick after data arrived');
                tick(fixture, errors, 'steady-state tick');

                expect(toasts.add).not.toHaveBeenCalled();
                expect(errors).withContext(errors.join('\n')).toEqual([]);
                expect(errors.join('\n')).not.toMatch(/NG0100|ExpressionChanged/);
            });
        });
    }
});
