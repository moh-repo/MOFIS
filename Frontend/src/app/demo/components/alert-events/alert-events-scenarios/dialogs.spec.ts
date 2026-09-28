import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { AlertEventsScenariosComponent } from './alert-events-scenarios.component';
import { AlertEventsModule } from '../alert-events.module';
import { AlertEventsService, AlertEventScenarioDto } from '../../../service/alert-events.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';
import { CodeListService } from '../../../service/code-list.service';
import { codeListStub, codeRow } from '../../../../testing/code-list.stub';

/**
 * The alert scenarios page's dialogs (25 Sep 2026): add and edit rebuilt in the
 * .dv-* vocabulary every other record's dialogs use, and a new read-only view
 * of one scenario that can hand over to editing.
 *
 * <p>Dialogs land on <body> (providePrimeNG's overlayAppendTo, as in
 * app.module.ts), so they are read from the document, not the fixture.
 */
describe('AlertEventsScenarios — add, edit and view dialogs', () => {

    let fixture: ComponentFixture<AlertEventsScenariosComponent>;
    let component: AlertEventsScenariosComponent;

    const scenario = (patch: Partial<AlertEventScenarioDto> = {}): AlertEventScenarioDto => ({
        scenarioId: 7, scenarioCode: 'PASSPORT_EXPIRY_SOON',
        scenarioNameEn: 'Passport expiring soon', scenarioNameAr: 'جواز السفر ينتهي قريباً',
        descriptionEn: 'Fires ninety days before the passport expires.', descriptionAr: '',
        severity: 'HIGH', thresholdDays: 90, isActive: true,
        createdAt: '2026-01-05T08:00:00Z', updatedAt: '2026-09-01T08:00:00Z', ...patch
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AlertEventsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                providePrimeNG({ overlayAppendTo: 'body' }),
                {
                    provide: CodeListService,
                    useValue: codeListStub({
                        'alert-severity': [
                            codeRow('HIGH', { uiSeverity: 'warn' }),
                            codeRow('MEDIUM', { uiSeverity: 'info' }),
                            codeRow('LOW', { uiSeverity: 'success' })
                        ]
                    })
                },
                {
                    provide: AlertEventsService,
                    useValue: new Proxy({} as any, {
                        get: (_t, prop) => prop === 'getScenarios'
                            ? () => of([scenario(), scenario({ scenarioId: 8, scenarioCode: 'VISA_EXPIRY', isActive: false, severity: 'LOW' })])
                            : () => of([])
                    })
                },
                {
                    provide: LookupService,
                    useValue: new Proxy({} as any, {
                        get: (_t, prop) => prop === 'getAlertSeverities'
                            ? () => of([{ label: 'High', value: 'HIGH' }, { label: 'Medium', value: 'MEDIUM' }, { label: 'Low', value: 'LOW' }])
                            : () => of([])
                    })
                },
                { provide: UmsPermissionService, useValue: { hasPermission: () => true, getAllowedCountryIds: () => [] } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(AlertEventsScenariosComponent);
        component = fixture.componentInstance;
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    afterEach(() => {
        component.viewVisible = false;
        component.dialogVisible = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const settle = async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    /** The .p-dialog that holds the given element. */
    const dialogWith = (selector: string) =>
        document.querySelector(selector)?.closest('.p-dialog.dv-dialog') as HTMLElement;

    const footerButtons = (dialog: HTMLElement) =>
        Array.from(dialog.querySelectorAll('.dv-footer__actions button')).map(b => b.textContent?.trim() ?? '');

    it('every row offers View ahead of the manage buttons', () => {
        const rows = Array.from(fixture.nativeElement.querySelectorAll('tbody tr')) as HTMLElement[];
        expect(rows.length).toBe(2);
        for (const row of rows) {
            // p-button renders its icon as a span, so match on the icon class rather than the tag.
            const icons = Array.from(row.querySelectorAll('td:last-child button'))
                .map(b => b.querySelector('[class*="pi-"]')?.className ?? '');
            expect(icons[0]).toContain('pi-eye');
            expect(icons.some(c => c.includes('pi-pencil'))).toBeTrue();
        }
    });

    it('view: the scenario in the shared vocabulary, coloured by severity', async () => {
        component.openView(scenario());
        await settle();

        const dialog = dialogWith('.dv-kpis');
        expect(dialog).withContext('view dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog)).toBeFalse();
        expect(dialog.querySelector('.dv-hero')?.className).withContext('HIGH is a warning').toContain('dv-hero--warn');
        expect(dialog.querySelector('.dv-hero__badge')?.textContent).toContain('7');
        expect(dialog.querySelector('.dv-hero__eyebrow')?.textContent).toContain('PASSPORT_EXPIRY_SOON');
        expect(dialog.querySelector('.dv-hero__title')?.textContent).toContain('Passport expiring soon');
        expect(dialog.querySelectorAll('.dv-kpi').length).toBe(3);
        expect(dialog.querySelector('.dv-notes')?.textContent).toContain('ninety days');
        expect(dialog.querySelector('.dv-notes--empty')).withContext('no Arabic description').toBeTruthy();
        expect(footerButtons(dialog)).toEqual(['COMMON.CLOSE', 'COMMON.EDIT']);
    });

    it('view: an inactive scenario reads grey whatever its severity', async () => {
        component.openView(scenario({ isActive: false, severity: 'HIGH' }));
        await settle();
        const dialog = dialogWith('.dv-kpis');
        expect(dialog.querySelector('.dv-hero')?.className).toContain('dv-hero--muted');
        expect(dialog.querySelector('.dv-chip')?.textContent).toContain('90 ALERT_EVENTS.DAYS');
    });

    it('add: a new-record hero, the code hint, and every required field marked on an empty save', async () => {
        component.openAdd();
        await settle();

        const dialog = dialogWith('#asCode');
        expect(dialog).withContext('add dialog').toBeTruthy();
        expect(dialog.querySelector('.dv-hero')?.className).toContain('dv-hero--new');
        expect(dialog.querySelector('.dv-notice--info')).withContext('code is fixed once created').toBeTruthy();
        expect((dialog.querySelector('#asCode') as HTMLInputElement).disabled).toBeFalse();
        expect(dialog.querySelector('.dv-hero__title')?.className).toContain('dv-hero__title--placeholder');
        expect(footerButtons(dialog)).toEqual(['COMMON.CANCEL', 'ALERT_EVENTS.ADD_SCENARIO']);

        component.saveDialog();
        await settle();
        expect(dialog.querySelectorAll('.dv-error').length).withContext('two names and the code').toBe(3);
    });

    it('edit: the code is locked, the hero previews the name as typed', async () => {
        component.openEdit(scenario());
        await settle();

        const dialog = dialogWith('#asCode');
        expect((dialog.querySelector('#asCode') as HTMLInputElement).disabled).toBeTrue();
        expect(dialog.querySelector('.dv-label__lock')).withContext('lock on the label').toBeTruthy();
        expect(dialog.querySelector('.dv-notice--info')).withContext('no new-record notice').toBeNull();
        expect(dialog.querySelector('.dv-hero')?.className).toContain('dv-hero--warn');
        expect(dialog.querySelector('.dv-hero__title')?.textContent).toContain('Passport expiring soon');
        expect(footerButtons(dialog)).toEqual(['COMMON.CANCEL', 'COMMON.SAVE']);

        component.form.scenarioNameEn = 'Passport expiry';
        component.form.isActive = false;
        await settle();
        expect(dialog.querySelector('.dv-hero__title')?.textContent).toContain('Passport expiry');
        expect(dialog.querySelector('.dv-hero')?.className).withContext('inactive preview').toContain('dv-hero--muted');
    });

    it('the view hands over to editing the same scenario', async () => {
        component.openView(scenario());
        await settle();
        component.editFromView();
        await settle();

        expect(component.viewVisible).toBeFalse();
        expect(component.dialogVisible).toBeTrue();
        expect(component.dialogMode).toBe('edit');
        expect(component.editingId).toBe(7);
    });
});
