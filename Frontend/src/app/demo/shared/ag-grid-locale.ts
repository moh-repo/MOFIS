/**
 * AG Grid's own words (pagination panel, filter menu, overlays, screen-reader
 * labels) in the page's language.
 *
 * AG Grid draws those words itself, in English, unless the grid is given
 * `localeText`. The Financial Data Form grids never were, so their Arabic pages
 * read "Page Size", "1 to 20 of 134", "Contains"… (live QA R5-14).
 *
 * Keyed by AG Grid's locale key; the value is this app's translation key. A key
 * with no translation is left out, so the grid keeps its English default for it
 * rather than showing the key.
 *
 * `localeText` and `enableRtl` are read only when a grid is created, so a page
 * that switches language re-creates its grid (see the Financial Data Form
 * templates).
 */
export const AG_GRID_TEXT_KEYS: Readonly<Record<string, string>> = {
    // Pagination panel
    page: 'AG_GRID.PAGE',
    of: 'AG_GRID.OF',
    to: 'AG_GRID.TO',
    firstPage: 'AG_GRID.FIRST_PAGE',
    previousPage: 'AG_GRID.PREVIOUS_PAGE',
    nextPage: 'AG_GRID.NEXT_PAGE',
    lastPage: 'AG_GRID.LAST_PAGE',
    pageSizeSelectorLabel: 'AG_GRID.PAGE_SIZE_LABEL',
    ariaPageSizeSelectorLabel: 'AG_GRID.PAGE_SIZE',
    // Overlays
    loadingOoo: 'AG_GRID.LOADING',
    noRowsToShow: 'AG_GRID.NO_ROWS',
    // Text and number filters
    filterOoo: 'AG_GRID.FILTER_PLACEHOLDER',
    applyFilter: 'AG_GRID.APPLY',
    clearFilter: 'AG_GRID.CLEAR',
    resetFilter: 'AG_GRID.RESET',
    cancelFilter: 'AG_GRID.CANCEL',
    empty: 'AG_GRID.CHOOSE_ONE',
    equals: 'AG_GRID.EQUALS',
    notEqual: 'AG_GRID.NOT_EQUAL',
    lessThan: 'AG_GRID.LESS_THAN',
    greaterThan: 'AG_GRID.GREATER_THAN',
    lessThanOrEqual: 'AG_GRID.LESS_THAN_OR_EQUAL',
    greaterThanOrEqual: 'AG_GRID.GREATER_THAN_OR_EQUAL',
    inRange: 'AG_GRID.BETWEEN',
    inRangeStart: 'AG_GRID.FROM',
    inRangeEnd: 'AG_GRID.TO_VALUE',
    contains: 'AG_GRID.CONTAINS',
    notContains: 'AG_GRID.NOT_CONTAINS',
    startsWith: 'AG_GRID.STARTS_WITH',
    endsWith: 'AG_GRID.ENDS_WITH',
    blank: 'AG_GRID.BLANK',
    notBlank: 'AG_GRID.NOT_BLANK',
    andCondition: 'AG_GRID.AND',
    orCondition: 'AG_GRID.OR',
    // Screen-reader labels
    ariaFilterMenuOpen: 'AG_GRID.OPEN_FILTER_MENU',
    ariaFilterInput: 'AG_GRID.FILTER_INPUT',
    ariaFilterValue: 'AG_GRID.FILTER_VALUE',
    ariaFilterFromValue: 'AG_GRID.FILTER_FROM_VALUE',
    ariaFilterToValue: 'AG_GRID.FILTER_TO_VALUE',
    ariaColumnFiltered: 'AG_GRID.COLUMN_FILTERED'
};

/** The grid's `localeText` for the language `translate` answers in. */
export function agGridLocaleText(translate: (key: string) => string): Record<string, string> {
    const text: Record<string, string> = {};
    for (const [gridKey, key] of Object.entries(AG_GRID_TEXT_KEYS)) {
        const translated = translate(key);
        if (translated && translated !== key) {
            text[gridKey] = translated;
        }
    }
    return text;
}
