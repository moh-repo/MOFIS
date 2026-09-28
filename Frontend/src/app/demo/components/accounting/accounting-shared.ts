import { MoheQuarter } from '../../service/mohe-quarter.service';

/** A MOHE quarter as the accounting screens pick it: by its YYYYQ key. */
export interface QuarterOption {
    key: number;
    label: string;
    closed: boolean;
    /** No fiscal year covers it, so nothing can be allocated or paid in it yet (W4-01). */
    disabled?: boolean;
}

/**
 * Marks the quarters whose year has no fiscal year as not selectable, and
 * picks the one to open on: the newest open quarter that has a year, else the
 * newest that has a year (W4-01). The screens used to open on the newest
 * quarter, a test row for 2030 on the review database, and refuse every save
 * with "No fiscal year covers quarter 20301".
 */
export function withFiscalYears(options: QuarterOption[], years: Array<{ yearNo: number }>,
                                noYearSuffix: string): { options: QuarterOption[]; selected: number | null } {
    const known = new Set((years ?? []).map(y => y.yearNo));
    const marked = options.map(o => {
        const covered = known.has(Math.floor(o.key / 10));
        return covered ? { ...o, disabled: false } : { ...o, disabled: true, label: `${o.label} — ${noYearSuffix}` };
    });
    const usable = marked.filter(o => !o.disabled);
    const selected = (usable.find(o => !o.closed) ?? usable[0])?.key ?? null;
    return { options: marked, selected };
}

/**
 * Turns the MOHE quarter rows into distinct, newest-first options.
 *
 * The allocation and batch tables key on `YYYYQ` (e.g. 20253), the same value
 * `STUDENT_FINANCE` carries. Rows with no year or number cannot be keyed and are
 * dropped; a country-specific row that repeats a global key is folded into it,
 * because the key is the quarter and the key is what the ledger stores.
 */
export function toQuarterOptions(quarters: MoheQuarter[], arabic: boolean): QuarterOption[] {
    const byKey = new Map<number, QuarterOption>();
    for (const q of quarters ?? []) {
        if (q.quarterYear == null || q.quarterNumber == null) { continue; }
        const key = q.quarterYear * 10 + q.quarterNumber;
        if (byKey.has(key)) { continue; }
        const name = arabic ? q.quarterName : (q as { quarterNameEn?: string }).quarterNameEn || q.quarterName;
        byKey.set(key, {
            key,
            label: name ? `${name} (${key})` : `${q.quarterYear} Q${q.quarterNumber}`,
            closed: !!q.quarterClosed
        });
    }
    return [...byKey.values()].sort((a, b) => b.key - a.key);
}

/** `yyyy-MM-dd` in local time, for the server's `@DateTimeFormat(iso = DATE)` parameters. */
export function toIsoDate(d: Date | null | undefined): string | null {
    if (!d) { return null; }
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Tag severities come from the code lists; see CodeListService.severity and the codeSeverity pipe. */
export type { PrimeSeverity as TagSeverity } from '../../service/code-list.service';
