/**
 * The codes each backend read gate accepts (`MoheSecurityExpressions`), for a screen
 * that makes a call behind another module's permission only when the person may make
 * it (live QA M6: a role allowed on a screen got an "Access denied" toast from such a
 * call). Keep in step with the Java constants named on each list. The server decides
 * either way; these only spare a call it would refuse.
 */

/** `APPROVALS`. */
export const APPROVALS_READ = ['APPROVALS.VIEW', 'APP_PENDING.VIEW', 'APP_PENDING.APPROVE', 'APP_PENDING.REJECT', 'UMS_ADMIN.VIEW'];

/** `FINANCE`. */
export const FINANCE_READ = ['FINANCE.VIEW', 'FINANCE.EDIT', 'FINANCE.EXPORT', 'UMS_ADMIN.VIEW'];

/** `FINANCE_ARCH or FINANCE`, the finance history's read gate. */
export const FINANCE_ARCH_READ = ['FINANCE_ARCH.VIEW', 'FINANCE_ARCH.EDIT', 'FINANCE_ARCH.EXPORT', 'FINANCE_ARCH.DELETE', ...FINANCE_READ];

/** `WAFAR`. */
export const WAFAR_READ = ['WAFAR.VIEW', 'WAFAR.ADD', 'WAFAR.EDIT', 'WAFAR.DELETE', 'WAFAR.EXPORT', 'UMS_ADMIN.VIEW'];
