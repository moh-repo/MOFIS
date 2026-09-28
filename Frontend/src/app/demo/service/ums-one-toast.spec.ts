import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { UmsGroupService } from './ums-group.service';
import { UmsRoleService } from './ums-role.service';
import { UmsUserService } from './ums-user.service';
import { SUPPRESS_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { extractApiErrorMessage } from './api-error.util';

/**
 * One toast per error on the administration screens (live QA #18, review M10).
 *
 * <p>Every UMS page reports a failed write itself, and the interceptor reported it
 * too: two toasts, and the page's one never carried the server's reason, because
 * it read `error` (Spring's "Bad Request") instead of `message`.
 */
describe('UMS writes report their failure once', () => {

    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    const silentOf = (urlPart: string) => {
        const req = http.expectOne(r => r.url.includes(urlPart));
        const silent = req.request.context.get(SUPPRESS_ERROR_TOAST);
        req.flush({});
        return { silent, req };
    };

    it('marks the writes the pages report themselves', () => {
        const users = TestBed.inject(UmsUserService);
        const groups = TestBed.inject(UmsGroupService);
        const roles = TestBed.inject(UmsRoleService);

        users.deleteUser(5).subscribe();
        expect(silentOf('/users/5').silent).withContext('deleteUser').toBeTrue();

        users.grantPermissionsToUser(5, [1], 'r', '2026-12-31').subscribe();
        expect(silentOf('/permissions/batch').silent).withContext('grantPermissionsToUser').toBeTrue();

        users.revokePermissionsFromUser(5, [1]).subscribe();
        expect(silentOf('/permissions/batch').silent).withContext('revokePermissionsFromUser').toBeTrue();

        groups.assignRoleToGroup(3, 2).subscribe();
        expect(silentOf('/groups/3/roles/2').silent).withContext('assignRoleToGroup').toBeTrue();

        roles.setRolePermissions(7, [], true).subscribe();
        const { silent, req } = silentOf('/roles/7/permissions');
        expect(silent).withContext('setRolePermissions').toBeTrue();
        expect(req.request.params.get('clearAll')).withContext('clearAll kept alongside the context').toBe('true');
    });

    it('leaves reads to the interceptor, which is the only one to report them', () => {
        TestBed.inject(UmsRoleService).getAllActiveRoles().subscribe();
        expect(silentOf('/roles').silent).toBeFalse();
    });

    it('reads the server’s message, not Spring’s "Bad Request"', () => {
        const refused = { status: 400, error: { error: 'Bad Request', message: 'The group name is already in use.' } };

        expect(extractApiErrorMessage(refused)).toBe('The group name is already in use.');
    });
});
