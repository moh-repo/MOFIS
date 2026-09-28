import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { NZ_I18N, NzI18nService, ar_EG, en_US } from 'ng-zorro-antd/i18n';
import { AppModule, nzI18nFactory } from './app.module';
import { TranslationService } from './demo/service/translation.service';

/**
 * Builds the real root injector. Unit specs never do, which is how a provider
 * cycle (NZ_I18N factory -> TranslationService -> NzI18nService -> NZ_I18N)
 * reached the browser as NG0200 on 28 Aug 2026 with every spec green.
 */
describe('AppModule', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppModule],
      // The APP_INITIALIZER fetches the locale files; a testing backend leaves
      // those requests pending instead of hitting the network.
      providers: [provideHttpClientTesting()],
    });
  });

  it('resolves TranslationService and the ng-zorro locale without a provider cycle', () => {
    const service = TestBed.inject(TranslationService);
    const locale = TestBed.inject(NZ_I18N);
    const nzI18n = TestBed.inject(NzI18nService);
    expect(service).toBeTruthy();
    expect(locale).toBeTruthy();
    expect(nzI18n).toBeTruthy();
  });

  it('switches the ng-zorro locale through NzI18nService when the language changes', () => {
    const service = TestBed.inject(TranslationService);
    const nzI18n = TestBed.inject(NzI18nService);
    const setLocale = spyOn(nzI18n, 'setLocale').and.callThrough();
    service.setLanguage('ar');
    expect(setLocale).toHaveBeenCalledWith(ar_EG);
    service.setLanguage('en');
    expect(setLocale).toHaveBeenCalledWith(en_US);
  });
});

describe('nzI18nFactory', () => {
  afterEach(() => {
    try {
      localStorage.removeItem('selectedLanguage');
    } catch {
      // storage unavailable in this browser profile
    }
  });

  it('is English when nothing is saved and Arabic when "ar" is saved', () => {
    expect(nzI18nFactory()).toBe(en_US);
    localStorage.setItem('selectedLanguage', 'ar');
    expect(nzI18nFactory()).toBe(ar_EG);
  });
});
