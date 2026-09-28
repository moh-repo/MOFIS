import { of, throwError } from 'rxjs';
import { NzMessageService } from 'ng-zorro-antd/message';

import { SecurityComponent } from './security.component';
import { StudentPortalApiService } from '../student-portal-api.service';
import { TranslationService } from '../../demo/service/translation.service';

/**
 * The password card on Account security (live QA L12, 24 Sep 2026).
 *
 * <p>The portal had a change-password API and no form to reach it. The card
 * checks what the server checks first — length, a letter and a digit, the
 * confirmation — and shows the server's own refusal for the rest, which is now
 * in the reader's language.
 */
describe('SecurityComponent password card', () => {

    let component: SecurityComponent;
    let api: { changePassword: jasmine.Spy; securityStatus: jasmine.Spy };
    let message: { success: jasmine.Spy };

    beforeEach(() => {
        api = {
            changePassword: jasmine.createSpy('changePassword').and.returnValue(of({ message: 'تم تغيير كلمة المرور بنجاح' })),
            securityStatus: jasmine.createSpy('securityStatus').and.returnValue(of({ emailVerifiedAt: null, totpEnabled: false }))
        };
        message = { success: jasmine.createSpy('success') };
        const translation = { translate: (k: string) => k } as unknown as TranslationService;
        component = new SecurityComponent(
            api as unknown as StudentPortalApiService,
            message as unknown as NzMessageService,
            translation);
    });

    function fill(current: string, next: string, confirm = next): void {
        component.currentPassword = current;
        component.newPassword = next;
        component.confirmPassword = confirm;
    }

    it('names the rule a new password breaks, in the order the server checks them', () => {
        fill('old-one-1', 'short1');
        expect(component.passwordProblemKey()).toBe('STUDENT_PORTAL.SECURITY.PASSWORD_TOO_SHORT');

        fill('old-one-1', 'onlyletterslong');
        expect(component.passwordProblemKey()).toBe('STUDENT_PORTAL.SECURITY.PASSWORD_NEEDS_LETTER_DIGIT');

        fill('old-one-1', '1234567890');
        expect(component.passwordProblemKey()).toBe('STUDENT_PORTAL.SECURITY.PASSWORD_NEEDS_LETTER_DIGIT');

        fill('old-one-1', 'Correct-Horse-9', 'Correct-Horse-8');
        expect(component.passwordProblemKey()).toBe('STUDENT_PORTAL.SECURITY.PASSWORD_MISMATCH');

        fill('old-one-1', 'كلمة-مرور-2026');
        expect(component.passwordProblemKey()).withContext('a letter in any script counts').toBeNull();
    });

    it('asks for the current password before anything else, and sends nothing', () => {
        fill('', 'Correct-Horse-9');
        component.changePassword();

        expect(component.passwordError).toBe('STUDENT_PORTAL.SECURITY.CURRENT_PASSWORD_REQUIRED');
        expect(api.changePassword).not.toHaveBeenCalled();
    });

    it('refuses a weak password on the page, and sends nothing', () => {
        fill('old-one-1', 'short1');
        component.changePassword();

        expect(component.passwordError).toBe('STUDENT_PORTAL.SECURITY.PASSWORD_TOO_SHORT');
        expect(api.changePassword).not.toHaveBeenCalled();
    });

    it('sends the current and the new password, then clears the form and thanks the reader in the server\'s words', () => {
        fill('old-one-1', 'Correct-Horse-9');
        component.changePassword();

        expect(api.changePassword).toHaveBeenCalledOnceWith('old-one-1', 'Correct-Horse-9');
        expect(component.passwordBusy).toBeFalse();
        expect(component.passwordError).toBe('');
        expect(component.currentPassword).toBe('');
        expect(component.newPassword).toBe('');
        expect(component.confirmPassword).toBe('');
        expect(message.success).toHaveBeenCalledOnceWith('تم تغيير كلمة المرور بنجاح');
    });

    it('shows the server\'s refusal as it comes — a wrong current password, a common password', () => {
        api.changePassword.and.returnValue(throwError(() => ({ status: 400, error: { error: 'كلمة المرور الحالية غير صحيحة' } })));
        fill('wrong-one-1', 'Correct-Horse-9');
        component.changePassword();

        expect(component.passwordError).toBe('كلمة المرور الحالية غير صحيحة');
        expect(component.passwordBusy).toBeFalse();
        expect(component.newPassword).withContext('the typed values survive a refusal').toBe('Correct-Horse-9');
    });

    it('falls back to the generic text when the server says nothing', () => {
        api.changePassword.and.returnValue(throwError(() => ({ status: 500 })));
        fill('old-one-1', 'Correct-Horse-9');
        component.changePassword();

        expect(component.passwordError).toBe('STUDENT_PORTAL.SECURITY.FAILED');
    });

    // Live QA M16: a wrong code when turning the factor off is a 400; the card says so
    // and stays on the code step instead of the student being signed out.
    it('shows a wrong authenticator code and stays on the code step', () => {
        (api as any).totpDisable = jasmine.createSpy('totpDisable').and.returnValue(
            throwError(() => ({ status: 400, error: { message: 'The code did not match' } })));
        component.step = 'disable';
        component.code = '000000';

        component.disable();

        expect(component.step).toBe('disable');
        expect(component.error).toBeTruthy();
        expect(component.busy).toBeFalse();
    });

    // SEC-001 (D9-a): once the student has chosen a password, the portal opens again.
    it('releases a portal held to the password change once the password is changed', () => {
        const router = { navigate: jasmine.createSpy('navigate') };
        (api as any).mustChangePassword = true;
        component = new SecurityComponent(
            api as unknown as StudentPortalApiService,
            message as unknown as NzMessageService,
            { translate: (k: string) => k } as unknown as TranslationService,
            router as any);
        expect(component.mustChangePassword).toBeTrue();
        fill('old-password', 'NewPassw0rd1');

        component.changePassword();

        expect((api as any).mustChangePassword).toBeFalse();
        expect(router.navigate).toHaveBeenCalledWith(['/student-portal/dashboard']);
    });
});
