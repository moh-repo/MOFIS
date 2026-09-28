import { CodeListService } from '../demo/service/code-list.service';
import { pickLanguage } from '../demo/pipes/in-language.pipe';
import { PortalFinance } from './student-portal-api.service';

/**
 * The name of a backend code in the reader's language, from the code list
 * that owns it (`extension-status`, `info-request-status`, `inquiry-type`…).
 * An empty code is the fallback. A code the list does not know is shown as
 * the server's own label when one was sent — the request-language reading
 * the server made for a client whose lists did not load — and as itself
 * otherwise.
 */
export function portalEnumLabel(
  codeLists: CodeListService,
  list: string,
  code: string | null | undefined,
  fallback = '—',
  serverLabel?: string | null
): string {
  if (code == null || code === '') return serverLabel || fallback;
  if (codeLists.find(list, code)) return codeLists.label(list, code);
  return serverLabel || String(code);
}

/**
 * The finance status as the portal words it: the status row's own name in the
 * reader's language, else the generic wording by whether the money is received.
 *
 * <p>Never the raw id. STUDENT_FINANCE.STUDY_FIN_RECIEVED_STATUS is 0 on nearly
 * every row — the column's default, which names no row of
 * STUDY_FIN_RECEIVED_STATUS — and My Data printed "Status: 0" (QA SP-004, live
 * M7). The finance-status page had its own copy of this rule; both read it here.
 */
export function financeStatusLabel(
  finance: PortalFinance | null | undefined,
  language: string,
  translate: (key: string) => string
): string {
  const name = pickLanguage(finance, 'studyFinRecievedStatusName', language);
  if (name) return name;
  return translate(finance?.received === true
    ? 'STUDENT_PORTAL.FINANCE_STATUS_RECEIVED'
    : 'STUDENT_PORTAL.FINANCE_STATUS_PENDING');
}
