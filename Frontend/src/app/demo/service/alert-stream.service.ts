import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { environment } from 'src/environments/environment';
import { UmsAuthService } from './ums-auth.service';
import { MessageService } from 'primeng/api';
import { CodeListService } from './code-list.service';
import { TranslationService } from './translation.service';

export interface AlertPush {
  alertId: number;
  title: string;
  priority: string;
  type: string;
}

/**
 * Connects to the backend SSE stream at /api/v1/alerts/stream.
 * Emits each new alert via alert$ observable and shows a toast.
 *
 * Lifecycle:
 *   - Call connect() after login to start listening.
 *   - Call disconnect() on logout or destroy.
 *   - Auto-reconnects with exponential back-off on connection loss.
 */
@Injectable({ providedIn: 'root' })
export class AlertStreamService implements OnDestroy {

  readonly alert$ = new Subject<AlertPush>();

  private es: EventSource | null = null;
  private reconnectDelay = 5000;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private active = false;

  constructor(
    private zone: NgZone,
    private http: HttpClient,
    private umsAuthService: UmsAuthService,
    private messageService: MessageService,
    private codeLists: CodeListService,
    private translationService: TranslationService
  ) {}

  connect(): void {
    if (this.active || this.es) return;
    this.active = true;
    this.openStream();
  }

  disconnect(): void {
    this.active = false;
    this.closeStream();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private openStream(): void {
    // EventSource doesn't support custom headers, so we cannot send the JWT as a
    // Bearer header. Putting the JWT in the URL leaks it into server access
    // logs, so instead we POST (authenticated by the auth interceptor) for a
    // short-lived single-use ticket and open the stream with ?ticket=. Each
    // reconnect mints a fresh ticket.
    this.http.post<{ ticket: string }>(`${environment.apiUrl}/mohe/api/v1/alerts/stream/ticket`, {})
      .subscribe({
        next: ({ ticket }) => {
          if (!this.active) { return; }
          this.openEventSource(`${environment.apiUrl}/mohe/api/v1/alerts/stream?ticket=${encodeURIComponent(ticket)}`);
        },
        error: () => {
          if (this.active) {
            this.scheduleReconnect();
          }
        }
      });
  }

  private openEventSource(url: string): void {
    this.es = new EventSource(url);

    this.es.addEventListener('alert', (ev: MessageEvent) => {
      this.zone.run(() => {
        try {
          const data: AlertPush = JSON.parse(ev.data);
          this.alert$.next(data);
          this.showToast(data);
        } catch { /* ignore malformed events */ }
      });
    });

    this.es.onerror = () => {
      this.closeStream();
      if (this.active) {
        this.scheduleReconnect();
      }
    };

    this.reconnectDelay = 5000; // reset delay on successful open
  }

  private closeStream(): void {
    if (this.es) {
      this.es.close();
      this.es = null;
    }
  }

  private scheduleReconnect(): void {
    this.reconnectTimer = setTimeout(() => {
      if (this.active) {
        this.openStream();
      }
    }, this.reconnectDelay);
    this.reconnectDelay = Math.min(this.reconnectDelay * 2, 60000); // max 60s
  }

  /**
   * The toast colour is the severity row of the alert's priority
   * (LU_ALERT_SEVERITY.UI_SEVERITY): PrimeNG `danger` is a toast `error`.
   */
  private showToast(alert: AlertPush): void {
    const t = (key: string) => this.translationService.translate(key);
    const priorityName = this.codeLists.label('alert-severity', alert.priority);
    this.messageService.add({
      severity: CodeListService.toToastSeverity(this.codeLists.severity('alert-severity', alert.priority)),
      summary: alert.title || t('ALERT_EVENTS.STREAM.NEW_ALERT'),
      detail: priorityName ? `${t('ALERT_EVENTS.STREAM.PRIORITY')}: ${priorityName}` : undefined,
      life: 8000,
      key: 'alert-stream'
    });
  }
}
