import { BehaviorSubject } from 'rxjs';
import { MenuVariantService } from './menu-variant.service';
import { MenuVariant } from './menu-model';

/** Enough of UmsAuthService for the variant service to namespace its storage. */
class FakeAuth {
    currentUser$ = new BehaviorSubject<any>({ id: 7 });
    get currentUserValue() { return this.currentUser$.value; }
}

describe('MenuVariantService', () => {

    let auth: FakeAuth;

    const make = () => new MenuVariantService(auth as any);

    beforeEach(() => {
        auth = new FakeAuth();
        localStorage.clear();
    });

    afterEach(() => localStorage.clear());

    it('starts on the proposed layout when nothing is stored', () => {
        expect(make().variant).toBe('proposed');
    });

    it('remembers a switch to classic across a reload', () => {
        make().variant = 'classic';
        // A second instance stands in for the next page load.
        expect(make().variant).toBe('classic');
        expect(make().isProposed).toBe(false);
    });

    it('emits the new variant to whoever is rendering the sidebar', () => {
        const service = make();
        const seen: MenuVariant[] = [];
        service.variant$.subscribe((v) => seen.push(v));

        service.variant = 'classic';
        service.variant = 'proposed';

        expect(seen).toEqual(['proposed', 'classic', 'proposed']);
    });

    it('does not re-emit when the variant is set to what it already is', () => {
        const service = make();
        const seen: MenuVariant[] = [];
        service.variant$.subscribe((v) => seen.push(v));

        service.variant = 'proposed';

        expect(seen).toEqual(['proposed']);
    });

    it('keeps two people on the same browser apart', () => {
        make().variant = 'classic';

        auth.currentUser$.next({ id: 99 });
        expect(make().variant).toBe('proposed');

        auth.currentUser$.next({ id: 7 });
        expect(make().variant).toBe('classic');
    });

    it('picks up the stored choice when a user signs in', () => {
        localStorage.setItem('mohe.menuVariant.42', 'classic');
        const service = make();
        expect(service.variant).toBe('proposed');

        auth.currentUser$.next({ id: 42 });
        expect(service.variant).toBe('classic');
    });

    it('still switches when storage throws', () => {
        // A private window, or a browser set to block site data, throws on access
        // rather than returning null. Losing the preference is acceptable;
        // failing to render a sidebar is not.
        const getItem = spyOn(localStorage, 'getItem').and.throwError('denied');
        const setItem = spyOn(localStorage, 'setItem').and.throwError('denied');

        const service = make();
        expect(service.variant).toBe('proposed');

        expect(() => (service.variant = 'classic')).not.toThrow();
        expect(service.variant).toBe('classic');

        expect(getItem).toHaveBeenCalled();
        expect(setItem).toHaveBeenCalled();
    });

    it('ignores a stored value that is not a known layout', () => {
        localStorage.setItem('mohe.menuVariant.7', 'something-else');
        expect(make().variant).toBe('proposed');
    });
});
