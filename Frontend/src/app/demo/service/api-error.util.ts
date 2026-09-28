import { HttpErrorResponse } from '@angular/common/http';

/**
 * The backend reports failures in more than one shape:
 *
 *   { message, details, fieldErrors }  — GlobalExceptionHandler (ErrorDetails)
 *   { error: "..." }                   — controllers that catch broadly and
 *                                        return ResponseEntity.badRequest(),
 *                                        e.g. StudentFinanceController,
 *                                        StudyUniversityFeeController,
 *                                        StudentExtensionController,
 *                                        StudentInfoRequestController,
 *                                        StudentTravelTicketController
 *
 * Reading only `message` silently drops the second shape and leaves the user
 * with a generic "request failed" for what is usually an actionable business
 * rule ("There is already a pending approval request for this entity").
 */
export function extractApiErrorMessage(error: unknown): string | null {
  const body = (error as HttpErrorResponse)?.error ?? error;

  if (typeof body === 'string' && body.trim()) {
    return body.trim();
  }

  if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>;

    // Field-level validation errors are the most specific thing available.
    const fieldErrors = record['fieldErrors'];
    if (Array.isArray(fieldErrors) && fieldErrors.length) {
      const lines = fieldErrors
        .map(fe => {
          const entry = fe as { field?: string; message?: string };
          return entry?.field ? `${entry.field}: ${entry.message ?? ''}`.trim() : (entry?.message ?? '');
        })
        .filter(line => line.length);
      if (lines.length) {
        return lines.join('\n');
      }
    }

    for (const key of ['message', 'error', 'detail', 'details']) {
      const value = record[key];
      if (typeof value === 'string' && value.trim()) {
        return value.trim();
      }
    }
  }

  /*
   * Deliberately not falling back to HttpErrorResponse.message: Angular fills it
   * with "Http failure response for <url>: 400 Bad Request", which is worse for
   * a user than the caller's own translated message. Only a plain Error's
   * message is worth surfacing.
   */
  if (error instanceof HttpErrorResponse) {
    return null;
  }

  const fallback = (error as { message?: string })?.message;
  return typeof fallback === 'string' && fallback.trim() ? fallback.trim() : null;
}

/** What a failed request says about itself, once its body has been read. */
export interface ApiErrorInfo {
  status: number;
  /** `ErrorDetails.code` — today the report codes (REPORT_EXPORT_ROW_CAP, …) and RATE_LIMITED. */
  code: string | null;
  /** The server's message, already in the caller's language. */
  message: string | null;
  /** From `Retry-After`, for a throttled request. */
  retryAfterSeconds: number;
}

/**
 * Reads a failed request's body, including the ones that arrive as a `Blob`.
 *
 * A download asks for `responseType: 'blob'`, so Angular hands back the error body
 * as a Blob too — and `error.error.message` is then undefined, which is why a
 * refused export ("this export would contain 80,000 rows") reached the user as
 * "could not load the PDF report" (review item P7). Reading the blob is
 * asynchronous, hence the promise.
 */
export async function readApiError(error: unknown): Promise<ApiErrorInfo> {
  const response = error as HttpErrorResponse;
  const status = typeof response?.status === 'number' ? response.status : 0;
  const retryAfter = Number(response?.headers?.get?.('Retry-After'));
  const info: ApiErrorInfo = {
    status,
    code: null,
    message: null,
    retryAfterSeconds: Number.isFinite(retryAfter) && retryAfter > 0 ? Math.ceil(retryAfter) : 0
  };

  let body: unknown = response?.error ?? error;
  if (body instanceof Blob) {
    try {
      const text = await body.text();
      body = text.trim().startsWith('{') ? JSON.parse(text) : text;
    } catch {
      body = null; // not JSON, or unreadable: fall back to the caller's own wording
    }
  }

  if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>;
    const code = record['code'];
    if (typeof code === 'string' && code.trim()) {
      info.code = code.trim();
    }
    const seconds = Number(record['retryAfterSeconds']);
    if (!info.retryAfterSeconds && Number.isFinite(seconds) && seconds > 0) {
      info.retryAfterSeconds = Math.ceil(seconds);
    }
  }

  info.message = extractApiErrorMessage(body instanceof Blob ? null : { error: body });
  return info;
}
