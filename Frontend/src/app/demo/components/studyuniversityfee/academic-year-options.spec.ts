import { StudyUniversityFeeComponent } from './studyuniversityfee.component';

/**
 * Fee Management edit blanked the academic year (live QA #9).
 *
 * <p>The academic-years lookup holds single years; fee rows store the academic
 * year as a pair. No option matched a stored value, so the field opened empty and
 * saving it failed as "required".
 */
describe('the fee form academic year options', () => {

    const years = [{ label: '2023', value: '2023' }, { label: '2024', value: '2024' }];

    it('offers the years in the stored "2023/2024" shape', () => {
        expect(StudyUniversityFeeComponent.academicYearPairsFrom(years)).toEqual([
            { label: '2023/2024', value: '2023/2024' },
            { label: '2024/2025', value: '2024/2025' }
        ]);
    });

    it('matches a stored row, so edit shows its year', () => {
        const options = StudyUniversityFeeComponent.academicYearPairsFrom(years);
        const shown = StudyUniversityFeeComponent.withStoredValue(options, '2023/2024');

        expect(shown).toBe(options);
        expect(shown.some(o => o.value === '2023/2024')).toBeTrue();
    });

    it('keeps an older stored value the list does not offer, rather than blanking it', () => {
        const options = StudyUniversityFeeComponent.academicYearPairsFrom(years);

        expect(StudyUniversityFeeComponent.withStoredValue(options, '2021')[0])
            .toEqual({ label: '2021', value: '2021' });
        expect(StudyUniversityFeeComponent.withStoredValue(options, '2021/2022-2022/2023').length)
            .toBe(options.length + 1);
    });

    it('adds nothing for a new row', () => {
        const options = StudyUniversityFeeComponent.academicYearPairsFrom(years);

        expect(StudyUniversityFeeComponent.withStoredValue(options, null)).toBe(options);
        expect(StudyUniversityFeeComponent.withStoredValue(options, '  ')).toBe(options);
    });
});
