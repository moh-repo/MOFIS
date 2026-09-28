import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TranslationService } from '../service/translation.service';

/**
 * Attaches Accept-Language on every API request so backend MessageSource
 * can return localized validation and error messages.
 */
@Injectable()
export class AcceptLanguageInterceptor implements HttpInterceptor {

  constructor(private translationService: TranslationService) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const lang = this.translationService.getCurrentLanguage() || 'en';
    const acceptLanguage = lang.toLowerCase().startsWith('ar') ? 'ar' : 'en';
    if (request.headers.has('Accept-Language')) {
      return next.handle(request);
    }
    return next.handle(request.clone({
      setHeaders: { 'Accept-Language': acceptLanguage }
    }));
  }
}
