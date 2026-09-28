import { buildProposedMenu } from './menu-proposed';
import { AppMenuItem } from './menu-model';

/**
 * The proposed sidebar's structural promises.
 *
 * Route resolution and permission-map coverage are checked for both trees by
 * `npm run validate-menu`, which reads the real routing modules; this covers the
 * shape claims the proposal makes, which no build step would otherwise catch.
 */
describe('proposed sidebar', () => {

    const tree = buildProposedMenu((key) => key);

    const leaves = (items: AppMenuItem[]): AppMenuItem[] =>
        items.flatMap((i) => (i.separator ? [] : i.items ? leaves(i.items) : [i]));

    const depth = (items: AppMenuItem[]): number =>
        Math.max(...items.map((i) => (i.items ? 1 + depth(i.items) : 1)));

    it('has seven top-level sections and no separators', () => {
        expect(tree.length).toBe(7);
        expect(tree.some((i) => i.separator)).toBe(false);
    });

    it('opens to seven rows, because only Dashboard is a root-level destination', () => {
        const rootLeaves = tree.filter((i) => i.routerLink && !i.items);
        expect(rootLeaves.map((i) => i.label)).toEqual(['NAVIGATION.DASHBOARD']);
    });

    it('reaches 76 destinations', () => {
        // 68 in the proposal, plus the four accounting screens added on 27 Aug 2026
        // (budget appropriations, allocations, payment batches, exchange rates),
        // plus the two settings screens of 30 Aug 2026 (code lists, system parameters),
        // plus the effective-permissions screen of 6 Sep 2026 (security plan R-13),
        // plus the access-review screen of the same day (security plan S-04).
        expect(leaves(tree).length).toBe(76);
    });

    it('adds no fourth level of nesting', () => {
        expect(depth(tree)).toBe(3);
    });

    it('gives every destination a route and an icon', () => {
        const bare = leaves(tree).filter((i) => !i.routerLink?.length || !i.icon);
        expect(bare.map((i) => i.label)).toEqual([]);
    });

    it('sends each destination to a distinct route', () => {
        // The six lookup rows that only differed by ?tab= are gone; nothing else
        // should quietly reintroduce a duplicate destination.
        const links = leaves(tree).map((i) => i.routerLink!.join('/'));
        const dupes = links.filter((l, i) => links.indexOf(l) !== i);
        expect(dupes).toEqual([]);
    });

    it('gives the seven sections seven different icons', () => {
        // Finding 06: Notifications and Alert Events shipped as two adjacent
        // pi-bell sections. They are one section now, and no icon repeats.
        const icons = tree.map((i) => i.icon);
        expect(new Set(icons).size).toBe(icons.length);
    });

    it('reaches every report category, Operations included', () => {
        const reports = tree.find((i) => i.label === 'NAVIGATION.REPORTS');
        const routes = leaves(reports!.items!).map((i) => i.routerLink!.join('/'));
        for (const category of ['student', 'financial', 'operations', 'statistical',
                                'periodic', 'university', 'custom']) {
            expect(routes).withContext(`/reports/${category}`).toContain(`/reports/${category}`);
        }
    });

    it('keeps the request queues out of Students', () => {
        // Decision 1: they live under Approvals & Requests here. Classic keeps
        // them under Students, which is why both menus ship.
        const students = tree.find((i) => i.label === 'NAVIGATION.STUDENTS');
        const approvals = tree.find((i) => i.label === 'NAVIGATION.APPROVALS_AND_REQUESTS');
        const routesOf = (s: AppMenuItem) => leaves(s.items!).map((i) => i.routerLink!.join('/'));

        expect(routesOf(students!)).not.toContain('/student-info-requests');
        expect(routesOf(approvals!)).toContain('/student-info-requests');
        expect(routesOf(approvals!)).toContain('/student-change-requests');
    });

    it('folds accounting into Finance rather than beside it', () => {
        expect(tree.some((i) => i.label === 'NAVIGATION.ACCOUNTING_SYSTEM')).toBe(false);
        const finance = tree.find((i) => i.label === 'NAVIGATION.FINANCE');
        expect(routesUnder(finance!)).toContain('/accounting/chart-of-accounts');
    });

    function routesUnder(section: AppMenuItem): string[] {
        return leaves(section.items!).map((i) => i.routerLink!.join('/'));
    }
});
