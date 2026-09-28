import { Pipe, PipeTransform, OnDestroy, ChangeDetectorRef, NgZone } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Subscription, combineLatest } from 'rxjs';
import { TranslationService } from '../service/translation.service';

const LOCALE_MAP: Record<string, string> = {
  en: 'en-US',
  ar: 'ar-EG'
};

/**
 * Locale-aware number pipe. Formats numbers using the current app language (en/ar).
 * Use: {{ value | localeNumber:'1.0-0' }} or {{ value | localeNumber }}
 */
@Pipe({
  name: 'localeNumber',
  pure: false,
  standalone: true
})
export class LocaleNumberPipe implements PipeTransform, OnDestroy {
  private sub?: Subscription;
  private lastLang = '';

  constructor(
    private translation: TranslationService,
    private decimalPipe: DecimalPipe,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {
    this.sub = combineLatest([
      this.translation.initialized$,
      this.translation.currentLanguage$
    ]).subscribe(([, lang]) => {
      if (lang !== this.lastLang) {
        this.lastLang = lang;
        this.ngZone.run(() => this.cdr.markForCheck());
      }
    });
  }

  transform(value: number | string | null | undefined, digitsInfo?: string, locale?: string): string | null {
    const resolvedLocale = locale || LOCALE_MAP[this.translation.getCurrentLanguage() || 'en'] || 'en-US';
    return this.decimalPipe.transform(value, digitsInfo || '1.0-0', resolvedLocale);
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
