/**
 * Filtering for the portal's client-side lists.
 *
 * <p>Only for lists the page has loaded whole — Travel Tickets, Extensions,
 * Documents, Year Results, My Requests. Inquiries and Info Requests are paged by
 * the server, and a box that searched only the page you happen to be on would
 * quietly lie: type a title that exists on page three and the list says there is
 * nothing. Info Requests filters through the server instead, which its endpoint
 * already supports; Inquiries has no filter parameters, so it gets no box.
 */

/** Case- and diacritic-insensitive, so an Arabic name matches however it is typed. */
function normalise(value: unknown): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value)
    .toLocaleLowerCase()
    // Decompose first, so an accent that arrived as one precomposed character
    // becomes a base letter plus a mark the class below can strip.
    .normalize('NFKD')
    // The class holds three ranges, invisible in this file because the
    // characters themselves are combining marks: U+0300-U+036F (Latin
    // accents), U+064B-U+0652 (Arabic short vowels) and U+0640 (tatweel).
    .replace(/[̀-ًͯ-ْـ]/g, '');
}

/**
 * The rows whose selected fields contain {@code term}.
 *
 * <p>An empty or blank term returns every row rather than none — a cleared
 * search box means "no filter", not "match the empty string".
 */
export function filterRows<T>(rows: T[], term: string, selectors: ((row: T) => unknown)[]): T[] {
  const needle = normalise(term).trim();
  if (!needle) {
    return rows;
  }
  return rows.filter(row => selectors.some(select => normalise(select(row)).includes(needle)));
}

/**
 * The distinct values of one field, in the order first seen, with blanks
 * dropped — the options for a status filter, built from the rows actually
 * present rather than from an enum the student may have none of.
 */
export function distinctValues<T>(rows: T[], select: (row: T) => string | null | undefined): string[] {
  const seen: string[] = [];
  for (const row of rows) {
    const value = select(row);
    if (value && !seen.includes(value)) {
      seen.push(value);
    }
  }
  return seen;
}
