import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { CodeListsComponent } from './code-lists.component';
import { SettingsModule } from '../../settings.module';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { environment } from 'src/environments/environment';

describe('CodeListsComponent', () => {
    let fixture: ComponentFixture<CodeListsComponent>;
    let component: CodeListsComponent;
    let httpMock: HttpTestingController;

    const base = `${environment.apiUrl}/mohe/api/v1/code-lists`;
    const names = [
        { name: 'alert-severity', editable: true, extra: '', table: 'MOHECORE.LU_ALERT_SEVERITY' },
        { name: 'graph-category', editable: true, extra: 'UI_COLOR', table: 'MOHECORE.LU_GRAPH_CATEGORY' },
        { name: 'study-status', editable: false, extra: 'UI_COLOR', table: 'MOHECORE.STUDENT_STUDY_STATUS' }
    ];
    /** Empty unless a test fills it: an absent key is echoed back, as the real service does. */
    const dictionary: Record<string, string> = {};
    const severities = [
        { id: 1, label: 'حرج', value: 'CRITICAL', labelAr: 'حرج', labelEn: 'Critical', sortOrder: 1, uiSeverity: 'danger', icon: 'pi-exclamation-circle', isSystem: true, active: true, extra: null },
        { id: 2, label: 'منخفض', value: 'LOW', labelAr: 'منخفض', labelEn: 'Low', sortOrder: 4, uiSeverity: 'success', icon: null, isSystem: false, active: true, extra: null }
    ];

    /** Renders the page, answering the list-names and first-list requests. */
    function create(withNames: any[] = names, first = 'alert-severity', rows: any[] = severities): void {
        fixture = TestBed.createComponent(CodeListsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        httpMock.expectOne(`${base}/names`).flush(withNames);
        httpMock.expectOne(`${base}/${first}`).flush(rows);
        fixture.detectChanges();
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SettingsModule, RouterModule.forRoot([])],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslationService, useValue: { currentLanguage$: of('en'), initialized$: of(true), isReady: () => true, isRTL: () => false, translate: (k: string) => dictionary[k] ?? k, getCurrentLanguage: () => 'en' } },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, canAccessPage: () => true } },
                { provide: UmsAuthService, useValue: { permissions$: of([]), permissions: [], isAdmin: () => false, isSuperAdmin: () => false, isLoggedIn: () => true } }
            ]
        }).compileComponents();
        httpMock = TestBed.inject(HttpTestingController);
        Object.keys(dictionary).forEach(k => delete dictionary[k]);
    });

    afterEach(() => httpMock.verify());

    it('loads the list names and the first list\'s rows, in sort order, with the severity tag and icon', () => {
        create();
        expect(component.lists.map(l => l.name)).toEqual(['alert-severity', 'graph-category', 'study-status']);
        expect(component.rows.map(r => r.value)).toEqual(['CRITICAL', 'LOW']);
        const critical = fixture.nativeElement.querySelector('[data-code="CRITICAL"]') as HTMLElement;
        const tag = critical.querySelector('p-tag') as HTMLElement;
        expect(tag.classList.contains('p-tag-danger') || !!tag.querySelector('.p-tag-danger')).withContext('the row\'s own severity').toBeTrue();
        expect(critical.querySelector('.pi-exclamation-circle')).not.toBeNull();
        expect(critical.classList).toContain('code-list-row--system');
        expect(critical.querySelector('.code-list-row__lock')).withContext('system rows show the lock').not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-code="LOW"] .code-list-row__lock')).toBeNull();
    });

    it('locks a system row: code disabled in the dialog, deactivate refused', async () => {
        create();
        component.openEdit(component.rows[0]);
        fixture.detectChanges();
        // NgModel applies [disabled] in a resolved promise, not synchronously.
        await fixture.whenStable();
        fixture.detectChanges();
        expect(component.draft.isSystem).toBeTrue();
        // The dialog may render in an overlay outside the fixture's element.
        const codeInput = document.querySelector('#clCode') as HTMLInputElement | null;
        expect(codeInput).withContext('dialog rendered').not.toBeNull();
        expect(codeInput!.disabled).withContext('code locked').toBeTrue();

        const toggle = fixture.nativeElement.querySelector('[data-code="CRITICAL"] p-toggleswitch') as HTMLElement;
        const toggleInput = toggle.querySelector('input') as HTMLInputElement | null;
        expect(toggle.classList.contains('p-disabled') || !!toggleInput?.disabled).withContext('deactivate refused').toBeTrue();
    });

    it('offers the Active switch and no Delete, which only ever switched the row off', () => {
        // DELETE set IS_DELETED, the same flag the switch sets, so a "deleted"
        // code came straight back as an inactive one (QA B14, N5, M4).
        create();
        expect(fixture.nativeElement.querySelector('.pi-trash')).withContext('Delete is back').toBeNull();
        expect(fixture.nativeElement.querySelector('[data-code="LOW"] p-toggleswitch')).not.toBeNull();
    });

    it('shows the extra column only for lists that have one and hides writes on a read-only list', () => {
        create();
        expect(fixture.nativeElement.textContent).not.toContain('UI_COLOR');

        component.selectList('study-status');
        httpMock.expectOne(`${base}/study-status`).flush([{ id: 9, label: 'نشط', value: 'ACTIVE', labelAr: 'نشط', labelEn: 'Active', sortOrder: 1, uiSeverity: null, icon: null, isSystem: false, active: true, extra: '#16A34A' }]);
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).toContain('UI_COLOR');
        expect(fixture.nativeElement.textContent).toContain('#16A34A');
        expect(component.editable).toBeFalse();
        expect(fixture.nativeElement.querySelector('.code-lists-readonly')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('.pi-pencil')).toBeNull();
    });

    it('creates a row, then re-reads the list and the bulk map', () => {
        create();
        component.openAdd();
        expect(component.draft.sortOrder).withContext('next order after the highest').toBe(5);
        component.draft.code = 'MEDIUM';
        component.draft.nameEn = 'Medium';
        component.draft.uiSeverity = 'info';
        component.save();

        const post = httpMock.expectOne(r => r.method === 'POST' && r.url === `${base}/alert-severity`);
        expect(post.request.body).toEqual({ code: 'MEDIUM', nameAr: '', nameEn: 'Medium', sortOrder: 5, uiSeverity: 'info', icon: null, active: true, extra: undefined });
        post.flush({ ...severities[1], id: 3, value: 'MEDIUM' });

        httpMock.expectOne(`${base}/alert-severity`).flush(severities);
        httpMock.expectOne(base).flush({});
        expect(component.dialogVisible).toBeFalse();
    });

    it('surfaces the server\'s 409 message when a system code is renamed or deleted', () => {
        create();
        component.openEdit(component.rows[0]);
        component.draft.nameEn = 'Renamed';
        component.save();
        httpMock.expectOne(r => r.method === 'PUT' && r.url === `${base}/alert-severity/1`)
            .flush({ message: 'CRITICAL is a system code and cannot be renamed' }, { status: 409, statusText: 'Conflict' });
        fixture.detectChanges();

        expect(component.dialogVisible).withContext('the dialog stays open on a refusal').toBeTrue();
        expect(fixture.nativeElement.textContent).toContain('CRITICAL is a system code and cannot be renamed');
        expect(component.serverMessage({ error: { error: 'x' } })).toBe('x');
    });

    it('names each list from the catalogue and groups the picker by the part of the system that uses it', () => {
        dictionary['SETTINGS.CODE_LISTS.LIST_NAMES.ALERT_SEVERITY'] = 'Alert severity';
        dictionary['SETTINGS.CODE_LISTS.LIST_DESCRIPTIONS.ALERT_SEVERITY'] = 'The colour and order of alert badges.';
        dictionary['SETTINGS.CODE_LISTS.GROUPS.ALERTS'] = 'Alerts & notifications';
        dictionary['SETTINGS.CODE_LISTS.GROUPS.REPORTS'] = 'Reports & charts';
        dictionary['SETTINGS.CODE_LISTS.GROUPS.STUDENTS'] = 'Students & study';
        create();

        // Catalogue order, not alphabetical: students before alerts before reports.
        expect(component.groupedLists.map(g => g.label))
            .toEqual(['Students & study', 'Alerts & notifications', 'Reports & charts']);
        expect(component.groupedLists.find(g => g.label === 'Alerts & notifications')!.items.map(l => l.name))
            .toEqual(['alert-severity']);
        expect(component.selected!.title).toBe('Alert severity');
        expect(fixture.nativeElement.textContent).toContain('The colour and order of alert badges.');
        // Its name, not the table or slug behind it (QA B14, I18N-003) ...
        expect(fixture.nativeElement.textContent).not.toContain('MOHECORE.LU_ALERT_SEVERITY');
        // ... which the picker's filter still matches, for whoever knows them.
        expect(component.selected!.search).toContain('alert-severity');
        expect(component.selected!.search).toContain('MOHECORE.LU_ALERT_SEVERITY');
    });

    it('sends a read-only list to the screen that owns its table', () => {
        create();
        component.selectList('study-status');
        httpMock.expectOne(`${base}/study-status`).flush([]);
        fixture.detectChanges();

        const link = fixture.nativeElement.querySelector('.code-lists-readonly a') as HTMLAnchorElement;
        expect(link).withContext('the owning screen is linked').not.toBeNull();
        expect(link.getAttribute('href')).toContain('/settings/lookuptables');
        expect(link.getAttribute('href')).toContain('table=student-study-status');
    });

    it('falls back to the slug and the Other group for a list the catalogue does not know', () => {
        dictionary['SETTINGS.CODE_LISTS.GROUPS.OTHER'] = 'Other lists';
        create([{ name: 'brand-new-list', editable: true, extra: '', table: 'MOHECORE.LU_BRAND_NEW' }], 'brand-new-list', []);

        expect(component.groupedLists.map(g => g.label)).toEqual(['Other lists']);
        expect(component.selected!.title).withContext('the slug, never a raw translation key').toBe('brand-new-list');
        expect(component.selected!.description).toBe('');
        expect(fixture.nativeElement.textContent).not.toContain('SETTINGS.CODE_LISTS.LIST_NAMES');
    });

    it('shows the extra column under a readable name, keeping the raw column on hover', () => {
        dictionary['SETTINGS.CODE_LISTS.EXTRA.UI_COLOR'] = 'Colour';
        create();
        component.selectList('graph-category');
        httpMock.expectOne(`${base}/graph-category`).flush([]);
        fixture.detectChanges();

        expect(component.extraColumnLabel).toBe('Colour');
        const header = Array.from(fixture.nativeElement.querySelectorAll('th'))
            .find((th: any) => th.textContent.trim() === 'Colour') as HTMLElement;
        expect(header).withContext('the header reads Colour, not UI_COLOR').not.toBeNull();
        expect(fixture.nativeElement.querySelector('thead')!.textContent).not.toContain('UI_COLOR');
    });
});
