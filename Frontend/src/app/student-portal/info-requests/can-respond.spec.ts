import { InfoRequestsComponent } from './info-requests.component';

/**
 * When the portal offers the answer form (live QA M15, 25 Sep 2026).
 *
 * A cancelled request whose earlier answer had been rejected counted as answerable,
 * so the student could answer it and reopen it. The server now refuses that; the form
 * follows the same rule.
 */
describe('InfoRequestsComponent canRespond', () => {

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const can = (status: string, reviewStatus?: string) =>
        InfoRequestsComponent.prototype.canRespond.call({ selected: { status, reviewStatus } } as any);

    it('offers the form on an open or overdue request', () => {
        expect(can('PENDING')).toBeTrue();
        expect(can('OVERDUE')).toBeTrue();
    });

    it('offers it again when the answer was sent back', () => {
        expect(can('RESPONDED', 'REJECTED')).toBeTrue();
    });

    it('does not offer it on a cancelled request, even one whose answer was rejected', () => {
        expect(can('CANCELLED')).toBeFalse();
        expect(can('CANCELLED', 'REJECTED')).toBeFalse();
    });

    it('does not offer it while the answer is under review or once approved', () => {
        expect(can('RESPONDED', 'PENDING_REVIEW')).toBeFalse();
        expect(can('RESPONDED', 'APPROVED')).toBeFalse();
    });
});
