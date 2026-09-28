import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { FinancialDataFormV2Service } from './financial-data-form-v2.service';
import { CountryFilterOption, relabelCountryOptions } from './financial-data-form.service';

describe('FinancialDataFormV2Service', () => {
    let service: FinancialDataFormV2Service;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()]
        });
        service = TestBed.inject(FinancialDataFormV2Service);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('asks the V2 endpoint for a quarter and year, carrying the country scope', () => {
        service.getByQuarterAndYear(2, 2024, 7).subscribe(rows => expect(rows.length).toBe(1));

        const req = http.expectOne(r => r.url.endsWith('/api/reports/financial-data-form-v2/by-quarter-year'));
        expect(req.request.method).toBe('GET');
        expect(req.request.params.get('quarter')).toBe('2');
        expect(req.request.params.get('year')).toBe('2024');
        expect(req.request.params.get('countryId')).toBe('7');
        req.flush([{ studentId: 1, studentName: 'x', entityName: 'y' }]);
    });

    it('omits countryId when no scope is selected', () => {
        service.getByCountryQuarterAndYear('مصر', 3, 2025, null).subscribe();

        const req = http.expectOne(r => r.url.endsWith('/by-country-quarter-year'));
        expect(req.request.params.get('country')).toBe('مصر');
        expect(req.request.params.has('countryId')).toBeFalse();
        req.flush([]);
    });

    it('loads the filter metadata', () => {
        service.getMetadata().subscribe(m => {
            expect(m.years).toEqual([2025, 2024]);
            expect(m.quarters).toEqual([1, 2]);
            expect(m.countries[0].value).toBe('مصر');
        });

        const req = http.expectOne(r => r.url.endsWith('/financial-data-form-v2/metadata'));
        req.flush({
            years: [2025, 2024], quarters: [1, 2],
            countries: [{ value: 'مصر', label: 'Egypt', labelEn: 'Egypt', labelAr: 'مصر' }]
        });
    });
});

// Live QA R5-29: the Country filter listed Arabic names on the English screen.
describe('relabelCountryOptions', () => {
    const options: CountryFilterOption[] = [
        { value: 'مصر', label: 'Egypt', labelEn: 'Egypt', labelAr: 'مصر' },
        { value: 'بلد قديم', label: 'بلد قديم', labelEn: '', labelAr: 'بلد قديم' }
    ];

    it('labels in Arabic after a switch to Arabic, and back in English, keeping the Arabic value', () => {
        const arabic = relabelCountryOptions(options, true);
        expect(arabic.map(o => o.label)).toEqual(['مصر', 'بلد قديم']);

        const english = relabelCountryOptions(arabic, false);
        expect(english[0].label).toBe('Egypt');
        expect(english.map(o => o.value)).toEqual(['مصر', 'بلد قديم']);
    });

    it('falls back to the value when a name is missing', () => {
        expect(relabelCountryOptions(options, false)[1].label).toBe('بلد قديم');
    });
});
