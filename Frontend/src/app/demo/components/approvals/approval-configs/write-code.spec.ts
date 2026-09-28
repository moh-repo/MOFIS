import { of } from 'rxjs';
import { APPROVAL_CONFIG_WRITE, ApprovalConfigsComponent } from './approval-configs.component';

/**
 * FIX-PLAN P5-5 (26 Sep 2026). The Approval Rules screen offered Add, Edit and Delete on
 * APP_CONFIG.ADD / .EDIT / .DELETE, while the server checks APP_CONFIG.EDIT on every
 * rule write and no role holds .ADD or .DELETE, so only the super admin saw Add and
 * Delete. And the level editor demanded a role on every level, although a level may name
 * a position instead (D25: the staff holding the position approve it).
 */
describe('ApprovalConfigs — the server\'s write code, and a role or a position', () => {

    /** Any service call answers an empty observable; translate answers its key. */
    const anything = () => new Proxy({} as any, {
        get: (_t, prop) => prop === 'translate' ? (k: string) => k : () => of([])
    });

    function screen(codes: string[], approvalService: any = anything()): ApprovalConfigsComponent {
        const permissions = { hasPermission: (code: string) => codes.includes(code) } as any;
        return new ApprovalConfigsComponent(approvalService, anything(), anything(), anything(), permissions,
            anything(), anything());
    }

    it('APP_CONFIG.EDIT alone shows Add, Edit and Delete; a viewer sees none', () => {
        expect(APPROVAL_CONFIG_WRITE).toBe('APP_CONFIG.EDIT');
        const writer = screen(['APP_CONFIG.EDIT']);
        expect([writer.canAdd, writer.canEdit, writer.canDelete]).toEqual([true, true, true]);

        const viewer = screen(['APPROVALS.VIEW', 'APP_CONFIG.VIEW']);
        expect([viewer.canAdd, viewer.canEdit, viewer.canDelete]).toEqual([false, false, false]);
    });

    it('a level with a position and no role is saved; one with neither is not', () => {
        const approvalService = { updateLevel: jasmine.createSpy('updateLevel').and.returnValue(of({})), getConfigLevels: () => of([]),
            getLevels: () => of([]) } as any;
        const component = screen(['APP_CONFIG.EDIT'], new Proxy(approvalService, {
            get: (t, prop) => prop in t ? t[prop] : () => of([])
        }));
        component.selectedConfig = { id: 2 } as any;

        component.editingLevel = { id: 4, levelName: 'Accountant review', requiredRoleId: null, requiredPositionCode: 'ACCOUNTANT' } as any;
        expect(component.levelNamesWhoApproves()).toBeTrue();
        component.saveLevel();
        expect(approvalService.updateLevel).toHaveBeenCalledTimes(1);

        component.editingLevel = { id: 4, levelName: 'Nobody', requiredRoleId: null, requiredPositionCode: null } as any;
        expect(component.levelNamesWhoApproves()).toBeFalse();
        component.saveLevel();
        expect(approvalService.updateLevel).toHaveBeenCalledTimes(1);
    });
});
