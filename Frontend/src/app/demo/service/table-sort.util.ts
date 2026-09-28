import { SortEvent } from 'primeng/api';

/**
 * Client-side table sorting that orders rows by what the column shows.
 *
 * PrimeNG's own sort reads `field` straight off the row, which is right for a
 * column that prints a property and wrong for most columns on these screens:
 * the accounting tables print `feeTypeName(row.feeTypeId)`, the bilingual ones
 * print the name for the current language, and a status column prints a
 * code-list label. Sorting those by the raw property orders fee types by their
 * primary key and Arabic names by an English field — visibly arbitrary, which
 * is worse than no sorting at all.
 *
 * So a screen names its columns freely (`pSortableColumn="feeType"`) and hands
 * this function a map from that name to the value the cell displays. A column
 * whose name is a real property needs no entry.
 *
 *     <p-table [customSort]="true" (sortFunction)="sortTable($event)" …>
 *       <th pSortableColumn="feeType">… <p-sortIcon field="feeType" />
 *
 *     sortTable(event: SortEvent): void {
 *         sortRows(event, {
 *             feeType: row => this.feeTypeName(row.feeTypeId),
 *             status: row => this.codeLists.label('allocation-status', row.status, this.lang)
 *         }, this.lang);
 *     }
 *
 * Sorting is in place, on the array PrimeNG hands over, as `customSort`
 * requires.
 */

/** How a column's sort value is read from a row, when it is not the field itself. */
export type SortAccessors = Record<string, (row: any) => unknown>;

/** Blank last when ascending, first when descending — the order flips with the column. */
function isBlank(value: unknown): boolean {
    return value === null || value === undefined || value === '';
}

/** The value at a (possibly dotted) property path. */
export function valueAt(row: any, path: string): unknown {
    if (!path) return undefined;
    if (!path.includes('.')) return row?.[path];
    return path.split('.').reduce((current: any, key) => (current == null ? current : current[key]), row);
}

/**
 * Compares two cell values: numbers numerically, dates chronologically,
 * booleans false-first, everything else as text in the given locale — which is
 * what puts Arabic names in Arabic order rather than UTF-16 order.
 */
export function compareValues(a: unknown, b: unknown, locale = 'en'): number {
    if (isBlank(a) && isBlank(b)) return 0;
    if (isBlank(a)) return 1;
    if (isBlank(b)) return -1;

    if (typeof a === 'number' && typeof b === 'number') return a - b;
    if (typeof a === 'boolean' || typeof b === 'boolean') return (a === b) ? 0 : (a ? 1 : -1);
    if (a instanceof Date && b instanceof Date) return a.getTime() - b.getTime();

    // `numeric` so BATCH_2 comes before BATCH_10, and codes with numbers in them
    // (quarter 1..12, semester 1..12) read in the order a person expects.
    return String(a).localeCompare(String(b), locale || 'en', { numeric: true, sensitivity: 'base' });
}

/** Sorts `event.data` in place by the sorted column, reading it through `accessors` when given. */
export function sortRows(event: SortEvent, accessors: SortAccessors = {}, locale = 'en'): void {
    const rows = event.data;
    if (!Array.isArray(rows)) return;

    const field = event.field ?? '';
    const order = event.order ?? 1;
    const read = accessors[field] ?? ((row: any) => valueAt(row, field));

    rows.sort((a, b) => order * compareValues(read(a), read(b), locale));
}

/**
 * The same, for a PrimeNG TreeTable: the rows are `TreeNode`s, so the value is
 * read from `node.data`. Only the level handed over is sorted — TreeTable walks
 * into `children` itself and raises this event again for each level, which is
 * what keeps a sorted tree a tree.
 */
export function sortTreeNodes(event: SortEvent, accessors: SortAccessors = {}, locale = 'en'): void {
    const nodes = event.data;
    if (!Array.isArray(nodes)) return;

    const field = event.field ?? '';
    const order = event.order ?? 1;
    const read = accessors[field] ?? ((row: any) => valueAt(row, field));

    nodes.sort((a, b) => order * compareValues(read(a?.data), read(b?.data), locale));
}
