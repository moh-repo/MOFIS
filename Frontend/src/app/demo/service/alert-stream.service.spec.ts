import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';
import { AlertStreamService } from './alert-stream.service';
import { UmsAuthService } from './ums-auth.service';
import { CodeListService } from './code-list.service';
import { TranslationService } from './translation.service';

/**
 * The SSE stream authenticates with a short-lived single-use ticket minted by
 * an authenticated POST — never with the JWT in the URL, which used to land
 * bearer tokens in server access logs.
 */
describe('AlertStreamService', () => {
    class FakeEventSource {
        static instances: FakeEventSource[] = [];
        onerror: (() => void) | null = null;
        constructor(public url: string) {
            FakeEventSource.instances.push(this);
        }
        addEventListener(): void { /* not needed for these tests */ }
        close(): void { /* no-op */ }
    }

    let service: AlertStreamService;
    let httpMock: HttpTestingController;
    let originalEventSource: typeof EventSource;

    beforeEach(() => {
        originalEventSource = window.EventSource;
        (window as unknown as { EventSource: unknown }).EventSource = FakeEventSource;
        FakeEventSource.instances = [];

        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                AlertStreamService,
                { provide: UmsAuthService, useValue: { getToken: () => 'jwt-should-not-appear-in-url' } },
                { provide: MessageService, useValue: { add: () => undefined } },
                { provide: CodeListService, useValue: { label: () => '', severity: () => '' } },
                { provide: TranslationService, useValue: { translate: (k: string) => k, isRTL: () => false } }
            ]
        });
        service = TestBed.inject(AlertStreamService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        service.disconnect();
        (window as unknown as { EventSource: unknown }).EventSource = originalEventSource;
        httpMock.verify();
    });

    it('mints a ticket via POST and opens the stream with ?ticket=, never the JWT', () => {
        service.connect();

        const req = httpMock.expectOne(r => r.method === 'POST' && r.url.endsWith('/mohe/api/v1/alerts/stream/ticket'));
        req.flush({ ticket: 't-123' });

        expect(FakeEventSource.instances.length).toBe(1);
        expect(FakeEventSource.instances[0].url).toContain('/mohe/api/v1/alerts/stream?ticket=t-123');
        expect(FakeEventSource.instances[0].url).not.toContain('jwt-should-not-appear-in-url');
        expect(FakeEventSource.instances[0].url).not.toContain('token=');
    });

    it('does not open a stream when the ticket request fails', () => {
        service.connect();

        const req = httpMock.expectOne(r => r.method === 'POST' && r.url.endsWith('/mohe/api/v1/alerts/stream/ticket'));
        req.flush({ error: 'nope' }, { status: 401, statusText: 'Unauthorized' });

        expect(FakeEventSource.instances.length).toBe(0);
    });
});
