import { of } from 'rxjs';
import { StudentChangeRequestsComponent } from './student-change-requests.component';

/**
 * Live QA M7 / M8 (25 Sep 2026): Modify & approve offered 10 of 21 current levels, in
 * Arabic on the English screen. The server now sends the whole table when no page is
 * asked for; the screen names each row in the reader's language, still names a retired
 * row in an old value, and no longer offers it.
 */
describe('StudentChangeRequests — lookup names and options (M7, M8)', () => {

    const levels = Array.from({ length: 21 }, (_, i) => ({
        id: i + 1,
        studentCurrentLevelName: `مستوى ${i + 1}`,
        studentCurrentLevelNameEn: `Level ${i + 1}`,
        isActive: i + 1 !== 21
    }));

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const build = (lang: string): any => {
        const proto = StudentChangeRequestsComponent.prototype as any;
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const self: any = {
            currentLanguage: lang,
            http: { get: (url: string) => of(url.endsWith('/student-current-level') ? { content: levels } : { content: [] }) },
            permissionService: { getAllowedCountryIds: () => [] },
            codeLists: { extra: (_list: string, field: string) => field === 'studentCurrentLevelId' ? 'currentLevels' : null }
        };
        for (const m of ['lookupKeyFor', 'optionName', 'getLookupDropdownOptions', 'resolveValue']) {
            self[m] = proto[m].bind(self);
        }
        proto.loadLookups.call(self);
        return self;
    };

    it('offers every active level (20 of 21), in English on the English screen', () => {
        const options = build('en').getLookupDropdownOptions('studentCurrentLevelId');
        expect(options.length).toBe(20);
        expect(options[0]).toEqual({ label: 'Level 1', value: '1' });
    });

    it('offers the Arabic names on the Arabic screen', () => {
        expect(build('ar').getLookupDropdownOptions('studentCurrentLevelId')[0].label).toBe('مستوى 1');
    });

    it('still names a retired level in an old value, without offering it', () => {
        const self = build('en');
        expect(self.resolveValue('studentCurrentLevelId', '21')).toBe('Level 21');
        expect(self.getLookupDropdownOptions('studentCurrentLevelId').some((o: { value: string }) => o.value === '21')).toBeFalse();
    });
});
