/**
 * "20242" → "2024 Q2": a YYYYQ quarter code as a reader says it.
 *
 * <p>STUDENT_DATA.CURRENT_QUARTER and the archive's snapshot quarter hold the
 * packed code, and the profile printed it as it stands: "Current Quarter 20242"
 * (QA B3). Formatted here, not looked up: the quarter list sits behind the quarter
 * and finance permissions, which a reader of a profile need not hold.
 *
 * @param short the word for "quarter" in the reader's language — `COMMON.QUARTER_SHORT`
 */
export function formatQuarterCode(code: unknown, short: string): string {
    const text = code == null ? '' : String(code).trim();
    const m = /^(\d{4})([1-4])$/.exec(text);
    return m ? `${m[1]} ${short}${m[2]}` : text;
}

/**
 * "20242" or "202402" → "2024 / 2": a packed year-month key as a reader says it.
 *
 * <p>STUDENT_FINANCE.STUDENT_FIN_RECIEVED_YEAR_MONTH holds the year and the month
 * run together, and the portal's My Data printed it as stored, "Year / Month
 * 20242", beside the same value already spelt out from its two columns (live QA
 * L10, 24 Sep 2026). Anything that is not a four-digit year followed by a month
 * 1–12 is returned as it came.
 */
export function formatYearMonthCode(code: unknown): string {
    const text = code == null ? '' : String(code).trim();
    const m = /^(\d{4})(\d{1,2})$/.exec(text);
    if (!m) return text;
    const month = Number(m[2]);
    return month >= 1 && month <= 12 ? `${m[1]} / ${month}` : text;
}
