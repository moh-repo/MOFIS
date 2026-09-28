import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { StudentPortalApiService, StudentInquiry } from '../student-portal-api.service';
import { finalize } from 'rxjs/operators';
import { TranslationService } from '../../demo/service/translation.service';
import { ToastService } from '../../demo/service/toast.service';
import { portalEnumLabel } from '../student-portal-i18n.helper';
import { CodeListService } from '../../demo/service/code-list.service';

@Component({
    selector: 'app-student-portal-inquiries',
    templateUrl: './inquiries.component.html',
    styleUrls: ['./inquiries.component.scss'],
    standalone: false
})
export class InquiriesComponent implements OnInit {

  /** FE-04: currentLanguage$ never completes; the subscription ends with the component. */
  private readonly destroyRef = inject(DestroyRef);

  inquiries: StudentInquiry[] = [];
  totalElements = 0;
  totalPages = 0;
  page = 0;
  size = 10;
  loading = true;
  showForm = false;
  /** Inquiry types, from the inquiry-type list; the first one is the form's default. */
  typeOptions: Array<{ label: string; value: string }> = [];
  newType = '';
  newTitle = '';
  newDescription = '';
  submitting = false;
  submitErrorKey = '';
  submitError = '';
  selected: StudentInquiry | null = null;
  showDetailModal = false;

  constructor(
    private api: StudentPortalApiService,
    private translationService: TranslationService,
    private toast: ToastService,
    private codeLists: CodeListService
  ) {}

  onQueryParams(e: { pageIndex: number; pageSize: number }): void {
    this.page = e.pageIndex - 1;
    this.size = e.pageSize;
    this.load();
  }

  ngOnInit(): void {
    this.buildTypeOptions();
    this.translationService.currentLanguage$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.buildTypeOptions());
    this.load();
  }

  private buildTypeOptions(): void {
    this.typeOptions = this.codeLists.options('inquiry-type');
    if (!this.typeOptions.some(o => o.value === this.newType)) {
      this.newType = this.typeOptions[0]?.value ?? '';
    }
  }

  statusColor(status: string | null | undefined): string {
    return this.codeLists.nzColor('inquiry-status', status);
  }

  load(): void {
    this.loading = true;
    // A failed load used to leave an empty table with no word; the student could
    // not tell "no inquiries" from "could not load".
    this.api.getInquiries(this.page, this.size)
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: (res) => {
          this.inquiries = res.content || [];
          this.totalElements = res.totalElements ?? 0;
          this.totalPages = res.totalPages ?? 0;
        },
        error: () => this.toast.error(this.translationService.translate('STUDENT_PORTAL.LOAD_INQUIRIES_ERROR'))
      });
  }

  openForm(): void {
    this.showForm = true;
    this.submitErrorKey = '';
    this.submitError = '';
  }

  closeForm(): void {
    this.showForm = false;
    this.newTitle = '';
    this.newDescription = '';
  }

  /** The one error the form shows under its field rather than in the banner. */
  get titleMissing(): boolean {
    return this.submitErrorKey === 'STUDENT_PORTAL.INQUIRY_TITLE_REQUIRED';
  }

  /** Typing a title answers the "title is required" error; it should not stay red. */
  clearTitleError(): void {
    if (this.titleMissing) this.submitErrorKey = '';
  }

  inquiryTypeLabel(type: string | null | undefined): string {
    return portalEnumLabel(this.codeLists, 'inquiry-type', type);
  }

  inquiryStatusLabel(status: string | null | undefined): string {
    return portalEnumLabel(this.codeLists, 'inquiry-status', status);
  }

  submit(): void | Promise<void> {
    if (!this.newTitle || !this.newTitle.trim()) {
      this.submitErrorKey = 'STUDENT_PORTAL.INQUIRY_TITLE_REQUIRED';
      this.submitError = '';
      return;
    }
    this.submitErrorKey = '';
    this.submitError = '';
    this.submitting = true;
    const desc = this.newDescription ? this.newDescription.trim() : undefined;
    return new Promise((resolve, reject) => {
      this.api.createInquiry(this.newType, this.newTitle.trim(), desc).subscribe({
        next: () => {
          this.submitting = false;
          this.closeForm();
          this.load();
          resolve();
        },
        error: (err) => {
          this.submitting = false;
          const apiMsg = err.error?.error;
          if (apiMsg) {
            this.submitError = apiMsg;
            this.submitErrorKey = '';
          } else {
            this.submitError = '';
            this.submitErrorKey = 'STUDENT_PORTAL.INQUIRY_SUBMIT_ERROR';
          }
          reject();
        }
      });
    });
  }

  viewDetail(inq: StudentInquiry): void {
    this.api.getInquiry(inq.id).subscribe({
      next: (full) => {
        this.selected = full;
        this.showDetailModal = true;
      },
      error: () => {
        this.submitErrorKey = 'STUDENT_PORTAL.INQUIRY_LOAD_DETAIL_ERROR';
        this.submitError = '';
      }
    });
  }

  closeDetail(): void {
    this.selected = null;
    this.showDetailModal = false;
  }
}
