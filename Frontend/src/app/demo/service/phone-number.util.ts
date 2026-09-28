/**
 * Phone-number rules for the student forms, mirrored by the backend's `PhoneNumbers`.
 *
 * A number is measured in digits, not characters. The old rule counted characters, so
 * "12-34" or "(((1)))" passed as a phone number while the client asked for a length
 * check. Separators (space, dash, dot, brackets) and one leading "+" are still allowed
 * wherever a person would write them.
 *
 * Bounds: at most 15 digits (the E.164 maximum, country code included); at least 8 for
 * a landline or emergency number (a Yemeni landline with its area code); at least 9 for
 * a mobile (a Yemeni mobile is 9 digits, 7XXXXXXXX).
 */
export const PHONE_MIN_DIGITS = 8;
export const MOBILE_MIN_DIGITS = 9;
export const PHONE_MAX_DIGITS = 15;
/** Characters, separators included — room for a fully formatted 15-digit number. */
export const PHONE_MAX_LENGTH = 25;

const PHONE_SHAPE = /^\+?[\d\s\-().]+$/;

/**
 * Trims and turns Arabic-Indic digits (٠-٩, ۰-۹) into Latin ones, so a number typed on
 * an Arabic keyboard is checked and stored the same way as one typed on a Latin one.
 */
export function normalizePhone(value: string | null | undefined): string {
    return (value ?? '')
        .replace(/[٠-٩]/g, d => String(d.charCodeAt(0) - 0x0660))
        .replace(/[۰-۹]/g, d => String(d.charCodeAt(0) - 0x06F0))
        .trim();
}

/** True for an acceptable number. Blank is acceptable: the fields are optional. */
export function isValidPhone(value: string | null | undefined, mobile = false): boolean {
    const phone = normalizePhone(value);
    if (!phone) {
        return true;
    }
    if (phone.length > PHONE_MAX_LENGTH || !PHONE_SHAPE.test(phone)) {
        return false;
    }
    const digits = phone.replace(/\D/g, '').length;
    return digits >= (mobile ? MOBILE_MIN_DIGITS : PHONE_MIN_DIGITS) && digits <= PHONE_MAX_DIGITS;
}

/**
 * Reactive-forms validator over {@link isValidPhone}: `{ phone: true }` for a number
 * that is not acceptable, nothing for a blank or valid one (review M8, account forms).
 */
export function phoneValidator(mobile = false): (control: { value: unknown }) => { phone: true } | null {
    return control => isValidPhone(control.value == null ? '' : String(control.value), mobile) ? null : { phone: true };
}
