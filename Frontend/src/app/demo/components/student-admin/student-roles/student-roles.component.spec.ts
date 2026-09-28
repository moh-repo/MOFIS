import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, Confirmation } from 'primeng/api';
import { StudentRolesComponent } from './student-roles.component';
import { StudentAdminModule } from '../student-admin.module';
import { TranslationService } from '../../../service/translation.service';

describe('StudentRolesComponent', () => {
    let fixture: ComponentFixture<StudentRolesComponent>;
    let component: StudentRolesComponent;
    let httpMock: HttpTestingController;
    let confirm: jasmine.Spy;

    const role = { roleId: 3, roleName: 'Reader', isSystem: false, isActive: true };

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

        fixture = TestBed.createComponent(StudentRolesComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        confirm = spyOn(fixture.debugElement.injector.get(ConfirmationService), 'confirm');
        fixture.detectChanges();
        httpMock.expectOne(r => r.url.endsWith('/student-admin/roles')).flush([role]);
    });

    afterEach(() => httpMock.verify());

    it('deletes the role once the confirmation is accepted', () => {
        confirm.and.callFake((c: Confirmation) => { c.accept!(); return {} as ConfirmationService; });

        component.deleteRole(role);

        httpMock.expectOne(r => r.method === 'DELETE' && r.url.endsWith('/student-admin/roles/3')).flush({});
        httpMock.expectOne(r => r.method === 'GET' && r.url.endsWith('/student-admin/roles')).flush([]);
    });

    it('finds a permission by its Arabic name too (W4-09, review M11)', () => {
        component.allPermissions = [
            { permissionId: 1, permissionCode: 'PORTAL_DOCS.VIEW', permissionName: 'View documents', permissionNameAr: 'عرض المستندات' },
            { permissionId: 2, permissionCode: 'PORTAL_FEES.VIEW', permissionName: 'View fees', permissionNameAr: 'عرض الرسوم' }
        ];
        component.permissionFilter = 'المستندات';

        expect(component.filteredPermissions.map(p => p.permissionId)).toEqual([1]);
        expect(component.permissionLabel(component.allPermissions[0])).toBe('View documents');
    });

    it('does not call the server when the confirmation is rejected', () => {
        confirm.and.callFake((c: Confirmation) => { c.reject?.(); return {} as ConfirmationService; });

        component.deleteRole(role);

        httpMock.expectNone(r => r.method === 'DELETE');
        expect(confirm).toHaveBeenCalled();
    });
});
