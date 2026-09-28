import { Subject, of, throwError } from 'rxjs';
import { UserViewComponent } from './user-view/user-view.component';
import { EffectivePermissionsComponent } from './effective-permissions/effective-permissions.component';
import { PaymentAuthReportComponent } from '../payment-authorization/payment-auth-report/payment-auth-report.component';

/**
 * Live QA L2 and L3 (25 Sep 2026).
 * L3 (#65, #66): a staff account or a payment voucher with a bad id stayed on an empty
 * page under "Record not found" toasts; every other screen goes to the not-found page.
 * L2 (#67): leaving Effective Permissions within about a second of opening it pulled
 * the browser back, because the page's reads outlived it and then updated its URL.
 */
describe('Bad ids and leaving a page (L2, L3)', () => {

    /** A DestroyRef the test can fire. */
    const destroyRef = () => {
        const callbacks: Array<() => void> = [];
        return {
            destroyed: false,
            onDestroy: (fn: () => void) => { callbacks.push(fn); return () => undefined; },
            destroy() { this.destroyed = true; callbacks.forEach(fn => fn()); }
        };
    };

    describe('the staff account page', () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const page = (userId: number | null, getUserById: () => any): any => {
            const router = jasmine.createSpyObj('Router', ['navigate']);
            const userService = {
                getUserById: jasmine.createSpy('getUserById').and.callFake(getUserById),
                getUserRoles: jasmine.createSpy('getUserRoles').and.returnValue(of([])),
                getUserGroups: jasmine.createSpy('getUserGroups').and.returnValue(of([])),
                getMfaStatus: jasmine.createSpy('getMfaStatus').and.returnValue(of(null))
            };
            return { userId, router, userService, http: { get: () => of([]) }, destroyRef: destroyRef(), message: jasmine.createSpyObj('Toast', ['error', 'warning']), t: (k: string) => k };
        };
        const load = (self: unknown) => (UserViewComponent.prototype as any).load.call(self);

        it('sends an id that is not a number to the not-found page', () => {
            const self = page(NaN, () => of({}));
            load(self);
            expect(self.router.navigate).toHaveBeenCalledWith(['/notfound'], { replaceUrl: true });
            expect(self.userService.getUserById).not.toHaveBeenCalled();
        });

        it('sends an id no account has to the not-found page, without the other reads', () => {
            const self = page(999999, () => throwError(() => ({ status: 404 })));
            load(self);
            expect(self.router.navigate).toHaveBeenCalledWith(['/notfound'], { replaceUrl: true });
            expect(self.userService.getUserRoles).not.toHaveBeenCalled();
            expect(self.message.error).not.toHaveBeenCalled();
        });

        it('still reads the rest for an account that exists', () => {
            const self = page(1, () => of({ id: 1, userName: 'jo_staff' }));
            load(self);
            expect(self.router.navigate).not.toHaveBeenCalled();
            expect(self.userService.getUserRoles).toHaveBeenCalledWith(1);
            expect(self.user?.userName).toBe('jo_staff');
        });
    });

    describe('the payment voucher', () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const voucher = (answer: () => any): any => ({
            recordId: 999999,
            router: jasmine.createSpyObj('Router', ['navigate']),
            service: { getById: () => answer() },
            messageService: jasmine.createSpyObj('MessageService', ['add']),
            translationService: { translate: (k: string) => k },
            showDocument: jasmine.createSpy('showDocument')
        });
        const loadRecord = (self: unknown, id: number) => (PaymentAuthReportComponent.prototype as any).loadRecord.call(self, id);

        it('goes to the not-found page for a voucher that does not exist, and asks for no document', () => {
            const self = voucher(() => throwError(() => ({ status: 404 })));
            loadRecord(self, 999999);
            expect(self.router.navigate).toHaveBeenCalledWith(['/notfound'], { replaceUrl: true });
            expect(self.showDocument).not.toHaveBeenCalled();
            expect(self.messageService.add).not.toHaveBeenCalled();
        });

        it('shows the document once the voucher is found', () => {
            const self = voucher(() => of({ id: 5, authFormNumber: 'PA-5' }));
            loadRecord(self, 5);
            expect(self.showDocument).toHaveBeenCalledTimes(1);
            expect(self.record.authFormNumber).toBe('PA-5');
        });
    });

    describe('Effective Permissions', () => {
        it('does not pull the browser back when its answer arrives after the page was left', () => {
            const report$ = new Subject<unknown>();
            const ref = destroyRef();
            const self = {
                selectedUserId: 7,
                destroyRef: ref,
                router: jasmine.createSpyObj('Router', ['navigate']),
                route: {},
                userService: {
                    getEffectivePermissions: () => report$,
                    getDirectGrantExceptions: () => of([]),
                    getSeparationOfDuties: () => of(null)
                },
                loadDiff: jasmine.createSpy('loadDiff'),
                toast: jasmine.createSpyObj('Toast', ['error']),
                translation: { translate: (k: string) => k }
            };
            (EffectivePermissionsComponent.prototype as any).load.call(self);

            ref.destroy();              // the user went elsewhere
            report$.next({ permissions: [] });
            report$.complete();

            expect(self.router.navigate).not.toHaveBeenCalled();
            expect(self.loadDiff).not.toHaveBeenCalled();
        });
    });
});
