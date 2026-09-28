import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';

import { StudyUniversityFeeArchModule } from './studyuniversityfeearch.module';
import { StudyUniversityFeeArchComponent } from './studyuniversityfeearch.component';
import { StudyUniversityFeeArchService } from '../../service/studyuniversityfeearch.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { provideUmsTestIcons } from '../../../testing/nz-icons.stub';

/**
 * Fee History is read-only (live QA H2, 24 Sep 2026).
 *
 * <p>The screen is the archive of the university-fee records, and it used to
 * offer "Add New", an edit dialog and a delete on the archived rows to anyone
 * holding FINANCE.EDIT — the same permission that edits the live records. The
 * server refuses every write to the archive now, and this holds that the
 * screen offers none, even to a user who holds every permission: a view
 * button per row and nothing else.
 */
describe('StudyUniversityFeeArchComponent is read-only', () => {

    const row = {
        studyUniversityFeeId: 1, studentId: 2, studyUniversityFeeStudentName: 'علي قاسم',
        studyUniversityFeeYear: '2024/2025', studyUniversityFeeAmount: 1200, studyUniversityFeePayStatus: 1,
        studyUniversityFeeCurrency: 1, studyUniversityFeeArchDate: '2026-06-04T02:04:24'
    };

    let fixture: ReturnType<typeof TestBed.createComponent<StudyUniversityFeeArchComponent>>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudyUniversityFeeArchModule, RouterModule.forRoot([])],
            providers: [
                provideNoopAnimations(),
                provideUmsTestIcons(),
                MessageService,
                { provide: HttpClient, useValue: { get: () => of([]), post: () => of({}), put: () => of({}), delete: () => of({}) } },
                {
                    provide: StudyUniversityFeeArchService,
                    useValue: {
                        getPagingAll: () => of({ content: [row], totalElements: 1 }),
                        getAll: () => of([row]),
                        getByStudentId: () => of([row]),
                        getDistinctStudents: () => of([[2, 'علي قاسم']]),
                        searchByStudentName: () => of([row]),
                        getByPayStatus: () => of([row]),
                        getByYear: () => of([row])
                    }
                },
                { provide: LookupService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
                // Every permission, so a write control would render if one were left.
                { provide: UmsAuthService, useValue: new Proxy({ permissions$: of(['FINANCE.EDIT', 'FINANCE.VIEW']) } as any, {
                    get: (target, prop) => prop in target ? target[prop] : () => true
                }) },
                { provide: UmsPermissionService, useValue: new Proxy({} as any, { get: () => () => true }) },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false,
                        getCurrentLanguage: () => 'en',
                        translate: (k: string) => k.split('.').pop()!.replace(/_/g, ' ')
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudyUniversityFeeArchComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        // Pick the student so the detail table renders its rows and, once, its buttons.
        fixture.componentInstance.onStudentSelect({ studentId: 2, studentName: 'علي قاسم' });
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    const host = (): HTMLElement => fixture.nativeElement;

    it('offers no "Add New" button and no add/edit dialog', () => {
        const labels = Array.from(host().querySelectorAll('button')).map(b => (b.getAttribute('label') ?? b.textContent ?? '').trim());
        expect(labels.some(l => /ADD NEW/i.test(l))).withContext(`buttons: ${labels.join(' | ')}`).toBeFalse();
        expect(host().querySelector('.pi-plus')).withContext('an add icon').toBeNull();
        expect(host().textContent).not.toMatch(/ADD NEW UNIVERSITY FEE RECORD|EDIT UNIVERSITY FEE RECORD/);
    });

    it('offers a view button per archived row, and neither an edit nor a delete', () => {
        expect(host().querySelector('.pi-eye')).withContext('the view button').not.toBeNull();
        expect(host().querySelector('.pi-pencil')).withContext('an edit button').toBeNull();
        expect(host().querySelector('.pi-trash')).withContext('a delete button').toBeNull();
    });

    it('has no write handler left on the component', () => {
        const c = fixture.componentInstance as unknown as Record<string, unknown>;
        for (const name of ['openNew', 'editStudyUniversityFeeArch', 'deleteStudyUniversityFeeArch', 'saveStudyUniversityFeeArch']) {
            expect(typeof c[name]).withContext(name).toBe('undefined');
        }
    });

    it('still opens the view dialog for a row', () => {
        fixture.componentInstance.viewStudyUniversityFeeArch(row as any);
        expect(fixture.componentInstance.viewDialog).toBeTrue();
        expect(fixture.componentInstance.newStudyUniversityFeeArch.studyUniversityFeeId).toBe(1);
    });
});
