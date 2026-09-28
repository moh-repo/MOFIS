import { TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { MyRequestsComponent } from './my-requests.component';
import { StudentPortalModule } from '../student-portal.module';
import { PortalLookups, StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { ToastService } from '../../demo/service/toast.service';
import { CodeListService } from '../../demo/service/code-list.service';
import { codeListStub, codeRow } from '../../testing/code-list.stub';

/**
 * The change-request dialog opened before the reference lists had loaded.
 *
 * <p>The lists load in the background when the page opens, and the dialog built
 * its rows from whatever had arrived. A student who opened it straight away got
 * Gender and Country as a raw id ("1") over a free-text box, and those rows
 * stayed that way until the dialog was opened again. The dialog now waits for
 * the lists, and still opens — with text boxes, and a warning — when they fail.
 */
describe('MyRequestsComponent: the change-request dialog', () => {
    const lookups = {
        genders: [{ id: 1, name: 'ذكر', nameEn: 'Male' }, { id: 2, name: 'أنثى', nameEn: 'Female' }]
    } as unknown as PortalLookups;

    let warning: jasmine.Spy;

    function create(getLookups: () => Observable<PortalLookups>): MyRequestsComponent {
        warning = jasmine.createSpy('warning');
        TestBed.configureTestingModule({
            imports: [StudentPortalModule, RouterModule.forRoot([])],
            providers: [
                {
                    provide: StudentPortalApiService,
                    useValue: {
                        getMyChangeRequests: () => of([]),
                        getLookups,
                        getAllowedChangeFields: () => of({ fields: [], types: { studentBirthdate: 'DATE' } }),
                        getMyData: () => of({
                            student: { studentName: 'Ahmed', studentGender: 1, studentBirthdate: '1999-04-12' },
                            finance: null
                        })
                    }
                },
                { provide: TranslationService, useValue: { currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en' } },
                { provide: ToastService, useValue: { error: () => {}, success: () => {}, warning } },
                {
                    provide: CodeListService,
                    useValue: codeListStub({
                        'change-request-field': [
                            codeRow('studentName', { labelEn: 'Student Name' }),
                            codeRow('studentGender', { labelEn: 'Gender', extra: 'genders' }),
                            codeRow('studentBirthdate', { labelEn: 'Birth Date' })
                        ]
                    })
                }
            ]
        });
        // No change detection: this is about what the dialog is given, not how it looks.
        const component = TestBed.createComponent(MyRequestsComponent).componentInstance;
        component.ngOnInit();
        return component;
    }

    const field = (c: MyRequestsComponent, name: string) => c.editableFields.find(f => f.fieldName === name)!;

    it('waits for the lookups before it builds its fields', () => {
        const pending = new Subject<PortalLookups>();
        const component = create(() => pending);

        component.openSubmitDialog();
        expect(component.studentDataLoading).withContext('still loading').toBeTrue();
        expect(component.editableFields).withContext('no rows built from half the data').toEqual([]);

        pending.next(lookups);
        pending.complete();

        expect(component.studentDataLoading).toBeFalse();
        expect(field(component, 'studentGender').type).toBe('select');
        expect(field(component, 'studentGender').currentValue).withContext('named, not the raw id').toBe('Male');
        expect(field(component, 'studentBirthdate').type).toBe('date');
        expect(field(component, 'studentName').type).toBe('text');
    });

    it('builds at once when the lookups are already there, without asking for them again', () => {
        const getLookups = jasmine.createSpy('getLookups').and.returnValue(of(lookups));
        const component = create(getLookups);

        component.openSubmitDialog();
        expect(component.studentDataLoading).toBeFalse();
        expect(field(component, 'studentGender').currentValue).toBe('Male');

        component.openSubmitDialog();
        expect(getLookups).withContext('fetched once for the page, shared by every opening').toHaveBeenCalledTimes(1);
    });

    it('still opens, with text boxes, when the lookups fail — and says so', () => {
        const component = create(() => throwError(() => new Error('down')));

        component.openSubmitDialog();

        expect(component.studentDataLoading).toBeFalse();
        expect(field(component, 'studentGender').type).toBe('text');
        expect(field(component, 'studentGender').currentValue).toBe('1');
        expect(warning).toHaveBeenCalledOnceWith('STUDENT_PORTAL.LOAD_LOOKUPS_ERROR');
    });
});
