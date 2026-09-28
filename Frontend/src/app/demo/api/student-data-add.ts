
export interface StudentDocument {
    id: number;
    name: string;
    type: string;
    path: string;
    uploadDate: Date;
}

export class StudentDataAdd {

    // Assigned by the database when the student is approved; never typed in.
    studentId: number;
    
    // Basic Information
    studentName: string;
    studentGender: number; // Lookup from STUDENT_GENDER
    studentBirthdate: Date;
    studentBirthdatePlace: number; // Lookup from STUDENT_COUNTRY (changed from string)
    
    // Location Information
    studentGovId: number; // Lookup from STUDENT_GOV (related to birthdate place)
    studentMudId: number; // Lookup from STUDENT_GOV_MUD
    
    // Passport Information
    studentPassportNumber: string; // Standard number format
    studentPassportIssueDate: Date;
    studentPassportExpireDate: Date;
    studentPassportIssueCountryId: number; // Lookup from STUDENT_COUNTRY_ALL
    
    // Profile Picture
    picturePath?: string;

    // Computed age (years.months format, e.g. "23.4")
    studentAge?: string;
    
    // Academic Information
    studentCountryId: number; // Lookup from STUDENT_COUNTRY
    studentDegreeId: number; // Lookup from STUDENT_DEGREE
    studentMajorId: number; // Lookup from STUDENT_MAJOR
    // studentMajorName removed as per requirements
    
    // Delegation Information
    studentDelegationDecisionYear: number; // Standard number (changed from string)
    studentDelegateDecisionNo: number; // Standard number
    studentStudyNoOfYears: number; // Standard number
    studentCurrentLevelId: number; // Default is 1
    studentFirstReceiptOfDue: number; // Standard number
    studentAmount: number; // Currency field (plain number)
    studentAmountCurrencyId?: number; // Lookup from STUDY_CURRENCY_IN
    
    // Study Type Information
    studentStudyTypeId: number; // Lookup from STUDENT_STUDY_TYPE
    studentAgencyTypeId: number; // Lookup from STUDENT_AGENCY_TYPE
    studentMinistryTypeId: number; // Lookup from STUDENT_MINISTRY_TYPE (related to agency)
    studentUniversityTypeId: number; // Lookup from STUDENT_UNIVERSITY_TYPE (related to agency)
    
    // Quarter Information
    studentAddedQuarter: number; // Standard number
    studentLastQuarter: number; // Standard number
    currentQuarter: string; // YYYYQ code (year + quarter number), e.g. '20242'

    // Study Location Information
    studyCountryIn: number; // Lookup from STUDY_COUNTRY_IN
    syudyGovIn: number; // Lookup from SYUDY_GOV_IN (related to study country)
    studyUniversityIn: number; // Lookup from STUDY_UNIVERSITY_IN
    studyCollegeIn: number; // Lookup from STUDY_COLLEGE_IN (related to university)
    studyEducationIn: number; // Lookup from STUDY_EDUCATION_IN
    studyMajorIn: number; // Lookup from STUDY_MAJOR_IN (related to education)
    
    // Status and Fees
    studentStudyStatusId: number; // Lookup from STUDENT_STUDY_STATUS
    studyUniversityFeeId: number; // Yes/No list field
    studyUniversityFeeStatusId: number; // Fee amount (plain number)
    studyUniversityFeeStatusCurrencyId?: number; // Lookup from STUDY_CURRENCY_IN
    
    // Contact Information
    studentEmail?: string;
    studentCellphone?: string;
    studentHomeNumber?: string;
    studentEmergencyContact?: string;

    // Notes and Dates
    studentNote1: string; // Text field
    studentNote2: string; // Text field
    studentMidate: Date; // Insertion date
    
    // Documents
    documents?: StudentDocument[];
  
    constructor(){
      // Identity field
      this.studentId = 0;
      
      // Basic Information
      this.studentName = "";
      this.studentGender = 0;
      // Dates start empty. Defaulting them to "now" made a new student 0.0 years old
      // and put issue == expiry, so "expiry must be after issue" fired on step 1
      // before any passport was entered (live QA #7).
      this.studentBirthdate = null;
      this.studentBirthdatePlace = 0; // Changed from string to number
      
      // Location Information
      this.studentGovId = 0;
      this.studentMudId = 0;
      
      // Passport Information
      this.studentPassportNumber = "";
      this.studentPassportIssueDate = null;
      this.studentPassportExpireDate = null;
      this.studentPassportIssueCountryId = 0;
      
      // Academic Information
      this.studentCountryId = 0; // Changed from studentCountry object
      this.studentDegreeId = 0;
      this.studentMajorId = 0;
      
      // Delegation Information
      this.studentDelegationDecisionYear = 0; // Changed from string to number
      this.studentDelegateDecisionNo = 0;
      this.studentStudyNoOfYears = 0;
      this.studentCurrentLevelId = 1; // Default is 1
      this.studentFirstReceiptOfDue = 0;
      this.studentAmount = 0;
      this.studentAmountCurrencyId = 0;
      
      // Study Type Information
      this.studentStudyTypeId = 0;
      this.studentAgencyTypeId = 0;
      this.studentMinistryTypeId = 0;
      this.studentUniversityTypeId = 0;
      
      // Quarter Information
      this.studentAddedQuarter = 0;
      this.studentLastQuarter = 0;
      this.currentQuarter = '';

      // Study Location Information
      this.studyCountryIn = 0;
      this.syudyGovIn = 0;
      this.studyUniversityIn = 0;
      this.studyCollegeIn = 0;
      // null, not 0: id 0 is a real row in STUDY_EDUCATION_IN and in
      // STUDENT_STUDY_STATUS, so 0 cannot also mean "nothing picked" (live QA #24).
      this.studyEducationIn = null;
      this.studyMajorIn = 0;

      // Status and Fees
      this.studentStudyStatusId = null;
      this.studyUniversityFeeId = 0;
      this.studyUniversityFeeStatusId = 0;
      this.studyUniversityFeeStatusCurrencyId = 0;
      
      // Notes and Dates
      this.studentNote1 = "";
      this.studentNote2 = "";
      this.studentMidate = new Date();
    }
}
