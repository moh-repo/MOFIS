import { resolveChangeRequestFieldLabel } from './change-request-field-label';
import { codeListStub, codeRow } from '../../testing/code-list.stub';

/**
 * The field names on the portal's "My requests" page (live M6).
 *
 * <p>In Arabic the page said "Cell Phone" and "Current Level": those requests
 * were stored under an older field vocabulary the list no longer knows, with
 * the English label beside the code, and the fallback printed the stored label
 * as it was. The list still names the same field in both languages, so the
 * stored label is matched back to its row.
 */
describe('resolveChangeRequestFieldLabel', () => {

    const lists = codeListStub({
        'change-request-field': [
            codeRow('studentCellphone', { labelEn: 'Cell Phone', labelAr: 'رقم الجوال', label: 'رقم الجوال' }),
            codeRow('studentCurrentLevelId', { labelEn: 'Current Level', labelAr: 'المستوى الحالي', label: 'المستوى الحالي' }),
            codeRow('studentEmail', { labelEn: 'Email', labelAr: 'البريد الإلكتروني', label: 'البريد الإلكتروني' })
        ]
    }, 'ar');

    it('names a current code from its row', () => {
        expect(resolveChangeRequestFieldLabel(lists, 'studentCellphone', null)).toBe('رقم الجوال');
        expect(resolveChangeRequestFieldLabel(lists, 'studentCellphone', 'Cell Phone')).toBe('رقم الجوال');
        expect(resolveChangeRequestFieldLabel(lists, 'STUDENTCELLPHONE', undefined)).withContext('case does not matter').toBe('رقم الجوال');
    });

    it('finds the row of an older code through the stored English label', () => {
        expect(resolveChangeRequestFieldLabel(lists, 'cellphone', 'Cell Phone')).toBe('رقم الجوال');
        expect(resolveChangeRequestFieldLabel(lists, 'currentLevel', 'Current Level')).toBe('المستوى الحالي');
    });

    it('matches the stored label whatever its case, spacing or language', () => {
        expect(resolveChangeRequestFieldLabel(lists, 'cellphone', '  cell phone ')).toBe('رقم الجوال');
        expect(resolveChangeRequestFieldLabel(lists, 'cellphone', 'رقم الجوال')).toBe('رقم الجوال');
    });

    it('tries the code itself as a name when the stored label matches nothing', () => {
        expect(resolveChangeRequestFieldLabel(lists, 'Email', null)).toBe('البريد الإلكتروني');
        expect(resolveChangeRequestFieldLabel(lists, 'Email', 'E-mail address')).toBe('البريد الإلكتروني');
    });

    it('shows the stored label, then the code, when no row names the field', () => {
        expect(resolveChangeRequestFieldLabel(lists, 'unknown', 'Something')).toBe('Something');
        expect(resolveChangeRequestFieldLabel(lists, 'unknown', null)).toBe('unknown');
        expect(resolveChangeRequestFieldLabel(lists, '', '')).toBe('');
    });

    it('follows the reader\'s language', () => {
        const english = codeListStub({
            'change-request-field': [codeRow('studentCellphone', { labelEn: 'Cell Phone', labelAr: 'رقم الجوال' })]
        }, 'en');

        expect(resolveChangeRequestFieldLabel(english, 'cellphone', 'رقم الجوال')).toBe('Cell Phone');
    });
});
