import { Component, OnInit, OnDestroy } from '@angular/core';
import { MessageService, ConfirmationService } from 'primeng/api';
import { TranslationService } from '../../../service/translation.service';
import { LookupService, LookupItem } from '../../../service/lookup.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { ReportCountryScopeService } from '../../../service/report-country-scope.service';
import {
    ChannelAvailability, DELIVERY_CHANNELS, DeliveryChannel, NotificationService, RecipientOption, SendMessageRequest
} from '../../../service/notification.service';
import { forkJoin, of, Subject } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { ensureGlobalStylesheet } from '../../../service/lazy-style.util';

// The <p-editor> body field is Quill; its snow theme is the only place in the
// app that needs quill.snow.css, so it is fetched here rather than shipped with
// the app shell (angular.json emits it with inject: false).
ensureGlobalStylesheet('quill-snow');

@Component({
    selector: 'app-send-notification',
    templateUrl: './send-notification.component.html',
    standalone: false
})
export class SendNotificationComponent implements OnInit, OnDestroy {

    readonly Math = Math;
    private destroy$ = new Subject<void>();

    /** Any set of channels; each goes out as its own history row under one batch. */
    channels: DeliveryChannel[] = ['EMAIL'];
    /**
     * Which channels the settings that apply to this send have switched on, and
     * whose settings those are. Null until known; the tiles are then all enabled
     * and the server has the last word.
     */
    availability: ChannelAvailability | null = null;
    recipientType: 'STUDENT' | 'EMPLOYEE' = 'STUDENT';
    scope: 'ONE' | 'GROUP' | 'ALL' = 'ALL';
    /**
     * scope=ONE (review R4): the option picked from the search, or what was typed.
     * A typed email is sent as recipientEmail and a typed number as the id.
     */
    recipient: RecipientOption | string | null = null;
    recipientSuggestions: RecipientOption[] = [];
    subject: string = '';
    body: string = '';

    selectedCountryIds: number[] = [];
    selectedMajorIds: number[] = [];
    selectedStudyStatusIds: number[] = [];
    selectedGroupIds: number[] = [];

    countries: LookupItem[] = [];
    majors: LookupItem[] = [];
    studyStatuses: LookupItem[] = [];
    umsGroups: any[] = [];

    loadingLookups = false;
    sending = false;
    previewCount: number | null = null;
    previewLoading = false;
    showConfirm = false;

    channelOptions: { label: string; value: DeliveryChannel }[] = [];
    recipientOptions: { label: string; value: string }[] = [];
    scopeOptions: { label: string; value: string }[] = [];

    constructor(
        private notificationService: NotificationService,
        private lookupService: LookupService,
        private umsAuthService: UmsAuthService,
        private reportCountryScope: ReportCountryScopeService,
        private messageService: MessageService,
        private confirmationService: ConfirmationService,
        public translationService: TranslationService
    ) {}

    ngOnInit(): void {
        this.loadLookups();
        this.translationService.currentLanguage$.pipe(takeUntil(this.destroy$)).subscribe(() => this.loadTypeOptions());
        this.reportCountryScope.selectedCountryId$
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => {
                this.applyDefaultCountries();
                this.refreshAvailability();
            });
    }

    ngOnDestroy(): void {
        this.destroy$.next();
        this.destroy$.complete();
    }

    get showStudentCountryFilter(): boolean {
        return this.recipientType === 'STUDENT' && (this.scope === 'GROUP' || this.scope === 'ALL');
    }

    private loadLookups(): void {
        this.loadingLookups = true;
        this.loadTypeOptions();
        forkJoin({
            countries: this.lookupService.getAllStudyCountries(),
            majors: this.lookupService.getStudentMajors(),
            statuses: this.lookupService.getStudyStatuses(),
            // The send page's own list (live QA M6); a failure leaves the group choice
            // empty instead of failing the countries, majors and statuses with it.
            groups: this.notificationService.getRecipientGroups().pipe(catchError(() => of([])))
        }).subscribe({
            next: data => {
                this.countries = this.umsAuthService.filterToAllowedStudyCountries(
                    (data.countries as any[]).map(c => ({ id: c.studyCountryInId ?? c.id, name: c.studyCountryInName ?? c.name }))
                );
                this.majors = data.majors;
                this.studyStatuses = data.statuses;
                const arabic = this.translationService.getCurrentLanguage() === 'ar';
                this.umsGroups = (data.groups ?? []).map(g => ({ id: g.id, name: (arabic && g.nameAr) || g.name }));
                this.applyDefaultCountries();
                this.refreshAvailability();
                this.loadingLookups = false;
            },
            error: () => { this.loadingLookups = false; }
        });
    }

    private applyDefaultCountries(): void {
        const selected = this.reportCountryScope.selectedCountryId;
        if (selected != null && this.countries.some(c => c.id === selected)) {
            this.selectedCountryIds = [selected];
        } else if (this.countries.length === 1) {
            this.selectedCountryIds = [this.countries[0].id];
        }
    }

    private loadTypeOptions(): void {
        // BOTH is the pre-WhatsApp code for e-mail + SMS; the tiles now combine freely.
        this.lookupService.getNotificationChannels().subscribe(c => {
            this.channelOptions = c.filter(o => (DELIVERY_CHANNELS as string[]).includes(o.value)) as
                { label: string; value: DeliveryChannel }[];
        });
        this.lookupService.getNotificationRecipientTypes().subscribe(r => { this.recipientOptions = r; });
        this.lookupService.getNotificationScopes().subscribe(s => { this.scopeOptions = s; });
    }

    get showSubject(): boolean {
        return this.channels.includes('EMAIL');
    }

    /** The e-mail editor is used when e-mail is among the channels; the phone channels get it as plain text. */
    get hasPhoneChannel(): boolean {
        return this.channels.includes('SMS') || this.channels.includes('WHATSAPP');
    }

    isChannelSelected(value: DeliveryChannel): boolean {
        return this.channels.includes(value);
    }

    isChannelAvailable(value: DeliveryChannel): boolean {
        return !this.availability || this.availability.enabledChannels.includes(value);
    }

    toggleChannel(value: DeliveryChannel): void {
        if (!this.isChannelAvailable(value)) return;
        this.channels = this.isChannelSelected(value)
            ? this.channels.filter(c => c !== value)
            : DELIVERY_CHANNELS.filter(c => c === value || this.channels.includes(c));
        this.previewCount = null;
    }

    channelLabel(value: DeliveryChannel): string {
        return this.channelOptions.find(o => o.value === value)?.label ?? value;
    }

    get channelsLabel(): string {
        return this.channels.map(c => this.channelLabel(c)).join(' + ');
    }

    get isMinistryUser(): boolean {
        return this.umsAuthService.isMinistryUser;
    }

    get currentLanguage(): string {
        return this.translationService.getCurrentLanguage();
    }

    /** Whose settings row a send with the current choices would go through. */
    get settingsSourceLabel(): string | null {
        if (!this.availability) return null;
        const id = this.availability.settingsCountryId;
        if (id == null) return this.t('NOTIFICATIONS.SETTINGS.GLOBAL_DEFAULT');
        return this.countries.find(c => c.id === id)?.name ?? String(id);
    }

    /**
     * Asks the server which channels the settings for the chosen countries have
     * switched on, and drops any selected channel that is off there.
     */
    refreshAvailability(): void {
        this.notificationService.channelAvailability(this.resolveCountryIdsForRequest()).subscribe({
            next: a => {
                // Anything but the expected shape is "unknown": the tiles stay open and the server decides.
                if (!a || !Array.isArray(a.enabledChannels)) {
                    this.availability = null;
                    return;
                }
                this.availability = a;
                this.channels = this.channels.filter(c => a.enabledChannels.includes(c));
                if (this.channels.length === 0 && a.enabledChannels.length > 0) {
                    this.channels = [a.enabledChannels[0]];
                }
            },
            error: () => { this.availability = null; }
        });
    }

    onCountriesChange(): void {
        this.previewCount = null;
        this.refreshAvailability();
    }

    buildRequest(): SendMessageRequest {
        const req: SendMessageRequest = {
            channels: [...this.channels],
            recipientType: this.recipientType,
            scope: this.scope,
            body: this.body
        };
        if (this.showSubject) req.subject = this.subject;
        if (this.scope === 'ONE') {
            const r = this.recipient;
            if (r && typeof r === 'object') {
                req.recipientId = r.id;
            } else if (typeof r === 'string' && r.includes('@')) {
                req.recipientEmail = r.trim();
            } else if (typeof r === 'string' && /^\d+$/.test(r.trim())) {
                req.recipientId = Number(r.trim());
            }
        }
        if (this.recipientType === 'STUDENT') {
            req.studentFilter = {
                countryIds: this.resolveCountryIdsForRequest(),
                majorIds: this.scope === 'GROUP' && this.selectedMajorIds.length ? this.selectedMajorIds : undefined,
                studyStatusIds: this.scope === 'GROUP' && this.selectedStudyStatusIds.length ? this.selectedStudyStatusIds : undefined
            };
        } else if (this.scope === 'GROUP') {
            req.employeeFilter = {
                groupIds: this.selectedGroupIds.length ? this.selectedGroupIds : undefined
            };
        }
        return req;
    }

    private resolveCountryIdsForRequest(): number[] | undefined {
        if (this.recipientType !== 'STUDENT') return undefined;
        if (this.selectedCountryIds.length) {
            return [...this.selectedCountryIds];
        }
        if (this.umsAuthService.isMinistryUser) {
            return undefined;
        }
        const allowed = this.umsAuthService.countryIds || [];
        return allowed.length ? [...allowed] : undefined;
    }

    /** Nothing that could name one person: no pick, no email, no id. */
    get recipientMissing(): boolean {
        if (this.scope !== 'ONE') return false;
        const r = this.recipient;
        if (r && typeof r === 'object') return false;
        const text = typeof r === 'string' ? r.trim() : '';
        return !(text.includes('@') || /^\d+$/.test(text));
    }

    /** The picked recipient has no address for one of the chosen channels. */
    get recipientChannelGap(): 'EMAIL' | 'PHONE' | null {
        const r = this.recipient;
        if (!r || typeof r !== 'object') return null;
        if (this.showSubject && !r.hasEmail) return 'EMAIL';
        if (this.hasPhoneChannel && !r.hasPhone) return 'PHONE';
        return null;
    }

    searchRecipients(event: { query: string }): void {
        const q = (event.query ?? '').trim();
        if (!q) { this.recipientSuggestions = []; return; }
        this.notificationService.searchRecipients(this.recipientType, q).subscribe({
            next: list => { this.recipientSuggestions = list; },
            error: () => { this.recipientSuggestions = []; }
        });
    }

    /** Switching between students and employees makes a picked person meaningless. */
    setRecipientType(value: 'STUDENT' | 'EMPLOYEE'): void {
        if (value !== this.recipientType) this.recipient = null;
        this.recipientType = value;
        this.previewCount = null;
        this.refreshAvailability();
    }

    loadPreviewCount(): void {
        if (this.recipientMissing) {
            this.showError('NOTIFICATIONS.SEND.RECIPIENT_REQUIRED');
            return;
        }
        this.previewLoading = true;
        this.previewCount = null;
        this.notificationService.previewCount(this.buildRequest()).subscribe({
            next: r => { this.previewCount = r.count; this.previewLoading = false; },
            error: () => { this.previewLoading = false; }
        });
    }

    confirmSend(): void {
        if (this.channels.length === 0) {
            this.showError('NOTIFICATIONS.SEND.CHANNEL_REQUIRED');
            return;
        }
        if (this.recipientMissing) {
            this.showError('NOTIFICATIONS.SEND.RECIPIENT_REQUIRED');
            return;
        }
        if (!this.body.trim()) {
            this.showError('NOTIFICATIONS.SEND.BODY_REQUIRED');
            return;
        }
        if (this.showSubject && !this.subject.trim()) {
            this.showError('NOTIFICATIONS.SEND.SUBJECT_REQUIRED');
            return;
        }
        this.showConfirm = true;
    }

    doSend(): void {
        this.showConfirm = false;
        this.sending = true;
        this.notificationService.send(this.buildRequest()).subscribe({
            next: resp => {
                this.sending = false;
                this.messageService.add({
                    severity: 'success',
                    summary: this.t('COMMON.SUCCESS'),
                    detail: resp.message
                });
                this.resetForm();
            },
            error: err => {
                this.sending = false;
                const msg = err?.error?.message || this.t('COMMON.SAVE_ERROR');
                this.showError(msg);
                // A channel switched off since the page loaded is the likely cause.
                this.refreshAvailability();
            }
        });
    }

    resetForm(): void {
        this.subject = '';
        this.body = '';
        this.recipient = null;
        this.recipientSuggestions = [];
        this.selectedMajorIds = [];
        this.selectedStudyStatusIds = [];
        this.selectedGroupIds = [];
        this.previewCount = null;
        this.scope = 'ALL';
        this.applyDefaultCountries();
        this.refreshAvailability();
    }

    private showError(key: string): void {
        this.messageService.add({ severity: 'error', summary: this.t('COMMON.ERROR'), detail: this.t(key) || key });
    }

    t(key: string): string {
        return this.translationService.translate(key) || key;
    }

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }
}
