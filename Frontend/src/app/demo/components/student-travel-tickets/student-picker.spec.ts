import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Select } from 'primeng/select';
import { Subject, of } from 'rxjs';
import { StudentTravelTicketsComponent } from './student-travel-tickets.component';
import { StudentTravelTicketsModule } from './student-travel-tickets.module';
import { StudentOption, StudentPickerService } from '../../service/student-picker.service';
import { StudentTravelTicketService } from '../../service/student-travel-ticket.service';
import { LookupService } from '../../service/lookup.service';
import { TranslationService } from '../../service/translation.service';
import { CodeListService } from '../../service/code-list.service';
import { codeListStub } from '../../../testing/code-list.stub';

/**
 * Live QA M9 (25 Sep 2026): the Add Travel Ticket student list asked for 1000 students in
 * one page, which the server caps at 200, so 781 of 981 students could not be chosen. The
 * pickers now search on the server as the user types (`/studentdata/options`, the finance
 * screens' picker), and the listed tickets' students are named by id.
 */
describe('StudentTravelTickets — student picker (M9)', () => {

    const all: StudentOption[] = Array.from({ length: 981 }, (_, i) => ({ studentId: i + 1, studentName: `Student ${i + 1}` }));
    let urls: string[];

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const build = (): any => {
        urls = [];
        const http = {
            get: (url: string) => {
                urls.push(url);
                const search = /search=([^&]+)/.exec(url)?.[1];
                const ids = /ids=([^&]+)/.exec(url)?.[1]?.split(',').map(Number);
                const rows = ids ? all.filter(s => ids.includes(s.studentId))
                    : search ? all.filter(s => s.studentName.includes(decodeURIComponent(search))).slice(0, 50)
                    : all.slice(0, 50);
                return of(rows);
            }
        };
        const proto = StudentTravelTicketsComponent.prototype as any;
        const fb = new FormBuilder();
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const self: any = {
            studentPicker: new StudentPickerService(http as any),
            students: [],
            studentOptions: [],
            travelTickets: [],
            destroy$: new Subject<void>(),
            studentSearch$: new Subject<string>(),
            filtersForm: fb.group({ studentId: [null], travelType: [null] }),
            travelTicketForm: fb.group({ studentId: [null] }),
            lookupService: new Proxy({}, { get: () => () => of([]) }),
            travelTicketService: { getFundingSources: () => of([]), getWafarBalance: () => of(null) },
            permissionService: { hasAnyPermission: () => false },
            messageService: { add: () => undefined }
        };
        for (const m of ['holdStudents', 'onStudentFilter', 'nameStudentsOfTickets', 'getStudentName', 'loadWafarBalance']) {
            self[m] = proto[m].bind(self);
        }
        proto.loadMetadata.call(self);
        return self;
    };

    it('asks the picker, not the student list of 1000', () => {
        build();
        expect(urls.some(u => u.includes('/studentdata/options'))).toBeTrue();
        expect(urls.some(u => u.includes('size=1000') || u.includes('/studentall'))).toBeFalse();
    });

    it('finds a student beyond the first 200 by typing', fakeAsync(() => {
        const self = build();
        // What ngOnInit wires: a pause in typing runs one server search.
        const proto = StudentTravelTicketsComponent.prototype as any;
        self.studentSearch$ = new Subject<string>();
        proto.ngOnInit.call(Object.assign(self, {
            filterKey: () => '', applyFilters: () => undefined, loadLuTravelAndRelationship: () => undefined,
            loadMetadata: () => undefined, loadTravelTickets: () => undefined,
            translation: { currentLanguage$: of('en') }
        }));
        self.onStudentFilter({ filter: 'Student 950' });
        tick(300);
        expect(self.studentOptions.some((o: { id: number }) => o.id === 950)).toBeTrue();
        self.destroy$.next();
    }));

    it('names the students of the listed tickets, even outside the picker page', () => {
        const self = build();
        self.travelTickets = [{ studentId: 900 }, { studentId: 3 }];
        self.nameStudentsOfTickets();
        expect(self.getStudentName(900)).toBe('Student 900');
        expect(urls.filter(u => u.includes('ids=')).pop()).toContain('ids=900');
    });
});

/**
 * Live QA R5-30 (M9 residue): typing a student id into the Add dialog's picker showed
 * "No results found", although the server returned that student: the picker's own
 * filter matched the typed text against the name only. The options are now labelled
 * "id — name" and the pickers filter on that label.
 *
 * <p>Rendered, so the template's own Select settings are what is filtered with.
 */
describe('StudentTravelTickets — finding a student by id (R5-30)', () => {

    const all: StudentOption[] = Array.from({ length: 981 }, (_, i) => ({ studentId: i + 1, studentName: `Student ${i + 1}` }));
    let fixture: ComponentFixture<StudentTravelTicketsComponent>;

    beforeEach(async () => {
        // As /studentdata/options answers: a search matches the id as well as the name.
        const http = {
            get: (url: string) => {
                const search = decodeURIComponent(/search=([^&]+)/.exec(url)?.[1] ?? '');
                const rows = search
                    ? all.filter(s => String(s.studentId) === search || s.studentName.includes(search))
                    : all.slice(0, 50);
                return of(rows.slice(0, 50));
            }
        };
        await TestBed.configureTestingModule({
            imports: [StudentTravelTicketsModule],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: StudentPickerService, useValue: new StudentPickerService(http as any) },
                {
                    provide: StudentTravelTicketService,
                    useValue: {
                        search: () => of([]), getById: () => of({}), create: () => of({}), update: () => of({}),
                        delete: () => of({}), getFundingSources: () => of([]), getWafarBalance: () => of({ balance: 0 })
                    }
                },
                { provide: LookupService, useValue: new Proxy({} as any, { get: () => () => of([]) }) },
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
        fixture.componentInstance.modalVisible = false;
        fixture.detectChanges();
        fixture.nativeElement.remove();
    });

    const picker = (inputId: string): Select => {
        const select = fixture.debugElement.queryAll(By.directive(Select))
            .map(d => d.componentInstance as Select)
            .find(s => s.inputId === inputId);
        expect(select).withContext(`picker #${inputId}`).toBeTruthy();
        return select as Select;
    };

    /** Types into the picker's search box: the server search, then the Select's own filter. */
    const type = (inputId: string, text: string): Array<{ id: number; label: string }> => {
        fixture.componentInstance.onStudentFilter({ filter: text });
        tick(300); // the search debounce
        fixture.detectChanges();
        const select = picker(inputId) as any;
        select._filterValue.set(text);
        return select.visibleOptions();
    };

    for (const inputId of ['student', 'studentFilter']) {
        it(`#${inputId}: typing an id shows that student`, fakeAsync(() => {
            fixture.componentInstance.modalVisible = true;
            fixture.detectChanges();

            const shown = type(inputId, '950');

            expect(shown.map(o => o.id)).toContain(950);
            expect(shown.find(o => o.id === 950)?.label).toBe('950 — Student 950');
        }));

        it(`#${inputId}: typing a name still finds the student`, fakeAsync(() => {
            fixture.componentInstance.modalVisible = true;
            fixture.detectChanges();

            expect(type(inputId, 'Student 950').map(o => o.id)).toEqual([950]);
        }));
    }
});
