import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, Confirmation } from 'primeng/api';
import { StudentGroupsComponent } from './student-groups.component';
import { StudentAdminModule } from '../student-admin.module';
import { TranslationService } from '../../../service/translation.service';

describe('StudentGroupsComponent', () => {
    let fixture: ComponentFixture<StudentGroupsComponent>;
    let component: StudentGroupsComponent;
    let httpMock: HttpTestingController;
    let confirm: jasmine.Spy;

    const group = { groupId: 5, groupName: 'Cairo students', isSystem: false, isActive: true };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentAdminModule],
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        translate: (k: string) => k, isRTL: () => false, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentGroupsComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        // The confirmation service is component-scoped, so it is read from the component's injector.
        confirm = spyOn(fixture.debugElement.injector.get(ConfirmationService), 'confirm');
        fixture.detectChanges();
        httpMock.expectOne(r => r.url.endsWith('/student-admin/groups')).flush([group]);
        httpMock.expectOne(r => r.url.endsWith('/student-admin/roles')).flush([]);
    });

    afterEach(() => httpMock.verify());

    // Deleting was one click; a group carries role assignments, so it asks first.
    it('deletes the group once the confirmation is accepted', () => {
        confirm.and.callFake((c: Confirmation) => { c.accept!(); return {} as ConfirmationService; });

        component.deleteGroup(group);

        const req = httpMock.expectOne(r => r.method === 'DELETE' && r.url.endsWith('/student-admin/groups/5'));
        req.flush({});
        // Reloads the list afterwards.
        httpMock.expectOne(r => r.method === 'GET' && r.url.endsWith('/student-admin/groups')).flush([]);
        expect(confirm.calls.mostRecent().args[0].message).toContain('STUDENT_ADMIN.GROUPS.MESSAGES.CONFIRM_DELETE');
    });

    it('does not call the server when the confirmation is rejected', () => {
        confirm.and.callFake((c: Confirmation) => { c.reject?.(); return {} as ConfirmationService; });

        component.deleteGroup(group);

        httpMock.expectNone(r => r.method === 'DELETE');
        expect(confirm).toHaveBeenCalled();
    });

    it('refuses to delete a system group without asking', () => {
        component.deleteGroup({ ...group, isSystem: true });
        expect(confirm).not.toHaveBeenCalled();
        httpMock.expectNone(r => r.method === 'DELETE');
    });
});
