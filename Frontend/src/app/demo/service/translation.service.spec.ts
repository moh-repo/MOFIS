import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PrimeNG } from 'primeng/config';
import { NzI18nService, ar_EG, en_US } from 'ng-zorro-antd/i18n';
import { TranslationService } from './translation.service';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

describe('TranslationService', () => {
  let service: TranslationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
    imports: [],
    providers: [TranslationService, provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()]
});
    service = TestBed.inject(TranslationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return default current language as en', () => {
    expect(service.getCurrentLanguage()).toBe('en');
  });

  // PrimeNG's built-in strings live in its config, not in en.json/ar.json. Before
  // this hook every confirm dialog without explicit labels said Yes/No in Arabic.
  it('switches PrimeNG built-in strings with the language', () => {
    const primeng = TestBed.inject(PrimeNG);

    service.setLanguage('ar');
    expect(primeng.translation.accept).toBe('نعم');
    expect(primeng.translation.reject).toBe('لا');
    expect(primeng.translation.monthNames?.[0]).toBe('يناير');

    service.setLanguage('en');
    expect(primeng.translation.accept).toBe('Yes');
    expect(primeng.translation.reject).toBe('No');
  });

  // switchLanguage used to window.location.reload(); it now switches in place.
  it('switches in place: emits the language, flips body direction and the ng-zorro locale', () => {
    const nzI18n = TestBed.inject(NzI18nService);
    const seen: string[] = [];
    service.currentLanguage$.subscribe(l => seen.push(l));

    service.switchLanguage('ar');

    expect(seen).toEqual(['en', 'ar']);
    expect(service.getCurrentLanguage()).toBe('ar');
    expect(service.isRTL()).toBeTrue();
    expect(document.body.getAttribute('dir')).toBe('rtl');
    expect(document.documentElement.getAttribute('lang')).toBe('ar');
    expect(document.documentElement.classList.contains('rtl')).toBeTrue();
    expect(nzI18n.getLocaleId()).toBe(ar_EG.locale);

    service.switchLanguage('en');

    expect(seen).toEqual(['en', 'ar', 'en']);
    expect(document.body.getAttribute('dir')).toBe('ltr');
    expect(nzI18n.getLocaleId()).toBe(en_US.locale);
  });

  it('ignores an unknown language and a switch to the current one', () => {
    const seen: string[] = [];
    service.currentLanguage$.subscribe(l => seen.push(l));

    service.switchLanguage('fr');
    service.switchLanguage('en');

    expect(seen).toEqual(['en']);
  });

  it('should return available languages', () => {
    const langs = service.getAvailableLanguages();
    expect(langs.length).toBeGreaterThanOrEqual(2);
    expect(langs.map(l => l.code)).toContain('en');
    expect(langs.map(l => l.code)).toContain('ar');
  });

  it('should return key when not initialized (translate)', () => {
    expect(service.translate('COMMON.SAVE')).toBe('COMMON.SAVE');
  });

  it('should report not RTL for default language', () => {
    expect(service.isRTL()).toBe(false);
  });

  it('should report not ready when not initialized', () => {
    expect(service.isReady()).toBe(false);
  });
});
