import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslationService } from '../../../service/translation.service';
import {
    NotificationService, NotificationLogDto, NotificationHistoryFilters, NotificationRecipientDto
} from '../../../service/notification.service';
import { ReportCountryScopeService } from '../../../service/report-country-scope.service';
import { CodeListService } from '../../../service/code-list.service';
import { LookupItem, LookupService } from '../../../service/lookup.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { pageOf, resetPaging } from '../../../service/table-paging.util';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
    selector: 'app-notification-history',
    templateUrl: './notification-history.component.html',
    standalone: false
})
export class NotificationHistoryComponent implements OnInit, OnDestroy {
    /** For the code-list pipes: the language the names are read in. */
    get currentLanguage(): string {
        return this.translationService.getCurrentLanguage();
    }


    private destroy$ = new Subject<void>();
    logs: NotificationLogDto[] = [];
    totalRecords = 0;
    loading = false;
    page = 0;
    pageSize = 20;
    /** Bound to the table, so going back to page one moves the paginator there too (QA B8, N9). */
    first = 0;
    expandedRows: { [key: number]: boolean } = {};

    // ── view: one message, and who it went to ───────────────────────────────
    viewVisible = false;
    viewing: NotificationLogDto | null = null;
    recipients: NotificationRecipientDto[] = [];
    recipientsLoading = false;
    recipientsFailed = false;

    // ── filters ─────────────────────────────────────────────────────────────
    // The four codes the row already shows, plus the range the list is ordered
    // by. Narrowing happens on the server: the table is lazy and holds one page,
    // so a filter applied in the browser would only ever search that page.
    /**
     * One attaché, or null for every attaché in the user's scope. Starts at the
     * topbar country and follows it, but the page can narrow on its own.
     */
    filterCountryId: number | null = null;
    countryOptions: LookupItem[] = [];
    filterChannel: string | null = null;
    filterRecipientType: string | null = null;
    filterScope: string | null = null;
    filterStatus: string | null = null;
    filterSentFrom: Date | null = null;
    filterSentTo: Date | null = null;

    constructor(
        private route: ActivatedRoute,
        private service: NotificationService,
        private reportCountryScope: ReportCountryScopeService,
        private codeLists: CodeListService,
        private lookupService: LookupService,
        private umsAuthService: UmsAuthService,
        public translationService: TranslationService
    ) {}

    ngOnInit(): void {
        this.applyRouteFilters();
        this.lookupService.getAllStudyCountries()
            .pipe(takeUntil(this.destroy$))
            .subscribe(list => {
                this.countryOptions = this.umsAuthService.filterToAllowedStudyCountries(
                    (list as any[]).map(c => ({ id: c.studyCountryInId ?? c.id, name: c.studyCountryInName ?? c.name }))
                );
            });
        // Emits the current topbar country on subscribe, which is the first load.
        this.reportCountryScope.selectedCountryId$
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => {
                this.filterCountryId = this.reportCountryScope.selectedCountryId ?? null;
                resetPaging(this);
                this.loadHistory();
            });
    }

    /**
     * A date range handed over in the query string, so a link can open this page
     * on the day it was talking about — the topbar calendar's agenda does.
     *
     * <p>Read once rather than subscribed: the range is an opening position, not
     * a binding. Once the page is open the filter bar owns it, and re-applying
     * the parameter on every emission would undo whatever the user changed.
     */
    private applyRouteFilters(): void {
        const params = this.route.snapshot.queryParamMap;
        const from = NotificationHistoryComponent.parseDate(params.get('sentFrom'));
        const to = NotificationHistoryComponent.parseDate(params.get('sentTo'));
        if (from) { this.filterSentFrom = from; }
        if (to) { this.filterSentTo = to; }
    }

    /** `yyyy-MM-dd` as a local date; anything else is ignored rather than guessed at. */
    private static parseDate(value: string | null): Date | null {
        if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
            return null;
        }
        const [year, month, day] = value.split('-').map(Number);
        const date = new Date(year, month - 1, day);
        return Number.isNaN(date.getTime()) ? null : date;
    }

    /**
     * Dropdown options, read straight from the code lists the page already
     * holds — the same rows the cells are named from, so a filter can never
     * offer a value the table cannot show.
     */
    options(list: string): Array<{ label: string; value: string }> {
        return this.codeLists.options(list, this.currentLanguage);
    }

    get hasFilters(): boolean {
        return !!(this.filterCountryId != null || this.filterChannel || this.filterRecipientType || this.filterScope
            || this.filterStatus || this.filterSentFrom || this.filterSentTo);
    }

    /** Back to the first page: page 4 of the old list is rarely a page of the new one. */
    applyFilters(): void {
        resetPaging(this);
        this.loadHistory();
    }

    clearFilters(): void {
        this.filterCountryId = null;
        this.filterChannel = null;
        this.filterRecipientType = null;
        this.filterScope = null;
        this.filterStatus = null;
        this.filterSentFrom = null;
        this.filterSentTo = null;
        this.applyFilters();
    }

    private currentFilters(): NotificationHistoryFilters {
        return {
            channel: this.filterChannel,
            recipientType: this.filterRecipientType,
            scope: this.filterScope,
            status: this.filterStatus,
            sentFrom: this.filterSentFrom ? this.filterSentFrom.toISOString() : null,
            // The picker hands back midnight. Sent to the server as it is, a
            // range ending today would exclude everything sent today, which is
            // the opposite of what choosing today means.
            sentTo: this.filterSentTo ? NotificationHistoryComponent.endOfDay(this.filterSentTo) : null
        };
    }

    private static endOfDay(date: Date): string {
        const end = new Date(date);
        end.setHours(23, 59, 59, 999);
        return end.toISOString();
    }

    loadHistory(): void {
        this.loading = true;
        this.service.getHistory(this.page, this.pageSize, this.filterCountryId, this.currentFilters())
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: resp => {
                    this.logs = resp.content;
                    this.totalRecords = resp.totalElements;
                    this.loading = false;
                },
                error: () => { this.loading = false; }
            });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    onPage(event: any): void {
        const at = pageOf(event, this.pageSize);
        this.page = at.page;
        this.pageSize = at.rows;
        this.first = at.first;
        this.loadHistory();
    }

    // ── view ────────────────────────────────────────────────────────────────

    /** Opens the message and fetches who it went to (V140); older batches have no list. */
    openView(log: NotificationLogDto): void {
        this.viewing = log;
        this.recipients = [];
        this.recipientsFailed = false;
        this.recipientsLoading = true;
        this.viewVisible = true;
        this.service.getRecipients(log.id)
            .pipe(takeUntil(this.destroy$))
            .subscribe({
                next: rows => { this.recipients = rows ?? []; this.recipientsLoading = false; },
                error: () => { this.recipientsLoading = false; this.recipientsFailed = true; }
            });
    }

    get recipientsSent(): number {
        return this.recipients.filter(r => r.status === 'SENT').length;
    }

    get recipientsFailedCount(): number {
        return this.recipients.filter(r => r.status !== 'SENT').length;
    }

    recipientsCountText(): string {
        return this.t('NOTIFICATIONS.HISTORY.RECIPIENTS_COUNT').replace('{count}', String(this.recipients.length));
    }

    /** The send-status code list's severity as the palette name the .dv-* modifiers use. */
    private tone(status: string | null | undefined): 'green' | 'orange' | 'red' | 'blue' | 'gray' {
        const ui: string = status ? this.codeLists.severity('notification-send-status', status) : '';
        switch (ui) {
            case 'success': return 'green';
            case 'warn':
            case 'warning': return 'orange';
            case 'danger': return 'red';
            case 'info': return 'blue';
            default: return 'gray';
        }
    }

    heroClassFor(log: NotificationLogDto): string {
        switch (this.tone(log.status)) {
            case 'green': return 'dv-hero--success';
            case 'orange': return 'dv-hero--warn';
            case 'red': return 'dv-hero--danger';
            case 'blue': return 'dv-hero--info';
            default: return '';
        }
    }

    formatCountries(log: NotificationLogDto): string {
        if (log.countryNames?.length) {
            return log.countryNames.join(', ');
        }
        return this.t('NOTIFICATIONS.HISTORY.ALL_COUNTRIES');
    }

    t(key: string): string {
        return this.translationService.translate(key) || key;
    }

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }
}
