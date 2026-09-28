import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService, ConfirmationService } from 'primeng/api';
import { NzModalService } from 'ng-zorro-antd/modal';
import { of } from 'rxjs';

import { StudyUniversityFeeModule } from './studyuniversityfee/studyuniversityfee.module';
import { StudyUniversityFeeComponent } from './studyuniversityfee/studyuniversityfee.component';
import { StudyUniversityFeeArchModule } from './studyuniversityfeearch/studyuniversityfeearch.module';
import { StudyUniversityFeeArchComponent } from './studyuniversityfeearch/studyuniversityfeearch.component';
import { StudentFinanceWafarModule } from './student-finance-wafar/student-finance-wafar.module';
import { StudentFinanceWafarComponent } from './student-finance-wafar/student-finance-wafar.component';
import { WafarModule } from './wafar/wafar.module';
import { WafarComponent } from './wafar/wafar.component';
import { WafarReconciliationComponent } from './wafar-reconciliation/wafar-reconciliation.component';

import { TranslationService } from '../service/translation.service';
import { CodeListService } from '../service/code-list.service';
import { ToastService } from '../service/toast.service';
import { codeListStub } from '../../testing/code-list.stub';
import { provideUmsTestIcons } from '../../testing/nz-icons.stub';

/**
 * The headings on the five finance screens, and which side they sit on.
 *
 * <p>All five laid their header row out with `justify-content-between`. Under
 * `direction: rtl` a flex row already runs right to left, and `styles.scss:163`
 * reverses every `.flex.justify-content-between` again — the double flip that
 * put the title on the left and the buttons on the right in Arabic, in five more
 * places. They separate the two ends with `flex-1` on the heading block now,
 * which lays out identically in English and matches nothing in styles.scss.
 *
 * <p>Three of the five had no icon at all. Study university fee and its history
 * each opened with a `p-toolbar` carrying the page title above a card carrying a
 * second heading of its own — the history page's toolbar held a bare text node —
 * so both announced themselves twice. One heading each now, with the icon in
 * front. Wafar reconciliation had one heading and no icon.
 *
 * <p>Student finance wafar had its icon inside the `h1`, and its own `.rtl`
 * block turned that `h1` around a second time, so the wallet sat on the left of
 * the title in Arabic — the same fault as the quick-edit hero, in the same shape.
 *
 * <p>Measured against the card rather than against a number: what "the reading
 * edge" means survives a change of padding or font, and a pixel count does not.
 */

/**
 * An empty list that is also an empty page. These pages ask one service for a
 * page and another for a plain list, and hand the first straight to `.content`
 * and the second straight to a table's `[value]`; a value that is both keeps
 * every call on the happy path. See page-headings.spec.ts.
 */
const page = () => of(Object.assign([] as any[], { content: [], totalElements: 0, totalPages: 0 }));

interface Case {
    name: string;
    module: any;
    component: any;
    /** The block that must lead the header row. */
    icon: string;
    heading: string;
}

const CASES: Case[] = [
    {
        name: 'study university fee', module: StudyUniversityFeeModule, component: StudyUniversityFeeComponent,
        icon: '.pi-money-bill', heading: 'h2'
    },
    {
        name: 'study university fee history', module: StudyUniversityFeeArchModule, component: StudyUniversityFeeArchComponent,
        icon: '.pi-folder-open', heading: 'h2'
    },
    {
        name: 'student finance wafar', module: StudentFinanceWafarModule, component: StudentFinanceWafarComponent,
        icon: '.hero-title .pi-wallet', heading: '.hero-title'
    },
    {
        name: 'wafar', module: WafarModule, component: WafarComponent,
        icon: '.pi-credit-card', heading: 'h2'
    },
    {
        name: 'wafar reconciliation', module: WafarReconciliationComponent, component: WafarReconciliationComponent,
        icon: '.pi-calculator', heading: 'h2'
    }
];

CASES.forEach(({ name, module, component, icon, heading }) => {

    describe(`${name} heading`, () => {

        let fixture: any;
        let host: HTMLElement;

        const build = async (dir: 'ltr' | 'rtl') => {
            TestBed.resetTestingModule();
            await TestBed.configureTestingModule({
                imports: [module, RouterModule.forRoot([])],
                providers: [
                    provideNoopAnimations(),
                    provideUmsTestIcons(),
                    MessageService,
                    ConfirmationService,
                    { provide: NzModalService, useValue: { confirm: () => undefined, create: () => undefined } },
                    { provide: ToastService, useValue: new Proxy({} as any, { get: () => () => undefined }) },
                    { provide: HttpClient, useValue: { get: () => page(), post: () => of({}), put: () => of({}), delete: () => of({}) } },
                    { provide: CodeListService, useValue: codeListStub() },
                    {
                        provide: TranslationService,
                        useValue: {
                            currentLanguage$: of(dir === 'rtl' ? 'ar' : 'en'), initialized$: of(true),
                            isReady: () => true, isRTL: () => dir === 'rtl',
                            getCurrentLanguage: () => (dir === 'rtl' ? 'ar' : 'en'),
                            translate: (k: string) => k.split('.').pop()!.replace(/_/g, ' ')
                        }
                    }
                ]
            }).compileComponents();

            fixture = TestBed.createComponent(component);

            // The class and the attribute both: the shell carries the pair, and
            // rtl.css keys off `.rtl` while styles.scss keys off `[dir="rtl"]`.
            host = document.createElement('div');
            host.setAttribute('dir', dir);
            host.style.width = '1033px';
            if (dir === 'rtl') { host.className = 'rtl'; }
            document.body.appendChild(host);
            host.appendChild(fixture.nativeElement);

            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
        };

        afterEach(() => host?.remove());

        const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement;

        (['ltr', 'rtl'] as const).forEach(dir => {

            it(`leads with its icon in ${dir}`, async () => {
                await build(dir);

                const i = el(icon);
                const h = el(heading);
                expect(i).withContext(`${name} has no icon in front of its heading`).toBeTruthy();
                expect(h).withContext(`${name} has no heading`).toBeTruthy();

                const ib = i.getBoundingClientRect();
                const hb = h.getBoundingClientRect();

                // On the reading-start side of the words, and on their line.
                if (dir === 'ltr') {
                    expect(ib.left)
                        .withContext(`the icon starts at ${Math.round(ib.left)} and the heading at ${Math.round(hb.left)}`)
                        .toBeLessThanOrEqual(hb.left + 1);
                } else {
                    expect(ib.right)
                        .withContext(`the icon ends at ${Math.round(ib.right)} and the heading at ${Math.round(hb.right)}`)
                        .toBeGreaterThanOrEqual(hb.right - 1);
                }
                expect(ib.bottom).withContext('the icon sits above the heading').toBeGreaterThan(hb.top);
                expect(ib.top).withContext('the icon sits below the heading').toBeLessThan(hb.bottom);
            });
        });

        it('keeps the heading on the edge it reads from', async () => {
            // The check that actually failed before: with the row mirrored twice
            // the title crossed to the far end of its own card in Arabic. It
            // measures the heading against its container, so no padding or font
            // change can satisfy it from the wrong side.
            for (const dir of ['ltr', 'rtl'] as const) {
                await build(dir);

                const h = el(heading);
                const card = h.closest('.card, .ant-card-body, .page-hero') as HTMLElement;
                expect(card).withContext(`${name}: no container to measure the heading against`).toBeTruthy();

                const box = card.getBoundingClientRect();
                const text = h.getBoundingClientRect();
                const fromStart = dir === 'ltr' ? text.left - box.left : box.right - text.right;
                const fromEnd = dir === 'ltr' ? box.right - text.right : text.left - box.left;

                expect(fromStart)
                    .withContext(`${dir}: the heading is ${Math.round(fromStart)}px from the start edge `
                        + `and ${Math.round(fromEnd)}px from the end`)
                    .toBeLessThan(fromEnd);

                host.remove();
            }
        });
    });
});
