import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { SysParametersComponent } from './sys-parameters.component';
import { SettingsModule } from '../../settings.module';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { environment } from 'src/environments/environment';

describe('SysParametersComponent', () => {
    let fixture: ComponentFixture<SysParametersComponent>;
    let httpMock: HttpTestingController;
    let granted: Set<string>;

    const url = `${environment.apiUrl}/mohe/api/v1/sys-parameters`;
    const rows = [
        { key: 'DASHBOARD.MONTHS_WINDOW', value: '7', valueType: 'INT', nameAr: 'نافذة الأشهر', nameEn: 'Months window', description: 'd', updatedBy: 'admin', updatedAt: '2026-08-30T10:00:00' },
        { key: 'STUDENTS.MATCH_SIMILARITY_FLOOR', value: '0.5', valueType: 'DECIMAL', nameAr: null, nameEn: 'Similarity floor', description: null, updatedBy: null, updatedAt: null },
        { key: 'X.FLAG', value: 'true', valueType: 'BOOL', nameAr: 'علم', nameEn: null, description: null, updatedBy: null, updatedAt: null }
    ];

    function create(): void {
        fixture = TestBed.createComponent(SysParametersComponent);
        fixture.detectChanges();
        httpMock.expectOne(url).flush(rows);
        fixture.detectChanges();
    }

    beforeEach(async () => {
        granted = new Set(['SETTINGS.VIEW', 'SETTINGS.EDIT']);
        await TestBed.configureTestingModule({
            imports: [SettingsModule, RouterModule.forRoot([])],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslationService, useValue: { currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en' } },
                { provide: UmsPermissionService, useValue: { hasPermission: (c: string) => granted.has(c), canAccessPage: () => true } },
                { provide: UmsAuthService, useValue: { permissions$: of([]), permissions: [], isAdmin: () => false, isSuperAdmin: () => false, isLoggedIn: () => true } }
            ]
        }).compileComponents();
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('lists the parameters with names in the current language and a yes/no for booleans', () => {
        create();
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('DASHBOARD.MONTHS_WINDOW');
        expect(text).toContain('Months window');
        expect(text).withContext('Arabic-only name falls back').toContain('علم');
        expect(fixture.nativeElement.querySelector('[data-key="X.FLAG"] .sys-parameter-value').textContent.trim()).toBe('COMMON.YES');
    });

    it('edits a value inline with the control its type needs and PUTs the text back', () => {
        create();
        const component = fixture.componentInstance;
        const int = component.rows[0];
        component.edit(int);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-key="DASHBOARD.MONTHS_WINDOW"] p-inputnumber')).not.toBeNull();

        int.draft = 12;
        component.save(int);
        const req = httpMock.expectOne(`${url}/DASHBOARD.MONTHS_WINDOW`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual({ value: '12' });
        req.flush({ ...rows[0], value: '12', updatedBy: 'me' });
        fixture.detectChanges();

        expect(int.editing).toBeFalse();
        expect(int.value).toBe('12');
        expect(fixture.nativeElement.querySelector('[data-key="DASHBOARD.MONTHS_WINDOW"] .sys-parameter-value').textContent.trim()).toBe('12');
    });

    it('refuses a value that is not of the parameter\'s type without a request', () => {
        create();
        const component = fixture.componentInstance;
        const int = component.rows[0];
        component.edit(int);
        int.draft = 1.5;
        component.save(int);
        httpMock.expectNone(`${url}/DASHBOARD.MONTHS_WINDOW`);
        expect(int.editing).toBeTrue();

        expect(SysParametersComponent.serialise('DECIMAL', 1.5)).toBe('1.5');
        expect(SysParametersComponent.serialise('BOOL', false)).toBe('false');
        expect(SysParametersComponent.serialise('TEXT', null)).toBe('');
    });

    it('hides the edit controls from a reader without SETTINGS.EDIT', () => {
        granted.delete('SETTINGS.EDIT');
        create();
        expect(fixture.nativeElement.querySelector('.pi-pencil')).toBeNull();
    });
});
