import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MessageService } from 'primeng/api';
import { StudentHistoryComponent } from './student-history.component';
import { StudentHistoryModule } from './student-history.module';
import { StudentDataArchService } from '../../service/studentdataarch.service';
import { StudentdataService } from '../../service/studentdata.service';
import { StudentDocumentService } from '../../service/student-document.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';

/**
 * Student History is a read-only archive with a table that keeps its page
 * (QA B7: STD-004, STD-005, STD-006; decision DEC-08 a).
 *
 * <p>Live on 22 Sep: Add, Edit and Delete were offered on quarterly snapshots;
 * the ID column showed the archive row's id, which "search by id" does not
 * search; the quarter was "-" and the gender "0" on nearly every row; and a click
 * on page 2 or a header was undone at once, because every load destroyed the
 * table and the rebuilt one asked for page 1.
 */
describe('StudentHistory as a read-only archive', () => {

    let fixture: ComponentFixture<StudentHistoryComponent>;
    let component: StudentHistoryComponent;
    let requests: any[][];

    const row = {
        id: 1880, studentId: 939, studentName: 'Layla', studentMajorName: 'Law',
        currentQuarter: '20242', studentAmount: 100, studentGender: 0, archiveDate: '2026-01-01T00:00:00Z'
    };

    const emptyService = (overrides: Record<string, unknown> = {}) =>
        new Proxy(overrides, {
            get: (target, prop: string) => prop in target ? (target as any)[prop] : () => of([])
        });

    beforeEach(async () => {
        requests = [];
        await TestBed.configureTestingModule({
            imports: [StudentHistoryModule],
            providers: [
                MessageService,
                {
                    provide: StudentDataArchService,
                    useValue: emptyService({
                        getDistinctStudentsPaginated: (...args: any[]) => {
                            requests.push(args);
                            return of({ content: [row], totalElements: 45 });
                        }
                    })
                },
                { provide: StudentdataService, useValue: emptyService({ getProgress: () => of(null), getYearResults: () => of([]) }) },
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
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => false, translate: (k: string) => k, getCurrentLanguage: () => 'en'
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(StudentHistoryComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        await new Promise(resolve => setTimeout(resolve));
        fixture.detectChanges();
    });

    const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

    it('offers no Add, Edit or Delete', () => {
        const el = fixture.nativeElement as HTMLElement;
        expect(el.querySelector('.pi-plus')).withContext('Add is back').toBeNull();
        expect(el.querySelector('.pi-pencil')).withContext('Edit is back').toBeNull();
        expect(el.querySelector('.pi-trash')).withContext('Delete is back').toBeNull();
        expect(el.querySelector('.pi-eye')).withContext('View must stay').not.toBeNull();
    });

    it('shows the student id, the snapshot quarter and a gender word', () => {
        const cells = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr td'))
            .map(td => (td.textContent ?? '').trim());

        expect(cells[0]).withContext('the archive row id is not what "search by id" searches').toBe('939');
        expect(cells).toContain('2024 COMMON.QUARTER_SHORT2');
        expect(cells).toContain('COMMON.UNSPECIFIED');
        expect(text()).not.toContain('1880');
    });

    it('keeps the table on screen while a page loads', () => {
        component.loading = true;
        fixture.detectChanges();

        expect((fixture.nativeElement as HTMLElement).querySelector('p-table'))
            .withContext('the table was swapped for a spinner, which reset its page').not.toBeNull();
    });

    it('keeps the filter boxes usable while a page loads', () => {
        component.loading = true;
        component.isLoadingData = true;
        fixture.detectChanges();

        const inputs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLInputElement>('.filter-bar input'));
        expect(inputs.length).toBe(3);
        expect(inputs.every(i => !i.disabled)).withContext('typing lost focus on every load').toBeTrue();
    });

    it('goes to page 2 and stays there', () => {
        const before = requests.length;

        component.onPageChange({ first: 20, rows: 20, sortField: 'id', sortOrder: -1 });
        fixture.detectChanges();

        expect(requests.length).toBe(before + 1);
        expect(requests[requests.length - 1][0]).withContext('page index sent').toBe(1);
        expect(component.first).toBe(20);

        // The table announces the state it was given; that must not send page 1.
        component.onPageChange({ first: 20, rows: 20, sortField: 'id', sortOrder: -1 });
        expect(requests.length).withContext('the same page was fetched again').toBe(before + 1);
    });

    it('formats quarter codes, and leaves anything else as it is', () => {
        expect(component.quarterLabel('20253')).toBe('2025 COMMON.QUARTER_SHORT3');
        expect(component.quarterLabel(null)).toBe('');
        expect(component.quarterLabel('2025')).toBe('2025');
    });
});
