import { LOOKUP_DESCRIPTORS, describeLookup } from './lookup-descriptors';

/**
 * FIX-PLAN P5-6 (26 Sep 2026): report captions may have no English text (LBL_YEAR_SUFFIX
 * is "م" after an Arabic year and nothing after an English one). The server lets the
 * report-label table, and only that table, leave the English name blank
 * (@EnglishNameOptional); the form asks the same.
 */
describe('Lookup tables — an optional English name, on report labels only', () => {

    const required = (id: string, key: string) =>
        describeLookup(id)!.fields.find(f => f.key === key)!.required;

    it('report labels need the Arabic name only', () => {
        expect(required('report-labels', 'nameAr')).toBeTrue();
        expect(required('report-labels', 'nameEn')).toBeFalse();
    });

    it('every other bilingual table still needs both names', () => {
        const others = LOOKUP_DESCRIPTORS.filter(d => d.bilingual && d.id !== 'report-labels');
        expect(others.length).toBeGreaterThan(5);
        for (const d of others) {
            expect(d.fields.find(f => f.key === 'nameEn')?.required).withContext(d.id).toBeTrue();
        }
    });

    // Live QA R5-24: the server requires both names on these tables too (M11, D20-a),
    // but the form left their English name unmarked.
    it('the 17 standard tables need both names', () => {
        const standard = LOOKUP_DESCRIPTORS.filter(d => !d.bilingual);
        expect(standard.length).toBe(17);
        for (const d of standard) {
            const names = d.fields.filter(f => f.kind === 'text' && /Name(En)?$/.test(f.key));
            expect(names.length).withContext(d.id).toBe(2);
            for (const f of names) {
                expect(f.required).withContext(`${d.id}.${f.key}`).toBeTrue();
            }
        }
    });
});
