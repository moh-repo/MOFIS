/**
 * Mirrors backend {@code StudyUniversityFee} (MOHECORE.STUDY_UNIVERSITY_FEE).
 * STUDY_UNIVERSITY_FEE_SOURCE_ID: 1=Wafar, 2=Ministry, 3=reallocated/other field, 4=other fee row.
 * STUDY_UNIVERSITY_FEE_SEMESTER_SCOPE_ID: 1=one semester, 2=two, 3=three or more.
 * STUDY_UNIVERSITY_FEE_YEAR_SCOPE_ID: 1=single year, 2=multiple years, 3=overlapping years.
 */
export class StudyUniversityFee {
    id?: number;
    /** Used by some legacy form bindings; persisted PK is {@link id}. */
    studyUniversityFeeId?: string | number;
    studentId?: number;
    studyUniversityFeeStudentName?: string;
    studyUniversityFeeFirstYear?: string | number;
    studyUniversityFeeAmount?: number;
    studyUniversityFeeYear?: string | number;
    studyUniversityFeeDiscount?: number;
    studyUniversityRealFeeAmount?: number;
    studyUniversityFeeCurrency?: number;
    studyUniversityFeePayDate?: Date | string | null;
    studyUniversityFeePayStatus?: number | string;
    /** 0=unset, 1=Wafar, 2=Ministry, 3=reallocated, 4=other university fee entry */
    studyUniversityFeeSourceId?: number;
    /** 0=unset, 1=one semester, 2=two semesters, 3=three or more */
    studyUniversityFeeSemesterScopeId?: number;
    /** 0=unset, 1=single year, 2=multiple years, 3=overlapping years */
    studyUniversityFeeYearScopeId?: number;
    /** 1=first semester, 2=second, … (stored for duplicate validation) */
    studyUniversityFeeSemesterNumber?: number;
    /** 0=unset, 1 = paid in student's name, 2 = paid directly to university */
    studyUniversityCheckPayorTypeId?: number;
    studyUniversityCheckNo?: string;
    studyUniversityCheckDate?: Date | string | null;
    studyUniversityCheckNotes?: string;
    studyUniversityCheckFormNumber?: string;
    studyUniversityFeeMidate?: Date | string | null;

    constructor() {
        this.id = undefined;
        this.studyUniversityFeeId = undefined;
        this.studentId = undefined;
        this.studyUniversityFeeStudentName = '';
        this.studyUniversityFeeFirstYear = '';
        this.studyUniversityFeeAmount = 0;
        this.studyUniversityFeeYear = '';
        this.studyUniversityFeeDiscount = 0;
        this.studyUniversityRealFeeAmount = 0;
        this.studyUniversityFeeCurrency = 0;
        this.studyUniversityFeePayDate = new Date();
        this.studyUniversityFeePayStatus = 0;
        this.studyUniversityFeeSourceId = 0;
        this.studyUniversityFeeSemesterScopeId = 0;
        this.studyUniversityFeeYearScopeId = 0;
        this.studyUniversityFeeSemesterNumber = 0;
        this.studyUniversityCheckPayorTypeId = 1;
        this.studyUniversityCheckNo = '';
        this.studyUniversityCheckDate = null;
        this.studyUniversityCheckNotes = '';
        this.studyUniversityCheckFormNumber = '';
        this.studyUniversityFeeMidate = new Date();
    }
}
