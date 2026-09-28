import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PendingApprovalsComponent } from './pending-approvals.component';

/**
 * Round-5 QA R5-01: while the pending list answered 500, the table said "No pending
 * approval requests", so an approver was told there was nothing to do. A failed load now
 * says the list could not be loaded, with a Retry, and a later good load clears it.
 */
describe('PendingApprovals — a failed load is not an empty inbox', () => {

    /** Any service call answers an empty observable; translate answers its key. */
    const anything = () => new Proxy({} as any, {
        get: (_t, prop) => prop === 'translate' ? (k: string) => k : () => of([])
    });

    function screen(getPendingRequests: () => any): PendingApprovalsComponent {
        const approvalService = new Proxy({ getPendingRequests } as any, {
            get: (t, prop) => prop in t ? t[prop] : () => of([])
        });
        return TestBed.runInInjectionContext(() => new PendingApprovalsComponent(approvalService,
            anything(), anything(), anything(), anything(), anything(), anything(), anything(), anything()));
    }

    it('a 500 marks the load as failed and empties the table', () => {
        const component = screen(() => throwError(() => ({ status: 500 })));
        spyOn(console, 'error');
        component.loadRequests({ first: 0, rows: 10 });
        expect(component.loadFailed).toBeTrue();
        expect(component.requests).toEqual([]);
        expect(component.loading).toBeFalse();
    });

    it('a good load after it clears the failure', () => {
        let fail = true;
        const component = screen(() => fail
            ? throwError(() => ({ status: 500 }))
            : of({ content: [{ id: 1 }], totalElements: 1 }));
        spyOn(console, 'error');
        component.loadRequests({ first: 0, rows: 10 });
        fail = false;
        component.loadRequests({ first: 0, rows: 10 });
        expect(component.loadFailed).toBeFalse();
        expect(component.requests.length).toBe(1);
    });
});
