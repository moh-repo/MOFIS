import { Component, OnDestroy, OnInit, ViewChild, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfirmationService, MenuItem, MessageService } from "primeng/api";
import { firstValueFrom, Subject } from "rxjs";
import { debounceTime } from 'rxjs/operators';
import { Table } from "primeng/table";
import { StudentdataService } from '../../service/studentdata.service';
import { Studentdata } from '../../api/studentdata';
import { Router } from '@angular/router';
import { StudentdataAddService } from '../../service/studentdata-add.service';
import { StudentCountry } from '../../api/student-country';
import { studentCountryService } from '../../service/studentcountry.service';
import { StudentDocumentService } from '../../service/student-document.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { StudentAddComponent } from '../student-add/student-add.component';
import { followPage } from '../../service/table-paging.util';


interface expandedRows {
  [key: string]: boolean;
}

@Component({
    templateUrl: './students.component.html',
    styleUrls: ['./students.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentsComponent implements OnInit, OnDestroy {

  statuses: any[] = [];

  rowGroupMetadata: any;

  expandedRows: expandedRows = {};

  activityValues: number[] = [0, 100];

  isExpanded: boolean = false;

  idFrozen: boolean = false;

  loading: boolean = true;

  students: Studentdata[] = [];
  totalElements: number = 0;

  @ViewChild('dt') table!: Table;

  @ViewChild('studentFormEmbed') studentFormEmbed?: StudentAddComponent;

  studentFormModalVisible = false;

  /** null when creating; student id when editing — applied when modal opens */
  private studentFormOpenWithId: number | null = null;

  student: Studentdata = new Studentdata();
  selectedStudents: Studentdata[] = [];

  totalRecords: number = 0;
  rowsPerPageOptions = [5, 10, 25, 50];

  deleteStudentDialog: boolean = false;
  deleteStudentsDialog: boolean = false;

  newStudent: Studentdata = new Studentdata();
  viewDialog: boolean = false;
  /**
   * Both bound to the table (QA B8, N9). They were not: a filter loaded 5 rows
   * into a table that paged by 10, and reset a `first` the table never saw.
   */
  first: number = 0;
  rows: number = 10;

  // Filter properties
  filterStudentId: string = '';
  filterStudentName: string = '';
  filterPassport: string = '';
  selectedStatus: number | null = null;
  dateRange: Date[] = [];

  // Status filter popup menu
  statusFilterMenuItems: MenuItem[] = [];
  @ViewChild('statusFilterMenu') statusFilterMenu: any;
  studentStudyStatusList: any[] = [];

  // Sort properties
  currentSortField: string = '';
  currentSortOrder: string = '';

  // Lookup data
  countries: StudentCountry[] = [];
  countryMap: Map<number, string> = new Map();

  // View dialog: profile photo
  viewStudentPhotoSrc = '';
  viewStudentPhotoPlaceholder = false;
  private _viewPhotoBlobUrl: string | null = null;

  /**
   * Student whose documents and history the view dialog shows. Handed to
   * app-student-documents and app-student-document-history, which own the loading.
   */
  viewedStudentId: number | null = null;

  // Lookup maps for better display
  genderMap: Map<number, string> = new Map();
  degreeMap: Map<number, string> = new Map();
  majorMap: Map<number, string> = new Map();
  studyTypeMap: Map<number, string> = new Map();
  agencyTypeMap: Map<number, string> = new Map();
  ministryTypeMap: Map<number, string> = new Map();
  universityTypeMap: Map<number, string> = new Map();
  studyStatusMap: Map<number, string> = new Map();
  currencyMap: Map<number, string> = new Map();
  governmentMap: Map<number, string> = new Map();
  municipalityMap: Map<number, string> = new Map();
  studyCountryInMap: Map<number, string> = new Map();
  studyGovInMap: Map<number, string> = new Map();
  studyUniversityInMap: Map<number, string> = new Map();
  studyCollegeInMap: Map<number, string> = new Map();
  studyEducationInMap: Map<number, string> = new Map();
  studyMajorInMap: Map<number, string> = new Map();

  // Approval status
  pendingStudentIds: Set<string> = new Set();

  // currentLanguage$ never completes, so the subscription is tied to the component lifetime.
  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private studentDataService: StudentdataService,
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
    private router: Router,
    private studentService: StudentdataAddService,
    private countryService: studentCountryService,
    private studentDocumentService: StudentDocumentService,
    private lookupService: LookupService,
    public translationService: TranslationService
  ) { }

  /** Keystrokes in the filter boxes, debounced in `ngOnInit`. */
  private readonly filterInput$ = new Subject<void>();
  /** The filters the list was last loaded with; a pause that changed nothing sends nothing. */
  private lastFilterKey = '||';
  /** Numbers each list request so a slow earlier one cannot overwrite a newer result. */
  private loadSeq = 0;

  ngOnInit() {
    // One request once the user stops typing, not one per keystroke (review S8).
    this.filterInput$.pipe(debounceTime(400), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());
    this.loadStudents({ page: "0", size: "10" });
    this.loadCountries();
    this.loadAllLookupData();
    this.loadStudentStudyStatuses();

    // Update status filter menu when language changes
    this.translationService.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.buildStatusFilterMenu();
    });
  }

  /**
   * Load student study statuses from lookup table
   */
  loadStudentStudyStatuses() {
    this.lookupService.getStudentStudyStatuses().subscribe(
      (data) => {
        this.studentStudyStatusList = data;
        this.buildStatusFilterMenu();
      },
      (error) => {
        console.error('Error loading student study statuses:', error);
      }
    );
  }

  /**
   * Build status filter menu items from lookup data
   */
  buildStatusFilterMenu() {
    const menuItems: MenuItem[] = [];

    // Add "All" option
    menuItems.push({
      label: this.translationService.translate('STUDENTS.FILTERS.ALL_STATUSES') || 'All Statuses',
      icon: this.selectedStatus === null ? 'pi pi-check' : 'pi pi-filter-slash',
      command: () => this.clearStatusFilter()
    });

    // Add separator
    menuItems.push({
      separator: true
    });

    // Add each status from lookup table
    this.studentStudyStatusList.forEach((status) => {
      menuItems.push({
        label: status.name,
        icon: this.selectedStatus === status.id ? 'pi pi-check' : 'pi pi-circle',
        command: () => this.applyStatusFilter(status.id)
      });
    });

    this.statusFilterMenuItems = menuItems;
  }

  onViewStudentPhotoLoadError(_event: Event): void {
    this.viewStudentPhotoSrc = '';
    this.viewStudentPhotoPlaceholder = true;
  }

  loadCountries() {
    this.countryService.getAll().subscribe(
      (data) => {
        this.countries = data as StudentCountry[];
        // Create a map for quick lookup
        this.countries.forEach(country => {
          // The API row carries `id`; the old model's `studentCountryId` never existed on the wire.
          this.countryMap.set(country.id, country.studentCountryName);
        });
      },
      (error) => {
        console.error('Error loading countries:', error);
      }
    );
  }

  loadAllLookupData() {
    // Load gender options
    this.lookupService.getStudentGenders().subscribe(data => {
      data.forEach(item => {
        this.genderMap.set(item.id, item.name);
      });
    });

    // Load degree options
    this.lookupService.getStudentDegrees().subscribe(data => {
      data.forEach(item => {
        this.degreeMap.set(item.id, item.name);
      });
    });

    // Load major options
    this.lookupService.getStudentMajors().subscribe(data => {
      data.forEach(item => {
        this.majorMap.set(item.id, item.name);
      });
    });

    // Load study type options
    this.lookupService.getStudentStudyTypes().subscribe(data => {
      data.forEach(item => {
        this.studyTypeMap.set(item.id, item.name);
      });
    });

    // Load agency type options
    this.lookupService.getAgencyTypes().subscribe(data => {
      data.forEach(item => {
        this.agencyTypeMap.set(item.id, item.name);
      });
    });

    // Load ministry type options
    this.lookupService.getMinistryTypes().subscribe(data => {
      data.forEach(item => {
        this.ministryTypeMap.set(item.id, item.name);
      });
    });

    // Load university type options
    this.lookupService.getUniversityTypes().subscribe(data => {
      data.forEach(item => {
        this.universityTypeMap.set(item.id, item.name);
      });
    });

    // Load study status options (already loaded in loadStudentStudyStatuses)
    this.lookupService.getStudentStudyStatuses().subscribe(data => {
      data.forEach(item => {
        this.studyStatusMap.set(item.id, item.name);
      });
    });

    // Load study currencies
    this.lookupService.getStudyCurrencies().subscribe(data => {
      data.forEach(item => {
        this.currencyMap.set(Number(item.id), item.name);
      });
    });

    // Load governments
    this.lookupService.getGovernments().subscribe(data => {
      data.forEach(item => {
        this.governmentMap.set(item.id, item.name);
      });
    });

    // Load municipalities
    this.lookupService.getMunicipalities().subscribe(data => {
      data.forEach(item => {
        this.municipalityMap.set(item.id, item.name);
      });
    });

    // Load study countries
    this.lookupService.getStudyCountriesIn().subscribe(data => {
      data.forEach(item => {
        this.studyCountryInMap.set(item.id, item.name);
      });
    });

    // Load study governorates
    this.lookupService.getStudyGovsIn().subscribe(data => {
      data.forEach(item => {
        this.studyGovInMap.set(item.id, item.name);
      });
    });

    // Load study universities
    this.lookupService.getStudyUniversitiesIn().subscribe(data => {
      data.forEach(item => {
        this.studyUniversityInMap.set(item.id, item.name);
      });
    });

    // Load study colleges
    this.lookupService.getStudyCollegesIn().subscribe(data => {
      data.forEach(item => {
        this.studyCollegeInMap.set(item.id, item.name);
      });
    });

    // Load study educations
    this.lookupService.getStudyEducationsIn().subscribe(data => {
      data.forEach(item => {
        this.studyEducationInMap.set(item.id, item.name);
      });
    });

    // Load study majors
    this.lookupService.getStudyMajorsIn().subscribe(data => {
      data.forEach(item => {
        this.studyMajorInMap.set(item.id, item.name);
      });
    });
  }

  onSort() {
    this.updateRowGroupMetaData();
  }

  updateRowGroupMetaData() {
    this.rowGroupMetadata = {};
  }

  onFilterChange() {
    this.filterInput$.next();
  }

  clearFilters() {
    this.filterStudentId = '';
    this.filterStudentName = '';
    this.filterPassport = '';
    this.applyFilters();
  }

  /** Reloads from the first page when the filters differ from those last loaded. */
  private applyFilters(): void {
    const key = [this.filterStudentId, this.filterStudentName, this.filterPassport]
      .map(v => (v ?? '').trim()).join('|');
    if (key === this.lastFilterKey) return;
    this.lastFilterKey = key;
    this.first = 0;
    this.loadStudents({ page: "0", size: this.rows.toString(), sortField: this.currentSortField, sortOrder: this.currentSortOrder });
  }

  loadStudents(request: { page: string; size: string; sortField?: string; sortOrder?: string }) {
    this.loading = true;
    const seq = ++this.loadSeq;
    const parsedId = parseInt(this.filterStudentId, 10);

    this.studentDataService.getPagingAll({
      page: request.page,
      size: request.size,
      sortField: request.sortField,
      sortOrder: request.sortOrder,
      statusId: this.selectedStatus,
      filterStudentId: this.filterStudentId && !isNaN(parsedId) ? parsedId : null,
      filterStudentName: this.filterStudentName || undefined,
      filterPassport: this.filterPassport || undefined
    }).subscribe(
      (response: any) => {
        if (seq !== this.loadSeq) return;
        let students = [];
        if (response) {
          students = Array.isArray(response) ? response : (response.content || []);
          this.totalRecords = Array.isArray(response) ? response.length : (response.totalElements || 0);
        } else {
          students = [];
          this.totalRecords = 0;
        }

        this.students = students;
        this.loading = false;

        // Load pending status after students are loaded
        this.loadPendingStatus();
      },
      (error) => {
        if (seq !== this.loadSeq) return;
        console.error('API Error:', error); // Debug log
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENTS.MSG_LOAD_ERROR')
        });
        this.loading = false;
      }
    );
  }

  openAddStudent() {
    this.studentFormOpenWithId = null;
    this.studentFormModalVisible = true;
  }

  openStudentFormModalShown(): void {
    this.studentFormEmbed?.prepareDialog(this.studentFormOpenWithId);
  }

  studentFormModalTitle(): string {
    const id = this.studentFormOpenWithId;
    if (typeof id === 'number' && id > 0) {
      return this.translationService.translate('STUDENTS.EDIT_STUDENT');
    }
    return this.translationService.translate('STUDENTS.NEW_STUDENT');
  }

  onStudentFormSaved(_studentId: number): void {
    this.studentFormModalVisible = false;
    const pageIdx = Math.floor(this.first / Math.max(this.rows, 1));
    this.loadStudents({ page: Math.max(0, pageIdx).toString(), size: this.rows.toString(), sortField: this.currentSortField, sortOrder: this.currentSortOrder });
    this.loadPendingStatus();
  }

  openEditStudent(student: Studentdata) {
    const id = student?.studentId;
    this.studentFormOpenWithId = typeof id === 'number' && id > 0 ? id : null;
    this.studentFormModalVisible = true;
  }


  deleteStudent(student: Studentdata) {
    this.deleteStudentDialog = true;
    this.student = { ...student };
  }

  deleteSelectedStudents() {
    this.deleteStudentsDialog = true;
  }

  confirmDeleteSelected() {
    this.deleteStudentsDialog = false;

    // Create an array of promises for each delete operation
    const deletePromises = this.selectedStudents.map(student =>
      firstValueFrom(this.studentDataService.Delete(student.studentId))
    );

    // Execute all delete operations
    Promise.all(deletePromises)
      .then(() => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('STUDENTS.MSG_STUDENTS_DELETED'),
          life: 3000
        });
        this.selectedStudents = [];
        this.first = 0;
        this.loadStudents({ page: "0", size: this.rows.toString() });
      })
      .catch(() => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENTS.MSG_DELETE_SELECTED_ERROR')
        });
      });
  }

  confirmDelete() {
    this.deleteStudentDialog = false;
    this.studentDataService.Delete(this.student.studentId).subscribe(
      () => {
        this.messageService.add({
          severity: 'success',
          summary: this.translationService.translate('COMMON.SUCCESS'),
          detail: this.translationService.translate('STUDENTS.MSG_STUDENT_DELETED'),
          life: 3000
        });
        this.first = 0;
        this.loadStudents({ page: "0", size: this.rows.toString() });
      },
      () => {
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('STUDENTS.MSG_DELETE_ERROR')
        });
      }
    );
  }


  onPageChange(event: any) {

    // Update sort information if provided
    if (event.sortField) {
      this.currentSortField = event.sortField;
      this.currentSortOrder = event.sortOrder === 1 ? 'asc' : 'desc';
    }

    const at = followPage(this, event);
    this.loadStudents({
      page: at.page.toString(),
      size: at.rows.toString(),
      sortField: this.currentSortField,
      sortOrder: this.currentSortOrder
    });
  }

  /**
   * Handle right-click on status header to show popup menu
   */
  onStatusHeaderRightClick(event: MouseEvent) {
    event.preventDefault();
    event.stopPropagation();

    // Build menu with current selection state
    this.buildStatusFilterMenu();

    // Show popup menu at cursor position
    if (this.statusFilterMenu) {
      this.statusFilterMenu.toggle(event);
    }
  }

  /**
   * Apply status filter from popup menu
   */
  applyStatusFilter(statusId: number) {
    this.selectedStatus = statusId;
    this.first = 0;
    this.loadStudents({ page: "0", size: this.rows.toString(), sortField: this.currentSortField, sortOrder: this.currentSortOrder });
  }

  clearStatusFilter() {
    this.selectedStatus = null;
    this.first = 0;
    this.loadStudents({ page: "0", size: this.rows.toString(), sortField: this.currentSortField, sortOrder: this.currentSortOrder });
  }

  editStudent(student: any) {
    const id = student?.studentId;
    this.studentFormOpenWithId = typeof id === 'number' && id > 0 ? id : null;
    this.studentFormModalVisible = true;
  }

  // Add view method
  viewStudent(student: Studentdata) {
    this.newStudent = { ...student };
    this._revokeViewPhotoBlobUrl();
    this.viewStudentPhotoSrc = '';
    this.viewStudentPhotoPlaceholder = false;

    const id = student?.studentId;
    if (typeof id === 'number' && id > 0) {
      // Same resolver the profile and edit pages use, so all three always show the
      // latest photo: the student's picture when present, otherwise a picture-like document.
      this.studentDocumentService.resolveStudentPhoto(id).subscribe({
        next: (blob) => {
          if (blob) {
            this._revokeViewPhotoBlobUrl();
            this._viewPhotoBlobUrl = URL.createObjectURL(blob);
            this.viewStudentPhotoSrc = this._viewPhotoBlobUrl;
            this.viewStudentPhotoPlaceholder = false;
          } else {
            this.viewStudentPhotoSrc = '';
            this.viewStudentPhotoPlaceholder = true;
          }
        },
        error: () => {
          this.viewStudentPhotoSrc = '';
          this.viewStudentPhotoPlaceholder = true;
        }
      });
    } else {
      this.viewStudentPhotoPlaceholder = true;
    }

    this.viewDialog = true;
    this.viewedStudentId = student.studentId ?? null;
  }

  ngOnDestroy(): void {
    this._revokeViewPhotoBlobUrl();
  }

  private _revokeViewPhotoBlobUrl(): void {
    if (this._viewPhotoBlobUrl) {
      URL.revokeObjectURL(this._viewPhotoBlobUrl);
      this._viewPhotoBlobUrl = null;
    }
  }

  /** Full 360 profile (finance, ledger, delegation, loans, …). */
  openStudentProfile(student: Studentdata) {
    if (!student?.studentId) {
      return;
    }
    this.router.navigate(['/students', 'profile', student.studentId]);
  }

  /** True when this student has an undecided approval request. */
  isStudentPending(studentId: number | undefined): boolean {
    return studentId != null && this.pendingStudentIds.has(String(studentId));
  }

  loadPendingStatus() {
    this.studentDataService.getPendingStudentIds().subscribe(
      (ids) => {
        this.pendingStudentIds = new Set(ids);
      },
      (error) => {
        console.error('Error loading pending status:', error);
      }
    );
  }

  getStudyStatusSeverity(statusId: number | null | undefined): 'success' | 'info' | 'warning' | 'danger' | 'secondary' {
    const id = Number(statusId);
    if (id === 2) return 'success';
    if (id === 1) return 'info';
    if (id === 3) return 'warning';
    if (id === 4) return 'info';
    if (id === 6 || id === 7) return 'danger';
    return 'secondary';
  }
}
