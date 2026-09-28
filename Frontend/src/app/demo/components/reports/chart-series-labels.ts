/**
 * Translation key per series name the chart endpoints send (`ChartDataService` on the
 * server, which names every dataset in English).
 *
 * Keyed on the dataset's own label, not its position: an earlier positional list on
 * Line Charts mislabelled real intake figures as graduations when the server changed
 * a series. An unmapped name falls back to the server's English label rather than
 * borrowing its neighbour's name.
 *
 * Bar Charts used to draw the English names as they came (live QA R5-12), and the
 * legend is what the chart PDF prints as its column headers, so the Arabic PDF had
 * English headers. Line Charts and Bar Charts now share this one map.
 */
// TODO(plan 4.4): drop this map once the chart endpoints serve bilingual dataset
// labels from LU_REPORT_LABEL.
export const CHART_SERIES_LABEL_KEYS: Readonly<Record<string, string>> = {
    // Line Charts
    'Student Count': 'LINE_CHARTS.DATASETS.ENROLLMENT.STUDENT_COUNT',
    'Active Students': 'LINE_CHARTS.DATASETS.ENROLLMENT.ACTIVE_COUNT',
    'New Students': 'LINE_CHARTS.DATASETS.ENROLLMENT.NEW_COUNT',
    'Total Disbursed': 'LINE_CHARTS.DATASETS.PAYMENT.TOTAL_AMOUNT',
    'Average Disbursement': 'LINE_CHARTS.DATASETS.PAYMENT.AVERAGE_AMOUNT',
    'Payment Count': 'LINE_CHARTS.DATASETS.PAYMENT.PAYMENT_COUNT',
    // The payment-patterns chart names the same series "Payment Volume".
    'Payment Volume': 'LINE_CHARTS.DATASETS.PAYMENT.PAYMENT_COUNT',
    'Average Level': 'LINE_CHARTS.DATASETS.ACADEMIC.AVERAGE_LEVEL',
    'Suspended Students': 'LINE_CHARTS.DATASETS.RETENTION.SUSPENDED_COUNT',
    'Withdrawn Students': 'LINE_CHARTS.DATASETS.RETENTION.WITHDRAWN_COUNT',
    // Bar Charts ("Student Count" and "Active Students" above serve both)
    'Total Students': 'GRAPHS.SERIES.TOTAL_STUDENTS',
    'Total Amount': 'GRAPHS.SERIES.TOTAL_AMOUNT',
    'Average Amount': 'GRAPHS.SERIES.AVERAGE_AMOUNT',
    'Budgeted Amount': 'GRAPHS.SERIES.BUDGETED_AMOUNT',
    'Received Amount': 'GRAPHS.SERIES.RECEIVED_AMOUNT'
};

/** The series name in the page's language, or the server's name when it has no key or no translation. */
export function localizeSeriesLabel(label: string | undefined, translate: (key: string) => string): string {
    if (!label) {
        return label ?? '';
    }
    const key = CHART_SERIES_LABEL_KEYS[label];
    if (!key) {
        return label;
    }
    const translated = translate(key);
    return translated && translated !== key ? translated : label;
}

/** A copy of the chart data with each dataset's label localized; the server's data is left as it came. */
export function localizeChartSeries<T extends { label?: string }>(
    data: { labels: string[]; datasets: T[] },
    translate: (key: string) => string
): { labels: string[]; datasets: T[] } {
    return {
        labels: data.labels,
        datasets: (data.datasets ?? []).map(dataset => ({
            ...dataset,
            label: localizeSeriesLabel(dataset.label, translate)
        }))
    };
}
