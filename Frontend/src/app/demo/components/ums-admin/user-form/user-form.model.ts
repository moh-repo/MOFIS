import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { phoneValidator } from '../../../service/phone-number.util';
import { UmsUser, UmsUserSummary } from '../../../api/ums';

/**
 * The one form model behind every way of writing a staff account: the add page,
 * the edit page and the quick-add dialog on the list. The rules are the
 * server's (UmsUserService.requireContactShape, PasswordPolicy) restated once,
 * so the three cannot disagree with each other or with it.
 */
export type StaffUserFormMode = 'create' | 'edit';

/** PasswordPolicy.STAFF_MIN and its shape: 12 characters, a letter (any script) and a digit. */
export const STAFF_PASSWORD_MIN = 12;
export const STAFF_PASSWORD_PATTERN = /^(?=.*\p{L})(?=.*\d).+$/u;

/** Codes of MOHECORE.LU_USER_PRIVILEGE; the form maps them to the account's isAdmin flag. */
export const PRIVILEGE_ADMIN = 'ADMIN';
export const PRIVILEGE_REGULAR = 'REGULAR';

/** The confirm box against the password box beside it. */
export const matchesPassword: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
    const password = control.parent?.get('password')?.value ?? '';
    return control.value && control.value !== password ? { mismatch: true } : null;
};

/** At least one item picked: the attachés of an account a country admin creates (N-02.a). */
export const requiredList: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
    Array.isArray(control.value) && control.value.length > 0 ? null : { required: true };

export function buildStaffUserForm(fb: FormBuilder, mode: StaffUserFormMode): FormGroup {
    // Untyped on purpose: the credential controls exist in create mode only.
    const group: FormGroup = fb.group({
        // The username is the account's key; it is set once.
        userName: [{ value: '', disabled: mode === 'edit' }, [Validators.required, Validators.minLength(3), Validators.maxLength(100)]],
        firstName: ['', [Validators.required, Validators.maxLength(100)]],
        lastName: ['', [Validators.required, Validators.maxLength(100)]],
        email: ['', [Validators.email, Validators.maxLength(254)]],
        phone: ['', [phoneValidator()]],
        positionCode: [''],
        privilege: [PRIVILEGE_REGULAR],
        status: ['ACTIVE'],
        countryIds: [[] as number[]],
        groupIds: [[] as number[]],
        roleIds: [[] as number[]]
    });
    if (mode === 'create') {
        // A password is set at creation only; afterwards it changes through the
        // profile or the administrator's reset, never by editing the account.
        group.addControl('password', fb.control('', [
            Validators.required, Validators.minLength(STAFF_PASSWORD_MIN), Validators.pattern(STAFF_PASSWORD_PATTERN)
        ]));
        group.addControl('confirmPassword', fb.control('', [Validators.required, matchesPassword]));
    }
    return group;
}

export function fillStaffUserForm(form: FormGroup, user: UmsUser): void {
    form.patchValue({
        userName: user.userName ?? '',
        firstName: user.firstName ?? '',
        lastName: user.lastName ?? '',
        email: user.email ?? '',
        phone: user.phone ?? '',
        positionCode: user.positionCode ?? '',
        privilege: user.isAdmin ? PRIVILEGE_ADMIN : PRIVILEGE_REGULAR,
        status: user.status ?? 'ACTIVE',
        countryIds: user.countryIds ?? []
    });
}

/** The body of POST /ums/users: `password` in clear for hashing, the attachés with the request (N-02.a). */
export function staffUserCreatePayload(form: FormGroup): Partial<UmsUser> & { password: string; countryIds: number[] } {
    const v = form.getRawValue();
    return {
        userName: String(v.userName ?? '').trim(),
        firstName: String(v.firstName ?? '').trim(),
        lastName: String(v.lastName ?? '').trim(),
        email: String(v.email ?? '').trim(),
        phone: String(v.phone ?? '').trim() || undefined,
        positionCode: v.positionCode || null,
        status: v.status || 'ACTIVE',
        isAdmin: v.privilege === PRIVILEGE_ADMIN,
        password: String(v.password ?? ''),
        countryIds: [...(v.countryIds ?? [])]
    };
}

/** The body of PUT /ums/users/{id}: blanks clear phone and position; the server ignores the rest. */
export function staffUserUpdatePayload(form: FormGroup): Partial<UmsUser> {
    const v = form.getRawValue();
    return {
        firstName: String(v.firstName ?? '').trim(),
        lastName: String(v.lastName ?? '').trim(),
        email: String(v.email ?? '').trim(),
        phone: String(v.phone ?? '').trim(),
        positionCode: v.positionCode ?? '',
        status: v.status || 'ACTIVE',
        isAdmin: v.privilege === PRIVILEGE_ADMIN
    };
}

/** Marks every control, so each error shows at once when Save is pressed on an invalid form. */
export function markStaffUserFormTouched(form: FormGroup): void {
    Object.values(form.controls).forEach(control => {
        control.markAsDirty();
        control.markAsTouched();
        control.updateValueAndValidity({ onlySelf: true });
    });
}

/** The two letters on the account's badge: initials, or the start of the username. */
export function initialsOf(user: { firstName?: string | null; lastName?: string | null; userName?: string } | null | undefined): string {
    if (!user) {
        return '?';
    }
    const letters = [user.firstName, user.lastName]
        .map(part => (part ?? '').trim())
        .filter(Boolean)
        .map(part => part[0])
        .join('');
    return (letters || (user.userName ?? '?').slice(0, 2)).toUpperCase();
}

export function fullNameOf(user: { firstName?: string | null; lastName?: string | null; fullName?: string | null } | null | undefined): string {
    if (!user) {
        return '';
    }
    const joined = [user.firstName, user.lastName].map(part => (part ?? '').trim()).filter(Boolean).join(' ');
    return joined || (user.fullName ?? '').trim();
}

/** ng-zorro badge status for an account status code. */
export function statusBadge(status: string | null | undefined): 'success' | 'error' | 'default' {
    return status === 'ACTIVE' ? 'success' : status === 'LOCKED' ? 'error' : 'default';
}

/** The hero strip's tone for an account status code. */
export function statusTone(status: string | null | undefined): '' | 'dv-hero--danger' | 'dv-hero--muted' {
    return status === 'ACTIVE' ? '' : status === 'LOCKED' ? 'dv-hero--danger' : 'dv-hero--muted';
}

/** The privilege tag of a row or a record, whichever shape it came in. */
export function privilegeOf(user: UmsUser | UmsUserSummary | null | undefined): 'SUPER_ADMIN' | 'ADMIN' | 'USER' {
    if (!user) {
        return 'USER';
    }
    if ('privilege' in user && user.privilege) {
        return user.privilege;
    }
    const full = user as UmsUser;
    return full.isSuperAdmin ? 'SUPER_ADMIN' : full.isAdmin ? 'ADMIN' : 'USER';
}

export function privilegeColor(privilege: 'SUPER_ADMIN' | 'ADMIN' | 'USER'): string {
    return privilege === 'SUPER_ADMIN' ? 'gold' : privilege === 'ADMIN' ? 'blue' : 'default';
}
