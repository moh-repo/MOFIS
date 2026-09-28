import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import { UserMfaResetDialogComponent } from './user-mfa-reset-dialog.component';
import { UmsUserService } from '../../../service/ums-user.service';
import { ToastService } from '../../../service/toast.service';
import { TranslationService } from '../../../service/translation.service';
import { provideUmsTestIcons } from '../../../../testing/nz-icons.stub';

/**
 * The reset two-factor dialog of the users list and the account page.
 *
 * <p>What replaced the confirm box has to do three things the box did not:
 * say who the reset is about and what the account has now, say so when that
 * could not be read rather than pretend the account is not enrolled, and
 * only close once the server has agreed.
 */

const account = {
    id: 7, userName: 'h.saleh', firstName: 'Hana', lastName: 'Saleh', fullName: 'Hana Saleh',
    email: 'h.saleh@mof.test', status: 'ACTIVE' as const, privilege: 'USER' as const, countryNames: [], lastLoginAt: null
};

const enrolled = { enabled: true, required: true, enrolledAt: '2026-03-14T09:15:00', recoveryCodesLeft: 3, locked: false };
const notEnrolled = { enabled: false, required: true, enrolledAt: null, recoveryCodesLeft: 0, locked: false };

describe('the reset two-factor dialog', () => {

    let fixture: any;
    let component: UserMfaResetDialogComponent;
    let users: { getMfaStatus: jasmine.Spy; resetMfa: jasmine.Spy };
    let toast: { success: jasmine.Spy; error: jasmine.Spy };

    const build = async () => {
        users = {
            getMfaStatus: jasmine.createSpy('getMfaStatus').and.returnValue(of(enrolled)),
            resetMfa: jasmine.createSpy('resetMfa').and.returnValue(of({ message: 'ok' }))
        };
        toast = { success: jasmine.createSpy('success'), error: jasmine.createSpy('error') };
        await TestBed.configureTestingModule({
            imports: [UserMfaResetDialogComponent],
            providers: [
                provideNoopAnimations(),
                provideUmsTestIcons(),
                { provide: UmsUserService, useValue: users },
                { provide: ToastService, useValue: toast },
                {
                    provide: TranslationService,
                    useValue: {
                        translate: (k: string) => k, isRTL: () => false, isReady: () => true,
                        currentLanguage$: of('en'), initialized$: of(true), getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();
        fixture = TestBed.createComponent(UserMfaResetDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    };

    /** Opens the dialog for the account and lets the overlay render. */
    const open = async (user = account) => {
        component.open(user);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    afterEach(() => {
        fixture?.destroy();
        document.querySelectorAll('.cdk-overlay-container').forEach(o => o.remove());
    });

    const dialog = () => document.querySelector('.ums-dialog .ant-modal') as HTMLElement | null;
    const text = () => dialog()?.textContent ?? '';
    const confirmButton = () => document.querySelector('.ums-dialog .ums-mfa-reset__confirm') as HTMLButtonElement;
    const notice = () => document.querySelector('.ums-dialog .ums-mfa-reset__notice') as HTMLElement | null;

    it('says who the reset is about and what the account has now', async () => {
        await build();
        await open();

        expect(dialog()).withContext('the dialog did not open').toBeTruthy();
        expect(users.getMfaStatus).toHaveBeenCalledWith(7);
        expect(text()).toContain('h.saleh');
        expect(text()).toContain('Hana Saleh');
        expect(text()).toContain('h.saleh@mof.test');
        // The state tile, the enrolment date and the recovery codes.
        expect(text()).toContain('UMS.USERS.VIEW.MFA_ENABLED');
        expect(text()).toContain('Mar 14, 2026');
        expect(text()).toContain('UMS.USERS.VIEW.MFA_RECOVERY_LEFT');
        expect(dialog()!.querySelectorAll('.dv-kpi__value')[2].textContent!.trim()).toBe('3');
        expect(notice()!.classList).toContain('dv-notice--warn');
        expect(text()).toContain('UMS.USERS.MFA_RESET.EFFECT_APP');
    });

    it('says when the account has no factor to remove', async () => {
        await build();
        users.getMfaStatus.and.returnValue(of(notEnrolled));
        await open();

        expect(text()).toContain('UMS.USERS.VIEW.MFA_NOT_ENROLLED');
        expect(text()).toContain('UMS.USERS.NEVER');
        expect(notice()!.classList).toContain('dv-notice--info');
        expect(text()).toContain('UMS.USERS.MFA_RESET.NOT_ENROLLED');
    });

    it('says when the status could not be read, and still offers the reset', async () => {
        await build();
        users.getMfaStatus.and.returnValue(throwError(() => new Error('down')));
        await open();

        expect(text()).toContain('COMMON.NOT_AVAILABLE');
        expect(text()).not.toContain('UMS.USERS.VIEW.MFA_NOT_ENROLLED');
        expect(notice()!.classList).toContain('dv-notice--muted');
        expect(text()).toContain('UMS.USERS.MFA_RESET.STATUS_FAILED');
        expect(confirmButton().disabled).toBeFalse();
    });

    it('resets on the confirm button, closes and tells the host', async () => {
        await build();
        const done = jasmine.createSpy('done');
        component.done.subscribe(done);
        await open();

        confirmButton().click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(users.resetMfa).toHaveBeenCalledWith(7);
        expect(toast.success).toHaveBeenCalledWith('UMS.USERS.MFA_RESET_DONE');
        expect(done).toHaveBeenCalled();
        expect(component.visible).toBeFalse();
    });

    it('stays open and shows the refusal when the server says no', async () => {
        await build();
        users.resetMfa.and.returnValue(throwError(() => ({ error: { message: 'Not yours to administer' } })));
        const done = jasmine.createSpy('done');
        component.done.subscribe(done);
        await open();

        confirmButton().click();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(toast.error).toHaveBeenCalled();
        expect(done).not.toHaveBeenCalled();
        expect(component.visible).toBeTrue();
        expect(component.resetting).toBeFalse();
    });

    it('does nothing on the button before anyone is chosen', async () => {
        await build();
        component.reset();
        expect(users.resetMfa).not.toHaveBeenCalled();
    });
});
