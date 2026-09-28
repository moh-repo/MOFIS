import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { StudyFinanceBankBranchService } from './study-finance-bank-branch.service';
import { StudyFinanceBankBranch } from '../api/study-finance-bank-branch';
import { SUPPRESS_ERROR_TOAST } from '../interceptors/http-context.tokens';
import { environment } from 'src/environments/environment';

/**
 * The branch writes are silent: the page's own toast says why one failed, and
 * the interceptor's generic toast used to stack on top of it. The list read
 * keeps the interceptor's toast, since the page adds only a generic one there.
 */
describe('StudyFinanceBankBranchService', () => {
    let service: StudyFinanceBankBranchService;
    let httpMock: HttpTestingController;
    const base = `${environment.apiUrl}/mohe/api/v1/studyFinanceBankBranch`;
    const body = {} as StudyFinanceBankBranch;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(StudyFinanceBankBranchService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('create, update and delete opt out of the interceptor toast', () => {
        service.create(body).subscribe();
        service.update(20, body).subscribe();
        service.delete(20).subscribe();

        for (const [method, url] of [['POST', `${base}/`], ['PUT', `${base}/20`], ['DELETE', `${base}/20`]]) {
            const req = httpMock.expectOne(r => r.method === method && r.url === url);
            expect(req.request.context.get(SUPPRESS_ERROR_TOAST)).withContext(method).toBeTrue();
            req.flush({});
        }
    });

    it('the list read does not', () => {
        service.getAll().subscribe();

        const req = httpMock.expectOne(`${base}/`);
        expect(req.request.context.get(SUPPRESS_ERROR_TOAST)).toBeFalse();
        req.flush([]);
    });
});
