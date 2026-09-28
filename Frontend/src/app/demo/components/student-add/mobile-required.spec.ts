import { StudentAddComponent } from './student-add.component';

/**
 * Live QA L9, decision D11: the contact step let a student be saved with no mobile
 * number. It is now required for a new student, and at the next edit of a student
 * stored without one; the server refuses the same.
 */
describe('Add Student — the mobile number is required (L9)', () => {

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const step = (student: any, isEditMode = false): any => {
        const proto = StudentAddComponent.prototype as any;
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const self: any = {
            student, isEditMode, loadedPhones: { studentCellphone: student.studentCellphone },
            EMAIL_PATTERN: /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        };
        for (const m of ['phoneUnchanged', 'phoneInvalid']) {
            self[m] = proto[m].bind(self);
        }
        self.ok = proto.validateContactFields.call(self);
        return self;
    };

    it('refuses a new student without a mobile number', () => {
        const self = step({ studentCellphone: '  ' });
        expect(self.ok).toBeFalse();
        expect(self.cellphoneMissing).toBeTrue();
    });

    it('accepts a new student with one', () => {
        const self = step({ studentCellphone: '+962 79 123 4567' });
        expect(self.ok).toBeTrue();
        expect(self.cellphoneMissing).toBeFalse();
    });

    it('asks for one when a student stored without it is edited', () => {
        const self = step({ studentCellphone: null }, true);
        expect(self.ok).toBeFalse();
        expect(self.cellphoneMissing).toBeTrue();
    });
});
