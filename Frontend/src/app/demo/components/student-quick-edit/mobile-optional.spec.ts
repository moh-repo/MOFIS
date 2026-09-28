import { StudentQuickEditComponent } from './student-quick-edit.component';

/**
 * Live QA L9 / D11, confirmed 26 Sep 2026 (FIX-PLAN P5-9): Quick Edit does not require
 * the mobile number — the student form does. A student with none (every student on dev)
 * can still be changed here; a number that is typed is still checked for its shape.
 */
describe('Quick Edit — the mobile number is optional', () => {

    function screen(original: any): any {
        const component = Object.create(StudentQuickEditComponent.prototype) as any;
        component.translationService = { translate: (k: string) => k };
        component.messageService = { add: jasmine.createSpy('add') };
        component.editOriginal = original;
        return component;
    }

    it('an empty mobile number on a student who has none passes', () => {
        const component = screen({ studentCellphone: null });
        expect(component.validatePhoneFields({ studentCellphone: '' })).toBeTrue();
        expect(component.messageService.add).not.toHaveBeenCalled();
    });

    it('a typed number that is too short is still refused', () => {
        const component = screen({ studentCellphone: null });
        expect(component.validatePhoneFields({ studentCellphone: '12' })).toBeFalse();
        expect(component.messageService.add).toHaveBeenCalled();
    });
});
