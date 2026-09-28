import { Pipe, PipeTransform, OnDestroy, ChangeDetectorRef, NgZone } from '@angular/core';
import { Subscription, combineLatest } from 'rxjs';
import { TranslationService } from '../service/translation.service';

@Pipe({
  name: 'translate',
  pure: false, // Make it impure so it updates when language changes or translations load
  standalone: true
})
export class TranslatePipe implements PipeTransform, OnDestroy {
  private languageSubscription?: Subscription;
  private initializedSubscription?: Subscription;
  private combinedSubscription?: Subscription;
  private cache: Map<string, string> = new Map();
  private initialized: boolean = false;
  private lastLanguage: string = '';

  constructor(
    private translationService: TranslationService,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {
    // Check if service is ready
    this.initialized = this.translationService.isReady();
    this.lastLanguage = this.translationService.getCurrentLanguage();

    // Subscribe to both initialization and language changes
    this.combinedSubscription = combineLatest([
      this.translationService.initialized$,
      this.translationService.currentLanguage$
    ]).subscribe(([isReady, language]) => {
      const wasInitialized = this.initialized;
      this.initialized = isReady;

      // If translations just became ready, clear cache and trigger change detection
      if (isReady && !wasInitialized) {
        this.cache.clear();
        this.ngZone.run(() => {
          this.cdr.markForCheck();
        });
      }

      // If language changed, clear cache and trigger change detection
      if (language !== this.lastLanguage) {
        this.lastLanguage = language;
        this.cache.clear();
        this.ngZone.run(() => {
          this.cdr.markForCheck();
        });
      }
    });
  }

  transform(key: string, params?: { [key: string]: string | number }): string {
    if (!key) {
      return '';
    }

    // Always check if service is ready on each transform (in case it became ready)
    const nowReady = this.translationService.isReady();
    if (nowReady !== this.initialized) {
      this.initialized = nowReady;
      this.cache.clear();
      // Force change detection when service becomes ready
      if (nowReady) {
        this.ngZone.run(() => {
          this.cdr.markForCheck();
        });
      }
    }

    const cacheKey = params ? `${key}|${JSON.stringify(params)}` : key;
    // If service is ready and we have cached value, return it
    if (this.initialized && this.cache.has(cacheKey)) {
      return this.cache.get(cacheKey)!;
    }

    // Get translation from service
    let translation = this.translationService.translate(key);

    // If service is not initialized yet, return key to avoid showing undefined
    if (!this.initialized) {
      return key;
    }

    // Interpolate params: replace {paramName} with value
    if (params && translation) {
      Object.keys(params).forEach(k => {
        translation = translation!.replace(new RegExp(`\\{${k}\\}`, 'g'), String(params[k]));
      });
    }

    // Cache it only if service is initialized and translation is valid (different from key)
    if (translation && translation !== key) {
      this.cache.set(cacheKey, translation);
      return translation;
    }

    // If translation equals key, it means translation not found - return key anyway
    return translation || key;
  }

  ngOnDestroy(): void {
    if (this.combinedSubscription) {
      this.combinedSubscription.unsubscribe();
    }
    if (this.languageSubscription) {
      this.languageSubscription.unsubscribe();
    }
    if (this.initializedSubscription) {
      this.initializedSubscription.unsubscribe();
    }
    this.cache.clear();
  }
}
