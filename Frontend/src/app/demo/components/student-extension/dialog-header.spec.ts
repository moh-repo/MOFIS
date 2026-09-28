import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { StudentExtensionComponent } from './student-extension.component';
import { StudentExtensionModule } from './student-extension.module';
import { StudentExtensionService } from '../../service/student-extension.service';
import { TranslationService } from '../../service/translation.service';

/**
 * The extension edit dialog has a gradient header; its close and maximise
 * buttons must be white on it (reported 24 Sep 2026).
 *
 * <p>They were grey: the stylesheet still addressed v17's
 * .p-dialog-header-icon, which PrimeNG 21 no longer renders, so the app-wide
 * grey for secondary text buttons in styles.scss won. The view dialog uses the
 * shared dv-dialog vocabulary and is covered with it.
 *
 * <p>Computed colours, since the defect was stylesheets disagreeing. Karma
 * loads styles.scss, so the global rule is in force here as in the browser.
 */
describe('StudentExtension edit dialog header', () => {

    let fixture: ComponentFixture<StudentExtensionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentExtensionModule],
            providers: [
                MessageService,
                ConfirmationService,
                // As in app.module.ts: dialogs land on <body>, outside the host.
                providePrimeNG({ overlayAppendTo: 'body' }),
                {
                    provide: StudentExtensionService,
                    useValue: {
                        getMetadata: () => of({ students: [], extensionTypes: [], statuses: [] }),
                        search: () => of([]),
                        create: () => of({}),
                        update: () => of({}),
                        delete: () => of({})
                    }
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentExtensionComponent);
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.componentInstance.modalVisible = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    it('paints the close and maximise buttons white on the gradient', async () => {
        fixture.componentInstance.modalVisible = true;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const dialog = document.querySelector('.p-dialog.extension-dialog-compact') as HTMLElement;
        expect(dialog).withContext('edit dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog))
            .withContext('rendered outside the host, as in the app').toBeFalse();

        const close = dialog.querySelector('.p-dialog-close-button') as HTMLElement;
        const maximize = dialog.querySelector('.p-dialog-maximize-button') as HTMLElement;
        expect(getComputedStyle(close).color).withContext('close').toBe('rgb(255, 255, 255)');
        expect(getComputedStyle(maximize).color).withContext('maximize').toBe('rgb(255, 255, 255)');
    });
});
