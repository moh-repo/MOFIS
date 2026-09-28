import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, combineLatest, of } from 'rxjs';
import { catchError, map, shareReplay, tap } from 'rxjs/operators';
import { ApiUrlService } from './api-url.service';
import { UmsReport } from '../api/ums';
import { ReportHubCard, ReportHubPermissionService, hubCardName } from './report-hub-permission.service';

/** A hub and the reports under it, for the searchable catalogue on the landing page. */
export interface ReportCatalogGroup {
    hub: ReportHubCard & { reportCode: string };
    hubTitle: string;
    reports: ReportCatalogCard[];
}

/**
 * Arabic text reduced to what a search should match: no tashkeel, no tatweel, and
 * the letters people type interchangeably folded together (أ إ آ ٱ → ا, ى → ي,
 * ة → ه, ؤ ئ → و ي). Without it, searching "احصائيات" finds nothing when the
 * catalogue says "إحصائيات". Latin text is lower-cased and otherwise untouched.
 */
export function normaliseForSearch(value: string | null | undefined): string {
    return (value || '')
        .toLowerCase()
        .replace(/[\u064B-\u065F\u0670\u0640]/g, '')
        .replace(/[\u0622\u0623\u0625\u0671]/g, '\u0627')
        .replace(/\u0649/g, '\u064A')
        .replace(/\u0629/g, '\u0647')
        .replace(/\u0624/g, '\u0648')
        .replace(/\u0626/g, '\u064A')
        .replace(/\s+/g, ' ')
        .trim();
}

/** One card on a report hub page, named in the reader's language. */
export interface ReportCatalogCard {
    reportCode: string;
    title: string;
    /** The name in the other language, shown as the card's second line. */
    titleAr: string;
    description: string;
    /** PrimeIcons name, `pi-…`. */
    icon: string;
    /** The card's colour token as the catalogue stores it (`cyan`, `green-600`…). */
    color: string;
    route: string;
    queryParams: Record<string, string> | null;
    /** `UMS_Reports.IsActive`: an inactive report is listed as "coming soon". */
    implemented: boolean;
    available: boolean;
}

/** Turns `doc=id-card&x=y` into `{ doc: 'id-card', x: 'y' }`; null when there is nothing. */
export function parseQueryParams(raw: string | null | undefined): Record<string, string> | null {
    if (!raw || !raw.trim()) {
        return null;
    }
    const out: Record<string, string> = {};
    for (const pair of raw.replace(/^\?/, '').split('&')) {
        if (!pair) continue;
        const [k, ...rest] = pair.split('=');
        const key = decodeURIComponent(k.trim());
        if (key) {
            out[key] = decodeURIComponent(rest.join('='));
        }
    }
    return Object.keys(out).length ? out : null;
}

/**
 * The report catalogue: `UMSCORE.UMS_Reports` as a tree. Hubs and landing
 * cards have no parent; each hub's cards carry the hub's code as parent.
 * Loaded once per session and shared; `reload()` after an admin edit.
 */
@Injectable({ providedIn: 'root' })
export class ReportCatalogService {

    private catalog$: Observable<UmsReport[]> | null = null;

    /**
     * True when the last attempt to load the catalogue failed. The card streams
     * still emit an empty list — a hub page with no cards is a survivable state —
     * but the landing page needs to tell the difference between "you may open
     * nothing" and "we could not ask", which it showed as "no access" before.
     */
    loadFailed = false;

    constructor(
        private http: HttpClient,
        private apiUrl: ApiUrlService,
        private hubPermissions: ReportHubPermissionService
    ) { }

    /**
     * Every active row, in catalogue order. A failed request is an empty catalogue —
     * but not a cached one: the failure clears the cache, so the next page that asks
     * fetches again.
     *
     * It used to be cached like any other answer, because `catchError` sat in front
     * of `shareReplay`. One 429 while the report limit was ten a minute (review item
     * P3) therefore emptied every hub page and the landing page for the rest of the
     * session, and only a browser reload brought the reports back.
     */
    getCatalog(): Observable<UmsReport[]> {
        if (!this.catalog$) {
            this.catalog$ = this.http
                .get<UmsReport[]>(this.apiUrl.apiPath('/api/v1/ums/reports/catalog'))
                .pipe(
                    map(rows => (Array.isArray(rows) ? rows : []).slice().sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))),
                    tap(() => { this.loadFailed = false; }),
                    catchError(() => {
                        this.loadFailed = true;
                        this.catalog$ = null;
                        return of([] as UmsReport[]);
                    }),
                    shareReplay(1)
                );
        }
        return this.catalog$;
    }

    /**
     * Every report the reader may open, grouped by hub and named in the language
     * `lang$` emits — the flat catalogue behind the search on "كل التقارير"
     * (review item P1), which until now showed the hub cards and nothing else, so
     * finding a report meant knowing which hub it lived under.
     */
    allReports(lang$: Observable<string>): Observable<ReportCatalogGroup[]> {
        return combineLatest([this.getCatalog(), lang$]).pipe(
            map(([rows, lang]) => {
                const hubs = this.hubPermissions.filterCards(
                    rows.filter(r => r.parentCode == null).map(r => this.toHubCard(r)));
                return hubs
                    .map(hub => {
                        const children = rows.filter(r => r.parentCode === hub.reportCode);
                        const allowed = this.hubPermissions.filterCards(children.map(r => this.toHubCard(r)));
                        return {
                            hub,
                            hubTitle: hubCardName(hub, lang),
                            reports: allowed.map(card =>
                                this.toCatalogCard(rows.find(r => r.reportCode === card.reportCode)!, lang))
                        };
                    })
                    .filter(group => group.reports.length > 0);
            })
        );
    }

    /** Forgets the cached catalogue so the next read fetches it again. */
    reload(): void {
        this.catalog$ = null;
    }

    /** The landing page: every top-level row the reader may open, as hub cards. */
    landingCards(): Observable<ReportHubCard[]> {
        return this.getCatalog().pipe(
            map(rows => this.hubPermissions.filterCards(rows.filter(r => r.parentCode == null).map(r => this.toHubCard(r))))
        );
    }

    /**
     * The cards of the hub whose route is `route` (`/reports/student` …), in
     * the language `lang$` emits, filtered by what the reader may open.
     */
    hubCards(route: string, lang$: Observable<string>): Observable<ReportCatalogCard[]> {
        const path = ReportCatalogService.normaliseRoute(route);
        return combineLatest([this.getCatalog(), lang$]).pipe(
            map(([rows, lang]) => {
                const hub = rows.find(r => r.parentCode == null && ReportCatalogService.normaliseRoute(r.route) === path);
                if (!hub) {
                    return [];
                }
                const cards = rows.filter(r => r.parentCode === hub.reportCode);
                return this.hubPermissions.filterCards(cards.map(r => this.toHubCard(r)))
                    .map(card => this.toCatalogCard(rows.find(r => r.reportCode === card.reportCode)!, lang));
            })
        );
    }

    /**
     * The cards that deep-link with a `doc=` query parameter — the official
     * per-student documents — keyed by that document kind.
     */
    documentCards(lang$: Observable<string>): Observable<Array<ReportCatalogCard & { kind: string }>> {
        return combineLatest([this.getCatalog(), lang$]).pipe(
            map(([rows, lang]) => rows
                .map(r => ({ row: r, params: parseQueryParams(r.queryParams) }))
                .filter(x => !!x.params?.['doc'])
                .map(x => ({ ...this.toCatalogCard(x.row, lang), kind: x.params!['doc'] })))
        );
    }

    toHubCard(r: UmsReport): ReportHubCard & { reportCode: string } {
        return {
            reportCode: r.reportCode,
            nameAr: r.reportNameAr,
            nameEn: r.reportName,
            descAr: r.descriptionAr,
            descEn: r.description,
            icon: r.icon || '',
            color: r.color || '',
            route: r.route,
            queryParams: parseQueryParams(r.queryParams),
            pageCode: r.pageCode || undefined,
            permissionCodes: ReportCatalogService.splitCodes(r.permissionCodes)
        };
    }

    toCatalogCard(r: UmsReport, lang: string): ReportCatalogCard {
        const arabic = lang === 'ar';
        const primary = (arabic ? r.reportNameAr : r.reportName) || r.reportName || r.reportNameAr || r.reportCode;
        const other = (arabic ? r.reportName : r.reportNameAr) || '';
        const description = (arabic ? r.descriptionAr : r.description) || r.description || r.descriptionAr || '';
        return {
            reportCode: r.reportCode,
            title: primary,
            titleAr: other,
            description,
            icon: r.icon || '',
            color: r.color || '',
            route: r.route,
            queryParams: parseQueryParams(r.queryParams),
            implemented: r.isActive !== false,
            available: r.isActive !== false
        };
    }

    /** `A,B` → ['A', 'B']; undefined when the column is empty. */
    static splitCodes(raw: string | null | undefined): string[] | undefined {
        const codes = (raw || '').split(',').map(s => s.trim()).filter(Boolean);
        return codes.length ? codes : undefined;
    }

    static normaliseRoute(route: string | null | undefined): string {
        return (route || '').split('?')[0].split('#')[0].replace(/\/+$/, '');
    }
}
