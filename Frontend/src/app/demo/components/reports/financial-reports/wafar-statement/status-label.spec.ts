import { CodeListService, EnumOption } from '../../../../service/code-list.service';
import { WafarStatementComponent } from './wafar-statement.component';

/**
 * Live QA M14(a) (25 Sep 2026): the Wafar statement showed the status codes APPROVED /
 * PENDING as stored, on the Arabic screen too.
 */
describe('Wafar statement — status names (M14)', () => {

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

    describe('the Wafar statement', () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const page = (lang: string): any => {
            const proto = WafarStatementComponent.prototype as any;
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const self: any = { codeLists: codeLists(lang), statuses: ['APPROVED', 'PENDING'] };
            self.statusLabel = proto.statusLabel.bind(self);
            return self;
        };

        it('offers the status filter by name and keeps the code as its value', () => {
            const self = page('ar');
            const options = Object.getOwnPropertyDescriptor(WafarStatementComponent.prototype, 'statusOptions')!.get!.call(self);
            expect(options).toEqual([
                { label: 'معتمد', value: 'APPROVED' },
                { label: 'قيد الانتظار', value: 'PENDING' }
            ]);
        });

        it('names a row\'s status in English on the English screen', () => {
            expect(page('en').statusLabel('APPROVED')).toBe('Approved');
        });
    });
});
