import { EnumOption } from './code-list.service';

/**
 * The academic years a study-year column stores, built from the years the
 * database holds.
 *
 * <p>`LU_ACADEMIC_YEAR` lists single years — 2005 through 2030, one code each —
 * while the `STUDY_YEAR` columns store a **span**: every row of
 * `STUDENT_BOOKS_ALLOWANCE` and of `STUDENT_LOAN` holds `2022-2023`, `2023-2024`
 * or `2024-2025`. Offering the lookup's codes as they are would have written
 * `2024` into a column whose every existing value names two years, so each code
 * becomes the span it opens.
 *
 * <p>The field stays editable in the form, so a value stored outside this
 * convention is still shown as it was typed rather than blanked.
 */
export function academicYearSpans(rows: EnumOption[]): { label: string; value: string }[] {
    return rows
        .map(row => Number(row.value))
        .filter(year => Number.isInteger(year) && year > 0)
        .sort((a, b) => b - a) // most recent first: that is what a new record needs
        .map(year => {
            const span = `${year}-${year + 1}`;
            return { label: span, value: span };
        });
}
