import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { NzI18nService, en_US, ar_EG } from 'ng-zorro-antd/i18n';
import { LoginCountry, StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';

type ForgotStep = 'request' | 'reset' | 'done';

/**
 * One security-code challenge on screen: the id the server knows it by, what the
 * student typed, and the object URL of the image.
 *
 * <p>There are two independent ones — the sign-in form and the forgot-password
 * dialog — because both can be open at once and an id is spent the moment it is
 * checked. Sharing a single challenge between them would mean solving the dialog
 * silently invalidated the one behind it.
 */
class CaptchaState {
  id = '';
  answer = '';
  imageUrl: string | null = null;
  loading = false;
}

@Component({
    selector: 'app-student-portal-login',
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.scss'],
    standalone: false
})
export class StudentPortalLoginComponent implements OnInit, OnDestroy {

  // Login form
  username = '';
  password = '';
  showPwd = false;
  loading = false;
  error = '';
  currentLang = 'en';

  // Country selector. Required: the backend verifies the choice against the
  // student's record, so an unset value cannot succeed and is worth catching here
  // rather than spending a round trip to be told the credentials are invalid.
  countries: LoginCountry[] = [];
  studyCountryInId: number | null = null;
  countriesLoading = false;
  /** A load finished with nothing to offer — by error, or by an empty list. */
  countriesUnavailable = false;

  // Security verification. The image is drawn by the server and the answer stays
  // there, so nothing on this page can be read out of the debugger and a request
  // posted straight at the API meets the same challenge the form does.
  readonly captcha = new CaptchaState();
  readonly forgotCaptcha = new CaptchaState();

  // Forgot-password dialog
  showForgotDialog = false;
  forgotStep: ForgotStep = 'request';
  forgotUsername = '';
  forgotOtp = '';
  forgotNewPassword = '';
  forgotConfirmPassword = '';
  forgotLoading = false;
  forgotMessage = '';
  forgotError = '';

  private destroy$ = new Subject<void>();

  constructor(
    private api: StudentPortalApiService,
    private router: Router,
    public translationService: TranslationService,
    private nzI18n: NzI18nService
  ) {
    if (this.api.isLoggedIn()) {
      this.router.navigate(['/student-portal/dashboard']);
    }
    this.currentLang = this.translationService.getCurrentLanguage() || 'en';
  }

  ngOnInit(): void {
    this.applyNzLocale(this.currentLang);
    this.loadCountries();
    this.refreshCaptcha();
    this.translationService.currentLanguage$.pipe(takeUntil(this.destroy$)).subscribe(lang => {
      this.currentLang = lang || 'en';
      this.applyNzLocale(this.currentLang);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    // Object URLs are held by the document until revoked; leaving them behind
    // keeps the PNGs in memory for the life of the tab.
    this.releaseCaptchaImage(this.captcha);
    this.releaseCaptchaImage(this.forgotCaptcha);
  }

  /** A new challenge for the sign-in form. */
  refreshCaptcha(): void {
    this.loadCaptcha(this.captcha, message => this.error = message);
  }

  /** A new challenge for the forgot-password dialog. */
  refreshForgotCaptcha(): void {
    this.loadCaptcha(this.forgotCaptcha, message => this.forgotError = message);
  }

  private loadCaptcha(state: CaptchaState, onError: (message: string) => void): void {
    state.loading = true;
    state.answer = '';
    this.api.captcha().pipe(takeUntil(this.destroy$)).subscribe({
      next: ({ id, imageUrl }) => {
        this.releaseCaptchaImage(state);
        state.id = id;
        state.imageUrl = imageUrl;
        state.loading = false;
      },
      error: () => {
        this.releaseCaptchaImage(state);
        state.id = '';
        state.loading = false;
        onError(this.translationService.translate('STUDENT_PORTAL.CAPTCHA.LOAD_FAILED'));
      }
    });
  }

  private releaseCaptchaImage(state: CaptchaState): void {
    if (state.imageUrl) {
      URL.revokeObjectURL(state.imageUrl);
      state.imageUrl = null;
    }
  }

  /**
   * The country list, and it is retryable on purpose.
   *
   * <p>The field is required and the selector disables itself when the list is
   * empty, so a load that comes back with nothing leaves the page in a state no
   * student can sign in from. Without a way to ask again, the only way out is to
   * know to reload the page — so the empty case says what happened and offers the
   * retry.
   */
  loadCountries(): void {
    this.countriesLoading = true;
    this.countriesUnavailable = false;
    this.api.loginCountries().pipe(takeUntil(this.destroy$)).subscribe({
      next: (list) => {
        this.countries = list || [];
        this.countriesLoading = false;
        this.countriesUnavailable = this.countries.length === 0;
        // A choice the new list does not contain is stale — a reload that comes
        // back shorter would otherwise leave the form holding a country the
        // student can no longer see and the server will not accept.
        if (this.studyCountryInId != null
            && !this.countries.some(c => c.id === this.studyCountryInId)) {
          this.studyCountryInId = null;
        }
        // With one country there is no choice to make, so make it for them. The
        // field stays required — this fills it rather than waiving it.
        if (this.countries.length === 1) {
          this.studyCountryInId = this.countries[0].id;
        }
      },
      error: () => {
        this.countries = [];
        this.studyCountryInId = null;
        this.countriesLoading = false;
        this.countriesUnavailable = true;
        this.error = this.translationService.translate('STUDENT_PORTAL.COUNTRY_LOAD_FAILED');
      }
    });
  }

  /** The name in the reader's language, falling back to whichever exists. */
  countryLabel(c: LoginCountry): string {
    const ar = c.studyCountryInName;
    const en = c.studyCountryInNameEn;
    return (this.currentLang === 'ar' ? (ar || en) : (en || ar)) || String(c.id);
  }

  private applyNzLocale(code: string): void {
    this.nzI18n.setLocale(code === 'ar' ? ar_EG : en_US);
  }

  switchLang(code: string): void {
    // Switches in place — see TranslationService.switchLanguage.
    this.translationService.switchLanguage(code);
  }

  /** S-11: the code the server asked for, if any. */
  challenge: '' | 'VERIFY_EMAIL' | 'TOTP_REQUIRED' = '';
  challengeMessage = '';
  challengeCode = '';

  cancelChallenge(): void {
    this.challenge = '';
    this.challengeMessage = '';
    this.challengeCode = '';
    this.error = '';
  }

  onSubmit(): void {
    this.error = '';
    if (this.challenge && !this.challengeCode.trim()) {
      this.error = this.translationService.translate('STUDENT_PORTAL.CHALLENGE.CODE_REQUIRED');
      return;
    }
    if (!this.username?.trim() || !this.password) {
      this.error = this.translationService.translate('STUDENT_PORTAL.LOGIN_CREDENTIALS_REQUIRED');
      return;
    }
    if (this.studyCountryInId == null) {
      this.error = this.translationService.translate('STUDENT_PORTAL.COUNTRY_REQUIRED');
      return;
    }
    if (!this.captcha.answer.trim()) {
      this.error = this.translationService.translate('STUDENT_PORTAL.CAPTCHA.REQUIRED');
      return;
    }
    if (!this.captcha.id) {
      // The image never loaded, so there is nothing to answer. Fetch one rather
      // than posting a request the server is bound to refuse.
      this.refreshCaptcha();
      return;
    }
    this.loading = true;
    const codes = this.challenge === 'VERIFY_EMAIL' ? { emailCode: this.challengeCode.trim() }
      : this.challenge === 'TOTP_REQUIRED' ? { totpCode: this.challengeCode.trim() } : {};
    this.api.login(this.username.trim().toLowerCase(), this.password, this.studyCountryInId,
      this.captcha.id, this.captcha.answer.trim(), codes).subscribe({
      next: (res) => {
        this.loading = false;
        // S-11: the server may ask for a code before it hands out a session.
        if (res.status === 'VERIFY_EMAIL' || res.status === 'TOTP_REQUIRED') {
          this.challenge = res.status;
          this.challengeMessage = res.message || '';
          this.challengeCode = '';
          // The id was spent proving this request came from the form, and the
          // next one has to prove it again — the server checks the password
          // before it looks at the code, so the second call is as much a
          // guessing surface as the first.
          this.refreshCaptcha();
          return;
        }
        this.api.setToken(res.token);
        // SEC-001 (D9-a): an account that never chose its password goes to the form first.
        this.api.mustChangePassword = !!res.mustChangePassword;
        if (this.api.mustChangePassword) {
          this.router.navigate(['/student-portal/security'], { queryParams: { changePassword: 'required' } });
        } else {
          this.router.navigate(['/student-portal/dashboard']);
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.message || err.error?.error || this.translationService.translate('STUDENT_PORTAL.LOGIN_INVALID');
        // Spent either way, right answer or wrong.
        this.refreshCaptcha();
      }
    });
  }

  openForgot(): void {
    this.forgotStep = 'request';
    this.forgotUsername = this.username;
    this.forgotOtp = '';
    this.forgotNewPassword = '';
    this.forgotConfirmPassword = '';
    this.forgotMessage = '';
    this.forgotError = '';
    this.showForgotDialog = true;
    this.refreshForgotCaptcha();
  }

  requestOtp(): void {
    if (!this.forgotUsername?.trim()) {
      this.forgotError = this.translationService.translate('STUDENT_PORTAL.FORGOT_EMAIL_REQUIRED');
      return;
    }
    if (!this.forgotCaptcha.answer.trim()) {
      this.forgotError = this.translationService.translate('STUDENT_PORTAL.CAPTCHA.REQUIRED');
      return;
    }
    if (!this.forgotCaptcha.id) {
      this.refreshForgotCaptcha();
      return;
    }
    this.forgotLoading = true;
    this.forgotError = '';
    this.api.forgotPassword(this.forgotUsername.trim(), this.forgotCaptcha.id, this.forgotCaptcha.answer.trim()).subscribe({
      next: (res: any) => {
        this.forgotLoading = false;
        this.forgotMessage = res.message || this.translationService.translate('STUDENT_PORTAL.FORGOT_RESET_CODE_SENT');
        this.forgotStep = 'reset';
        // Step 2 has no challenge of its own — it needs the OTP this step just
        // sent — so the image comes off the screen here.
        this.releaseCaptchaImage(this.forgotCaptcha);
        this.forgotCaptcha.id = '';
        this.forgotCaptcha.answer = '';
      },
      error: (err: any) => {
        this.forgotLoading = false;
        this.forgotError = err.error?.message || err.error?.error
          || this.translationService.translate('STUDENT_PORTAL.FORGOT_REQUEST_ERROR');
        this.refreshForgotCaptcha();
      }
    });
  }

  submitReset(): void {
    if (!this.forgotOtp?.trim()) {
      this.forgotError = this.translationService.translate('STUDENT_PORTAL.FORGOT_CODE_REQUIRED');
      return;
    }
    // The server's rule (PasswordPolicy.PORTAL_MIN): 10 characters, a letter and a digit.
    // This said 6, so a password the message described as too short got past it
    // and was refused by the server instead.
    if (!this.forgotNewPassword || this.forgotNewPassword.length < 10
        || !/\p{L}/u.test(this.forgotNewPassword) || !/\d/.test(this.forgotNewPassword)) {
      this.forgotError = this.translationService.translate('STUDENT_PORTAL.FORGOT_PASSWORD_MIN_LENGTH');
      return;
    }
    if (this.forgotNewPassword !== this.forgotConfirmPassword) {
      this.forgotError = this.translationService.translate('STUDENT_PORTAL.FORGOT_PASSWORD_MISMATCH');
      return;
    }
    this.forgotLoading = true;
    this.forgotError = '';
    this.api.resetPassword(this.forgotUsername.trim(), this.forgotOtp.trim(), this.forgotNewPassword).subscribe({
      next: (res: any) => {
        this.forgotLoading = false;
        this.forgotMessage = res.message || this.translationService.translate('STUDENT_PORTAL.FORGOT_RESET_SUCCESS');
        this.forgotStep = 'done';
      },
      error: (err: any) => {
        this.forgotLoading = false;
        this.forgotError = err.error?.message || this.translationService.translate('STUDENT_PORTAL.FORGOT_INVALID_CODE');
      }
    });
  }
}
