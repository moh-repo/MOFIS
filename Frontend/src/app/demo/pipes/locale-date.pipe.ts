import { Pipe, PipeTransform, OnDestroy, ChangeDetectorRef, NgZone } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Subscription, combineLatest } from 'rxjs';
import { TranslationService } from '../service/translation.service';

const LOCALE_MAP: Record<string, string> = {
  en: 'en-US',
  ar: 'ar-EG'
};

/**
 * Locale-aware date pipe. Formats dates using the current app language (en/ar).
 * Use: {{ value | localeDate:'mediumDate' }} or {{ value | localeDate:'short':'':'UTC' }}
 */
@Pipe({
  name: 'localeDate',
  pure: false,
  standalone: true
})
export class LocaleDatePipe implements PipeTransform, OnDestroy {
  private sub?: Subscription;
  private lastLang = '';

  constructor(
    private translation: TranslationService,
    private datePipe: DatePipe,
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

  transform(value: Date | string | number | null | undefined, format?: string, timezone?: string): string | null {
    const locale = LOCALE_MAP[this.translation.getCurrentLanguage() || 'en'] || 'en-US';
    return this.datePipe.transform(value, format || 'mediumDate', timezone || undefined, locale);
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }
}
