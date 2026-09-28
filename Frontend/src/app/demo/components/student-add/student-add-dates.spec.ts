import { StudentAddComponent } from './student-add.component';
import { StudentDataAdd } from '../../api/student-data-add';

/**
 * Add Student: the defaults and date handling behind live QA #7 and #24.
 *
 * #7  — birth date and both passport dates defaulted to "now", so a new student was
 *       0.0 years old and "expiry must be after issue" fired before any passport was
 *       entered.
 * #24 — a picked day went out as the previous day's 21:00Z, and id 0 (a real row in
 *       the education and study-status lists) was dropped by `|| null`.
 */
describe('StudentAddComponent dates and defaults', () => {

    it('starts a new student with no dates', () => {
        const s = new StudentDataAdd();
        expect(s.studentBirthdate).toBeNull();
        expect(s.studentPassportIssueDate).toBeNull();
        expect(s.studentPassportExpireDate).toBeNull();
    });

    it('starts education and study status as "nothing picked", not 0', () => {
        const s = new StudentDataAdd();
        expect(s.studyEducationIn).toBeNull();
        expect(s.studentStudyStatusId).toBeNull();
    });

    it('sends a picked day as noon UTC of that same calendar day', () => {
        const picked = new Date(2000, 0, 1); // what the picker yields: local midnight
        expect(StudentAddComponent.calendarDay(picked)).toBe('2000-01-01T12:00:00.000Z');
    });

    it('leaves a loaded date that was not re-picked exactly as it came', () => {
        const loaded = new Date('1984-05-25T00:00:00.000Z');
        if (loaded.getHours() === 0) {
            // Only on a machine whose zone is UTC does a stored midnight read as local midnight.
            pending('the machine runs in UTC');
        }
        expect(StudentAddComponent.calendarDay(loaded)).toBe(loaded);
    });

    it('keeps an empty date empty', () => {
        expect(StudentAddComponent.calendarDay(null)).toBeNull();
        expect(StudentAddComponent.calendarDay(undefined)).toBeNull();
        expect(StudentAddComponent.calendarDay('not a date')).toBeNull();
    });
});
