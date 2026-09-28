import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, RouterModule } from '@angular/router';
import { of, throwError } from 'rxjs';
import { StudentPortalLoginComponent } from './login.component';
import { StudentPortalModule } from '../student-portal.module';
import { StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';
import { NZ_ICONS } from 'ng-zorro-antd/icon';
import {
    CarOutline, EyeInvisibleOutline, EyeOutline, FileTextOutline, LockOutline, LoginOutline,
    MailOutline, MessageOutline, QuestionCircleOutline, ReadOutline, ReloadOutline, SafetyOutline,
    UserOutline
} from '@ant-design/icons-angular/icons';

/**
 * The sign-in security code, from the browser's side.
 *
 * <p>The server refuses a login that does not carry a solved challenge, and an id
 * is spent the moment it is checked — right answer or wrong. So every outcome
 * that reaches the server has to leave a fresh challenge on screen. The one worth
 * a test of its own is the two-step sign-in: the server answers the first call
 * with "now give me a code", and if the page does not fetch a new image at that
 * point the student is left holding a spent id and the second call is refused for
 * a reason the screen is not showing them.
 */
describe('StudentPortalLoginComponent', () => {

    let fixture: ComponentFixture<StudentPortalLoginComponent>;
    let component: StudentPortalLoginComponent;
    let captcha: jasmine.Spy;
    let login: jasmine.Spy;
    let forgotPassword: jasmine.Spy;
    let loginCountries: jasmine.Spy;
    let navigate: jasmine.Spy;
    let issued: number;

    /** A distinct id and object URL per call, so "was it refreshed" is answerable. */
    function nextChallenge() {
        issued += 1;
        return of({ id: `challenge-${issued}`, imageUrl: `blob:image-${issued}` });
    }

    beforeEach(async () => {
        issued = 0;
        captcha = jasmine.createSpy('captcha').and.callFake(nextChallenge);
        login = jasmine.createSpy('login').and.returnValue(of({ status: 'OK', token: 't', studentId: 1, studentName: 'A', permissions: [] }));
        forgotPassword = jasmine.createSpy('forgotPassword').and.returnValue(of({ message: 'sent' }));
        loginCountries = jasmine.createSpy('loginCountries').and.returnValue(
            of([{ id: 7, studyCountryInName: 'مصر', studyCountryInNameEn: 'Egypt' }]));

        await TestBed.configureTestingModule({
            imports: [StudentPortalModule, RouterModule.forRoot([])],
            providers: [
                // The app registers ng-zorro's icons in AppModule; the portal module alone
                // does not. Every nzType this template names has to be here, the feature
                // list on the left panel included — an unregistered one is not a test
                // failure, only a page of console errors that hides real ones.
                {
                    provide: NZ_ICONS,
                    useValue: [MailOutline, LockOutline, EyeOutline, EyeInvisibleOutline,
                        LoginOutline, QuestionCircleOutline, SafetyOutline, ReloadOutline,
                        ReadOutline, UserOutline, FileTextOutline, MessageOutline, CarOutline]
                },
                {
                    provide: StudentPortalApiService,
                    useValue: {
                        isLoggedIn: () => false,
                        setToken: () => {},
                        loginCountries,
                        captcha,
                        login,
                        forgotPassword
                    }
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en',
                        switchLanguage: () => {}
                    }
                }
            ]
        }).compileComponents();

        // A successful sign-in navigates to the dashboard. Left real, the router
        // renders the portal shell — whose menu icons are registered in AppModule,
        // not here — and the run fills with icon errors that have nothing to do
        // with what is being tested.
        navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);

        fixture = TestBed.createComponent(StudentPortalLoginComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    /** The single country in the stub list is chosen for the student; fill in the rest. */
    function fillTheForm(): void {
        component.username = 'Student@Example.Test';
        component.password = 'a-password';
        component.captcha.answer = 'ABCDE';
    }

    it('picks the only country for the student, and leaves the field usable', () => {
        expect(component.countries.length).toBe(1);
        expect(component.studyCountryInId).toBe(7);
        expect(component.countriesUnavailable).toBeFalse();
    });

    it('offers a retry when the country list comes back empty', () => {
        // What the login page actually did: the list is read from a table under
        // Row-Level Security and an anonymous caller had no scope, so a 200 with
        // an empty body disabled the selector — a required field — and there was
        // no way to ask again short of reloading the page.
        loginCountries.and.returnValue(of([]));
        component.loadCountries();

        expect(component.countriesUnavailable).toBeTrue();
        expect(component.studyCountryInId).toBeNull();

        loginCountries.and.returnValue(of([{ id: 7, studyCountryInNameEn: 'Egypt' }]));
        component.loadCountries();

        expect(component.countriesUnavailable).toBeFalse();
        expect(component.studyCountryInId).toBe(7);
    });

    it('offers the same retry when the request itself fails', () => {
        loginCountries.and.returnValue(throwError(() => new Error('offline')));

        component.loadCountries();

        expect(component.countriesUnavailable).toBeTrue();
        expect(component.error).toBe('STUDENT_PORTAL.COUNTRY_LOAD_FAILED');
    });

    it('asks for a challenge as soon as the page opens', () => {
        expect(captcha).toHaveBeenCalledTimes(1);
        expect(component.captcha.id).toBe('challenge-1');
        expect(component.captcha.imageUrl).toBe('blob:image-1');
    });

    it('sends the challenge id and the typed answer with the credentials', () => {
        fillTheForm();

        component.onSubmit();

        expect(login).toHaveBeenCalledWith('student@example.test', 'a-password', 7, 'challenge-1', 'ABCDE', {});
        expect(navigate).toHaveBeenCalledWith(['/student-portal/dashboard']);
    });

    it('will not post a sign-in with the answer box empty', () => {
        fillTheForm();
        component.captcha.answer = '   ';

        component.onSubmit();

        expect(login).not.toHaveBeenCalled();
        expect(component.error).toBe('STUDENT_PORTAL.CAPTCHA.REQUIRED');
    });

    it('fetches a new challenge when the server asks for a second factor', () => {
        login.and.returnValue(of({ status: 'TOTP_REQUIRED', message: 'Enter the code from your app.' }));
        fillTheForm();

        component.onSubmit();

        // The id that got this far is spent, and the call carrying the authenticator
        // code is challenged in its own right — the server checks the password
        // before it reads the code, so that call is as much a guessing surface.
        expect(component.challenge).toBe('TOTP_REQUIRED');
        expect(captcha).toHaveBeenCalledTimes(2);
        expect(component.captcha.id).toBe('challenge-2');
        expect(component.captcha.answer).toBe('');
    });

    it('sends the second factor with the fresh challenge, not the spent one', () => {
        login.and.returnValue(of({ status: 'VERIFY_EMAIL', message: 'Check your inbox.' }));
        fillTheForm();
        component.onSubmit();

        login.calls.reset();
        login.and.returnValue(of({ status: 'OK', token: 't', studentId: 1, studentName: 'A', permissions: [] }));
        component.challengeCode = '123456';
        component.captcha.answer = 'FGHJK';
        component.onSubmit();

        expect(login).toHaveBeenCalledWith('student@example.test', 'a-password', 7, 'challenge-2', 'FGHJK',
            { emailCode: '123456' });
    });

    it('fetches a new challenge after a refused sign-in', () => {
        login.and.returnValue(throwError(() => ({ error: { error: 'Invalid email or password' } })));
        fillTheForm();

        component.onSubmit();

        // Spent on the way in, whether the answer was right or wrong; leaving the
        // old image up would make the next attempt fail on the code instead.
        expect(captcha).toHaveBeenCalledTimes(2);
        expect(component.captcha.id).toBe('challenge-2');
        expect(component.error).toBe('Invalid email or password');
    });

    it('gives the forgot-password dialog a challenge of its own', () => {
        component.openForgot();

        // Its own, not the one behind it: both can be on screen and an id is good once.
        expect(captcha).toHaveBeenCalledTimes(2);
        expect(component.forgotCaptcha.id).toBe('challenge-2');
        expect(component.captcha.id).toBe('challenge-1');

        component.forgotUsername = 'student@example.test';
        component.forgotCaptcha.answer = 'LMNPQ';
        component.requestOtp();

        expect(forgotPassword).toHaveBeenCalledWith('student@example.test', 'challenge-2', 'LMNPQ');
    });

    it('will not ask for a reset code with the answer box empty', () => {
        component.openForgot();
        component.forgotUsername = 'student@example.test';

        component.requestOtp();

        expect(forgotPassword).not.toHaveBeenCalled();
        expect(component.forgotError).toBe('STUDENT_PORTAL.CAPTCHA.REQUIRED');
    });

    it('says so, rather than failing silently, when the image will not load', () => {
        captcha.and.returnValue(throwError(() => new Error('offline')));

        component.refreshCaptcha();

        expect(component.captcha.id).toBe('');
        expect(component.captcha.imageUrl).toBeNull();
        expect(component.error).toBe('STUDENT_PORTAL.CAPTCHA.LOAD_FAILED');
    });

    it('revokes the images it created when the page goes away', () => {
        const revoke = spyOn(URL, 'revokeObjectURL');
        component.openForgot();

        fixture.destroy();

        // Object URLs are held by the document until revoked, so the PNGs would
        // otherwise sit in memory for the life of the tab.
        expect(revoke).toHaveBeenCalledWith('blob:image-1');
        expect(revoke).toHaveBeenCalledWith('blob:image-2');
    });

    // SEC-001 (D9-a): an account that never chose its password goes to the password form.
    it('opens the password form first when the account must choose its own password', () => {
        login.and.returnValue(of({ status: 'OK', token: 't', studentId: 1, studentName: 'A', permissions: [], mustChangePassword: true }));
        fillTheForm();

        component.onSubmit();

        expect(navigate).toHaveBeenCalledWith(['/student-portal/security'], { queryParams: { changePassword: 'required' } });
        expect(navigate).not.toHaveBeenCalledWith(['/student-portal/dashboard']);
    });
});
