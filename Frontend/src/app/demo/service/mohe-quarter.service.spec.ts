import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MoheQuarterCreateJob, MoheQuarterService } from './mohe-quarter.service';
import { SUPPRESS_ERROR_TOAST } from '../interceptors/http-context.tokens';

/**
 * W4-02 (review G1, Q4): the quarter page's background checks must not toast,
 * and one failed poll must not end the progress bar while the job runs on.
 */
describe('MoheQuarterService create jobs', () => {
    let service: MoheQuarterService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(MoheQuarterService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('asks for the running creation silently', () => {
        service.currentCreateJob().subscribe();
        const req = http.expectOne(r => r.url.endsWith('/create-jobs/current'));
        expect(req.request.context.get(SUPPRESS_ERROR_TOAST)).toBeTrue();
        req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('keeps following a job through a failed poll', fakeAsync(() => {
        const seen: MoheQuarterCreateJob[] = [];
        let failed = false;
        service.watchCreateJob('j1').subscribe({ next: j => seen.push(j), error: () => failed = true });

        tick(0);
        const first = http.expectOne(r => r.url.endsWith('/create-jobs/j1'));
        expect(first.request.context.get(SUPPRESS_ERROR_TOAST)).toBeTrue();
        first.flush({ message: 'gateway' }, { status: 502, statusText: 'Bad Gateway' });

        tick(2000);
        http.expectOne(r => r.url.endsWith('/create-jobs/j1'))
            .flush({ jobId: 'j1', state: 'DONE', total: 2, done: 2 });

        expect(failed).toBeFalse();
        expect(seen.map(j => j.state)).toEqual(['DONE']);
    }));

    it('gives up after the retries, so the page can say progress was lost', fakeAsync(() => {
        let failed = false;
        service.watchCreateJob('j2').subscribe({ error: () => failed = true });

        tick(0);
        for (let attempt = 0; attempt <= MoheQuarterService.POLL_RETRIES; attempt++) {
            http.expectOne(r => r.url.endsWith('/create-jobs/j2'))
                .flush({}, { status: 503, statusText: 'Unavailable' });
            tick(2000);
        }
        expect(failed).toBeTrue();
        http.match(() => true).forEach(r => r.flush({ jobId: 'j2', state: 'DONE', total: 0, done: 0 }));
    }));
});
