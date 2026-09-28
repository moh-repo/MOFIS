import { Component, NgModule } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject, of } from 'rxjs';
import { SharedModule } from './shared.module';
import { TranslationService } from '../service/translation.service';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';

/**
 * LocaleDatePipe and LocaleNumberPipe have two dependencies that are easy to
 * miss because neither is visible to the template compiler:
 *
 *   1. They inject DatePipe/DecimalPipe, which CommonModule exports but does
 *      not provide — SharedModule provides them.
 *   2. They format Arabic as 'ar-EG', whose locale data Angular does not ship
 *      by default — AppModule registers it.
 *
 * Both previously failed only at runtime. These tests stand in for the
 * consuming feature modules.
 */
@Component({
    template: `<span>{{ 1234.5 | localeNumber:'1.0-2' }}</span><span>{{ today | localeDate }}</span>`,
    standalone: false
})
class LocalePipeHostComponent {
  today = new Date(2026, 0, 15);
}

@NgModule({
  declarations: [LocalePipeHostComponent],
  imports: [SharedModule]
})
class LocalePipeHostModule { }

function configure(language: 'en' | 'ar') {
  const translationStub = {
    initialized$: of(true),
    currentLanguage$: new BehaviorSubject<string>(language).asObservable(),
    getCurrentLanguage: () => language,
    isRTL: () => language === 'ar',
    translate: (key: string) => key
  };

  return TestBed.configureTestingModule({
    imports: [LocalePipeHostModule],
    providers: [{ provide: TranslationService, useValue: translationStub }, provideHttpClient(withInterceptorsFromDi()), provideHttpClientTesting()]
}).compileComponents();
}

describe('SharedModule locale pipes', () => {

  it('renders in English without a missing-provider error', async () => {
    await configure('en');
    const fixture = TestBed.createComponent(LocalePipeHostComponent);

    expect(() => fixture.detectChanges()).not.toThrow();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('1,234.5');
    expect(text.trim().length).toBeGreaterThan('1,234.5'.length);
  });

  // Regression: 'ar-EG' locale data must be registered, or every Arabic render
  // throws NG0701 and takes the whole view down with it.
  it('renders in Arabic without missing locale data', async () => {
    await configure('ar');
    const fixture = TestBed.createComponent(LocalePipeHostComponent);

    expect(() => fixture.detectChanges()).not.toThrow();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text.trim().length).toBeGreaterThan(0);
  });
});
