import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { StudentInfoRequestsComponent } from './student-info-requests.component';
import { StudentInfoRequestsModule } from './student-info-requests.module';
import { StudentInfoRequestService } from '../../service/student-info-request.service';
import { StudentdataService } from '../../service/studentdata.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * Live QA H3 (25 Sep 2026): raising a request needs STUDENT_INFO_REQUESTS.EDIT.
 * The server refuses it without; the screen does not offer the button either.
 */
describe('StudentInfoRequests — write permission', () => {

    const render = async (granted: string[]) => {
        await TestBed.configureTestingModule({
            imports: [StudentInfoRequestsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                providePrimeNG({ overlayAppendTo: 'body' }),
                { provide: StudentInfoRequestService, useValue: { list: () => of({ content: [], totalElements: 0, totalPages: 0 }) } },
                { provide: StudentdataService, useValue: { getPagingAll: () => of({ content: [] }) } },
                { provide: LookupService, useValue: { getCountries: () => of([]), getStudentStudyStatuses: () => of([]),
                    getStudentInfoRequestKindsForUi: () => of([]) } },
                { provide: UmsPermissionService, useValue: { hasPermission: (c: string) => granted.includes(c), getAllowedCountryIds: () => [] } },
                { provide: HttpClient, useValue: { get: () => of({}) } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();
        const fixture = TestBed.createComponent(StudentInfoRequestsComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        return fixture;
    };

    const createButton = (root: HTMLElement) =>
        Array.from(root.querySelectorAll('button')).find(b => b.textContent?.includes('STUDENT_INFO_REQUESTS.CREATE_REQUEST'));

    it('hides "Create request" from a view-only account', async () => {
        const fixture = await render(['STUDENT_INFO_REQUESTS.VIEW', 'STUDENTS.VIEW']);
        expect(fixture.componentInstance.canWrite).toBeFalse();
        expect(createButton(fixture.nativeElement)).toBeUndefined();
    });

    it('offers "Create request" with STUDENT_INFO_REQUESTS.EDIT', async () => {
        const fixture = await render(['STUDENT_INFO_REQUESTS.VIEW', 'STUDENT_INFO_REQUESTS.EDIT']);
        expect(fixture.componentInstance.canWrite).toBeTrue();
        expect(createButton(fixture.nativeElement)).toBeDefined();
    });
});
