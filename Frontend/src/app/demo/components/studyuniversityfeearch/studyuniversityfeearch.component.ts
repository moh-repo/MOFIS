import { Component, OnInit, OnDestroy, ViewChild } from '@angular/core';
import { Table } from 'primeng/table';
import { MessageService } from 'primeng/api';
import { HttpClient } from '@angular/common/http';
import { StudyUniversityFeeArchService } from '../../service/studyuniversityfeearch.service';
import { LookupService } from '../../service/lookup.service';
import { StudyUniversityFeeArch } from '../../api/studyuniversityfeearch';
import { TranslationService } from '../../service/translation.service';
import { changesOnly } from '../../service/reload-streams.util';

@Component({
    selector: 'app-studyuniversityfeearch',
    templateUrl: './studyuniversityfeearch.component.html',
    providers: [MessageService],
    standalone: false
})
/**
 * Fee History: the archive of the university-fee records, read-only.
 *
 * <p>It offered "Add New", edit and delete dialogs on the archive rows until the
 * live test of 24 Sep 2026 (H2); the archive is written by the quarter close and
 * the API no longer accepts a write, so the screen shows the log, a view dialog,
 * and nothing else — as Student History does.
 */
export class StudyUniversityFeeArchComponent implements OnInit, OnDestroy {
    @ViewChild('dt') dt: Table | undefined;

    studyUniversityFeeArchList: StudyUniversityFeeArch[] = [];
    studyUniversityFeeArchDetails: StudyUniversityFeeArch[] = [];
    /** The row the view dialog shows. */
    newStudyUniversityFeeArch: StudyUniversityFeeArch = new StudyUniversityFeeArch();
    selectedStudyUniversityFeeArch: StudyUniversityFeeArch[] = [];
    loading: boolean = true;
    totalElements: number = 0;
    rows: number = 10;
    rowsPerPageOptions: number[] = [5, 10, 20];
    currentPage: number = 0;
    globalFilter: string = '';
    dateRange: Date[] = [];
    selectedStatus: any = null;
    viewDialog: boolean = false;

    // Master-Detail data
    masterStudents: any[] = [];
    selectedStudent: any = null;
    detailRecords: StudyUniversityFeeArch[] = [];
    studentSearchTerm: string = '';

    // Payment status options
    paymentStatuses: any[] = [];

    // Currency options
    currencies: any[] = [];
    private languageSubscription?: any;

    constructor(
        private studyUniversityFeeArchService: StudyUniversityFeeArchService,
        private lookupService: LookupService,
        private httpClient: HttpClient,
        private messageService: MessageService,
        public translationService: TranslationService
    ) {}

    ngOnInit() {
        this.initializePaymentStatuses();
        this.loadCurrencies();
        this.loadMasterStudents();
        
        this.languageSubscription = this.translationService.currentLanguage$.pipe(changesOnly()).subscribe(() => {
            this.initializePaymentStatuses();
            this.loadCurrencies();
        });
    }

    ngOnDestroy() {
        if (this.languageSubscription) {
            this.languageSubscription.unsubscribe();
        }
    }

    initializePaymentStatuses() {
        this.lookupService.getUniversityFeeStatuses().subscribe({
            next: opts => { this.paymentStatuses = opts; },
            error: () => { this.paymentStatuses = []; }
        });
    }

    loadCurrencies() {
        this.lookupService.getStudyCurrencies().subscribe({
            next: (data) => {
                this.currencies = (data || []).map(item => ({
                    label: item.name || item.label || '',
                    value: item.id ?? item.value
                }));
            },
            error: () => { this.currencies = []; }
        });
    }

    loadStudyUniversityFeeArch() {
        this.loading = true;
        
        // First try the paging method
        this.currentPage = 0;
        this.studyUniversityFeeArchService.getPagingAll({ page: '0', size: this.rows.toString() }).subscribe({
            next: (data) => {
                if (data && data.content) {
                    this.studyUniversityFeeArchList = data.content.map(record => {
                        return record;
                    });
                    this.totalElements = data.totalElements || data.content.length;
                } else {
                    // If paging data is empty, try the non-paging method
                    this.loadAllData();
                }
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading paging data:', error);
                // If paging fails, try the non-paging method
                this.loadAllData();
            }
        });
    }

    loadAllData() {
        this.studyUniversityFeeArchService.getAll().subscribe({
            next: (data) => {
                if (Array.isArray(data)) {
                    this.studyUniversityFeeArchList = data.map(record => {
                        return record;
                    });
                    this.totalElements = this.studyUniversityFeeArchList.length;
                } else {
                    this.messageService.add({ 
                        severity: 'warn', 
                        summary: this.translationService.translate('COMMON.WARNING'),
                        detail: this.translationService.translate('STUDY_UNIVERSITY_FEE_HISTORY.NO_DATA_AVAILABLE'),
                        life: 3000 
                    });
                }
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading all data:', error);
                this.messageService.add({ 
                    severity: 'error', 
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDY_UNIVERSITY_FEE_HISTORY.ERROR_LOADING_DATA'),
                    life: 3000 
                });
                this.loading = false;
            }
        });
    }

    loadMasterStudents() {
        this.loading = true;
        this.studyUniversityFeeArchService.getDistinctStudents().subscribe({
            next: (data) => {
                this.masterStudents = data.map((item: any[]) => ({
                    studentId: item[0],
                    studentName: item[1] || this.translationService.translate('COMMON.UNKNOWN')
                }));
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading master students:', error);
                this.messageService.add({ 
                    severity: 'error', 
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDY_UNIVERSITY_FEE_HISTORY.ERROR_LOADING_STUDENTS'),
                    life: 3000 
                });
                this.loading = false;
            }
        });
    }

    // Removed pagination logic - not needed for master-detail view

    onGlobalFilter(event: Event) {
        if (this.dt) {
            this.dt.filterGlobal((event.target as HTMLInputElement).value, 'contains');
        }
    }

    onStatusFilter(event: any) {
        if (this.dt) {
            this.dt.filter(event.value, 'studyUniversityFeePayStatus', 'equals');
        }
    }

    onDateFilter(_event: any) {
        if (this.dt && this.dateRange && this.dateRange.length === 2) {
            this.dt.filter(this.dateRange, 'studyUniversityFeePayDate', 'dateRange');
        }
    }

    clearFilters() {
        this.globalFilter = '';
        this.selectedStatus = null;
        this.dateRange = [];
        this.selectedStudent = null;
        this.detailRecords = [];
        this.studentSearchTerm = '';
        if (this.dt) {
            this.dt.clear();
        }
    }

    onStudentSelect(student: any) {
        if (student) {
            this.selectedStudent = student;
            this.loadStudentDetails(student.studentId);
        } else {
            this.selectedStudent = null;
            this.detailRecords = [];
        }
    }

    loadStudentDetails(studentId: number) {
        this.loading = true;
        this.studyUniversityFeeArchService.getByStudentId(studentId).subscribe({
            next: (data) => {
                this.detailRecords = data.map(record => {
                    return record;
                });
                this.loading = false;
            },
            error: (error) => {
                console.error('Error loading student details:', error);
                this.messageService.add({ 
                    severity: 'error', 
                    summary: this.translationService.translate('COMMON.ERROR'),
                    detail: this.translationService.translate('STUDY_UNIVERSITY_FEE_HISTORY.ERROR_LOADING_STUDENT_DETAILS'),
                    life: 3000 
                });
                this.loading = false;
            }
        });
    }

    viewStudyUniversityFeeArch(record: StudyUniversityFeeArch) {
        this.newStudyUniversityFeeArch = { ...record };
        this.viewDialog = true;
    }

    hideDialog() {
        this.viewDialog = false;
    }

    getSeverity(status: number) {
        switch (status) {
            case 0:
                return 'warning';
            case 1:
                return 'success';
            case 2:
                return 'danger';
            case 3:
                return 'info';
            default:
                return 'info';
        }
    }

    getPaymentStatusName(status: number): string {
        const statusObj = this.paymentStatuses.find(s => s.value === status);
        return statusObj ? statusObj.label : 'UNKNOWN';
    }

    getCurrencyName(currency: number): string {
        const currencyObj = this.currencies.find(c => c.value === currency);
        return currencyObj ? currencyObj.label : 'UNKNOWN';
    }

    get filteredStudents(): any[] {
        if (!this.studentSearchTerm) {
            return this.masterStudents;
        }
        return this.masterStudents.filter(student => 
            student.studentName.toLowerCase().includes(this.studentSearchTerm.toLowerCase()) ||
            student.studentId.toString().includes(this.studentSearchTerm)
        );
    }
}
