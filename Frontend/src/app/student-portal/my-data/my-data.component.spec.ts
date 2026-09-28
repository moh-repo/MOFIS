import { MyDataComponent } from './my-data.component';
import { StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';

/**
 * My Data builds its sections from the record rather than spelling each field
 * out in the template.
 *
 * <p>That page is sixty-odd fields and most students have a value for only some
 * of them. What used to guard every row was a hand-written
 * `@if (field != null)`; the rules are now in one place, and these are the ones
 * that matter: a field the ministry has not filled in produces no row, and a
 * section whose fields are all absent is not built at all — an empty card with
 * a heading and nothing under it tells the reader their record is broken.
 */
describe('MyDataComponent sections', () => {

    let component: MyDataComponent;

    beforeEach(() => {
        const api = {} as StudentPortalApiService;
        const translation = { translate: (k: string) => k, getCurrentLanguage: () => 'en' } as unknown as TranslationService;
        component = new MyDataComponent(api, translation);
    });

    /** Sets the record under test; the shape is wider than any test needs. */
    function withRecord(student: Record<string, unknown> | null, finance: Record<string, unknown> | null = null): void {
        component.data = { student, finance } as any;
    }

    function sectionIds(): string[] {
        return component.sections.map(s => s.id);
    }

    function factsOf(id: string): Record<string, unknown> {
        const section = component.sections.find(s => s.id === id);
        const out: Record<string, unknown> = {};
        for (const fact of section?.facts ?? []) {
            out[fact.labelKey] = fact.value;
        }
        return out;
    }

    it('builds no sections at all from an empty record', () => {
        withRecord(null, null);

        expect(component.sections).toEqual([]);
    });

    it('drops a section whose every field is absent', () => {
        withRecord({ studentName: 'Layla' });

        // Personal has a name; nothing else does, so nothing else is built.
        expect(sectionIds()).toEqual(['personal']);
    });

    it('drops the individual fields that are absent', () => {
        withRecord({ studentName: 'Layla', studentId: 4321, studentGender: null, studentEductionName: undefined });

        expect(Object.keys(factsOf('personal'))).toEqual([
            'STUDENT_PORTAL.NAME',
            'STUDENT_PORTAL.STUDENT_ID'
        ]);
    });

    it('treats an empty or whitespace string as absent', () => {
        // The API returns these for a field nobody has filled in, and a row
        // reading "Major: " is worse than no row.
        withRecord({ studentName: 'Layla', studentEductionName: '', studentGenderName: '   ' });

        expect(Object.keys(factsOf('personal'))).toEqual(['STUDENT_PORTAL.NAME']);
    });

    it('keeps a zero, which is a value and not an absence', () => {
        withRecord({ studentName: 'Layla', studentId: 0 });

        expect(factsOf('personal')['STUDENT_PORTAL.STUDENT_ID']).toBe(0);
    });

    it('orders the sections so identity comes before the administration of it', () => {
        withRecord({
            studentName: 'Layla',
            studentPassportNumber: 'P1',
            studentDegreeName: 'BSc',
            studyCountryInName: 'Egypt',
            studentDelegateDecisionNo: 77,
            studentAgencyTypeName: 'Agency'
        }, { studentFinId: 9 });

        expect(sectionIds()).toEqual([
            'personal', 'passport', 'academic', 'location', 'delegation', 'management', 'finance'
        ]);
    });

    it('prefers the resolved lookup name over the raw id', () => {
        withRecord({ studentName: 'L', studentCountryId: 12, studentCountryName: 'Egypt' });

        expect(factsOf('personal')['STUDENT_PORTAL.COUNTRY']).toBe('Egypt');
    });

    it('falls back to the id when the lookup did not resolve', () => {
        // Better a number the office can act on than a blank where a value is.
        // It stays a number rather than becoming a string: the fact list sets
        // numerals in a tabular figure so ids and amounts line up in a column.
        withRecord({ studentName: 'L', studentCountryId: 12 });

        expect(factsOf('personal')['STUDENT_PORTAL.COUNTRY']).toBe(12);
    });

    it('shows the amount with its currency as one value', () => {
        withRecord({ studentAmount: 1500, studentAmountCurrencyName: 'EGP' });

        expect(factsOf('delegation')['STUDENT_PORTAL.AMOUNT']).toBe('1500 EGP');
    });

    it('shows the amount alone when no currency is recorded', () => {
        withRecord({ studentAmount: 1500 });

        expect(factsOf('delegation')['STUDENT_PORTAL.AMOUNT']).toBe('1500');
    });

    it('hides the university fee status when no fee applies', () => {
        withRecord({ studyUniversityFeeId: 0, studyUniversityFeeStatusId: 3 });

        // A status for a fee that is not charged is noise at best and
        // contradictory at worst.
        expect(factsOf('delegation')['STUDENT_PORTAL.UNIVERSITY_FEE_STATUS']).toBeUndefined();
        expect(factsOf('delegation')['STUDENT_PORTAL.UNIVERSITY_FEE']).toBe('COMMON.NO');
    });

    it('marks dates so the template can format them in the reader locale', () => {
        withRecord({ studentBirthdate: '2001-04-05', studentMidate: '2026-09-08T10:00:00Z' });

        const birthdate = component.sections
            .find(s => s.id === 'personal')?.facts
            .find(f => f.labelKey === 'STUDENT_PORTAL.BIRTHDATE');
        const modified = component.sections
            .find(s => s.id === 'management')?.facts
            .find(f => f.labelKey === 'STUDENT_PORTAL.MODIFICATION_DATE');

        expect(birthdate?.type).toBe('date');
        expect(modified?.type).toBe('datetime');
    });

    it('builds the finance section from the finance record alone', () => {
        withRecord(null, { studentFinId: 9, studentFinRecievedAmount: 250 });

        expect(sectionIds()).toEqual(['finance']);
    });

    it('shows the major in the reader\'s language, from the lookup names the server sends', () => {
        // The record keeps the major's Arabic name as free text beside its id and
        // the page printed that, so English readers saw "طب بشري" (QA SP-004).
        const major = { studentMajorId: 208, studentMajorName: 'طب بشري', studentMajorNameEn: 'Human Medicine' };
        withRecord(major);
        expect(factsOf('academic')['STUDENT_PORTAL.MAJOR']).toBe('Human Medicine');

        const arabic = new MyDataComponent({} as StudentPortalApiService,
            { translate: (k: string) => k, getCurrentLanguage: () => 'ar' } as unknown as TranslationService);
        arabic.data = { student: major, finance: null } as any;
        expect(arabic.sections.find(s => s.id === 'academic')?.facts[0].value).toBe('طب بشري');
    });

    it('never prints the raw finance status id', () => {
        // Nearly every finance row carries the column default 0, which names no
        // status row; the page read "Status: 0" (live QA M7). It says what the
        // finance-status page says: the row's name, else received or pending.
        withRecord(null, { studentFinId: 9, studyFinRecievedStatus: 0 });
        expect(factsOf('finance')['STUDENT_PORTAL.STATUS']).toBe('STUDENT_PORTAL.FINANCE_STATUS_PENDING');

        withRecord(null, { studentFinId: 9, studyFinRecievedStatus: 1, received: true });
        expect(factsOf('finance')['STUDENT_PORTAL.STATUS']).toBe('STUDENT_PORTAL.FINANCE_STATUS_RECEIVED');

        withRecord(null, { studentFinId: 9, studyFinRecievedStatus: 2, studyFinRecievedStatusName: 'لم يستلم', studyFinRecievedStatusNameEn: 'Not Received' });
        expect(factsOf('finance')['STUDENT_PORTAL.STATUS']).toBe('Not Received');
    });

    it('treats a lookup id of 0 as absent, not as a value', () => {
        // 0 is the "not set" default of every lookup column on the student
        // record and names no row: "Country: 0" tells the student nothing.
        withRecord({ studentName: 'L', studentCountryId: 0, studentGovId: 3 });

        expect(factsOf('personal')['STUDENT_PORTAL.COUNTRY']).toBeUndefined();
        expect(factsOf('personal')['STUDENT_PORTAL.GOVERNMENT']).toBe(3);
    });

    describe('notes and initials', () => {

        it('lists only the notes that have text', () => {
            withRecord({ studentNote1: 'First', studentNote2: '' });

            expect(component.notes).toEqual([{ labelKey: 'STUDENT_PORTAL.NOTE_1', text: 'First' }]);
        });

        it('takes two words of initials', () => {
            withRecord({ studentName: 'Layla Al Mansouri' });

            expect(component.studentInitials).toBe('LA');
        });

        it('reports a dash rather than crashing on a missing name', () => {
            withRecord({});

            expect(component.studentInitials).toBe('—');
        });
    });

    /** QA SP-004 / B3: ids and codes the page printed as they are stored. */
    it('names the currency and the fee status, and formats the quarters', () => {
        withRecord({
            studentAmount: 500, amountCurrency: 3, amountCurrencyName: 'دولار', amountCurrencyNameEn: 'US dollar',
            studyUniversityFeeId: 7, studyUniversityFeeStatusId: 2,
            studyUniversityFeeStatusName: 'مدفوع', studyUniversityFeeStatusNameEn: 'Paid',
            studentAddedQuarter: 20242, studentLastQuarter: 0
        });
        const facts = factsOf('delegation');

        expect(facts['STUDENT_PORTAL.AMOUNT']).toBe('500 US dollar');
        expect(facts['STUDENT_PORTAL.UNIVERSITY_FEE_STATUS']).toBe('Paid');
        expect(facts['STUDENT_PORTAL.ADDED_QUARTER']).toBe('2024 COMMON.QUARTER_SHORT2');
        expect(facts['STUDENT_PORTAL.LAST_QUARTER']).withContext('0 is "none", not a quarter').toBeUndefined();
    });

    /** Live QA L10 (24 Sep 2026): two packed codes the page printed raw. */
    it('formats the first-receipt quarter and the packed year-month key', () => {
        withRecord(
            { studentName: 'Layla', studentFirstReceiptOfDue: 20173 },
            { studentFinId: 2, studentFinRecievedYear: 2024, studentFinRecievedMonth: 2, studentFinRecievedYearMonth: 20242 }
        );

        expect(factsOf('delegation')['STUDENT_PORTAL.FIRST_RECEIPT']).toBe('2017 COMMON.QUARTER_SHORT3');
        const finance = factsOf('finance');
        expect(finance['STUDENT_PORTAL.RECEIVED_YEAR_MONTH']).toBe('2024 / 2');
        expect(finance['STUDENT_PORTAL.YEAR_MONTH']).toBe('2024 / 2');
    });

    it('leaves a year-month key it cannot read as it came, and drops an empty one', () => {
        withRecord(null, { studentFinId: 2, studentFinRecievedYearMonth: 'n/a' });
        expect(factsOf('finance')['STUDENT_PORTAL.YEAR_MONTH']).toBe('n/a');

        withRecord(null, { studentFinId: 2, studentFinRecievedYearMonth: null });
        expect(factsOf('finance')['STUDENT_PORTAL.YEAR_MONTH']).toBeUndefined();
    });
});
