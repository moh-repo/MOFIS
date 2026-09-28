/**
 * Date values in student change requests (review R2b), shared by the portal's
 * "my requests" page and the ministry's review screen.
 *
 * The server keeps a date as yyyy-MM-dd. Requests made before that rule hold the
 * field's previous value as an ISO instant ("2000-05-26T21:00:00Z"), which is a
 * date in the ministry's zone written in UTC; shown raw it reads as the day before.
 */

const ISO_INSTANT = /^\d{4}-\d{2}-\d{2}T/;
const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/** A stored value as the date it names (yyyy-MM-dd); anything else is returned unchanged. */
export function changeRequestDateText(raw: string | null | undefined): string {
  if (raw == null || raw === '') return '';
  if (ISO_INSTANT.test(raw)) {
    const d = new Date(raw);
    return isNaN(d.getTime()) ? raw : toDateText(d);
  }
  return raw;
}

/** A local calendar date as yyyy-MM-dd, without the UTC shift toISOString() adds. */
export function toDateText(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** yyyy-MM-dd (or a stored instant) as a local Date for a date picker; null when it names no date. */
export function fromDateText(raw: string | null | undefined): Date | null {
  const m = ISO_DATE.exec(changeRequestDateText(raw));
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : null;
}
