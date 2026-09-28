import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiUrlService } from './api-url.service';

/** One line of the day agenda. `kind` is a key, not a label — see CALENDAR.KIND.* */
export interface CalendarEntry {
    kind: string;
    referenceId: number | null;
    title: string | null;
    detail: string | null;
    at: string | null;
    uiSeverity: string | null;
}

export interface CalendarDay {
    date: string;
    entries: CalendarEntry[];
    /** Entries of each kind on the day, before the per-kind cap on `entries`. */
    totals: Record<string, number>;
    /**
     * Kinds the server could not read at all (QA C10 / N14). One kind failing
     * used to fail the whole day, so a broken query showed an empty agenda that
     * looked like a free day. The rest is served and these are named, so the
     * panel can say what is missing and offer to try again.
     */
    failed?: string[];
}

export interface CalendarMonth {
    year: number;
    month: number;
    days: { date: string; count: number }[];
    /** Kinds that could not be counted; the month's marks are incomplete. */
    failed?: string[];
}

/**
 * The icon for each kind of agenda entry — the one its own screen shows in the
 * menu, so a row is recognisable before it is read.
 *
 * <p>Not a code list, and deliberately not in a {@code LU_} table: a kind is a
 * branch in {@code CalendarAgendaService} paired with a screen here, so adding
 * one is a code change on both sides and there is nothing an administrator could
 * usefully edit. The codes an entry <em>carries</em> — its severity — still come
 * from the code lists, through {@code uiSeverity}.
 */
export const AGENDA_ICONS: Readonly<Record<string, string>> = {
    DOCUMENT_EXPIRY: 'pi pi-file',
    ALERT_CASE: 'pi pi-exclamation-triangle',
    NOTIFICATION: 'pi pi-send',
    QUARTER_PAY: 'pi pi-money-bill',
    QUARTER_RECEIVED: 'pi pi-inbox',
    REPORT_RUN: 'pi pi-clock',
    APPROVAL_PENDING: 'pi pi-check-square'
};

/** For a kind this build does not know — a newer server, an older browser. */
export const AGENDA_FALLBACK_ICON = 'pi pi-circle';

/**
 * The kinds the day holds more of than it listed, and how many more.
 *
 * <p>The server lists a few entries of each kind and reports the true count
 * beside them, so "and 9 more notifications" is the difference between the two —
 * not something that can be read off the list, which is exactly as long as the
 * cap allows. A kind whose entries all fit is not mentioned.
 */
export function extraByKind(day: CalendarDay): { kind: string; extra: number }[] {
    const listed = new Map<string, number>();
    for (const entry of day.entries ?? []) {
        listed.set(entry.kind, (listed.get(entry.kind) ?? 0) + 1);
    }
    return Object.entries(day.totals ?? {})
        .map(([kind, total]) => ({ kind, extra: total - (listed.get(kind) ?? 0) }))
        .filter(more => more.extra > 0);
}

/**
 * What falls on a day, for the calendar in the topbar.
 *
 * <p>The server decides which kinds the caller may see — a kind whose screen the
 * user cannot open is absent from the payload — so nothing here filters by
 * permission a second time.
 */
@Injectable({ providedIn: 'root' })
export class CalendarAgendaService {

    private readonly base = '/api/v1/calendar';

    constructor(
        private http: HttpClient,
        private apiUrl: ApiUrlService
    ) {}

    /** @param date a calendar date, in the application's time zone */
    day(date: Date): Observable<CalendarDay> {
        const params = new HttpParams().set('date', CalendarAgendaService.isoDate(date));
        return this.http.get<CalendarDay>(this.apiUrl.apiPath(`${this.base}/day`), { params });
    }

    month(year: number, month: number): Observable<CalendarMonth> {
        const params = new HttpParams().set('year', year).set('month', month);
        return this.http.get<CalendarMonth>(this.apiUrl.apiPath(`${this.base}/month`), { params });
    }

    /**
     * `yyyy-MM-dd` for the date the user picked.
     *
     * <p>Not `toISOString()`: that converts to UTC first, so a date picked east
     * of Greenwich comes back as the day before whenever the local midnight has
     * not yet reached UTC. The picker hands us a calendar date; it is sent as one.
     */
    static isoDate(date: Date): string {
        const pad = (n: number) => `${n}`.padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }
}
