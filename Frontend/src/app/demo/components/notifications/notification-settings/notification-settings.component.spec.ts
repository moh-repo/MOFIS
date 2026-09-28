import { TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

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
 * Notification settings as a list: the global row, and a row per attaché that
 * has its own. Adding offers only the attachés without one; editing, testing
 * and deleting follow the row's canEdit, which is the server's decision.
 */
describe('notification settings list', () => {

    let fixture: any;
    let service: any;

    const row = (countryId: number | null, extra: Partial<NotificationSettingsDto> = {}): NotificationSettingsDto => ({
        countryId, countryName: countryId == null ? null : `Country ${countryId}`,
        emailEnabled: true, smtpHost: 'smtp.example.com', smtpPort: 587, smtpUsername: 'u', smtpPassword: null,
        smtpFromAddress: 'no-reply@example.com', smtpFromName: 'MOHE', smtpUseTls: true, smtpUseSsl: false,
        smsEnabled: false, smsWebhookUrl: null, smsWebhookMethod: 'POST', smsWebhookHeaders: null, smsFromNumber: null,
        whatsappEnabled: false, whatsappWebhookUrl: null, whatsappWebhookHeaders: null,
        whatsappFromNumber: null, whatsappBodyTemplate: null,
        whatsappMessageMode: 'TEXT', whatsappApprovedTemplate: null,
        smtpPasswordSet: true, canEdit: true, updatedAt: '2026-09-19T10:00:00Z',
        ...extra
    });

    const build = async (rows: NotificationSettingsDto[]) => {
        service = {
            listSettings: jasmine.createSpy('listSettings').and.returnValue(of(rows)),
            createSettings: jasmine.createSpy('createSettings').and.callFake((d: any) => of(d)),
            updateSettings: jasmine.createSpy('updateSettings').and.callFake((d: any) => of(d)),
            deleteSettings: jasmine.createSpy('deleteSettings').and.returnValue(of(undefined)),
            testSettings: jasmine.createSpy('testSettings').and.returnValue(of({ ok: true, enabled: true, error: null }))
        };
        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [NotificationsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: CodeListService, useValue: codeListStub({}, 'en') },
                { provide: NotificationService, useValue: service },
                {
                    provide: LookupService,
                    useValue: new Proxy({
                        getAllStudyCountries: () => of([
                            { studyCountryInId: 1004, studyCountryInName: 'Jordan' },
                            { studyCountryInId: 1005, studyCountryInName: 'Egypt' }
                        ])
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
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => fixture?.nativeElement.remove());

    const bodyRows = (): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));

    it('lists the global row and each attaché row, with a tag per channel', async () => {
        await build([row(null), row(1004, { whatsappEnabled: true, whatsappWebhookUrl: 'https://gw' })]);

        const rendered = bodyRows().map(r => r.innerText);
        expect(rendered.length).toBe(2);
        expect(rendered[0]).toContain('NOTIFICATIONS.SETTINGS.GLOBAL_DEFAULT');
        expect(rendered[1]).toContain('Country 1004');
        expect(fixture.nativeElement.querySelectorAll('tbody p-tag').length).toBe(6);
    });

    it('offers to add only the attachés that have no row yet', async () => {
        await build([row(null), row(1004)]);
        const component = fixture.componentInstance;

        expect(component.addableCountries.map((c: any) => c.id)).toEqual([1005]);
        component.openNew();
        expect(component.current.countryId).withContext('the only choice is preselected').toBe(1005);
        expect(component.current.smtpHost).withContext('starts from the global values').toBe('smtp.example.com');
        expect(component.current.smtpPassword).withContext('secrets are never copied').toBeNull();
    });

    it('adds with POST and edits with PUT, without the read-only fields', async () => {
        await build([row(null), row(1004)]);
        const component = fixture.componentInstance;

        component.openNew();
        component.save();
        expect(service.createSettings).toHaveBeenCalled();
        const created = service.createSettings.calls.mostRecent().args[0];
        expect(created.countryId).toBe(1005);
        expect('canEdit' in created).withContext('read-only fields are not sent back').toBeFalse();

        component.editRow(component.rows[1]);
        component.save();
        expect(service.updateSettings).toHaveBeenCalled();
        expect(service.updateSettings.calls.mostRecent().args[0].countryId).toBe(1004);
    });

    it('will not save a channel that is on with nowhere to send', async () => {
        await build([row(null)]);
        const component = fixture.componentInstance;

        component.editRow(component.rows[0]);
        component.current.whatsappEnabled = true;
        component.save();

        expect(service.updateSettings).not.toHaveBeenCalled();
        expect(component.formProblem).toBe('NOTIFICATIONS.SETTINGS.WHATSAPP_URL_REQUIRED');
    });

    it('asks for the approved template when the message type sends it, and offers an example', async () => {
        await build([row(null, { whatsappEnabled: true, whatsappWebhookUrl: 'https://gw' })]);
        const component = fixture.componentInstance;

        component.editRow(component.rows[0]);
        component.current.whatsappMessageMode = 'AUTO';
        component.save();
        expect(service.updateSettings).not.toHaveBeenCalled();
        expect(component.formProblem).toBe('NOTIFICATIONS.SETTINGS.WHATSAPP_APPROVED_REQUIRED');

        component.insertExampleApprovedTemplate();
        expect(component.current.whatsappApprovedTemplate).toContain('{{bodyOneLine}}');
        component.save();
        expect(service.updateSettings.calls.mostRecent().args[0].whatsappMessageMode).toBe('AUTO');
    });

    it('deletes an attaché row but never the global one, and not a row the user may not change', async () => {
        await build([row(null), row(1004), row(1005, { canEdit: false })]);
        const component = fixture.componentInstance;

        component.confirmDelete(component.rows[0]);
        expect(component.deleteDialogVisible).withContext('global row').toBeFalse();
        component.confirmDelete(component.rows[2]);
        expect(component.deleteDialogVisible).withContext('row without canEdit').toBeFalse();

        component.confirmDelete(component.rows[1]);
        expect(component.deleteDialogVisible).toBeTrue();
        component.deleteRow();
        expect(service.deleteSettings).toHaveBeenCalledWith(1004);
        expect(service.listSettings).toHaveBeenCalledTimes(2);
    });

    it('opens a row the user may not change read-only, with no save button', async () => {
        await build([row(null, { canEdit: false })]);
        const component = fixture.componentInstance;

        component.editRow(component.rows[0]);
        expect(component.readOnly).toBeTrue();
        component.save();
        expect(service.updateSettings).not.toHaveBeenCalled();
    });

    it('shows the server reason when a save is refused', async () => {
        await build([row(null), row(1004)]);
        const component = fixture.componentInstance;
        const messages = TestBed.inject(MessageService);
        const add = spyOn(messages, 'add');
        service.updateSettings.and.returnValue(throwError(() => ({ error: { message: 'not in scope' } })));

        component.editRow(component.rows[1]);
        component.save();

        expect(add.calls.mostRecent().args[0].detail).toBe('not in scope');
    });

    it('tests a row through its own settings, on WhatsApp too', async () => {
        await build([row(null), row(1004, { emailEnabled: false, whatsappEnabled: true, whatsappWebhookUrl: 'https://gw' })]);
        const component = fixture.componentInstance;

        component.openTestDialog(component.rows[1]);
        expect(component.testChannel).withContext('the first enabled channel').toBe('WHATSAPP');
        component.testTo = '+962790000000';
        component.sendTest();

        expect(service.testSettings).toHaveBeenCalledWith(1004, 'WHATSAPP', '+962790000000');
    });
});
