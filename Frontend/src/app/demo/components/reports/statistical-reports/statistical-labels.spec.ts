import { ComparativeStatisticsComponent } from './comparative-statistics/comparative-statistics.component';
import { GrowthAnalysisComponent } from './growth-analysis/growth-analysis.component';

/**
 * Live QA L1 (#63, #64, 25 Sep 2026): Comparative Statistics printed "null%" when the
 * previous period had no students, showed the comparison types as the codes `country`,
 * `degree` and the dimensions in Arabic on the English screen; Growth Analysis printed
 * "null" in its table and its statuses as `growing` / `declining`.
 */
describe('Comparative Statistics and Growth Analysis — labels (L1)', () => {

    const translations: Record<string, Record<string, string>> = {
        en: {
            'REPORTS.INDIVIDUAL_REPORTS.COMPARATIVE_STATISTICS.TYPES.COUNTRY': 'Study country',
            'REPORTS.INDIVIDUAL_REPORTS.GROWTH_STATUS_OPTIONS.GROWTH': 'Growth',
            'REPORTS.INDIVIDUAL_REPORTS.GROWTH_STATUS_OPTIONS.BASELINE': 'Base year',
            'REPORTS.INDIVIDUAL_REPORTS.GROWTH_ANALYSIS.CATEGORIES.TOTAL_STUDENTS': 'Total students'
        },
        ar: {
            'REPORTS.INDIVIDUAL_REPORTS.COMPARATIVE_STATISTICS.TYPES.COUNTRY': 'دولة الدراسة',
            'REPORTS.INDIVIDUAL_REPORTS.GROWTH_STATUS_OPTIONS.GROWTH': 'نمو',
            'REPORTS.INDIVIDUAL_REPORTS.GROWTH_STATUS_OPTIONS.BASELINE': 'سنة الأساس'
        }
    };
    const translationService = (lang: string) => ({
        getCurrentLanguage: () => lang,
        translate: (k: string) => translations[lang][k] ?? k
    });

    describe('Comparative Statistics', () => {
        const proto = ComparativeStatisticsComponent.prototype as any;
        const self = (lang: string) => ({ translationService: translationService(lang) });

        it('shows a dash, not "null%", when there is no previous period to compare with', () => {
            expect(proto.changeText.call(self('en'), null)).toBe('—');
            expect(proto.changeText.call(self('en'), 12.5)).toBe('12.5%');
            expect(proto.getChangeSeverity.call(self('en'), null)).toBe('secondary');
        });

        it('names the comparison type, and keeps an unknown one as it is', () => {
            expect(proto.comparisonTypeLabel.call(self('en'), 'country')).toBe('Study country');
            expect(proto.comparisonTypeLabel.call(self('ar'), 'country')).toBe('دولة الدراسة');
            expect(proto.comparisonTypeLabel.call(self('en'), 'gender')).toBe('gender');
        });

        it('names the dimension in the reader\'s language', () => {
            const row = { dimensionName: 'مصر', dimensionNameEn: 'Egypt' };
            expect(proto.dimensionLabel.call(self('en'), row)).toBe('Egypt');
            expect(proto.dimensionLabel.call(self('ar'), row)).toBe('مصر');
            expect(proto.dimensionLabel.call(self('en'), { dimensionName: 'مصر', dimensionNameEn: null })).toBe('مصر');
        });
    });

    describe('Growth Analysis', () => {
        const proto = GrowthAnalysisComponent.prototype as any;
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const page = (lang: string, statusOptions: any[] = []): any => {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const self: any = { translationService: translationService(lang), statusOptions };
            for (const m of ['growthStatusKey', 'growthStatusLabel', 'growthCategoryLabel']) {
                self[m] = proto[m].bind(self);
            }
            return self;
        };
        const rows = [
            { growthCategory: 'total_students', analysisYear: 2022, growthRate: null, growthStatus: 'stable' },
            { growthCategory: 'total_students', analysisYear: 2023, growthRate: 4, growthStatus: 'growing' }
        ];

        it('shows a dash for the first year\'s missing rate', () => {
            expect(proto.rateText.call(page('en'), null)).toBe('—');
            expect(proto.rateText.call(page('en'), 4)).toBe('4%');
        });

        it('names a status by the trend class the filter offers, else by the translation', () => {
            expect(page('ar', [{ label: 'نمو (جدول)', value: 'Growth' }]).growthStatusLabel('growing')).toBe('نمو (جدول)');
            expect(page('ar').growthStatusLabel('growing')).toBe('نمو');
            expect(page('en').growthCategoryLabel('total_students')).toBe('Total students');
        });

        it('filters the rows by the chosen trend class although the rows say "growing"', () => {
            const self = page('en', [{ label: 'Growth', value: 'Growth' }]);
            Object.assign(self, { growthData: rows, selectedCategory: '', selectedStatus: 'Growth' });
            proto.filterData.call(self);
            expect(self.filteredData.map((r: any) => r.analysisYear)).toEqual([2023]);
            expect(proto.getGrowthSeverity.call(self, 'growing')).toBe('success');
        });

        // FE-12: the server marks the first year of a series `baseline`.
        it('names the base year instead of showing the raw "baseline" code', () => {
            expect(page('en').growthStatusLabel('baseline')).toBe('Base year');
            expect(page('ar').growthStatusLabel('baseline')).toBe('سنة الأساس');
            expect(proto.getGrowthSeverity.call(page('en'), 'baseline')).toBe('secondary');
        });
    });
});
