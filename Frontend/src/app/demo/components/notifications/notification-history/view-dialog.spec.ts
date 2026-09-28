import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { NotificationsModule } from '../notifications.module';
import { NotificationHistoryComponent } from './notification-history.component';
import { NotificationService, NotificationRecipientDto } from '../../../service/notification.service';
import { ReportCountryScopeService } from '../../../service/report-country-scope.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { UmsGroupService } from '../../../service/ums-group.service';
import { LookupService } from '../../../service/lookup.service';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { CodeListService } from '../../../service/code-list.service';
import { codeListStub, codeRow } from '../../../../testing/code-list.stub';

/**
 * The notification history's view dialog (25 Sep 2026): one message in the
 * .dv-* vocabulary every other record's View dialog uses, with who it went to
 * — one row per recipient from the recipient log (V140) — and, for a batch
 * sent before that log existed, a plain statement that no list was recorded.
 *
 * <p>Dialogs land on <body> (providePrimeNG's overlayAppendTo, as in
 * app.module.ts), so they are read from the document, not the fixture.
 */
describe('notification history — the view dialog', () => {

    let fixture: ComponentFixture<NotificationHistoryComponent>;
    let component: NotificationHistoryComponent;
    let recipients: () => any;

    const row = {
        id: 7, batchId: 'batch-1', channel: 'EMAIL', recipientType: 'EMPLOYEE', scope: 'GROUP',
        subject: 'Quarter closing', body: 'THE FULL MESSAGE BODY', totalCount: 2, sentCount: 1,
        failedCount: 1, status: 'PARTIAL', errorSummary: 'b@x: mailbox full', createdBy: 1,
        createdAt: '2026-09-20T10:00:00Z', sentAt: '2026-09-20T10:01:00Z',
        countryIds: [1004], countryNames: ['Jordan']
    };

    const sentTo: NotificationRecipientDto[] = [
        { id: 1, recipientType: 'EMPLOYEE', recipientId: 41, name: 'Amal Officer', address: 'a@x', status: 'SENT', error: null, sentAt: '2026-09-20T10:01:00Z' },
        { id: 2, recipientType: 'EMPLOYEE', recipientId: 42, name: 'Basim Officer', address: 'b@x', status: 'FAILED', error: 'mailbox full', sentAt: '2026-09-20T10:01:00Z' }
    ];

    const lists = {
        'notification-channel': [codeRow('EMAIL', { icon: 'pi-envelope' })],
        'notification-recipient-type': [codeRow('EMPLOYEE')],
        'notification-scope': [codeRow('GROUP')],
        'notification-send-status': [
            codeRow('SENT', { uiSeverity: 'success' }), codeRow('FAILED', { uiSeverity: 'danger' }),
            codeRow('PARTIAL', { uiSeverity: 'warn' })
        ]
    };

    beforeEach(async () => {
        recipients = () => of(sentTo);
        await TestBed.configureTestingModule({
            imports: [NotificationsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                providePrimeNG({ overlayAppendTo: 'body' }),
                { provide: CodeListService, useValue: codeListStub(lists, 'en') },
                {
                    provide: NotificationService,
                    useValue: {
                        getHistory: () => of({ content: [row], totalElements: 1, totalPages: 1, number: 0, size: 20 }),
                        getRecipients: () => recipients()
                    }
                },
                { provide: ReportCountryScopeService, useValue: { selectedCountryId$: of(null), selectedCountryId: null } },
                {
                    provide: LookupService,
                    useValue: new Proxy({ getAllStudyCountries: () => of([{ studyCountryInId: 1004, studyCountryInName: 'Jordan' }]) } as any,
                        { get: (t: any, p: any) => p in t ? t[p] : () => of([]) })
                },
                { provide: EnumOptionsService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
                { provide: UmsGroupService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
                {
                    provide: UmsAuthService,
                    useValue: new Proxy({ permissions$: of([]), filterToAllowedStudyCountries: (items: any[]) => items } as any,
                        { get: (t: any, p: any) => p in t ? t[p] : () => true })
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(NotificationHistoryComponent);
        component = fixture.componentInstance;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    afterEach(() => {
        component.viewVisible = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const settle = async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    const dialog = () => document.querySelector('.dv-kpis')?.closest('.p-dialog.dv-dialog') as HTMLElement;

    it('the row keeps the toggler first and gains a View button last', () => {
        const buttons = Array.from(fixture.nativeElement.querySelectorAll('tbody tr:first-child button')) as HTMLElement[];
        const icon = (b: HTMLElement) => b.querySelector('[class*="pi-"]')?.className ?? '';
        expect(icon(buttons[0])).toContain('pi-chevron');
        expect(icon(buttons[buttons.length - 1])).toContain('pi-eye');
        expect(fixture.nativeElement.querySelectorAll('thead th').length).toBe(12);
    });

    it('shows the message, its audience, and who it went to', async () => {
        component.openView(row as any);
        await settle();

        expect(dialog()).withContext('view dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog())).toBeFalse();
        expect(dialog().querySelector('.dv-hero')?.className).withContext('PARTIAL is a warning').toContain('dv-hero--warn');
        expect(dialog().querySelector('.dv-hero__eyebrow')?.textContent).toContain('EMAIL-en');
        expect(dialog().querySelector('.dv-hero__title')?.textContent).toContain('Quarter closing');
        expect(dialog().querySelector('.dv-hero__subtitle')?.textContent).toContain('EMPLOYEE-en');
        expect(dialog().querySelector('.dv-hero__subtitle')?.textContent).toContain('GROUP-en');
        expect(dialog().querySelector('.dv-hero__subtitle')?.textContent).toContain('Jordan');

        const kpis = Array.from(dialog().querySelectorAll('.dv-kpi__value')).map(k => k.textContent?.trim());
        expect(kpis).toEqual(['2', '1', '1']);
        expect(dialog().querySelector('.dv-notes')?.textContent).toContain('THE FULL MESSAGE BODY');

        const cells = Array.from(dialog().querySelectorAll('.dv-section table tbody td')).map(c => c.textContent?.trim());
        expect(cells).toContain('Amal Officer');
        expect(cells).toContain('a@x');
        expect(cells).toContain('Basim Officer');
        expect(cells).toContain('mailbox full');
        expect(dialog().querySelectorAll('.dv-section table tbody tr').length).toBe(2);
        expect(dialog().querySelector('.dv-section__aside')?.textContent).toContain('NOTIFICATIONS.HISTORY.RECIPIENTS_COUNT');
        expect(dialog().textContent).withContext('the batch errors').toContain('b@x: mailbox full');
    });

    it('says so when no recipient list was recorded', async () => {
        recipients = () => of([]);
        component.openView(row as any);
        await settle();
        expect(dialog().querySelector('.dv-notice--muted')?.textContent).toContain('NOTIFICATIONS.HISTORY.RECIPIENTS_NOT_RECORDED');
        expect(dialog().querySelector('.dv-section table')).toBeNull();
    });

    it('reports a failed recipients request instead of an empty list', async () => {
        recipients = () => throwError(() => new Error('boom'));
        component.openView(row as any);
        await settle();
        expect(dialog().querySelector('.dv-notice--danger')?.textContent).toContain('NOTIFICATIONS.HISTORY.RECIPIENTS_LOAD_FAILED');
    });
});
