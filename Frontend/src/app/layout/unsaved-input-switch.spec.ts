import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Confirmation, ConfirmationService } from 'primeng/api';

import { AppTopBarComponent } from './app.topbar.component';
import { PortalLayoutComponent } from '../student-portal/portal-layout/portal-layout.component';
import { UnsavedInputService } from '../demo/service/unsaved-input.service';

/**
 * A language or country switch no longer wipes typed input without a word.
 *
 * <p>A language switch rebuilds the routed page and a country switch re-navigates
 * to it, so the Add Student wizard, a filter or a half-written reason vanished
 * (live QA #4, N15). Decision DEC-07 (a): when the page holds input, ask first,
 * and on "stay" leave everything — the language, the selected country — as it was.
 */
describe('a switch that would wipe typed input', () => {

    describe('UnsavedInputService', () => {

        const service = new UnsavedInputService();

        function page(html: string): HTMLElement {
            const root = document.createElement('div');
            root.innerHTML = html;
            return root;
        }

        it('sees a changed control inside the page area', () => {
            const root = page('<div class="layout-main"><form class="ng-pristine"><input class="ng-dirty"></form></div>');
            expect(service.hasUnsavedInput(UnsavedInputService.STAFF_PAGE, root)).toBeTrue();
        });

        it('does not count an untouched page', () => {
            const root = page('<div class="layout-main"><input class="ng-pristine"></div>');
            expect(service.hasUnsavedInput(UnsavedInputService.STAFF_PAGE, root)).toBeFalse();
        });

        it('ignores the shell itself — the country dropdown in the topbar is not page input', () => {
            const root = page('<div class="layout-topbar"><p-select class="ng-dirty"></p-select></div>'
                + '<div class="layout-main"><input class="ng-pristine"></div>');
            expect(service.hasUnsavedInput(UnsavedInputService.STAFF_PAGE, root)).toBeFalse();
        });

        it('looks in the portal page area for the portal', () => {
            const root = page('<div class="portal-content-inner"><textarea class="ng-dirty"></textarea></div>');
            expect(service.hasUnsavedInput(UnsavedInputService.PORTAL_PAGE, root)).toBeTrue();
            expect(service.hasUnsavedInput(UnsavedInputService.STAFF_PAGE, root)).toBeFalse();
        });
    });

    describe('in the staff topbar', () => {

        let component: AppTopBarComponent;
        let dirty: boolean;
        let asked: Confirmation[];
        let switchedTo: string[];
        let selection: { selectedCountryId: number | null; setSelectedCountryId: (id: number | null) => void };
        let navigations: string[];

        beforeEach(() => {
            dirty = false;
            asked = [];
            switchedTo = [];
            navigations = [];
            selection = {
                selectedCountryId: 1,
                setSelectedCountryId(id) { this.selectedCountryId = id; }
            };
            TestBed.configureTestingModule({
                providers: [
                    ConfirmationService,
                    { provide: UnsavedInputService, useValue: { hasUnsavedInput: () => dirty } }
                ]
            });
            const confirmation = TestBed.inject(ConfirmationService);
            spyOn(confirmation, 'confirm').and.callFake((c: Confirmation) => {
                asked.push(c);
                return confirmation;
            });

            const quiet = () => new Proxy({} as any, { get: () => () => of([]) });
            const translation = {
                currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                isRTL: () => false, getCurrentLanguage: () => 'en',
                translate: (key: string) => key,
                switchLanguage: (code: string) => switchedTo.push(code)
            } as any;
            const router = {
                url: '/students',
                navigateByUrl: (u: string) => { navigations.push(u); return Promise.resolve(true); },
                navigate: () => Promise.resolve(true)
            } as any;

            component = TestBed.runInInjectionContext(() => new AppTopBarComponent(
                { config: {}, state: {}, onMenuToggle: () => undefined } as any,
                { reset: () => undefined } as any,
                router,
                translation,
                { getCurrentUser: () => ({ id: 1 }), logout: () => undefined } as any,
                new Proxy({} as any, { get: () => () => of({ content: [], totalElements: 0 }) }),
                new Proxy({} as any, { get: () => () => true }),
                { countryIds: [], isMinistryUser: true, getCurrentUser: () => ({ id: 1 }) } as any,
                selection as any,
                quiet(),
                { getSettings: () => of(null), getLogoUrl: () => '' } as any,
                quiet(),
                quiet(),
                { setLocale: () => undefined } as any
            ));
        });

        it('switches the language straight away when nothing is typed', () => {
            component.switchLanguage('ar');

            expect(asked.length).toBe(0);
            expect(switchedTo).toEqual(['ar']);
        });

        it('asks first when the page holds input, and switches only on "continue"', () => {
            dirty = true;
            component.switchLanguage('ar');

            expect(switchedTo).withContext('switched before the user answered').toEqual([]);
            expect(asked.length).toBe(1);
            expect(asked[0].key).withContext('must be the topbar dialog, not one a page hosts').toBe('unsaved-input');
            expect(asked[0].message).toBe('LAYOUT.UNSAVED_INPUT_MESSAGE');

            asked[0].accept!();
            expect(switchedTo).toEqual(['ar']);
        });

        it('does nothing for the language already in use', () => {
            dirty = true;
            component.switchLanguage('en');

            expect(asked.length).toBe(0);
            expect(switchedTo).toEqual([]);
        });

        it('on "stay", keeps the country that was selected', () => {
            dirty = true;
            // The dropdown has already taken the new value when onChange fires.
            component.selectedCountryId = 2;
            component.onCountryChange();

            expect(selection.selectedCountryId).withContext('changed before the user answered').toBe(1);
            asked[0].reject!();

            expect(component.selectedCountryId).toBe(1);
            expect(selection.selectedCountryId).toBe(1);
            expect(navigations).toEqual([]);
        });

        it('on "continue", applies the country and reloads the page', async () => {
            dirty = true;
            component.selectedCountryId = 2;
            component.onCountryChange();
            asked[0].accept!();
            await Promise.resolve();

            expect(selection.selectedCountryId).toBe(2);
            expect(navigations).toEqual(['/', '/students']);
        });
    });

    describe('in the student portal', () => {

        it('asks first when the page holds input, and switches only on OK', () => {
            const switchedTo: string[] = [];
            let options: any = null;
            const translation = {
                getAvailableLanguages: () => [], getCurrentLanguage: () => 'en',
                translate: (k: string) => k,
                switchLanguage: (code: string) => switchedTo.push(code)
            } as any;
            const modal = { confirm: (o: any) => { options = o; return {} as any; } } as any;
            let dirty = true;
            const component = new PortalLayoutComponent(
                {} as any, {} as any, translation, {} as any, {} as any, modal,
                { hasUnsavedInput: (selector: string) => dirty && selector === UnsavedInputService.PORTAL_PAGE } as any);

            component.switchLanguage('ar');
            expect(switchedTo).toEqual([]);
            expect(options.nzContent).toBe('LAYOUT.UNSAVED_INPUT_MESSAGE');
            options.nzOnOk();
            expect(switchedTo).toEqual(['ar']);

            options = null;
            dirty = false;
            component.switchLanguage('ar');
            expect(options).withContext('asked on an untouched page').toBeNull();
            expect(switchedTo).toEqual(['ar', 'ar']);
        });
    });
});
