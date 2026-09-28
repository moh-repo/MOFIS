import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService, ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';

import { StudentAdminModule } from './student-admin/student-admin.module';
import { StudentSessionsComponent } from './student-admin/student-sessions/student-sessions.component';
import { TranslationService } from '../service/translation.service';

/**
 * How wide `/student-admin/sessions` is, and what it spends the width on.
 *
 * <p>Ten columns asked for 1030px of pinned width inside a 981px table — three
 * date columns at 150px each to show `dd/MM/yy HH:mm`, an actions column of
 * 200px for two icon buttons — before the two columns with no width of their own
 * were given anything. Those two paid for all of it: the student's name had
 * <b>50px</b> to be read in and the token 49px. The name is the only thing on
 * this row a person reads.
 *
 * <p>The page also wrapped its card in `.grid > .col-12`, which is the same 13px
 * of overflow the nine Staff-access pages had: PrimeFlex gives `.grid` a margin
 * of -0.5rem and hands the matching padding to the column, so a grid holding one
 * full-width column is 1rem wider than its box.
 *
 * <p>Measured in both directions. The widths came out identical, which is worth
 * knowing rather than assuming — the Arabic headings are shorter than the English
 * ones but wrap at different places.
 */

/** The width the shell leaves a page on a 1366px window; see page-headings.spec.ts. */
const MAIN_CONTAINER_CONTENT = 1033;

/** The Arabic headings this table actually renders, from ar.json. */
const AR: Record<string, string> = {
    'STUDENT_ADMIN.SESSIONS.SESSION_ID': 'رقم الجلسة',
    'STUDENT_ADMIN.SESSIONS.USER_ID': 'رقم المستخدم',
    'STUDENTS.STUDENT_ID': 'رقم الطالب',
    'STUDENT_PORTAL.NAME': 'الاسم',
    'STUDENT_ADMIN.SESSIONS.TOKEN': 'الرمز (جزئي)',
    'STUDENT_ADMIN.SESSIONS.CREATED': 'تاريخ الإنشاء',
    'STUDENT_ADMIN.SESSIONS.LOGGED_OUT_AT': 'تسجيل الخروج',
    'STUDENT_ADMIN.SESSIONS.EXPIRES': 'تاريخ الانتهاء',
    'STUDENT_ADMIN.SESSIONS.STATUS': 'الحالة',
    'COMMON.ACTIONS': 'الإجراءات'
};

/**
 * A page of sessions as the server sends it: a 32-character token (a UUID with
 * its dashes taken out, from StudentUserAuthService), an eight-digit student
 * number, and an Arabic name of the length these actually run to. Sample rows of
 * the `Test User` sort would make this table look as though it fitted, which is
 * the whole thing being measured.
 */
const ROWS = [
    {
        sessionId: 1, studentUserId: 1042, studentId: 20211534,
        studentName: 'عبدالرحمن محمد علي الشامي',
        token: 'a3f1c9e84b7d264fa0915c3e7b28d41f',
        createdAt: '2026-08-30T09:15:00Z', loggedOutAt: null, expiresAt: '2026-09-29T09:15:00Z'
    },
    {
        sessionId: 2, studentUserId: 998, studentId: 20180042,
        studentName: 'فاطمة عبدالله سعيد المخلافي',
        token: 'bb02d7461f8e4a3c9d05e17a6c3b8402',
        createdAt: '2026-08-29T08:00:00Z', loggedOutAt: '2026-08-29T16:40:00Z', expiresAt: '2026-08-29T16:00:00Z'
    }
];

([false, true] as const).forEach(rtl => {

    describe(`student sessions, in the width the shell leaves it (${rtl ? 'rtl' : 'ltr'})`, () => {

        let fixture: any;
        let frame: HTMLElement;

        beforeEach(async () => {
            const translation = {
                currentLanguage$: of(rtl ? 'ar' : 'en'), initialized$: of(true), isReady: () => true,
                isRTL: () => rtl, getCurrentLanguage: () => (rtl ? 'ar' : 'en'),
                // The real heading, or the last segment of the key with its
                // underscores opened out. Returning the whole key would give every
                // heading an unbreakable 30-character string and make the
                // measurement about the stub rather than the page.
                translate: (key: string) =>
                    rtl ? (AR[key] ?? key.split('.').pop()!.replace(/_/g, ' '))
                        : key.split('.').pop()!.replace(/_/g, ' ')
            };

            await TestBed.configureTestingModule({
                imports: [StudentAdminModule, RouterModule.forRoot([])],
                providers: [
                    MessageService, ConfirmationService, provideNoopAnimations(),
                    {
                        provide: HttpClient,
                        useValue: {
                            get: () => of({ content: [], totalElements: 0 }),
                            post: () => of({}), put: () => of({}), delete: () => of({})
                        }
                    },
                    { provide: TranslationService, useValue: translation }
                ]
            }).compileComponents();

            fixture = TestBed.createComponent(StudentSessionsComponent);
            fixture.detectChanges();
            await fixture.whenStable();

            // The rows go in after the page has loaded its (empty) list, so what
            // is measured is this page rendering real content.
            fixture.componentInstance.sessions = ROWS;
            fixture.componentInstance.totalRecords = ROWS.length;

            frame = document.createElement('div');
            frame.style.width = `${MAIN_CONTAINER_CONTENT}px`;
            frame.setAttribute('dir', rtl ? 'rtl' : 'ltr');
            document.body.appendChild(frame);
            frame.appendChild(fixture.nativeElement);
            fixture.detectChanges();
        });

        afterEach(() => frame.remove());

        const table = () => fixture.nativeElement.querySelector('p-table table') as HTMLElement;

        /** Each column's width, with its heading. */
        const columns = () => {
            const total = table().getBoundingClientRect().width;
            return (Array.from(fixture.nativeElement.querySelectorAll('p-table thead th')) as HTMLElement[])
                .map(th => ({
                    label: (th.textContent ?? '').trim() || '(unlabelled)',
                    width: Math.round(th.getBoundingClientRect().width),
                    share: Math.round((th.getBoundingClientRect().width / total) * 100)
                }));
        };

        const named = (label: string) => {
            const found = columns().find(c => c.label === label);
            expect(found).withContext(`no "${label}" column; the headings changed`).toBeTruthy();
            return found!;
        };

        const nameHeading = () => (rtl ? AR['STUDENT_PORTAL.NAME'] : 'NAME');
        const dateHeadings = () => (rtl
            ? [AR['STUDENT_ADMIN.SESSIONS.CREATED'], AR['STUDENT_ADMIN.SESSIONS.LOGGED_OUT_AT'], AR['STUDENT_ADMIN.SESSIONS.EXPIRES']]
            : ['CREATED', 'LOGGED OUT AT', 'EXPIRES']);

        it('renders its rows', () => {
            expect(fixture.nativeElement.querySelectorAll('p-table tbody tr').length)
                .withContext('the sample rows did not reach the table, so nothing below measures anything')
                .toBeGreaterThan(0);
        });

        it('is no wider than the container the shell gives it', () => {
            // The frame, not the page's own first child: `.grid` carries negative
            // margins, so its own clientWidth already includes the overflow and
            // measuring it against itself can never fail.
            expect(frame.scrollWidth)
                .withContext(`the page needs ${frame.scrollWidth}px inside ${frame.clientWidth}px, `
                    + `so the whole window scrolls`)
                .toBeLessThanOrEqual(frame.clientWidth);
        });

        /**
         * A column of 50px holds about six characters; below that a heading is
         * unreadable and a value is a column of single letters. The name had
         * exactly 50px and the token 49px.
         */
        it('leaves every column a readable width', () => {
            const thin = columns().filter(c => c.width < 60);
            expect(thin.map(c => `${c.label} ${c.width}px`))
                .withContext(`— ${columns().map(c => `${c.label} ${c.width}px`).join(', ')}`)
                .toEqual([]);
        });

        /**
         * Three date columns held 150px each to show `dd/MM/yy HH:mm` while the
         * student's name — the only thing on the row a person reads — had 50px.
         */
        it('gives the student more room than any one timestamp', () => {
            const name = named(nameHeading());
            dateHeadings().forEach(heading => {
                const date = named(heading);
                expect(name.width)
                    .withContext(`${name.label} has ${name.width}px against ${date.width}px of ${date.label}`)
                    .toBeGreaterThan(date.width);
            });
        });

        /**
         * The token was the one column here that was never sortable, and it names
         * the same thing the session id above it does.
         */
        it('folds the token into the session id it belongs to', () => {
            const labels = columns().map(c => c.label);
            const token = rtl ? AR['STUDENT_ADMIN.SESSIONS.TOKEN'] : 'TOKEN (PARTIAL)';
            expect(labels.filter(l => l === token || l.startsWith('TOKEN')))
                .withContext(`still a column of its own — ${labels.join(', ')}`)
                .toEqual([]);
        });

        it('still shows what that column said', () => {
            const first = fixture.nativeElement.querySelector('p-table tbody tr td') as HTMLElement;
            expect(first.querySelector('.cell-note'))
                .withContext('the token went with its column rather than moving into the cell')
                .not.toBeNull();
            expect(first.textContent)
                .withContext('the token is not the one shown against this session')
                .toContain(ROWS[0].token.slice(0, 20));
        });

        /**
         * Every cell holds its value inside its own column. A name in 50px does
         * not overflow — it wraps, four or five lines of it — so this says what
         * the previous check does not: nothing is clipped or spilling.
         */
        it('holds every value inside its column', () => {
            const row = fixture.nativeElement.querySelector('p-table tbody tr') as HTMLElement;
            const spilling = (Array.from(row.querySelectorAll('td')) as HTMLElement[])
                .filter(td => td.scrollWidth > td.clientWidth)
                .map(td => `${(td.textContent ?? '').trim().slice(0, 20)} needs ${td.scrollWidth}px in ${td.clientWidth}px`);
            expect(spilling).toEqual([]);
        });
    });
});
