import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { silentErrors } from '../../../interceptors/http-context.tokens';
import { MessageService } from 'primeng/api';
import { PaymentAuth } from '../../../api/payment-auth';
import { PaymentAuthService } from '../../../service/payment-auth.service';
import { TranslationService } from '../../../service/translation.service';
import { LookupService } from '../../../service/lookup.service';
import { ReportPdfQuery } from '../../../service/report-pdf.service';
import { uiReportLang } from '../../../service/report-lang.util';

/**
 * One payment authorisation as the ministry's own document — استمارة اعتماد الصرف,
 * نموذج رقم ١١ حسابات — rendered by Jasper on the server and shown in the embedded
 * viewer.
 *
 * <p>This page used to draw an HTML replica of the form and call
 * {@code window.print()} for all three of its View, Print and Download PDF buttons.
 * That replica could not show the masthead, the ministry logo or the footer sentence,
 * because those live in {@code REPORT_TYPE_SETTINGS} and only the server's rendering
 * reads them — so what a user printed was never quite the statutory form. It now shows
 * the PDF the server produces, which is the same document the Reports menu has always
 * served from this record; the four actions come from the shared bar every other report
 * page uses.
 */
@Component({
    selector: 'app-payment-auth-report',
    templateUrl: './payment-auth-report.component.html',
    styleUrls: ['./payment-auth-report.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class PaymentAuthReportComponent implements OnInit {

  record: PaymentAuth | null = null;
  loading = false;
  recordId: number | null = null;

  /** The language the document is rendered in; the masthead is stored in both. */
  selectedLang = uiReportLang(this.translationService);

  pdfApiPath: string | null = null;
  pdfQuery: ReportPdfQuery | null = null;
  loadTrigger = 0;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private service: PaymentAuthService,
    private messageService: MessageService,
    public translationService: TranslationService,
    private lookupService: LookupService
  ) {}

  ngOnInit(): void {
    this.selectedLang = this.translationService.getCurrentLanguage() === 'en' ? 'en' : 'ar';
    this.route.params.subscribe(params => {
      const id = Number(params['id']);
      if (Number.isInteger(id) && id > 0) {
        this.recordId = id;
        this.loadRecord(id);
      } else {
        // Live QA L3 (#65): /payment-authorization/report/abc stayed on an empty page.
        this.router.navigate(['/notfound'], { replaceUrl: true });
      }
    });
  }

  /**
   * The record itself, for the heading and the status tag. The viewer is pointed at
   * the document once the record is known to exist (live QA L3, #65): it used to be
   * pointed at once, so a bad id gave an empty voucher under two "Record not found"
   * toasts, one from each read. A record that does not exist is the not-found page.
   */
  loadRecord(id: number): void {
    this.loading = true;
    this.service.getById(id, silentErrors()).subscribe({
      next: (rec) => { this.record = rec; this.loading = false; this.showDocument(); },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        if (err?.status === 404) {
          this.router.navigate(['/notfound'], { replaceUrl: true });
          return;
        }
        this.messageService.add({
          severity: 'error',
          summary: this.translationService.translate('COMMON.ERROR'),
          detail: this.translationService.translate('PAYMENT_AUTH.RECORD_NOT_FOUND')
        });
      }
    });
  }

  /** The Jasper voucher for this record. Also the path the action bar acts on. */
  get voucherApiPath(): string {
    return `/api/v1/paymentAuth/${this.recordId}/export-report`;
  }

  get filename(): string {
    return `payment-auth-${this.record?.authFormNumber || this.recordId}.pdf`;
  }

  /** Loads (or reloads) the document into the viewer. */
  showDocument(): void {
    if (!this.recordId) { return; }
    this.pdfApiPath = this.voucherApiPath;
    this.pdfQuery = { lang: this.selectedLang };
    this.loadTrigger++;
  }

  /** The masthead is stored in both languages; re-render rather than reload the page. */
  onLangChange(): void {
    this.showDocument();
  }

  get reportLangOptions(): Array<{ label: string; value: string }> {
    return this.lookupService.getReportLangOptions();
  }

  goBack(): void {
    this.router.navigate(['/payment-authorization']);
  }
}
