import { EnvironmentInjector, Type, createEnvironmentInjector, importProvidersFrom } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ROUTES, Route, Routes } from '@angular/router';
import { AppRoutingModule } from '../../app-routing.module';
import { UmsPermissionGuard } from '../../demo/guards/ums-permission.guard';
import { buildClassicMenu } from './menu-classic';
import { buildProposedMenu } from './menu-proposed';
import { MENU_ROUTE_CODES, MenuAccess, RouteGate, passesGate, visibleMenu } from './menu-access';
import { AppMenuItem } from './menu-model';

/**
 * R5-09 (QA round 5): Finance Officers were offered "Financial Reports", which opened
 * Access Denied. The router runs UmsPermissionGuard on every route it matches, so
 * /reports/financial also needs the /reports gate (REPORTS.VIEW), and the sidebar only
 * asked for the row's own code.
 *
 * This spec reads the real route configuration, lazy modules included, and checks every
 * sidebar row of both layouts against the guards the router would run for its link:
 * for every combination of the codes involved, the row is shown exactly when the page
 * would open. A gate changed on one side only fails here.
 */

/**
 * Rows the sweep for R5-09 found to disagree with their route, listed for their own fix
 * rather than fixed under R5-09. The last spec fails once one of them agrees, so the
 * entry is removed with the fix. Empty since round-5 Phase 7: the /ums-admin and
 * /notifications parents lost their gates (access reviews, send and settings), and the
 * Send row asks NOTIFICATIONS.SEND.
 */
const KNOWN_MISMATCHES: { [link: string]: string } = {};

type GuardData = RouteGate & { superAdminOnly?: boolean; ministryAdminOnly?: boolean };

interface Who extends MenuAccess {
    label: string;
}

/** One route's UmsPermissionGuard decision, in the order canActivate/evaluate take it. */
function guardAllows(data: GuardData, who: Who): boolean {
    if (data.superAdminOnly) return who.isSuperAdmin();
    if (data.ministryAdminOnly && !who.isMinistryAdminOrAbove()) return false;
    if (who.isSuperAdmin()) return true;
    return passesGate(data, who);
}

/** The routes a lazy route loads, as the router reads them from the loaded module. */
async function loadedChildren(route: Route, parent: EnvironmentInjector): Promise<Routes> {
    const loaded = await (route.loadChildren as () => Promise<Type<unknown> | Routes>)();
    if (Array.isArray(loaded)) return loaded;
    const injector = createEnvironmentInjector([importProvidersFrom(loaded)], parent);
    return (injector.get(ROUTES, [], { self: true, optional: true }) as Routes[]).flat();
}

/** The routes the router activates for a URL, outermost first; null when nothing matches. */
async function matchChain(routes: Routes, segments: string[], parent: EnvironmentInjector): Promise<Route[] | null> {
    for (const route of routes) {
        if (route.redirectTo !== undefined || route.path === '**') continue;
        const own = (route.path ?? '').split('/').filter(Boolean);
        if (own.length > segments.length || !own.every((s, i) => s.startsWith(':') || s === segments[i])) continue;
        const rest = segments.slice(own.length);
        const children = route.children ?? (route.loadChildren ? await loadedChildren(route, parent) : null);
        if (!children) {
            if (!rest.length) return [route];
            continue;
        }
        const below = await matchChain(children, rest, parent);
        if (below) return [route, ...below];
    }
    return null;
}

/** Every row that leads to a page, with the layout it belongs to. */
function rows(): { layout: string; item: AppMenuItem }[] {
    const t = (key: string) => key;
    const leaves = (items: AppMenuItem[]): AppMenuItem[] =>
        items.flatMap(i => (i.items?.length ? leaves(i.items) : i.routerLink ? [i] : []));
    return [
        ...leaves(buildClassicMenu(t)).map(item => ({ layout: 'classic', item })),
        ...leaves(buildProposedMenu(t)).map(item => ({ layout: 'proposed', item }))
    ];
}

/** Every person the codes in play can make: each subset of them, as plain, ministry and super admin. */
function everyone(codes: string[]): Who[] {
    const people: Who[] = [];
    for (let mask = 0; mask < 1 << codes.length; mask++) {
        const held = new Set(codes.filter((_, i) => mask & (1 << i)));
        for (const tier of ['staff', 'ministry', 'super'] as const) {
            const superAdmin = tier === 'super';
            const hasPermission = (code: string) => superAdmin || held.has(code);
            people.push({
                label: `${tier} holding [${[...held].join(', ')}]`,
                hasPermission,
                canView: code => hasPermission(`${code}.VIEW`),
                isSuperAdmin: () => superAdmin,
                isMinistryAdminOrAbove: () => tier !== 'staff'
            });
        }
    }
    return people;
}

/** The permission codes a gate can ask for. */
function codesOf(gate: GuardData | string | string[] | undefined): string[] {
    if (!gate) return [];
    if (typeof gate === 'string') return [`${gate}.VIEW`];
    if (Array.isArray(gate)) return gate.map(code => `${code}.VIEW`);
    return [
        ...(gate.permission ? [gate.permission] : []),
        ...codesOf(gate.pageCode),
        ...codesOf(gate.pageCodes)
    ];
}

/** Links that disagree with their route, each with one person who shows it. */
async function disagreements(): Promise<Map<string, string>> {
    TestBed.configureTestingModule({ imports: [AppRoutingModule] });
    const injector = TestBed.inject(EnvironmentInjector);
    const appRoutes = TestBed.inject(ROUTES).flat();
    const found = new Map<string, string>();

    for (const { layout, item } of rows()) {
        const link = '/' + item.routerLink!.join('/').replace(/^\/+/, '');
        const chain = await matchChain(appRoutes, link.split('/').filter(Boolean), injector);
        expect(chain).withContext(`${layout} ${link} matches no route`).not.toBeNull();
        if (!chain) continue;

        const gates = chain
            .filter(route => route.canActivate?.includes(UmsPermissionGuard))
            .map(route => (route.data ?? {}) as GuardData);
        const segments = link.split('/').filter(Boolean);
        const mapGates = segments.map((_, i) => MENU_ROUTE_CODES['/' + segments.slice(0, i + 1).join('/')]);
        const codes = [...new Set([
            ...gates.flatMap(codesOf),
            ...mapGates.flatMap(codesOf),
            ...codesOf(MENU_ROUTE_CODES[link]),
            ...codesOf(item.pageCode),
            ...(item.permission ? [item.permission] : [])
        ])];

        for (const who of everyone(codes)) {
            const opens = gates.every(gate => guardAllows(gate, who));
            const shown = visibleMenu([item], who).length === 1;
            if (opens !== shown) {
                found.set(link, `${layout}: ${who.label} — row ${shown ? 'shown' : 'hidden'}, page ${opens ? 'opens' : 'refused'}`);
                break;
            }
        }
    }
    return found;
}

describe('sidebar rows and their routes (R5-09)', () => {

    it('shows each row exactly when its page would open', async () => {
        const found = await disagreements();
        const unexpected = [...found].filter(([link]) => !(link in KNOWN_MISMATCHES)).map(([link, why]) => `${link}: ${why}`);
        expect(unexpected).toEqual([]);
    });

    it('still disagrees on every row in the known list (remove an entry once it is fixed)', async () => {
        const found = await disagreements();
        const agreeing = Object.keys(KNOWN_MISMATCHES).filter(link => !found.has(link));
        expect(agreeing).toEqual([]);
    });
});
