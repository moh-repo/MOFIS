import { SortEvent } from 'primeng/api';
import { compareValues, sortRows, sortTreeNodes, valueAt } from './table-sort.util';

/** A sort event as PrimeNG raises it for a `customSort` table. */
function event(data: any[], field: string, order: number): SortEvent {
    return { data, field, order } as SortEvent;
}

describe('table-sort.util', () => {

    it('sorts by a plain property, ascending and descending', () => {
        const rows = [{ code: 'B' }, { code: 'C' }, { code: 'A' }];
        sortRows(event(rows, 'code', 1));
        expect(rows.map(r => r.code)).toEqual(['A', 'B', 'C']);
        sortRows(event(rows, 'code', -1));
        expect(rows.map(r => r.code)).toEqual(['C', 'B', 'A']);
    });

    it('sorts by what the cell prints when the column is given an accessor', () => {
        // The row carries an id; the cell prints a name. Sorting by the id would
        // order Egypt, Jordan, France as 3, 1, 2 — which is the bug this exists for.
        const names: Record<number, string> = { 3: 'Egypt', 1: 'Jordan', 2: 'France' };
        const rows = [{ countryId: 1 }, { countryId: 2 }, { countryId: 3 }];
        sortRows(event(rows, 'attache', 1), { attache: row => names[row.countryId] });
        expect(rows.map(r => names[r.countryId])).toEqual(['Egypt', 'France', 'Jordan']);
    });

    it('sorts numbers as numbers, not as text', () => {
        const rows = [{ n: 10 }, { n: 9 }, { n: 100 }];
        sortRows(event(rows, 'n', 1));
        expect(rows.map(r => r.n)).toEqual([9, 10, 100]);
    });

    it('sorts codes that carry a number in the order a person reads them', () => {
        const rows = [{ batch: 'B-10' }, { batch: 'B-2' }, { batch: 'B-1' }];
        sortRows(event(rows, 'batch', 1));
        expect(rows.map(r => r.batch)).toEqual(['B-1', 'B-2', 'B-10']);
    });

    it('keeps blanks out of the way and never loses a row', () => {
        const rows = [{ ref: 'B' }, { ref: null }, { ref: 'A' }, { ref: '' }, { ref: undefined }];
        sortRows(event(rows, 'ref', 1));
        expect(rows.length).toBe(5);
        expect(rows.slice(0, 2).map(r => r.ref)).toEqual(['A', 'B']);
    });

    it('sorts booleans false first, and dates chronologically', () => {
        const flags = [{ on: true }, { on: false }, { on: true }];
        sortRows(event(flags, 'on', 1));
        expect(flags.map(f => f.on)).toEqual([false, true, true]);

        const days = [{ d: new Date('2026-03-01') }, { d: new Date('2025-12-31') }];
        sortRows(event(days, 'd', 1));
        expect(days[0].d.getFullYear()).toBe(2025);
    });

    it('orders Arabic names in Arabic, not by their code points', () => {
        const rows = [{ name: 'بغداد' }, { name: 'أمانة' }, { name: 'تونس' }];
        sortRows(event(rows, 'name', 1), {}, 'ar');
        expect(rows.map(r => r.name)).toEqual(['أمانة', 'بغداد', 'تونس']);
    });

    it('reads a dotted path', () => {
        expect(valueAt({ a: { b: 7 } }, 'a.b')).toBe(7);
        expect(valueAt({ a: null }, 'a.b')).toBeNull();
        expect(valueAt(undefined, 'a')).toBeUndefined();
    });

    it('compares nothing to nothing as equal', () => {
        expect(compareValues(null, undefined)).toBe(0);
        expect(compareValues('a', null)).toBeLessThan(0);
        expect(compareValues(null, 'a')).toBeGreaterThan(0);
    });

    it('leaves a non-array payload alone rather than throwing', () => {
        expect(() => sortRows({ data: undefined, field: 'x', order: 1 } as SortEvent)).not.toThrow();
        expect(() => sortTreeNodes({ data: null, field: 'x', order: 1 } as SortEvent)).not.toThrow();
    });

    it('sorts tree nodes by their data, one level at a time', () => {
        // TreeTable raises the event for each level itself, so a node's children
        // must be left where they are: sorting them here would sort them twice.
        const nodes = [
            { data: { code: '2' }, children: [{ data: { code: '2.2' } }, { data: { code: '2.1' } }] },
            { data: { code: '1' }, children: [] }
        ];
        sortTreeNodes(event(nodes, 'code', 1));
        expect(nodes.map(n => n.data.code)).toEqual(['1', '2']);
        expect(nodes[1].children.map(c => c.data.code)).withContext('this level is not this call\'s business')
            .toEqual(['2.2', '2.1']);
    });
});
