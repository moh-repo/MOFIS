import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { StudentHistoryComponent } from './student-history.component';
import { StudentHistoryModule } from './student-history.module';
import { StudentDataArch } from '../../api/studentdataarch';
import { StudentDataArchService } from '../../service/studentdataarch.service';
import { StudentdataService } from '../../service/studentdata.service';
import { StudentDocumentService } from '../../service/student-document.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';

/**
 * The view dialog's hero photo and header buttons (reported 24 Sep 2026).
 *
 * <p>Photo: the rule that sized it lived under :host ::ng-deep in the page
 * stylesheet, but providePrimeNG appends every dialog to <body>, outside the
 * host, so it never applied and the photo rendered at its natural size. The
 * photo variant of the hero badge is now part of _detail-dialog.scss.
 *
 * <p>Buttons: PrimeNG renders close and maximise as secondary text buttons,
 * and the app-wide grey for those in styles.scss outranked the dialog's white,
 * so both icons were grey on the gradient header.
 *
 * <p>Geometry and computed colours rather than class checks, since both defects
 * were stylesheets disagreeing. Karma loads styles.scss, so the global rules
 * are in force here exactly as in the browser.
 */
describe('StudentHistory view dialog hero photo and header buttons', () => {

    let fixture: ComponentFixture<StudentHistoryComponent>;

    // 400 x 400: far larger than the badge, so an unconstrained photo shows.
    const bigPhoto = 'data:image/svg+xml;utf8,' +
        encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' width='400' height='400'><rect width='400' height='400' fill='#888'/></svg>");

    const emptyService = (overrides: Record<string, unknown> = {}) =>
        new Proxy(overrides, {
            get: (target, prop: string) =>
                prop in target ? (target as any)[prop] : () => of([])
        });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StudentHistoryModule],
            providers: [
                MessageService,
                ConfirmationService,
                // As in app.module.ts: dialogs land on <body>, outside the host.
                providePrimeNG({ overlayAppendTo: 'body' }),
                {
                    provide: StudentDataArchService,
                    useValue: emptyService({
                        getDistinctStudentsPaginated: () => of({ content: [], totalElements: 0 }),
                        getAllDistinctStudents: () => of([])
                    })
                },
                {
                    provide: StudentdataService,
                    useValue: emptyService({ getProgress: () => of(null), getYearResults: () => of([]) })
                },
                {
                    provide: StudentDocumentService,
                    useValue: emptyService({
                        isFormatLikeType: () => true,
                        getDocumentFormatLabelKey: () => 'STUDENTS.FORMAT_OTHER',
                        getDocumentFormatIcon: () => 'pi pi-file'
                    })
                },
                { provide: LookupService, useValue: emptyService() },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'),
                        initialized$: of(true),
                        isReady: () => true,
                        isRTL: () => false,
                        translate: (k: string) => k,
                        getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentHistoryComponent);
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();

        const c = fixture.componentInstance;
        c.newStudent = Object.assign(new StudentDataArch(), { studentId: 1, studentName: 'Test Student' });
        c.viewPhotoUrl = bigPhoto;
        c.viewDialog = true;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const img = document.querySelector('.dv-dialog .dv-hero__badge img') as HTMLImageElement;
        if (img && !img.complete) {
            await new Promise<void>(resolve => { img.onload = img.onerror = () => resolve(); });
        }
    });

    afterEach(() => {
        fixture.componentInstance.viewDialog = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const dialog = () => document.querySelector('.p-dialog.dv-dialog') as HTMLElement;

    it('renders the dialog outside the component host', () => {
        expect(dialog()).withContext('dialog').toBeTruthy();
        expect((fixture.nativeElement as HTMLElement).contains(dialog())).toBeFalse();
    });

    it('keeps the photo inside the hero badge instead of its natural size', () => {
        const badge = dialog().querySelector('.dv-hero__badge--photo') as HTMLElement;
        const img = badge.querySelector('img') as HTMLImageElement;
        expect(img.naturalWidth).withContext('the test photo loaded').toBe(400);

        const box = badge.getBoundingClientRect();
        const pic = img.getBoundingClientRect();
        expect(box.height).withContext('badge is the 4.25rem square').toBeLessThan(100);
        expect(Math.abs(box.width - box.height)).withContext('badge is square').toBeLessThan(2);
        expect(pic.width).withContext('photo fills the badge width').toBeCloseTo(box.width, 0);
        expect(pic.height).withContext('photo fills the badge height').toBeCloseTo(box.height, 0);
    });

    it('paints the close and maximise buttons white on the gradient header', () => {
        const close = dialog().querySelector('.p-dialog-close-button') as HTMLElement;
        const maximize = dialog().querySelector('.p-dialog-maximize-button') as HTMLElement;
        expect(close).withContext('close button').toBeTruthy();
        expect(maximize).withContext('maximize button').toBeTruthy();
        expect(getComputedStyle(close).color).toBe('rgb(255, 255, 255)');
        expect(getComputedStyle(maximize).color).toBe('rgb(255, 255, 255)');
    });
});
