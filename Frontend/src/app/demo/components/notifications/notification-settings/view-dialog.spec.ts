import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { NotificationsModule } from '../notifications.module';
import { NotificationSettingsComponent } from './notification-settings.component';
import { NotificationService, NotificationSettingsDto } from '../../../service/notification.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { LookupService } from '../../../service/lookup.service';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { CodeListService } from '../../../service/code-list.service';
import { codeListStub } from '../../../../testing/code-list.stub';

/**
 * The notification settings view dialog (25 Sep 2026): one scope's settings,
 * read-only, in the .dv-* vocabulary every other record's View dialog uses.
 * Every row gets a View button; Edit stays where the server allows it.
 *
 * <p>Dialogs land on <body> (providePrimeNG's overlayAppendTo, as in
 * app.module.ts), so they are read from the document, not the fixture.
 */
describe('notification settings — the view dialog', () => {

    let fixture: ComponentFixture<NotificationSettingsComponent>;
    let component: NotificationSettingsComponent;

    const row = (countryId: number | null, extra: Partial<NotificationSettingsDto> = {}): NotificationSettingsDto => ({
        countryId, countryName: countryId == null ? null : `Country ${countryId}`,
        emailEnabled: true, smtpHost: 'smtp.example.com', smtpPort: 587, smtpUsername: 'mailer', smtpPassword: null,
        smtpFromAddress: 'no-reply@example.com', smtpFromName: 'MOHE', smtpUseTls: true, smtpUseSsl: false,
        smsEnabled: true, smsWebhookUrl: 'https://sms.example.com/v1/send', smsWebhookMethod: 'POST',
        smsWebhookHeaders: null, smsFromNumber: '+967700000000',
        whatsappEnabled: false, whatsappWebhookUrl: null, whatsappWebhookHeaders: null,
        whatsappFromNumber: null, whatsappBodyTemplate: null,
        whatsappMessageMode: 'TEXT', whatsappApprovedTemplate: null,
        smtpPasswordSet: true, smsWebhookHeadersSet: false, whatsappWebhookHeadersSet: false,
        canEdit: true, updatedAt: '2026-09-19T10:00:00Z',
        ...extra
    });

    const rows = [row(null), row(1004, { canEdit: false })];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [NotificationsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                providePrimeNG({ overlayAppendTo: 'body' }),
                { provide: CodeListService, useValue: codeListStub({}, 'en') },
                {
                    provide: NotificationService,
                    useValue: {
                        listSettings: () => of(rows),
                        testSettings: () => of({ ok: true, enabled: true, error: null })
                    }
                },
                {
                    provide: LookupService,
                    useValue: new Proxy({
                        getAllStudyCountries: () => of([{ studyCountryInId: 1004, studyCountryInName: 'Jordan' }])
                    } as any, { get: (t: any, p: any) => p in t ? t[p] : () => of([]) })
                },
                { provide: EnumOptionsService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
                {
                    provide: UmsAuthService,
                    useValue: new Proxy({ permissions$: of([]), filterToAllowedStudyCountries: (items: any[]) => items } as any, {
                        get: (t: any, p: any) => p in t ? t[p] : () => true
                    })
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

        fixture = TestBed.createComponent(NotificationSettingsComponent);
        component = fixture.componentInstance;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    afterEach(() => {
        component.viewVisible = false;
        component.dialogVisible = false;
        component.showTestDialog = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const settle = async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    const dialog = () => document.querySelector('.dv-kpis')?.closest('.p-dialog.dv-dialog') as HTMLElement;
    const footerButtons = () =>
        Array.from(dialog().querySelectorAll('.dv-footer__actions button')).map(b => b.textContent?.trim() ?? '');

    it('every row offers View; Edit only where the server allows it', () => {
        const cells = Array.from(fixture.nativeElement.querySelectorAll('tbody tr td:last-child')) as HTMLElement[];
        expect(cells.length).toBe(2);
        const icons = (cell: HTMLElement) =>
            Array.from(cell.querySelectorAll('button')).map(b => b.querySelector('[class*="pi-"]')?.className ?? '');
        expect(icons(cells[0])[0]).toContain('pi-eye');
        expect(icons(cells[0]).some(c => c.includes('pi-pencil'))).withContext('editable row').toBeTrue();
        expect(icons(cells[1])[0]).toContain('pi-eye');
        expect(icons(cells[1]).some(c => c.includes('pi-pencil'))).withContext('read-only row').toBeFalse();
    });

    it('the global row: scope, channel tiles, the settings by section, and no secret values', async () => {
        component.openView(rows[0]);
        await settle();

        expect(dialog()).withContext('view dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog())).toBeFalse();
        expect(dialog().querySelector('.dv-hero')?.className).toContain('dv-hero--info');
        expect(dialog().querySelector('.dv-hero__title')?.textContent).toContain('NOTIFICATIONS.SETTINGS.GLOBAL_DEFAULT');
        expect(dialog().querySelector('.dv-hero__subtitle')?.textContent).toContain('NOTIFICATIONS.SETTINGS.CHANNELS_ON');
        expect(dialog().querySelectorAll('.dv-kpi').length).toBe(3);
        expect(dialog().querySelectorAll('.dv-kpi--green').length).withContext('email and SMS on').toBe(2);
        expect(dialog().querySelectorAll('.dv-columns > .dv-section').length).toBe(2);
        expect(dialog().textContent).toContain('smtp.example.com');
        expect(dialog().textContent).toContain('https://sms.example.com/v1/send');
        expect(dialog().textContent).toContain('NOTIFICATIONS.SETTINGS.SECURITY_TLS');
        expect(dialog().textContent).toContain('NOTIFICATIONS.SETTINGS.SECRET_SET');
        expect(dialog().textContent).toContain('NOTIFICATIONS.SETTINGS.SECRET_NOT_SET');
        expect(dialog().querySelector('.dv-notes--empty')?.textContent).withContext('default WhatsApp template').toContain('DEFAULT_TEMPLATE_NOTE');
        expect(footerButtons()).toEqual(['COMMON.CLOSE', 'NOTIFICATIONS.SETTINGS.TEST_SEND', 'COMMON.EDIT']);
    });

    it('a row the user may not change: the read-only notice, and no Edit', async () => {
        component.openView(rows[1]);
        await settle();
        expect(dialog().querySelector('.dv-hero')?.className).toContain('dv-hero--success');
        expect(dialog().querySelector('.dv-notice--muted')?.textContent).toContain('NOTIFICATIONS.SETTINGS.READ_ONLY');
        expect(footerButtons()).toEqual(['COMMON.CLOSE', 'NOTIFICATIONS.SETTINGS.TEST_SEND']);
    });

    it('nothing switched on reads grey with a warning', async () => {
        component.openView(row(1004, { emailEnabled: false, smsEnabled: false, whatsappEnabled: false }));
        await settle();
        expect(dialog().querySelector('.dv-hero')?.className).toContain('dv-hero--muted');
        expect(dialog().querySelector('.dv-notice--warn')?.textContent).toContain('NOTIFICATIONS.SETTINGS.NO_CHANNELS');
        expect(dialog().querySelectorAll('.dv-kpi--gray').length).toBe(3);
    });

    it('hands over to editing and to a test send on the same row', async () => {
        component.openView(rows[0]);
        await settle();
        component.editFromView();
        expect(component.viewVisible).toBeFalse();
        expect(component.dialogVisible).toBeTrue();
        expect(component.editMode).toBeTrue();
        expect(component.readOnly).toBeFalse();

        component.dialogVisible = false;
        component.openView(rows[1]);
        component.editFromView();
        expect(component.dialogVisible).withContext('no edit for a read-only row').toBeFalse();
        component.testFromView();
        expect(component.viewVisible).toBeFalse();
        expect(component.showTestDialog).toBeTrue();
        expect(component.testRow).toBe(rows[1]);
    });
});
