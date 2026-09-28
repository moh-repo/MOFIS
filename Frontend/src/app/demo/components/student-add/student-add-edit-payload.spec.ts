import { StudentDataAdd } from '../../api/student-data-add';
import { buildStudentPayload, changedKeys } from './student-add-payload';

/**
 * Add Student in edit mode sends only what changed (live QA H3, 23 Sep 2026).
 *
 * Editing student 1003's e-mail through the wizard produced an approval request that
 * cleared nine fields the form had never shown — agency, ministry, study and
 * university type, birthplace, both passport dates, the passport country and the
 * amount currency — and the e-mail change itself was dropped. The wizard now
 * snapshots the loaded record in wire form and submits the keys that differ.
 */
describe('StudentAddComponent edit payload', () => {

    /** What GET /api/v1/studentdata/1003 gives once the wizard has normalised it. */
    function loaded(): StudentDataAdd {
        const s = new StudentDataAdd();
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const m: any = s;
        m.studentId = 1003;
        m.studentName = 'QA-TEST L4 Student';
        m.studentEmail = 'old@example.org';
        m.studentCellphone = '0777000000';
        m.studentPassportNumber = 'P-1003';
        m.studentAgencyTypeId = 1;
        m.studentMinistryTypeId = 1;
        m.studentStudyTypeId = 2;
        m.studentUniversityTypeId = 2;
        m.studentBirthdatePlace = 1;                 // the DB text "1", made a number on load
        m.studentPassportIssueCountryId = 1;
        m.studentAmountCurrencyId = 1;               // the amountCurrency alias the wizard binds
        m.studyUniversityFeeStatusCurrencyId = 0;
        m.studentPassportIssueDate = new Date('2026-05-26T22:19:49.557Z');
        m.studentPassportExpireDate = new Date('2026-05-30T00:00:00.000Z');
        m.studentBirthdate = new Date('2000-01-01T12:00:00.000Z');
        m.studyEducationIn = 0;
        m.studentStudyStatusId = 1;
        m.studentCountryId = 1;
        return s;
    }

    const opts = { isEditMode: true, studentId: 1003 };

    it('sends only the id and the e-mail when only the e-mail changed', () => {
        const record = loaded();
        const snapshot = buildStudentPayload(record, opts);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (record as any).studentEmail = 'new@example.org';

        const sent = changedKeys(snapshot, buildStudentPayload(record, opts));

        expect(Object.keys(sent).sort()).toEqual(['studentEmail', 'studentId']);
        expect(sent['studentEmail']).toBe('new@example.org');
        expect(sent['studentId']).toBe(1003);
    });

    it('never mentions the nine fields the probe cleared when they were not touched', () => {
        const record = loaded();
        const snapshot = buildStudentPayload(record, opts);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (record as any).studentName = 'Renamed';

        const sent = changedKeys(snapshot, buildStudentPayload(record, opts));

        for (const untouched of ['amountCurrency', 'studentAgencyTypeId', 'studentMinistryTypeId',
            'studentStudyTypeId', 'studentUniversityTypeId', 'studentBirthdatePlace',
            'studentPassportIssueDate', 'studentPassportExpireDate', 'studentPassportIssueCountryId']) {
            expect(untouched in sent).withContext(untouched).toBeFalse();
        }
        expect(Object.keys(sent).sort()).toEqual(['studentId', 'studentName']);
    });

    it('an untouched edit sends the id alone, which the server answers as "no changes"', () => {
        const record = loaded();
        const snapshot = buildStudentPayload(record, opts);

        const sent = changedKeys(snapshot, buildStudentPayload(record, opts));

        expect(Object.keys(sent)).toEqual(['studentId']);
    });

    it('a field cleared on purpose is still sent as cleared', () => {
        const record = loaded();
        const snapshot = buildStudentPayload(record, opts);
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (record as any).studentPassportNumber = '';

        const sent = changedKeys(snapshot, buildStudentPayload(record, opts));

        expect(Object.keys(sent).sort()).toEqual(['studentId', 'studentPassportNumber']);
        expect(sent['studentPassportNumber']).toBe('');
    });

    it('a re-picked date is sent as the day picked; the untouched one is not sent', () => {
        const record = loaded();
        const snapshot = buildStudentPayload(record, opts);
        // What the picker yields: local midnight of the day chosen.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (record as any).studentPassportExpireDate = new Date(2027, 4, 30);

        const sent = changedKeys(snapshot, buildStudentPayload(record, opts));

        expect(Object.keys(sent).sort()).toEqual(['studentId', 'studentPassportExpireDate']);
        expect(sent['studentPassportExpireDate']).toBe('2027-05-30T12:00:00.000Z');
    });

    it('a new student still sends the whole record, without an id', () => {
        const record = loaded();
        const full = buildStudentPayload(record, { isEditMode: false, studentId: 0 });

        expect('studentId' in full).toBeFalse();
        expect(full['amountCurrency']).toBe(1);
        expect(full['studentAgencyTypeId']).toBe(1);
        expect('studentAmountCurrencyId' in full).withContext('UI-only alias is dropped').toBeFalse();
        expect(full['studentCountryId']).toBe(1);
    });
});
