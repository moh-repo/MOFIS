import { Provider } from '@angular/core';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './auth.interceptor';
import { AcceptLanguageInterceptor } from './accept-language.interceptor';
import { CountryScopeInterceptor } from './country-scope.interceptor';
import { ErrorInterceptor } from './error.interceptor';

/**
 * The application's HTTP interceptors, in order. Angular runs them outermost first on
 * the way out and **innermost first on the way back**, so an error reaches the last one
 * listed before any other.
 *
 * FE-02: ErrorInterceptor used to be last. It saw every 401 before AuthInterceptor,
 * cleared the token and redirected to sign-in, and AuthInterceptor's silent refresh
 * then found no token to refresh — the 401 → refresh → retry path could never run.
 * ErrorInterceptor is now first (outermost): a 401 reaches it only once AuthInterceptor
 * has tried the refresh and failed. `interceptor-chain.spec.ts` runs this exact list.
 */
export const APP_HTTP_INTERCEPTORS: Provider[] = [
  { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
  { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
  { provide: HTTP_INTERCEPTORS, useClass: AcceptLanguageInterceptor, multi: true },
  { provide: HTTP_INTERCEPTORS, useClass: CountryScopeInterceptor, multi: true },
];
