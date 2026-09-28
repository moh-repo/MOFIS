import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormArray, FormGroup, Validators } from '@angular/forms';
import { MessageService, ConfirmationService } from 'primeng/api';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, finalize, switchMap, takeUntil } from 'rxjs/operators';
import { StudentTravelTicket, TravelFundingSource, WafarBalanceSummary } from '../../api/student-travel-ticket';
import { StudentTravelTicketService } from '../../service/student-travel-ticket.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { changesOnly } from '../../service/reload-streams.util';
import { resetPaging } from '../../service/table-paging.util';
import { extractApiErrorMessage } from '../../service/api-error.util';
import { UmsPermissionService } from '../../service/ums-permission.service';
import { WAFAR_READ } from '../../service/read-gates';
import { StudentOption, StudentPickerOption, StudentPickerService } from '../../service/student-picker.service';

interface LookupOption {
    id: number | null;
    name: string;
}

/** The type a new ticket starts as; a behaviour key, its name comes from the table. */
const DEFAULT_TRAVEL_TYPE = 'ALONE';
/** The type that carries family members; a behaviour key, its name comes from the table. */
const FAMILY_TRAVEL_TYPE = 'WITH_FAMILY';

@Component({
    selector: 'app-student-travel-tickets',
    templateUrl: './student-travel-tickets.component.html',
    styleUrls: ['./student-travel-tickets.component.scss'],
    providers: [MessageService, ConfirmationService],
    standalone: false
})
export class StudentTravelTicketsComponent implements OnInit, OnDestroy {

    filtersForm: FormGroup;
    travelTicketForm: FormGroup;

    studentOptions: StudentPickerOption[] = [];
    /** The students the pickers hold: one page of a search, plus those the page refers to. */
    private students: StudentOption[] = [];
    private readonly studentSearch$ = new Subject<string>();
    travelTypeOptions: Array<{ label: string; value: string }> = [];
    relationshipOptions: Array<{ label: string; value: string }> = [];
    genderOptions: LookupOption[] = [];
    currencyOptions: LookupOption[] = [];
    fundingSourceOptions: TravelFundingSource[] = [];
    wafarBalance: WafarBalanceSummary | null = null;

    travelTickets: StudentTravelTicket[] = [];
    /** Bound to the table, so a search that shrinks the list starts it on page one (QA B8, N9). */
    first = 0;

    /** When false, dropdown labels refresh with language using static fallbacks. */
    travelKindsFromDb = false;
    relationshipKindsFromDb = false;

    /** One count per travel type the table holds (LU_TRAVEL_TYPE), in its order. */
    summary: {
        total: number;
        byType: Array<{ code: string; label: string; count: number }>;
        totalFamilyMembers: number;
        lastUpdated: Date | null;
    } = {
        total: 0,
        byType: [],
        totalFamilyMembers: 0,
        lastUpdated: null
    };

    /** Keys the form's behaviour hangs on: the new-ticket default and the type that carries family members. */
    readonly defaultTravelType = DEFAULT_TRAVEL_TYPE;
    readonly familyTravelType = FAMILY_TRAVEL_TYPE;

    loading = false;
    modalVisible = false;
    modalSubmitting = false;
    drawerVisible = false;
    formMode: 'create' | 'edit' = 'create';
    selectedTicketId?: number;
    viewingTicket?: StudentTravelTicket;
    deletingIds = new Set<number>();
    today = new Date();

    private readonly destroy$ = new Subject<void>();
    /** The list request in flight; a newer search cancels it so its answer cannot land last. */
    private loadSubscription?: Subscription;
    /** The filters the list was last searched with. */
    private lastFilterKey = '';

    constructor(
        private readonly fb: FormBuilder,
        private readonly travelTicketService: StudentTravelTicketService,
        private readonly lookupService: LookupService,
        private readonly messageService: MessageService,
        private readonly confirmationService: ConfirmationService,
        public readonly translation: TranslationService,
        private readonly permissionService: UmsPermissionService,
        private readonly studentPicker: StudentPickerService
    ) {
        this.filtersForm = this.fb.group({
            studentId: [null],
            travelType: [null]
        });

        this.travelTicketForm = this.fb.group({
            studentId: [null, Validators.required],
            travelType: [DEFAULT_TRAVEL_TYPE, Validators.required],
            travelDate: [null],
            departureCountry: [null],
            destinationCountry: [null],
            departureCity: [null],
            destinationCity: [null],
            airline: [null],
            flightNumber: [null],
            ticketNumber: [null],
            ticketCost: [null],
            currencyId: [null],
            fundingSourceId: [null],
            bookingDate: [null],
            travelPurpose: [null],
            notes: [null],
            // The ticket PDF (review T3): uploaded first, then named here by id.
            documentId: [null],
            familyMembers: this.fb.array([])
        });
    }

    /**
     * Filters search as they change, like the other student lists (review item S10): one
     * request once the user pauses, and none when the pause left the filters as they were.
     */
    ngOnInit(): void {
        this.lastFilterKey = this.filterKey();
        this.filtersForm.valueChanges
            .pipe(debounceTime(400), takeUntil(this.destroy$))
            .subscribe(() => this.applyFilters());
        this.loadLuTravelAndRelationship();
        this.translation.currentLanguage$.pipe(changesOnly(), takeUntil(this.destroy$)).subscribe(() => {
            this.loadLuTravelAndRelationship();
        });
        this.loadMetadata();
        this.loadTravelTickets();
        this.studentSearch$.pipe(
            debounceTime(300),
            distinctUntilChanged(),
            switchMap(term => this.studentPicker.search(term)),
            takeUntil(this.destroy$)
        ).subscribe(rows => this.holdStudents(rows, true));
    }

    /** A search typed into a student picker runs on the server (live QA M9). */
    onStudentFilter(event: { filter?: string }): void {
        this.studentSearch$.next(event?.filter ?? '');
    }

    /**
     * Folds options into the pickers. `replace` swaps in a new search, keeping the
     * students the filter, the form and the listed tickets still name: a `p-select`
     * shows its value's label only while that value is one of its options.
     */
    private holdStudents(rows: StudentOption[], replace = false): void {
        const inUse = [
            this.filtersForm.value?.studentId,
            this.travelTicketForm.value?.studentId,
            ...this.travelTickets.map(t => t.studentId)
        ];
        this.students = replace
            ? this.studentPicker.merge(this.students, rows, inUse)
            : this.studentPicker.mergeAll(this.students, rows);
        // "id — name", as the finance pickers show it, and the pickers filter on that
        // label (live QA R5-30): filtering on the name alone hid the student the server
        // had found by id, so typing an id showed "No results found".
        this.studentOptions = this.studentPicker.toOptions(this.students.filter(s => s.studentName));
    }

    private loadLuTravelAndRelationship(): void {
        this.lookupService.getLuTravelTypesForUi().pipe(takeUntil(this.destroy$)).subscribe(types => {
            this.travelTypeOptions = types;
            this.travelKindsFromDb = types.length > 0;
            this.updateSummary();
        });
        this.lookupService.getLuFamilyRelationshipsForUi().pipe(takeUntil(this.destroy$)).subscribe(rels => {
            this.relationshipOptions = rels;
            this.relationshipKindsFromDb = rels.length > 0;
        });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    /** The travel type's name, from the loaded LU_TRAVEL_TYPE rows. */
    travelTypeLabel(code: string | null | undefined): string {
        if (!code) return '';
        return this.travelTypeOptions.find(t => t.value === code)?.label ?? code;
    }

    /** The summary band: total, one card per type, family members. */
    get summaryColumns(): number {
        return 2 + this.summary.byType.length;
    }

    get familyMembersFormArray(): FormArray {
        return this.travelTicketForm.get('familyMembers') as FormArray;
    }

    loadMetadata(): void {
        // Students: the first page of the type-ahead picker (live QA M9). The whole list
        // was asked for as one page of 1000, which the server caps at 200 of 981; a
        // search now reaches every student in the caller's countries.
        this.studentPicker.search().subscribe(rows => this.holdStudents(rows, true));

        // Load genders
        this.lookupService.getStudentGenders().subscribe({
            next: (data) => {
                this.genderOptions = data;
            },
            error: () => {
                console.error('Failed to load gender options');
            }
        });

        // Load currencies (one currency applies to all costs on a ticket)
        this.lookupService.getStudyCurrencies().subscribe({
            next: (data) => { this.currencyOptions = (data || []).map((c: any) => ({ id: c.id, name: c.name })); },
            error: () => console.error('Failed to load currency options')
        });

        // Load funding sources (Wafar / Ministry / Other, localized)
        this.travelTicketService.getFundingSources().subscribe({
            next: (data) => { this.fundingSourceOptions = data || []; },
            error: () => console.error('Failed to load funding sources')
        });

        // Load the current Wafar balance so the form can warn before submitting a Wafar-funded ticket
        this.loadWafarBalance();
    }

    private loadWafarBalance(): void {
        // A Wafar read (live QA M6): STAFF and the Student Officer open this page without
        // a Wafar code. The server checks the balance when a Wafar-funded ticket is saved.
        if (!this.permissionService.hasAnyPermission(WAFAR_READ)) {
            this.wafarBalance = null;
            return;
        }
        this.travelTicketService.getWafarBalance().subscribe({
            next: (data) => { this.wafarBalance = data; },
            error: () => { this.wafarBalance = null; }
        });
    }

    // ── Cost / funding helpers ────────────────────────────────────────────────

    /** Live total = student ticket cost + sum of each family member's cost. */
    get totalCost(): number {
        const student = Number(this.travelTicketForm.get('ticketCost')?.value) || 0;
        const members = (this.familyMembersFormArray?.controls || [])
            .reduce((sum, ctrl) => sum + (Number(ctrl.get('ticketCost')?.value) || 0), 0);
        return student + members;
    }

    /** True when the selected funding source is Wafar (by its lookup code). */
    get isWafarFunding(): boolean {
        const id = this.travelTicketForm.get('fundingSourceId')?.value;
        const src = this.fundingSourceOptions.find(o => o.id === id);
        return !!src && (src.code || '').toUpperCase() === 'WAFAR';
    }

    /** True when Wafar is the source and its balance cannot cover the total. */
    get wafarInsufficient(): boolean {
        return this.isWafarFunding
            && this.wafarBalance != null
            && this.totalCost > (this.wafarBalance.currentBalance || 0);
    }

    getCurrencyName(id?: number | null): string | null {
        if (id == null) return null;
        return this.currencyOptions.find(c => c.id === id)?.name ?? null;
    }

    getFundingSourceName(id?: number | null): string | null {
        if (id == null) return null;
        return this.fundingSourceOptions.find(f => f.id === id)?.name ?? null;
    }

    loadTravelTickets(): void {
        const filters = this.filtersForm.value ?? {};
        this.loadSubscription?.unsubscribe();
        this.loading = true;
        this.loadSubscription = this.travelTicketService.search({
            studentId: filters.studentId ?? null,
            travelType: filters.travelType ?? null
        }).pipe(
            takeUntil(this.destroy$),
            finalize(() => this.loading = false)
        ).subscribe({
            next: (data) => {
                this.travelTickets = data ?? [];
                this.updateSummary();
                this.nameStudentsOfTickets();
            },
            error: () => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translation.translate('COMMON.ERROR'),
                    detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.MESSAGES.LOAD_TICKETS_ERROR')
                });
                this.travelTickets = [];
                this.updateSummary();
            }
        });
    }

    onTravelTypeChange(): void {
        const travelType = this.travelTicketForm.get('travelType')?.value;
        if (travelType !== FAMILY_TRAVEL_TYPE) {
            // Only the family type carries members; drop them for any other.
            while (this.familyMembersFormArray.length !== 0) {
                this.familyMembersFormArray.removeAt(0);
            }
        }
    }

    addFamilyMember(): void {
        const memberForm = this.fb.group({
            memberName: [null, Validators.required],
            relationship: [null],
            birthdate: [null, Validators.required],
            age: [null],
            ticketCost: [null],
            passportNumber: [null],
            passportExpiryDate: [null],
            gender: [null],
            notes: [null]
        });

        // Calculate age when birthdate changes
        memberForm.get('birthdate')?.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(birthdate => {
            if (birthdate) {
                const age = this.calculateAge(birthdate);
                memberForm.patchValue({ age }, { emitEvent: false });
            }
        });

        this.familyMembersFormArray.push(memberForm);
    }

    removeFamilyMember(index: number): void {
        this.familyMembersFormArray.removeAt(index);
    }

    calculateAge(birthdate: Date | string): number {
        const birth = new Date(birthdate);
        const today = new Date();
        let age = today.getFullYear() - birth.getFullYear();
        const monthDiff = today.getMonth() - birth.getMonth();
        if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
            age--;
        }
        return age;
    }

    // ── Ticket PDF (review T3) ─────────────────────────────────────────────
    /** The attached file's name as shown in the dialog; null when none. */
    ticketFileName: string | null = null;
    ticketUploading = false;

    /** Uploads the chosen PDF for the selected student and keeps its document id in the form. */
    onTicketFileSelected(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        input.value = '';
        if (!file) return;
        const studentId = this.travelTicketForm.get('studentId')?.value;
        if (!studentId) {
            this.messageService.add({ severity: 'warn', summary: this.translation.translate('COMMON.VALIDATION_ERROR'),
                detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.DOCUMENT.STUDENT_FIRST') });
            return;
        }
        if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
            this.messageService.add({ severity: 'warn', summary: this.translation.translate('COMMON.VALIDATION_ERROR'),
                detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.DOCUMENT.PDF_ONLY') });
            return;
        }
        this.ticketUploading = true;
        this.travelTicketService.uploadTicketDocument(studentId, file)
            .pipe(finalize(() => this.ticketUploading = false))
            .subscribe({
                next: res => {
                    this.travelTicketForm.patchValue({ documentId: res.documentId });
                    this.ticketFileName = res.originalFilename || file.name;
                },
                error: err => this.messageService.add({ severity: 'error', summary: this.translation.translate('COMMON.ERROR'),
                    detail: err?.error?.error || this.translation.translate('STUDENT_TRAVEL_TICKETS.DOCUMENT.UPLOAD_FAILED') })
            });
    }

    removeTicketFile(): void {
        this.travelTicketForm.patchValue({ documentId: null });
        this.ticketFileName = null;
    }

    /** Opens a ticket PDF in a new tab. */
    openTicketDocument(documentId: number | null | undefined): void {
        if (!documentId) return;
        this.travelTicketService.viewTicketDocument(documentId).subscribe({
            next: blob => {
                const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }));
                window.open(url, '_blank');
                setTimeout(() => URL.revokeObjectURL(url), 60_000);
            },
            error: () => this.messageService.add({ severity: 'error', summary: this.translation.translate('COMMON.ERROR'),
                detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.DOCUMENT.VIEW_FAILED') })
        });
    }

    openCreate(): void {
        this.ticketFileName = null;
        this.formMode = 'create';
        this.selectedTicketId = undefined;
        this.travelTicketForm.reset({
            travelType: DEFAULT_TRAVEL_TYPE
        });
        while (this.familyMembersFormArray.length !== 0) {
            this.familyMembersFormArray.removeAt(0);
        }
        this.loadWafarBalance();
        this.modalVisible = true;
    }

    openEdit(ticket: StudentTravelTicket): void {
        this.formMode = 'edit';
        this.selectedTicketId = ticket.travelTicketId;
        this.loadWafarBalance();

        // Load full ticket with family members
        if (ticket.travelTicketId) {
            this.travelTicketService.getById(ticket.travelTicketId).subscribe({
                next: (fullTicket) => {
                    this.populateForm(fullTicket);
                    this.modalVisible = true;
                },
                error: () => {
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.MESSAGES.LOAD_DETAILS_ERROR')
                    });
                }
            });
        }
    }

    populateForm(ticket: StudentTravelTicket): void {
        this.travelTicketForm.patchValue({
            studentId: ticket.studentId,
            travelType: ticket.travelType,
            travelDate: ticket.travelDate ? new Date(ticket.travelDate as any) : null,
            departureCountry: ticket.departureCountry,
            destinationCountry: ticket.destinationCountry,
            departureCity: ticket.departureCity,
            destinationCity: ticket.destinationCity,
            airline: ticket.airline,
            flightNumber: ticket.flightNumber,
            ticketNumber: ticket.ticketNumber,
            ticketCost: ticket.ticketCost,
            currencyId: ticket.currencyId,
            fundingSourceId: ticket.fundingSourceId,
            bookingDate: ticket.bookingDate ? new Date(ticket.bookingDate as any) : null,
            travelPurpose: ticket.travelPurpose,
            notes: ticket.notes,
            documentId: ticket.documentId ?? null
        });
        this.ticketFileName = ticket.documentId ? this.translation.translate('STUDENT_TRAVEL_TICKETS.DOCUMENT.ATTACHED') : null;

        // Clear and populate family members
        while (this.familyMembersFormArray.length !== 0) {
            this.familyMembersFormArray.removeAt(0);
        }

        if (ticket.familyMembers && ticket.familyMembers.length > 0) {
            ticket.familyMembers.forEach(member => {
                const memberForm = this.fb.group({
                    memberName: [member.memberName, Validators.required],
                    relationship: [member.relationship],
                    birthdate: [member.birthdate ? new Date(member.birthdate as any) : null, Validators.required],
                    age: [member.age],
                    ticketCost: [member.ticketCost ?? null],
                    passportNumber: [member.passportNumber],
                    passportExpiryDate: [member.passportExpiryDate ? new Date(member.passportExpiryDate as any) : null],
                    gender: [member.gender],
                    notes: [member.notes]
                });

                // Calculate age when birthdate changes
                memberForm.get('birthdate')?.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(birthdate => {
                    if (birthdate) {
                        const age = this.calculateAge(birthdate);
                        memberForm.patchValue({ age }, { emitEvent: false });
                    }
                });

                this.familyMembersFormArray.push(memberForm);
            });
        }
    }

    submitForm(): void {
        if (this.travelTicketForm.invalid) {
            this.travelTicketForm.markAllAsTouched();
            this.messageService.add({
                severity: 'warn',
                summary: this.translation.translate('COMMON.VALIDATION_ERROR'),
                detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.FORM.REQUIRED_FIELDS')
            });
            return;
        }

        if (this.wafarInsufficient) {
            this.messageService.add({
                severity: 'error',
                summary: this.translation.translate('COMMON.ERROR'),
                detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.FORM.WAFAR_INSUFFICIENT')
            });
            return;
        }

        const formValue = this.travelTicketForm.value;
        
        // Family members travel only with the family type
        const familyMembers = formValue.travelType === FAMILY_TRAVEL_TYPE && formValue.familyMembers
            ? formValue.familyMembers.map((member: any) => ({
                ...member,
                birthdate: this.formatDate(member.birthdate),
                passportExpiryDate: this.formatDate(member.passportExpiryDate),
                age: member.age || null
            }))
            : [];
        
        const travelTicket: StudentTravelTicket = {
            studentId: formValue.studentId,
            travelType: formValue.travelType,
            travelDate: this.formatDate(formValue.travelDate),
            departureCountry: formValue.departureCountry || null,
            destinationCountry: formValue.destinationCountry || null,
            departureCity: formValue.departureCity || null,
            destinationCity: formValue.destinationCity || null,
            airline: formValue.airline || null,
            flightNumber: formValue.flightNumber || null,
            ticketNumber: formValue.ticketNumber || null,
            ticketCost: formValue.ticketCost || null,
            currencyId: formValue.currencyId || null,
            fundingSourceId: formValue.fundingSourceId || null,
            bookingDate: this.formatDate(formValue.bookingDate),
            travelPurpose: formValue.travelPurpose || null,
            notes: formValue.notes || null,
            documentId: formValue.documentId || null,
            familyMembers: familyMembers
        };

        this.modalSubmitting = true;
        const operation = this.formMode === 'create'
            ? this.travelTicketService.create(travelTicket)
            : this.travelTicketService.update(this.selectedTicketId!, travelTicket);

        operation.pipe(
            takeUntil(this.destroy$),
            finalize(() => this.modalSubmitting = false)
        ).subscribe({
            next: (response: any) => {
                const detail = (response?.status === 'PENDING' && response?.message)
                    ? response.message
                    : this.translation.translate(this.formMode === 'create'
                        ? 'STUDENT_TRAVEL_TICKETS.MESSAGES.CREATE_SUCCESS'
                        : 'STUDENT_TRAVEL_TICKETS.MESSAGES.UPDATE_SUCCESS');
                this.messageService.add({
                    severity: 'success',
                    summary: this.translation.translate('COMMON.SUCCESS'),
                    detail
                });
                this.closeModal();
                this.loadTravelTickets();
            },
            // The server's own refusal first — a Wafar balance that does not cover
            // the ticket, a pending request on the same student, a missing
            // document — and the generic text only when it said nothing. The
            // dialog used to stay open over a 405 with no explanation at all
            // (live QA H4 / M4, 24 Sep 2026).
            error: (err) => {
                this.messageService.add({
                    severity: 'error',
                    summary: this.translation.translate('COMMON.ERROR'),
                    detail: extractApiErrorMessage(err) || this.translation.translate(this.formMode === 'create'
                        ? 'STUDENT_TRAVEL_TICKETS.MESSAGES.CREATE_ERROR'
                        : 'STUDENT_TRAVEL_TICKETS.MESSAGES.UPDATE_ERROR'),
                    life: 8000
                });
            }
        });
    }

    closeModal(): void {
        this.modalVisible = false;
        this.travelTicketForm.reset({ travelType: DEFAULT_TRAVEL_TYPE });
        while (this.familyMembersFormArray.length !== 0) {
            this.familyMembersFormArray.removeAt(0);
        }
    }

    openView(ticket: StudentTravelTicket): void {
        if (ticket.travelTicketId) {
            this.travelTicketService.getById(ticket.travelTicketId).subscribe({
                next: (fullTicket) => {
                    this.viewingTicket = fullTicket;
                    this.drawerVisible = true;
                },
                error: () => {
                    this.messageService.add({
                        severity: 'error',
                        summary: this.translation.translate('COMMON.ERROR'),
                        detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.MESSAGES.LOAD_DETAILS_ERROR')
                    });
                }
            });
        } else {
            this.viewingTicket = ticket;
            this.drawerVisible = true;
        }
    }

    closeDrawer(): void {
        this.drawerVisible = false;
        this.viewingTicket = undefined;
    }

    editFromView(): void {
        const ticket = this.viewingTicket;
        if (!ticket) return;
        this.drawerVisible = false;
        this.viewingTicket = undefined;
        this.formMode = 'edit';
        this.selectedTicketId = ticket.travelTicketId;
        this.populateForm(ticket);
        // Let the view dialog close animation finish before opening edit
        setTimeout(() => { this.modalVisible = true; }, 150);
    }

    deleteTicket(ticket: StudentTravelTicket): void {
        if (!ticket.travelTicketId) return;

        this.confirmationService.confirm({
            message: this.translation.translate('STUDENT_TRAVEL_TICKETS.MESSAGES.DELETE_CONFIRM'),
            header: this.translation.translate('COMMON.CONFIRM'),
            icon: 'pi pi-exclamation-triangle',
            accept: () => {
                const targetId = ticket.travelTicketId!;
                this.deletingIds.add(targetId);
                this.travelTicketService.delete(targetId).pipe(
                    takeUntil(this.destroy$),
                    finalize(() => this.deletingIds.delete(targetId))
                ).subscribe({
                    next: (response: any) => {
                        const detail = (response?.status === 'PENDING' && response?.message)
                            ? response.message
                            : this.translation.translate('STUDENT_TRAVEL_TICKETS.MESSAGES.DELETE_SUCCESS');
                        this.messageService.add({
                            severity: 'success',
                            summary: this.translation.translate('COMMON.SUCCESS'),
                            detail
                        });
                        this.loadTravelTickets();
                    },
                    error: () => {
                        this.messageService.add({
                            severity: 'error',
                            summary: this.translation.translate('COMMON.ERROR'),
                            detail: this.translation.translate('STUDENT_TRAVEL_TICKETS.MESSAGES.DELETE_ERROR')
                        });
                    }
                });
            }
        });
    }

    clearFilters(): void {
        this.filtersForm.reset();
        this.applyFilters();
    }

    /** Searches when the filters differ from the last search; `force` searches regardless. */
    applyFilters(force = false): void {
        const key = this.filterKey();
        if (!force && key === this.lastFilterKey) return;
        this.lastFilterKey = key;
        resetPaging(this);
        this.loadTravelTickets();
    }

    /** The filters as the server sees them: blank text is no filter. */
    private filterKey(): string {
        const value = this.filtersForm.getRawValue() as Record<string, unknown>;
        return JSON.stringify(Object.keys(value).sort().map(k => {
            const v = value[k];
            return typeof v === 'string' ? (v.trim() || null) : (v ?? null);
        }));
    }


    /** Names for the students the listed tickets refer to, which the picker's page rarely holds. */
    private nameStudentsOfTickets(): void {
        const known = new Set(this.students.map(s => s.studentId));
        const missing = this.travelTickets.map(t => t.studentId).filter(id => id != null && !known.has(id));
        if (!missing.length) return;
        this.studentPicker.byIds(missing).pipe(takeUntil(this.destroy$)).subscribe(rows => this.holdStudents(rows));
    }

    getStudentName(studentId: number): string {
        const student = this.students.find(s => s.studentId === studentId);
        return student?.studentName || `Student ${studentId}`;
    }

    getMemberCountText(count: number): string {
        const baseText = this.translation.translate('STUDENT_TRAVEL_TICKETS.TABLE.MEMBER_COUNT');
        return baseText.replace('{count}', count.toString());
    }

    private formatDate(value?: Date | null): string | null {
        if (!value) {
            return null;
        }
        const year = value.getFullYear();
        const month = `${value.getMonth() + 1}`.padStart(2, '0');
        const day = `${value.getDate()}`.padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    getRelationshipLabel(value: string): string {
        const option = this.relationshipOptions.find(opt => opt.value === value);
        return option?.label || value;
    }

    get modalTitle(): string {
        return this.translation.translate(
            this.formMode === 'create'
                ? 'STUDENT_TRAVEL_TICKETS.FORM.TITLE_ADD'
                : 'STUDENT_TRAVEL_TICKETS.FORM.TITLE_EDIT'
        );
    }

    private updateSummary(): void {
        const normalized = this.travelTickets ?? [];
        const byType = this.travelTypeOptions.map(type => ({
            code: type.value,
            label: type.label,
            count: normalized.filter(item => item.travelType === type.value).length
        }));

        const totalFamilyMembers = normalized
            .filter(item => item.travelType === FAMILY_TRAVEL_TYPE && item.familyMembers)
            .reduce((sum, item) => sum + (item.familyMembers?.length || 0), 0);

        const mostRecent = normalized
            .map(item => item.modifiedDate || item.createdDate)
            .filter((date): date is string => !!date)
            .map(dateStr => new Date(dateStr))
            .filter((date): date is Date => !isNaN(date.getTime()))
            .sort((a, b) => b.getTime() - a.getTime())[0] ?? null;

        this.summary = {
            total: normalized.length,
            byType,
            totalFamilyMembers,
            lastUpdated: mostRecent
        };
    }
}

