import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { StudentFinanceArchComponent } from './studentfinancearch.component';
import { StudentFinanceArchModule } from './studentfinancearch.module';
import { StudentDataArch } from '../../api/studentdataarch';
import { StudentDataArchService } from '../../service/studentdataarch.service';
import { StudentFinanceArchService } from '../../service/studentfinancearch.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';

/**
 * Both finance-archive dialogs have a gradient header; everything on it must be
 * white (reported 25 Sep 2026).
 *
 * <p>The close and maximise buttons were grey: the stylesheet still addressed
 * v17's .p-dialog-header-icon, which PrimeNG 21 no longer renders, so the
 * app-wide grey for secondary text buttons in styles.scss won. The view
 * dialog's header template also coloured its subtitle with text-500 (grey).
 *
 * <p>Computed colours, since the defect was stylesheets disagreeing. Karma
 * loads styles.scss, so the global rules are in force here as in the browser.
 */
describe('StudentFinanceArch dialog headers', () => {

    let fixture: ComponentFixture<StudentFinanceArchComponent>;

    const WHITE = 'rgb(255, 255, 255)';

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentFinanceArchModule],
            providers: [
                MessageService,
                // As in app.module.ts: dialogs land on <body>, outside the host.
                providePrimeNG({ overlayAppendTo: 'body' }),
                {
                    provide: StudentDataArchService,
                    useValue: { getDistinctStudentsPaginated: () => of({ content: [], totalElements: 0 }) }
                },
                { provide: StudentFinanceArchService, useValue: { getByStudentId: () => of([]) } },
                { provide: LookupService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentFinanceArchComponent);
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.componentInstance.viewDialog = false;
        fixture.componentInstance.feeDialog = false;
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

    it('view dialog: subtitle and header buttons are white on the gradient', async () => {
        const c = fixture.componentInstance;
        c.selectedStudentFinanceArch = Object.assign(new StudentDataArch(),
            { studentId: 1, studentName: 'Test Student', studentDelegationDecisionYear: 2024 });
        c.viewDialog = true;
        await settle();

        const dialog = document.querySelector('.p-dialog.sfa-view-dialog') as HTMLElement;
        expect(dialog).withContext('view dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog))
            .withContext('rendered outside the host, as in the app').toBeFalse();

        const sub = dialog.querySelector('.sfa-dialog-header__sub') as HTMLElement;
        expect(getComputedStyle(sub).color).withContext('subtitle').toBe('rgba(255, 255, 255, 0.85)');

        const { close, maximize } = headerButtons(dialog);
        expect(getComputedStyle(close).color).withContext('close').toBe(WHITE);
        expect(getComputedStyle(maximize).color).withContext('maximize').toBe(WHITE);
    });

    it('edit dialog: header buttons are white on the gradient', async () => {
        fixture.componentInstance.feeDialog = true;
        await settle();

        const dialog = document.querySelector('.p-dialog.sfa-edit-dialog') as HTMLElement;
        expect(dialog).withContext('edit dialog').toBeTruthy();
        const { close, maximize } = headerButtons(dialog);
        expect(getComputedStyle(close).color).withContext('close').toBe(WHITE);
        expect(getComputedStyle(maximize).color).withContext('maximize').toBe(WHITE);
    });
});
