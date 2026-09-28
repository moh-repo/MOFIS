import { financeStatusLabel, portalEnumLabel } from './student-portal-i18n.helper';
import { codeListStub, codeRow } from '../testing/code-list.stub';
import { PortalFinance } from './student-portal-api.service';

/**
 * The portal's two naming rules.
 *
 * <p>A stored value is named by the code list that owns it; a value the list
 * does not know is shown as the server labelled it rather than as a bare code
 * (QA SP-009). A finance status is the row's name in the reader's language and
 * never the raw id, which is 0 on nearly every row (QA SP-004, live M7).
 */
describe('student portal naming helpers', () => {

    describe('portalEnumLabel', () => {
        const lists = codeListStub({
            'extension-status': [
                codeRow('IN_PROGRESS', { labelEn: 'In progress', labelAr: 'قيد المعالجة' }),
                codeRow('APPROVED', { labelEn: 'Approved', labelAr: 'موافق عليه' })
            ]
        }, 'ar');

        it('names a known code from its list row, in the reader\'s language', () => {
            expect(portalEnumLabel(lists, 'extension-status', 'IN_PROGRESS')).toBe('قيد المعالجة');
            expect(portalEnumLabel(lists, 'extension-status', 'approved')).withContext('case does not matter').toBe('موافق عليه');
        });

        it('falls back to the server\'s label for a value the list does not know, then to the value', () => {
            // A legacy row holds a status no lookup row matches: the server
            // could still name it in the request language, so that name wins
            // over printing the stored text; with neither, the text is all there is.
            expect(portalEnumLabel(lists, 'extension-status', 'ACTIVE', '-', 'نشط')).toBe('نشط');
            expect(portalEnumLabel(lists, 'extension-status', 'ACTIVE')).toBe('ACTIVE');
        });

        it('shows the fallback for an empty value', () => {
            expect(portalEnumLabel(lists, 'extension-status', null)).toBe('—');
            expect(portalEnumLabel(lists, 'extension-status', '', '-')).toBe('-');
            expect(portalEnumLabel(lists, 'extension-status', '', '-', 'غير مبين')).toBe('غير مبين');
        });
    });

    describe('financeStatusLabel', () => {
        const translate = (key: string) => key;

        it('uses the status row\'s name in the reader\'s language', () => {
            const f = { studyFinRecievedStatus: 2, studyFinRecievedStatusName: 'لم يستلم', studyFinRecievedStatusNameEn: 'Not Received' } as PortalFinance;

            expect(financeStatusLabel(f, 'en', translate)).toBe('Not Received');
            expect(financeStatusLabel(f, 'ar', translate)).toBe('لم يستلم');
        });

        it('falls back to the other language when one name is blank', () => {
            const f = { studyFinRecievedStatusName: 'معلق', studyFinRecievedStatusNameEn: '' } as PortalFinance;

            expect(financeStatusLabel(f, 'en', translate)).toBe('معلق');
        });

        it('says received or pending, never the id, when the status names no row', () => {
            expect(financeStatusLabel({ studyFinRecievedStatus: 0 } as PortalFinance, 'en', translate))
                .toBe('STUDENT_PORTAL.FINANCE_STATUS_PENDING');
            expect(financeStatusLabel({ studyFinRecievedStatus: 0, received: true } as PortalFinance, 'ar', translate))
                .toBe('STUDENT_PORTAL.FINANCE_STATUS_RECEIVED');
            expect(financeStatusLabel(null, 'en', translate)).toBe('STUDENT_PORTAL.FINANCE_STATUS_PENDING');
        });
    });
});
