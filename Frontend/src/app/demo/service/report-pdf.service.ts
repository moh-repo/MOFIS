import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiUrlService } from './api-url.service';
import { MessageService } from 'primeng/api';
import { TranslationService } from './translation.service';
import { ReportPdfModalService } from './report-pdf-modal.service';
import { readApiError } from './api-error.util';
import { silentErrors } from '../interceptors/http-context.tokens';

export type ReportPdfQueryScalar = string | number | boolean;
export type ReportPdfQueryValue = ReportPdfQueryScalar | ReportPdfQueryScalar[];
export type ReportPdfQuery = Record<string, ReportPdfQueryValue>;

/** Avoid dot-access on `Record<>` (see `noPropertyAccessFromIndexSignature` in tsconfig). */
export function buildReportPdfQuery(
  entries: ReadonlyArray<readonly [string, ReportPdfQueryValue | null | undefined]>
): ReportPdfQuery {
  const out: ReportPdfQuery = {};
  for (const [k, v] of entries) {
    if (v === null || v === undefined || v === '') {
      continue;
    }
    if (Array.isArray(v) && v.length === 0) {
      continue;
    }
    out[k] = v;
  }
  return out;
}

@Injectable({ providedIn: 'root' })
export class ReportPdfService {
  constructor(
    private http: HttpClient,
    private apiUrl: ApiUrlService,
    private messageService: MessageService,
    private translationService: TranslationService,
    private reportPdfModalService: ReportPdfModalService
  ) {}

  /**
   * Opens the PDF in an in-app EmbedPDF modal dialog.
   */
  previewPdf(apiPath: string, query: ReportPdfQuery = buildReportPdfQuery([]), filename = 'report.pdf', body?: unknown): void {
    this.reportPdfModalService.open({
      pdfApiPath: apiPath,
      pdfQuery: { ...query, disposition: 'inline' },
      pdfBody: body,
      filename
    });
  }

  /**
   * Downloads the PDF via HttpClient blob fetch (same-origin in dev via proxy).
   *
   * Resolves `true` once the file has been handed to the browser and `false` once a
   * failure has been reported; it never rejects. Callers that only fire the download
   * can ignore it — the chart pages await it to show progress (R5-11).
   */
  downloadPdf(apiPath: string, query: ReportPdfQuery, defaultFilename: string, body?: unknown): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      this.fetchPdfBlob(apiPath, { ...query, disposition: 'attachment' }, body).subscribe({
        next: (blob) => {
          if (!blob?.size || blob.type.includes('json')) {
            // JSON where a file was expected: the server's explanation, not a download.
            this.toastPdfError(blob?.size ? { status: 200, error: blob } : undefined);
            resolve(false);
            return;
          }
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = defaultFilename;
          document.body.appendChild(anchor);
          anchor.click();
          document.body.removeChild(anchor);
          URL.revokeObjectURL(url);
          resolve(true);
        },
        error: (error) => {
          this.toastPdfError(error);
          resolve(false);
        },
        // A no-op after `next`; there only so a caller showing progress can never wait forever.
        complete: () => resolve(false)
      });
    });
  }

  /**
   * Prints the report.
   *
   * Opens the same EmbedPDF modal as {@link previewPdf} and raises the print dialog
   * once the document has rendered, so the printed pages are the Jasper PDF itself
   * rather than a second layout built in HTML. The viewer stays open behind the
   * dialog, which is also what lets the user re-print or change pages.
   */
  printPdf(apiPath: string, query: ReportPdfQuery = buildReportPdfQuery([]), filename = 'report.pdf', body?: unknown): void {
    this.reportPdfModalService.open({
      pdfApiPath: apiPath,
      pdfQuery: { ...query, disposition: 'inline' },
      pdfBody: body,
      filename,
      print: true
    });
  }

  /**
   * Downloads the report as a spreadsheet.
   *
   * Hits the `/export-excel` alias of the very same report endpoint, so the workbook
   * is rendered from the same JRXML, with the same filters and country scope, as the
   * PDF beside it — see ReportExports on the server.
   */
  exportExcel(apiPath: string, query: ReportPdfQuery, defaultFilename: string, body?: unknown): void {
    this.fetchPdfBlob(this.toExcelApiPath(apiPath), { ...query, disposition: 'attachment' }, body).subscribe({
      next: (blob) => {
        if (!blob?.size || blob.type.includes('json')) {
          // JSON where a file was expected: the server's explanation, not a download.
          this.toastPdfError(blob?.size ? { status: 200, error: blob } : undefined);
          return;
        }
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = defaultFilename;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        URL.revokeObjectURL(url);
      },
      error: (error) => this.toastPdfError(error)
    });
  }

  /**
   * Swaps a report's PDF alias for its spreadsheet one. Pages therefore configure a
   * single API path and both formats follow from it.
   */
  toExcelApiPath(apiPath: string): string {
    return apiPath.replace(/\/(export-report|report-pdf|doc)$/, '/export-excel');
  }

  /**
   * Fetch PDF bytes via HttpClient (same-origin when apiUrl is empty + dev proxy).
   *
   * <p>The only way this service reaches a report endpoint. Authentication is the
   * `Authorization` header that `AuthInterceptor` attaches, which is the point:
   * this service used to also expose `openPdfInNewTab`, `buildViewerPdfUrl` and
   * `buildInlinePdfUrl`, each of which built a URL with the full 8-hour JWT in a
   * `?token=` query parameter. A bearer token in a URL is written to the web
   * server's access log, kept in the browser's history and address bar, and
   * handed to any link the page follows unless `Referrer-Policy` stops it.
   *
   * <p>They were removed rather than fixed because nothing called them and
   * nothing could: `JwtAuthFilter` stopped accepting `?token=` (the alert stream,
   * the last caller, now mints a single-use `?ticket=` instead), and `/api/pdf/**`
   * is not `permitAll`, so every one of those URLs was a 401 that also leaked a
   * credential. Do not reintroduce the pattern — if a direct navigation is ever
   * genuinely needed, mint a short-lived single-use ticket the way
   * `AlertStreamTicketService` does.
   */
  fetchPdfBlob(apiPath: string, query: ReportPdfQuery = buildReportPdfQuery([]), body?: unknown): Observable<Blob> {
    const params = this.buildHttpParams(query);
    const url = this.apiUrl.apiPath(this.normalizePdfApiPath(apiPath));

    // A report whose input the server cannot look up for itself — the chart pages
    // send the cards they rendered — has to POST them. Everything else about the
    // request is identical, the Authorization header included, so the two verbs
    // share this one method rather than drifting into two request paths.
    // Silent: the body of a failed report request is a Blob, which the global
    // interceptor cannot read, so it could only ever say something generic — and it
    // said it on top of this service's own toast. toastPdfError below reads the blob
    // and gives the one message that fits (review items P2 and P7).
    const context = silentErrors();

    if (body !== undefined && body !== null) {
      return this.http.post(url, body, { params, responseType: 'blob', context });
    }

    return this.http.get(url, { params, responseType: 'blob', context });
  }

  /**
   * Ad blockers block paths containing "reports", "export-report", or "report-pdf".
   * We route all PDF fetches through /api/pdf/ — an innocuous prefix the dev proxy
   * rewrites back to /api/. Idempotent: a path already starting with /api/pdf/ is unchanged.
   */
  normalizePdfApiPath(apiPath: string): string {
    let path = apiPath
      .replace('/export-report', '/doc')
      .replace('/report-pdf', '/doc');
    if (!path.startsWith('/api/pdf/')) {
      path = path.replace(/^\/api\//, '/api/pdf/');
    }
    return path;
  }

  /** The report's own filters, and nothing else — see {@link fetchPdfBlob} on the credential. */
  private buildHttpParams(query: ReportPdfQuery): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value === null || value === undefined || value === '') {
        continue;
      }
      if (Array.isArray(value)) {
        for (const v of value) {
          params = params.append(key, String(v));
        }
      } else {
        params = params.set(key, String(value));
      }
    }
    return params;
  }

  /**
   * The single toast for a report that did not arrive.
   *
   * Reads the failure's own body first — it is a Blob for these requests, so the
   * server's message was being thrown away and everything from "too many rows" to
   * "the renderer is busy" came out as "could not load the PDF report" (review item
   * P7). Codes come from ReportException on the server; the message beside them is
   * already in the user's language, so it is shown as it stands.
   */
  toastPdfError(error?: unknown): void {
    if (error === undefined) {
      this.showPdfError(null, 0);
      return;
    }
    void readApiError(error).then(info => {
      if (info.status === 401) {
        return; // the session is gone; the interceptor is already signing the user out
      }
      this.showPdfError(info.message, info.status, info.retryAfterSeconds);
    });
  }

  private showPdfError(message: string | null, status: number, retryAfterSeconds = 0): void {
    const throttled = status === 429;
    const detail = throttled
      ? this.retryMessage(retryAfterSeconds)
      : (message || this.t('REPORTS.VIEWER.LOAD_FAILED', 'Could not load the PDF report. Please try again.'));
    this.messageService.add({
      severity: throttled ? 'warn' : 'error',
      summary: throttled
        ? this.t('COMMON.HTTP.TOO_MANY_REQUESTS_TITLE', 'Too many requests')
        : this.t('COMMON.ERROR', 'Error'),
      detail,
      life: 10000
    });
  }

  private retryMessage(seconds: number): string {
    return seconds
      ? this.t('COMMON.HTTP.TOO_MANY_REQUESTS_RETRY',
          'Too many requests were sent in a short time. Please try again in {{seconds}} seconds.')
          .replace('{{seconds}}', String(seconds))
      : this.t('COMMON.HTTP.TOO_MANY_REQUESTS',
          'Too many requests were sent in a short time. Please wait a minute and try again.');
  }

  /** translate() returns the key itself when it is missing, so fall back explicitly. */
  private t(key: string, fallback: string): string {
    const translated = this.translationService.translate(key);
    return translated && translated !== key ? translated : fallback;
  }
}
