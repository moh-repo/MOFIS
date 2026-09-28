import { isValidPhone, normalizePhone } from './phone-number.util';

describe('phone-number.util', () => {
    it('accepts blank: the contact fields are optional', () => {
        expect(isValidPhone(null)).toBeTrue();
        expect(isValidPhone('  ', true)).toBeTrue();
    });

    it('needs 9 digits for a mobile and 8 for a landline', () => {
        expect(isValidPhone('77712345', true)).toBeFalse();
        expect(isValidPhone('777123456', true)).toBeTrue();
        expect(isValidPhone('01234567')).toBeTrue();
        expect(isValidPhone('0123456')).toBeFalse();
    });

    it('counts digits, not separators', () => {
        expect(isValidPhone('12-34')).toBeFalse();
        expect(isValidPhone('(((1)))')).toBeFalse();
        expect(isValidPhone('+967 777-123-456', true)).toBeTrue();
    });

    it('allows at most 15 digits and rejects letters or an inner plus sign', () => {
        expect(isValidPhone('123456789012345')).toBeTrue();
        expect(isValidPhone('1234567890123456')).toBeFalse();
        expect(isValidPhone('777 123 45a', true)).toBeFalse();
        expect(isValidPhone('777+123456', true)).toBeFalse();
    });

    it('turns Arabic-Indic digits into Latin ones and trims', () => {
        expect(normalizePhone(' ٧٧٧١٢٣٤٥٦ ')).toBe('777123456');
        expect(normalizePhone('۰۱۲')).toBe('012');
        expect(isValidPhone('٧٧٧١٢٣٤٥٦', true)).toBeTrue();
    });
});
