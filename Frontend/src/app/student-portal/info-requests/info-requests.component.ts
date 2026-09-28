import { Component, OnInit } from '@angular/core';
import { StudentPortalApiService, StudentInfoRequest } from '../student-portal-api.service';
import { finalize } from 'rxjs/operators';
import { TranslationService } from '../../demo/service/translation.service';
import { ToastService } from '../../demo/service/toast.service';
import { portalEnumLabel } from '../student-portal-i18n.helper';
import { CodeListService } from '../../demo/service/code-list.service';

@Component({
    selector: 'app-student-portal-info-requests',
    templateUrl: './info-requests.component.html',
    styleUrls: ['./info-requests.component.scss'],
    standalone: false
})
export class InfoRequestsComponent implements OnInit {

  requests: StudentInfoRequest[] = [];
  totalElements = 0;
  totalPages = 0;
  page = 0;
  size = 10;
  loading = true;
  selected: StudentInfoRequest | null = null;
  showDetailModal = false;
  responseText = '';
  responseFile: File | null = null;
  submitting = false;
  submitErrorKey = '';
  submitError = '';

  constructor(
    private api: StudentPortalApiService,
    private translationService: TranslationService,
    private toast: ToastService,
    private codeLists: CodeListService
  ) {}

  /**
   * Status filter, applied by the server.
   *
   * <p>The endpoint takes a status, so this narrows the whole list rather than
   * the page in view. A client-side box here would have searched only the ten
   * rows already fetched and reported "nothing" for a request sitting on page
   * two — which is why Inquiries, whose endpoint takes no filter, has none.
   */
  statusFilter: string | null = null;

  /**
   * The statuses the server assigns (InfoRequestStatus); the labels come from
   * the code list. The list used to offer CLOSED, which no request ever holds,
   * and neither OVERDUE nor CANCELLED, which they do (live QA L9, 24 Sep 2026).
   */
  readonly statusOptions = ['PENDING', 'OVERDUE', 'RESPONDED', 'CANCELLED'];

  onStatusFilterChange(): void {
    // A narrower list is a different list: page 3 of the old one is meaningless.
    this.page = 0;
    this.load();
  }

  onQueryParams(e: { pageIndex: number; pageSize: number }): void {
    this.page = e.pageIndex - 1;
    this.size = e.pageSize;
    this.load();
  }

  getStatusColor(status: string): string {
    return this.codeLists.nzColor('info-request-status', status);
  }

  getReviewColor(review: string): string {
    return this.codeLists.nzColor('info-review-status', review);
  }

  requestStatusLabel(status: string | null | undefined): string {
    return portalEnumLabel(this.codeLists, 'info-request-status', status);
  }

  /** STATUS_CERT → "Study status certificate" (QA SP-007). */
  kindLabel(kind: string | null | undefined): string {
    return portalEnumLabel(this.codeLists, 'info-request-kind', kind);
  }

  reviewStatusLabel(review: string | null | undefined): string {
    return portalEnumLabel(this.codeLists, 'info-review-status', review);
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    // A failed load used to look like "no requests".
    this.api.getInfoRequests(this.page, this.size, this.statusFilter || undefined)
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: (res) => {
          this.requests = res.content || [];
          this.totalElements = res.totalElements ?? 0;
          this.totalPages = res.totalPages ?? 0;
        },
        error: () => this.toast.error(this.translationService.translate('STUDENT_PORTAL.LOAD_INFO_REQUESTS_ERROR'))
      });
  }

  viewDetail(req: StudentInfoRequest): void {
    this.api.getInfoRequest(req.id).subscribe({
      next: (r) => {
        this.selected = r;
        this.responseText = '';
        this.responseFile = null;
        this.submitErrorKey = '';
        this.submitError = '';
        this.showDetailModal = true;
      },
      error: () => {
        this.submitErrorKey = 'STUDENT_PORTAL.LOAD_INFO_REQUEST_DETAIL_ERROR';
        this.submitError = '';
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length) this.responseFile = input.files[0];
  }

  closeDetail(): void {
    this.selected = null;
    this.showDetailModal = false;
  }

  /**
   * Can respond: PENDING or OVERDUE, or an answer that was sent back (RESPONDED + REJECTED).
   * A cancelled request whose earlier answer had been rejected used to count as answerable
   * (live QA M15); the server refuses it, so the form is not offered.
   */
  canRespond(): boolean {
    if (!this.selected) return false;
    if (this.selected.reviewStatus === 'APPROVED') return false;
    if (this.selected.status === 'PENDING' || this.selected.status === 'OVERDUE') return true;
    return this.selected.status === 'RESPONDED' && this.selected.reviewStatus === 'REJECTED';
  }

  /** Request is approved – student cannot edit. */
  isApproved(): boolean {
    return !!this.selected && this.selected.reviewStatus === 'APPROVED';
  }

  submitResponse(includeMyData = false): void {
    if (!this.selected) return;
    // There was no check at all here: both buttons posted whatever was in the
    // form, so an empty response could be filed against the request and would
    // read, to whoever reviews it, as though the student had answered.
    if (!this.responseText.trim() && !this.responseFile) {
      this.submitErrorKey = 'STUDENT_PORTAL.RESPONSE_REQUIRED';
      this.submitError = '';
      return;
    }
    this.submitErrorKey = '';
    this.submitError = '';
    this.submitting = true;
    const done = (updated: StudentInfoRequest) => {
      this.submitting = false;
      this.selected = updated;
      this.load();
    };
    const err = (e: any) => {
      this.submitting = false;
      const apiMsg = e.error?.error;
      if (apiMsg) {
        this.submitError = apiMsg;
        this.submitErrorKey = '';
      } else {
        this.submitError = '';
        this.submitErrorKey = 'STUDENT_PORTAL.SUBMIT_RESPONSE_ERROR';
      }
    };
    if (includeMyData) {
      this.api.getMyData().subscribe({
        next: (data) => {
          this.api.respondToInfoRequest(
            this.selected!.id,
            this.responseText || undefined,
            this.responseFile || undefined,
            data.student,
            data.finance
          ).subscribe({ next: done, error: err });
        },
        error: err
      });
    } else {
      this.api.respondToInfoRequest(
        this.selected.id,
        this.responseText || undefined,
        this.responseFile || undefined
      ).subscribe({ next: done, error: err });
    }
  }

  downloadResponseDoc(): void {
    if (!this.selected || !this.selected.responseDocumentId) return;
    this.api.downloadNamedDocument(this.selected.responseDocumentId, 'response-document').subscribe({
      next: ({ blob, filename }) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        // The name it was uploaded under, extension included (L12, P15).
        a.download = filename;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.submitErrorKey = 'STUDENT_PORTAL.DOWNLOAD_DOCUMENT_ERROR';
        this.submitError = '';
      }
    });
  }

}
