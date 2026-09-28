import { distinctValues, filterRows } from './portal-list.helper';

interface Row {
    title: string;
    status: string | null;
    ref?: string | null;
}

const ROWS: Row[] = [
    { title: 'Résumé review', status: 'PENDING', ref: 'A-1' },
    { title: 'Travel to Cairo', status: 'APPROVED', ref: null },
    { title: 'مِنحة دراسية', status: 'APPROVED', ref: 'B-2' },
    { title: 'Extension request', status: null }
];

const BY_TITLE = [(r: Row) => r.title];

/**
 * Filtering for the portal's client-side lists.
 *
 * <p>The behaviour worth pinning is what happens at the edges: a cleared box
 * must mean "no filter" rather than "match nothing", a null field must not throw,
 * and a name typed without its diacritics must still find the row — students
 * type Arabic both ways and would otherwise conclude the record is missing.
 */
describe('portal list filtering', () => {

    describe('filterRows', () => {

        it('returns every row when the box is empty or blank', () => {
            // Not "match the empty string": clearing a search is asking for the
            // whole list back, and returning nothing would look like data loss.
            expect(filterRows(ROWS, '', BY_TITLE)).toEqual(ROWS);
            expect(filterRows(ROWS, '   ', BY_TITLE)).toEqual(ROWS);
        });

        it('matches on a fragment, regardless of case', () => {
            expect(filterRows(ROWS, 'cairo', BY_TITLE).map(r => r.ref)).toEqual([null]);
            expect(filterRows(ROWS, 'EXTENSION', BY_TITLE).length).toBe(1);
        });

        it('matches a Latin word whether or not the accent is typed', () => {
            expect(filterRows(ROWS, 'resume', BY_TITLE).length).toBe(1);
            expect(filterRows(ROWS, 'résumé', BY_TITLE).length).toBe(1);
        });

        it('matches an Arabic word whether or not the short vowels are typed', () => {
            // The row is stored with a kasra on the first letter; nobody types it.
            expect(filterRows(ROWS, 'منحة', BY_TITLE).length).toBe(1);
        });

        it('searches every selector it is given', () => {
            const selectors = [(r: Row) => r.title, (r: Row) => r.ref];

            expect(filterRows(ROWS, 'b-2', selectors).length).toBe(1);
        });

        it('treats a null or missing field as no match rather than throwing', () => {
            const selectors = [(r: Row) => r.ref];

            expect(() => filterRows(ROWS, 'a-1', selectors)).not.toThrow();
            expect(filterRows(ROWS, 'a-1', selectors).length).toBe(1);
        });

        it('finds nothing when nothing matches, without falling back to everything', () => {
            expect(filterRows(ROWS, 'zzz', BY_TITLE)).toEqual([]);
        });
    });

    describe('distinctValues', () => {

        it('lists each value once, in the order first seen', () => {
            expect(distinctValues(ROWS, r => r.status)).toEqual(['PENDING', 'APPROVED']);
        });

        it('drops blanks, so a filter never offers an empty option', () => {
            const rows: Row[] = [
                { title: 'a', status: null },
                { title: 'b', status: '' },
                { title: 'c', status: 'OPEN' }
            ];

            expect(distinctValues(rows, r => r.status)).toEqual(['OPEN']);
        });

        it('is empty for an empty list, so the filter can hide itself', () => {
            expect(distinctValues([] as Row[], r => r.status)).toEqual([]);
        });
    });
});
