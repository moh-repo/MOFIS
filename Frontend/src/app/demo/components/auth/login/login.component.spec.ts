import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MessageService } from 'primeng/api';
import { NzI18nService } from 'ng-zorro-antd/i18n';
import { LoginComponent } from './login.component';
import { LoginModule } from './login.module';
import { AuthService } from '../../../service/auth.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';
import { CountrySelectionService } from '../../../service/country-selection.service';
import { SystemSettingsService } from '../../../service/system-settings.service';

/**
 * The login page after the August review.
 *
 * <p>Two findings on page 1 of that review are what these tests hold in place.
 * The attempt counter lived in `localStorage`, so a second browser or one
 * deleted key removed it — and the component skipped the request entirely while
 * it believed the account was locked, which meant the only real check never ran.
 * The security code was drawn and marked here as well, in a field beside the
 * canvas. Both decisions belong to the server now, and these tests fail if
 * either comes back.
 */
describe('LoginComponent', () => {

    let fixture: ComponentFixture<LoginComponent>;
    let component: LoginComponent;
    let umsAuth: jasmine.SpyObj<UmsAuthService>;
    let messages: MessageService;

    const captchaResponse = { id: 'challenge-1', imageUrl: 'blob:fake-image' };

    /** A 401 shaped the way the server now answers a refused sign-in. */
    const refusal = (body: any) => throwError(() => ({ status: 401, error: body }));

    beforeEach(async () => {
        umsAuth = jasmine.createSpyObj<UmsAuthService>('UmsAuthService',
            ['login', 'captcha'], { currentUserValue: null, countryIds: [] });
        umsAuth.captcha.and.returnValue(of(captchaResponse));

        await TestBed.configureTestingModule({
            imports: [LoginModule],
            providers: [
                MessageService,
                { provide: UmsAuthService, useValue: umsAuth },
                { provide: AuthService, useValue: { currentUserValue: null, setUserFromUmsResponse: () => {} } },
                { provide: LookupService, useValue: { getStudyCountriesForLogin: () => of([]) } },
                {
                    provide: CountrySelectionService,
                    useValue: { selectedCountryId: null, setSelectedCountryId: () => {}, syncWithAllowedCountryIds: () => {} }
                },
                { provide: SystemSettingsService, useValue: { getLogoUrl: () => 'logo.png' } },
                { provide: NzI18nService, useValue: { setLocale: () => {} } },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'),
                        isRTL: () => false,
                        isReady: () => true,
                        translate: (k: string) => k,
                        getCurrentLanguage: () => 'en',
                        getAvailableLanguages: () => [],
                        switchLanguage: () => {}
                    }
                },
                { provide: ActivatedRoute, useValue: { snapshot: { queryParams: {} } } }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(LoginComponent);
        component = fixture.componentInstance;
        // The component declares its own MessageService, so the one the toast
        // actually goes to is the component injector's, not the module's.
        messages = fixture.debugElement.injector.get(MessageService);
        spyOn(URL, 'revokeObjectURL');
        fixture.detectChanges();
    });

    /** Fills the form the way a person would and submits. */
    const signIn = (username = 'bob', password = 'secret', code = 'AB12C') => {
        component.loginForm.patchValue({ username, password });
        component.userCaptchaInput = code;
        component.onSubmit();
    };

    it('asks the server for the security code instead of drawing one', () => {
        expect(umsAuth.captcha).toHaveBeenCalled();
        expect(component.captchaId).toBe('challenge-1');
        expect(component.captchaImageUrl).toBe('blob:fake-image');
        // Nothing here knows the answer — there is no field that could hold it.
        expect(Object.keys(component)).not.toContain('captchaText');
    });

    it('never writes the attempt count to storage, and clears what older builds left', () => {
        localStorage.setItem('loginAttempts', '{"admin":{"count":3}}');
        const written: string[] = [];
        spyOn(localStorage, 'setItem').and.callFake((key: string) => { written.push(key); });

        component.ngOnInit();
        umsAuth.login.and.returnValue(refusal({ error: 'Invalid username or password', remainingAttempts: 2 }));
        signIn();

        expect(written).withContext('nothing about attempts is stored').not.toContain('loginAttempts');
        expect(localStorage.getItem('loginAttempts')).toBeNull();
    });

    it('shows the count the server sent, not one of its own', () => {
        umsAuth.login.and.returnValue(refusal({ error: 'Invalid username or password', remainingAttempts: 2 }));

        signIn();

        expect(component.remainingAttempts).toBe(2);
        expect(component.isLocked).toBeFalse();
    });

    it('reports a timed lock with the server\'s deadline', () => {
        const until = new Date(Date.now() + 15 * 60_000).toISOString();
        umsAuth.login.and.returnValue(refusal({
            error: 'Too many failed attempts.', remainingAttempts: 0, locked: true, lockedUntil: until
        }));

        signIn();

        expect(component.isLocked).toBeTrue();
        expect(component.lockedIndefinitely).toBeFalse();
        expect(component.lockedMinutesRemaining).toBeGreaterThan(13);
        expect(component.lockedMinutesRemaining).toBeLessThanOrEqual(16);
    });

    it('reports the last rung as needing an administrator', () => {
        umsAuth.login.and.returnValue(refusal({
            error: 'Account is locked.', remainingAttempts: 0, locked: true, lockedIndefinitely: true, lockedUntil: null
        }));

        signIn();

        expect(component.isLocked).toBeTrue();
        expect(component.lockedIndefinitely).toBeTrue();
    });

    it('still sends the request when the account is locked — the decision is not this page\'s', () => {
        // The old component returned early while it believed the user was
        // locked, so the server never heard about the attempt at all.
        umsAuth.login.and.returnValue(refusal({
            error: 'Too many failed attempts.', remainingAttempts: 0, locked: true,
            lockedUntil: new Date(Date.now() + 60_000).toISOString()
        }));
        signIn();
        expect(component.isLocked).toBeTrue();

        signIn();

        expect(umsAuth.login).toHaveBeenCalledTimes(2);
    });

    it('fetches a fresh challenge after a refusal, because the last one is spent', () => {
        umsAuth.login.and.returnValue(refusal({ error: 'Invalid username or password', remainingAttempts: 2 }));

        signIn();

        expect(umsAuth.captcha).toHaveBeenCalledTimes(2);
        expect(component.userCaptchaInput).toBe('');
    });

    it('does not say the security code is required after the server refused the sign-in (L5)', () => {
        umsAuth.login.and.returnValue(refusal({ error: 'Invalid username or password', remainingAttempts: 2 }));

        signIn();
        fixture.detectChanges();

        const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
        expect(text).not.toContain('AUTH.CAPTCHA_REQUIRED');
        expect(component.lastErrorMessage).toBe('Invalid username or password');

        // Submitting the empty box of the new challenge still says so.
        signIn('bob', 'secret', '');
        fixture.detectChanges();
        expect((fixture.nativeElement as HTMLElement).textContent).toContain('AUTH.CAPTCHA_REQUIRED');
    });

    it('sends the challenge id with the credentials', () => {
        umsAuth.login.and.returnValue(refusal({ error: 'Invalid username or password' }));

        signIn('bob', 'secret', 'ab12c');

        expect(umsAuth.login).toHaveBeenCalledWith('bob', 'secret', 'challenge-1', 'ab12c');
    });

    it('does not submit an empty security code, and does not judge a filled one', () => {
        umsAuth.login.and.returnValue(refusal({ error: 'Invalid username or password' }));
        spyOn(messages, 'add');

        signIn('bob', 'secret', '   ');
        expect(umsAuth.login).not.toHaveBeenCalled();
        expect(messages.add).toHaveBeenCalled();

        // Anything non-empty goes to the server: whether it is right is not a
        // question this page is able to answer any more.
        signIn('bob', 'secret', 'zzzzz');
        expect(umsAuth.login).toHaveBeenCalledTimes(1);
    });

    it('drops the numbers when the username changes — they were about the other name', () => {
        umsAuth.login.and.returnValue(refusal({ error: 'Invalid username or password', remainingAttempts: 1 }));
        signIn();
        expect(component.remainingAttempts).toBe(1);

        component.onUsernameChange();

        expect(component.remainingAttempts).toBe(0);
        expect(component.isLocked).toBeFalse();
    });

    it('leaves the banner alone when the reply carries no numbers at all', () => {
        // A proxy error page or an offline server: better to say nothing than to
        // invent a count, which is what the storage counter amounted to.
        umsAuth.login.and.returnValue(refusal({ error: 'Invalid username or password', remainingAttempts: 2 }));
        signIn();
        expect(component.remainingAttempts).toBe(2);

        umsAuth.login.and.returnValue(throwError(() => ({ status: 0, error: null })));
        signIn();

        expect(component.remainingAttempts).toBe(2);
    });

    it('counts in the forms Arabic actually has', () => {
        // The string the review photographed was "لديك 2 محاولة(ات) متبقية":
        // one template with a bracketed suffix standing in for a plural.
        const keyFor = (n: number) => {
            component.remainingAttempts = n;
            return component.attemptsRemainingMessage;
        };

        expect(keyFor(1)).toBe('AUTH.ATTEMPTS_REMAINING_ONE');
        expect(keyFor(2)).toBe('AUTH.ATTEMPTS_REMAINING_TWO');
        expect(keyFor(3)).toBe('AUTH.ATTEMPTS_REMAINING_FEW');
        expect(keyFor(10)).toBe('AUTH.ATTEMPTS_REMAINING_FEW');
        expect(keyFor(11)).toBe('AUTH.ATTEMPTS_REMAINING_MANY');
    });
});
