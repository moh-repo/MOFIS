import { InLanguagePipe, localizeField, pickLanguage } from './in-language.pipe';
import { FinanceLookupsService } from '../service/finance-lookups.service';

/**
 * Pages printed the Arabic of a bilingual value in both languages: "Current Year:
 * السنة الثانية" beside "Current Level: Second Year" on an English profile (QA B1).
 */
describe('inLanguage', () => {

    const progress = { currentYearLabel: 'السنة الثانية', currentYearLabelEn: 'Second Year' };

    it('gives the English in English and the Arabic in Arabic', () => {
        expect(pickLanguage(progress, 'currentYearLabel', 'en')).toBe('Second Year');
        expect(pickLanguage(progress, 'currentYearLabel', 'ar')).toBe('السنة الثانية');
    });

    it('falls back to whichever it has', () => {
        expect(pickLanguage({ currentYearLabel: 'الأولى', currentYearLabelEn: '  ' }, 'currentYearLabel', 'en')).toBe('الأولى');
        expect(pickLanguage({ currentYearLabelEn: 'First Year' }, 'currentYearLabel', 'ar')).toBe('First Year');
        expect(pickLanguage(null, 'currentYearLabel', 'en')).toBe('');
    });

    it('follows a language switched in place', () => {
        let lang = 'ar';
        const pipe = new InLanguagePipe({ getCurrentLanguage: () => lang } as any);

        expect(pipe.transform(progress, 'currentYearLabel')).toBe('السنة الثانية');
        lang = 'en';
        expect(pipe.transform(progress, 'currentYearLabel')).toBe('Second Year');
    });
});

describe('wafar type and other finance names in the reader\u2019s language (QA B2, FIN-006)', () => {

    const types = [{ id: 1, wafarName: 'رسوم جامعية', wafarNameEn: 'University fees' }];

    it('localizes the option label and keeps the id', () => {
        const shown = localizeField(types, 'wafarName', 'en');
        expect(shown[0].wafarName).toBe('University fees');
        expect(shown[0].id).toBe(1);
        expect(types[0].wafarName).withContext('the source rows were changed in place').toBe('رسوم جامعية');
    });

    it('resolves a name by id in English on the finance view', () => {
        const lookups = new FinanceLookupsService({} as any, {} as any,
            { getCurrentLanguage: () => 'en', translate: (k: string) => k } as any);

        expect(lookups.resolveName(types, 1, 'wafarName')).toBe('University fees');
        expect(lookups.resolveName(types, 9, 'wafarName')).toBe('COMMON.NOT_SPECIFIED');
    });
});
