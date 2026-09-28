import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { PortalLayoutComponent } from './portal-layout.component';
import { StudentPortalModule } from '../student-portal.module';
import { StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import {
    BarChartOutline, BellOutline, CalendarOutline, CarOutline, CloseOutline, DashboardOutline,
    DollarOutline, EditOutline, FileTextOutline, FormOutline, IdcardOutline, LeftOutline,
    LogoutOutline, MenuOutline, MessageOutline, ReadOutline, RightOutline, SafetyOutline
} from '@ant-design/icons-angular/icons';

/**
 * The portal shell, now a side menu.
 *
 * <p>Eleven destinations never fitted the horizontal bar this replaced — they
 * scrolled sideways inside the header, so whatever came after Extensions was
 * off-screen — and the list was written out three times, in the bar, the mobile
 * drawer and the dashboard's card grid. The copies had already drifted apart.
 * These tests hold the two properties that keep it fixed: one array feeds every
 * rendering of the menu, and the header says which page you are on.
 */
describe('PortalLayoutComponent', () => {

    let fixture: ComponentFixture<PortalLayoutComponent>;
    let component: PortalLayoutComponent;
    let router: Router;

    /** Every nzType the shell template names; an unregistered one floods the console. */
    const ICONS = [
        ReadOutline, DashboardOutline, IdcardOutline, FileTextOutline, BarChartOutline,
        DollarOutline, FormOutline, MessageOutline, EditOutline, CarOutline, CalendarOutline,
        SafetyOutline, MenuOutline, CloseOutline, LeftOutline, RightOutline, BellOutline,
        LogoutOutline
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentPortalModule, RouterModule.forRoot([])],
            providers: [
                { provide: NZ_ICONS, useValue: ICONS },
                {
                    provide: StudentPortalApiService,
                    useValue: {
                        getProfile: () => of({ studentId: 4321, studentName: 'Layla Al Mansouri' }),
                        logout: () => of(void 0),
                        clearToken: () => {}
                    }
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en',
                        getAvailableLanguages: () => [], switchLanguage: () => {}
                    }
                }
            ]
        }).compileComponents();

        router = TestBed.inject(Router);
        fixture = TestBed.createComponent(PortalLayoutComponent);
        component = fixture.componentInstance;
        component.isMobile = false;
        fixture.detectChanges();
    });

    function textOf(selector: string): string[] {
        return Array.from(fixture.nativeElement.querySelectorAll(selector))
            .map(el => ((el as HTMLElement).textContent || '').trim());
    }

    it('shows every destination, under a heading for each group', () => {
        const items = fixture.nativeElement.querySelectorAll('.portal-menu li[nz-menu-item]');
        const expected = component.navGroups.reduce((n, g) => n + g.items.length, 0);

        expect(expected).toBe(11);
        expect(items.length).toBe(expected);
        expect(textOf('.portal-nav__heading')).toEqual([
            'STUDENT_PORTAL.NAV_GROUP.OVERVIEW',
            'STUDENT_PORTAL.NAV_GROUP.MY_FILE',
            'STUDENT_PORTAL.NAV_GROUP.REQUESTS',
            'STUDENT_PORTAL.NAV_GROUP.ACCOUNT'
        ]);
    });

    /**
     * Drives the mapping directly rather than through a real navigation. Going
     * through the router here would activate the portal's own routes — guard,
     * page component, every call that page makes — none of which this is about.
     * What the header shows for a given url is the logic worth pinning; that it
     * is wired to NavigationEnd is one line, and the build checks it.
     */
    function goTo(url: string): void {
        spyOnProperty(router, 'url', 'get').and.returnValue(url);
        (component as any).syncTitleToRoute();
        fixture.detectChanges();
    }

    it('names the page in the header, and the section above it', () => {
        goTo('/student-portal/documents');

        expect(component.pageTitleKey).toBe('STUDENT_PORTAL.DOCUMENTS');
        expect(component.sectionTitleKey).toBe('STUDENT_PORTAL.NAV_GROUP.MY_FILE');
        expect(fixture.nativeElement.querySelector('.portal-header__title').textContent.trim())
            .toBe('STUDENT_PORTAL.DOCUMENTS');
    });

    it('still names the page when the url carries an id', () => {
        // startsWith, not equals: a detail view is still that section.
        goTo('/student-portal/inquiries/42');

        expect(component.pageTitleKey).toBe('STUDENT_PORTAL.INQUIRIES');
    });

    it('ignores a query string when working out where it is', () => {
        goTo('/student-portal/extensions?page=2');

        expect(component.pageTitleKey).toBe('STUDENT_PORTAL.EXTENSIONS');
    });

    it('falls back to the portal name on a page the menu does not carry', () => {
        goTo('/student-portal/somewhere-else');

        expect(component.pageTitleKey).toBe('STUDENT_PORTAL.PORTAL_TITLE');
        expect(component.sectionTitleKey).toBe('');
    });

    it('collapses to a rail, and remembers it', () => {
        const setItem = spyOn(localStorage, 'setItem');

        component.toggleCollapsed();
        fixture.detectChanges();

        expect(component.isCollapsed).toBeTrue();
        // The headings are words; they have no place on a 72px rail.
        expect(fixture.nativeElement.querySelectorAll('.portal-nav__heading').length).toBe(0);
        expect(fixture.nativeElement.querySelectorAll('.portal-menu li[nz-menu-item]').length).toBe(11);
        expect(setItem).toHaveBeenCalledWith('studentPortalSiderCollapsed', '1');
    });

    it('opens expanded when storage cannot be read', () => {
        // A private window, or a browser set to block site data, throws on read.
        spyOn(localStorage, 'getItem').and.throwError('denied');

        const other = TestBed.createComponent(PortalLayoutComponent);

        expect(other.componentInstance.isCollapsed).toBeFalse();
    });

    it('shows the drawer instead of the rail on a narrow window', () => {
        component.isMobile = true;
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.portal-sider')).toBeNull();
        expect(fixture.nativeElement.querySelector('.portal-drawer')).not.toBeNull();
    });

    it('gives the drawer exactly the destinations the rail has', () => {
        component.isMobile = true;
        fixture.detectChanges();

        const drawerLabels = textOf('.portal-drawer__nav li a');
        const railLabels = component.navGroups.flatMap(g => g.items.map(i => i.labelKey));

        // The point of the single array: these cannot drift apart again.
        expect(drawerLabels).toEqual(railLabels);
    });

    it('shows who is signed in', () => {
        expect(component.studentInitials).toBe('LA');
        expect(fixture.nativeElement.querySelector('.portal-identity__name').textContent.trim())
            .toBe('Layla Al Mansouri');
    });

    it('reports no initials rather than crashing when the profile has no name', () => {
        component.studentName = '';

        expect(component.studentInitials).toBe('—');
    });

    /**
     * Travel tickets and Extensions ran off the page at 768–1024px (live QA #14):
     * the wrapper's `overflow-x: auto` was scoped to the shell's own markup, so it
     * never reached the routed pages that carry the class.
     */
    it('lets a wide table on a routed page scroll inside its wrapper', () => {
        const inner = fixture.nativeElement.querySelector('.portal-content-inner') as HTMLElement;
        const wrapper = document.createElement('div');
        wrapper.className = 'portal-table-wrapper';
        wrapper.innerHTML = '<table style="width: 3000px"><tr><td>wide</td></tr></table>';
        inner.appendChild(wrapper);

        expect(getComputedStyle(wrapper).overflowX).toBe('auto');
        expect(wrapper.getBoundingClientRect().width)
            .withContext('the wrapper grew with the table instead of scrolling')
            .toBeLessThan(3000);
        wrapper.remove();
    });
});
