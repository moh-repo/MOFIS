import en from '../../assets/i18n/en.json';
import ar from '../../assets/i18n/ar.json';

/**
 * Live QA L7 (25 Sep 2026): the Data Import page told the user that rows land in
 * `MOHECORE.STUDENT_SCHOLARSHIP_DATA_IMPORT`. Database schema and table names are for
 * the people who run the database, not for the screens: no text shown to a user may
 * name one.
 */
describe('Screen text names no database schema (L7)', () => {

    const SCHEMA = /\b(MOHECORE|UMSCORE|STUDENTCORE|MOHENTracer)\b/;

    const offenders = (tree: unknown, path = ''): string[] => {
        if (typeof tree === 'string') {
            return SCHEMA.test(tree) ? [path] : [];
        }
        if (tree && typeof tree === 'object') {
            return Object.entries(tree as Record<string, unknown>)
                .flatMap(([k, v]) => offenders(v, path ? `${path}.${k}` : k));
        }
        return [];
    };

    it('in English', () => expect(offenders(en)).toEqual([]));
    it('in Arabic', () => expect(offenders(ar)).toEqual([]));
});
