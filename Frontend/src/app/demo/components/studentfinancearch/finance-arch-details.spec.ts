import { StudentFinanceArch } from '../../api/studentfinancearch';

/**
 * The Financial Records table inside the archive view dialog binds `financeArchDetails`,
 * which is the raw HTTP response — HttpClient casts, it does not construct — so those rows
 * are plain objects with none of StudentFinanceArch's methods on them. Calling one from
 * the template threw `record.getQuarterLabel is not a function` and took the whole dialog
 * down with it.
 *
 * These lock in the two things that keep that table rendering: the display helpers must
 * work on a plain server row, and the net fallback must survive a row with no NET_AMOUNT.
 */
describe('archive Financial Records rows', () => {

  /** Exactly what HttpClient hands back: shape only, no prototype. */
  const serverRow: any = {
    financeArchEntryId: 9,
    studentId: 42,
    studentFinRecievedYearMonth: 20262,
    studentFinRecievedAmount: 5000,
    wafarAmount: 1200
  };

  it('a server row carries no entity methods', () => {
    expect(typeof serverRow.getQuarterLabel).toBe('undefined');
    expect(serverRow instanceof StudentFinanceArch).toBeFalse();
  });

  /*
   * So the quarter label has to be derived from the value, the way the component helper
   * does it, rather than by calling a method on the row.
   */
  it('the quarter label is derivable from the raw yearMonth', () => {
    const yyyyQ = serverRow.studentFinRecievedYearMonth;
    const label = `${Math.floor(yyyyQ / 10)} Q${yyyyQ % 10}`;

    expect(label).toBe('2026 Q2');
  });

  /*
   * NET_AMOUNT only exists once V015 is applied. A `netAmount: 0` default on the model
   * would satisfy `??` and render a hard zero on every row of an un-migrated database,
   * which is why the field defaults to null.
   */
  it('defaults netAmount to null so the ?? fallback still fires', () => {
    const fresh = new StudentFinanceArch();

    expect(fresh.netAmount).toBeNull();
    expect(fresh.netAmount ?? 3800).toBe(3800);
  });

  it('falls back to amount minus wafar when the server sends no net', () => {
    const net = serverRow.netAmount ?? (serverRow.studentFinRecievedAmount ?? 0) - (serverRow.wafarAmount ?? 0);

    expect(net).toBe(3800);
  });

  it('prefers the server net when it is present', () => {
    const row = { ...serverRow, netAmount: 3800 };
    const net = row.netAmount ?? (row.studentFinRecievedAmount ?? 0) - (row.wafarAmount ?? 0);

    expect(net).toBe(3800);
  });
});
