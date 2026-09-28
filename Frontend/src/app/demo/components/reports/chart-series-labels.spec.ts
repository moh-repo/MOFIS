import en from '../../../../assets/i18n/en.json';
import ar from '../../../../assets/i18n/ar.json';
import { CHART_SERIES_LABEL_KEYS, localizeChartSeries, localizeSeriesLabel } from './chart-series-labels';

function lookup(dictionary: unknown, key: string): string | undefined {
    const value = key.split('.').reduce<unknown>(
        (node, part) => (node && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined),
        dictionary
    );
    return typeof value === 'string' ? value : undefined;
}

const arabic = (key: string) => lookup(ar, key) ?? key;

describe('chart series labels', () => {
    // The names ChartDataService sends for the five bar charts (live QA R5-12) and the
    // one line series the old Line Charts map missed.
    const barSeries = ['Total Students', 'Active Students', 'Total Amount', 'Average Amount',
        'Student Count', 'Budgeted Amount', 'Received Amount'];

    it('maps every bar-chart series and "Payment Volume"', () => {
        for (const name of [...barSeries, 'Payment Volume']) {
            expect(CHART_SERIES_LABEL_KEYS[name]).withContext(name).toBeDefined();
        }
    });

    it('has an English and a distinct Arabic text for every key', () => {
        for (const [name, key] of Object.entries(CHART_SERIES_LABEL_KEYS)) {
            const english = lookup(en, key);
            const translated = lookup(ar, key);
            expect(english).withContext(`${name} → ${key} (en)`).toBeTruthy();
            expect(translated).withContext(`${name} → ${key} (ar)`).toBeTruthy();
            expect(translated).withContext(`${name} → ${key} (ar)`).not.toBe(english);
            expect(translated).withContext(`${name} → ${key} (ar)`).toMatch(/[؀-ۿ]/);
        }
    });

    it('gives the Arabic PDF Arabic column headers: every bar series is translated', () => {
        const reply = {
            labels: ['2024', '2025'],
            datasets: barSeries.map(label => ({ label, data: [1, 2], backgroundColor: 'x', borderColor: 'y' }))
        };

        const localized = localizeChartSeries(reply, arabic);

        for (const dataset of localized.datasets) {
            expect(dataset.label).toMatch(/[؀-ۿ]/);
        }
        expect(localized.datasets[0].data).toEqual([1, 2]);
        expect(localized.labels).toBe(reply.labels);
        // The server's reply is left as it came, so a language switch can re-label it.
        expect(reply.datasets[0].label).toBe('Total Students');
    });

    it('keeps the server name for a series it does not know, or a key with no translation', () => {
        expect(localizeSeriesLabel('Something New', arabic)).toBe('Something New');
        expect(localizeSeriesLabel('Total Amount', key => key)).toBe('Total Amount');
        expect(localizeSeriesLabel(undefined, arabic)).toBe('');
    });
});
