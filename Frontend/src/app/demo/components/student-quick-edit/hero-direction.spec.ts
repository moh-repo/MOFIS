import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';

import { StudentQuickEditComponent } from './student-quick-edit.component';
import { StudentQuickEditModule } from './student-quick-edit.module';
import { StudentQuickEditService } from '../../service/student-quick-edit.service';
import { StudentdataService } from '../../service/studentdata.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { LookupService } from '../../service/lookup.service';
import { StudentDocumentService } from '../../service/student-document.service';
import { DocumentTypeService } from '../../service/document-type.service';

/**
 * Which side of the title the hero's icon lands on.
 *
 * <p>Under `direction: rtl` a flex row already runs right to left, so its first
 * child sits at the right edge without help. The component's own `.rtl` block
 * then said `flex-direction: row-reverse` on `.page-hero__content`, which flipped
 * it back: in Arabic the icon sat on the left of the title, against the text it
 * introduces, and the meta row and the hero's buttons were turned around with it.
 *
 * <p>The same double mirror as `styles.scss`'s rule for
 * `.flex.justify-content-between`, and as the four `row-reverse` declarations
 * taken out of `report-settings.component.scss` — written locally this time, so
 * the global fix could not reach it.
 *
 * <p>Measured in both directions against the title, not against a number: what
 * "in front of" means here is "on the edge the page is read from", and that
 * survives a change of padding or font.
 */

const LOOKUP_KEYS = [
    'genders', 'statuses', 'degrees', 'majors', 'levels', 'countries', 'governments',
    'banks', 'bankBranches', 'dofs', 'universityFeeStatuses', 'finReceivedStatuses',
    'receivedWhomStatuses', 'agencyTypes', 'ministryTypes', 'studyTypes',
    'universityTypes', 'homeCountries', 'finReceivedWhoms'
];
const emptyMetadata = LOOKUP_KEYS.reduce((acc, k) => ({ ...acc, [k]: [] }), {});

describe('student quick edit hero', () => {

    let fixture: any;
    let host: HTMLElement;

    const build = async (dir: 'ltr' | 'rtl') => {
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [StudentQuickEditModule, RouterTestingModule],
            providers: [
                MessageService,
                {
                    provide: StudentQuickEditService,
                    useValue: { getMetadata: () => of(emptyMetadata), getAll: () => of([]), update: () => of({}) }
                },
                { provide: StudentdataService, useValue: { getYearResults: () => of([]), getProgress: () => of(null) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of(dir === 'rtl' ? 'ar' : 'en'), initialized$: of(true),
                        isReady: () => true, isRTL: () => dir === 'rtl',
                        translate: (k: string) => k.split('.').pop()!, getCurrentLanguage: () => (dir === 'rtl' ? 'ar' : 'en')
                    }
                },
                { provide: UmsPermissionService, useValue: { getAllowedCountryIds: () => [], hasPermission: () => true } },
                { provide: UmsAuthService, useValue: { permissions$: of([]) } },
                { provide: LookupService, useValue: { getQuarterNumbers: () => of([]) } },
                {
                    provide: StudentDocumentService,
                    useValue: {
                        getStudentDocuments: () => of([]), isFormatLikeType: () => true,
                        getDocumentFormatLabelKey: () => 'STUDENTS.FORMAT_OTHER',
                        getDocumentFormatIcon: () => 'pi pi-file'
                    }
                },
                { provide: DocumentTypeService, useValue: { getAllActiveDocumentTypes: () => of([]) } }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentQuickEditComponent);

        // The component binds `dir` and `.rtl` on its own wrapper from
        // `isRTL()`; the frame carries them too, because the shell does and some
        // of the rules that matter key on an ancestor.
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

    const icon = () => fixture.nativeElement.querySelector('.page-hero__icon') as HTMLElement;
    const title = () => fixture.nativeElement.querySelector('.page-hero__title') as HTMLElement;

    (['ltr', 'rtl'] as const).forEach(dir => {

        it(`puts the hero icon on the reading-start side of the title in ${dir}`, async () => {
            await build(dir);

            expect(icon()).withContext('no hero icon').toBeTruthy();
            const i = icon().getBoundingClientRect();
            const t = title().getBoundingClientRect();

            if (dir === 'ltr') {
                expect(i.right)
                    .withContext(`the icon ends at ${Math.round(i.right)} and the title starts at ${Math.round(t.left)}`)
                    .toBeLessThanOrEqual(t.left + 1);
            } else {
                expect(i.left)
                    .withContext(`the icon starts at ${Math.round(i.left)} and the title ends at ${Math.round(t.right)}`)
                    .toBeGreaterThanOrEqual(t.right - 1);
            }
        });
    });

    it('mirrors the hero once, not twice', async () => {
        // The check above pins a side; this one states the cause. `direction`
        // already reverses a row, so a `row-reverse` on top of it is the double
        // flip — and it is the reason the icon was on the wrong side.
        await build('rtl');

        ['.page-hero__content', '.page-hero__meta', '.page-hero__actions', '.meta-item']
            .forEach(selector => {
                const el = fixture.nativeElement.querySelector(selector) as HTMLElement;
                if (!el) { return; }
                expect(getComputedStyle(el).flexDirection)
                    .withContext(`${selector} is reversed a second time on top of direction: rtl`)
                    .not.toBe('row-reverse');
            });
    });
});
