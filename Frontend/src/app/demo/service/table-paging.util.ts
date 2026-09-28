/**
 * Keeps a p-table's paginator on the page the component is really showing.
 *
 * A list screen keeps its own page number, because that is what the server is
 * asked for, and PrimeNG keeps its own `first` — the index of the first row on
 * screen, which is what the paginator and its "Showing 21 to 30 of 948" read.
 * Nothing ties the two together unless the component binds `[first]`. So a
 * filter change that set `this.page = 0` fetched page one while the paginator
 * stayed on page three: the footer read "Showing 21 to 2 of 2", and the next
 * click asked for page four of a two-row result (QA B8, N9). A client-side
 * table has the same fault without the server: the filtered array shrinks
 * under a `first` that points past its end.
 *
 * The fix is one field and one binding; these helpers keep the arithmetic in
 * one place:
 *
 *     first = 0;
 *
 *     <p-table [lazy]="true" [rows]="rows" [(first)]="first" (onLazyLoad)="onPage($event)" …>
 *
 *     onPage(event: TableLazyLoadEvent): void { followPage(this, event); this.load(); }
 *     applyFilters(): void { resetPaging(this); this.load(); }
 *
 * `[(first)]`, not `[first]`: the table moves `first` by itself — on a
 * paginator click, and back to 0 on a sort. A component that never heard of it
 * still holds 0, and setting 0 over 0 is no change, so Angular would not pass
 * it on and the table would stay where it was.
 *
 * A client-side table needs only the field, the binding and `resetPaging(this)`
 * where its filters change — not on the reload after a save or a delete, where
 * staying on the page the user was working on is right, and the paginator
 * steps back by itself if a delete empties the last page.
 */

/** The part of a PrimeNG `onPage` or `onLazyLoad` event that says where the table now is. */
export interface TablePageEvent {
    first?: number | null;
    rows?: number | null;
}

/** Where a p-table stands, in the units the table and the server each count in. */
export interface TablePage {
    /** Index of the first row on screen: what `[first]` binds and the paginator reads. */
    first: number;
    /** Rows per page. */
    rows: number;
    /** Zero-based page number: what the server's `page` parameter counts. */
    page: number;
}

/**
 * Where a page or lazy-load event moved the table. `fallbackRows` stands in
 * when the event carries no page size, which a table without `[rows]` sends.
 */
export function pageOf(event: TablePageEvent | null | undefined, fallbackRows: number): TablePage {
    const rows = event?.rows && event.rows > 0 ? event.rows : fallbackRows;
    const first = event?.first && event.first > 0 ? event.first : 0;
    return { first, rows, page: Math.floor(first / Math.max(rows, 1)) };
}

/** The `first` of a zero-based page, for a page the server chose rather than the table. */
export function firstOf(page: number, rows: number): number {
    return Math.max(0, page || 0) * Math.max(0, rows || 0);
}

/**
 * Moves a screen's paging fields to the page an event moved the table to:
 * `first` and `rows` always, and `page` where the screen keeps one. For the
 * names most screens use; a screen that names them otherwise (`currentPage`,
 * `pageSize`) reads `pageOf` and sets its own.
 */
export function followPage(state: { first: number; rows: number; page?: number }, event: TablePageEvent | null | undefined): TablePage {
    const at = pageOf(event, state.rows);
    state.first = at.first;
    state.rows = at.rows;
    if (typeof state.page === 'number') state.page = at.page;
    return at;
}

/**
 * Back to the first page: `first` and, where the screen keeps one, the page
 * number — together, because resetting only the page number is the fault this
 * file exists for. Call it on every filter or search change.
 */
export function resetPaging(state: { first: number; page?: number }): void {
    state.first = 0;
    if (typeof state.page === 'number') state.page = 0;
}
