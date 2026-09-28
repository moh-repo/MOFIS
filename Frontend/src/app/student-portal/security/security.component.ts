import { Component, OnInit, Optional } from '@angular/core';
import { Router } from '@angular/router';
import { NzMessageService } from 'ng-zorro-antd/message';
import { StudentPortalApiService, PortalSecurityStatus, PortalTotpStart } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';

/**
 * Account security on the student portal (security plan S-11): whether the
 * e-mail was verified, the optional authenticator-app second factor the
 * student turns on and off here, and — since the live test of 24 Sep 2026
 * (L12) — the password. Turning the second factor off needs a current code,
 * and changing the password needs the current one, so a stolen open session
 * can do neither.
 */
@Component({
  selector: 'app-portal-security',
  standalone: false,
  templateUrl: './security.component.html',
  styleUrls: ['./security.component.scss']
})
export class SecurityComponent implements OnInit {
  /** The portal floor (PasswordPolicy.PORTAL_MIN on the server); the server has the last word. */
  static readonly PASSWORD_MIN = 10;

  status: PortalSecurityStatus | null = null;
  loading = true;
  step: 'idle' | 'scan' | 'disable' = 'idle';
  enrolment: PortalTotpStart | null = null;
  code = '';
  busy = false;
  error = '';

  // The password card. Only /me/change-password existed until L12: the page
  // had nowhere to type one.
  currentPassword = '';
  newPassword = '';
  confirmPassword = '';
  passwordBusy = false;
  passwordError = '';

  constructor(
    private readonly api: StudentPortalApiService,
    private readonly message: NzMessageService,
    private readonly translation: TranslationService,
    @Optional() private readonly router?: Router
  ) {}

  /** The account still has the password it was given (SEC-001): the page says so first. */
  get mustChangePassword(): boolean {
    return this.api.mustChangePassword;
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.api.securityStatus().subscribe({
      next: s => { this.status = s; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  startEnrolment(): void {
    this.error = '';
    this.busy = true;
    this.api.totpStart().subscribe({
      next: e => { this.enrolment = e; this.step = 'scan'; this.code = ''; this.busy = false; },
      error: err => { this.busy = false; this.error = this.reason(err); }
    });
  }

  confirmEnrolment(): void {
    if (!this.code.trim()) { return; }
    this.error = '';
    this.busy = true;
    this.api.totpConfirm(this.code.trim()).subscribe({
      next: () => {
        this.busy = false;
        this.step = 'idle';
        this.enrolment = null;
        this.message.success(this.translation.translate('STUDENT_PORTAL.SECURITY.TOTP_ON'));
        this.reload();
      },
      error: err => { this.busy = false; this.error = this.reason(err); }
    });
  }

  askDisable(): void {
    this.step = 'disable';
    this.code = '';
    this.error = '';
  }

  disable(): void {
    if (!this.code.trim()) { return; }
    this.error = '';
    this.busy = true;
    this.api.totpDisable(this.code.trim()).subscribe({
      next: () => {
        this.busy = false;
        this.step = 'idle';
        this.message.success(this.translation.translate('STUDENT_PORTAL.SECURITY.TOTP_OFF'));
        this.reload();
      },
      error: err => { this.busy = false; this.error = this.reason(err); }
    });
  }

  cancel(): void {
    this.step = 'idle';
    this.enrolment = null;
    this.code = '';
    this.error = '';
  }

  /**
   * The first rule the new password breaks, as a translation key, or null. The
   * same three rules the server checks first (length, a letter and a digit)
   * plus the confirmation; the common-password and username rules stay on the
   * server, whose refusal is shown as it comes.
   */
  passwordProblemKey(): string | null {
    const p = this.newPassword ?? '';
    if (p.length < SecurityComponent.PASSWORD_MIN) { return 'STUDENT_PORTAL.SECURITY.PASSWORD_TOO_SHORT'; }
    if (!/\p{L}/u.test(p) || !/\d/.test(p)) { return 'STUDENT_PORTAL.SECURITY.PASSWORD_NEEDS_LETTER_DIGIT'; }
    if (p !== (this.confirmPassword ?? '')) { return 'STUDENT_PORTAL.SECURITY.PASSWORD_MISMATCH'; }
    return null;
  }

  changePassword(): void {
    if (this.passwordBusy) { return; }
    if (!(this.currentPassword ?? '').length) {
      this.passwordError = this.translation.translate('STUDENT_PORTAL.SECURITY.CURRENT_PASSWORD_REQUIRED');
      return;
    }
    const problem = this.passwordProblemKey();
    if (problem) {
      this.passwordError = this.translation.translate(problem);
      return;
    }
    this.passwordError = '';
    this.passwordBusy = true;
    this.api.changePassword(this.currentPassword, this.newPassword).subscribe({
      next: r => {
        this.passwordBusy = false;
        this.currentPassword = this.newPassword = this.confirmPassword = '';
        this.message.success(r?.message || this.translation.translate('STUDENT_PORTAL.SECURITY.PASSWORD_CHANGED'));
        // The portal was held to this change (SEC-001): it opens again.
        if (this.api.mustChangePassword) {
          this.api.mustChangePassword = false;
          this.router?.navigate(['/student-portal/dashboard']);
        }
      },
      // The server's reason as it comes: it is in the reader's language now.
      error: err => { this.passwordBusy = false; this.passwordError = this.reason(err); }
    });
  }

  private reason(err: { error?: { error?: string; message?: string } }): string {
    return err?.error?.error || err?.error?.message || this.translation.translate('STUDENT_PORTAL.SECURITY.FAILED');
  }
}
