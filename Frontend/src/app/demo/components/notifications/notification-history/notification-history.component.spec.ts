import { TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { NotificationsModule } from '../notifications.module';
import { NotificationHistoryComponent } from './notification-history.component';
import { NotificationService } from '../../../service/notification.service';
import { ReportCountryScopeService } from '../../../service/report-country-scope.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { UmsGroupService } from '../../../service/ums-group.service';
import { LookupService } from '../../../service/lookup.service';
import { EnumOptionsService } from '../../../service/enum-options.service';
import { CodeListService } from '../../../service/code-list.service';
import { codeListStub, codeRow } from '../../../../testing/code-list.stub';

/**
 * The notification history row and the panel behind it.
 *
 * <p>Three faults, reported together because they read as one on the screen.
 *
 * <p><b>The panel could not be opened at all.</b> The expansion was written as
 * `pTemplate="rowexpansion"`, and PrimeNG 21's Table matches these templates by
 * name against a fixed list that has `expandedrow` on it and not that. The
 * template was never claimed, so `expandedRowTemplate` stayed undefined and the
 * `*ngIf` guarding the expansion row was permanently false. The chevron worked
 * the whole time — clicking it set `expandedRows` correctly — and nothing
 * appeared, which is the worst shape a defect of this kind can take: the state
 * is right, the markup reads right, and the screen is wrong. So the test here
 * clicks and reads the rendered body. A test that asserted the template's
 * existence, or the component's state, would have passed throughout.
 *
 * <p><b>Two columns printed raw codes.</b> `recipientType` and `scope` went
 * straight to the screen while the channel and status beside them went through
 * `codeLabel`, so a reader in Arabic got STUDENT and GROUP mid-row. The names
 * were in the lookup tables all along.
 *
 * <p><b>The chevron did not turn round.</b> Closed, it always pointed right,
 * including in Arabic, where the row opens the other way.
 */
describe('notification history', () => {

    let fixture: any;
    let rtl = false;

    const row = {
        id: 7, batchId: 'batch-1', channel: 'BOTH', recipientType: 'EMPLOYEE', scope: 'GROUP',
        subject: 'a subject', body: 'THE FULL MESSAGE BODY', totalCount: 3, sentCount: 3,
        failedCount: 0, status: 'SENT', errorSummary: null, createdBy: 1,
        createdAt: '2026-08-29T10:00:00Z', sentAt: '2026-08-29T10:01:00Z',
        countryIds: [], countryNames: []
    };

    /** The three lists this row's cells are named from, in both languages. */
    const lists = {
        'notification-channel': [codeRow('BOTH', { icon: 'pi-send' })],
        'notification-recipient-type': [codeRow('EMPLOYEE')],
        'notification-scope': [codeRow('GROUP')],
        'notification-send-status': [codeRow('SENT', { uiSeverity: 'success' })]
    };

    const build = async (dir: 'ltr' | 'rtl') => {
        rtl = dir === 'rtl';

        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [NotificationsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: CodeListService, useValue: codeListStub(lists, rtl ? 'ar' : 'en') },
                {
                    provide: NotificationService,
                    useValue: { getHistory: () => of({ content: [row], totalElements: 1, totalPages: 1, number: 0, size: 20 }) }
                },
                { provide: ReportCountryScopeService, useValue: { selectedCountryId$: of(null), selectedCountryId: null } },
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
                { provide: UmsGroupService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
                {
                    provide: UmsAuthService,
                    useValue: new Proxy({ permissions$: of([]), filterToAllowedStudyCountries: (items: any[]) => items } as any, {
                        get: (t: any, p: any) => p in t ? t[p] : () => true
                    })
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of(rtl ? 'ar' : 'en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => rtl, translate: (k: string) => k,
                        getCurrentLanguage: () => rtl ? 'ar' : 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(NotificationHistoryComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    };

    afterEach(() => fixture?.nativeElement.remove());

    const host = (): HTMLElement => fixture.nativeElement;
    const cells = () =>
        Array.from(host().querySelectorAll('tbody tr td')).map(c => (c as HTMLElement).innerText.trim());

    const toggle = () => {
        (host().querySelector('tbody button') as HTMLElement).click();
        fixture.detectChanges();
    };

    it('opens the row and shows the whole message', async () => {
        await build('ltr');

        expect(host().innerText).withContext('the panel should start closed')
            .not.toContain('THE FULL MESSAGE BODY');

        toggle();

        expect(host().innerText)
            .withContext('the expansion template is not being rendered at all')
            .toContain('THE FULL MESSAGE BODY');
    });

    it('opens the row in Arabic too', async () => {
        // The template name was the fault, not the direction, but the panel is
        // what the reader asked for in Arabic — so it is checked there.
        await build('rtl');
        toggle();

        expect(host().innerText).toContain('THE FULL MESSAGE BODY');
    });

    it('closes the row again', async () => {
        await build('ltr');
        toggle();
        toggle();

        expect(host().innerText).not.toContain('THE FULL MESSAGE BODY');
    });

    it('shows which channels the message went out on', async () => {
        // A send to both channels is one row with channel BOTH; the panel is
        // where the reader confirms it, so the row has to name it rather than
        // print the code.
        await build('rtl');

        expect(cells()).withContext(cells().join(' | ')).toContain('BOTH-ar');
    });

    it('names the recipient type and the scope instead of printing their codes', async () => {
        await build('rtl');

        const rendered = cells();
        expect(rendered).withContext(rendered.join(' | ')).toContain('EMPLOYEE-ar');
        expect(rendered).withContext(rendered.join(' | ')).toContain('GROUP-ar');
        expect(rendered).withContext('raw codes are still reaching the screen')
            .not.toContain('EMPLOYEE');
        expect(rendered).not.toContain('GROUP');
    });

    it('names them in English when the reader is in English', async () => {
        await build('ltr');

        const rendered = cells();
        expect(rendered).withContext(rendered.join(' | ')).toContain('EMPLOYEE-en');
        expect(rendered).withContext(rendered.join(' | ')).toContain('GROUP-en');
    });

    it('points the closed chevron the way the language reads', async () => {
        for (const dir of ['ltr', 'rtl'] as const) {
            await build(dir);

            const icon = host().querySelector('tbody button .p-button-icon') as HTMLElement;
            const expected = dir === 'ltr' ? 'pi-chevron-right' : 'pi-chevron-left';

            expect(icon.className).withContext(`${dir}: icon is ${icon.className}`).toContain(expected);

            fixture.nativeElement.remove();
        }
    });

    it('turns the chevron down once the row is open, in both directions', async () => {
        for (const dir of ['ltr', 'rtl'] as const) {
            await build(dir);
            toggle();

            const icon = host().querySelector('tbody button .p-button-icon') as HTMLElement;
            expect(icon.className).withContext(`${dir}: icon is ${icon.className}`).toContain('pi-chevron-down');

            fixture.nativeElement.remove();
        }
    });

    // ────────────────────────────────────────────────────────── filter bar ──

    it('uses the shared filter bar', async () => {
        await build('ltr');

        expect(host().querySelector('.filter-bar')).withContext('no shared filter bar').toBeTruthy();
        expect(host().querySelectorAll('.filter-bar__field').length).toBe(7);
    });

    it('filters by one attaché, sent to the server as countryId, and clears back to all', async () => {
        await build('ltr');
        const component = fixture.componentInstance;
        expect(component.countryOptions.map((c: any) => c.id)).toEqual([1004, 1005]);

        const service = TestBed.inject(NotificationService);
        const spy = spyOn(service, 'getHistory').and.callThrough();
        component.filterCountryId = 1005;
        component.applyFilters();
        expect(spy.calls.mostRecent().args[2]).toBe(1005);
        expect(component.hasFilters).toBeTrue();

        component.clearFilters();
        expect(spy.calls.mostRecent().args[2]).withContext('cleared means every attaché in scope').toBeNull();
    });

    it('declares a column count its fields divide into', async () => {
        // Never auto-fit: it picks a count from the available width and strands
        // whatever does not divide into it.
        await build('ltr');

        const grid = host().querySelector('.filter-bar__grid') as HTMLElement;
        const fields = host().querySelectorAll('.filter-bar__field').length;
        const wide = host().querySelectorAll('.filter-bar__field--wide').length;
        const declared = Array.from(grid.classList)
            .map(c => /^filter-bar__grid--cols-(\d+)$/.exec(c))
            .find(Boolean);

        expect(declared).withContext(`no cols-N on: ${grid.className}`).toBeTruthy();
        expect(Number(declared![1])).toBe(fields + wide);
    });

    it('labels every filter, bound to its control', async () => {
        await build('ltr');

        Array.from(host().querySelectorAll('.filter-bar__field')).forEach((field, i) => {
            const label = field.querySelector('label') as HTMLLabelElement;
            expect(label).withContext(`field ${i} has no label`).toBeTruthy();
            expect(label.htmlFor).withContext(`field ${i} label is not bound`).not.toBe('');
        });
    });

    it('keeps the buttons out of the field grid', async () => {
        await build('ltr');

        const actions = host().querySelector('.filter-bar__actions') as HTMLElement;
        expect(actions).withContext('no action row').toBeTruthy();
        expect((host().querySelector('.filter-bar__grid') as HTMLElement).contains(actions))
            .withContext('the buttons are back inside the field grid').toBeFalse();
    });

    it('offers only codes the table can actually show', async () => {
        // The dropdowns read the same lists the cells are named from, so a
        // filter can never offer a value no row could carry.
        await build('rtl');
        const component = fixture.componentInstance;

        expect(component.options('notification-channel').map((o: any) => o.value)).toEqual(['BOTH']);
        expect(component.options('notification-recipient-type').map((o: any) => o.label)).toEqual(['EMPLOYEE-ar']);
    });

    it('sends the chosen filters to the server, and nothing for the ones left empty', async () => {
        // Narrowing is the server's job: the table is lazy and holds one page,
        // so a filter applied in the browser would search that page only.
        await build('ltr');
        const service = TestBed.inject(NotificationService);
        const spy = spyOn(service, 'getHistory').and.callThrough();

        const component = fixture.componentInstance;
        component.filterChannel = 'EMAIL';
        component.filterStatus = 'FAILED';
        component.applyFilters();

        const filters = spy.calls.mostRecent().args[3] as any;
        expect(filters.channel).toBe('EMAIL');
        expect(filters.status).toBe('FAILED');
        expect(filters.recipientType).withContext('an untouched filter must not be sent').toBeNull();
        expect(filters.scope).toBeNull();
    });

    it('asks for the last instant of the closing day, not its midnight', async () => {
        // The picker hands back midnight. Sent as it is, a range ending today
        // would exclude everything sent today — the opposite of what choosing
        // today means.
        await build('ltr');
        const service = TestBed.inject(NotificationService);
        const spy = spyOn(service, 'getHistory').and.callThrough();

        const component = fixture.componentInstance;
        component.filterSentTo = new Date(2026, 7, 29);
        component.applyFilters();

        const sentTo = new Date((spy.calls.mostRecent().args[3] as any).sentTo);
        expect(sentTo.getHours()).toBe(23);
        expect(sentTo.getMinutes()).toBe(59);
        expect(sentTo.getDate()).withContext('still the day that was chosen').toBe(29);
    });

    it('goes back to the first page when the filters change', async () => {
        // Page four of the old list is rarely page four of the new one.
        await build('ltr');
        const component = fixture.componentInstance;
        component.page = 3;

        component.filterChannel = 'SMS';
        component.applyFilters();

        expect(component.page).toBe(0);
    });

    it('clears every filter at once and reloads', async () => {
        await build('ltr');
        const component = fixture.componentInstance;
        component.filterChannel = 'EMAIL';
        component.filterSentFrom = new Date(2026, 0, 1);

        expect(component.hasFilters).toBeTrue();

        component.clearFilters();

        expect(component.hasFilters).toBeFalse();
        expect(component.filterChannel).toBeNull();
        expect(component.filterSentFrom).toBeNull();
    });

    it('says nothing matched rather than nothing was sent, when a filter is on', async () => {
        // Two different statements, and with a filter bar on the page the first
        // one is usually untrue.
        await build('ltr');
        const component = fixture.componentInstance;
        component.logs = [];
        fixture.detectChanges();

        expect(host().innerText).toContain('NOTIFICATIONS.HISTORY.EMPTY');

        component.filterStatus = 'FAILED';
        fixture.detectChanges();

        expect(host().innerText).toContain('NOTIFICATIONS.HISTORY.NO_MATCH');
    });

    it('spans the empty message across every column', async () => {
        // It said ten; the table has eleven, counting the toggler.
        await build('ltr');

        const columns = host().querySelectorAll('thead th').length;
        fixture.componentInstance.logs = [];
        fixture.detectChanges();

        const empty = host().querySelector('tbody td[colspan]') as HTMLElement;
        expect(Number(empty.getAttribute('colspan'))).toBe(columns);
    });
});
