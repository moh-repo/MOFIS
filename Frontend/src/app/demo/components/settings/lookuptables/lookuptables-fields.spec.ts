import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';

import { LookupTablesModule } from './lookuptables.module';
import { LookupTablesComponent } from './lookuptables.component';
import { LOOKUP_DESCRIPTORS, describeLookup } from './lookup-descriptors';
import { LookupTableService } from '../../../service/lookuptable.service';
import { TranslationService } from '../../../service/translation.service';
import { UmsAuthService } from '../../../service/ums-auth.service';
import { UmsPermissionService } from '../../../service/ums-permission.service';

/**
 * What the Lookup Tables screen shows, offers and sends, per lookup.
 *
 * <p>The screen renders 33 lookups through one table and one pair of dialogs, and
 * it used to guess what each of them held. It showed `description`, `createdDate`
 * and `modifiedDate` on every one — **no lookup entity has any of the three** — so
 * two columns were blank in all 33 lists, the Description textarea's contents were
 * dropped by the server on every save, and the view dialog's whole Status and
 * Timeline section read "N/A". It showed none of `sortOrder`, `isSystem`,
 * `moheMainId` or `studentAgencyTypeId`, which those tables do have: a MOHE Sub
 * Main, a Ministry Type or a University Type created here got the column default
 * of 0 for its parent and became an orphan.
 *
 * <p>One descriptor now says what each lookup is made of, and the list, the form
 * and the view dialog all read it — so these checks walk all 33 rather than
 * sampling: a table described wrongly is described wrongly in three places at once.
 */

/** Rows shaped like the real ones, for the lookups these checks touch. */
const ROWS: Record<string, any[]> = {
    country: [
        { studentCountryId: 1, studentCountryName: 'ليبيا', studentCountryNameEn: 'Libya', isActive: true },
        { studentCountryId: 2, studentCountryName: 'مصر', studentCountryNameEn: 'Egypt', isActive: true }
    ],
    governorate: [
        { id: 11, studentGovName: 'طرابلس', studentGovNameEn: 'Tripoli', studentCountryId: 1, isActive: true },
        { id: 21, studentGovName: 'القاهرة', studentGovNameEn: 'Cairo', studentCountryId: 2, isActive: true }
    ],
    'mohe-main': [{ id: 1, moheMainName: 'رئيسي', moheMainNameEn: 'Main', isActive: true }],
    'student-agency-type': [
        { id: 1, studentAgencyTypeName: 'جامعة', studentAgencyTypeNameEn: 'University', isActive: true }
    ],
    // The longest values these two tables actually hold, read off the dev
    // database: a 41-character report-label code, a 29-character group code and
    // a 291-character English label. Short sample rows made the table look as if
    // it fitted, which is the whole thing being measured.
    'report-dimensions': [
        { id: 1, code: 'studyType', nameAr: 'نوع الدراسة', nameEn: 'Study type', groupCode: 'ENROLLMENT_STATISTICS_GROUPBY', sortOrder: 1, isSystem: true },
        { id: 2, code: 'country', nameAr: 'الدولة', nameEn: 'Country', groupCode: 'STUDENT_DEMOGRAPHICS_GROUPBY', sortOrder: 2, isSystem: true }
    ],
    'report-labels': [
        {
            id: 1, code: 'LBL_DOC_TITLE@STUDENT_FINANCIAL_STATEMENT', sortOrder: 1, isSystem: true,
            nameAr: 'كشف الحساب المالي للطالب الدارس بالخارج على نفقة الدولة',
            nameEn: 'Financial statement for a student studying abroad at the expense of the state, '
                + 'issued by the Ministry of Finance for the '
                + 'purposes of the annual settlement of entitlements and disbursements'
        },
        {
            id: 2, code: 'COL_DECISION_NO@FINANCIAL_DATA_FORM_V2', sortOrder: 2, isSystem: true,
            nameAr: 'رقم القرار', nameEn: 'Decision no.'
        }
    ],
    'wafar-spending-statuses': [
        { id: 1, code: 'PENDING', nameAr: 'قيد الانتظار', nameEn: 'Pending', sortOrder: 1, isSystem: true },
        { id: 9, code: 'LOCAL', nameAr: 'محلي', nameEn: 'Local', sortOrder: 9, isSystem: false }
    ]
};

class ServiceStub {
    /** Every list fetch, in order, so a re-fetch is visible. */
    readonly fetched: string[] = [];
    readonly created: { table: string; body: any }[] = [];
    readonly deleted: { table: string; ids: any }[] = [];

    getAll(table: string) {
        this.fetched.push(table);
        const rows = ROWS[table] ?? [];
        return of({ content: rows, totalElements: rows.length });
    }
    create(table: string, body: any) { this.created.push({ table, body }); return of({}); }
    update() { return of({}); }
    delete(table: string, id: any) { this.deleted.push({ table, ids: id }); return of({}); }
    deleteMultiple(table: string, ids: any[]) { this.deleted.push({ table, ids }); return of({}); }
}

let arabic = false;
const translation = {
    currentLanguage$: of('en'), initialized$: of(true), isReady: () => true,
    isRTL: () => arabic,
    getCurrentLanguage: () => (arabic ? 'ar' : 'en'),
    // The last segment, underscores opened out: "Name Ar", "Sort Order", "Group
    // Code". Returning the whole key would be fine for the checks that read a
    // field's identity, but not for the ones that measure: a heading of
    // "LOOKUP_TABLES.COLUMN_HEADERS.GROUP_CODE" has nowhere to break and would
    // make every column as wide as its key.
    translate: (key: string) => key.split('.').pop()!.replace(/_/g, ' ')
};

describe('the lookup tables screen and its 33 lookups', () => {

    let fixture: any;
    let page: LookupTablesComponent;
    let service: ServiceStub;

    beforeEach(async () => {
        arabic = false;
        service = new ServiceStub();

        await TestBed.configureTestingModule({
            imports: [LookupTablesModule, RouterModule.forRoot([])],
            providers: [
                MessageService,
                ConfirmationService,
                { provide: HttpClient, useValue: { get: () => of({}), post: () => of({}), put: () => of({}), delete: () => of({}) } },
                { provide: LookupTableService, useValue: service },
                { provide: TranslationService, useValue: translation },
                { provide: ActivatedRoute, useValue: { queryParams: of({}), snapshot: { queryParams: {} } } },
                { provide: UmsAuthService, useValue: new Proxy({ permissions$: of([]) } as any, { get: (t, p) => p in t ? (t as any)[p] : () => true }) },
                { provide: UmsPermissionService, useValue: new Proxy({} as any, { get: (_t, prop) => prop === 'getAllowedCountryIds' ? () => [] : () => true }) }
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(LookupTablesComponent);
        page = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
        document.body.appendChild(fixture.nativeElement);
        fixture.detectChanges();
    });

    afterEach(() => fixture.nativeElement.remove());

    /** Every lookup id the six tabs can select, in the order they appear. */
    const offered = (): string[] => [
        ...page.studentLookups, ...page.studyUniversityLookups, ...page.financeLookups,
        ...page.operationsLookups, ...page.systemLookups, ...page.locationLookups
    ].map(l => l.id);

    const select = (id: string) => {
        page.selectTableById(id);
        fixture.detectChanges();
    };

    // ── the descriptor covers the screen, and nothing else ────────────────────

    it('describes every lookup the tabs offer, and offers every lookup it describes', () => {
        const ids = offered();
        expect(ids.length).withContext('the tabs stopped offering 33 lookups').toBe(33);

        const undescribed = ids.filter(id => !describeLookup(id));
        expect(undescribed)
            .withContext('these lookups can be selected but have no descriptor, so the '
                + 'list, the form and the view dialog would each have nothing to show')
            .toEqual([]);

        const unreachable = LOOKUP_DESCRIPTORS.map(d => d.id).filter(id => !ids.includes(id));
        expect(unreachable).withContext('described but not reachable from any tab').toEqual([]);
    });

    /**
     * The three the dialogs used to show on every table. Kept as a list rather
     * than a comment: whichever of them comes back, comes back everywhere.
     */
    const PHANTOM = ['description', 'createdDate', 'modifiedDate'];

    it('never shows a column no lookup table has', () => {
        const offenders: string[] = [];
        offered().forEach(id => {
            select(id);
            page.columns
                .filter(col => PHANTOM.includes(col.key))
                .forEach(col => offenders.push(`${id}.${col.key}`));
        });
        expect(offenders)
            .withContext('no lookup entity carries description, createdDate or modifiedDate; '
                + 'these columns were blank in every row of every list')
            .toEqual([]);
    });

    it('never offers a field no lookup table has', () => {
        const offenders: string[] = [];
        offered().forEach(id => {
            select(id);
            page.formFields
                .filter(field => PHANTOM.includes(field.key))
                .forEach(field => offenders.push(`${id}.${field.key}`));
        });
        expect(offenders)
            .withContext('the server drops these on save, so the user types into nothing')
            .toEqual([]);
    });

    it('renders one table, for the tab that is open', () => {
        select('wafar-spending-statuses');
        expect(fixture.nativeElement.querySelectorAll('p-table').length)
            .withContext('a p-tabpanel keeps its content in the DOM whether or not it is '
                + 'showing, so all six tabs rendered the shared template — six lazy tables '
                + 'over the same rows, each firing its own onLazyLoad')
            .toBe(1);
    });

    it('fetches the chosen lookup once, not once per tab', () => {
        service.fetched.length = 0;
        select('wafar-spending-statuses');
        expect(service.fetched.filter(t => t === 'wafar-spending-statuses').length).toBe(1);
    });

    it('renders a header cell for each of the selected lookup\'s columns', () => {
        select('wafar-spending-statuses');
        const headers = Array.from(
            fixture.nativeElement.querySelectorAll('p-table table thead tr th')
        ) as HTMLElement[];
        // the select-all column, one per descriptor column, and Actions
        expect(headers.length).toBe(page.columns.length + 2);
    });

    it('shows the sort order on a code list', () => {
        select('wafar-spending-statuses');
        expect(page.columns.map(c => c.key))
            .withContext('the lists are read back in this order, and a code created '
                + 'without one arrived at 0, at the top and out of sequence')
            .toContain('sortOrder');
    });

    // ── the two hierarchies, and the two other parents ────────────────────────

    it('offers a municipality the country and the governorate it belongs to', () => {
        select('municipality');
        expect(page.formFields.map(f => f.key))
            .toEqual(['studentMudName', 'studentMudNameEn', 'studentCountryId', 'studentGovId', 'isActive']);
    });

    it('narrows the governorates to the country that was chosen', () => {
        select('municipality');
        page.openNew();
        const gov = page.formFields.find(f => f.key === 'studentGovId')!;

        expect(page.optionsFor(gov))
            .withContext('with no country chosen there is nothing to choose from').toEqual([]);

        page.currentItem.studentCountryId = 1;
        expect(page.optionsFor(gov).map((o: any) => o.studentGovNameEn))
            .withContext('the list is every governorate, not the chosen country\'s')
            .toEqual(['Tripoli']);

        page.currentItem.studentCountryId = 2;
        expect(page.optionsFor(gov).map((o: any) => o.studentGovNameEn)).toEqual(['Cairo']);
    });

    it('clears a governorate that no longer belongs to the country', () => {
        select('municipality');
        page.openNew();
        const country = page.formFields.find(f => f.key === 'studentCountryId')!;

        page.currentItem.studentCountryId = 1;
        page.currentItem.studentGovId = 11;

        page.currentItem.studentCountryId = 2;
        page.onRefChange(country);

        expect(page.currentItem.studentGovId)
            .withContext('Tripoli stayed selected under Egypt')
            .toBeFalsy();
    });

    it('names a municipality\'s governorate in the list and the view dialog', () => {
        select('municipality');
        const gov = page.viewFields.find(f => f.key === 'studentGovId')!;
        // STUDENT_GOV_MUD.STUDENT_GOV_ID is a varchar and STUDENT_GOV.ID an int,
        // which is what the === this replaces could never match.
        expect(page.displayValue({ studentGovId: '11' }, gov))
            .withContext('a municipality\'s governorate reads as unavailable')
            .toBe('Tripoli');
    });

    it('sends the parent of a MOHE sub main', () => {
        select('mohe-sub-main');
        page.openNew();
        page.currentItem.moheSubMainName = 'فرع';
        page.currentItem.moheSubMainNameEn = 'Branch';
        page.currentItem.moheMainId = 1;
        page.saveItem();

        expect(service.created.length).withContext('nothing was sent').toBe(1);
        expect(service.created[0].body.moheMainId)
            .withContext('a sub main used to be created with the column default of 0')
            .toBe(1);
    });

    it('will not create a sub main with no parent', () => {
        select('mohe-sub-main');
        page.openNew();
        page.currentItem.moheSubMainName = 'فرع';
        page.saveItem();

        expect(service.created.length).toBe(0);
        const parent = page.formFields.find(f => f.key === 'moheMainId')!;
        expect(page.missing(parent)).withContext('no message under the empty field').toBeTrue();
    });

    it('asks a ministry type and a university type which agency they belong to', () => {
        select('student-ministry-type');
        expect(page.formFields.map(f => f.key)).toContain('studentAgencyTypeId');
        select('student-university-type');
        expect(page.formFields.map(f => f.key)).toContain('studentAgencyTypeId');
    });

    // ── View → Edit ──────────────────────────────────────────────────────────

    it('opens the editor on the viewed record, not an empty form', () => {
        select('governorate');
        const row = ROWS['governorate'][0];
        page.viewItem(row);
        page.editFromView();

        expect(page.viewDialog).toBeFalse();
        expect(page.dataDialog).toBeTrue();
        expect(page.isEditMode).toBeTrue();
        expect(page.currentItem[page.getIdField()])
            .withContext('closing the view used to clear the record first, so Save sent PUT …/undefined')
            .toBe(row[page.getIdField()]);
    });

    // ── the option lists are fetched once ────────────────────────────────────

    it('fetches a foreign-key list once, not on every dialog', () => {
        select('municipality');
        page.openNew();
        page.hideDialog();
        page.editItem(ROWS['governorate'][0]);
        page.hideDialog();

        const countries = service.fetched.filter(t => t === 'country').length;
        expect(countries)
            .withContext('the countries were re-fetched on each dialog; opening two used to '
                + 'cost four round trips for eighteen rows')
            .toBe(1);
    });

    it('does not re-fetch a list it already holds when a second lookup needs it', () => {
        select('governorate');
        select('municipality');
        expect(service.fetched.filter(t => t === 'country').length).toBe(1);
    });

    // ── the rows the application depends on ──────────────────────────────────

    it('offers no delete on a system code', () => {
        select('wafar-spending-statuses');
        expect(page.canDelete(ROWS['wafar-spending-statuses'][0]))
            .withContext('a row a Java constant names; the server refuses it and the '
                + 'application will not start without it').toBeFalse();
        expect(page.canDelete(ROWS['wafar-spending-statuses'][1])).toBeTrue();
    });

    it('leaves a system code out of a select-all', () => {
        select('wafar-spending-statuses');
        page.onSelectionChange(ROWS['wafar-spending-statuses']);
        expect(page.selectedItems.map((r: any) => r.code)).toEqual(['LOCAL']);
    });

    it('sends no system code to a batch delete', () => {
        select('wafar-spending-statuses');
        page.selectedItems = [...ROWS['wafar-spending-statuses']];
        page.confirmDeleteMultiple();
        expect(service.deleted.length).toBe(1);
        expect(service.deleted[0].ids).toEqual([9]);
    });

    it('gives a system row no checkbox and no delete button', () => {
        select('wafar-spending-statuses');
        const rows = Array.from(
            fixture.nativeElement.querySelectorAll('p-table table tbody tr')
        ) as HTMLElement[];
        expect(rows.length).withContext('the stub rows did not render').toBe(2);

        expect(rows[0].querySelector('p-tablecheckbox'))
            .withContext('a system row can be ticked, so select-all puts it in a batch that fails')
            .toBeNull();
        expect(rows[0].querySelector('button.p-button-danger'))
            .withContext('a delete button that always answers 409').toBeNull();

        expect(rows[1].querySelector('p-tablecheckbox')).withContext('positive control').not.toBeNull();
    });

    // ── switching tab starts the new lookup clean ────────────────────────────

    it('drops the previous lookup\'s paging and sort when the tab changes', () => {
        select('student-gender');
        page.first = 30;
        page.sortField = 'studentGenderName';
        page.sortOrder = -1;
        page.globalFilter = 'x';

        page.onTabChange(2);

        expect(page.first).withContext('page 4 of a list with six rows').toBe(0);
        expect(page.sortField)
            .withContext('the sort column of the lookup just left, sent to one that has no such column')
            .toBe('');
        expect(page.globalFilter).toBe('');
    });

    // ── the table fits the page ──────────────────────────────────────────────

    /**
     * The width the shell leaves for a page on a 1366px window: 1366 less the
     * 270px sidebar and the main container's own 3.5rem + 1.5rem of padding at
     * this application's 12.6px root size. The card's 2rem padding comes out of
     * that in the DOM, as it does in the shell.
     */
    const MAIN_CONTAINER_CONTENT = 1033;

    /** The rendered width of each header cell, in column order. */
    const columnWidths = (): number[] =>
        Array.from(fixture.nativeElement.querySelectorAll('p-table table thead th'))
            .map(th => Math.round((th as HTMLElement).getBoundingClientRect().width));

    const widthOf = (key: string): number => {
        // +1 for the select-all column, which no descriptor field describes.
        const index = page.columns.findIndex(c => c.key === key);
        expect(index).withContext(`no ${key} column`).toBeGreaterThan(-1);
        return columnWidths()[index + 1];
    };

    describe('inside the width the shell leaves it', () => {

        beforeEach(() => {
            // The fixture is put on the body by the outer beforeEach; give it the
            // width a page actually has rather than the browser's full 1600px,
            // which no user has once the sidebar is out.
            const frame = document.createElement('div');
            frame.style.width = `${MAIN_CONTAINER_CONTENT}px`;
            fixture.nativeElement.parentElement.insertBefore(frame, fixture.nativeElement);
            frame.appendChild(fixture.nativeElement);
            fixture.detectChanges();
        });

        it('leaves the page itself no wider than the frame it was given', () => {
            select('report-labels');
            const el = fixture.nativeElement.querySelector('.lookuptables-page') as HTMLElement;
            expect(el.scrollWidth)
                .withContext('PrimeFlex gives .grid a negative margin and the matching padding '
                    + 'to the column, so a grid holding one full-width column is 1rem wider '
                    + 'than its box and the whole window scrolls')
                .toBeLessThanOrEqual(el.clientWidth);
        });

        /**
         * The table always "fitted" — by squeezing. `white-space: nowrap` means a
         * column cannot be narrower than its longest value, and the report labels'
         * codes run to 41 characters (`LBL_DOC_TITLE@STUDENT_FINANCIAL_STATEMENT`).
         * That column took 361px of a 981px table and left 113px each for the two
         * name columns, which are the ones anyone reads.
         */
        /** The width the descriptor asked for, as a number. */
        const declared = (key: string): number =>
            parseFloat(page.columns.find(c => c.key === key)!.width!);

        /**
         * A column that declares a width must not be dragged far past it by one
         * long value. The report labels' codes run to 41 characters, and with
         * `nowrap` that column took 361px of a 981px table — a third of it — while
         * the two name columns, which are what anyone reads, got 113px each.
         */
        it('keeps a long code inside the width its column asked for', () => {
            select('report-labels');
            const code = widthOf('code');
            expect(code)
                .withContext(`the code column asked for ${declared('code')}px and took ${code}px, `
                    + `against ${widthOf('nameAr')}px and ${widthOf('nameEn')}px for the names`)
                .toBeLessThanOrEqual(declared('code') * 1.2);
        });

        it('keeps a long group code inside the width its column asked for', () => {
            select('report-dimensions');
            const group = widthOf('groupCode');
            expect(group)
                .withContext(`the group code column asked for ${declared('groupCode')}px and took ${group}px`)
                .toBeLessThanOrEqual(declared('groupCode') * 1.2);
        });

        it('leaves the row buttons their line', () => {
            select('report-labels');
            const cell = fixture.nativeElement
                .querySelector('p-table table tbody tr td.lk-col-actions') as HTMLElement;
            expect(cell).withContext('no actions cell').toBeTruthy();
            expect(cell.scrollWidth)
                .withContext(`the actions column was squeezed to ${cell.clientWidth}px for buttons `
                    + `that need ${cell.scrollWidth}px`)
                .toBeLessThanOrEqual(cell.clientWidth);
        });

        it('fills the width it is given rather than stopping short', () => {
            select('report-labels');
            const box = fixture.nativeElement
                .querySelector('.p-datatable-table-container') as HTMLElement;
            const table = fixture.nativeElement.querySelector('p-table table') as HTMLElement;
            expect(table.getBoundingClientRect().width)
                .withContext('the table is narrower than the container it sits in')
                .toBeGreaterThanOrEqual(box.clientWidth - 1);
            expect(box.scrollWidth)
                .withContext('the table scrolls sideways inside the card')
                .toBeLessThanOrEqual(box.clientWidth);
        });
    });

    it('shows the system flag on the code rather than in a column of its own', () => {
        select('report-dimensions');
        expect(page.columns.map(c => c.key))
            .withContext('a flag that is either a lock or nothing does not need a column')
            .not.toContain('isSystem');
        expect(page.viewFields.map(f => f.key))
            .withContext('it is still worth naming in the view dialog').toContain('isSystem');

        const rows = Array.from(
            fixture.nativeElement.querySelectorAll('p-table table tbody tr')
        ) as HTMLElement[];
        expect(rows[0].querySelector('.lk-code__lock'))
            .withContext('no lock on a system code').not.toBeNull();
    });

    // ── the leftovers from the 22 Sep live test (QA B15) ──────────────────────

    it('opens a municipality\'s edit with its governorate selected', () => {
        // The column is a varchar: the stored "11" has to match an option keyed 11.
        select('municipality');
        page.editItem({ id: 5, studentMudName: 'x', studentCountryId: 1, studentGovId: '11', isActive: true });
        const gov = page.formFields.find(f => f.key === 'studentGovId')!;

        const values = page.optionsFor(gov).map((o: any) => o[page.optionValueField(gov)]);
        expect(values).withContext('"11" matched no option, so the field opened empty').toContain('11');
    });

    it('states and enforces a ten-character code', () => {
        select('books-allowance-pay-statuses');
        page.openNew();
        fixture.detectChanges();

        const input = document.querySelector('#lk-code') as HTMLInputElement;
        expect(input.getAttribute('maxlength')).toBe('10');
        expect(document.querySelector('.lk-max-length')).withContext('no hint of the limit').not.toBeNull();
        page.hideDialog();
        fixture.detectChanges();
    });

    // Live QA R5-24: the screen did not mark the English name required on the standard
    // tables, although the server requires it; and a new report label saved with the
    // English name left empty was refused, because the field was absent from the body
    // (null) and NAME_EN is NOT NULL.
    it('will not save a standard lookup with no English name, and says so under the field', () => {
        select('student-degree');
        page.openNew();
        page.currentItem.studentDegreeName = 'دبلوم';
        page.saveItem();

        expect(service.created.length).toBe(0);
        const english = page.formFields.find(f => f.key === 'studentDegreeNameEn')!;
        expect(english.required).toBeTrue();
        expect(page.missing(english)).withContext('no message under the empty English field').toBeTrue();
    });

    it('sends a new report label\'s empty English name as "", not leaving it out', () => {
        select('report-labels');
        page.openNew();
        Object.assign(page.currentItem, { code: 'LBL_NEW', nameAr: 'م' });

        page.saveItem();

        expect(service.created.length).withContext('the empty English name blocked the save').toBe(1);
        expect(service.created[0].body.nameEn).toBe('');
    });

    it('puts a new row where it can be seen', () => {
        select('wafar-spending-statuses');
        page.first = 40;
        page.globalFilter = 'x';
        page.openNew();
        Object.assign(page.currentItem, { code: 'NEW', nameAr: 'جديد', nameEn: 'New' });

        page.saveItem();

        expect(service.created.length).toBe(1);
        expect(page.first).withContext('reloaded page 5, which the new row is not on').toBe(0);
        expect(page.sortField).toBe('id');
        expect(page.sortOrder).withContext('newest first').toBe(-1);
        expect(page.globalFilter).toBe('');
    });

    it('closes the confirm when a delete is refused, and says why once', () => {
        select('wafar-spending-statuses');
        // The page's own instance: the component provides one.
        const messages = fixture.debugElement.injector.get(MessageService);
        const shown: any[] = [];
        spyOn(messages, 'add').and.callFake((m: any) => shown.push(m));
        service.delete = () => throwError(() => ({ status: 409, error: { message: 'This record is still used by other records' } }));

        page.deleteItem(ROWS['wafar-spending-statuses'][1]);
        expect(page.deleteDialog).toBeTrue();
        page.confirmDelete();

        expect(page.deleteDialog).withContext('left open, offering the same refused delete').toBeFalse();
        expect(shown.length).toBe(1);
        expect(shown[0].detail).toBe('This record is still used by other records');
    });

    it('keeps its own words for a server error, which carries no reason worth reading', () => {
        select('wafar-spending-statuses');
        // The page's own instance: the component provides one.
        const messages = fixture.debugElement.injector.get(MessageService);
        const shown: any[] = [];
        spyOn(messages, 'add').and.callFake((m: any) => shown.push(m));
        service.update = () => throwError(() => ({ status: 500, error: { message: 'java.lang.NullPointerException' } }));

        page.editItem(ROWS['wafar-spending-statuses'][1]);
        page.saveItem();

        expect(shown[0].detail).not.toContain('NullPointerException');
    });

    it('lets a newer load win over a slower older one', () => {
        select('wafar-spending-statuses');
        const replies: any[] = [];
        service.getAll = () => new Observable<any>(o => { replies.push(o); }) as any;

        page.loadData();
        page.loadData();
        replies[1].next({ content: [{ id: 2, code: 'NEWER' }], totalElements: 1 });
        replies[0].next({ content: [{ id: 1, code: 'OLDER' }], totalElements: 1 });

        expect(page.currentData.map((r: any) => r.code)).toEqual(['NEWER']);
    });
});
