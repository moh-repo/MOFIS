import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { NzModalService } from 'ng-zorro-antd/modal';
import { ReportManagementComponent } from './report-management.component';
import { ToastService } from '../../../service/toast.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { ReportCatalogService } from '../../../service/report-catalog.service';
import { UmsReport } from '../../../api/ums';
import { environment } from 'src/environments/environment';

/** Report management edits the catalogue columns the hubs read: parent, icon, colour, route params, page code, permissions, order. */
describe('ReportManagementComponent', () => {
    let fixture: ComponentFixture<ReportManagementComponent>;
    let component: ReportManagementComponent;
    let httpMock: HttpTestingController;
    let catalog: { getCatalog: jasmine.Spy; reload: jasmine.Spy };

    const base = `${environment.apiUrl}/mohe/api/v1/ums/reports`;
    const row = (patch: Partial<UmsReport>): UmsReport => ({
        id: 1, reportCode: 'REP_X', reportName: 'X', reportNameAr: 'س', description: '', descriptionAr: '', category: 'STUDENT', route: '/reports/student/x',
        isActive: true, parentCode: 'HUB_STUDENT', icon: 'pi-users', color: 'cyan', queryParams: 'doc=id-card', pageCode: 'REPORT_ACADEMIC',
        permissionCodes: null, sortOrder: 3, createdAt: '', updatedAt: '', ...patch
    });

    beforeEach(async () => {
        catalog = {
            getCatalog: jasmine.createSpy('getCatalog').and.returnValue(of([row({ id: 9, reportCode: 'HUB_STUDENT', parentCode: null, reportName: 'Student hub' }), row({})])),
            reload: jasmine.createSpy('reload')
        };
        await TestBed.configureTestingModule({
            imports: [ReportManagementComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideNoopAnimations(),
                { provide: ReportCatalogService, useValue: catalog },
                { provide: ToastService, useValue: { success: () => {}, error: () => {} } },
                { provide: NzModalService, useValue: { confirm: () => {} } },
                { provide: TranslationService, useValue: { currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en' } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, hasAnyPermission: () => true } },
                { provide: UmsAuthService, useValue: { permissions$: of([]), permissions: [], isAdmin: () => true, isSuperAdmin: () => true, isLoggedIn: () => true } }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(ReportManagementComponent);
        component = fixture.componentInstance;
        httpMock = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
        httpMock.expectOne(r => r.url === `${base}/`).flush({ content: [row({})], totalElements: 1 });
        fixture.detectChanges();
    });

    afterEach(() => {
        // ng-zorro fetches its icon SVGs over HttpClient; they are not the API under test.
        httpMock.match(r => r.url.endsWith('.svg'));
        httpMock.verify();
    });

    it('shows the catalogue columns in the table and offers the hubs as parents', () => {
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('HUB_STUDENT');
        // The icon is drawn in front of the report's name now, rather than
        // printed beside itself in a column of its own: eleven columns left
        // Report Name 81px to be read in. Its class is still what carries it.
        expect(fixture.nativeElement.querySelector('tbody i.pi-users'))
            .withContext('the report icon is not rendered on the row').not.toBeNull();
        expect(text).toContain('?doc=id-card');
        expect(text).toContain('REPORT_ACADEMIC');
        expect(component.hubOptions.map(h => h.reportCode)).toEqual(['HUB_STUDENT']);
    });

    it('loads every catalogue field into the form and sends them back, blanks as null', () => {
        component.showEditModal(row({}));
        expect(component.reportForm.getRawValue()).toEqual(jasmine.objectContaining({
            parentCode: 'HUB_STUDENT', icon: 'pi-users', color: 'cyan', queryParams: 'doc=id-card', pageCode: 'REPORT_ACADEMIC', permissionCodes: '', sortOrder: 3
        }));

        component.reportForm.patchValue({ permissionCodes: 'REPORT_FINANCE, FINANCE', queryParams: '', sortOrder: 5 });
        component.handleModalOk();
        const put = httpMock.expectOne(r => r.method === 'PUT' && r.url === `${base}/1`);
        expect(put.request.body).toEqual(jasmine.objectContaining({
            parentCode: 'HUB_STUDENT', icon: 'pi-users', queryParams: null, permissionCodes: 'REPORT_FINANCE, FINANCE', sortOrder: 5
        }));
        put.flush(row({}));
        httpMock.expectOne(r => r.url === `${base}/`).flush({ content: [], totalElements: 0 });

        expect(catalog.reload).withContext('the hubs re-read the catalogue after a save').toHaveBeenCalled();
    });
});
