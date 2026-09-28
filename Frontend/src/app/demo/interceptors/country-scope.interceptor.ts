import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpParams, HttpRequest } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CountrySelectionService } from '../service/country-selection.service';

const HEADER_SELECTED_COUNTRY = 'X-Country-Id';

const EXCLUDE_URL_PATTERNS: RegExp[] = [
  /\/student-portal\//i,
  /\/ums\//i,
  /\/lookup-tables\//i,
  /\/api\/v1\/lookup(?:\/|$)/i,
  /\/api\/v1\/ref-data\//i,
  /\/sys-settings/i,
  /\/report-settings/i,
  /\/approval-configs/i,
  /\/assets\//i,
  /\/alerts\/stream/i
];

@Injectable()
export class CountryScopeInterceptor implements HttpInterceptor {

  constructor(private countrySelectionService: CountrySelectionService) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (req.method !== 'GET') {
      return next.handle(req);
    }

    const url = req.url;
    if (!url.includes('/mohe/api/')) {
      return next.handle(req);
    }

    if (EXCLUDE_URL_PATTERNS.some(pattern => pattern.test(url))) {
      return next.handle(req);
    }

    if (this.hasCountryParam(req.params) || this.hasCountryParamInUrl(url)) {
      return next.handle(req);
    }

    const selectedId = this.countrySelectionService.selectedCountryId;
    if (selectedId == null) {
      return next.handle(req);
    }

    return next.handle(req.clone({
      setHeaders: { [HEADER_SELECTED_COUNTRY]: String(selectedId) }
    }));
  }

  private hasCountryParam(params: HttpParams): boolean {
    return params.has('countryId') || params.has('countryIds');
  }

  private hasCountryParamInUrl(url: string): boolean {
    return /[?&]countryId=/i.test(url) || /[?&]countryIds=/i.test(url);
  }
}
