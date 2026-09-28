import { StudentAddComponent } from './student-add.component';

/**
 * The finance draft raised with a new student (live QA H7, 25 Sep 2026).
 *
 * The first receipt of due is stored as a YYYYQ key (20251 = 2025 Q1). The draft copied
 * it into the received year, the finance row was saved with the year 20251, and the
 * dashboard's financial trend drew a year "20251".
 */
describe('StudentAddComponent linked finance draft', () => {

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const draftFor = (student: any) => StudentAddComponent.prototype.buildLinkedFinanceDraft.call({ student } as any);

    it('splits a YYYYQ first receipt of due into year and quarter', () => {
        const d = draftFor({ studentFirstReceiptOfDue: 20251, studentAmount: 300 });
        expect(d['studentFinRecievedYear']).toBe(2025);
        expect(d['studentFinRecievedMonth']).toBe(1);
        expect(d['studentFinRecievedYearMonth']).toBe(20251);
    });

    it('keeps a plain year as the year', () => {
        const d = draftFor({ studentFirstReceiptOfDue: 2025 });
        expect(d['studentFinRecievedYear']).toBe(2025);
        expect(d['studentFinRecievedMonth']).toBe(0);
    });

    it('falls back to the delegation decision year when there is no due', () => {
        const d = draftFor({ studentFirstReceiptOfDue: 0, studentDelegationDecisionYear: '2024/2025' });
        expect(d['studentFinRecievedYear']).toBe(2024);
    });
});
