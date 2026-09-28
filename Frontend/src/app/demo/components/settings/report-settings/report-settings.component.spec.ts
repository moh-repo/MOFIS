import { of } from 'rxjs';
import { ReportSettingsComponent } from './report-settings.component';
import { ReportTypeSettingsDto } from '../../../service/report-settings.service';

/**
 * DEC-22 (review M3, plan W4-07): "Add type" picks a report the system prints
 * from the server's catalogue; it took any text as a code.
 */
describe('ReportSettingsComponent add type', () => {
    let created: ReportTypeSettingsDto[];

    function make(catalogue: string[]): ReportSettingsComponent {
        created = [];
        const service = {
            getAll: () => of([]),
            catalogue: () => of(catalogue),
            create: (dto: ReportTypeSettingsDto) => { created.push(dto); return of({ ...dto, id: 9 }); }
        };
        const messages = { add: () => undefined };
        const translation = { translate: (k: string) => k, getCurrentLanguage: () => 'en', isRTL: () => false };
        return new ReportSettingsComponent(service as any, messages as any, {} as any, translation as any);
    }

    it('offers the unconfigured report codes when the dialog opens', () => {
        const component = make(['APPROVALS_REQUESTS', 'STUDENT_LIST']);
        component.openAddDialog();

        expect(component.showAddDialog).toBeTrue();
        expect(component.catalogueCodes).toEqual(['APPROVALS_REQUESTS', 'STUDENT_LIST']);
        expect(component.catalogueLoading).toBeFalse();
        expect(component.newTypeCode).toBe('');
    });

    it('creates the picked code as it is', () => {
        const component = make(['STUDENT_LIST']);
        component.openAddDialog();
        component.newTypeCode = 'STUDENT_LIST';
        component.newTypeNameEn = 'Student list';
        component.createType();

        expect(created.length).toBe(1);
        expect(created[0].reportTypeCode).toBe('STUDENT_LIST');
        expect(component.showAddDialog).toBeFalse();
    });

    it('creates nothing until a code is picked', () => {
        const component = make(['STUDENT_LIST']);
        component.openAddDialog();
        component.createType();
        expect(created.length).toBe(0);
    });
});
