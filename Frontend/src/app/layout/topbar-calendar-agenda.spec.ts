import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CalendarAgendaService, CalendarDay, extraByKind } from '../demo/service/calendar-agenda.service';

/**
 * The topbar calendar's agenda, on the browser's side.
 *
 * <p>Two things here are easy to get wrong and invisible when they are.
 *
 * <p>The date sent to the server: `Date.toISOString()` converts to UTC first, so
 * east of Greenwich a day picked in the calendar arrives as the day before for
 * every hour between local midnight and UTC midnight. The ministry runs at
 * UTC+3, so that is three hours of every day in which the agenda would answer
 * for yesterday — and it would look right for the rest of the day.
 *
 * <p>And "and N more": the server lists a few entries of each kind but reports
 * the true count, so the extra has to be worked out from the difference rather
 * than from the length of the list.
 */
describe('topbar calendar agenda', () => {

    describe('the date it asks about', () => {

        let service: CalendarAgendaService;
        let http: HttpTestingController;

        beforeEach(() => {
            TestBed.configureTestingModule({
                providers: [provideHttpClient(), provideHttpClientTesting(), CalendarAgendaService]
            });
            service = TestBed.inject(CalendarAgendaService);
            http = TestBed.inject(HttpTestingController);
        });

        afterEach(() => http.verify());

        it('sends the day that was picked, not the UTC day it falls in', () => {
            // Half past midnight: in any zone ahead of UTC this instant is still
            // the previous day in UTC.
            const picked = new Date(2026, 7, 30, 0, 30, 0);

            service.day(picked).subscribe();

            const request = http.expectOne(r => r.url.endsWith('/api/v1/calendar/day'));
            expect(request.request.params.get('date'))
                .withContext('the picker hands over a calendar date; it has to be sent as one')
                .toBe('2026-08-30');
            request.flush({ date: '2026-08-30', entries: [], totals: {} });
        });

        it('pads single-digit months and days', () => {
            service.day(new Date(2026, 0, 5)).subscribe();

            const request = http.expectOne(r => r.url.endsWith('/api/v1/calendar/day'));
            expect(request.request.params.get('date')).toBe('2026-01-05');
            request.flush({ date: '2026-01-05', entries: [], totals: {} });
        });

        it('asks for a month by its ordinary number, not the zero-based one', () => {
            service.month(2026, 8).subscribe();

            const request = http.expectOne(r => r.url.endsWith('/api/v1/calendar/month'));
            expect(request.request.params.get('year')).toBe('2026');
            expect(request.request.params.get('month')).toBe('8');
            request.flush({ year: 2026, month: 8, days: [] });
        });
    });

    describe('isoDate', () => {

        it('agrees with the local calendar across the whole day', () => {
            // The failure this guards against only appears in part of the day, so
            // one sample would not have caught it.
            for (let hour = 0; hour < 24; hour++) {
                const date = new Date(2026, 11, 31, hour, 45);
                expect(CalendarAgendaService.isoDate(date))
                    .withContext(`at ${hour}:45`)
                    .toBe('2026-12-31');
            }
        });
    });

    describe('"and N more"', () => {

        const day = (entries: string[], totals: Record<string, number>): CalendarDay => ({
            date: '2026-08-30',
            entries: entries.map(kind => ({ kind, referenceId: 1, title: kind, detail: null, at: null, uiSeverity: null })),
            totals
        });

        it('counts what the day holds beyond what it listed', () => {
            const result = extraByKind(day(
                ['NOTIFICATION', 'NOTIFICATION', 'NOTIFICATION'],
                { NOTIFICATION: 12 }));

            expect(result).toEqual([{ kind: 'NOTIFICATION', extra: 9 }]);
        });

        it('says nothing about a kind whose entries all fit', () => {
            const result = extraByKind(day(['ALERT_CASE', 'ALERT_CASE'], { ALERT_CASE: 2 }));

            expect(result).toEqual([]);
        });

        it('counts each kind against its own total', () => {
            const result = extraByKind(day(
                ['ALERT_CASE', 'NOTIFICATION', 'NOTIFICATION'],
                { ALERT_CASE: 1, NOTIFICATION: 40 }));

            expect(result).toEqual([{ kind: 'NOTIFICATION', extra: 38 }]);
        });
    });
});
