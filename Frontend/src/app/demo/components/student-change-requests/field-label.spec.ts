import { CodeListService, EnumOption } from '../../service/code-list.service';
import { StudentChangeRequestsComponent } from './student-change-requests.component';

/**
 * Live QA M14(b) (25 Sep 2026): staff Change Requests showed the label a request was
 * submitted under ("Cell Phone", "Gender" from an English portal) on the Arabic screen.
 */
describe('Staff Change Requests — the field in the reader\'s language (M14)', () => {

    const row = (list: string, value: string, labelAr: string, labelEn: string): EnumOption => ({
        label: labelAr, value, labelAr, labelEn, sortOrder: 0, uiSeverity: null,
        icon: null, isSystem: true, active: true, extra: null
    });

    const codeLists = (lang: string): CodeListService => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const service = new CodeListService({} as any, {} as any, { getCurrentLanguage: () => lang } as any);
        service.store({
            'change-request-field': [
                row('change-request-field', 'studentCellphone', 'رقم الجوال', 'Cell Phone'),
                row('change-request-field', 'studentGenderId', 'الجنس', 'Gender')
            ],
            'wafar-spending-status': [
                row('wafar-spending-status', 'APPROVED', 'معتمد', 'Approved'),
                row('wafar-spending-status', 'PENDING', 'قيد الانتظار', 'Pending')
            ]
        });
        return service;
    };

    describe('staff Change Requests', () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const label = (lang: string, req: any): string =>
            (StudentChangeRequestsComponent.prototype as any).fieldLabel.call({ codeLists: codeLists(lang), currentLanguage: lang }, req);

        it('names the field in Arabic although the student submitted it in English', () => {
            expect(label('ar', { fieldName: 'studentGenderId', fieldLabel: 'Gender' })).toBe('الجنس');
        });

        it('finds a field stored under an older code by the label it was submitted with', () => {
            expect(label('ar', { fieldName: 'cellphone', fieldLabel: 'Cell Phone' })).toBe('رقم الجوال');
            expect(label('en', { fieldName: 'cellphone', fieldLabel: 'Cell Phone' })).toBe('Cell Phone');
        });
    });
});

/**
 * Live QA L11 (25 Sep 2026): on the Arabic screen a phone number's "+" was drawn at the
 * wrong end. Phone values are written left to right; other values keep the page's
 * direction.
 */
describe('Staff Change Requests — phone numbers left to right (L11)', () => {
    const dir = (fieldName: string) => (StudentChangeRequestsComponent.prototype as any).valueDir.call({}, { fieldName });

    it('writes the contact numbers left to right, under current and older field codes', () => {
        expect(dir('studentCellphone')).toBe('ltr');
        expect(dir('studentEmergencyContact')).toBe('ltr');
        expect(dir('cellphone')).toBe('ltr');
    });

    it('leaves every other value in the page\'s direction', () => {
        expect(dir('studentName')).toBeNull();
        expect(dir('studentGenderId')).toBeNull();
    });
});
