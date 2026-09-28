import { HttpParams } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { PaymentSummaryComponent } from './payment-summary.component';

/**
 * Payment Summary's cards and names.
 *
 * <p>QA round 5, R5-05: the data call returned null rows inside its pages; the
 * summary then threw on {@code paymentAmount}. The page survives one.
 *
 * <p>QA round 6, R6-05: Total Amount and Amount by status added up the page on
 * screen while Total Payments counted every row (24,300 of 1,596,620). They come
 * from the totals call now, over every row the filters select.
 *
 * <p>QA round 6, R6-06: the English screen showed the Arabic type, status and quarter.
 */
describe('PaymentSummaryComponent', () => {

    let urls: string[];

    function build(page: unknown[], totals: unknown, lang: 'en' | 'ar' = 'en'): PaymentSummaryComponent {
        urls = [];
        const http = {
            get: (url: string) => {
                urls.push(url);
                if (url.endsWith('/totals')) {
                    return totals instanceof Error ? throwError(() => totals) : of(totals);
                }
                return of({ content: page, totalElements: 981, number: 0, size: 20 });
            }
        };
        const translation = { getCurrentLanguage: () => lang, translate: (k: string) => k };
        const scope = { appendCountryParam: () => new HttpParams() };
        const codeLists = { active: () => [{ value: 'PAID' }, { value: 'PENDING' }] };
        return new PaymentSummaryComponent(http as any, { add: () => undefined } as any,
            translation as any, scope as any, {} as any, {} as any, codeLists as any);
    }

    const totalsCalls = () => urls.filter((u) => u.endsWith('/totals')).length;

    it('Total Amount and Amount by status are every selected row\'s, not the page\'s', () => {
        const component = build(
            [{ paymentAmount: 1000, paymentStatusCode: 'PAID' }, { paymentAmount: 500, paymentStatusCode: 'PENDING' }],
            { totalAmount: 1592120, byStatus: [{ code: 'PENDING', amount: 92120 }, { code: 'PAID', amount: 1500000 }] });
        component.onFilterChange();

        expect(component.totalPayments).toBe(981);
        expect(component.totalAmount).toBe(1592120);
        expect(component.amountByStatus).toEqual([
            { code: 'PAID', amount: 1500000 },
            { code: 'PENDING', amount: 92120 }
        ]);
    });

    it('turning the page reloads the rows but not the totals', () => {
        const component = build([], { totalAmount: 10, byStatus: [] });
        component.onFilterChange();
        expect(totalsCalls()).toBe(1);

        component.onPageChange({ page: 3, rows: 20 });
        expect(totalsCalls()).toBe(1);
        expect(urls.length).toBe(3);
    });

    it('skips a null row on the page', () => {
        const component = build([{ paymentAmount: 1000 }, null, { paymentAmount: 500 }], { totalAmount: 1500, byStatus: [] });
        component.loadPaymentData();

        expect(component.paymentData.length).toBe(2);
    });

    it('counts a missing or unreadable amount as 0, and a status with no code last', () => {
        const component = build([], {
            totalAmount: '1000', byStatus: [{ code: null, amount: 'n/a' }, { code: 'PAID', amount: '1000' }]
        });
        component.loadTotals();

        expect(component.totalAmount).toBe(1000);
        expect(component.amountByStatus).toEqual([
            { code: 'PAID', amount: 1000 },
            { code: null, amount: 0 }
        ]);
    });

    it('a failed totals call leaves the cards empty rather than showing 0', () => {
        spyOn(console, 'error');
        const component = build([], new Error('500'));
        component.loadTotals();

        expect(component.totalAmount).toBeNull();
        expect(component.amountByStatus).toEqual([]);
    });

    it('shows each name in the screen\'s language, and the other when one is blank', () => {
        const english = build([], null, 'en');
        expect(english.localized('بنفسه', 'Self')).toBe('Self');
        expect(english.localized('الربع الثاني 2024', '')).toBe('الربع الثاني 2024');
        expect(english.localized(null, null)).toBeNull();
        expect(english.nameField('paymentType')).toBe('paymentTypeEn');

        const arabic = build([], null, 'ar');
        expect(arabic.localized('بنفسه', 'Self')).toBe('بنفسه');
        expect(arabic.localized(null, 'Not specified')).toBe('Not specified');
        expect(arabic.nameField('paymentStatus')).toBe('paymentStatus');
    });
});
