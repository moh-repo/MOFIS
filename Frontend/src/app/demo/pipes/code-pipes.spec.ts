import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { CodeIconPipe, CodeLabelPipe, CodeSeverityPipe } from './code-pipes';
import { CodeListService, EnumOption } from '../service/code-list.service';
import { TranslationService } from '../service/translation.service';

@Component({
    template: `
        <span id="label">{{ 'HIGH' | codeLabel:'alert-severity':lang }}</span>
        <span id="missing">{{ 'GONE' | codeLabel:'alert-severity':lang }}</span>
        <span id="severity">{{ 'HIGH' | codeSeverity:'alert-severity' }}</span>
        <span id="severity-bad">{{ 'ODD' | codeSeverity:'alert-severity' }}</span>
        <i id="icon" class="pi {{ 'HIGH' | codeIcon:'alert-severity' }}"></i>
        <i id="icon-none" class="pi {{ 'ODD' | codeIcon:'alert-severity' }}"></i>
    `,
    imports: [CodeLabelPipe, CodeSeverityPipe, CodeIconPipe]
})
class HostComponent {
    lang = 'en';
}

/**
 * The three pipes are pure views over CodeListService: a name in the bound
 * language, a PrimeNG severity (never an invalid one), a PrimeIcons name.
 */
describe('code-list pipes', () => {
    let fixture: ComponentFixture<HostComponent>;
    let language$: BehaviorSubject<string>;

    const row = (value: string, patch: Partial<EnumOption> = {}): EnumOption => ({
        label: `${value}-ar`, value, labelAr: `${value}-ar`, labelEn: `${value}-en`,
        sortOrder: 0, uiSeverity: null, icon: null, isSystem: false, active: true, extra: null, ...patch
    });

    const text = (id: string) => (fixture.nativeElement.querySelector(`#${id}`) as HTMLElement).textContent!.trim();
    const cls = (id: string) => (fixture.nativeElement.querySelector(`#${id}`) as HTMLElement).className.trim();

    beforeEach(async () => {
        language$ = new BehaviorSubject<string>('en');
        const translation = { currentLanguage$: language$.asObservable(), getCurrentLanguage: () => language$.value };
        // Only HttpClient-free members are used: store() fills the map directly.
        const service = new CodeListService(null as never, null as never, translation as never);
        service.store({
            'alert-severity': [
                row('HIGH', { uiSeverity: 'warn', icon: 'pi-exclamation-triangle' }),
                row('ODD', { uiSeverity: 'warning' as never })
            ]
        });

        await TestBed.configureTestingModule({
            imports: [HostComponent],
            providers: [
                { provide: TranslationService, useValue: translation },
                { provide: CodeListService, useValue: service }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    });

    it('labels in the bound language and re-evaluates when it changes', () => {
        expect(text('label')).toBe('HIGH-en');
        fixture.componentInstance.lang = 'ar';
        fixture.detectChanges();
        expect(text('label')).toBe('HIGH-ar');
    });

    it('shows an unknown code as itself', () => {
        expect(text('missing')).toBe('GONE');
    });

    it('returns a PrimeNG severity, `secondary` for anything else', () => {
        expect(text('severity')).toBe('warn');
        expect(text('severity-bad')).toBe('secondary');
    });

    it('returns the PrimeIcons name, or nothing', () => {
        expect(cls('icon')).toBe('pi pi-exclamation-triangle');
        expect(cls('icon-none')).toBe('pi');
    });
});
