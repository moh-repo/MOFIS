import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService } from 'primeng/api';

import { AppTopBarComponent } from './app.topbar.component';

/**
 * What the account button opens.
 *
 * <p>It offered Profile, Settings and Logout. Settings went to a bare
 * `/settings`, which redirects to Lookup Tables — one of the seven settings
 * screens, and not an obvious destination for a menu item called Settings. All
 * seven are their own entries under Administration → Settings in the sidebar, so
 * the item duplicated the menu and landed somewhere arbitrary.
 *
 * <p>The component is built directly rather than rendered: the menu is a list of
 * `MenuItem`s the component hands to PrimeNG, which only renders the overlay once
 * the button is pressed. Rendering it would drag in the router, the translation
 * stack and half of PrimeNG to test none of them. Same reason as
 * app.menu.component.spec.ts.
 */

const quiet = () => new Proxy({} as any, { get: () => () => of([]) });

describe('the topbar account menu', () => {

    let component: AppTopBarComponent;
    let navigated: any[][];

    beforeEach(() => {
        navigated = [];
        // The topbar asks its own ConfirmationService before a switch wipes input (DEC-07).
        TestBed.configureTestingModule({ providers: [ConfirmationService] });

        const translation = {
            currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
            isRTL: () => false, getCurrentLanguage: () => 'en',
            translate: (key: string) => key
        } as any;

        // `inject(DestroyRef)` runs as a field initialiser, so construction needs
        // an injection context even though nothing here is rendered.
        component = TestBed.runInInjectionContext(() => new AppTopBarComponent(
            { config: {}, state: {}, onMenuToggle: () => undefined } as any,
            { reset: () => undefined } as any,
            { navigate: (c: any[]) => { navigated.push(c); return Promise.resolve(true); } } as any,
            translation,
            { getCurrentUser: () => ({ id: 1 }), logout: () => undefined } as any,
            new Proxy({} as any, { get: () => () => of({ content: [], totalElements: 0 }) }),
            // Permissive: the removed item appeared for anyone who could view
            // Settings or the dashboard, so this is the case where it would still
            // be there if it had survived.
            new Proxy({} as any, { get: () => () => true }),
            { countryIds: [], isMinistryUser: true, getCurrentUser: () => ({ id: 1 }) } as any,
            { selectedCountryId: null, selectedCountryId$: of(null) } as any,
            quiet(),
            { getSettings: () => of(null), getLogoUrl: () => '' } as any,
            quiet(),
            quiet(),
            { setLocale: () => undefined } as any
        ));

        component.updateProfileItems();
    });

    it('offers the account and the way out of it', () => {
        expect(component.profileItems.filter(i => !i.separator).map(i => i.label))
            .toEqual(['LAYOUT.PROFILE', 'LAYOUT.LOGOUT']);
    });

    it('does not offer Settings', () => {
        // Stated on its own, because the check above would also pass if the
        // whole menu were emptied.
        expect(component.profileItems.some(i => i.label === 'LAYOUT.SETTINGS'))
            .withContext('Settings is back in the account menu; the sidebar is where it lives')
            .toBeFalse();
    });

    it('still goes to the profile', () => {
        component.profileItems.find(i => i.label === 'LAYOUT.PROFILE')!.command!({} as any);
        expect(navigated).toEqual([['/profile']]);
    });
});
