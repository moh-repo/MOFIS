import { Component, OnInit } from '@angular/core';
import { StudentPortalApiService, PortalFinance } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { financeStatusLabel } from '../student-portal-i18n.helper';

@Component({
    selector: 'app-student-portal-finance-status',
    templateUrl: './finance-status.component.html',
    styleUrls: ['./finance-status.component.scss'],
    standalone: false
})
export class FinanceStatusComponent implements OnInit {
  finance: PortalFinance | null = null;
  loading = true;
  errorKey = '';

  constructor(
    private api: StudentPortalApiService,
    private translationService: TranslationService
  ) {}

  /**
   * Decided by the server from the status code (RECEIVED or DISBURSED). This used
   * to be "any id but 0", so "Not received" and every other status read as
   * received (live QA #10).
   */
  get statusReceived(): boolean {
    return this.finance?.received === true;
  }

  /** The status's own name in the portal's language; the generic wording when there is none. Shared with My Data. */
  get statusLabel(): string {
    return financeStatusLabel(this.finance, this.translationService.getCurrentLanguage(),
      key => this.translationService.translate(key));
  }

  ngOnInit(): void {
    this.api.getMyData().subscribe({
      next: d => {
        this.finance = d.finance;
        this.loading = false;
      },
      error: () => {
        this.errorKey = 'STUDENT_PORTAL.LOAD_FINANCE_ERROR';
        this.loading = false;
      }
    });
  }
}
