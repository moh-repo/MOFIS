import { TestBed } from '@angular/core/testing';
import { Router, Routes } from '@angular/router';
import { provideLocationMocks } from '@angular/common/testing';
import { AppRoutingModule } from 'src/app/app-routing.module';
import { DASHBOARD_SECTION_CODES } from '../../service/dashboard.service';

/**
 * DASHBOARD_SECTION_CODES is the one TypeScript list of dashboard sections.
 * The home route's `pageCodes` in app-routing.module.ts has to stay a literal
 * (the menu validator reads the file as text), so this pins the two equal.
 */
describe('dashboard section codes', () => {

    it('match the home route\'s pageCodes guard list', () => {
        TestBed.configureTestingModule({
            imports: [AppRoutingModule],
            providers: [provideLocationMocks()]
        });
        const config = TestBed.inject(Router).config as Routes;
        const shell = config.find(r => r.path === '' && r.children?.length);
        const home = shell?.children?.find(r => r.path === '');

        expect(home).withContext('home route under the layout shell').toBeDefined();
        expect(home!.data?.['pageCodes']).toEqual(['DASHBOARD', ...DASHBOARD_SECTION_CODES]);
    });
});
