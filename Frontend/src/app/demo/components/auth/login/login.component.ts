import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../../service/auth.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';
import { CountrySelectionService } from '../../../service/country-selection.service';
import { SystemSettingsService } from '../../../service/system-settings.service';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { StudyCountryIn } from '../../../api/study-country-in';
import { LoginResponse, MfaEnrolment } from '../../../api/ums';
import { SYSTEM_LOGO_FALLBACK } from '../../../service/system-logo';
import { NzI18nService } from 'ng-zorro-antd/i18n';

@Component({
    selector: 'app-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss'],
    providers: [MessageService],
    standalone: false
})
export class LoginComponent implements OnInit, OnDestroy {

    loginForm: FormGroup;
    loading = false;
    submitted = false;
    returnUrl: string;
    error = '';
    
    /**
     * The security code, as the server issued it. The image is a blob URL and
     * the id travels back with the credentials; nothing here knows the answer,
     * which is the whole difference from the canvas this replaced.
     */
    captchaImageUrl: string | null = null;
    captchaId: string = '';
    userCaptchaInput: string = '';
    captchaLoading = false;

    /**
     * Where the sign-in stands, as the last reply from the server said.
     *
     * <p>These used to be kept in `localStorage` and decided here: three tries,
     * fifteen minutes, and the request simply not sent while this file believed
     * the account was locked. A second browser removed the control, and the
     * server was meanwhile running a different rule nobody could see. The count
     * is the server's now and this component only reports it.
     */
    remainingAttempts: number = 0;
    lockedUntil: Date | null = null;
    lockedIndefinitely = false;
    lastErrorMessage: string = '';

    shakeAnimation: boolean = false;
    hidePassword: boolean = true;
    logoUrl: string;

    studyCountries: StudyCountryIn[] = [];
    selectedCountryId: number | null = null;

    /**
     * O-16: where the sign-in stands after the password. `none` is the
     * credentials form; `verify` asks for a code; `enrol` shows the QR code of
     * a new secret and asks for its first code; `codes` shows the recovery
     * codes once, before the sign-in completes.
     */
    mfaStep: 'none' | 'verify' | 'enrol' | 'codes' = 'none';
    mfaToken = '';
    mfaCode = '';
    mfaBusy = false;
    mfaEnrolment: MfaEnrolment | null = null;
    recoveryCodes: string[] = [];
    recoveryCodesCopied = false;
    private completedSignIn: LoginResponse | null = null;

    constructor(
        private formBuilder: FormBuilder,
        private route: ActivatedRoute,
        private router: Router,
        private authService: AuthService,
        private umsAuthService: UmsAuthService,
        private messageService: MessageService,
        private lookupService: LookupService,
        public translationService: TranslationService,
        private countrySelectionService: CountrySelectionService,
        private systemSettingsService: SystemSettingsService,
        private nzI18n: NzI18nService
    ) {
        this.logoUrl = this.systemSettingsService.getLogoUrl();
        // redirect to home if already logged in (UMS or legacy)
        if (this.umsAuthService.currentUserValue || this.authService.currentUserValue) {
            this.router.navigate(['/']);
        }
    }

    /**
     * Shown when no logo has been uploaded in Settings, or the request for it
     * fails. It used to be `logo-dark.svg`, an auto-trace whose first path was
     * an opaque black rectangle over the whole canvas — the black box the
     * August review asked about. `MOHE_Logo.png` is the emblem the ministry
     * actually uploaded, on a transparent background, and it is what the report
     * masthead has always fallen back to.
     */
    onLogoError(): void {
        this.logoUrl = SYSTEM_LOGO_FALLBACK;
    }

    ngOnInit() {
        // S-09: the previous session was ended by the server for an anomaly.
        try {
            const ended = sessionStorage.getItem('mohe.sessionEnded');
            if (ended) {
                sessionStorage.removeItem('mohe.sessionEnded');
                this.messageService.add({
                    severity: 'warn',
                    summary: this.translationService.translate('AUTH.SESSION_ENDED_TITLE'),
                    detail: this.translationService.translate('AUTH.SESSION_ENDED_ANOMALY'),
                    life: 8000
                });
            }
        } catch { /* storage blocked */ }
        this.loginForm = this.formBuilder.group({
            username: ['', [Validators.required, Validators.minLength(3)]],
            password: ['', [Validators.required, Validators.minLength(3)]],
            remember: [false],
            captcha: ['']
        });

        // get return url from route parameters or default to '/'
        this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';

        // The browser's own attempt counter is gone; clear what old builds left
        // behind so nothing reads a stale lock out of storage.
        localStorage.removeItem('loginAttempts');
        // Load countries for country selector (from UMS user privilege assignments)
        this.lookupService.getStudyCountriesForLogin().subscribe({
            next: (list) => { this.studyCountries = list || []; },
            error: () => { this.studyCountries = []; }
        });
        this.selectedCountryId = this.countrySelectionService.selectedCountryId;
        this.loadCaptcha();
    }

    ngOnDestroy(): void {
        this.releaseCaptchaImage();
    }

    onCountryChange(): void {
        this.countrySelectionService.setSelectedCountryId(this.selectedCountryId);
    }

    switchLanguage(code: string): void {
        // Switches in place. The login page sits outside the shell, so it is
        // not rebuilt the way a routed page is (AppLayoutComponent
        // .reinitialiseRoute); it does not need to be, as everything on it is
        // template text and the translate pipe is impure.
        this.translationService.switchLanguage(code);
    }

    // convenience getter for easy access to form fields
    get f() { return this.loginForm.controls; }

    /** Asks the server for a fresh challenge and shows it. */
    loadCaptcha(): void {
        this.captchaLoading = true;
        this.userCaptchaInput = '';
        this.umsAuthService.captcha().subscribe({
            next: ({ id, imageUrl }) => {
                this.releaseCaptchaImage();
                this.captchaId = id;
                this.captchaImageUrl = imageUrl;
                this.captchaLoading = false;
            },
            error: () => {
                // No challenge means no sign-in: say so rather than leave an
                // empty box the user cannot fill.
                this.releaseCaptchaImage();
                this.captchaId = '';
                this.captchaImageUrl = null;
                this.captchaLoading = false;
                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('AUTH.CAPTCHA_ERROR'),
                    detail: this.translationService.translate('AUTH.CAPTCHA_NOT_GENERATED')
                });
            }
        });
    }

    private releaseCaptchaImage(): void {
        if (this.captchaImageUrl) {
            URL.revokeObjectURL(this.captchaImageUrl);
            this.captchaImageUrl = null;
        }
    }

    /**
     * Reads the ladder position out of a refused sign-in.
     *
     * <p>A reply without these fields (an offline server, a proxy error page)
     * leaves the banner as it was rather than inventing a number — the defect
     * this whole change is about was a count that came from nowhere real.
     */
    private applyAttemptStatus(body: any): void {
        if (!body || typeof body !== 'object') {
            return;
        }
        if (typeof body.remainingAttempts === 'number') {
            this.remainingAttempts = body.remainingAttempts;
        }
        this.lockedIndefinitely = body.lockedIndefinitely === true;
        this.lockedUntil = body.lockedUntil ? new Date(body.lockedUntil) : null;
    }

    /** Minutes left on a timed lock, for the message. */
    get lockedMinutesRemaining(): number {
        if (!this.lockedUntil) {
            return 0;
        }
        return Math.max(1, Math.ceil((this.lockedUntil.getTime() - Date.now()) / 60000));
    }

    get isLocked(): boolean {
        return this.lockedIndefinitely || (!!this.lockedUntil && this.lockedUntil.getTime() > Date.now());
    }

    /**
     * "Sign-in failed — N tries left", in a form the language actually has.
     *
     * <p>The old string was `لديك {count} محاولة(ات) متبقية`: a bracketed suffix
     * standing in for a plural, because the translate pipe substitutes and does
     * nothing else. Arabic counts in four shapes — one, two, a few (3–10) and
     * many — so there are four keys and the count picks one. English uses two of
     * them and repeats itself in the rest, which costs nothing and keeps the two
     * files the same shape.
     */
    get attemptsRemainingMessage(): string {
        return this.countedMessage('AUTH.ATTEMPTS_REMAINING', this.remainingAttempts, '{count}');
    }

    get lockedMinutesMessage(): string {
        return this.countedMessage('AUTH.LOCKED_FOR_MINUTES', this.lockedMinutesRemaining, '{minutes}');
    }

    private countedMessage(prefix: string, count: number, placeholder: string): string {
        const form = count === 1 ? 'ONE'
            : count === 2 ? 'TWO'
                : count >= 3 && count <= 10 ? 'FEW'
                    : 'MANY';
        return this.translationService.translate(`${prefix}_${form}`).replace(placeholder, String(count));
    }

    triggerShakeAnimation() {
        this.shakeAnimation = true;
        setTimeout(() => {
            this.shakeAnimation = false;
        }, 500);
    }

    onSubmit() {
        this.submitted = true;

        // Basic form validation
        if (this.loginForm.invalid) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('AUTH.VALIDATION_ERROR'),
                detail: this.translationService.translate('AUTH.VALIDATION_FILL_FIELDS')
            });
            return;
        }

        const username = this.f['username'].value;
        const password = this.f['password'].value;

        // Validate username and password length
        if (username.length < 3) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('AUTH.VALIDATION_ERROR'),
                detail: this.translationService.translate('AUTH.USERNAME_MIN_LENGTH')
            });
            return;
        }

        if (password.length < 3) {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('AUTH.VALIDATION_ERROR'),
                detail: this.translationService.translate('AUTH.PASSWORD_MIN_LENGTH')
            });
            return;
        }

        // The answer is only checked on the server, so all this can say is that
        // the box is empty — anything more would be the check that was not one.
        if (!this.userCaptchaInput || this.userCaptchaInput.trim() === '') {
            this.messageService.add({
                severity: 'error',
                summary: this.translationService.translate('AUTH.CAPTCHA_REQUIRED_TITLE'),
                detail: this.translationService.translate('AUTH.CAPTCHA_REQUIRED_DETAIL')
            });
            return;
        }

        if (!this.captchaId) {
            this.loadCaptcha();
            return;
        }

        this.loading = true;

        // Use UMS login only (POST with JSON body); avoids password in URL and backend GET issues
        this.umsAuthService.login(username, password, this.captchaId, this.userCaptchaInput.trim()).subscribe({
            next: (response) => {
                // O-16: the password was accepted but the sign-in is not done.
                if (response.mfaToken && (response.mfaRequired || response.mfaEnrolmentRequired)) {
                    this.mfaToken = response.mfaToken;
                    this.mfaCode = '';
                    this.loading = false;
                    if (response.mfaEnrolmentRequired) {
                        this.startEnrolment();
                    } else {
                        this.mfaStep = 'verify';
                    }
                    return;
                }
                this.authService.setUserFromUmsResponse(response.user);
                this.finishLogin();
            },
            error: error => {
                // Whatever the server decided — a wrong password, a spent
                // challenge, a lock — this component reports it and asks for a
                // new challenge, because the last one has been consumed.
                this.applyAttemptStatus(error.error);
                this.lastErrorMessage = error.error?.error || error.error?.message
                    || this.translationService.translate('AUTH.LOGIN_INVALID');
                this.triggerShakeAnimation();
                this.loadCaptcha();
                // The new challenge's box is empty because it is new, not because the
                // user left it so: the "security code is required" line waits for the
                // next submit (live QA L5, it stood beside the real message).
                this.submitted = false;

                this.messageService.add({
                    severity: 'error',
                    summary: this.translationService.translate('AUTH.LOGIN_FAILED'),
                    detail: this.lastErrorMessage
                });
                this.loading = false;
            }
        });
    }

    private finishLogin(): void {
        this.remainingAttempts = 0;
        this.lockedUntil = null;
        this.lockedIndefinitely = false;
        this.lastErrorMessage = '';
        this.countrySelectionService.syncWithAllowedCountryIds(this.umsAuthService.countryIds || []);
        this.messageService.add({
            severity: 'success',
            summary: this.translationService.translate('AUTH.LOGIN_SUCCESS'),
            detail: this.translationService.translate('AUTH.WELCOME_BACK_DETAIL')
        });
        this.loading = false;
        this.router.navigateByUrl(this.returnUrl || '/');
    }

    /**
     * Typing a different username clears the last reply's numbers: they were
     * about the name that was in the box, and showing them against another one
     * would be the same fiction the storage counter was.
     */
    onUsernameChange() {
        this.remainingAttempts = 0;
        this.lockedUntil = null;
        this.lockedIndefinitely = false;
        this.lastErrorMessage = '';
    }

    refreshCaptcha() {
        this.loadCaptcha();
    }

    showForgotPasswordInfo() {
        this.messageService.add({
            severity: 'info',
            summary: this.translationService.translate('AUTH.FORGOT_PASSWORD'),
            detail: this.translationService.translate('AUTH.FORGOT_PASSWORD_INFO')
        });
    }

    // ── O-16: the second factor ─────────────────────────────────────────────

    /** A code from the app, or a recovery code, finishes the sign-in. */
    submitMfaCode(): void {
        const code = this.mfaCode.trim();
        if (!code || this.mfaBusy) { return; }
        this.mfaBusy = true;
        this.umsAuthService.mfaVerify(this.mfaToken, code).subscribe({
            next: response => {
                this.mfaBusy = false;
                this.completeMfaSignIn(response);
            },
            error: err => this.mfaFailed(err)
        });
    }

    /** A required account that has not enrolled: fetch a secret and show it. */
    startEnrolment(): void {
        this.mfaBusy = true;
        this.mfaStep = 'enrol';
        this.umsAuthService.mfaEnrolStart(this.mfaToken).subscribe({
            next: enrolment => {
                this.mfaEnrolment = enrolment;
                this.mfaBusy = false;
            },
            error: err => this.mfaFailed(err)
        });
    }

    /** The first code from the app proves the secret landed; the reply is the sign-in plus the recovery codes. */
    confirmEnrolment(): void {
        const code = this.mfaCode.trim();
        if (!code || this.mfaBusy) { return; }
        this.mfaBusy = true;
        this.umsAuthService.mfaEnrolConfirm(code, this.mfaToken).subscribe({
            next: response => {
                this.mfaBusy = false;
                this.recoveryCodes = response.recoveryCodes || [];
                this.recoveryCodesCopied = false;
                this.completedSignIn = response;
                this.mfaStep = 'codes';
            },
            error: err => this.mfaFailed(err)
        });
    }

    /** After the recovery codes have been shown once. */
    continueAfterRecoveryCodes(): void {
        if (this.completedSignIn) {
            this.completeMfaSignIn(this.completedSignIn);
        }
    }

    copyRecoveryCodes(): void {
        const text = this.recoveryCodes.join('\n');
        const done = () => {
            this.recoveryCodesCopied = true;
            this.messageService.add({
                severity: 'success',
                summary: this.translationService.translate('AUTH.MFA.CODES_TITLE'),
                detail: this.translationService.translate('AUTH.MFA.COPIED')
            });
        };
        if (navigator.clipboard?.writeText) {
            navigator.clipboard.writeText(text).then(done, () => { /* the codes stay on screen */ });
        }
    }

    /** Back to the credentials: the pending token is dropped and a fresh challenge drawn. */
    cancelMfa(): void {
        this.mfaStep = 'none';
        this.mfaToken = '';
        this.mfaCode = '';
        this.mfaEnrolment = null;
        this.recoveryCodes = [];
        this.completedSignIn = null;
        this.mfaBusy = false;
        this.loadCaptcha();
    }

    private completeMfaSignIn(response: LoginResponse): void {
        this.authService.setUserFromUmsResponse(response.user);
        this.mfaStep = 'none';
        this.mfaToken = '';
        this.mfaCode = '';
        this.completedSignIn = null;
        this.finishLogin();
    }

    private mfaFailed(err: any): void {
        this.mfaBusy = false;
        this.mfaCode = '';
        const detail = err?.error?.message || err?.error?.error
            || this.translationService.translate('AUTH.MFA.FAILED');
        this.messageService.add({
            severity: 'error',
            summary: this.translationService.translate('AUTH.MFA.TITLE'),
            detail
        });
        // An expired pending token sends the user back to the password.
        if (err?.status === 401 && /expired/i.test(detail)) {
            this.cancelMfa();
        }
    }

}
