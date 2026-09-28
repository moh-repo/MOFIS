import { Component, OnInit, OnDestroy, Input, Output, EventEmitter, ViewChild, ViewChildren, ElementRef, QueryList } from '@angular/core';
import { NgModel } from '@angular/forms';
import { MessageService, ConfirmationService } from 'primeng/api';
import { FileUpload } from 'primeng/fileupload';
import { StudentDataAdd } from '../../api/student-data-add';
import { StudentdataAddService } from '../../service/studentdata-add.service';
import { StudentCountry } from '../../api/student-country';
import { studentCountryService } from '../../service/studentcountry.service';
import { Router, ActivatedRoute } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { TranslationService } from '../../service/translation.service';
import { LookupService, LookupItem } from '../../service/lookup.service';
import { forkJoin, Subject } from 'rxjs';
import { skip, takeUntil } from 'rxjs/operators';
import { DocumentTypeService } from '../../service/document-type.service';
import { DocumentType } from '../../api/document-type';
import { StudentDocumentService, StudentDocument } from '../../service/student-document.service';
import { StudentNameValidationService } from '../../service/student-name-validation.service';
import { StudentDraftService } from '../../service/student-draft.service';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { StudentdataService } from '../../service/studentdata.service';
import { StudentParentDto, StudentRelativeDto, StudentRelativeSearchDto } from '../../api/student-family';
import { isValidPhone, normalizePhone, PHONE_MAX_LENGTH } from '../../service/phone-number.util';
import { HttpErrorResponse } from '@angular/common/http';
import { buildStudentPayload, calendarDay as payloadCalendarDay, changedKeys } from './student-add-payload';

/** The contact numbers the digit-count rule applies to. */
const PHONE_FIELDS = ['studentCellphone', 'studentHomeNumber', 'studentEmergencyContact'] as const;
type PhoneField = typeof PHONE_FIELDS[number];

interface RelativeRowEdit extends StudentRelativeDto {
    selectedStudent?: StudentRelativeSearchDto | null;
    searchText?: string;
}

/**
 * Scopes the save prompt to this component's own <p-confirmdialog>. The wizard is embedded
 * in the students page, which runs a separate ConfirmationService instance and dialog —
 * without a key the two would answer for each other.
 */
export const STUDENT_SAVE_CONFIRM_KEY = 'studentSaveConfirm';

@Component({
    selector: 'app-student-add',
    templateUrl: './student-add.component.html',
    styleUrls: ['./student-add.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class StudentAddComponent implements OnInit, OnDestroy {
    /** Embedded in modal (students list): skip route-based boot and use {@link prepareDialog}. */
    @Input() dialogMode = false;
    /** Emitted after successful save in {@link dialogMode}; argument is resolved student ID when known */
    @Output() dialogSaved = new EventEmitter<number>();
    /** Emitted when user cancels/closes modal flow without saving from this handler */
    @Output() dialogCancelled = new EventEmitter<void>();

    student: StudentDataAdd = new StudentDataAdd();
    submitted: boolean = false;
    countries: StudentCountry[] = [];
    activeIndex: number = 0;
    isEditMode: boolean = false;
    studentId: number = 0;
    loading: boolean = false;

    steps: MenuItem[] = [];

    studentPicture: any;
    previewImageSrc: string = '';
    existingPicturePath: string = '';
    /** True when no photo could be loaded (primary endpoint and document fallback both failed) */
    profilePhotoUnreachable = false;
    private _photoBlobUrl: string | null = null;
    documents: any[] = [];
    uploadedFiles: any[] = [];

    /** Type recorded for the profile picture when it is also stored as a document. */
    readonly profilePictureDocumentType = 'PROFILE_PICTURE';

    /** Set to open the cropper; cleared when it closes. */
    pictureToCrop: File | null = null;
    /** Uncropped pick, retained so re-cropping does not recompress an earlier crop. */
    originalPicture: File | null = null;

    readonly saveConfirmKey = STUDENT_SAVE_CONFIRM_KEY;
    existingDocuments: StudentDocument[] = [];
    documentTypes: DocumentType[] = [];
    docToReplace: StudentDocument | null = null;
    @ViewChild('docReplaceInput') docReplaceInput!: ElementRef<HTMLInputElement>;
    @ViewChild('documentsUpload') documentsUpload?: FileUpload;
    /** The wizard's fields; see persistDraft. */
    @ViewChildren(NgModel) private fieldModels?: QueryList<NgModel>;
    /** The user has changed a field of this wizard, so leaving it keeps a draft. */
    private userEdited = false;
    /** The student was submitted; nothing of it is a draft any more. */
    private draftFinished = false;
    private destroy$ = new Subject<void>();

    // Lookup data arrays
    genderOptions: LookupItem[] = [];
    countryOptions: LookupItem[] = [];
    countriesAllOptions: LookupItem[] = [];
    /** STUDENT_COUNTRY rows — the table the passport-issue country FK references. */
    passportIssueCountryOptions: LookupItem[] = [];
    studentDegreeOptions: LookupItem[] = [];
    studentMajorOptions: LookupItem[] = [];
    currentLevelOptions: LookupItem[] = [];
    studyStatusOptions: LookupItem[] = [];
    studentStudyTypeOptions: LookupItem[] = [];
    universityTypeOptions: LookupItem[] = [];
    agencyTypeOptions: LookupItem[] = [];
    ministryTypeOptions: LookupItem[] = [];
    familyRelationshipOptions: LookupItem[] = [];
    parentRows: StudentParentDto[] = [];
    relativeRows: RelativeRowEdit[] = [];
    relativeSearchResults: StudentRelativeSearchDto[] = [];
    governmentOptions: LookupItem[] = [];
    municipalityOptions: LookupItem[] = [];
    selectedGovernment?: LookupItem;
    selectedMunicipality?: LookupItem;

    // Study location lookup arrays
    studyCountryInOptions: LookupItem[] = [];
    studyGovInOptions: LookupItem[] = [];
    studyUniversityInOptions: LookupItem[] = [];
    studyCollegeInOptions: LookupItem[] = [];
    studyEducationInOptions: LookupItem[] = [];
    studyMajorInOptions: LookupItem[] = [];
    studyCurrencyOptions: LookupItem[] = [];
    /** US dollar row id; a new student's amounts start in it (review item S3). */
    private defaultCurrencyId: number | null = null;

    // Student study status
    studentStudyStatusOptions: LookupItem[] = [];
    // Conditional flags for displaying fields after agency selection
    showMinistryField: boolean = false;
    showUniversityField: boolean = false;

    // Yes/No options
    yesNoOptions: LookupItem[] = [];

    statusOptions: LookupItem[] = [];

    // Quarter split UI state (year + quarter stored separately, combined into studentAddedQuarter / studentLastQuarter)
    addedQuarterYear: number | null = null;
    addedQuarterQ: number | null = null;
    lastQuarterYear: number | null = null;
    lastQuarterQ: number | null = null;
    currentQuarterYear: number | null = null;
    currentQuarterQ: number | null = null;
    quarterOptions: { label: string; value: number }[] = [];

    // Study years lookup loading flag
    studyYearsLookupLoading = false;


    // Duplicate validation properties
    showDuplicateDialog: boolean = false;
    duplicateStudents: StudentDataAdd[] = [];
    checkingDuplicate: boolean = false;
    isPendingApproval: boolean = false;

    constructor(
        private studentAddService: StudentdataAddService,
        private countryService: studentCountryService,
        private messageService: MessageService,
        private router: Router,
        private route: ActivatedRoute,
        public translationService: TranslationService,
        private lookupService: LookupService,
        private documentTypeService: DocumentTypeService,
        private studentDocumentService: StudentDocumentService,
        private nameValidationService: StudentNameValidationService,
        private permissionService: UmsPermissionService,
        private studentdataService: StudentdataService,
        private confirmationService: ConfirmationService,
        private draftService: StudentDraftService
    ) { }

    ngOnInit() {
        this.loadAllLookupData();
        this.initializeSteps();

        // Subscribe to language changes — skip(1) avoids double-load on init
        this.translationService.currentLanguage$
            .pipe(skip(1), takeUntil(this.destroy$))
            .subscribe(() => {
                this.initializeSteps();
                this.loadAllLookupData();
            });

        // Check if we're in edit mode via route (full-page wizard only)
        if (!this.dialogMode) {
            this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
                if (params['id']) {
                    this.isEditMode = true;
                    this.studentId = +params['id'];
                    this.loadStudentData(this.studentId);
                    this.checkPendingStatus(this.studentId);
                } else {
                    this.offerDraft();
                }
            });
        }

        // A reload does not run ngOnDestroy, and a reload is the loss the draft
        // exists for (QA C7 / N1).
        window.addEventListener('beforeunload', this.persistDraft);
    }

    /**
     * Keeps whatever has been typed (decision DEC-13 (a)).
     *
     * <p>An arrow function so it can be handed to addEventListener and removed
     * again by the same reference. Edits are not drafted: the record already
     * exists, and a stale copy of it competing with the server's is a worse
     * problem than the one being solved.
     */
    private readonly persistDraft = (): void => {
        if (this.isEditMode || this.dialogMode || this.draftFinished) { return; }
        // Only a wizard the user has changed is a draft (round-6 QA R6-07). The model is
        // never blank - the currency and other defaults are filled in on open - so a visit
        // with nothing typed saved one, and "Unfinished student" was offered next time. A
        // field turns dirty only on the user's own change; each step's fields go with the
        // step, so once any has been dirty the flag stays.
        this.userEdited ||= this.fieldModels?.some((model) => !!model.dirty) ?? false;
        if (!this.userEdited) { return; }
        this.draftService.save(this.student, this.activeIndex);
    };

    /** Offers to bring back an unfinished wizard, and forgets it if declined. */
    private offerDraft(): void {
        const draft = this.draftService.load();
        if (!draft) { return; }

        this.confirmationService.confirm({
            header: this.translationService.translate('STUDENT_ADD.DRAFT_FOUND_TITLE'),
            message: this.translationService.translate('STUDENT_ADD.DRAFT_FOUND_MESSAGE'),
            icon: 'pi pi-history',
            acceptLabel: this.translationService.translate('STUDENT_ADD.DRAFT_RESTORE'),
            rejectLabel: this.translationService.translate('STUDENT_ADD.DRAFT_DISCARD'),
            accept: () => {
                // Assigned field by field onto a fresh model, so a draft written
                // by an older build cannot remove a field this one expects.
                this.student = Object.assign(new StudentDataAdd(), draft.student);
                this.activeIndex = draft.step ?? 0;
                // What was restored is the user's own typing; it stays a draft.
                this.userEdited = true;
                this.messageService.add({
                    severity: 'info',
                    summary: this.translationService.translate('COMMON.INFO'),
                    detail: this.translationService.translate('STUDENT_ADD.DRAFT_RESTORED')
                });
            },
            reject: () => this.draftService.clear()
        });
    }

    /**
     * Initialize embedded wizard when opening modal: pass `null`/`undefined`/0 for new student,
     * or a student id for edit mode.
     */
    prepareDialog(studentIdToEdit: number | null | undefined): void {
        this.dialogMode = true;
        this.resetWizardState();

        const sid =
            typeof studentIdToEdit === 'number' && Number.isFinite(studentIdToEdit) && studentIdToEdit > 0
                ? studentIdToEdit
                : null;
        if (sid != null) {
            this.isEditMode = true;
            this.studentId = sid;
            this.loadStudentData(this.studentId);
            this.checkPendingStatus(this.studentId);
        } else {
            this.isEditMode = false;
            this.studentId = 0;
            this.applyDefaultCurrency();
        }
    }

    private resetWizardState(): void {
        this.student = new StudentDataAdd();
        this.submitted = false;
        this.activeIndex = 0;
        this.loading = false;
        this.studentPicture = undefined;
        this.previewImageSrc = '';
        this.existingPicturePath = '';
        this.profilePhotoUnreachable = false;
        this._revokeBlobUrl();
        this.documents = [];
        this.uploadedFiles = [];
        this.existingDocuments = [];
        this.showDuplicateDialog = false;
        this.duplicateStudents = [];
        this.checkingDuplicate = false;
        this.isPendingApproval = false;
        this.selectedGovernment = undefined;
        this.selectedMunicipality = undefined;
        this.showMinistryField = false;
        this.showUniversityField = false;
        this.parentRows = [];
        this.relativeRows = [];
        this.loadedPhones = {};
        this.cellphoneInvalid = this.homeNumberInvalid = this.emergencyContactInvalid = this.emailInvalid = false;
        this.cellphoneMissing = false;
    }

    checkPendingStatus(id: number) {
        this.studentdataService.getPendingStudentIds().subscribe(ids => {
            this.isPendingApproval = ids.includes(id.toString());
            if (this.isPendingApproval) {
                this.messageService.add({
                    severity: 'warn',
                    summary: this.translationService.translate('STUDENT_ADD.MSG_PENDING_APPROVAL'),
                    detail: this.translationService.translate('STUDENT_ADD.MSG_PENDING_DETAIL'),
                    life: 10000
                });
            }
        });
    }

    ngOnDestroy() {
        this.persistDraft();
        window.removeEventListener('beforeunload', this.persistDraft);
        this.destroy$.next();
        this.destroy$.complete();
        this._revokeBlobUrl();
    }

    private _revokeBlobUrl(): void {
        if (this._photoBlobUrl) {
            URL.revokeObjectURL(this._photoBlobUrl);
            this._photoBlobUrl = null;
        }
    }

    loadAllLookupData() {
        this.lookupService.getStudentGenders().subscribe({
            next: data => { this.genderOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_GENDER') }); }
        });

        this.lookupService.getCountries().subscribe({
            next: data => {
                this.countryOptions = data;
                this.passportIssueCountryOptions = data;
                if (!this.isEditMode) {
                    const yemen = data.find(c => c.name?.includes('اليمن') || c.name?.toLowerCase().includes('yemen'));
                    if (yemen) {
                        this.student.studentCountryId = yemen.value;
                        this.student.studentPassportIssueCountryId = yemen.value;
                        this.onStudentCountryChange();
                    }
                }
            },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_COUNTRY') }); }
        });

        this.lookupService.getCountriesAll().subscribe({
            next: data => {
                this.countriesAllOptions = data;
                if (!this.isEditMode) {
                    const yemenItem = data.find(c => c.id === 1);
                    if (yemenItem) {
                        this.student.studentBirthdatePlace = yemenItem.value;
                    }
                }
            },
            error: () => {}
        });

        this.lookupService.getStudentDegrees().subscribe({
            next: data => { this.studentDegreeOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_DEGREE') }); }
        });

        this.lookupService.getStudentMajors().subscribe({
            next: data => { this.studentMajorOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_MAJOR') }); }
        });

        this.lookupService.getCurrentLevels().subscribe({
            next: data => { this.currentLevelOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_LEVEL') }); }
        });

        this.lookupService.getStudyStatuses().subscribe({
            next: data => { this.studyStatusOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_STUDY_STATUS') }); }
        });

        this.lookupService.getStudentStudyTypes().subscribe({
            next: data => { this.studentStudyTypeOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_STUDY_TYPE') }); }
        });

        this.lookupService.getUniversityTypes().subscribe({
            next: data => { this.universityTypeOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_UNIVERSITY_TYPE') }); }
        });

        this.lookupService.getAgencyTypes().subscribe({
            next: data => { this.agencyTypeOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_AGENCY_TYPE') }); }
        });

        this.lookupService.getStudentStudyStatuses().subscribe({
            next: data => {
                this.statusOptions = data.map(item => ({ ...item, label: item.name || item.label, value: item.id || item.value }));
                this.studentStudyStatusOptions = data;
            },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_STUDENT_STUDY_STATUS') }); }
        });

        this.lookupService.getMinistryTypes().subscribe({
            next: data => { this.ministryTypeOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_MINISTRY_TYPE') }); }
        });

        this.lookupService.getFamilyRelationshipLookupItems().subscribe({
            next: data => { this.familyRelationshipOptions = data; },
            error: () => { this.familyRelationshipOptions = []; }
        });

        // governmentOptions starts empty; populated lazily by onStudentCountryChange()
        // or by restoreCascadeOptions() in edit mode — no unfiltered pre-load.

        this.lookupService.getMunicipalities().subscribe({
            next: data => { this.municipalityOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_MUNICIPALITY') }); }
        });

        this.lookupService.getStudyCountriesIn().subscribe({
            next: data => {
                const allowedIds = this.permissionService.getAllowedCountryIds();
                this.studyCountryInOptions = allowedIds.length > 0
                    ? data.filter(c => allowedIds.includes(c.id))
                    : data;
            },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_STUDY_COUNTRY') }); }
        });

        this.lookupService.getStudyEducationsIn().subscribe({
            next: data => { this.studyEducationInOptions = data; },
            error: () => { this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_STUDY_EDUCATION') }); }
        });

        this.lookupService.getStudyCurrencies().subscribe({
            next: data => { this.studyCurrencyOptions = data; },
            error: () => { }
        });

        this.lookupService.getCurrencyIdByIso('USD').subscribe(id => {
            this.defaultCurrencyId = id;
            this.applyDefaultCurrency();
        });

        this.lookupService.getYesNoOptions().subscribe({
            next: data => { this.yesNoOptions = data; },
            error: () => { this.yesNoOptions = []; }
        });

        this.lookupService.getQuarterNumbers().subscribe({
            next: data => { this.quarterOptions = data ?? []; },
            error: () => { this.quarterOptions = []; }
        });

        this.documentTypeService.getAllActiveDocumentTypes().subscribe({
            next: (data) => { this.documentTypes = data; },
            error: () => {
                this.documentTypes = [];
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_DOC_TYPES') });
            }
        });
    }

    // Validation flags for contact info
    emailInvalid = false;
    cellphoneInvalid = false;
    /** L9 (D11): the mobile number is required, for a new student and at the next edit of one without it. */
    cellphoneMissing = false;
    homeNumberInvalid = false;
    emergencyContactInvalid = false;
    /** Contact numbers as loaded for edit; unchanged ones are not re-checked (see `phoneInvalid`). */
    private loadedPhones: Partial<Record<PhoneField, string>> = {};

    private readonly EMAIL_PATTERN = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    readonly phoneMaxLength = PHONE_MAX_LENGTH;

    /**
     * Amounts are paid in US dollars for almost every student, so a new record starts
     * in USD; the selector stays for the exceptions. An existing record keeps whatever
     * it holds — defaulting there would show up as a change the approver never asked for.
     */
    private applyDefaultCurrency(): void {
        if (this.isEditMode || !this.defaultCurrencyId) {
            return;
        }
        if (!this.student.studentAmountCurrencyId) {
            this.student.studentAmountCurrencyId = this.defaultCurrencyId;
        }
        if (!this.student.studyUniversityFeeStatusCurrencyId) {
            this.student.studyUniversityFeeStatusCurrencyId = this.defaultCurrencyId;
        }
    }

    onPassportDateChange() {
        const issue = this.student.studentPassportIssueDate;
        const expire = this.student.studentPassportExpireDate;
        if (issue && expire && new Date(expire) <= new Date(issue)) {
            this.student.studentPassportExpireDate = null as any;
            this.messageService.add({
                severity: 'warn',
                summary: this.translationService.translate('STUDENT_ADD.PASSPORT_DATE_ERROR_TITLE') || 'Invalid Date',
                detail: this.translationService.translate('STUDENT_ADD.PASSPORT_DATE_ERROR') || 'Passport expiry date must be after the issue date.',
                life: 4000
            });
        }
    }

    /**
     * `normalize` rewrites changed phone fields as they are checked (Arabic-Indic digits to
     * Latin, surrounding spaces dropped). It is off while the user is still typing, where
     * trimming would eat the space they just entered.
     */
    validateContactFields(normalize = true): boolean {
        const email = (this.student.studentEmail || '').trim();
        if (normalize) {
            for (const field of PHONE_FIELDS) {
                if (!this.phoneUnchanged(field)) {
                    this.student[field] = normalizePhone(this.student[field]);
                }
            }
        }

        this.emailInvalid = !!email && !this.EMAIL_PATTERN.test(email);
        this.cellphoneMissing = !normalizePhone(this.student.studentCellphone);
        this.cellphoneInvalid = this.phoneInvalid('studentCellphone', true);
        this.homeNumberInvalid = this.phoneInvalid('studentHomeNumber', false);
        this.emergencyContactInvalid = this.phoneInvalid('studentEmergencyContact', false);

        return !this.emailInvalid && !this.cellphoneMissing && !this.cellphoneInvalid
            && !this.homeNumberInvalid && !this.emergencyContactInvalid;
    }

    /**
     * A number saved before the digit-count rule may fall short of it. When editing, such a
     * number is left alone, neither checked nor rewritten, until the user changes it. The
     * server does the same, so an old record can still be edited for something else.
     */
    private phoneUnchanged(field: PhoneField): boolean {
        return this.isEditMode && normalizePhone(this.student[field]) === normalizePhone(this.loadedPhones[field]);
    }

    private phoneInvalid(field: PhoneField, mobile: boolean): boolean {
        return !this.phoneUnchanged(field) && !isValidPhone(this.student[field], mobile);
    }

    /** Clears a shown contact error as soon as the user's typing fixes it, rather than on blur. */
    recheckContactField(): void {
        if (this.emailInvalid || this.cellphoneMissing || this.cellphoneInvalid || this.homeNumberInvalid || this.emergencyContactInvalid) {
            this.validateContactFields(false);
        }
    }

    // Cascading dropdown methods
    onBirthdatePlaceChange() {
        // Birth place is a standalone country reference — no dependent cascade fields in this form
    }

    onStudentCountryChange() {
        // When student country changes, load related governments
        if (this.student.studentCountryId) {
            this.lookupService.getGovernmentsByCountry(this.student.studentCountryId).subscribe(data => {
                this.governmentOptions = data;
            });
        } else {
            this.governmentOptions = [];
        }
        // Reset dependent fields
        this.student.studentGovId = 0;
        this.student.studentMudId = 0;
        this.municipalityOptions = [];
    }

    onGovernmentChange() {
        this.student.studentMudId = 0;
        this.municipalityOptions = [];
        if (this.selectedGovernment && this.selectedGovernment.id) {
            this.student.studentGovId = this.selectedGovernment.id;
            this.lookupService.getMunicipalitiesByGovernment(this.selectedGovernment.id, this.student.studentCountryId)
                .subscribe(data => { this.municipalityOptions = data; });
        }
    }

    onAgencyTypeChange() {
        // Reset selections and visibility
        this.student.studentMinistryTypeId = 0;
        this.student.studentUniversityTypeId = 0;
        this.showMinistryField = false;
        this.showUniversityField = false;

        if (this.student.studentAgencyTypeId) {
            const agencyId = Number(this.student.studentAgencyTypeId);
            // Fetch both filtered lists in parallel and then decide what to show
            forkJoin({
                ministry: this.lookupService.getMinistryTypesByAgency(agencyId),
                university: this.lookupService.getUniversityTypesByAgency(agencyId)
            }).subscribe({
                next: ({ ministry, university }) => {
                    this.ministryTypeOptions = ministry || [];
                    this.universityTypeOptions = university || [];
                    this.showMinistryField = this.ministryTypeOptions.length > 0;
                    this.showUniversityField = this.universityTypeOptions.length > 0;
                },
                error: () => {
                    this.ministryTypeOptions = [];
                    this.universityTypeOptions = [];
                    this.showMinistryField = false;
                    this.showUniversityField = false;
                }
            });

            // Auto-fill amount from lookup when degree + agency type are both set
            const degreeId = Number(this.student.studentDegreeId || 0);
            if (degreeId) {
                this.lookupService.getStudyAmount(degreeId, agencyId).subscribe({
                    next: (amount) => {
                        if (amount !== null && amount > 0) {
                            this.student.studentAmount = amount;
                        }
                    }
                });
            }
        }
    }

    onStudyCountryInChange() {
        // Reset all downstream
        this.student.syudyGovIn = 0;
        this.student.studyUniversityIn = 0;
        this.student.studyCollegeIn = 0;
        this.student.studyEducationIn = null;
        this.student.studyMajorIn = 0;
        this.studyGovInOptions = [];
        this.studyUniversityInOptions = [];
        this.studyCollegeInOptions = [];
        this.studyEducationInOptions = [];
        this.studyMajorInOptions = [];
        // Governorates and universities of the chosen country; a governorate narrows the
        // universities later (live QA M10: they waited for a governorate, then fell back to
        // every country's).
        const countryId = Number(this.student.studyCountryIn || 0);
        if (countryId) {
            this.lookupService.getStudyGovernoratesInByCountry(countryId).subscribe(list => this.studyGovInOptions = list);
            this.lookupService.getStudyUniversitiesOfCountry(countryId).subscribe(list => this.studyUniversityInOptions = list);
        }
        // Study years and finance amount are per-attaché, so moving the student to
        // another one changes both. Years re-resolve on their own because the reset
        // above clears the major, but the amount depends only on degree and agency
        // type — nothing above touches those, so it has to be asked for again here
        // or it would silently keep the previous country's figure.
        this.refreshAmountForCountry();
    }

    /** Re-reads the finance amount for the currently selected attaché. */
    private refreshAmountForCountry(): void {
        const degreeId = Number(this.student.studentDegreeId || 0);
        const agencyTypeId = Number(this.student.studentAgencyTypeId || 0);
        const countryId = Number(this.student.studyCountryIn || 0) || null;
        if (!degreeId || !agencyTypeId) return;

        this.lookupService.getStudyAmount(degreeId, agencyTypeId, countryId).subscribe({
            next: (amount) => {
                if (amount !== null && amount > 0) {
                    this.student.studentAmount = amount;
                }
            }
        });
    }

    onStudyGovInChange() {
        // Reset downstream
        this.student.studyUniversityIn = 0;
        this.student.studyCollegeIn = 0;
        this.studyUniversityInOptions = [];
        this.studyCollegeInOptions = [];
        // The country's universities, narrowed to the governorate when one is chosen.
        const govId = Number(this.student.syudyGovIn || 0);
        const countryId = Number(this.student.studyCountryIn || 0);
        if (countryId) {
            this.lookupService.getStudyUniversitiesOfCountry(countryId, govId || null)
                .subscribe(list => this.studyUniversityInOptions = list);
        }
    }

    onStudyUniversityInChange() {
        // Reset colleges then load filtered by university only (independent of education)
        this.student.studyCollegeIn = 0;
        this.studyCollegeInOptions = [];
        const uniId = Number(this.student.studyUniversityIn || 0);
        if (uniId) {
            this.lookupService.getStudyCollegesByUniversity(uniId)
                .subscribe(list => this.studyCollegeInOptions = list);
        }
    }

    onStudyEducationInChange() {
        this.student.studyMajorIn = 0;
        this.studyMajorInOptions = [];
        // Id 0 is a real education row, so only "nothing picked" (null) skips the load.
        const eduId = this.student.studyEducationIn;
        if (eduId != null) {
            this.lookupService.getStudyMajorsInByEducation(eduId)
                .subscribe(list => this.studyMajorInOptions = list);
        }
    }

    onStudyCollegeInChange() {
        this.student.studyEducationIn = null;
        this.student.studyMajorIn = 0;
        this.studyEducationInOptions = [];
        this.studyMajorInOptions = [];
        const collegeId = Number(this.student.studyCollegeIn || 0);
        if (collegeId) {
            this.lookupService.getStudyEducationsInByCollege(collegeId)
                .subscribe(list => this.studyEducationInOptions = list);
        }
    }

    /**
     * After loading a student for edit, re-populate all cascade-dependent option arrays
     * so that the already-selected values are visible in the dropdowns.
     */
    private restoreCascadeOptions(data: StudentDataAdd) {
        // Residence country → governments
        const residenceCountryId = Number(data.studentCountryId || 0);
        if (residenceCountryId) {
            this.lookupService.getGovernmentsByCountry(residenceCountryId).subscribe(govs => {
                this.governmentOptions = govs;
                const govId = Number(data.studentGovId || 0);
                if (govId) {
                    this.selectedGovernment = govs.find(g => Number(g.id) === govId || Number(g.value) === govId);
                    this.lookupService.getMunicipalitiesByGovernment(govId, residenceCountryId).subscribe(
                        muns => { this.municipalityOptions = muns.length ? muns : []; },
                        () => this.lookupService.getMunicipalities().subscribe(all => this.municipalityOptions = all)
                    );
                }
            });
        }

        // Agency type → ministry types & university types
        const agencyTypeId = Number(data.studentAgencyTypeId || 0);
        if (agencyTypeId) {
            forkJoin({
                ministry: this.lookupService.getMinistryTypesByAgency(agencyTypeId),
                university: this.lookupService.getUniversityTypesByAgency(agencyTypeId)
            }).subscribe(({ ministry, university }) => {
                this.ministryTypeOptions = ministry || [];
                this.universityTypeOptions = university || [];
                this.showMinistryField = this.ministryTypeOptions.length > 0;
                this.showUniversityField = this.universityTypeOptions.length > 0;
            });
        }

        // Study location cascade: Country → Gov → University → College → Education → Major
        const studyCountryId = Number(data.studyCountryIn || 0);
        if (studyCountryId) {
            this.lookupService.getStudyGovernoratesInByCountry(studyCountryId).subscribe(
                list => this.studyGovInOptions = list);
        }
        const studyGovId = Number(data.syudyGovIn || 0);
        if (studyCountryId) {
            this.lookupService.getStudyUniversitiesOfCountry(studyCountryId, studyGovId || null).subscribe(
                list => this.studyUniversityInOptions = list);
        }
        const uniId = Number(data.studyUniversityIn || 0);
        if (uniId) {
            this.lookupService.getStudyCollegesByUniversity(uniId).subscribe(
                list => this.studyCollegeInOptions = list);
        }
        const collegeId = Number(data.studyCollegeIn || 0);
        if (collegeId) {
            this.lookupService.getStudyEducationsInByCollege(collegeId).subscribe(
                list => this.studyEducationInOptions = list);
        }
        const eduId = data.studyEducationIn;
        if (eduId != null) {
            this.lookupService.getStudyMajorsInByEducation(eduId).subscribe(
                list => this.studyMajorInOptions = list);
        }
    }

    onDegreeOrMajorChange() {
        const degreeId = Number(this.student.studentDegreeId || 0);
        const majorId = Number(this.student.studentMajorId || 0);
        // The attaché this student studies at. Each may set its own number of
        // study years and its own finance amount; without this the auto-fill
        // silently reads the shared default for everyone, which is the whole
        // point of the per-country rules.
        const countryId = Number(this.student.studyCountryIn || 0) || null;
        if (degreeId && majorId) {
            this.studyYearsLookupLoading = true;
            this.lookupService.getStudyYears(degreeId, majorId, countryId).subscribe({
                next: (years) => {
                    if (years !== null && years > 0) {
                        this.student.studentStudyNoOfYears = years;
                        this.computeLastQuarter();
                    }
                    this.studyYearsLookupLoading = false;
                },
                error: () => { this.studyYearsLookupLoading = false; }
            });
        }
        // Also auto-fill amount when degree changes and agency type is already set
        const agencyTypeId = Number(this.student.studentAgencyTypeId || 0);
        if (degreeId && agencyTypeId) {
            this.lookupService.getStudyAmount(degreeId, agencyTypeId, countryId).subscribe({
                next: (amount) => {
                    if (amount !== null && amount > 0) {
                        this.student.studentAmount = amount;
                    }
                }
            });
        }
    }

    onStudyYearsChange() {
        this.computeLastQuarter();
    }

    // Quarter helpers

    private parseQuarterInt(val: number | null | undefined): { year: number; q: number } | null {
        if (!val || val <= 0) return null;
        const q = val % 10;
        const year = Math.floor(val / 10);
        if (q < 1 || q > 4 || year < 1990 || year > 2099) return null;
        return { year, q };
    }

    private buildQuarterInt(year: number | null, q: number | null): number | null {
        if (!year || !q || year < 1990 || year > 2099 || q < 1 || q > 4) return null;
        return year * 10 + q;
    }

    onAddedQuarterChange() {
        const val = this.buildQuarterInt(this.addedQuarterYear, this.addedQuarterQ);
        this.student.studentAddedQuarter = val ?? 0;
        this.computeLastQuarter();
    }

    onLastQuarterChange() {
        const val = this.buildQuarterInt(this.lastQuarterYear, this.lastQuarterQ);
        this.student.studentLastQuarter = val ?? 0;
    }

    onCurrentQuarterChange() {
        const val = this.buildQuarterInt(this.currentQuarterYear, this.currentQuarterQ);
        this.student.currentQuarter = val != null ? String(val) : '';
    }

    private computeLastQuarter() {
        const years = this.student.studentStudyNoOfYears;
        if (!this.addedQuarterYear || !this.addedQuarterQ || !years || years <= 0) return;
        const lastYear = this.addedQuarterYear + years;
        this.lastQuarterYear = lastYear;
        this.lastQuarterQ = this.addedQuarterQ;
        this.student.studentLastQuarter = this.buildQuarterInt(lastYear, this.addedQuarterQ) ?? 0;
    }

    private syncQuarterSplitFromModel() {
        const added = this.parseQuarterInt(this.student.studentAddedQuarter);
        if (added) {
            this.addedQuarterYear = added.year;
            this.addedQuarterQ = added.q;
        }
        const last = this.parseQuarterInt(this.student.studentLastQuarter);
        if (last) {
            this.lastQuarterYear = last.year;
            this.lastQuarterQ = last.q;
        }
        const current = this.parseQuarterInt(Number(this.student.currentQuarter));
        if (current) {
            this.currentQuarterYear = current.year;
            this.currentQuarterQ = current.q;
        }
    }

    get studentAgeDisplay(): string {
        if (!this.student.studentBirthdate) return '';
        const bdate = new Date(this.student.studentBirthdate);
        if (isNaN(bdate.getTime())) return '';
        const now = new Date();
        let years = now.getFullYear() - bdate.getFullYear();
        let months = now.getMonth() - bdate.getMonth();
        if (months < 0) { years--; months += 12; }
        if (now.getDate() < bdate.getDate()) { months--; if (months < 0) { years--; months += 11; } }
        return years >= 0 ? `${years}.${months}` : '';
    }

    onBirthdateChange() {
        // Trigger age display recalculation (binding is a getter so it auto-updates on change detection)
    }

    initializeSteps() {
        this.steps = [
            { label: this.translationService.translate('STUDENT_ADD.PERSONAL_INFO') },
            { label: this.translationService.translate('STUDENT_ADD.RESIDENCE_CONTACT_INFO') },
            { label: this.translationService.translate('STUDENT_ADD.STUDENT_PICTURE') },
            { label: this.translationService.translate('STUDENT_ADD.ACADEMIC_INFO') },
            { label: this.translationService.translate('STUDENT_ADD.STUDY_DETAILS') },
            { label: this.translationService.translate('STUDENT_ADD.FINANCIAL_INFO') },
            { label: this.translationService.translate('STUDENT_ADD.STUDY_LOCATION_INFO') },
            { label: this.translationService.translate('STUDENT_ADD.FAMILY_INFO') },
            { label: this.translationService.translate('STUDENT_ADD.DOCUMENTS_STATUS') },
            { label: this.translationService.translate('STUDENT_ADD.UPLOAD_DOCUMENTS') }
        ];
    }

    loadFamilyData(studentId: number): void {
        if (!studentId || studentId <= 0) {
            return;
        }
        this.studentdataService.getParents(studentId).subscribe({
            next: rows => { this.parentRows = rows ?? []; },
            error: () => { this.parentRows = []; }
        });
        this.studentdataService.getRelatives(studentId).subscribe({
            next: rows => {
                this.relativeRows = (rows ?? []).map(r => ({
                    ...r,
                    selectedStudent: r.relatedStudentId ? {
                        studentId: r.relatedStudentId,
                        studentName: r.relatedStudentName ?? '',
                        studyStatusName: r.relatedStudentStatusName,
                        currentLevelName: r.relatedStudentCurrentLevelName
                    } : null,
                    searchText: r.relatedStudentName ?? ''
                }));
            },
            error: () => { this.relativeRows = []; }
        });
    }

    addParentRow(): void {
        this.parentRows.push({
            relationshipId: 0,
            parentName: '',
            isAlive: true
        });
    }

    removeParentRow(index: number): void {
        this.parentRows.splice(index, 1);
    }

    addRelativeRow(): void {
        this.relativeRows.push({
            relationshipId: 0,
            relatedStudentId: 0,
            selectedStudent: null,
            searchText: ''
        });
    }

    removeRelativeRow(index: number): void {
        this.relativeRows.splice(index, 1);
    }

    searchRelativeStudents(event: { query: string }, _row: RelativeRowEdit): void {
        const q = (event?.query ?? '').trim();
        if (q.length < 2) {
            this.relativeSearchResults = [];
            return;
        }
        const excludeId = this.studentId > 0 ? this.studentId : undefined;
        this.studentdataService.searchRelativeStudents(q, excludeId).subscribe({
            next: results => { this.relativeSearchResults = results ?? []; },
            error: () => { this.relativeSearchResults = []; }
        });
    }

    onRelativeStudentSelected(row: RelativeRowEdit, selected: StudentRelativeSearchDto | null): void {
        row.selectedStudent = selected;
        row.relatedStudentId = selected?.studentId ?? 0;
        row.searchText = selected?.studentName ?? '';
    }

    relativeStudentLabel(item: StudentRelativeSearchDto): string {
        if (!item) {
            return '';
        }
        const parts = [`#${item.studentId}`, item.studentName];
        if (item.passportNumber) {
            parts.push(item.passportNumber);
        }
        return parts.filter(Boolean).join(' — ');
    }

    /** The family step's rows that are complete enough to save. */
    private familyPayload(): { parents: StudentParentDto[]; relatives: StudentRelativeDto[] } {
        const parents = (this.parentRows ?? []).filter(p => p.parentName?.trim() && p.relationshipId > 0);
        const relatives = (this.relativeRows ?? [])
            .filter(r => r.relationshipId > 0 && r.relatedStudentId > 0)
            .map(r => ({
                relativeId: r.relativeId,
                relationshipId: r.relationshipId,
                relatedStudentId: r.relatedStudentId,
                notes: r.notes
            } as StudentRelativeDto));
        return { parents, relatives };
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
    static calendarDay(value: Date | string | null | undefined): Date | string | null {
        // Lives in student-add-payload.ts with the rest of the payload rules; kept here
        // as the name the specs and templates know.
        return payloadCalendarDay(value);
    }

    private saveFamilyData(studentId: number, onDone: () => void): void {
        const { parents, relatives } = this.familyPayload();

        this.studentdataService.replaceParents(studentId, parents).subscribe({
            next: () => {
                this.studentdataService.replaceRelatives(studentId, relatives).subscribe({
                    next: () => onDone(),
                    error: err => {
                        this.messageService.add({
                            severity: 'warn',
                            summary: this.translationService.translate('COMMON.WARNING'),
                            detail: err?.error?.message || this.translationService.translate('STUDENT_ADD.FAMILY_RELATIVES_SAVE_FAILED')
                        });
                        onDone();
                    }
                });
            },
            error: err => {
                this.messageService.add({
                    severity: 'warn',
                    summary: this.translationService.translate('COMMON.WARNING'),
                    detail: err?.error?.message || this.translationService.translate('STUDENT_ADD.FAMILY_PARENTS_SAVE_FAILED')
                });
                onDone();
            }
        });
    }


    /**
     * The record as it would be sent untouched, taken once it is loaded and normalised.
     * An edit sends only what differs from this (live QA H3): the server keeps every
     * column the payload does not mention, so a field the wizard never showed — or one
     * the server masked for this user — can no longer be cleared by accident.
     */
    private loadedPayload: Record<string, unknown> | null = null;

    loadStudentData(id: number) {
        this.loading = true;
        this.profilePhotoUnreachable = false;
        this.loadedPayload = null;

        this.studentAddService.get(id).subscribe(
            (data: StudentDataAdd) => {
                this.student = data;
                const anyData: any = data as any;

                // ── Field-type normalisation ─────────────────────────────────────────────
                // studentCountryId: getter is commented-out in the entity, so the backend
                // serialises it inside the nested studentCountry object instead of flat.
                if (!this.student.studentCountryId) {
                    this.student.studentCountryId =
                        Number(anyData.studentCountry?.studentCountryId) || 0;
                }

                // studentBirthdatePlace: DB stores as String (country-id stored as text),
                // but the dropdown is bound to a number via optionValue="value".
                if (typeof anyData.studentBirthdatePlace === 'string') {
                    (this.student as any).studentBirthdatePlace =
                        Number(anyData.studentBirthdatePlace) || 0;
                }

                // studentDelegationDecisionYear: DB stores as String, p-inputNumber needs number.
                if (typeof anyData.studentDelegationDecisionYear === 'string') {
                    (this.student as any).studentDelegationDecisionYear =
                        Number(anyData.studentDelegationDecisionYear) || 0;
                }

                // Dates: backend returns ISO Instant strings; p-calendar needs Date objects.
                const toDate = (v: any): Date | any =>
                    v && typeof v === 'string' ? new Date(v) : v;
                this.student.studentBirthdate         = toDate(anyData.studentBirthdate);
                this.student.studentPassportIssueDate = toDate(anyData.studentPassportIssueDate);
                this.student.studentPassportExpireDate= toDate(anyData.studentPassportExpireDate);

                // Currency column aliases (backend returns amountCurrency / universityFeeStatusCurrency)
                if (Object.prototype.hasOwnProperty.call(anyData, 'amountCurrency') && !this.student.studentAmountCurrencyId) {
                    this.student.studentAmountCurrencyId = Number(anyData.amountCurrency) || 0;
                }
                if (Object.prototype.hasOwnProperty.call(anyData, 'universityFeeStatusCurrency') && !this.student.studyUniversityFeeStatusCurrencyId) {
                    this.student.studyUniversityFeeStatusCurrencyId = Number(anyData.universityFeeStatusCurrency) || 0;
                }
                // ────────────────────────────────────────────────────────────────────────

                // Nothing below this line writes to the model in edit mode (the cascade,
                // quarter-split and draft paths fill option arrays or are add-mode only),
                // so this is what an untouched form would submit.
                this.loadedPayload = buildStudentPayload(this.student, { isEditMode: true, studentId: id });

                this.loadedPhones = {
                    studentCellphone: data.studentCellphone,
                    studentHomeNumber: data.studentHomeNumber,
                    studentEmergencyContact: data.studentEmergencyContact
                };

                if (data.picturePath) {
                    this.existingPicturePath = data.picturePath;
                }

                // Sync quarter split UI from model values
                this.syncQuarterSplitFromModel();

                // Re-populate cascade-dependent option arrays for edit mode
                this.restoreCascadeOptions(data);

                // Load existing documents
                this.loadExistingDocuments();

                this.loadExistingPicture();
                this.loadFamilyData(id);

                this.loading = false;
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_SUCCESS')
                });
            },
            (err: HttpErrorResponse) => {
                this.loading = false;
                // An id nobody has is the not-found page, as for a malformed one (live QA
                // L3): the empty ten-step wizard with a toast read as a form to fill in.
                if (err?.status === 404) {
                    this.router.navigate(['/notfound']);
                    return;
                }
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDENT_ADD.MSG_LOAD_ERROR')
                });
            }
        );
    }

    nextStep() {
        if (this.validateCurrentStep()) {
            this.activeIndex++;
            this.persistDraft();
        }
    }

    prevStep() {
        this.activeIndex--;
        this.persistDraft();
    }

    /**
     * Opens the step picked in the sidebar. Editing only.
     *
     * <p>An existing record has data on every step, and "Update" still runs
     * {@link validateAllSteps} before anything is sent, so any step may be opened
     * in any order. A new student keeps the Next/Back flow: each step's check runs
     * on the way forward, and the sidebar stays a progress display there.
     */
    goToStep(index: number): void {
        if (!this.isEditMode || this.loading || index === this.activeIndex || index < 0 || index >= this.steps.length) {
            return;
        }
        this.activeIndex = index;
    }

    validateCurrentStep(): boolean {
        switch (this.activeIndex) {
            case 0: // Personal Information
                if (!this.student.studentName?.trim()) {
                    this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_NAME') });
                    return false;
                }
                if (!this.student.studentGender) {
                    this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.GENDER_REQUIRED') });
                    return false;
                }
                {
                    const issue = this.student.studentPassportIssueDate;
                    const expire = this.student.studentPassportExpireDate;
                    if (issue && expire && new Date(expire) <= new Date(issue)) {
                        this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.PASSPORT_DATE_ERROR') || 'Passport expiry date must be after the issue date.' });
                        return false;
                    }
                }
                return true;

            case 1: // Residence & Contact
                if (!this.validateContactFields()) {
                    this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.CONTACT_VALIDATION_ERROR') || 'Please fix the contact information errors.' });
                    return false;
                }
                return true;

            case 2: // Student Picture — optional, warn but allow proceeding
                if (!this.studentPicture && !this.previewImageSrc) {
                    this.messageService.add({ severity: 'warn', summary: this.translationService.translate('COMMON.WARNING'), detail: this.translationService.translate('STUDENT_ADD.MSG_NO_PROFILE_PICTURE') });
                }
                return true;

            case 3: // Academic Information
                if (!this.student.studentDelegationDecisionYear) {
                    this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_DELEGATION_YEAR') });
                    return false;
                }
                if (!this.student.studentDegreeId) {
                    this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_STUDENT_DEGREE') });
                    return false;
                }
                if (!this.student.studentMajorId) {
                    this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_STUDENT_MAJOR') });
                    return false;
                }
                return true;

            case 4: // Study Details
                return true;

            case 5: // Financial Information
                return true;

            case 6: // Study Location Info
                return true;

            case 7: // Family Info — optional
                return true;

            case 8: // Documents & Status
                return true;

            case 9: // Upload Documents
                return true;

            default:
                return true;
        }
    }

    /**
     * A picked file goes to the cropper rather than straight into the form: the stored
     * picture is printed on ID cards and forms, so it has to be framed to the identity
     * photo format instead of whatever aspect ratio the camera produced.
     */
    onProfilePictureSelect(event: any) {
        if (event.files && event.files[0]) {
            this.pictureToCrop = event.files[0];
        }
    }

    /** Re-frame the already selected picture without picking the file again. */
    recropPicture(): void {
        if (this.originalPicture) {
            this.pictureToCrop = this.originalPicture;
        }
    }

    onPictureCropped(result: { file: File; dataUrl: string }): void {
        this._revokeBlobUrl();
        // Keep the untouched original so re-cropping starts from full resolution
        // rather than compounding losses on an already cropped JPEG.
        this.originalPicture = this.pictureToCrop ?? this.originalPicture;
        this.studentPicture = result.file;
        this.previewImageSrc = result.dataUrl;
        this.profilePhotoUnreachable = false;
        this.pictureToCrop = null;
        this.messageService.add({
            severity: 'success',
            summary: this.translationService.translate('COMMON.SUCCESS'),
            detail: this.translationService.translate('STUDENT_ADD.MSG_PROFILE_PICTURE_SELECTED')
        });
    }

    onCropCancelled(): void {
        this.pictureToCrop = null;
    }

    onDocumentUpload(event: any) {
        for (const file of event.files) {
            this.uploadedFiles.push(file);
        }
        this.messageService.add({ severity: 'info', summary: this.translationService.translate('COMMON.SUCCESS'), detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENTS_UPLOADED') });
    }

    removeDocument(index: number) {
        this.uploadedFiles.splice(index, 1);
    }

    /**
     * Business classification (passport, certificate, …). Rows created before types were
     * carried through hold a file extension, which describes the format rather than the
     * type — those read as "unspecified" so the two concepts stay distinct.
     */
    getDocumentTypeLabel(documentType: string | null | undefined): string {
        if (this.studentDocumentService.isFormatLikeType(documentType)) {
            return this.translationService.translate('STUDENTS.UNKNOWN_TYPE');
        }
        const raw = (documentType || '').trim();
        const match = this.documentTypes.find(t =>
            (t.documentTypeName || '').trim().toLowerCase() === raw.toLowerCase()
            || String(t.documentTypeId) === raw);
        return match ? match.documentTypeName : raw;
    }

    /** True when the row carries a real business type (not blank, not an extension). */
    hasDocumentType(documentType: string | null | undefined): boolean {
        return !this.studentDocumentService.isFormatLikeType(documentType);
    }

    /** File format (PDF, image, …), derived from the filename. Accepts a stored
     *  document or a not-yet-uploaded File from the picker. */
    getDocumentFormatLabel(doc: StudentDocument | File): string {
        return this.translationService.translate(
            this.studentDocumentService.getDocumentFormatLabelKey(this.asFormatSource(doc)));
    }

    getDocumentFormatIcon(doc: StudentDocument | File): string {
        return this.studentDocumentService.getDocumentFormatIcon(this.asFormatSource(doc));
    }

    private asFormatSource(doc: StudentDocument | File): { originalFilename: string; filePath: string } {
        const filename = (doc as StudentDocument)?.originalFilename ?? (doc as File)?.name ?? '';
        return { originalFilename: filename, filePath: '' };
    }

    loadExistingDocuments() {
        if (this.studentId && this.studentId > 0) {
            this.studentDocumentService.getDocumentsByStudentId(this.studentId).subscribe({
                next: (documents) => {
                    this.existingDocuments = documents;
                },
                error: () => {
                    this.existingDocuments = [];
                }
            });
        }
    }

    loadExistingPicture() {
        if (!(this.studentId && this.studentId > 0)) {
            return;
        }
        this._revokeBlobUrl();
        this.previewImageSrc = '';
        this.profilePhotoUnreachable = false;

        this.studentDocumentService.getStudentPhotoBlob(this.studentId).subscribe({
            next: (blob) => {
                this._photoBlobUrl = URL.createObjectURL(blob);
                this.previewImageSrc = this._photoBlobUrl;
                this.profilePhotoUnreachable = false;
            },
            error: () => {
                // Primary /photo endpoint has no photo — try a document fallback
                this._tryPhotoFallbackFromDocuments();
            }
        });
    }

    private _tryPhotoFallbackFromDocuments(): void {
        this.studentDocumentService.getDocumentsByStudentId(this.studentId).subscribe({
            next: (docs) => {
                const pic = this._findLikelyPictureDocument(docs);
                if (pic) {
                    this.studentDocumentService.viewDocument(pic.documentId).subscribe({
                        next: (blob) => {
                            this._revokeBlobUrl();
                            this._photoBlobUrl = URL.createObjectURL(blob);
                            this.previewImageSrc = this._photoBlobUrl;
                            this.profilePhotoUnreachable = false;
                        },
                        error: () => {
                            this.previewImageSrc = '';
                            this.profilePhotoUnreachable = true;
                        }
                    });
                } else {
                    this.previewImageSrc = '';
                    this.profilePhotoUnreachable = true;
                }
            },
            error: () => {
                this.previewImageSrc = '';
                this.profilePhotoUnreachable = true;
            }
        });
    }

    private _findLikelyPictureDocument(docs: StudentDocument[]): StudentDocument | undefined {
        const lowered = docs.map(d => ({
            doc: d,
            type: (d.documentType || '').toLowerCase(),
            names: `${d.originalFilename || ''} ${d.documentName || ''}`.toLowerCase()
        }));
        const byType = lowered.find(({ type, names }) =>
            type.includes('photo') || type.includes('profile') || type === 'picture'
            || type === 'image' || names.includes('profile') || names.includes('photo'));
        if (byType) return byType.doc;
        return lowered.map(x => x.doc).find(d => {
            const n = `${d.originalFilename || ''} ${d.documentName || ''}`.toLowerCase();
            return n.endsWith('.jpg') || n.endsWith('.jpeg') || n.endsWith('.png') || n.endsWith('.webp');
        });
    }

    onProfilePictureLoadError(_event: Event): void {
        this.previewImageSrc = '';
        this.profilePhotoUnreachable = true;
    }

    viewDocument(studentDoc: StudentDocument) {
        if (studentDoc.documentId) {
            this.studentDocumentService.downloadDocument(studentDoc.documentId).subscribe({
                next: (blob) => {
                    const url = window.URL.createObjectURL(blob);
                    const link = document.createElement('a');
                    link.href = url;
                    link.download = studentDoc.originalFilename || studentDoc.documentName;
                    link.click();
                    window.URL.revokeObjectURL(url);
                },
                error: (err) => {
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate(
                            this.studentDocumentService.documentErrorKey(err, 'STUDENT_ADD.MSG_DOCUMENT_DOWNLOAD_ERROR'))
                    });
                }
            });
        }
    }

    deleteDocument(studentDoc: StudentDocument) {
        if (studentDoc.documentId) {
            this.studentDocumentService.deleteDocument(studentDoc.documentId).subscribe({
                next: () => {
                    // Removal takes effect on approval, so the list is unchanged for now.
                    this.messageService.add({
                        severity: 'info',
                        summary: this.translationService.translate('COMMON.INFO'),
                        detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENT_DELETE_PENDING'),
                        life: 7000
                    });
                },
                error: () => {
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translationService.translate('COMMON.ERROR'),
                        detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENT_DELETE_ERROR')
                    });
                }
            });
        }
    }

    triggerReplaceDocument(doc: StudentDocument) {
        this.docToReplace = doc;
        this.docReplaceInput.nativeElement.value = '';
        this.docReplaceInput.nativeElement.click();
    }

    onReplaceFileSelected(event: Event) {
        const input = event.target as HTMLInputElement;
        if (!input.files || input.files.length === 0 || !this.docToReplace) {
            return;
        }
        const file = input.files[0];
        const docId = this.docToReplace.documentId;
        this.studentDocumentService.replaceDocument(docId, file).subscribe({
            next: () => {
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENT_REPLACED')
                });
                this.docToReplace = null;
                this.loadExistingDocuments();
            },
            error: () => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENT_REPLACE_ERROR')
                });
                this.docToReplace = null;
            }
        });
    }

    formatFileSize(bytes: number): string {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    formatUploadDate(dateString: string): string {
        return new Date(dateString).toLocaleDateString();
    }

    clearProfilePicture() {
        this.studentPicture = null;
        this.originalPicture = null;
        this.pictureToCrop = null;
        this._revokeBlobUrl();
        this.previewImageSrc = '';
        this.existingPicturePath = '';
        if (this.isEditMode) {
            this.profilePhotoUnreachable = true;
        }
    }

    /** Optional draft for STUDENT_FINANCE chained approval after student is approved. */
    buildLinkedFinanceDraft(): Record<string, unknown> {
        const delegation = this.student?.studentDelegationDecisionYear
            ? String(this.student.studentDelegationDecisionYear)
            : '';
        let yr = Number(this.student?.studentFirstReceiptOfDue || 0);
        let quarter = 0;
        let yearQuarter = 0;
        // The first receipt of due is a YYYYQ key (20251 = 2025 Q1), not a year: copied
        // as the year it stored 20251 and drew a year "20251" on the dashboard (live QA H7).
        if (yr >= 10000 && yr % 10 >= 1 && yr % 10 <= 4) {
            yearQuarter = yr;
            quarter = yr % 10;
            yr = Math.floor(yr / 10);
        }
        if ((!yr || yr <= 0) && delegation.length >= 4) {
            const parsed = parseInt(delegation.substring(0, 4), 10);
            yr = Number.isFinite(parsed) ? parsed : 0;
        }
        return {
            studyCountryInId: Number(this.student?.studyCountryIn) || 0,
            studyGovInId: Number(this.student?.syudyGovIn) || 0,
            studentFinRecievedAmount: Number(this.student?.studentAmount) || 0,
            studentFinRecievedYear: yr || 0,
            studentFinRecievedMonth: quarter,
            studentFinRecievedYearMonth: yearQuarter,
            studyFinRecievedStatus: 0,
            studentFinName: this.student?.studentName || ''
        };
    }

    saveStudent() {
        this.submitted = true;

        // Validate first so an incomplete form reports its errors instead of
        // asking the user to confirm a save that cannot go through.
        if (!this.validateAllSteps()) {
            return;
        }

        this.confirmationService.confirm({
            key: this.saveConfirmKey,
            header: this.translationService.translate('STUDENT_ADD.CONFIRM_SAVE_HEADER'),
            message: this.translationService.translate(
                this.isEditMode ? 'STUDENT_ADD.CONFIRM_UPDATE_MSG' : 'STUDENT_ADD.CONFIRM_CREATE_MSG'),
            icon: 'pi pi-question-circle',
            acceptLabel: this.translationService.translate(this.isEditMode ? 'COMMON.UPDATE' : 'COMMON.SAVE'),
            rejectLabel: this.translationService.translate('COMMON.CANCEL'),
            acceptButtonStyleClass: 'p-button-success',
            rejectButtonStyleClass: 'p-button-text',
            accept: () => this.submitStudent()
        });
    }

    /** Builds the payload and sends it. Reached only after validation and confirmation. */
    private submitStudent() {
        const formData = new FormData();

        /*
         * The model in the server's shape (student-add-payload.ts). A new student sends
         * all of it. An edit sends only what differs from the record as loaded, plus the
         * id: the server keeps every column the payload does not mention, so the nine
         * fields this wizard used to clear because it had never loaded them (live QA
         * H3), and any field the server masked for this user, stay as they are.
         */
        const full = buildStudentPayload(this.student, {
            isEditMode: this.isEditMode,
            studentId: Number(this.studentId) || 0
        });
        const normalizedData: Record<string, unknown> = this.isEditMode && this.loadedPayload
            ? changedKeys(this.loadedPayload, full)
            : full;

        // Append student data
        const studentDataJson = JSON.stringify(normalizedData);
        formData.append('studentData', studentDataJson);

        if (!this.isEditMode) {
            formData.append('linkedFinanceDraft', JSON.stringify(this.buildLinkedFinanceDraft()));
            // A new student has no id until the request is approved, so the family step
            // travels with the request and is written on approval (it was dropped before).
            const family = this.familyPayload();
            formData.append('parents', JSON.stringify(family.parents));
            formData.append('relatives', JSON.stringify(family.relatives));
        }

        // Append profile picture if changed
        if (this.studentPicture) {
            // Dedicated field (if backend supports separate photo upload)
            formData.append('profilePicture', this.studentPicture);
            // Also include in documents so it's saved in the same path as other documents
            try {
                const inferredName = (this.studentPicture as File).name || `student_${this.studentId || 'new'}_photo`;
                formData.append('documents', this.studentPicture, inferredName);
                formData.append('documentTypes', this.profilePictureDocumentType);
            } catch {
                // A picture value that is not a real File is skipped rather than blocking the save.
            }
        }

        // Append new documents. documentTypes is positionally aligned with documents,
        // so an entry is appended for every file even when no type was picked.
        this.uploadedFiles.forEach((file) => {
            formData.append('documents', file);
            formData.append('documentTypes', file.documentType || '');
        });

        if (this.isEditMode) {
            this.updateStudent(formData);
        } else {
            this.createStudent(formData);
        }
    }

    createStudent(formData: FormData) {
        this.studentAddService.Create(formData).subscribe(
            (response) => {
                const r = response as { message?: string; linkedFinanceWillFollow?: boolean };
                let detail =
                    r?.linkedFinanceWillFollow && r?.message
                        ? r.message
                        : this.translationService.translate('STUDENT_ADD.MSG_STUDENT_ADDED');
                if (response && typeof response === 'object' && 'message' in response && r?.linkedFinanceWillFollow !== true)
                    detail = typeof r.message === 'string' ? r.message : detail;
                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail
                });
                // Queued for approval: no student id exists yet, and the family step
                // went with the request.
                this.afterSuccessfulSave(0);
            },
            (error) => {
                let errorMessage = 'Failed to add student';
                if (error.error?.message) {
                    errorMessage = error.error.message;
                } else if (error.message) {
                    errorMessage = error.message;
                } else if (error.statusText) {
                    errorMessage = `${error.status} ${error.statusText}`;
                }

                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: errorMessage
                });
            }
        );
    }

    updateStudent(formData: FormData) {
        this.studentAddService.updateWithFormData(this.studentId, formData).subscribe(
            (response) => {
                const result = response as { status?: string; documentsPending?: boolean };

                /*
                 * Nothing was changed, so no approval was queued. Stay on the form rather than
                 * closing it — the user pressed Update meaning to save something, and leaving
                 * the wizard open lets them make the edit and retry.
                 */
                if (result?.status === 'NO_CHANGES') {
                    this.messageService.add({
                        severity: 'info',
                        summary: this.translationService.translate('COMMON.INFO'),
                        detail: this.translationService.translate('STUDENT_ADD.MSG_NO_CHANGES'),
                        life: 6000
                    });
                    return;
                }

                // Staged server-side and awaiting approval, so drop the local selection.
                this.uploadedFiles = [];
                this.documentsUpload?.clear();

                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate(
                        result?.documentsPending
                            ? 'STUDENT_ADD.MSG_UPDATE_PENDING_WITH_UPLOADS'
                            : 'STUDENT_ADD.MSG_STUDENT_UPDATED'),
                    life: 7000
                });
                this.afterSuccessfulSave(this.studentId || this.student.studentId || 0);
            },
            (error) => {
                const detail = error.error?.message
                    || (typeof error.error === 'string' ? error.error : null)
                    || error.message
                    || this.translationService.translate('STUDENT_ADD.MSG_FAILED_UPDATE');
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail
                });
            }
        );
    }

    downloadDocument(doc: any) {
        this.studentAddService.downloadDocument(doc.documentId).subscribe(
            (blob: Blob) => {
                // Create a URL for the blob
                const url = window.URL.createObjectURL(blob);

                // Create a temporary link element
                const link = document.createElement('a');
                link.href = url;
                link.download = doc.name; // Set the file name

                // Append to body, click programmatically and remove
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);

                // Clean up the URL
                window.URL.revokeObjectURL(url);

                this.messageService.add({
                    severity: 'success',
                    summary: this.translationService.translate('COMMON.SUCCESS'),
                    detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENT_DOWNLOADED')
                });
            },
            () => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDENT_ADD.MSG_DOCUMENT_DOWNLOAD_ERROR')
                });
            }
        );
    }

    navigateToList(): void {
        if (this.dialogMode) {
            return;
        }
        setTimeout(() => {
            this.router.navigate(['/students']);
        }, 1500);
    }

    /**
     * After a save. An edit writes the family step directly; a new student's family
     * went with the approval request (the student has no id yet), so there is
     * nothing to write here for it — calling /{id}/parents with a typed id is what
     * answered 404 "Student not found" (live QA #25).
     */
    afterSuccessfulSave(resolvedStudentId: number): void {
        // It is the ministry's record now, or a request for one; the draft has
        // done its job and must not be offered again (QA C7 / N1). Nor written
        // back: leaving the page afterwards would save the submitted student.
        this.draftFinished = true;
        this.draftService.clear();
        const sid = Number(resolvedStudentId);
        const id =
            Number.isFinite(sid) && sid > 0
                ? sid
                : this.studentId > 0
                    ? this.studentId
                    : Number(this.student.studentId ?? 0) || 0;

        const finish = () => {
            if (this.dialogMode) {
                setTimeout(() => this.dialogSaved.emit(id), 350);
                return;
            }
            setTimeout(() => {
                this.router.navigate(['/students']);
            }, 1500);
        };

        if (id > 0) {
            this.saveFamilyData(id, finish);
            return;
        }
        finish();
    }

    validateAllSteps(): boolean {
        // Always required
        if (!this.student.studentName?.trim()) {
            this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_NAME') });
            return false;
        }

        if (!this.student.studentGender) {
            this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.GENDER_REQUIRED') });
            return false;
        }

        if (!this.student.studentDegreeId) {
            this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_STUDENT_DEGREE') });
            return false;
        }

        if (!this.student.studentMajorId) {
            this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_STUDENT_MAJOR') });
            return false;
        }

        if (!this.student.studentDelegationDecisionYear || this.student.studentDelegationDecisionYear <= 0) {
            this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_DELEGATION_YEAR') });
            return false;
        }

        // The step's own check runs only on "next"; a save from a later step must not skip it.
        if (!this.validateContactFields()) {
            this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.CONTACT_VALIDATION_ERROR') });
            return false;
        }

        // Additional required only when adding a new student
        if (!this.isEditMode) {
            if (!this.student.studentBirthdate) {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_BIRTH_DATE') });
                return false;
            }

            if (!this.student.studentPassportNumber?.trim()) {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_PASSPORT_NUMBER') });
                return false;
            }

            if (!this.student.studentPassportIssueDate) {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_PASSPORT_ISSUE_DATE') });
                return false;
            }

            if (!this.student.studentPassportExpireDate) {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_PASSPORT_EXPIRY') });
                return false;
            }

            if (!this.student.studentDelegateDecisionNo || this.student.studentDelegateDecisionNo <= 0) {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_DELEGATE_DECISION_NO') });
                return false;
            }

            if (!this.student.studentStudyNoOfYears || this.student.studentStudyNoOfYears <= 0) {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_STUDY_YEARS') });
                return false;
            }

            if (!this.student.studentAmount || this.student.studentAmount <= 0) {
                this.messageService.add({ severity: 'error', summary: this.translationService.translate('COMMON.ERROR'), detail: this.translationService.translate('STUDENT_ADD.MSG_REQUIRED_AMOUNT') });
                return false;
            }
        }

        return true;
    }

    /**
     * Validate if the student name already exists in the database
     */
    validateStudentName() {
        // Validate that name is not empty
        if (!this.student.studentName || !this.student.studentName.trim()) {
            this.messageService.add({
                severity: 'warn',
                summary: this.translationService.translate('COMMON.WARNING'),
                detail: this.translationService.translate('STUDENT_ADD.NAME_REQUIRED')
            });
            return;
        }

        this.checkingDuplicate = true;
        this.nameValidationService.checkDuplicateName(this.student.studentName).subscribe({
            next: (duplicates) => {
                this.checkingDuplicate = false;

                // Filter out the current student if in edit mode
                if (this.isEditMode && this.studentId) {
                    this.duplicateStudents = duplicates.filter(s => s.studentId !== this.studentId);
                } else {
                    this.duplicateStudents = duplicates;
                }

                this.showDuplicateDialog = true;
            },
            error: () => {
                this.checkingDuplicate = false;
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDENT_ADD.DUPLICATE_CHECK_ERROR')
                });
            }
        });
    }

    /**
     * Close the duplicate dialog
     */
    closeDuplicateDialog() {
        this.showDuplicateDialog = false;
    }

    /**
     * Get lookup name from ID
     */
    getLookupName(lookupArray: LookupItem[], id: number | null | undefined): string {
        if (!id || !lookupArray || lookupArray.length === 0) {
            return 'N/A';
        }
        const item = lookupArray.find(option => option.value === id || option.id === id);
        return item ? item.name : 'N/A';
    }

    /**
     * Get country name from ID
     */
    getCountryName(countryId: number | null | undefined): string {
        return this.getLookupName(this.countryOptions, countryId);
    }

    /**
     * Get major name from ID
     */
    getMajorName(majorId: number | null | undefined): string {
        return this.getLookupName(this.studentMajorOptions, majorId);
    }

    /**
     * Get degree name from ID
     */
    getDegreeName(degreeId: number | null | undefined): string {
        return this.getLookupName(this.studentDegreeOptions, degreeId);
    }

    cancel() {
        if (this.dialogMode) {
            this.dialogCancelled.emit();
            return;
        }
        this.router.navigate(['/students']);
    }
} 