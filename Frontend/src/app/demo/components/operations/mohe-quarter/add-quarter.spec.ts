import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { MoheQuarterComponent } from './mohe-quarter.component';
import { OperationsModule } from '../operations.module';
import { MoheQuarterService } from '../../../service/mohe-quarter.service';
import { LookupService } from '../../../service/lookup.service';
import { TranslationService } from '../../../service/translation.service';

/**
 * Adding a quarter.
 *
 * <p>The dialog opened on three empty boxes, and the two that matter most —
 * year and quarter number — were optional. Behind them QUARTER, QUARTER_YEAR and
 * QUARTER_NO are all NOT NULL, and the form has never had a control for QUARTER at
 * all, so a create from this screen offered NULL to a NOT NULL column. The period key
 * is derived on the server now, and the dialog opens on the quarter that follows the
 * latest one on record.
 *
 * <p>The list's Quarter column had the same confusion in reverse: it printed the
 * identity key with a Q in front of it (Q3, Q10 … Q15), while every other table in the
 * system carries a period as its YYYYQ code.
 */
describe('MoheQuarter — adding one', () => {

    let fixture: ComponentFixture<MoheQuarterComponent>;
    let component: MoheQuarterComponent;
    let next: jasmine.Spy;
    let create: jasmine.Spy;

    const QUARTERS = [
        { id: 14, quarter: 20252, quarterYear: 2025, quarterNumber: 2, quarterName: 'الربع الثاني 2025' },
        { id: 15, quarter: 20253, quarterYear: 2025, quarterNumber: 3, quarterName: 'الربع الثالث 2025' }
    ];

    /** What GET /next answers for the two quarters above. */
    const DEFAULTS = {
        quarter: 20254, quarterYear: 2025, quarterNumber: 4,
        quarterName: 'الربع الرابع 2025', quarterNameEn: 'Quarter 4 2025'
    };

    beforeEach(async () => {
        next = jasmine.createSpy('next').and.returnValue(of(DEFAULTS));
        create = jasmine.createSpy('create').and.returnValue(of({ quarterId: 16 }));

        await TestBed.configureTestingModule({
            imports: [OperationsModule],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: MoheQuarterService,
                    useValue: {
                        getAll: () => of(QUARTERS), next, create, update: () => of({}),
                        currentCreateJob: () => of(null),
                        watchCreateJob: () => of({ jobId: 'j1', state: 'DONE', total: 1, done: 1, summary: { quarterId: 16 } })
                    }
                },
                {
                    provide: LookupService,
                    useValue: {
                        getAcademicYears: () => of([{ label: '2025', value: '2025' }, { label: '2026', value: '2026' }]),
                        getQuarterNumbers: () => of([
                            { label: 'Q1', value: 1 }, { label: 'Q2', value: 2 },
                            { label: 'Q3', value: 3 }, { label: 'Q4', value: 4 }
                        ]),
                        getStudyCountriesIn: () => of([])
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

        fixture = TestBed.createComponent(MoheQuarterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    });

    const value = (control: string) => component.quarterForm.get(control)?.value;

    // ── The defaults ─────────────────────────────────────────────────────────

    it('opens on the quarter that follows the latest one on record', () => {
        component.openCreateDialog();

        expect(next).toHaveBeenCalled();
        expect(value('quarterYear')).toBe(2025);
        expect(value('quarterNumber')).toBe(4);
    });

    it('names it the way the rows already there are named', () => {
        // The single Name box is bound to QUARTER_NAME, the nationalized column, and
        // all seven rows in it are Arabic. The English name is not put there.
        component.openCreateDialog();

        expect(value('quarterName')).toBe('الربع الرابع 2025');
    });

    it('leaves every default editable: a skipped period can still be recorded', () => {
        component.openCreateDialog();

        expect(component.quarterForm.get('quarterYear')?.disabled).toBeFalse();
        expect(component.quarterForm.get('quarterNumber')?.disabled).toBeFalse();
        expect(component.quarterForm.get('quarterName')?.disabled).toBeFalse();
    });

    it('opens empty rather than erroring when the defaults cannot be read', () => {
        next.and.returnValue(throwError(() => new Error('403')));

        component.openCreateDialog();

        expect(value('quarterYear')).toBeNull();
        expect(component.formDialogVisible).toBeTrue();
    });

    it('does not fill a form that is no longer the one it was asked for', () => {
        // The answer can arrive after the dialog was closed, or after an Edit was
        // opened from behind it; filling either would overwrite what the user is
        // looking at.
        let emit: (d: any) => void = () => {};
        next.and.returnValue({ subscribe: (o: any) => { emit = o.next; return { unsubscribe: () => {} }; } });

        component.openCreateDialog();
        component.openEditDialog(QUARTERS[0] as any);
        emit(DEFAULTS);

        expect(value('quarterYear')).toBe(2025);
        expect(value('quarterNumber')).withContext('still the row being edited').toBe(2);
    });

    it('asks for the defaults again each time, not once per session', () => {
        component.openCreateDialog();
        component.hideFormDialog();
        component.openCreateDialog();

        expect(next).toHaveBeenCalledTimes(2);
    });

    // ── What is required, and what is sent ───────────────────────────────────

    it('will not save without a year and a quarter number', () => {
        component.openCreateDialog();
        component.quarterForm.patchValue({ quarterYear: null, quarterNumber: null });

        component.saveQuarter();

        expect(create).not.toHaveBeenCalled();
    });

    it('does not send the period key: the server derives it', () => {
        component.openCreateDialog();

        component.saveQuarter();

        const payload = create.calls.mostRecent().args[0];
        expect(payload.quarter).toBeUndefined();
        expect(payload.id1).toBeUndefined();
        expect(payload.quarterYear).toBe(2025);
        expect(payload.quarterNumber).toBe(4);
    });

    // ── The list ─────────────────────────────────────────────────────────────

    it('shows the period, not the row id, in the Quarter column', () => {
        const firstCell = fixture.nativeElement.querySelector('tbody tr td');

        expect(firstCell.textContent.trim()).toBe('20252');
    });

    it('lets the search box find a quarter by its period code', () => {
        expect(component.table?.globalFilterFields).toContain('quarter');
    });

    it('sorts the Quarter column on the period, not on the row id', () => {
        // Sorting on the id put 2024 Q2 (id 3) before 2024 Q1 (id 10): the two were
        // entered in the wrong order and the identity key remembers that, the period
        // does not.
        (fixture.nativeElement.querySelector('thead th') as HTMLElement).click();
        fixture.detectChanges();

        expect(component.table?.sortField).toBe('quarter');
    });
});
