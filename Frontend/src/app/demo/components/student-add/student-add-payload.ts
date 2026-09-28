import { StudentDataAdd } from '../../api/student-data-add';

/**
 * What the Add Student wizard sends, and how an edit is reduced to what changed
 * (live QA H3, 23 Sep 2026).
 *
 * The wizard used to submit its whole model on an edit. Nine columns it had never
 * loaded — agency, ministry, study and university type, birthplace, the passport
 * dates and issuing country, the amount currency — went out as empty, reached the
 * approver as "cleared" and were written as NULL/0 once approved. The server keeps
 * every column an update does not mention (the controller overlays the payload onto
 * the stored record), so an edit now sends only the keys whose wire form differs from
 * the snapshot taken when the record was loaded, plus the id. A field a restricted
 * user cannot even see (masked to null by the server) is therefore never echoed back
 * as a clear either.
 *
 * Pure functions, so they are testable without the 15-dependency component.
 */

export interface StudentPayloadOptions {
    isEditMode: boolean;
    /** The id in the route; falls back to the model's own. */
    studentId: number;
}

/**
 * A date-only value as noon UTC of the calendar day the user picked. The picker
 * yields local midnight, which serialises as the previous day for any zone east
 * of UTC; noon keeps the day for every zone within ±11 h.
 *
 * Only a value at local midnight — what the picker produces — is moved. A date
 * loaded from the server and left alone goes back exactly as it came, so an edit
 * does not show an untouched date as changed to the approver.
 */
export function calendarDay(value: Date | string | null | undefined): Date | string | null {
    if (!value) {
        return null;
    }
    const d = value instanceof Date ? value : new Date(value);
    if (isNaN(d.getTime())) {
        return null;
    }
    const pickedInThePicker = d.getHours() === 0 && d.getMinutes() === 0
        && d.getSeconds() === 0 && d.getMilliseconds() === 0;
    if (!pickedInThePicker) {
        return value;
    }
    return new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), 12)).toISOString();
}

/**
 * The wizard's model in the shape the server takes: ids normalised, dates as calendar
 * days, the UI-only currency ids mapped onto the entity's columns, nested and alias
 * keys removed, and the id present only on an edit.
 */
export function buildStudentPayload(student: StudentDataAdd, opts: StudentPayloadOptions): Record<string, unknown> {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const s: any = student ?? {};

    const studentData: Record<string, unknown> = {
        ...s,
        studentCountryId: s.studentCountryId || null,
        studentCurrentLevelId: s.studentCurrentLevelId || 1,
        studentBirthdatePlace: s.studentBirthdatePlace ? s.studentBirthdatePlace.toString() : null,
        studentDelegationDecisionYear: s.studentDelegationDecisionYear ? s.studentDelegationDecisionYear.toString() : null,
        studentPassportIssueCountryId: s.studentPassportIssueCountryId || null,
        studyCountryIn: s.studyCountryIn || null,
        syudyGovIn: s.syudyGovIn || null,
        studyUniversityIn: s.studyUniversityIn || null,
        studyCollegeIn: s.studyCollegeIn || null,
        // `??`, not `||`: id 0 is a real row in these two lists (live QA #24).
        studyEducationIn: s.studyEducationIn ?? null,
        studyMajorIn: s.studyMajorIn || null,
        studentDegreeId: s.studentDegreeId || null,
        studentMajorId: s.studentMajorId || null,
        studentStudyStatusId: s.studentStudyStatusId ?? null,
        studentStudyTypeId: s.studentStudyTypeId || null,
        studentUniversityTypeId: s.studentUniversityTypeId || null,
        studentAgencyTypeId: s.studentAgencyTypeId || null,
        studentMinistryTypeId: s.studentMinistryTypeId || null,
        studentGovId: s.studentGovId || null,
        studentMudId: s.studentMudId || null,
        // Calendar dates, not moments: sent at noon UTC of the day picked, so the
        // same day is read back in every time zone. Local midnight went out as the
        // previous day's 21:00Z from here (live QA #24).
        studentBirthdate: calendarDay(s.studentBirthdate),
        studentPassportIssueDate: calendarDay(s.studentPassportIssueDate),
        studentPassportExpireDate: calendarDay(s.studentPassportExpireDate)
    };

    // Normalize fields for backend
    // - Map currency IDs to backend columns
    // - Convert boolean to number (1/0)
    // - Remove nested objects and non-updatable fields
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const normalizedData: any = { ...studentData };
    normalizedData.amountCurrency = s.studentAmountCurrencyId || 0;
    normalizedData.universityFeeStatusCurrency = s.studyUniversityFeeStatusCurrencyId || 0;
    if (typeof s.studyUniversityFeeId === 'boolean') {
        normalizedData.studyUniversityFeeId = s.studyUniversityFeeId ? 1 : 0;
    }
    if (normalizedData.studentCountry) {
        delete normalizedData.studentCountry;
    }
    // UI-only fields that do not exist in the backend model (always removed, even if 0/null)
    for (const uiOnly of ['studentAmountCurrencyId', 'studyUniversityFeeStatusCurrencyId', 'studyGovIn']) {
        if (Object.prototype.hasOwnProperty.call(normalizedData, uiOnly)) {
            delete normalizedData[uiOnly];
        }
    }
    // For update mode, remove non-updatable fields to avoid Hibernate errors
    if (opts.isEditMode && normalizedData.studentCountryId) {
        delete normalizedData.studentCountryId;
    }

    /*
     * STUDENT_ID: a new student gets its id from the database when approved (there is
     * no manual id any more, DEC-02). Edits must send it so the approval stores the
     * EntityId the execution needs.
     */
    delete normalizedData.studentId;
    if (opts.isEditMode) {
        const editId = Number(opts.studentId) || Number(s?.studentId) || 0;
        if (editId > 0) {
            normalizedData.studentId = editId;
        }
    }
    return normalizedData as Record<string, unknown>;
}

/** The value as it would travel: `undefined` is absent, a Date is its ISO text. */
function wireForm(value: unknown): string | undefined {
    return value === undefined ? undefined : JSON.stringify(value ?? null);
}

/**
 * The keys of {@code current} whose wire form differs from {@code snapshot}, plus the
 * id. A key that was loaded and is now absent comes back as `undefined`, which
 * JSON.stringify drops, so the server keeps its stored value; a field the user
 * cleared on purpose (an empty string, a null) is sent, so intentional clears still
 * work.
 */
export function changedKeys(snapshot: Record<string, unknown>, current: Record<string, unknown>): Record<string, unknown> {
    const changed: Record<string, unknown> = {};
    const keys = new Set<string>([...Object.keys(snapshot), ...Object.keys(current)]);
    for (const key of keys) {
        if (wireForm(current[key]) !== wireForm(snapshot[key])) {
            changed[key] = current[key];
        }
    }
    if (current['studentId'] !== undefined) {
        changed['studentId'] = current['studentId'];
    }
    return changed;
}
