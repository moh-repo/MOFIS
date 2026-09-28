import { HttpParams } from '@angular/common/http';
import { ReportCountryScopeService } from './report-country-scope.service';
import { ReportPdfQuery, buildReportPdfQuery } from './report-pdf.service';

export type ReportFilterEntry = readonly [string, string | number | boolean | null | undefined];

/**
 * Build query map for report list/search, server PDF preview/download, and in-page blob export.
 */
export function buildReportFilterQuery(entries: ReadonlyArray<ReportFilterEntry>): ReportPdfQuery {
  return buildReportPdfQuery(entries);
}

/** HttpParams for Angular HttpClient GET (view data or blob PDF). */
export function buildReportHttpParams(
  entries: ReadonlyArray<ReportFilterEntry>,
  countryScope?: ReportCountryScopeService,
  extras?: ReadonlyArray<ReportFilterEntry>
): HttpParams {
  let params = new HttpParams();
  const countryId = countryScope?.countryIdParam();
  if (countryId != null) {
    params = params.set('countryId', String(countryId));
  }
  for (const [key, value] of [...entries, ...(extras ?? [])]) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

/** URLSearchParams for legacy fetch patterns. */
export function buildReportSearchParams(
  entries: ReadonlyArray<ReportFilterEntry>,
  countryScope?: ReportCountryScopeService
): URLSearchParams {
  const params = new URLSearchParams();
  const countryId = countryScope?.countryIdParam();
  if (countryId != null) {
    params.append('countryId', String(countryId));
  }
  for (const [key, value] of entries) {
    if (value !== null && value !== undefined && value !== '') {
      params.append(key, String(value));
    }
  }
  return params;
}
