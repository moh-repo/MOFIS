import { QuarterOption, withFiscalYears } from './accounting-shared';

/**
 * W4-01: the allocation and batch screens opened on the newest quarter — a test
 * row for 2030 on the review database — and every save was refused with "No
 * fiscal year covers quarter 20301".
 */
describe('withFiscalYears', () => {
    const quarters: QuarterOption[] = [
        { key: 20301, label: 'Q1 2030', closed: false },
        { key: 20253, label: 'Q3 2025', closed: false },
        { key: 20252, label: 'Q2 2025', closed: true }
    ];

    it('disables a quarter whose year has no fiscal year and says why', () => {
        const { options } = withFiscalYears(quarters, [{ yearNo: 2025 }], 'no fiscal year yet');

        expect(options[0].disabled).toBeTrue();
        expect(options[0].label).toBe('Q1 2030 — no fiscal year yet');
        expect(options[1].disabled).toBeFalse();
        expect(options[1].label).toBe('Q3 2025');
    });

    it('opens on the newest open quarter that has a year', () => {
        expect(withFiscalYears(quarters, [{ yearNo: 2025 }], '').selected).toBe(20253);
    });

    it('falls back to a closed quarter with a year, then to nothing', () => {
        const closedOnly: QuarterOption[] = [{ key: 20252, label: 'Q2 2025', closed: true }];
        expect(withFiscalYears(closedOnly, [{ yearNo: 2025 }], '').selected).toBe(20252);
        expect(withFiscalYears(quarters, [], '').selected).toBeNull();
    });
});
