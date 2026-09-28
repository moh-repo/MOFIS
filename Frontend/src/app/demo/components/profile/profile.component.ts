import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { UserService } from '../../service/user.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { TranslationService } from '../../service/translation.service';
import { AuthService } from '../../service/auth.service';
import { MfaEnrolment, MfaStatus, UmsUser } from '../../api/ums';
import { MessageService } from 'primeng/api';

/**
 * The signed-in user's own account.
 *
 * <p>Read-only by policy (owner, 29 August 2026): an account's login name and
 * the name on it are the administrator's to set, not the holder's. The page
 * shows what is recorded and offers exactly one action — changing the password.
 *
 * <p>It used to offer an Edit mode over a two-field form. Half of it could not
 * work: the UMS update endpoint has no branch for `userName`, so editing the
 * login name looked saved and never persisted. The other half now contradicts
 * the policy. Both are gone rather than disabled, so the screen does not show a
 * control that exists only to refuse.
 *
 * <p>What the page shows is read from the record and nothing else. Three of the
 * things it used to state were not read from anywhere: "Member since" and
 * "Last login" both returned `new Date()` — today, every day, for everyone —
 * and the security card claimed two-factor authentication was "Available" with
 * a green tick beside it, when no second factor exists anywhere in the system.
 * The real values were in the record all along (`createdAt`, `lastLoginAt`,
 * `passwordChangedAt`); `getUserProfile` was throwing them away by mapping the
 * UMS user down to the shell's four-field `User` before the page ever saw it.
 */
@Component({
    selector: 'app-profile',
    templateUrl: './profile.component.html',
    styleUrls: ['./profile.component.scss'],
    standalone: false
})
export class ProfileComponent implements OnInit {

    /** The minimum this screen will submit. The server enforces no policy of its own. */
    static readonly MIN_PASSWORD_LENGTH = 12; // O-25: matches PasswordPolicy.STAFF_MIN

    account: UmsUser | null = null;
    loadingProfile = false;

    // ── two-factor authentication (O-16) ────────────────────────────────────
    mfa: MfaStatus | null = null;
    mfaDialogVisible = false;
    mfaMode: 'enrol' | 'disable' | 'codes' = 'enrol';
    mfaEnrolment: MfaEnrolment | null = null;
    mfaCode = '';
    mfaBusy = false;
    mfaError: string | null = null;
    recoveryCodes: string[] = [];

    // ── change password ─────────────────────────────────────────────────────
    passwordDialogVisible = false;
    changingPassword = false;
    currentPassword = '';
    newPassword = '';
    confirmPassword = '';
    passwordError: string | null = null;

    /** Optional: the page is also built in tests that provide no router. */
    private readonly router = inject(Router, { optional: true });

    constructor(
        private userService: UserService,
        private umsAuth: UmsAuthService,
        private authService: AuthService,
        private messageService: MessageService,
        public translationService: TranslationService
    ) {}

    /** DEC-04: the password was set by an administrator and must be changed first. */
    get passwordChangeRequired(): boolean {
        return this.umsAuth.mustChangePassword;
    }

    ngOnInit(): void {
        this.loadMfaStatus();
        this.loadAccount();
        if (this.passwordChangeRequired) {
            this.openPasswordDialog();
        }
    }

    get isRtl(): boolean {
        return this.translationService.isRTL();
    }

    private t(key: string): string {
        return this.translationService.translate(key) || key;
    }

    loadAccount(): void {
        if (!this.authService.currentUserValue) {
            this.messageService.add({
                severity: 'warn',
                summary: this.t('COMMON.WARNING'),
                detail: this.t('PROFILE.NO_USER_LOGGED_IN')
            });
            return;
        }

        this.loadingProfile = true;
        this.userService.getMyAccount().subscribe({
            next: account => {
                this.account = account;
                // Keeps the topbar's name in step with the record when an
                // administrator has changed it since this session signed in.
                this.authService.setUserFromUmsResponse(account);
                this.loadingProfile = false;
            },
            error: () => {
                // No invented fallback account. The page previously built one
                // named "User 41" and displayed it as though it had been read,
                // which is a worse outcome than an empty page and an error.
                this.account = null;
                this.loadingProfile = false;
                this.messageService.add({
                    severity: 'error',
                    summary: this.t('COMMON.ERROR'),
                    detail: this.t('PROFILE.LOAD_ERROR')
                });
            }
        });
    }

    /** Both names when they are recorded, the login name when they are not. */
    get displayName(): string {
        const full = [this.account?.firstName, this.account?.lastName]
            .filter(part => !!part && part.trim() !== '')
            .join(' ');
        return full || this.account?.userName || '';
    }

    /** The account's tier in words: the tag printed SUPER_ADMIN / ADMIN / USER (QA B11). */
    get roleName(): string {
        const key = this.account?.isSuperAdmin ? 'UMS.USERS.SUPER_ADMIN'
            : this.account?.isAdmin ? 'UMS.USERS.ADMIN' : 'UMS.USERS.USER';
        return this.t(key);
    }

    getInitials(): string {
        const source = this.displayName.trim();
        if (!source) { return 'U'; }
        const parts = source.split(/\s+/);
        const first = parts[0].charAt(0);
        const second = parts.length > 1 ? parts[parts.length - 1].charAt(0) : '';
        return (first + second).toUpperCase();
    }

    // ── two-factor authentication (O-16) ────────────────────────────────────

    loadMfaStatus(): void {
        this.umsAuth.mfaStatus().subscribe({
            next: status => { this.mfa = status; },
            error: () => { this.mfa = null; }
        });
    }

    /** Turning the factor on: fetch a secret, show it, ask for its first code. */
    openMfaEnrol(): void {
        this.mfaMode = 'enrol';
        this.mfaEnrolment = null;
        this.mfaCode = '';
        this.mfaError = null;
        this.recoveryCodes = [];
        this.mfaDialogVisible = true;
        this.mfaBusy = true;
        this.umsAuth.mfaEnrolStart().subscribe({
            next: enrolment => { this.mfaEnrolment = enrolment; this.mfaBusy = false; },
            error: err => { this.mfaBusy = false; this.mfaError = this.errorText(err); }
        });
    }

    /** Turning it off, or asking for fresh recovery codes: both need a current code. */
    openMfaDialog(mode: 'disable' | 'codes'): void {
        this.mfaMode = mode;
        this.mfaCode = '';
        this.mfaError = null;
        this.recoveryCodes = [];
        this.mfaDialogVisible = true;
    }

    submitMfa(): void {
        const code = this.mfaCode.trim();
        if (!code || this.mfaBusy) { return; }
        this.mfaBusy = true;
        this.mfaError = null;
        const done = (codes: string[], toastKey: string) => {
            this.mfaBusy = false;
            this.mfaCode = '';
            this.recoveryCodes = codes;
            this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t(toastKey) });
            this.loadMfaStatus();
            // Recovery codes are shown once, in the dialog, before it closes.
            if (codes.length === 0) { this.mfaDialogVisible = false; }
        };
        const fail = (err: any) => { this.mfaBusy = false; this.mfaCode = ''; this.mfaError = this.errorText(err); };
        if (this.mfaMode === 'enrol') {
            this.umsAuth.mfaEnrolConfirm(code).subscribe({
                next: r => done(r.recoveryCodes || [], 'PROFILE.MFA.ENABLED_SUCCESS'), error: fail });
        } else if (this.mfaMode === 'disable') {
            this.umsAuth.mfaDisable(code).subscribe({
                next: () => done([], 'PROFILE.MFA.DISABLED_SUCCESS'), error: fail });
        } else {
            this.umsAuth.mfaRecoveryCodes(code).subscribe({
                next: r => done(r.recoveryCodes || [], 'PROFILE.MFA.CODES_SUCCESS'), error: fail });
        }
    }

    copyRecoveryCodes(): void {
        if (navigator.clipboard?.writeText) {
            navigator.clipboard.writeText(this.recoveryCodes.join('\n')).then(() =>
                this.messageService.add({ severity: 'success', summary: this.t('COMMON.SUCCESS'), detail: this.t('AUTH.MFA.COPIED') }));
        }
    }

    get mfaDialogTitle(): string {
        return this.recoveryCodes.length ? this.t('AUTH.MFA.CODES_TITLE')
            : this.mfaMode === 'enrol' ? this.t('PROFILE.MFA.ENABLE')
            : this.mfaMode === 'disable' ? this.t('PROFILE.MFA.DISABLE')
            : this.t('PROFILE.MFA.NEW_CODES');
    }

    private errorText(err: any): string {
        return err?.error?.message || err?.error?.error || this.t('AUTH.MFA.FAILED');
    }

    // ── change password ─────────────────────────────────────────────────────

    openPasswordDialog(): void {
        this.currentPassword = '';
        this.newPassword = '';
        this.confirmPassword = '';
        this.passwordError = null;
        this.passwordDialogVisible = true;
    }

    /**
     * What can be settled without asking the server. The current password is
     * deliberately not among them — only the server can say whether it is right,
     * and it answers with a 400.
     */
    private validationError(): string | null {
        if (!this.currentPassword || !this.newPassword || !this.confirmPassword) {
            return 'PROFILE.PASSWORD_ALL_FIELDS';
        }
        if (this.newPassword.length < ProfileComponent.MIN_PASSWORD_LENGTH) {
            return 'PROFILE.PASSWORD_TOO_SHORT';
        }
        if (this.newPassword !== this.confirmPassword) {
            return 'PROFILE.PASSWORD_MISMATCH';
        }
        if (this.newPassword === this.currentPassword) {
            return 'PROFILE.PASSWORD_UNCHANGED';
        }
        return null;
    }

    get canSubmitPassword(): boolean {
        return !this.changingPassword && this.validationError() === null;
    }

    submitPassword(): void {
        const invalid = this.validationError();
        if (invalid) {
            this.passwordError = invalid;
            return;
        }

        this.changingPassword = true;
        this.passwordError = null;

        this.umsAuth.changePassword(this.currentPassword, this.newPassword).subscribe({
            next: () => {
                this.changingPassword = false;
                this.passwordDialogVisible = false;
                this.currentPassword = '';
                this.newPassword = '';
                this.confirmPassword = '';
                this.messageService.add({
                    severity: 'success',
                    summary: this.t('COMMON.SUCCESS'),
                    detail: this.t('PROFILE.PASSWORD_CHANGED_SUCCESS')
                });
                // The record now carries a new passwordChangedAt, which the
                // security card shows.
                this.loadAccount();
                // A required change is over: re-read the session so the flag clears,
                // then let the user into the application.
                if (this.passwordChangeRequired) {
                    this.umsAuth.refreshProfile().subscribe(() => this.router?.navigate(['/']));
                }
            },
            error: err => {
                this.changingPassword = false;
                // The endpoint answers 400 for exactly one reason: the current
                // password did not match. Anything else is a failure to reach it.
                this.passwordError = err?.status === 400
                    ? 'PROFILE.CURRENT_PASSWORD_WRONG'
                    : 'PROFILE.PASSWORD_CHANGE_FAILED';
            }
        });
    }
}
