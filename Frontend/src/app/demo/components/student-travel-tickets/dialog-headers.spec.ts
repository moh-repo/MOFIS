import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { StudentTravelTicketsComponent } from './student-travel-tickets.component';
import { StudentTravelTicketsModule } from './student-travel-tickets.module';
import { StudentTravelTicketService } from '../../service/student-travel-ticket.service';
import { StudentPickerService } from '../../service/student-picker.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { codeListStub } from '../../../testing/code-list.stub';

/**
 * Both travel ticket dialogs have a gradient header; everything on it must be
 * white (reported 24 Sep 2026).
 *
 * <p>The close and maximise buttons were grey: the stylesheet still addressed
 * v17's .p-dialog-header-icon, which PrimeNG 21 no longer renders, so the
 * app-wide grey for secondary text buttons in styles.scss won. The view
 * dialog's header template also coloured its icon with text-primary
 * (primary on primary) and its subtitle with text-500 (grey).
 *
 * <p>Computed colours, since the defect was stylesheets disagreeing. Karma
 * loads styles.scss, so the global rules are in force here as in the browser.
 */
describe('StudentTravelTickets dialog headers', () => {

    let fixture: ComponentFixture<StudentTravelTicketsComponent>;

    const WHITE = 'rgb(255, 255, 255)';

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentTravelTicketsModule],
            providers: [
                MessageService,
                ConfirmationService,
                // As in app.module.ts: dialogs land on <body>, outside the host.
                providePrimeNG({ overlayAppendTo: 'body' }),
                {
                    provide: StudentTravelTicketService,
                    useValue: {
                        search: () => of([]),
                        getById: () => of({}),
                        create: () => of({}),
                        update: () => of({}),
                        delete: () => of({}),
                        getFundingSources: () => of([]),
                        getWafarBalance: () => of({ balance: 0 })
                    }
                },
                {
                    provide: StudentPickerService,
                    useValue: { search: () => of([]), byIds: () => of([]), merge: () => [], mergeAll: () => [], toOptions: () => [] }
                },
                {
                    provide: LookupService,
                    useValue: new Proxy({} as any, { get: () => () => of([]) })
                },
                { provide: CodeListService, useValue: codeListStub() },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentTravelTicketsComponent);
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.componentInstance.drawerVisible = false;
        fixture.componentInstance.modalVisible = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const settle = async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    const headerButtons = (dialog: HTMLElement) => ({
        close: dialog.querySelector('.p-dialog-close-button') as HTMLElement,
        maximize: dialog.querySelector('.p-dialog-maximize-button') as HTMLElement
    });

    it('view dialog: icon, subtitle and header buttons are white on the gradient', async () => {
        const c = fixture.componentInstance;
        c.viewingTicket = { travelTicketId: 7, studentId: 1 } as any;
        c.drawerVisible = true;
        await settle();

        const dialog = document.querySelector('.p-dialog.travel-ticket-view-dialog') as HTMLElement;
        expect(dialog).withContext('view dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog))
            .withContext('rendered outside the host, as in the app').toBeFalse();

        const icon = dialog.querySelector('.view-dialog-header__icon') as HTMLElement;
        const sub = dialog.querySelector('.view-dialog-header__sub') as HTMLElement;
        expect(getComputedStyle(icon).color).withContext('icon').toBe(WHITE);
        expect(getComputedStyle(sub).color).withContext('subtitle').toBe('rgba(255, 255, 255, 0.85)');

        const { close, maximize } = headerButtons(dialog);
        expect(getComputedStyle(close).color).withContext('close').toBe(WHITE);
        expect(getComputedStyle(maximize).color).withContext('maximize').toBe(WHITE);
    });

    it('edit dialog: header buttons are white on the gradient', async () => {
        fixture.componentInstance.modalVisible = true;
        await settle();

        const dialog = document.querySelector('.p-dialog.travel-ticket-dialog-compact') as HTMLElement;
        expect(dialog).withContext('edit dialog').toBeTruthy();
        const { close, maximize } = headerButtons(dialog);
        expect(getComputedStyle(close).color).withContext('close').toBe(WHITE);
        expect(getComputedStyle(maximize).color).withContext('maximize').toBe(WHITE);
    });
});
