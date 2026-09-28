import { firstOf, followPage, pageOf, resetPaging } from './table-paging.util';

describe('table-paging.util', () => {

    it('reads the page a paginator event moved to, in rows and in pages', () => {
        expect(pageOf({ first: 40, rows: 20 }, 10)).toEqual({ first: 40, rows: 20, page: 2 });
        expect(pageOf({ first: 0, rows: 50 }, 10)).toEqual({ first: 0, rows: 50, page: 0 });
    });

    it('falls back to the screen\'s page size when the event carries none', () => {
        // A table without [rows] sends a lazy load with no page size; dividing
        // by it would ask the server for page NaN.
        expect(pageOf({ first: 30 }, 10)).toEqual({ first: 30, rows: 10, page: 3 });
        expect(pageOf({ first: 30, rows: null }, 10).page).toBe(3);
        expect(pageOf({ first: 30, rows: 0 }, 10).page).toBe(3);
    });

    it('reads a missing or nonsense event as the first page', () => {
        expect(pageOf(undefined, 20)).toEqual({ first: 0, rows: 20, page: 0 });
        expect(pageOf(null, 20)).toEqual({ first: 0, rows: 20, page: 0 });
        expect(pageOf({ first: -10, rows: 20 }, 20).first).toBe(0);
        expect(pageOf({ first: 30, rows: 0 }, 0).page).withContext('never divides by zero').toBe(30);
    });

    it('rounds a first that is not on a page boundary down to its page', () => {
        // After the page size changes, `first` can sit mid-page until the
        // paginator moves it; the page it is on is still the one below.
        expect(pageOf({ first: 25, rows: 10 }, 10).page).toBe(2);
    });

    it('turns a page the server chose back into the table\'s first', () => {
        expect(firstOf(3, 20)).toBe(60);
        expect(firstOf(0, 20)).toBe(0);
        expect(firstOf(-1, 20)).toBe(0);
        expect(firstOf(NaN, 20)).toBe(0);
    });

    it('follows the table to its page, keeping first, rows and page together', () => {
        const screen = { first: 0, rows: 10, page: 0 };
        const at = followPage(screen, { first: 50, rows: 25 });
        expect(screen).toEqual({ first: 50, rows: 25, page: 2 });
        expect(at).toEqual({ first: 50, rows: 25, page: 2 });
    });

    it('follows without inventing a page number a client-side table does not keep', () => {
        const screen: { first: number; rows: number; page?: number } = { first: 0, rows: 10 };
        followPage(screen, { first: 20, rows: 10 });
        expect(screen.first).toBe(20);
        expect('page' in screen).toBeFalse();
    });

    it('resets the page number and the table\'s first together', () => {
        // Resetting only the page number was the fault (QA B8, N9): the server
        // was asked for page one and the paginator stayed on page three.
        const screen = { first: 40, rows: 20, page: 2 };
        resetPaging(screen);
        expect(screen.page).toBe(0);
        expect(screen.first).withContext('the table has to move too').toBe(0);
        expect(screen.rows).withContext('the page size is the user\'s choice, not the filter\'s').toBe(20);
    });

    it('resets a client-side table, which has only first', () => {
        const screen: { first: number; page?: number } = { first: 30 };
        resetPaging(screen);
        expect(screen.first).toBe(0);
        expect('page' in screen).toBeFalse();
    });
});
