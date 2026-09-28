import { AccessComponent } from './access.component';

/**
 * Live QA M4 (25 Sep 2026): "Logout & Go to Login" on the Access Denied page
 * cleared the browser only; the server session stayed valid. It now sends the
 * same logout the profile menu sends, with the stored token, before clearing.
 */
describe('AccessComponent — logout', () => {

    let component: AccessComponent;
    let fetchSpy: jasmine.Spy;
    let tokenAtFetch: string | null;

    beforeEach(() => {
        component = new AccessComponent();
        spyOn(component as any, 'go');
        tokenAtFetch = 'unset';
        fetchSpy = spyOn(window, 'fetch').and.callFake(() => {
            tokenAtFetch = localStorage.getItem('umsToken');
            return Promise.resolve(new Response(null, { status: 200 }));
        });
    });

    afterEach(() => {
        localStorage.removeItem('umsToken');
        sessionStorage.removeItem('umsToken');
    });

    it('ends the server session with the stored token, then clears it', () => {
        localStorage.setItem('umsToken', 'staff-token');

        component.logout();

        expect(fetchSpy).toHaveBeenCalledTimes(1);
        const [url, init] = fetchSpy.calls.mostRecent().args as [string, RequestInit];
        expect(url).toContain('/mohe/api/v1/ums/auth/logout');
        expect(init.method).toBe('POST');
        expect(init.keepalive).toBeTrue();
        expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer staff-token');
        expect(tokenAtFetch).toBe('staff-token');
        expect(localStorage.getItem('umsToken')).toBeNull();
        expect((component as any).go).toHaveBeenCalledWith('/#/auth/login');
    });

    it('finds a per-tab token too', () => {
        sessionStorage.setItem('umsToken', 'tab-token');
        component.logout();
        const init = fetchSpy.calls.mostRecent().args[1] as RequestInit;
        expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer tab-token');
    });

    it('sends nothing when no one is signed in', () => {
        component.logout();
        expect(fetchSpy).not.toHaveBeenCalled();
        expect((component as any).go).toHaveBeenCalledWith('/#/auth/login');
    });
});
