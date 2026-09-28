import { BehaviorSubject } from 'rxjs';
import { AppMenuComponent } from './app.menu.component';
import { MenuService } from './app.menu.service';
import { AppMenuItem, MenuVariant } from './menu/menu-model';

/**
 * The switch itself: which tree renders, what collapses, and what search does.
 *
 * The component is instantiated directly rather than through TestBed — all of
 * this is class logic, and rendering it would drag in PrimeNG, the router and the
 * whole translation stack to test none of them.
 */
describe('AppMenuComponent', () => {

    /** Permission codes a "country officer" holds; everything else is denied. */
    const OFFICER = ['DASHBOARD', 'STUDENTS', 'STUDENT_HISTORY', 'STUDENT_EXTENSION',
                     'STUDENT_TRAVEL', 'STUDENT_INFO_REQUESTS', 'FINANCE', 'FINANCE_ARCH',
                     'WAFAR', 'APPROVALS', 'REPORTS', 'REPORT_ACADEMIC', 'REPORT_FINANCE',
                     'REPORT_OTHER'];

    let menuService: MenuService;
    let variant$: BehaviorSubject<MenuVariant>;
    let permissions$: BehaviorSubject<string[]>;
    let allowed: string[] | 'all';
    /** The ministry admin tier (DEC-15); the super admin ('all') counts too. */
    let ministryAdmin: boolean;

    const variantService = {
        get variant() { return variant$.value; },
        get isProposed() { return variant$.value === 'proposed'; },
        variant$: undefined as any
    };

    /**
     * The real UmsPermissionService holds a flat list of codes and answers
     * canView(X) as hasPermission('X.VIEW'). The fixtures here name page codes
     * without the suffix, so both spellings count as held.
     */
    const has = (code: string) =>
        allowed === 'all' || allowed.includes(code) || allowed.includes(code.replace(/\.VIEW$/, ''));

    function make(): AppMenuComponent {
        variantService.variant$ = variant$;
        const component = new AppMenuComponent(
            {} as any,
            menuService,
            { translate: (k: string) => k, currentLanguage$: new BehaviorSubject('en') } as any,
            {
                canView: (code: string) => has(`${code}.VIEW`) || has(code),
                hasPermission: (code: string) => has(code)
            } as any,
            // 'all' stands for the super admin, the only account that holds everything.
            { permissions$, currentUser$: new BehaviorSubject(null), isSuperAdmin: () => allowed === 'all',
              isMinistryAdminOrAbove: () => allowed === 'all' || ministryAdmin } as any,
            variantService as any
        );
        component.ngOnInit();
        return component;
    }

    const labelsOf = (items: AppMenuItem[]): string[] => items.map((i) => i.label!);

    const leaves = (items: AppMenuItem[]): AppMenuItem[] =>
        items.flatMap((i) => (i.separator ? [] : i.items ? leaves(i.items) : [i]));

    beforeEach(() => {
        menuService = new MenuService();
        variant$ = new BehaviorSubject<MenuVariant>('proposed');
        permissions$ = new BehaviorSubject<string[]>([]);
        allowed = 'all';
        ministryAdmin = false;
    });

    it('renders the proposed tree by default', () => {
        const component = make();
        expect(component.model.length).toBe(7);
        expect(labelsOf(component.model)).toContain('NAVIGATION.APPROVALS_AND_REQUESTS');
    });

    it('renders the classic tree when the user has switched', () => {
        variant$.next('classic');
        const component = make();
        // Ten sections and the separator between the last two.
        expect(component.model.length).toBe(11);
        expect(labelsOf(component.model)).toContain('NAVIGATION.ACCOUNTING_SYSTEM');
        expect(labelsOf(component.model)).not.toContain('NAVIGATION.APPROVALS_AND_REQUESTS');
    });

    it('swaps trees when the variant changes, without being rebuilt', () => {
        const component = make();
        expect(component.model.length).toBe(7);

        variant$.next('classic');
        expect(component.model.length).toBe(11);

        variant$.next('proposed');
        expect(component.model.length).toBe(7);
    });

    it('leaves classic without collapsible roots or a search field', () => {
        variant$.next('classic');
        const component = make();
        expect(component.collapsibleRoots).toBe(false);
        expect(component.showSearch).toBe(false);
    });

    it('shows system settings to the super admin only, whatever else is held (review M2)', () => {
        const hasSystemSettings = (c: AppMenuComponent) =>
            leaves(c.model).some(i => i.routerLink?.join('/') === '/settings/system-settings');

        expect(hasSystemSettings(make())).toBe(true);

        allowed = ['SETTINGS', 'SETTINGS.VIEW', 'SETTINGS.EDIT'];
        const officer = make();
        expect(leaves(officer.model).some(i => i.routerLink?.join('/') === '/settings/code-lists'))
            .withContext('the rest of the settings section stays').toBe(true);
        expect(hasSystemSettings(officer)).toBe(false);
    });

    it('shows the admin tool pages to the super admin and the ministry admin only (DEC-15)', () => {
        const tools = ['/ums-admin/pages', '/ums-admin/reports-admin', '/ums-admin/fields', '/ums-admin/api-links'];
        const shown = (c: AppMenuComponent) =>
            leaves(c.model).map(i => i.routerLink?.join('/')).filter(r => tools.includes(r ?? ''));

        for (const variant of ['proposed', 'classic'] as MenuVariant[]) {
            variant$.next(variant);
            expect(shown(make()).length).withContext(`super admin, ${variant}`).toBe(4);

            allowed = ['UMS_ADMIN', 'UMS_ADMIN.VIEW'];
            ministryAdmin = false;
            const countryAdmin = make();
            expect(shown(countryAdmin)).withContext(`country admin, ${variant}`).toEqual([]);
            expect(leaves(countryAdmin.model).some(i => i.routerLink?.join('/') === '/ums-admin/users'))
                .withContext('the staff pages stay').toBe(true);

            ministryAdmin = true;
            expect(shown(make()).length).withContext(`ministry admin, ${variant}`).toBe(4);
            allowed = 'all';
        }
    });

    it('gives the proposed layout collapsible roots and a search field', () => {
        const component = make();
        expect(component.collapsibleRoots).toBe(true);
        expect(component.showSearch).toBe(true);
    });

    it('hides what a country officer cannot view', () => {
        allowed = OFFICER;

        const proposed = make();
        // The server gates /student-admin/* with STUDENT_PII (STUDENTS.VIEW is
        // enough), so Administration survives for an officer — but holding only
        // the student-portal access pages, nothing else the section lists.
        const links = leaves(proposed.model).map((l) => l.routerLink?.[0] as string);
        expect(links.filter((l) => l.startsWith('/student-admin/')).length).toBe(5);
        expect(links.some((l) => l.startsWith('/ums-admin/') || l.startsWith('/audit/') || l.startsWith('/system-admin/')))
            .withContext('UMS, audit and system administration stay hidden').toBeFalse();
        expect(labelsOf(proposed.model)).toContain('NAVIGATION.STUDENTS');

        variant$.next('classic');
        expect(labelsOf(proposed.model)).toContain('NAVIGATION.STUDENTS');
    });

    it('drops a classic group left holding nothing but its own separators', () => {
        // The bug this replaced: filterMenuItemByPermission returned separators
        // untouched, so System Settings — five separators between six themed
        // blocks — survived on those alone once every real row was denied, and
        // kept the whole Administration section alive with it. An officer got a
        // section header opening onto a stack of horizontal rules.
        // Without STUDENTS the officer holds nothing under Administration at all
        // (the student-portal access pages ride on STUDENTS.VIEW).
        allowed = OFFICER.filter((c) => c !== 'STUDENTS');
        variant$.next('classic');
        const classic = make();

        expect(labelsOf(classic.model)).not.toContain('NAVIGATION.ADMINISTRATION');
    });

    it('does not strand the separator left behind by a dropped section', () => {
        // Classic's one root separator sits between Alert Events and
        // Administration. Dropping Administration leaves it trailing, which
        // renders as a rule with nothing under it.
        allowed = OFFICER;
        variant$.next('classic');
        const classic = make();

        expect(classic.model.length).toBeGreaterThan(0);
        expect(classic.model[classic.model.length - 1].separator).toBeFalsy();
        expect(classic.model[0].separator).toBeFalsy();
    });

    it('never leaves two separators back to back', () => {
        allowed = OFFICER;
        variant$.next('classic');
        const doubled = (items: AppMenuItem[]): boolean =>
            items.some((item, i) =>
                (!!item.separator && !!items[i - 1]?.separator) ||
                (item.items ? doubled(item.items) : false));

        expect(doubled(make().model)).toBe(false);
    });

    it('keeps every separator classic ships when nothing is denied', () => {
        // The fix must not tidy away rules that are still doing their job. A
        // superadmin sees classic exactly as before: one at root, five inside
        // System Settings.
        allowed = 'all';
        variant$.next('classic');
        const classic = make();

        const count = (items: AppMenuItem[]): number =>
            items.reduce((n, i) => n + (i.separator ? 1 : 0) + (i.items ? count(i.items) : 0), 0);

        expect(count(classic.model)).toBe(6);
        expect(labelsOf(classic.model)).toContain('NAVIGATION.ADMINISTRATION');
    });

    it('leaves the proposed tree no separators to survive on', () => {
        const anySeparator = (items: AppMenuItem[]): boolean =>
            items.some((i) => i.separator || (i.items ? anySeparator(i.items) : false));

        allowed = 'all';
        expect(anySeparator(make().model)).toBe(false);
    });

    it('drops a section whose every child is denied', () => {
        allowed = OFFICER;
        const component = make();
        // ALERT_EVENTS and NOTIFICATIONS are both absent from OFFICER, so the
        // merged section should not survive as an empty header.
        expect(labelsOf(component.model)).not.toContain('NAVIGATION.ALERTS_AND_NOTIFICATIONS');
    });

    it('narrows the tree to matching rows while searching', () => {
        const component = make();
        component.onSearch('wafar');

        const found = leaves(component.model).map((i) => i.routerLink!.join('/'));
        expect(found).toContain('/wafar');
        expect(found).toContain('/wafar-reconciliation');
        expect(found).not.toContain('/students');
    });

    it('keeps a matching group whole rather than hiding its children', () => {
        const component = make();
        component.onSearch('NAVIGATION.WAFAR');

        const finance = component.model.find((i) => i.label === 'NAVIGATION.FINANCE');
        const wafar = finance!.items!.find((i) => i.label === 'NAVIGATION.WAFAR');
        expect(wafar!.items!.length).toBe(3);
    });

    it('expands everything while a search is running', () => {
        const component = make();
        expect(component.collapsibleRoots).toBe(true);

        component.onSearch('reports');
        expect(component.collapsibleRoots).toBe(false);

        component.clearSearch();
        expect(component.collapsibleRoots).toBe(true);
    });

    it('restores the full tree when the search is cleared', () => {
        const component = make();
        const before = component.model.length;

        component.onSearch('zzzz-no-such-page');
        expect(component.model.length).toBe(0);

        component.clearSearch();
        expect(component.model.length).toBe(before);
    });

    it('does not take the topbar menu button away when a search finds nothing', () => {
        // hasVisibleItems drives the hamburger. It is a property of the tree, not
        // of what the user has typed into the filter.
        let hasItems: boolean | undefined;
        menuService.hasVisibleItems$.subscribe((v) => (hasItems = v));

        const component = make();
        component.onSearch('zzzz-no-such-page');

        expect(component.model.length).toBe(0);
        expect(hasItems).toBe(true);
    });

    it('reports no visible items when the user may see nothing', () => {
        allowed = [];
        let hasItems: boolean | undefined;
        menuService.hasVisibleItems$.subscribe((v) => (hasItems = v));

        make();

        expect(hasItems).toBe(false);
    });

    it('builds the tree again when permissions arrive after it was first built', () => {
        // The sidebar used to be removed from the DOM the moment hasVisibleItems
        // went false, which destroyed this component and unsubscribed it. When the
        // permissions it was waiting on landed a moment later there was nothing
        // left to hear them, and the menu stayed missing until a page reload.
        allowed = [];
        let hasItems: boolean | undefined;
        menuService.hasVisibleItems$.subscribe((v) => (hasItems = v));

        const component = make();
        expect(component.model.length).toBe(0);
        expect(hasItems).toBe(false);

        allowed = 'all';
        permissions$.next(['DASHBOARD.VIEW']);

        expect(component.model.length).toBeGreaterThan(0);
        expect(hasItems).toBe(true);
    });

    it('leaves the flag at its default once no menu is left to compute it', () => {
        // Sign-out empties the permissions and the flag goes false. It must not
        // still be false when the next sign-in builds a fresh layout, or that
        // layout hides its sidebar and hamburger on the previous user's answer.
        allowed = [];
        let hasItems: boolean | undefined;
        menuService.hasVisibleItems$.subscribe((v) => (hasItems = v));

        const component = make();
        expect(hasItems).toBe(false);

        component.ngOnDestroy();

        expect(hasItems).toBe(true);
    });
    it('shows each notifications row to the code its page needs, not to the section\'s VIEW right', () => {
        // The backend guards sending and the settings with their action permissions,
        // and only the history with NOTIFICATIONS.VIEW. Gating the Send row on VIEW
        // showed it to people whose every request on the page the server then
        // refused (round-5 Phase 7; the settings row was fixed the same way before).
        allowed = ['NOTIFICATIONS'];
        const viewOnly = leaves(make().model).map((i) => i.routerLink!.join('/'));

        expect(viewOnly).toContain('/notifications/history');
        expect(viewOnly).not.toContain('/notifications/send');
        expect(viewOnly).not.toContain('/notifications/settings');

        allowed = ['NOTIFICATIONS.SEND'];
        expect(leaves(make().model).map((i) => i.routerLink!.join('/'))).toContain('/notifications/send');

        allowed = ['NOTIFICATIONS.SETTINGS'];
        const settingsOnly = leaves(make().model).map((i) => i.routerLink!.join('/'));

        expect(settingsOnly).toContain('/notifications/settings');
        expect(settingsOnly).not.toContain('/notifications/history');
    });

});
