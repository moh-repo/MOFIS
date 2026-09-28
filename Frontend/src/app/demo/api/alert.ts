/**
 * A code resolved by the server: the code plus both names, the tag severity
 * and the icon its code-list row carries. What the dashboard notices show.
 */
export interface CodeRef {
    code: string;
    nameAr: string | null;
    nameEn: string | null;
    uiSeverity: string | null;
    icon: string | null;
}

/**
 * One system notice as `/api/v1/dashboard/notices` returns it.
 *
 * No field has a default: a notice with no priority has `priority: null` and
 * renders as `—`, not as LOW; a missing date is not "now".
 */
export interface Alert {
    alertId: number;
    alertTitle: string | null;
    alertMessage: string | null;
    /** English text where the notice has one; pick with `inLanguage` (QA H2). */
    alertTitleEn?: string | null;
    alertMessageEn?: string | null;
    type: CodeRef | null;
    priority: CodeRef | null;
    status: CodeRef | null;
    createdBy: string | null;
    createdDate: string | null;
    expiresDate: string | null;
    isRead: boolean;
}

/** The name of a resolved code in the given language; the other name, then the code, when missing. */
export function codeRefName(ref: CodeRef | null | undefined, lang: string): string {
    if (!ref) {
        return '';
    }
    const primary = lang === 'ar' ? ref.nameAr : ref.nameEn;
    const other = lang === 'ar' ? ref.nameEn : ref.nameAr;
    return primary || other || ref.code || '';
}

const RELATIVE_UNITS: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['year', 1000 * 60 * 60 * 24 * 365],
    ['month', 1000 * 60 * 60 * 24 * 30],
    ['week', 1000 * 60 * 60 * 24 * 7],
    ['day', 1000 * 60 * 60 * 24],
    ['hour', 1000 * 60 * 60],
    ['minute', 1000 * 60]
];

/**
 * "3 hours ago" / "قبل ٣ ساعات" for a timestamp, in the reader's locale via
 * Intl.RelativeTimeFormat. Empty when there is no date. `now` is injectable
 * for the tests.
 */
export function timeAgo(date: string | Date | null | undefined, locale: string, now: Date = new Date()): string {
    if (!date) {
        return '';
    }
    const then = new Date(date);
    if (isNaN(then.getTime())) {
        return '';
    }
    const diff = then.getTime() - now.getTime();
    const rtf = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
    for (const [unit, ms] of RELATIVE_UNITS) {
        if (Math.abs(diff) >= ms) {
            return rtf.format(Math.round(diff / ms), unit);
        }
    }
    return rtf.format(0, 'second');
}
