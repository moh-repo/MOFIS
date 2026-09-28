import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApprovalRequest } from '../../../service/approval.service';
import { MyRequestsComponent } from './my-requests.component';

/**
 * Round-5 QA R5-16: a Finance Officer raised requests but could not take one back. My
 * requests now offers Withdraw on a request still waiting on a level, and calls the
 * requester-only POST /approvals/{id}/withdraw.
 */
describe('MyRequests — the requester withdraws (R5-16)', () => {

    /** Any service call answers an empty observable; translate answers its key. */
    const anything = () => new Proxy({} as any, {
        get: (_t, prop) => prop === 'translate' ? (k: string) => k : () => of([])
    });

    function screen(withdrawRequest: jasmine.Spy) {
        const getMyRequests = jasmine.createSpy('getMyRequests').and.returnValue(of({ content: [], totalElements: 0 }));
        const approvalService = new Proxy({ withdrawRequest, getMyRequests } as any, {
            get: (t, prop) => prop in t ? t[prop] : () => of([])
        });
        const messages = { add: jasmine.createSpy('add') };
        const component = TestBed.runInInjectionContext(() => new MyRequestsComponent(approvalService,
            anything(), anything(), anything(), anything(), anything(), messages as any));
        return { component, messages, getMyRequests };
    }

    const request = (status: ApprovalRequest['status']) => ({ id: 21823, status } as ApprovalRequest);

    it('is offered only while the request waits on a level', () => {
        const { component } = screen(jasmine.createSpy('withdrawRequest'));
        expect(component.canWithdraw(request('PENDING'))).toBeTrue();
        expect(component.canWithdraw(request('IN_PROGRESS'))).toBeTrue();
        expect(component.canWithdraw(request('APPROVED'))).toBeFalse();
        expect(component.canWithdraw(request('REJECTED'))).toBeFalse();
        expect(component.canWithdraw(request('EXECUTED'))).toBeFalse();
    });

    it('withdraws with the reason, says so, and reloads the list', () => {
        const withdraw = jasmine.createSpy('withdrawRequest').and.returnValue(of({}));
        const { component, messages, getMyRequests } = screen(withdraw);
        component.openWithdraw(request('PENDING'));
        component.withdrawReason = 'wrong amount';
        component.confirmWithdraw();
        expect(withdraw).toHaveBeenCalledWith(21823, 'wrong amount');
        expect(messages.add).toHaveBeenCalledWith(jasmine.objectContaining({ severity: 'success', detail: 'APPROVAL.requestWithdrawn' }));
        expect(component.showWithdrawDialog).toBeFalse();
        expect(getMyRequests).toHaveBeenCalled();
    });

    it('shows the server\'s refusal and keeps the dialog open', () => {
        const withdraw = jasmine.createSpy('withdrawRequest').and.returnValue(
            throwError(() => ({ status: 409, error: { message: 'Request is already EXECUTED' } })));
        const { component, messages } = screen(withdraw);
        component.openWithdraw(request('PENDING'));
        component.confirmWithdraw();
        expect(messages.add).toHaveBeenCalledWith(jasmine.objectContaining({ severity: 'error' }));
        expect(component.showWithdrawDialog).toBeTrue();
        expect(component.withdrawing).toBeFalse();
    });
});
