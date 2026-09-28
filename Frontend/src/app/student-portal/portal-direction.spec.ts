import { Type } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeArEg from '@angular/common/locales/ar-EG';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { DIR_DOCUMENT } from '@angular/cdk/bidi';
import { of } from 'rxjs';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import {
    ArrowLeftOutline, ArrowRightOutline, CalendarOutline, CarOutline, CheckCircleFill, CheckCircleOutline,
    CheckOutline, ClockCircleFill, ClockCircleOutline, CloseCircleFill, CloseCircleOutline, CloseOutline,
    DollarOutline, DownloadOutline, EditOutline, EnvironmentOutline, ExclamationCircleOutline, EyeOutline,
    FieldTimeOutline, FileDoneOutline, FilePdfTwoTone, FileTextOutline, FileTwoTone, FlagOutline,
    FolderOpenOutline, FormOutline, GlobalOutline, IdcardOutline, InboxOutline, InfoCircleOutline, LockOutline,
    LoginOutline, MailOutline, MessageOutline, PlusOutline, ProfileOutline, QuestionCircleOutline, ReadOutline,
    ReloadOutline, SafetyOutline, SearchOutline, TeamOutline, UploadOutline, UserOutline, BarChartOutline
} from '@ant-design/icons-angular/icons';

import { StudentPortalModule } from './student-portal.module';
import { StudentPortalApiService } from './student-portal-api.service';
import { TranslationService } from '../demo/service/translation.service';
import { CodeListService } from '../demo/service/code-list.service';
import { MyDataComponent } from './my-data/my-data.component';
import { FinanceStatusComponent } from './finance-status/finance-status.component';
import { YearResultsComponent } from './year-results/year-results.component';
import { DocumentsComponent } from './documents/documents.component';
import { MyRequestsComponent } from './my-requests/my-requests.component';
import { StudentPortalLoginComponent } from './login/login.component';

/**
 * Which side of the portal's rows their first item lands on.
 *
 * <p>The shell puts `dir="rtl"` on its root in Arabic and `direction` is
 * inherited, so every flex row on a page already runs right to left: its first
 * child sits at the right edge without help. Six stylesheets then said
 * `flex-direction: row-reverse` under `.portal-rtl` (the login page under its own
 * `[dir="rtl"]`), which turned each row round a second time. In Arabic the My Data
 * avatar sat at the far left with the name beside it, away from the edge the
 * page is read from; the year cards and the finance cards read value-then-label;
 * and the change-request arrow pointed from the new value back at the old one.
 * The same double mirror styles.scss documents for `.flex.justify-content-between`
 * on the staff side.
 *
 * <p>Measured in both directions against the neighbour rather than against a
 * number: "in front of" means "on the edge the page is read from", and that
 * survives a change of padding or font.
 *
 * <p>The labels of a vertical ng-zorro form are here too. ng-zorro's own RTL rule
 * for them never matches (see styles.scss), so on the sign-in and Documents
 * forms each label sat at its field's left edge in Arabic.
 */

// The app registers it in SharedModule, which the portal module does not import;
// the Arabic dates on Documents and My requests need it.
registerLocaleData(localeArEg, 'ar-EG');

const NAME = 'زيد علي عبدالجبار المهاب';
const CREATED = '2026-09-20T10:00:00Z';

/** Every reference list empty: the names on the record are shown as they came. */
const NO_LOOKUPS = Object.fromEntries([
    'genders', 'studentCountries', 'studentGovernments', 'studentMunicipalities', 'degrees', 'currentLevels',
    'studyTypes', 'studyStatuses', 'studyCountries', 'studyGovernments', 'studyUniversities', 'studyColleges',
    'studyEducations', 'studyMajors', 'agencyTypes', 'ministryTypes', 'universityTypes'
].map(list => [list, []]));

const api = {
    isLoggedIn: () => false,
    getMyData: () => of({
        student: {
            studentId: 2, studentName: NAME,
            studentCurrentLevelId: 1, studentCurrentLevelName: 'السنة الأولى',
            studentStudyStatusId: 1, studentStudyStatusName: 'نشط'
        },
        finance: {
            studentFinId: 9, received: true, studyFinRecievedStatusName: 'مستلم',
            studyFinRecievedStatusNameEn: 'Received', studentFinRecievedAmount: 1500
        }
    }),
    getLookups: () => of(NO_LOOKUPS),
    getProgress: () => of(null),
    getYearResults: () => of([
        { yearNumber: 1, academicYear: '2024/2025', passed: true, grade: 'B+', recorded: true }
    ]),
    getDocuments: () => of([
        { documentId: 1, studentId: 2, documentName: 'passport.pdf', documentType: 'PASSPORT', uploadDate: '2026-09-01' }
    ]),
    getDocumentTypes: () => of([]),
    getMyChangeRequests: () => of([
        {
            requestId: 11, studentId: 2, studentUserId: 5, submissionId: 'SUB-1', fieldName: 'studentName',
            fieldLabel: 'Name', oldValue: 'Old', newValue: 'New', status: 'REJECTED',
            rejectionReason: 'The document is not legible', createdAt: CREATED
        }
    ]),
    getAllowedChangeFields: () => of({ fields: [], types: {} }),
    loginCountries: () => of([{ id: 1, studyCountryInName: 'مصر', studyCountryInNameEn: 'Egypt' }]),
    captcha: () => of({ id: 'c-1', imageUrl: '' })
};

describe('student portal reading direction', () => {

    let fixture: ComponentFixture<unknown>;
    let frame: HTMLElement;

    /** `isRTL` can be swapped out to switch the language in place after the build. */
    const build = async <T>(component: Type<T>, dir: 'ltr' | 'rtl', isRTL = () => dir === 'rtl') => {
        const lang = dir === 'rtl' ? 'ar' : 'en';
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [StudentPortalModule, RouterModule.forRoot([])],
            providers: [
                {
                    provide: NZ_ICONS,
                    useValue: [
                        ArrowLeftOutline, ArrowRightOutline, BarChartOutline, CalendarOutline, CarOutline,
                        CheckCircleFill, CheckCircleOutline, CheckOutline, ClockCircleFill, ClockCircleOutline,
                        CloseCircleFill, CloseCircleOutline, CloseOutline, DollarOutline, DownloadOutline,
                        EditOutline, EnvironmentOutline, ExclamationCircleOutline, EyeOutline, FieldTimeOutline,
                        FileDoneOutline, FilePdfTwoTone, FileTextOutline, FileTwoTone, FlagOutline,
                        FolderOpenOutline, FormOutline, GlobalOutline, IdcardOutline, InboxOutline,
                        InfoCircleOutline, LockOutline, LoginOutline, MailOutline, MessageOutline, PlusOutline,
                        ProfileOutline, QuestionCircleOutline, ReadOutline, ReloadOutline, SafetyOutline,
                        SearchOutline, TeamOutline, UploadOutline, UserOutline
                    ]
                },
                { provide: StudentPortalApiService, useValue: api },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of(lang), initialized$: of(true), isReady: () => true,
                        isRTL, translate: (k: string) => k.split('.').pop()!,
                        getCurrentLanguage: () => lang, getAvailableLanguages: () => [], switchLanguage: () => {}
                    }
                },
                {
                    provide: CodeListService,
                    useValue: { find: () => undefined, rows: () => [], label: (_: string, v: string) => v, active: () => [], extra: () => null }
                },
                // ng-zorro widgets take their direction from the CDK Directionality,
                // not from CSS: the shell's [dir] directive in the portal, the root
                // one (read from <body dir>) on the sign-in page.
                { provide: DIR_DOCUMENT, useValue: { body: { dir }, documentElement: { dir } } }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(component);

        // What the portal shell puts around a routed page: the direction, and in
        // Arabic the class the pages' own stylesheets key on.
        frame = document.createElement('div');
        frame.setAttribute('dir', dir);
        frame.style.width = '1100px';
        if (dir === 'rtl') { frame.className = 'portal-rtl'; }
        document.body.appendChild(frame);
        frame.appendChild(fixture.nativeElement);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => frame?.remove());

    const el = (selector: string): HTMLElement => {
        const found = (fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement;
        expect(found).withContext(`nothing matches ${selector}`).toBeTruthy();
        return found;
    };

    /** `first` sits on the reading-start side of `second`: the right in Arabic, the left in English. */
    const expectReadFirst = (first: string, second: string, dir: 'ltr' | 'rtl') => {
        const a = el(first).getBoundingClientRect();
        const b = el(second).getBoundingClientRect();
        if (dir === 'ltr') {
            expect(a.right)
                .withContext(`${first} ends at ${Math.round(a.right)}, ${second} starts at ${Math.round(b.left)}`)
                .toBeLessThanOrEqual(b.left + 1);
        } else {
            expect(a.left)
                .withContext(`${first} starts at ${Math.round(a.left)}, ${second} ends at ${Math.round(b.right)}`)
                .toBeGreaterThanOrEqual(b.right - 1);
        }
    };

    /** Each label above a field starts where the field starts: the right edge in Arabic. */
    const expectLabelsAtFieldStart = (dir: 'ltr' | 'rtl') => {
        const items = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.ant-form-item'));
        expect(items.length).withContext('no form items').toBeGreaterThan(0);
        for (const item of items) {
            const label = item.querySelector('.ant-form-item-label label')!.getBoundingClientRect();
            const field = item.querySelector('.ant-form-item-control')!.getBoundingClientRect();
            const gap = dir === 'rtl' ? field.right - label.right : label.left - field.left;
            expect(Math.abs(gap))
                .withContext(`"${item.querySelector('label')!.textContent!.trim()}" starts ${Math.round(gap)}px from its field's start`)
                .toBeLessThanOrEqual(1);
        }
    };

    /** The row is mirrored by `direction` alone, never reversed on top of it. */
    const expectNotReversed = (...selectors: string[]) => {
        for (const selector of selectors) {
            expect(getComputedStyle(el(selector)).flexDirection)
                .withContext(`${selector} is reversed a second time on top of direction: rtl`)
                .not.toBe('row-reverse');
        }
    };

    (['ltr', 'rtl'] as const).forEach(dir => {

        describe(dir, () => {

            it('My Data: the avatar leads the name, on the edge with the accent bar', async () => {
                await build(MyDataComponent, dir);

                expectReadFirst('.md-identity__avatar', '.md-identity__name', dir);
                expectReadFirst('.md-identity__avatar', '.md-identity__meta', dir);
                if (dir === 'rtl') { expectNotReversed('.md-identity'); }
            });

            it('Finance status: each icon leads its text', async () => {
                await build(FinanceStatusComponent, dir);

                expectReadFirst('.fs-status-icon', '.fs-status-label', dir);
                expectReadFirst('.fs-info-icon', '.fs-info-label', dir);
                if (dir === 'rtl') { expectNotReversed('.fs-status-banner', '.fs-info-card'); }
            });

            it('Year results: the icon leads the year, the label leads its value', async () => {
                await build(YearResultsComponent, dir);

                expectReadFirst('.yr-card-icon', '.yr-card-num', dir);
                expectReadFirst('.yr-label', '.yr-val', dir);
                if (dir === 'rtl') { expectNotReversed('.yr-card-year', '.yr-row'); }
            });

            it('Documents: the file icon leads the name, the download ends the row', async () => {
                await build(DocumentsComponent, dir);

                expectReadFirst('.doc-item-icon', '.doc-item-info', dir);
                expectReadFirst('.doc-item-info', '.doc-item-action', dir);
                if (dir === 'rtl') { expectNotReversed('.doc-item'); }
            });

            it('Documents: the upload form labels start where their fields start', async () => {
                await build(DocumentsComponent, dir);

                expectLabelsAtFieldStart(dir);
            });

            it('My requests: the date leads the status, the old value leads the new, the icon leads the note', async () => {
                await build(MyRequestsComponent, dir);

                expectReadFirst('.cr-group-head__left', '.cr-group-head__right', dir);
                expectReadFirst('.cr-field-name', '.cr-status-tag', dir);
                expectReadFirst('.cr-value--old', '.cr-value--new', dir);
                expectReadFirst('.cr-field-note > :first-child', '.cr-field-note > :last-child', dir);
                if (dir === 'rtl') {
                    expectNotReversed('.cr-group-head', '.cr-field-top', '.cr-field-values', '.cr-field-note');
                }
            });

            it('Login: the brand panel leads the form, and the language switch ends the card', async () => {
                await build(StudentPortalLoginComponent, dir);

                // The brand panel is hidden below 768px; the test browser is a desktop window.
                expectReadFirst('.lp-brand', '.lp-form-panel', dir);

                // Mirrored with the page: top right in English, top left in Arabic.
                const toggle = el('.lp-lang-toggle').getBoundingClientRect();
                const card = el('.lp-form-wrap').getBoundingClientRect();
                const toStart = dir === 'rtl' ? card.right - toggle.right : toggle.left - card.left;
                const toEnd = dir === 'rtl' ? toggle.left - card.left : card.right - toggle.right;
                expect(toEnd)
                    .withContext(`the language switch is ${Math.round(toEnd)}px from the card's end, ${Math.round(toStart)}px from its start`)
                    .toBeLessThan(toStart);
                if (dir === 'rtl') { expectNotReversed('.lp-root'); }
            });

            it('Login: the form labels start where their fields start', async () => {
                await build(StudentPortalLoginComponent, dir);

                expectLabelsAtFieldStart(dir);
            });
        });
    });

    it('Login: a switch to Arabic on the page reaches the ng-zorro widgets', async () => {
        // The sign-in page switches language in place and is not rebuilt, so its
        // widgets only learn of the new direction from the Dir directive on
        // .lp-root. The root Directionality keeps what <body dir> said at load.
        let rtl = false;
        await build(StudentPortalLoginComponent, 'ltr', () => rtl);

        rtl = true;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(el('form.lp-form').classList).withContext('the form kept its left-to-right layout').toContain('ant-form-rtl');
        expectLabelsAtFieldStart('rtl');
        expectReadFirst('.lp-brand', '.lp-form-panel', 'rtl');
    });
});
