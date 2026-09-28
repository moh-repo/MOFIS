import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map, shareReplay, tap } from 'rxjs/operators';
import { ApiUrlService } from './api-url.service';

/** p-tag severity derived from a ministry-set target; 'secondary' means "no target agreed". */
export type MetricSeverity = 'success' | 'warn' | 'danger' | 'secondary';

/**
 * One KPI target as the backend exposes it. The source of truth is
 * MOHECORE.REPORT_METRIC_TARGET (V028), keyed by (METRIC_CATEGORY, METRIC_NAME);
 * the backend joins it into GET /mohe/api/statistical-reports/performance-metrics,
 * whose rows carry metricCategory / metricName / targetValue (null when no row).
 */
export interface ReportMetricTarget {
    metricCategory: string;
    metricName: string;
    targetValue: number | null;
}

interface PerformanceMetricRow {
    metricCategory: string;
    metricName: string;
    targetValue: number | null;
}

/**
 * Targets for report KPIs, read from the database instead of being hardcoded.
 *
 * <p>Report screens used to colour a rate with literals such as
 * {@code retentionRate > 80 ? 'success' : ...}. No policy backed those numbers.
 * This service asks the backend which targets the ministry has actually set and
 * returns 'secondary' (informational) when none exists for a metric, which is
 * the truth while REPORT_METRIC_TARGET is empty. Inserting a row there activates
 * the colour with no frontend change.
 *
 * <p>Usage: call {@link load} once (e.g. in ngOnInit, with takeUntil) so the
 * snapshot is populated, then call {@link severityFor} from the template.
 */
@Injectable({ providedIn: 'root' })
export class ReportMetricTargetService {

    private targets$?: Observable<ReportMetricTarget[]>;
    private snapshot = new Map<string, number>();

    constructor(private http: HttpClient, private apiUrl: ApiUrlService) {}

    /** Cached list of targets; the request runs once per session and is shared. */
    load(): Observable<ReportMetricTarget[]> {
        if (!this.targets$) {
            this.targets$ = this.http
                .get<PerformanceMetricRow[]>(this.apiUrl.apiPath('/api/statistical-reports/performance-metrics'))
                .pipe(
                    map(rows => (Array.isArray(rows) ? rows : []).map(r => ({
                        metricCategory: r.metricCategory,
                        metricName: r.metricName,
                        targetValue: r.targetValue == null ? null : Number(r.targetValue)
                    }))),
                    catchError(() => {
                        // Not cached: a later call retries instead of pinning "no targets".
                        this.targets$ = undefined;
                        return of([] as ReportMetricTarget[]);
                    }),
                    tap(list => {
                        this.snapshot = new Map(
                            list
                                .filter(t => t.targetValue != null && isFinite(t.targetValue))
                                .map(t => [this.key(t.metricCategory, t.metricName), t.targetValue as number])
                        );
                    }),
                    shareReplay(1)
                );
        }
        return this.targets$;
    }

    /** The agreed target for a metric, or null when none has been set. */
    targetFor(reportCode: string, metricCode: string): number | null {
        return this.snapshot.get(this.key(reportCode, metricCode)) ?? null;
    }

    /**
     * Severity for a measured value against its target.
     *
     * <p>'secondary' when no target exists or the value is missing; 'success' at
     * or above the target; 'danger' below it. REPORT_METRIC_TARGET records a
     * single TARGET_VALUE and no warning band, so 'warn' is never produced here:
     * a "near target" band would be a number nobody set.
     */
    severityFor(reportCode: string, metricCode: string, value: number | null | undefined): MetricSeverity {
        const target = this.targetFor(reportCode, metricCode);
        if (target == null || value == null || !isFinite(value)) {
            return 'secondary';
        }
        return value >= target ? 'success' : 'danger';
    }

    private key(reportCode: string, metricCode: string): string {
        return `${(reportCode ?? '').toLowerCase()}|${(metricCode ?? '').toLowerCase()}`;
    }
}
