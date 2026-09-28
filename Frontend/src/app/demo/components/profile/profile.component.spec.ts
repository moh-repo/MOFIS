import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MessageService } from 'primeng/api';

import { ProfileModule } from './profile.module';
import { ProfileComponent } from './profile.component';
import { UserService } from '../../service/user.service';
import { UmsAuthService } from '../../service/ums-auth.service';
import { AuthService } from '../../service/auth.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { codeListStub, codeRow } from '../../../testing/code-list.stub';

/**
 * The signed-in user's own account page.
 *
 * <p>The rule it exists to hold (owner, 29 August 2026): **a user may not change
 * their login name or their name, may change their password, and may read their
 * own details.** Two of those three were wrong on the page.
 *
 * <p>It offered an Edit mode over a form. Half of it could never have worked —
 * the UMS update endpoint has no branch for `userName`, so editing the login
 * name looked saved and silently did not persist — and the other half is
 * against the policy. The password button, meanwhile, had no `(click)` at all,
 * and was invisible besides: it carried no severity class, so the global filled
 * button rule painted its label white on a near-white card.
 *
 * <p>And three of the things the page stated were read from nothing. "Member
 * since" and "Last login" both returned `new Date()` — today, for everyone,
 * every day — and the security card claimed two-factor authentication was
 * "Available", with a green tick, when no second factor exists anywhere in the
 * system. The dates were in the record the whole time; the service was mapping
 * them away before the page saw them.
 *
 * <p>So the assertions below are mostly about absence: no way to edit, and no
 * claim that is not read from the record. Absence is the part that regresses
 * quietly, because adding a control back is easy and nothing else complains.
 */
describe('profile', () => {

    let fixture: any;
    let rtl = false;

    const account = {
        id: 41,
        userName: 'w.ali',
        firstName: 'Wael',
        lastName: 'Ali',
        email: 'w.ali@mohe.gov.ye',
        phone: '+967 1 234567',
        status: 'ACTIVE',
        isAdmin: true,
        isSuperAdmin: false,
        failedLoginAttempts: 0,
        lastLoginAt: '2026-08-28T07:15:00Z',
        passwordChangedAt: '2026-06-01T09:00:00Z',
        createdAt: '2025-03-04T10:00:00Z',
        updatedAt: '2026-08-28T07:15:00Z'
    };

    let userService: any;
    let umsAuth: any;

    const build = async (dir: 'ltr' | 'rtl' = 'ltr', overrides: any = {}) => {
        rtl = dir === 'rtl';
        userService = { getMyAccount: () => of({ ...account }), ...overrides.userService };
        // O-16: the security card reads the second-factor status on load.
        umsAuth = {
            changePassword: () => of({ message: 'ok' }),
            mfaStatus: () => of({ enabled: false, required: false, enrolledAt: null, recoveryCodesLeft: 0, locked: false }),
            ...overrides.umsAuth
        };

        TestBed.resetTestingModule();
        await TestBed.configureTestingModule({
            imports: [ProfileModule],
            providers: [
                MessageService,
                { provide: UserService, useValue: userService },
                { provide: UmsAuthService, useValue: umsAuth },
                {
                    provide: AuthService,
                    useValue: { currentUserValue: { id: 41 }, setUserFromUmsResponse: () => {} }
                },
                {
                    provide: CodeListService,
                    useValue: codeListStub({ 'ums-user-status': [codeRow('ACTIVE', { uiSeverity: 'success' })] },
                        rtl ? 'ar' : 'en')
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of(rtl ? 'ar' : 'en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => rtl, translate: (k: string) => k,
                        getCurrentLanguage: () => rtl ? 'ar' : 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(ProfileComponent);
        fixture.detectChanges();
        await fixture.whenStable();
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    };

    afterEach(() => {
        fixture?.nativeElement.remove();
    });

    const host = (): HTMLElement => fixture.nativeElement;
    const text = () => host().innerText;
    const component = () => fixture.componentInstance;

    // ─────────────────────────────────────────────── nothing here is editable ──

    it('offers no way to edit the account', async () => {
        await build();

        expect(host().querySelector('form')).withContext('the edit form is back').toBeNull();
        expect(text()).not.toContain('PROFILE.EDIT_PROFILE');
        expect(text()).not.toContain('PROFILE.SAVE_CHANGES');
    });

    it('puts the login name and both names on the page as text, not as fields', async () => {
        await build();

        // The dialog is closed, so any input on the page would be an edit field.
        expect(host().querySelectorAll('input').length)
            .withContext('an input outside the password dialog is an edit control').toBe(0);

        expect(text()).toContain('w.ali');
        expect(text()).toContain('Wael');
        expect(text()).toContain('Ali');
    });

    it('says who does own those fields', async () => {
        // A read-only field with no explanation reads as a broken one.
        await build();

        expect(text()).toContain('PROFILE.MANAGED_BY_ADMIN');
    });

    it('gives each field a label of its own', async () => {
        // Both name rows used to be labelled COMMON.PROFILE — the "الملف الشخصي
        // appears twice" of the August review.
        await build();

        const labels = Array.from(host().querySelectorAll('.info-label'))
            .map(l => (l as HTMLElement).innerText.trim());

        expect(new Set(labels).size).withContext(labels.join(' | ')).toBe(labels.length);
    });

    // ───────────────────────────────────────────── nothing here is invented ──

    it('shows the recorded dates rather than today', async () => {
        await build();

        expect(text()).withContext('createdAt from the record').toContain('2025-03-04');
        expect(text()).withContext('lastLoginAt from the record').toContain('2026-08-28');

        const today = new Date().toISOString().slice(0, 10);
        expect(text()).withContext(`today (${today}) is being displayed as a stored date`)
            .not.toContain(today);
    });

    it('says so plainly when a date is not recorded', async () => {
        await build('ltr', {
            userService: { getMyAccount: () => of({ ...account, lastLoginAt: null, passwordChangedAt: null }) }
        });

        expect(text()).toContain('PROFILE.NEVER');
    });

    it('makes no claim about two-factor authentication or a security rating', async () => {
        // Neither exists. Stating them with a green tick is worse than silence:
        // it tells the reader the account is protected by something it is not.
        await build();

        expect(text()).not.toContain('PROFILE.TWO_FACTOR');
        expect(text()).not.toContain('PROFILE.SECURITY_LEVEL');
        expect(text()).not.toContain('PROFILE.ACCOUNT_SECURITY');
    });

    it('shows an error instead of an invented account when the record cannot be read', async () => {
        // It used to build one called "User 41" and display it as though read.
        await build('ltr', {
            userService: { getMyAccount: () => throwError(() => ({ status: 500 })) }
        });

        expect(component().account).toBeNull();
        expect(text()).toContain('PROFILE.LOAD_ERROR');
        expect(text()).not.toContain('User 41');
    });

    // ──────────────────────────────────────────────────── change password ──

    it('opens the change-password dialog from the button', async () => {
        await build();

        const button = Array.from(host().querySelectorAll('button'))
            .find(b => (b as HTMLElement).innerText.includes('PROFILE.CHANGE_PASSWORD')) as HTMLElement;

        expect(button).withContext('no change-password button on the page').toBeTruthy();

        button.click();
        fixture.detectChanges();

        expect(component().passwordDialogVisible).toBeTrue();
    });

    it('gives the change-password button a class the global filled-button rule exempts', async () => {
        // Without one, styles.scss paints the label white on a near-white card
        // and the control is invisible — which is what happened here.
        await build();

        const button = Array.from(host().querySelectorAll('.action-buttons button'))[0] as HTMLElement;
        expect(button.className).toContain('p-button-outlined');
    });

    it('refuses a submission the server would only reject', async () => {
        await build();
        const spy = spyOn(umsAuth, 'changePassword').and.callThrough();
        const c = component();

        c.openPasswordDialog();
        c.currentPassword = 'old-password';
        c.newPassword = 'short';
        c.confirmPassword = 'short';
        expect(c.canSubmitPassword).withContext('too short').toBeFalse();

        c.newPassword = 'a-long-enough-one';
        c.confirmPassword = 'a-different-one';
        expect(c.canSubmitPassword).withContext('the two do not match').toBeFalse();

        c.confirmPassword = 'a-long-enough-one';
        expect(c.canSubmitPassword).toBeTrue();

        c.currentPassword = 'a-long-enough-one';
        expect(c.canSubmitPassword).withContext('new is the same as current').toBeFalse();

        expect(spy).withContext('nothing should have reached the server').not.toHaveBeenCalled();
    });

    it('sends the two passwords and closes on success', async () => {
        await build();
        const spy = spyOn(umsAuth, 'changePassword').and.returnValue(of({ message: 'ok' }));
        const reload = spyOn(userService, 'getMyAccount').and.returnValue(of({ ...account }));
        const c = component();

        c.openPasswordDialog();
        c.currentPassword = 'the-old-one';
        c.newPassword = 'the-new-one-2026';
        c.confirmPassword = 'the-new-one-2026';
        c.submitPassword();

        expect(spy).toHaveBeenCalledWith('the-old-one', 'the-new-one-2026');
        expect(c.passwordDialogVisible).toBeFalse();
        expect(c.passwordError).toBeNull();
        // The record now carries a new passwordChangedAt, which the card shows.
        expect(reload).toHaveBeenCalled();
    });

    it('does not keep the typed passwords around after a successful change', async () => {
        await build();
        const c = component();

        c.openPasswordDialog();
        c.currentPassword = 'the-old-one';
        c.newPassword = 'the-new-one-2026';
        c.confirmPassword = 'the-new-one-2026';
        c.submitPassword();

        expect(c.currentPassword).toBe('');
        expect(c.newPassword).toBe('');
        expect(c.confirmPassword).toBe('');
    });

    it('names the reason when the current password is wrong', async () => {
        // The endpoint answers 400 for exactly one reason, so the message can
        // be specific rather than "something went wrong".
        await build('ltr', {
            umsAuth: { changePassword: () => throwError(() => ({ status: 400 })) }
        });
        const c = component();

        c.openPasswordDialog();
        c.currentPassword = 'the-wrong-one';
        c.newPassword = 'the-new-one-2026';
        c.confirmPassword = 'the-new-one-2026';
        c.submitPassword();

        expect(c.passwordError).toBe('PROFILE.CURRENT_PASSWORD_WRONG');
        expect(c.passwordDialogVisible).withContext('the dialog stays open to be corrected').toBeTrue();
    });

    it('does not blame the password when the request could not be made', async () => {
        await build('ltr', {
            umsAuth: { changePassword: () => throwError(() => ({ status: 0 })) }
        });
        const c = component();

        c.openPasswordDialog();
        c.currentPassword = 'the-old-one';
        c.newPassword = 'the-new-one-2026';
        c.confirmPassword = 'the-new-one-2026';
        c.submitPassword();

        expect(c.passwordError).toBe('PROFILE.PASSWORD_CHANGE_FAILED');
    });

    // ──────────────────────────────────────────────────────────── direction ──

    it('puts the avatar on the side the language starts from', async () => {
        // It sat on the left in Arabic. Not for want of mirroring but for too
        // much of it: `direction: rtl` already lays the row out right to left,
        // so the avatar — the first child — reaches the right edge on its own,
        // and the component's own `flex-direction: row-reverse` put it back.
        for (const dir of ['ltr', 'rtl'] as const) {
            await build(dir);

            const row = host().querySelector('.profile-avatar-section') as HTMLElement;
            const avatar = host().querySelector('.profile-avatar') as HTMLElement;
            const name = host().querySelector('.profile-name') as HTMLElement;

            const box = row.getBoundingClientRect();
            const circle = avatar.getBoundingClientRect();

            const fromStart = dir === 'ltr' ? circle.left - box.left : box.right - circle.right;
            expect(fromStart)
                .withContext(`${dir}: the avatar is ${fromStart}px from the edge the language starts at`)
                .toBeLessThan(2);

            // And the name reads after it, not over it.
            const text = name.getBoundingClientRect();
            const ordered = dir === 'ltr' ? text.left >= circle.right : text.right <= circle.left;
            expect(ordered).withContext(`${dir}: the name is on the wrong side of the avatar`).toBeTrue();

            fixture.nativeElement.remove();
        }
    });

    it('keeps one gap between the avatar and the name, not two', async () => {
        // The RTL block added `margin-right: 1.5rem` to a row already spaced by
        // `gap: 1.5rem`, so Arabic got double the separation.
        const gaps: number[] = [];

        for (const dir of ['ltr', 'rtl'] as const) {
            await build(dir);

            const avatar = (host().querySelector('.profile-avatar') as HTMLElement).getBoundingClientRect();
            const info = (host().querySelector('.profile-basic-info') as HTMLElement).getBoundingClientRect();

            gaps.push(dir === 'ltr' ? info.left - avatar.right : avatar.left - info.right);
            fixture.nativeElement.remove();
        }

        expect(gaps[1]).withContext(`ltr ${gaps[0]}px vs rtl ${gaps[1]}px`).toBeCloseTo(gaps[0], 0);
    });

    it('carries dir, so the global RTL rules reach the page', async () => {
        // It carried only [class.rtl], and every global rule in the project is
        // keyed on [dir="rtl"], so none of them applied here.
        await build('rtl');

        const container = host().querySelector('.profile-container') as HTMLElement;
        expect(container.getAttribute('dir')).toBe('rtl');

        await build('ltr');
        expect((host().querySelector('.profile-container') as HTMLElement).getAttribute('dir')).toBe('ltr');
    });
});
