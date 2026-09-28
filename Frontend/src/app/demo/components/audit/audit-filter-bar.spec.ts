import { TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';

import { AuditLogModule } from './audit-log.module';
import { AuditLogComponent } from './audit-log.component';
import { AuditLogFilters, AuditLogService, localDateTime } from '../../api/auditlog';
import { TranslationService } from '../../service/translation.service';
import { UmsAuthService } from '../../service/ums-auth.service';

/**
 * The filter bar on the audit trail, and what it sends.
 *
 * <p>The page used to hold two boxes, each with its own full-width Search
 * button under it, in a hand-styled grey panel; searching by table replaced
 * the list, searching by user replaced it again, and the two could not be
 * combined. The endpoint had always taken six parameters at once. The bar now
 * offers all six, and the request carries whatever is set — which is the part
 * a layout test cannot see and this file pins directly.
 */

const page = () => of({ content: [], totalElements: 0, totalPages: 0, currentPage: 0, pageSize: 25 });

const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
};

describe('filter bar on audit logs', () => {

    let fixture: any;
    let component: AuditLogComponent;
    let service: { getAuditLogs: jasmine.Spy; getDistinctTableNames: jasmine.Spy; getDistinctUsers: jasmine.Spy };

    beforeEach(async () => {
        service = {
            getAuditLogs: jasmine.createSpy('getAuditLogs').and.callFake(() => page()),
            getDistinctTableNames: jasmine.createSpy('getDistinctTableNames').and.returnValue(of(['STUDENT_DATA', 'MOHE_QUARTER'])),
            getDistinctUsers: jasmine.createSpy('getDistinctUsers').and.returnValue(of(['admin']))
        };

        await TestBed.configureTestingModule({
            imports: [AuditLogModule, RouterModule.forRoot([])],
            providers: [
                { provide: AuditLogService, useValue: service },
                { provide: TranslationService, useValue: translation },
                // profile$ and currentUser$ are named explicitly: AuthService projects the
                // shell user from them in its constructor, and the proxy's fallback hands
                // back a function, which has no subscribe.
                { provide: UmsAuthService, useValue: new Proxy(
                    { permissions$: of([]), profile$: of(), currentUser$: of(null) } as any, {
                        get: (t, p) => p in t ? t[p] : () => true
                    }) }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(AuditLogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();

        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    });

    afterEach(() => fixture.nativeElement.remove());

    const el = (selector: string) => fixture.nativeElement.querySelector(selector) as HTMLElement;
    const all = (selector: string) => Array.from(fixture.nativeElement.querySelectorAll(selector)) as HTMLElement[];
    const lastRequest = (): AuditLogFilters => service.getAuditLogs.calls.mostRecent().args[0];

    it('asks the server for the order: a header sorts the table, not the page', () => {
        // 11,544 rows, twenty-five of them on screen. Sorting in the browser
        // would order the page that happened to be loaded and call it the table.
        expect(lastRequest().sortBy).withContext('newest first, before any header is touched').toBe('changedAt');
        expect(lastRequest().sortDir).toBe('desc');

        const header = all('th').find(th => (th.textContent ?? '').includes('AUDIT_LOGS.TABLE'))!;
        expect(header).withContext('the table column carries a sort header').toBeTruthy();
        header.click();
        fixture.detectChanges();

        expect(lastRequest().sortBy).toBe('tableName');
        expect(lastRequest().sortDir).toBe('asc');
        expect(component.sortField).toBe('tableName');
    });

    it('uses the shared filter bar, six fields over six tracks', () => {
        expect(el('.filter-bar')).withContext('no shared filter bar on the page').toBeTruthy();
        expect(all('.filter-bar__field').length).toBe(6);
        expect(el('.filter-bar__grid').classList).toContain('filter-bar__grid--cols-6');
    });

    it('labels every control, bound to it', () => {
        all('.filter-bar__field').forEach((field, i) => {
            const label = field.querySelector('label') as HTMLLabelElement;
            expect(label).withContext(`field ${i} has no label`).toBeTruthy();
            expect(label.htmlFor).withContext(`field ${i} label is not bound`).not.toBe('');
            expect(field.querySelector(`#${CSS.escape(label.htmlFor)}`))
                .withContext(`field ${i} label points at nothing in its own field`)
                .toBeTruthy();
        });
    });

    it('keeps the buttons out of the field flow', () => {
        const bar = el('.filter-bar__actions');
        expect(bar).withContext('no action row').toBeTruthy();
        expect(bar.querySelectorAll('button').length).toBe(2);
        expect(el('.filter-bar__grid').contains(bar)).toBeFalse();
    });

    it('offers the tables and users the trail actually holds', () => {
        expect(service.getDistinctTableNames).toHaveBeenCalled();
        expect(service.getDistinctUsers).toHaveBeenCalled();
        expect(component.tableOptions.map(o => o.value)).toEqual(['STUDENT_DATA', 'MOHE_QUARTER']);
        expect(component.userOptions.map(o => o.value)).toEqual(['admin']);
    });

    it('asks the server for one page, not the whole trail', () => {
        // The old page asked for size 1000 and paged in the browser.
        const first = service.getAuditLogs.calls.first().args[0] as AuditLogFilters;
        expect(first.page).toBe(0);
        expect(first.size).toBe(component.rows);
        expect(first.size).toBeLessThanOrEqual(100);
    });

    it('sends every filter in one request, and none when cleared', () => {
        component.filterTable = 'STUDENT_DATA';
        component.filterUser = 'admin';
        component.filterOperation = 'UPDATE';
        component.filterRecordId = ' 42 ';
        component.filterFrom = new Date(2026, 7, 1);
        component.filterTo = new Date(2026, 7, 30);
        component.applyFilters();

        const sent = lastRequest();
        expect(sent.tableName).toBe('STUDENT_DATA');
        expect(sent.changedBy).toBe('admin');
        expect(sent.operation).toBe('UPDATE');
        expect(sent.recordId).withContext('trimmed').toBe('42');
        expect(localDateTime(sent.startDate!)).toBe('2026-08-01T00:00:00');
        // The closing date is the last instant of its day, or a range ending
        // today would exclude everything changed today.
        expect(localDateTime(sent.endDate!)).toBe('2026-08-30T23:59:59');
        expect(sent.page).withContext('a changed filter starts from the first page').toBe(0);

        component.clearFilters();
        const cleared = lastRequest();
        for (const key of ['tableName', 'changedBy', 'operation', 'recordId', 'startDate', 'endDate'] as const) {
            expect(cleared[key]).withContext(key).toBeUndefined();
        }
        expect(component.hasFilters).toBeFalse();
    });

    it('sends a date as local wall-clock, which is what a LocalDateTime is', () => {
        // toISOString() would carry a Z and the controller drops the offset:
        // local midnight became 21:00 the day before on a UTC+3 machine.
        expect(localDateTime(new Date(2026, 0, 5, 9, 7, 3))).toBe('2026-01-05T09:07:03');
    });
});
