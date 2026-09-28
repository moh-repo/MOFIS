import { HttpParams } from '@angular/common/http';

/** Spring `Page<T>` JSON from UMS list endpoints. */
export interface UmsPageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/** Query string for paginated UMS list APIs — omits blank strings; keeps booleans including false. */
export function buildUmsListParams(
  entries: Record<string, string | number | boolean | null | undefined>
): HttpParams {
  let p = new HttpParams();
  for (const [key, raw] of Object.entries(entries)) {
    if (raw === undefined || raw === null) {
      continue;
    }
    if (typeof raw === 'string') {
      const t = raw.trim();
      if (t === '') {
        continue;
      }
      p = p.set(key, t);
      continue;
    }
    if (typeof raw === 'boolean') {
      p = p.set(key, String(raw));
      continue;
    }
    p = p.set(key, String(raw));
  }
  return p;
}
