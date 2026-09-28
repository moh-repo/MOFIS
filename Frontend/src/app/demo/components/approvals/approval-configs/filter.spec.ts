import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { of } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ApprovalConfigsComponent } from './approval-configs.component';
import { ApprovalsModule } from '../approvals.module';
import { ApprovalService } from '../../../service/approval.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';

/**
 * The filter row on the approval configurations screen.
 *
 * <p>The page listed every workflow with nothing to narrow it by — it was the
 * one page of the Approvals menu with no filter row at all. That was tolerable
 * while there were five configurations; there are twelve now, three of them
 * added this week, and a deactivated one looks exactly like an active one until
 * you read across to the tag.
 *
 * <p>`getAllConfigs()` returns the whole list and the controller offers no
 * filtering, so this narrows what is already in hand rather than asking the
 * server again — twelve rows do not need a round trip.
 */
describe('ApprovalConfigs — the filter row', () => {

    let fixture: ComponentFixture<ApprovalConfigsComponent>;
    let component: ApprovalConfigsComponent;
    /** The UI language, switchable per test - the descriptions follow it. */
    let lang: string;

    /** A slice of ALERTCORE.ApprovalConfigs, active and inactive. */
    const CONFIGS = [
        { id: 1, entityType: 'STUDENT_DATA', description: 'Student main data approval', descriptionAr: 'موافقة بيانات الطالب الأساسية', totalLevels: 3, isActive: true },
        { id: 2, entityType: 'STUDENT_FINANCE', description: 'Student finance data approval', descriptionAr: 'موافقة البيانات المالية للطالب', totalLevels: 4, isActive: true },
        { id: 4, entityType: 'LOOKUP_TABLE', description: 'Lookup table changes approval', descriptionAr: 'موافقة تغييرات جداول القيم', totalLevels: 2, isActive: false },
        { id: 13, entityType: 'STUDENT_LOAN', description: 'Approval workflow for student loans', descriptionAr: 'مسار الموافقة على قروض الطلاب', totalLevels: 2, isActive: true },
        { id: 14, entityType: 'STUDENT_BOOKS_ALLOWANCE', description: 'Approval workflow for student books allowances', descriptionAr: 'مسار الموافقة على مخصصات الكتب', totalLevels: 2, isActive: true },
        // The one live row with no Arabic description: STUDY_UNIVERSITY_FEE.
        { id: 6, entityType: 'STUDY_UNIVERSITY_FEE', description: 'Study university fee approval', descriptionAr: null, totalLevels: 1, isActive: true },
        { id: 15, entityType: 'STUDY_FIN_RECEIVED_WHOM', description: null, descriptionAr: 'مسار الموافقة على مستلمي المبالغ', totalLevels: 2, isActive: false }
    ];

    beforeEach(async () => {
        lang = 'en';

        await TestBed.configureTestingModule({
            imports: [ApprovalsModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                {
                    provide: ApprovalService,
                    useValue: { getAllConfigs: () => of(CONFIGS.map(c => ({ ...c }))) }
                },
                { provide: HttpClient, useValue: { get: () => of([]) } },
                {
                    provide: UmsPermissionService,
                    useValue: new Proxy({} as any, {
                        get: (_t, prop) => prop === 'getAllowedCountryIds' ? () => [] : () => true
                    })
                },
                {
                    provide: TranslationService,
                    useValue: {
                        currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
                        isRTL: () => lang !== 'en', translate: (k: string) => k,
                        getCurrentLanguage: () => lang
                    }
                }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(ApprovalConfigsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    const shown = () => component.filteredConfigs.map(c => c.entityType);

    it('shows every configuration until something is filtered', () => {
        expect(shown().length).toBe(7);
        expect(component.hasFilters).toBeFalse();
    });

    // ── Narrowing ────────────────────────────────────────────────────────────

    it('matches part of an entity type, whatever the case', () => {
        component.filters.text = 'loan';
        component.applyFilters();

        expect(shown()).toEqual(['STUDENT_LOAN']);
    });

    it('matches the description too, not only the code', () => {
        // The code is SHOUTING_SNAKE_CASE; the words a person would search for
        // are in the description beside it.
        component.filters.text = 'books allowances';
        component.applyFilters();

        expect(shown()).toEqual(['STUDENT_BOOKS_ALLOWANCE']);
    });

    it('survives a row with no description at all', () => {
        component.filters.text = 'received';
        component.applyFilters();

        expect(shown()).toEqual(['STUDY_FIN_RECEIVED_WHOM']);
    });

    it('ignores surrounding spaces', () => {
        component.filters.text = '   student_data   ';
        component.applyFilters();

        expect(shown()).toEqual(['STUDENT_DATA']);
    });

    it('separates the active workflows from the retired ones', () => {
        component.filters.active = true;
        component.applyFilters();
        expect(shown().length).toBe(5);

        component.filters.active = false;
        component.applyFilters();
        expect(shown()).toEqual(['LOOKUP_TABLE', 'STUDY_FIN_RECEIVED_WHOM']);
    });

    it('applies both filters at once', () => {
        component.filters.text = 'student';
        component.filters.active = false;
        component.applyFilters();

        // STUDENT_DATA, STUDENT_FINANCE, STUDENT_LOAN and
        // STUDENT_BOOKS_ALLOWANCE all match the text, and all four are active.
        expect(shown()).toEqual([]);
    });

    it('says so when nothing matches, rather than showing everything', () => {
        component.filters.text = 'no-such-workflow';
        component.applyFilters();

        expect(shown()).toEqual([]);
        expect(component.configs.length).withContext('the full list is still held').toBe(7);
    });

    // ── Clearing ─────────────────────────────────────────────────────────────

    it('knows whether anything is filtered', () => {
        component.filters.active = false;
        expect(component.hasFilters).toBeTrue();

        component.filters.active = null;
        component.filters.text = 'x';
        expect(component.hasFilters).toBeTrue();
    });

    it('does not count an all-spaces box as a filter', () => {
        component.filters.text = '   ';
        expect(component.hasFilters).toBeFalse();
    });

    it('resets both filters and shows the whole list again', () => {
        component.filters.text = 'loan';
        component.filters.active = true;
        component.applyFilters();
        expect(shown().length).toBe(1);

        component.resetFilters();

        expect(component.filters.text).toBe('');
        expect(component.filters.active).toBeNull();
        expect(component.filters.levels).toBeNull();
        expect(shown().length).toBe(7);
    });

    // ── Staying in step with the list ────────────────────────────────────────

    it('re-applies the filter when the list is reloaded', () => {
        // Saving a configuration reloads the list. Without re-applying, the
        // table would quietly widen back to everything while the boxes still
        // showed a filter.
        component.filters.text = 'loan';
        component.applyFilters();

        component.loadConfigs();

        expect(shown()).toEqual(['STUDENT_LOAN']);
    });

    // -- Levels ---------------------------------------------------------------

    it('offers the level counts the configurations actually use', () => {
        // Built from the rows in hand rather than 1..10: offering a count no
        // workflow has can only ever return an empty table.
        expect(component.levelOptions.map(o => o.value)).toEqual([1, 2, 3, 4]);
    });

    it('narrows to one level count', () => {
        component.filters.levels = 4;
        component.applyFilters();

        expect(shown()).toEqual(['STUDENT_FINANCE']);
    });

    it('combines the level count with the other two filters', () => {
        component.filters.levels = 2;
        component.filters.active = false;
        component.applyFilters();

        expect(shown()).toEqual(['LOOKUP_TABLE', 'STUDY_FIN_RECEIVED_WHOM']);
    });

    // -- The description, in the language being read ---------------------------

    it('shows the English description in English', () => {
        expect(component.configDescription(CONFIGS[0] as any)).toBe('Student main data approval');
    });

    it('shows the Arabic description in Arabic', () => {
        lang = 'ar';
        expect(component.configDescription(CONFIGS[0] as any)).toBe('موافقة بيانات الطالب الأساسية');
    });

    it('falls back to the other language rather than showing nothing', () => {
        // STUDY_UNIVERSITY_FEE has no Arabic description on the live table, and
        // STUDY_FIN_RECEIVED_WHOM's English one is empty here. A blank cell says
        // less than the wrong language does.
        const noArabic = CONFIGS.find(c => c.entityType === 'STUDY_UNIVERSITY_FEE') as any;
        const noEnglish = CONFIGS.find(c => c.entityType === 'STUDY_FIN_RECEIVED_WHOM') as any;

        lang = 'ar';
        expect(component.configDescription(noArabic)).toBe('Study university fee approval');

        lang = 'en';
        expect(component.configDescription(noEnglish)).toBe('مسار الموافقة على مستلمي المبالغ');
    });

    it('shows a dash when there is no description in either language', () => {
        expect(component.configDescription({ entityType: 'X' } as any)).toBe('—');
    });

    it('searches the description in the language on screen', () => {
        lang = 'ar';
        component.filters.text = 'قروض';
        component.applyFilters();

        expect(shown()).toEqual(['STUDENT_LOAN']);
    });

    // -- Pagination ------------------------------------------------------------

    it('pages the list rather than printing all of it', () => {
        expect(component.tableRows).toBeGreaterThan(0);
        expect(component.rowsPerPageOptions.length).toBeGreaterThan(1);
    });

    it('goes back to the first page when the filter changes', () => {
        // The paginator keeps its offset when the array under it changes, so a
        // user filtering from page 2 would be shown an empty page of a result
        // that has one.
        component.tableFirst = 20;

        component.filters.text = 'loan';
        component.applyFilters();

        expect(component.tableFirst).toBe(0);
    });

    it('goes back to the first page when the filters are reset', () => {
        component.tableFirst = 20;
        component.resetFilters();

        expect(component.tableFirst).toBe(0);
    });
});
