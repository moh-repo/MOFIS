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
import { StudentQuickEditDto } from '../../api/student-quick-edit';

/**
 * Where the student table's cells put their content (round-5 Phase 7).
 *
 * <p>The component's `.rtl` block reversed `.student-cell__meta`, so in Arabic the id
 * tag sat on the left of the gender tag; `dir` already runs the row from the right.
 * It also said `align-items: flex-end` on every `.cell-stack`, a column: under RTL
 * flex-end is the left edge, so the decision year and the finance year sat against
 * the left of their cells, under right-aligned headers. The amount column is the
 * exception on purpose: its header is `text-right` in both languages, and its figures
 * stay at the right edge with it.
 */

const LOOKUP_KEYS = [
    'genders', 'statuses', 'degrees', 'majors', 'levels', 'countries', 'governments',
    'banks', 'bankBranches', 'dofs', 'universityFeeStatuses', 'finReceivedStatuses',
    'receivedWhomStatuses', 'agencyTypes', 'ministryTypes', 'studyTypes',
    'universityTypes', 'homeCountries', 'finReceivedWhoms'
];
const emptyMetadata = LOOKUP_KEYS.reduce((acc, k) => ({ ...acc, [k]: [] }), {});

const STUDENT: StudentQuickEditDto = {
    studentId: 12, studentName: 'زيد علي عبدالجبار', studentGender: 1, studentDelegationDecisionYear: '2017',
    studentCurrentLevelId: 2, studentAmount: 1500, studentFinRecievedYear: 2024, studentFinRecievedAmount: 1500
};

describe('student quick edit table cells', () => {

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
                    useValue: { getMetadata: () => of(emptyMetadata), getAll: () => of([STUDENT]), update: () => of({}) }
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

        host = document.createElement('div');
        host.setAttribute('dir', dir);
        host.style.width = '1200px';
        if (dir === 'rtl') { host.className = 'rtl'; }
        document.body.appendChild(host);
        host.appendChild(fixture.nativeElement);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => host?.remove());

    const el = (selector: string): HTMLElement => {
        const found = (fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement;
        expect(found).withContext(`nothing matches ${selector}`).toBeTruthy();
        return found;
    };

    const textRect = (node: Element): DOMRect => {
        const range = document.createRange();
        range.selectNodeContents(node);
        return range.getBoundingClientRect();
    };

    (['ltr', 'rtl'] as const).forEach(dir => {

        it(`puts the id tag on the reading-start side of the gender tag in ${dir}`, async () => {
            await build(dir);

            const [id, gender] = Array.from(el('.student-cell__meta').children).map(t => t.getBoundingClientRect());
            if (dir === 'ltr') {
                expect(id.right).withContext(`the id tag ends at ${Math.round(id.right)}, the gender starts at ${Math.round(gender.left)}`)
                    .toBeLessThanOrEqual(gender.left + 1);
            } else {
                expect(id.left).withContext(`the id tag starts at ${Math.round(id.left)}, the gender ends at ${Math.round(gender.right)}`)
                    .toBeGreaterThanOrEqual(gender.right - 1);
                expect(getComputedStyle(el('.student-cell__meta')).flexDirection)
                    .withContext('reversed a second time on top of direction: rtl').not.toBe('row-reverse');
            }
        });

        it(`starts a plain cell's text at the reading-start edge in ${dir}`, async () => {
            await build(dir);

            const cell = el('.cell-stack:not(.align-end)').getBoundingClientRect();
            const text = textRect(el('.cell-stack:not(.align-end) .cell-primary'));
            const fromStart = dir === 'rtl' ? cell.right - text.right : text.left - cell.left;
            expect(Math.abs(fromStart)).withContext(`the text starts ${Math.round(fromStart)}px from its cell's start`)
                .toBeLessThanOrEqual(1);
        });

        it(`keeps the amount at the right edge, under its text-right header, in ${dir}`, async () => {
            await build(dir);

            const cell = el('.cell-stack.align-end').getBoundingClientRect();
            const amount = el('.cell-stack.align-end .cell-primary').getBoundingClientRect();
            expect(Math.abs(cell.right - amount.right)).withContext(`the amount ends ${Math.round(cell.right - amount.right)}px from the right`)
                .toBeLessThanOrEqual(1);
        });
    });
});
