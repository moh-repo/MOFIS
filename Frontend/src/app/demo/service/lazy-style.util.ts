/**
 * Loads a global stylesheet that angular.json emits with `inject: false`.
 *
 * Two third-party sheets — ag-grid (used by the two financial-data-form
 * reports) and Quill's snow theme (the notification editor) — were in the
 * initial styles bundle for every page load although only three lazy-loaded
 * screens use them. Their DOM is created by the library at runtime, outside
 * Angular's view encapsulation, so putting them in a component's styleUrls
 * would not style it; they stay global and are fetched on first use instead.
 *
 * Idempotent: a second call for the same sheet is a no-op.
 */
export function ensureGlobalStylesheet(bundleName: string): void {
  if (typeof document === 'undefined') {
    return;
  }
  const id = `lazy-style-${bundleName}`;
  if (document.getElementById(id)) {
    return;
  }
  const link = document.createElement('link');
  link.id = id;
  link.rel = 'stylesheet';
  // Relative, not absolute: the app is served under a context path in production.
  link.href = `${bundleName}.css`;
  document.head.appendChild(link);
}
