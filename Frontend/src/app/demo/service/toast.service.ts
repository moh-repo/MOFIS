import { Injectable, Optional } from '@angular/core';
import { MessageService } from 'primeng/api';
import { TranslationService } from './translation.service';

/**
 * One toast API for the whole app, on top of PrimeNG's root MessageService and
 * the `<p-toast>` in app.component.html.
 *
 * Fourteen screens (UMS administration, backup/restore, data import, Wafar
 * reconciliation, the portal's change requests) used ng-zorro's NzMessageService
 * instead, so the same kind of event surfaced in two places with two looks, and
 * an Nz message could not be translated through our dictionaries. The method
 * names mirror the Nz ones so those screens read the same after the switch:
 * `success` / `error` / `warning` / `info` take the detail text and use the
 * COMMON.* word for the title.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {

  /**
   * MessageService is provided by AppModule (it is not providedIn root). It is
   * optional here only so a component spec that mocks the network but not the
   * toast host can still create the component; in the app it is always present.
   */
  constructor(@Optional() private messageService: MessageService | null,
              private translationService: TranslationService) {}

  success(detail: string, life = 4000): void {
    this.add('success', 'COMMON.SUCCESS', detail, life);
  }

  error(detail: string, life = 8000): void {
    this.add('error', 'COMMON.ERROR', detail, life);
  }

  warning(detail: string, life = 6000): void {
    this.add('warn', 'COMMON.WARNING', detail, life);
  }

  info(detail: string, life = 5000): void {
    this.add('info', 'COMMON.INFO', detail, life);
  }

  private add(severity: 'success' | 'error' | 'warn' | 'info', summaryKey: string, detail: string, life: number): void {
    if (!this.messageService) {
      console.warn(`[toast:${severity}] ${detail}`);
      return;
    }
    this.messageService.add({
      severity,
      summary: this.translationService.translate(summaryKey),
      detail,
      life
    });
  }
}
