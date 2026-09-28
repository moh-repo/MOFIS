import { buildClassicMenu } from './menu-classic';
import { AppMenuItem } from './menu-model';
import snapshot from './menu-classic.snapshot.json';

/**
 * Holds the classic sidebar still.
 *
 * Both menus ship and a user picks between them, on the promise that the classic
 * one is exactly what it has always been. That promise is only worth something if
 * it survives future edits, so it is a test rather than a comment: if anyone
 * moves, renames, re-icons or re-orders a classic row, this fails and names the
 * row.
 *
 * If classic is being changed on purpose, regenerate the snapshot with
 * `npm run gen-menu-snapshot` and commit it alongside the change.
 */
describe('classic sidebar', () => {

    /** Translation keys, not English — the snapshot must survive copy edits. */
    const tree = buildClassicMenu((key) => key);

    /** Only what decides what a user sees and where it goes. */
    const shape = (items: AppMenuItem[]): any[] => items.map((item) => {
        if (item.separator) { return { separator: true }; }
        const node: any = { label: item.label, icon: item.icon };
        if (item.routerLink) { node.routerLink = item.routerLink; }
        if (item.queryParams) { node.queryParams = item.queryParams; }
        if (item.items) { node.items = shape(item.items); }
        return node;
    });

    const destinations = (items: AppMenuItem[]): number =>
        items.reduce((n, i) => n + (i.separator ? 0 : i.items ? destinations(i.items) : 1), 0);

    const labels = (items: AppMenuItem[], trail = ''): string[] =>
        items.flatMap((i) => {
            if (i.separator) { return []; }
            const here = trail ? `${trail} > ${i.label}` : `${i.label}`;
            return i.items ? labels(i.items, here) : [here];
        });

    it('matches the checked-in snapshot exactly', () => {
        expect(shape(tree)).toEqual(snapshot as any[]);
    });

    it('still offers every destination it shipped with', () => {
        // The snapshot covers this, but the count is the number quoted in the
        // proposal (66), plus the four accounting screens added on 27 Aug 2026,
        // so it is asserted in its own right.
        // Plus the two settings screens of 30 Aug 2026 (code lists, system parameters),
        // plus the effective-permissions screen of 6 Sep 2026 (security plan R-13),
        // plus the access-review screen of the same day (security plan S-04).
        expect(destinations(tree)).toBe(74);
    });

    it('reports the first differing row when the snapshot drifts', () => {
        // Not an assertion about the menu — it proves the failure above is
        // diagnosable, since a deep-equal on a 70-row tree is not.
        const now = labels(tree);
        const then = labels(snapshot as AppMenuItem[]);
        const firstDiff = now.findIndex((l, i) => l !== then[i]);
        expect(firstDiff)
            .withContext(firstDiff === -1 ? '' : `classic row changed: "${then[firstDiff]}" became "${now[firstDiff]}"`)
            .toBe(-1);
    });

    it('has no search field and no collapsible sections to lose', () => {
        // Both are properties of the proposed layout. Classic's contract is that
        // its root rows are plain static headers, so nothing here should ever
        // grow a `pageCode` override or a nested fourth level.
        const depth = (items: AppMenuItem[]): number =>
            Math.max(...items.map((i) => (i.items ? 1 + depth(i.items) : 1)));
        expect(depth(tree)).toBe(3);
        expect(tree.some((i) => !!i.pageCode)).toBe(false);
    });
});
