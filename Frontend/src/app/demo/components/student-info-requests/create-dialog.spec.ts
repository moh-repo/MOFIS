import { ComponentFixture, TestBed } from '@angular/core/testing';
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
import { CodeListService } from '../../service/code-list.service';
import { TranslationService } from '../../service/translation.service';
import { UmsPermissionService } from '../../service/ums-permission.service';

/**
 * The create-request dialog (25 Sep 2026): the two steps laid out with the
 * .dv-* vocabulary every other record's Add dialog uses, with a hero that
 * previews what is about to be sent and the actions in the footer.
 *
 * <p>The dialog lands on <body> (providePrimeNG's overlayAppendTo, as in
 * app.module.ts), so it is read from the document, not the fixture.
 */
describe('StudentInfoRequests — the create dialog', () => {

    let fixture: ComponentFixture<StudentInfoRequestsComponent>;
    let component: StudentInfoRequestsComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentInfoRequestsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                providePrimeNG({ overlayAppendTo: 'body' }),
                {
                    provide: StudentInfoRequestService,
                    useValue: { list: () => of({ content: [], totalElements: 0, totalPages: 0 }) }
                },
                { provide: StudentdataService, useValue: { getPagingAll: () => of({ content: [] }) } },
                { provide: LookupService, useValue: { getCountries: () => of([{ id: 1, name: 'Yemen' }]),
                    getStudyCountriesIn: () => of([{ id: 1, name: 'Egypt' }, { id: 1004, name: 'Jordan' }, { id: 1005, name: 'Saudi Arabia' }]),
                    getStudentStudyStatuses: () => of([{ id: 5, name: 'Enrolled' }]), getStudentInfoRequestKindsForUi: () => of([{ label: 'Other', value: 'OTHER' }]) } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, getAllowedCountryIds: () => [],
                    hasCountryAccess: (id: number) => id !== 1005 } },
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

        TestBed.inject(CodeListService).reload().subscribe();

        fixture = TestBed.createComponent(StudentInfoRequestsComponent);
        component = fixture.componentInstance;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();

        component.openCreate();
        await settle();
    });

    afterEach(() => {
        component.showCreateDialog = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const settle = async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    const dialog = () => document.querySelector('.p-dialog.dv-dialog') as HTMLElement;
    const footerButtons = () =>
        Array.from(dialog().querySelectorAll('.dv-footer__actions button')).map(b => b.textContent?.trim() ?? '');

    it('opens on step one in the shared vocabulary, outside the component host', () => {
        expect(dialog()).withContext('dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog())).toBeFalse();
        expect(dialog().querySelector('.dv-hero.dv-hero--new')).withContext('new-record hero').toBeTruthy();
        expect(dialog().querySelector('.p-steps')).withContext('the two steps').toBeTruthy();
        expect(dialog().querySelector('#irTitle')).withContext('title field').toBeTruthy();
        expect(dialog().querySelector('.create-form')).withContext('old layout gone').toBeNull();
        expect(footerButtons()).toEqual(['STUDENT_INFO_REQUESTS.CANCEL', 'STUDENT_INFO_REQUESTS.NEXT']);
        expect(dialog().querySelector('.dv-footer__hint')?.textContent).toContain('STUDENT_INFO_REQUESTS.STEP_OF');
    });

    it('the hero previews the title as it is typed', async () => {
        expect(dialog().querySelector('.dv-hero__title')?.className).toContain('dv-hero__title--placeholder');
        component.createTitle = 'Renew the status certificate';
        await settle();
        expect(dialog().querySelector('.dv-hero__title')?.textContent).toContain('Renew the status certificate');
        expect(dialog().querySelector('.dv-hero__title')?.className).not.toContain('dv-hero__title--placeholder');
    });

    it('step two chooses the recipients and offers back and create', async () => {
        component.createTitle = 'Renew the status certificate';
        component.nextCreateStep();
        await settle();
        expect(component.createStep).toBe(1);
        expect(dialog().querySelector('#irTitle')).withContext('step one gone').toBeNull();
        expect(dialog().querySelectorAll('p-radiobutton').length).withContext('one / selected / all').toBe(3);
        expect(dialog().querySelector('#irStudent')).withContext('one student is the default').toBeTruthy();
        expect(footerButtons()).toEqual([
            'STUDENT_INFO_REQUESTS.CANCEL',
            'STUDENT_INFO_REQUESTS.BACK',
            'STUDENT_INFO_REQUESTS.CREATE'
        ]);

        component.targetMode = 'all';
        await settle();
        expect(dialog().querySelector('.dv-notice--warn')).withContext('everyone: a warning').toBeTruthy();
        expect(dialog().querySelector('.dv-hero__meta')?.textContent).toContain('STUDENT_INFO_REQUESTS.TARGET_ALL');
    });

    // The server narrows "all students" by the study country and STUDENT_STUDY_STATUS_ID
    // (P5-4: the country was the nationality, from the home-country list).
    it('"all students" narrows by country and study status from the lookup tables, and the hero names the chosen country', async () => {
        component.createTitle = 'Renew the status certificate';
        component.nextCreateStep();
        component.targetMode = 'all';
        await settle();

        const selects = dialog().querySelectorAll('.dv-section p-select');
        expect(selects.length).withContext('country and study status dropdowns').toBe(2);
        expect(dialog().querySelector('input[type="number"]')).withContext('no id boxes').toBeNull();
        expect(component.countryOptions.map(c => c.name))
            .withContext('study countries, only those the user may reach')
            .toEqual(['Egypt', 'Jordan']);
        expect(component.studyStatusOptions.map(s => s.name)).toEqual(['Enrolled']);

        component.targetCountryId = 1; // Egypt, as a study country
        component.targetStatusId = 5;
        await settle();
        expect(dialog().querySelector('.dv-hero__meta')?.textContent).toContain('Egypt');
        expect(dialog().querySelector('.dv-hero__meta')?.textContent).toContain('Enrolled');
    });
});
