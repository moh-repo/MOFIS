import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { StudentTravelTicketService } from './student-travel-ticket.service';

/**
 * The wire shape of the Travel Tickets page's list request: the two filters go
 * as query parameters of the collection URL, and only when they are set. The
 * endpoint's side of this contract is StudentTravelTicketEndpointsTest in the
 * backend; the filter bar did nothing until both sides agreed (24 Sep 2026).
 */
describe('StudentTravelTicketService list request', () => {
    let service: StudentTravelTicketService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()]
        });
        service = TestBed.inject(StudentTravelTicketService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('sends the student and travel type filters as query parameters', () => {
        service.search({ studentId: 1003, travelType: 'WITH_FAMILY' }).subscribe();

        const req = http.expectOne(r => r.url.endsWith('/api/v1/student-travel-tickets'));
        expect(req.request.method).toBe('GET');
        expect(req.request.params.get('studentId')).toBe('1003');
        expect(req.request.params.get('travelType')).toBe('WITH_FAMILY');
        req.flush([]);
    });

    it('sends no parameter for a filter that is not set', () => {
        service.search({ studentId: null, travelType: null }).subscribe();

        const req = http.expectOne(r => r.url.endsWith('/api/v1/student-travel-tickets'));
        expect(req.request.params.keys()).toEqual([]);
        req.flush([]);
    });
});
