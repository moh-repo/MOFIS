import { TravelTicketsComponent } from './travel-tickets.component';

/**
 * Live QA M17 (P22, 25 Sep 2026): the family members of a travel ticket showed their
 * relationship as the stored code, SPOUSE / CHILD, in both languages.
 */
describe('Portal travel tickets — family relationship names (M17)', () => {

    const keys: Record<string, Record<string, string>> = {
        ar: { 'STUDENT_TRAVEL_TICKETS.RELATIONSHIPS.SPOUSE': 'زوج/زوجة', 'STUDENT_TRAVEL_TICKETS.RELATIONSHIPS.CHILD': 'طفل' },
        en: { 'STUDENT_TRAVEL_TICKETS.RELATIONSHIPS.SPOUSE': 'Spouse', 'STUDENT_TRAVEL_TICKETS.RELATIONSHIPS.CHILD': 'Child' }
    };
    const label = (lang: string, code: string | null): string =>
        (TravelTicketsComponent.prototype as any).relationshipLabel.call(
            { translationService: { translate: (k: string) => keys[lang][k] ?? k } }, code);

    it('names SPOUSE and CHILD in the reader\'s language', () => {
        expect(label('ar', 'SPOUSE')).toBe('زوج/زوجة');
        expect(label('en', 'CHILD')).toBe('Child');
        expect(label('en', 'spouse')).toBe('Spouse');
    });

    it('shows a code with no name as itself, and a missing one as a dash', () => {
        expect(label('en', 'COUSIN')).toBe('COUSIN');
        expect(label('en', null)).toBe('-');
    });
});
