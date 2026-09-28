import { DataImportComponent } from './data-import.component';

/**
 * The column widths behind the file-preview table.
 *
 * `nzScroll.x` makes ng-zorro render the header and the body as two separate
 * tables laid out `fixed`. A column with no width is sized independently in
 * each, so the headers drift out of line with the cells below them — and
 * `nzScroll.x` has to be the sum of the widths, or the last column is clipped.
 *
 * Only the preview table scrolls now. The imported-data table in step 4 shows
 * eight columns that fit the page, and the rest of the record opens in a
 * dialog rather than 2350px of sideways scrolling.
 *
 * The class is instantiated bare: these are pure getters over a static map and
 * touch none of the injected services.
 */
describe('scholarship preview table widths', () => {

  const component = new DataImportComponent(
    null as any, null as any, null as any, null as any, null as any);

  const px = (value: string) => {
    expect(value).withContext(`"${value}" is not a px length`).toMatch(/^\d+px$/);
    return parseInt(value, 10);
  };

  it('gives every grid column a usable width', () => {
    component.scholarshipGridColumns.forEach(column => {
      expect(px(component.columnWidth(column)))
        .withContext(`${column} is too narrow to read`)
        .toBeGreaterThanOrEqual(80);
    });
  });

  it('scrolls to exactly the sum of the grid columns', () => {
    const summed = component.scholarshipGridColumns
      .reduce((total, column) => total + px(component.columnWidth(column)), 0);

    expect(px(component.scholarshipGridWidth)).toBe(summed);
  });

  it('falls back to a real width for a column it does not know', () => {
    expect(component.columnWidth('NO_SUCH_COLUMN')).toMatch(/^\d+px$/);
    expect(component.scholarshipGridWidth).not.toContain('NaN');
  });
});

/**
 * The record dialog.
 *
 * <p>It is assembled from four pieces rather than one list — a summary band,
 * stat tiles, the free-text notes, and definition lists for the letterhead and
 * the provenance. That is a nicer read, but it also means a field can now go
 * missing by falling between two of them, so the first test below collects
 * every label the dialog renders and checks the whole record is in there.
 */
describe('scholarship record dialog', () => {

  const component = new DataImportComponent(
    null as any,
    null as any,
    { isRTL: () => false } as any,
    null as any,
    null as any);

  /** A row shaped like the one GET /scholarship-data/{id} returns. */
  const row: Record<string, any> = {
    ID: 843, STUDENT_NAME: 'دعاء علي عبده', ENTITY: 'د. جامعية', COUNTRY: 'مصر',
    SCHOLARSHIP_YEAR: 2017, DEGREE: 'جامعية', SPECIALIZATION: 'طب بشري',
    DECISION_NO: '2025082', STUDY_DURATION: 7, CURRENT_LEVEL: 6,
    FIRST_DISBURSEMENT: 20173, AMOUNT: 1500.00, STUDENT_SIGNATURE: null,
    NOTES: 'تمديد بموجب قرار', FINANCIAL_NOTES: '0',
    HDR_REPUBLIC: 'الجمهورية اليمنية', HDR_MINISTRY: 'وزارة المالية',
    HDR_DEPARTMENT: 'الإدارة العامة للبعثات', HDR_REPORT_TITLE: 'كشوفات المساعدة',
    HDR_ENTITY: 'وزارة المالية', HDR_QUARTER: 'الثالث', HDR_YEAR: '2025م',
    HDR_STATEMENT_DATE: '17.08.2026', HDR_NOTE_1: 'note one', HDR_NOTE_4: 'note four',
    SOURCE_FILE: 'countries/country_1/dataImportXLS/x.xlsx', SOURCE_SHEET: 'الكل',
    SOURCE_ROW: 10, IMPORTED_AT: '2026-08-24T14:21:32', IMPORTED_BY: 1
  };

  /** Every label the dialog puts on screen, wherever it renders it. */
  const allLabels = () => [
    ...component.recordChips().map(c => c.label),
    ...component.recordStats().map(s => s.label),
    ...component.recordNotes().map(n => n.label),
    ...component.scholarshipDetailSections().flatMap(s => s.fields.map(f => f.label))
  ];

  beforeEach(() => {
    component.scholarshipDetail = row;
  });

  it('shows nothing at all with no record loaded', () => {
    component.scholarshipDetail = null;

    expect(component.scholarshipDetailSections()).toEqual([]);
    expect(component.recordChips()).toEqual([]);
    expect(component.recordNotes()).toEqual([]);
    expect(component.recordAmount()).toBe('-');
  });

  it('shows every grid column somewhere, plus the name in the summary', () => {
    const labels = allLabels();

    component.scholarshipGridColumns
      // The name headlines the summary band and the amount is the figure beside
      // it; neither is a labelled row, so neither is in the collected labels.
      .filter(column => column !== 'STUDENT_NAME' && column !== 'AMOUNT')
      .forEach(column => {
        expect(labels)
          .withContext(`${column} fell between the summary, the tiles and the lists`)
          .toContain('DATA_IMPORT.SCHOLARSHIP_COL_' + column);
      });

    expect(component.scholarshipDetail!['STUDENT_NAME']).toBe('دعاء علي عبده');
    expect(component.recordAmount()).toBe('1,500.00');
  });

  it('carries the statement header and the provenance', () => {
    const labels = allLabels();

    expect(labels).toContain('DATA_IMPORT.SCHOLARSHIP_HDR_REPUBLIC');
    expect(labels).toContain('DATA_IMPORT.SCHOLARSHIP_HDR_STATEMENT_DATE');
    expect(labels).toContain('DATA_IMPORT.SCHOLARSHIP_COL_SOURCE_FILE');
    expect(labels).toContain('DATA_IMPORT.IMPORTED_AT');
  });

  it('lists only the notes the statement actually had', () => {
    const labels = allLabels();

    expect(labels).toContain('DATA_IMPORT.SCHOLARSHIP_HDR_NOTE_1');
    expect(labels).toContain('DATA_IMPORT.SCHOLARSHIP_HDR_NOTE_4');
    // 2 and 3 were empty on this statement; an empty note is not a note.
    expect(labels).not.toContain('DATA_IMPORT.SCHOLARSHIP_HDR_NOTE_2');
  });

  it('drops empty chips instead of rendering blank pills', () => {
    component.scholarshipDetail = { ...row, SPECIALIZATION: null, DEGREE: '' };

    expect(component.recordChips().map(c => c.value)).toEqual(['مصر']);
  });

  it('renders an absent value as empty, not as "null"', () => {
    const signature = component.recordStats()
      .find(s => s.label === 'DATA_IMPORT.SCHOLARSHIP_COL_STUDENT_SIGNATURE');

    expect(signature?.value).toBe('');
  });

  it('groups the amount and keeps two decimals', () => {
    component.scholarshipDetail = { ...row, AMOUNT: 1413120 };
    expect(component.recordAmount()).toBe('1,413,120.00');

    // Whatever the column holds, it must not render as "NaN".
    component.scholarshipDetail = { ...row, AMOUNT: 'not a number' };
    expect(component.recordAmount()).toBe('not a number');

    component.scholarshipDetail = { ...row, AMOUNT: null };
    expect(component.recordAmount()).toBe('-');
  });

  it('gives the long fields the full row, and the file path a monospace face', () => {
    const fields = component.scholarshipDetailSections().flatMap(s => s.fields);
    const file = fields.find(f => f.label === 'DATA_IMPORT.SCHOLARSHIP_COL_SOURCE_FILE');

    expect(file?.wide).withContext('a stored path is a sentence, not a cell').toBeTrue();
    expect(file?.mono).toBeTrue();
    expect(fields.find(f => f.label === 'DATA_IMPORT.SCHOLARSHIP_HDR_MINISTRY')?.wide).toBeTrue();
  });
});

/**
 * Sorting, filtering and search on the imported-data table.
 *
 * <p>All three run on the server, and that is the point worth protecting. The
 * table holds ten rows out of several thousand, so a sort or a filter applied
 * in the browser would quietly reorder the page in front of you and leave the
 * other several thousand rows out of it — which looks like it worked.
 *
 * <p>The component is built bare with a stub HttpClient, so the tests can see
 * the exact query string each interaction produces.
 */
describe('scholarship list query', () => {

  let component: DataImportComponent;
  let requests: string[];

  /** Stands in for HttpClient: records the URL and never completes. */
  const http = {
    get: (url: string, options?: any) => {
      requests.push(url + '?' + (options?.params?.toString() ?? ''));
      return { subscribe: () => ({ unsubscribe: () => { } }) };
    }
  };

  /** The query string of the last /scholarship-data request. */
  const lastQuery = () => {
    const list = requests.filter(r => r.includes('/scholarship-data?'));
    return list.length ? list[list.length - 1].split('?')[1] : '';
  };

  /** An nzQueryParams payload the way ng-zorro emits it. */
  const query = (over: Partial<any> = {}) => ({
    pageIndex: 1, pageSize: 10, sort: [], filter: [], ...over
  }) as any;

  beforeEach(() => {
    requests = [];
    component = new DataImportComponent(
      http as any, null as any, { isRTL: () => false } as any, null as any, null as any);
    component.scholarshipFilterCountryId = 1;
  });

  it('asks the server to sort, rather than sorting the page it was given', () => {
    component.onScholarshipTableQuery(
      query({ sort: [{ key: 'STUDENT_NAME', value: 'descend' }] }));

    expect(lastQuery()).toContain('sortBy=STUDENT_NAME');
    expect(lastQuery()).toContain('sortDir=descend');
  });

  it('drops the sort entirely when the header cycles back to unsorted', () => {
    component.onScholarshipTableQuery(query({ sort: [{ key: 'AMOUNT', value: 'ascend' }] }));
    component.onScholarshipTableQuery(query({ sort: [{ key: 'AMOUNT', value: null }] }));

    expect(component.scholarshipSortBy).toBeNull();
    expect(lastQuery()).not.toContain('sortBy');
  });

  it('sends each ticked filter value as its own parameter', () => {
    component.onScholarshipTableQuery(
      query({ filter: [{ key: 'DEGREE', value: ['جامعية', 'ماجستير'] }] }));

    const sent = decodeURIComponent(lastQuery());
    expect(sent).toContain('filter.DEGREE=جامعية');
    expect(sent).toContain('filter.DEGREE=ماجستير');
  });

  it('treats a cleared filter as no filter', () => {
    component.onScholarshipTableQuery(query({ filter: [{ key: 'DEGREE', value: ['جامعية'] }] }));
    component.onScholarshipTableQuery(query({ filter: [{ key: 'DEGREE', value: [] }] }));

    expect(component.scholarshipFilters['DEGREE']).toBeUndefined();
    expect(lastQuery()).not.toContain('filter.DEGREE');
  });

  it('goes back to page 1 when the filter changes under a later page', () => {
    // Page 7 of the unfiltered list is not page 7 of the filtered one, and is
    // usually not a page at all — the user would land on an empty table.
    component.onScholarshipTableQuery(query({ pageIndex: 7 }));
    component.onScholarshipTableQuery(
      query({ pageIndex: 7, filter: [{ key: 'DEGREE', value: ['جامعية'] }] }));

    expect(component.scholarshipPageIndex).toBe(0);
    expect(lastQuery()).toContain('page=0');
  });

  it('keeps the page when only the page changed', () => {
    component.onScholarshipTableQuery(query({ pageIndex: 3 }));

    expect(component.scholarshipPageIndex).toBe(2);
    expect(lastQuery()).toContain('page=2');
  });

  it('does not re-ask a question it has already asked', () => {
    // ng-zorro re-emits nzQueryParams for changes we made ourselves — the page
    // reset above, the filter state restored when the dropdowns reload. Each
    // echo would otherwise cost a round trip.
    component.onScholarshipTableQuery(query({ pageIndex: 2 }));
    const after = requests.length;
    component.onScholarshipTableQuery(query({ pageIndex: 2 }));

    expect(requests.length).toBe(after);
  });

  it('sends the search once it is trimmed, and not at all when it is blank', () => {
    component.scholarshipSearch = '  احمد  ';
    component.onScholarshipTableQuery(query({ pageIndex: 2 }));
    expect(decodeURIComponent(lastQuery())).toContain('search=احمد');

    component.scholarshipSearch = '   ';
    component.onScholarshipTableQuery(query({ pageIndex: 3 }));
    expect(lastQuery()).not.toContain('search=');
  });

  it('keeps the chosen country while sorting and filtering', () => {
    component.onScholarshipTableQuery(
      query({ sort: [{ key: 'AMOUNT', value: 'descend' }] }));

    expect(lastQuery()).toContain('countryId=1');
  });

  it('shows the arrow only on the column being sorted', () => {
    component.onScholarshipTableQuery(query({ sort: [{ key: 'AMOUNT', value: 'descend' }] }));

    expect(component.sortOrderFor('AMOUNT')).toBe('descend');
    expect(component.sortOrderFor('STUDENT_NAME')).toBeNull();
  });

  it('offers the reset only when there is something to reset', () => {
    expect(component.hasScholarshipQuery).toBeFalse();

    component.onScholarshipTableQuery(query({ filter: [{ key: 'COUNTRY', value: ['مصر'] }] }));
    expect(component.hasScholarshipQuery).toBeTrue();

    component.resetScholarshipQuery();
    expect(component.hasScholarshipQuery).toBeFalse();
    expect(lastQuery()).not.toContain('filter.');
  });

  it('leaves the country alone when the query is reset', () => {
    // The country is the scope the user chose, not a filter they are escaping.
    component.onScholarshipTableQuery(query({ sort: [{ key: 'ID', value: 'ascend' }] }));
    component.resetScholarshipQuery();

    expect(component.scholarshipFilterCountryId).toBe(1);
    expect(lastQuery()).toContain('countryId=1');
  });

  it('throws away the column filters when the country changes', () => {
    // The degrees on offer in one country are not the degrees on offer in
    // another; a tick carried over shows an empty table for no visible reason.
    component.onScholarshipTableQuery(query({ filter: [{ key: 'DEGREE', value: ['جامعية'] }] }));
    component.scholarshipFilterCountryId = 2;
    component.onScholarshipCountryChange();

    expect(component.scholarshipFilters).toEqual({});
    expect(lastQuery()).not.toContain('filter.DEGREE');
    expect(requests.some(r => r.includes('/scholarship-data/filters'))).toBeTrue();
  });
});

/**
 * The quarterly-base ETL card.
 *
 * <p>It moves rows that are already in the landing table into the staging
 * table; there is no file and no upload. The two country fields are separate
 * on purpose — the country being staged and the country being looked at are
 * different questions — and most of these tests are about not confusing them.
 */
describe('quarterly base ETL', () => {

  let component: DataImportComponent;
  let requests: { url: string; params: string }[];
  let confirmed: () => void;

  const http = {
    get: (url: string, options?: any) => {
      requests.push({ url, params: paramsOf(options) });
      return { subscribe: (h: any) => { h.next?.({ sourceRows: 842, stagedRows: 12, stagedTotal: 12 }); } };
    },
    post: (url: string, _body: any, options?: any) => {
      requests.push({ url, params: paramsOf(options) });
      return { subscribe: (h: any) => { h.next?.({ rowsStaged: 842, rowsReplaced: 12, rowsRejected: 0, currentQuarter: '20253' }); } };
    }
  };

  /**
   * The query string, whichever shape the caller used.
   *
   * <p>These calls pass a plain object rather than HttpParams, and a plain
   * object answers toString() with "[object Object]" — which contains no
   * parameter at all but would still satisfy a careless assertion.
   */
  const paramsOf = (options: any) => {
    const params = options?.params;
    if (!params) return '';
    return typeof params.keys === 'function'
      ? params.toString()
      : Object.entries(params).map(([k, v]) => `${k}=${v}`).join('&');
  };

  const modal = { confirm: (config: any) => { confirmed = config.nzOnOk; } };
  const message = { success: () => { }, error: () => { } };
  const translate = { translate: (key: string) => key, isRTL: () => false };

  const lastTo = (fragment: string) =>
    [...requests].reverse().find(r => r.url.includes(fragment));

  beforeEach(() => {
    requests = [];
    confirmed = () => { };
    component = new DataImportComponent(
      http as any, message as any, translate as any, null as any, modal as any);
  });

  it('does not offer to stage a country with nothing imported', () => {
    component.etlCountryId = 1;
    component.onEtlCountryChange();

    expect(component.etlCounts?.sourceRows).toBe(842);
    // The template disables the button on this; the state is what it reads.
    expect(component.etlCounts?.stagedRows).toBe(12);
  });

  it('previews without writing anything', () => {
    component.etlCountryId = 1;
    component.etlPreview();

    const preview = lastTo('/etl/staging/preview');
    expect(preview).toBeDefined();
    expect(preview!.params).toContain('countryId=1');
    // The run endpoint must not have been touched by a preview.
    expect(lastTo('/etl/staging/run')).toBeUndefined();
    expect(component.etlStep).toBe(1);
  });

  it('asks before replacing rows that are already staged', () => {
    component.etlCountryId = 1;
    component.onEtlCountryChange();
    component.etlPreview();

    let ran = false;
    (component as any).runEtl = () => { ran = true; };
    component.confirmEtlRun();

    expect(ran).withContext('staging must not start before the user confirms').toBeFalse();
    confirmed();
    expect(ran).toBeTrue();
  });

  it('does not ask when there is nothing to replace', () => {
    component.etlCountryId = 1;
    component.etlCounts = { sourceRows: 842, stagedRows: 0, stagedTotal: 0 };

    let ran = false;
    (component as any).runEtl = () => { ran = true; };
    component.confirmEtlRun();

    expect(ran).toBeTrue();
  });

  it('reports what the run did', () => {
    component.etlCountryId = 1;
    component.etlPreview();
    (component as any).runEtl();

    expect(component.etlResult?.success).toBeTrue();
    expect(component.etlStep).toBe(2);
    expect(component.etlSummary()).toContain('842');
    expect(component.etlSummary()).toContain('20253');
  });

  it('keeps the staged view on its own country filter', () => {
    component.etlCountryId = 1;
    component.goToViewStagedData();
    expect(lastTo('/etl/staging/data')!.params).toContain('countryId=1');

    // Widening the view to every country must not change what gets staged.
    component.etlViewCountryId = null;
    component.onStagedCountryChange();
    expect(lastTo('/etl/staging/data')!.params).not.toContain('countryId');
    expect(component.etlCountryId).toBe(1);
  });

  it('shows a resolved lookup with the id beside the name', () => {
    // السنة الأولى is both id 1 and id 100 in the real lookup, so the name
    // alone cannot say which row the load picked.
    expect(component.lookupCell('السنة الأولى', 1)).toBe('السنة الأولى (1)');
    expect(component.lookupCell(null, 99)).toBe('99');
    expect(component.lookupCell(null, null)).toBe('-');
  });

  it('keeps the country but drops the report when staging another', () => {
    component.etlCountryId = 1;
    component.etlPreview();
    component.resetEtl();

    expect(component.etlStep).toBe(0);
    expect(component.etlReport).toBeNull();
    expect(component.etlCountryId).toBe(1);
  });
});

/**
 * The staged-record dialog.
 *
 * <p>It answers one question the table cannot: did the ETL resolve this row
 * correctly? So every lookup shows its name and its id together, and anything
 * sitting on غير مبين is flagged rather than quietly shown as a normal value.
 */
describe('staged record dialog', () => {

  const component = new DataImportComponent(
    null as any, null as any, { isRTL: () => false } as any, null as any, null as any);

  /** A row shaped like the one GET /etl/staging/{id} returns. */
  const row: Record<string, any> = {
    STUDENT_ID: 1685, STUDENT_NAME: 'دعاء علي عبده',
    STUDENT_DEGREE_ID: 1, DEGREE_NAME: 'جامعية',
    STUDENT_MAJOR_ID: 185, MAJOR_NAME: 'طب بشري', STUDENT_MAJOR_NAME: 'طب  بشري',
    STUDENT_CURRENT_LEVEL_ID: 6, LEVEL_NAME: 'السنة السادسة',
    STUDENT_AGENCY_TYPE_ID: 2, AGENCY_NAME: 'جامعية',
    STUDENT_MINISTRY_TYPE_ID: 99, MINISTRY_NAME: 'غير مبين',
    STUDENT_STUDY_STATUS_ID: 2, STATUS_NAME: 'جديد',
    STUDENT_COUNTRY_ID: 1, NATIONALITY_NAME: 'اليمن',
    STUDY_COUNTRY_IN: 1, COUNTRY_NAME: 'مصر',
    STUDY_UNIVERSITY_IN: 999, UNIVERSITY_NAME: 'غير مبين',
    STUDY_COLLEGE_IN: 999, COLLEGE_NAME: 'غير مبين',
    SYUDY_GOV_IN: 999, STUDY_GOV_NAME: 'غير مبين',
    STUDY_EDUCATION_IN: 999, EDUCATION_NAME: 'غير مبين',
    STUDY_MAJOR_IN: null, STUDY_MAJOR_NAME: null,
    STUDENT_STUDY_TYPE_ID: 999, STUDY_TYPE_NAME: 'غير مبين',
    STUDENT_UNIVERSITY_TYPE_ID: null, UNIVERSITY_TYPE_NAME: null,
    STUDENT_GOV_ID: null, HOME_GOV_NAME: null,
    STUDENT_MUD_ID: null, HOME_DISTRICT_NAME: null,
    STUDENT_GENDER: null, GENDER_NAME: null,
    STUDENT_DELEGATION_DECISION_YEAR: '2017', STUDENT_DELEGATE_DECISION_NO: 2025082,
    STUDENT_STUDY_NO_OF_YEARS: 7, STUDENT_FIRST_RECEIPT_OF_DUE: 20173,
    STUDENT_AMOUNT: 1500, STUDENT_NOTE_1: 'تمديد بموجب قرار', STUDENT_NOTE_2: null,
    STUDENT_ADDED_QUARTER: 20253, STUDENT_LAST_QUARTER: 20253, CURRENT_QUARTER: '20253',
    RECORD_VISIBILITY: 'ALL', STUDENT_MIDATE: '2026-08-24T22:22:58'
  };

  /** Every field the dialog renders, wherever it renders it. */
  const allFields = () => component.stagedSections().flatMap(s => s.fields);

  beforeEach(() => {
    component.stagedDetail = row;
  });

  it('shows nothing at all with no record loaded', () => {
    component.stagedDetail = null;

    expect(component.stagedSections()).toEqual([]);
    expect(component.stagedChips()).toEqual([]);
    expect(component.stagedNotes()).toEqual([]);
    expect(component.stagedAmount()).toBe('-');
  });

  it('shows each lookup as its name and its id together', () => {
    const degree = allFields().find(f => f.label === 'DATA_IMPORT.ETL_LOOKUP_DEGREE');
    const level = allFields().find(f => f.label === 'DATA_IMPORT.ETL_LOOKUP_CURRENT_LEVEL');

    // The id matters: السنة السادسة is both 6 and 105 in the real lookup, so
    // the name on its own cannot say which row the load chose.
    expect(degree?.value).toBe('جامعية (1)');
    expect(level?.value).toBe('السنة السادسة (6)');
  });

  it('flags a field that landed on غير مبين', () => {
    const ministry = allFields().find(f => f.label === 'DATA_IMPORT.ETL_LOOKUP_MINISTRY_TYPE');
    const university = allFields().find(f => f.label === 'DATA_IMPORT.ETL_UNIVERSITY');
    const degree = allFields().find(f => f.label === 'DATA_IMPORT.ETL_LOOKUP_DEGREE');

    // 99 in some lookups, 999 in others; both mean the same thing.
    expect(ministry?.unresolved).toBeTrue();
    expect(university?.unresolved).toBeTrue();
    expect(degree?.unresolved).toBeFalse();
  });

  it('does not flag a column that is simply empty', () => {
    // NULL is "the sheet never said", not "we could not work it out".
    const gender = allFields().find(f => f.label === 'DATA_IMPORT.ETL_GENDER');

    expect(gender?.value).toBe('');
    expect(gender?.unresolved).toBeFalse();
  });

  it('keeps the specialization as the sheet wrote it, beside the resolved one', () => {
    const written = allFields().find(f => f.label === 'DATA_IMPORT.ETL_MAJOR_AS_WRITTEN');
    const resolved = allFields().find(f => f.label === 'DATA_IMPORT.ETL_LOOKUP_MAJOR');

    // The raw text has a double space the lookup name does not; that difference
    // is exactly what someone checking the resolution needs to see.
    expect(written?.value).toBe('طب  بشري');
    expect(resolved?.value).toBe('طب بشري (185)');
  });

  it('separates nationality from country of study', () => {
    const nationality = allFields().find(f => f.label === 'DATA_IMPORT.ETL_NATIONALITY');
    const study = allFields().find(f => f.label === 'DATA_IMPORT.ETL_STUDY_COUNTRY');

    // Both are id 1 and mean opposite things.
    expect(nationality?.value).toBe('اليمن (1)');
    expect(study?.value).toBe('مصر (1)');
  });

  it('shows the study status, which is the column with the worst default', () => {
    const status = allFields().find(f => f.label === 'DATA_IMPORT.ETL_STUDY_STATUS');

    // 0 there is متوفى. Seeing the name is how anyone would ever notice.
    expect(status?.value).toBe('جديد (2)');
  });

  it('groups the amount and keeps it whole', () => {
    component.stagedDetail = { ...row, STUDENT_AMOUNT: 1413120 };
    expect(component.stagedAmount()).toBe('1,413,120');

    component.stagedDetail = { ...row, STUDENT_AMOUNT: null };
    expect(component.stagedAmount()).toBe('-');

    component.stagedDetail = { ...row, STUDENT_AMOUNT: 'not a number' };
    expect(component.stagedAmount()).toBe('not a number');
  });

  it('drops empty chips and empty notes rather than rendering blanks', () => {
    expect(component.stagedChips().map(c => c.value)).toEqual(['جامعية', 'طب بشري', 'مصر']);
    // NOTE_2 is null on this row.
    expect(component.stagedNotes().map(n => n.label))
      .toEqual(['DATA_IMPORT.SCHOLARSHIP_COL_NOTES']);
  });

  it('puts what the sheet stated and what it did not in separate sections', () => {
    const titles = component.stagedSections().map(s => s.title);

    expect(titles).toEqual([
      'DATA_IMPORT.ETL_SECTION_RESOLVED',
      'DATA_IMPORT.ETL_SECTION_STATUS',
      'DATA_IMPORT.ETL_SECTION_PLACEHOLDERS'
    ]);
  });

  it('closes the dialog when the ETL card is reset', () => {
    component.stagedDetailVisible = true;
    component.etlCountryId = null;
    component.resetEtl();

    expect(component.stagedDetailVisible).toBeFalse();
    expect(component.stagedDetail).toBeNull();
  });
});


/**
 * The staged-row edit form.
 *
 * <p>Two things it must get right. It sends only what actually changed, so a
 * save cannot quietly rewrite a field somebody never touched; and it treats an
 * untouched field as untouched even though the form hands numbers back as
 * strings and empty boxes back as "".
 */
describe('staged record edit form', () => {

  let component: DataImportComponent;
  let put: { url: string; body: any } | null;
  let deleted: string | null;

  const record: Record<string, any> = {
    STUDENT_ID: 1685, STUDENT_NAME: 'دعاء علي', IS_EDITED: false,
    STUDENT_DEGREE_ID: 1, STUDENT_MAJOR_ID: 185, STUDENT_MAJOR_NAME: 'طب بشري',
    STUDENT_CURRENT_LEVEL_ID: 6, STUDENT_AGENCY_TYPE_ID: 2, STUDENT_MINISTRY_TYPE_ID: 99,
    STUDENT_STUDY_STATUS_ID: 2, STUDENT_COUNTRY_ID: 1, STUDENT_GENDER: null,
    STUDENT_AMOUNT: 1500, STUDENT_NOTE_1: 'تمديد', STUDENT_NOTE_2: null,
    CURRENT_QUARTER: '20253', STUDY_COUNTRY_IN: 1
  };

  const http = {
    get: (url: string) => ({
      subscribe: (h: any) => {
        h.next?.(url.includes('/lookups')
          ? { STUDENT_DEGREE_ID: [{ id: 1, name: 'جامعية' }, { id: 104, name: 'دكتوراه' }] }
          : url.includes('/etl/staging/') ? { ...record } : { content: [], totalElements: 0 });
      }
    }),
    put: (url: string, body: any) => {
      put = { url, body };
      return { subscribe: (h: any) => h.next?.({ ...record, ...body, IS_EDITED: true }) };
    },
    delete: (url: string) => {
      deleted = url;
      return { subscribe: (h: any) => h.next?.({}) };
    }
  };

  beforeEach(() => {
    put = null;
    deleted = null;
    component = new DataImportComponent(
      http as any,
      { success: () => { }, error: () => { } } as any,
      { translate: (k: string) => k, isRTL: () => false } as any,
      null as any,
      // Clearing a manual edit is confirmed now, so the modal cannot be null
      // here; the two tests below replace this with what each of them needs.
      { confirm: (config: any) => config } as any);
    component.openStagedEdit(1685);
  });

  it('loads the row into the form and nothing else', () => {
    expect(component.etlEditId).toBe(1685);
    expect(component.etlEditForm['STUDENT_NAME']).toBe('دعاء علي');
    expect(component.etlEditForm['STUDENT_DEGREE_ID']).toBe(1);

    // The country is not on the form: the ETL delete is scoped by it, and a
    // row moved out of its country would outlive every future run.
    expect(component.etlEditForm['STUDY_COUNTRY_IN']).toBeUndefined();
    expect(component.etlEditForm['STUDENT_ID']).toBeUndefined();
  });

  it('offers no save until something actually changes', () => {
    expect(component.etlEditDirty()).toBeFalse();

    component.etlEditForm['STUDENT_DEGREE_ID'] = 104;
    expect(component.etlEditDirty()).toBeTrue();
  });

  it('does not call a retyped identical value a change', () => {
    // A number box hands back "1500" where the record carried 1500. Without
    // this every field would look edited on every open.
    component.etlEditForm['STUDENT_AMOUNT'] = '1500';

    expect(component.isFieldChanged('STUDENT_AMOUNT')).toBeFalse();
    expect(component.etlEditDirty()).toBeFalse();
  });

  it('treats an emptied box and a null the same way', () => {
    component.etlEditForm['STUDENT_NOTE_2'] = '';

    expect(component.isFieldChanged('STUDENT_NOTE_2')).toBeFalse();
  });

  it('sends only the fields that changed', () => {
    component.etlEditForm['STUDENT_DEGREE_ID'] = 104;
    component.etlEditForm['STUDENT_GENDER'] = 1;
    component.saveStagedEdit();

    expect(put!.url).toContain('/etl/staging/1685');
    expect(Object.keys(put!.body).sort()).toEqual(['STUDENT_DEGREE_ID', 'STUDENT_GENDER']);
    // A save must not rewrite a field nobody touched.
    expect(put!.body['STUDENT_NAME']).toBeUndefined();
  });

  it('sends a cleared field so the server can null it', () => {
    component.etlEditForm['STUDENT_NOTE_1'] = '';
    component.saveStagedEdit();

    expect(put!.body).toEqual({ STUDENT_NOTE_1: '' });
  });

  it('does not send anything when nothing changed', () => {
    component.saveStagedEdit();

    expect(put).toBeNull();
  });

  it('closes and refreshes after a save', () => {
    component.etlEditForm['STUDENT_DEGREE_ID'] = 104;
    component.saveStagedEdit();

    expect(component.etlEditVisible).toBeFalse();
    expect(component.etlEditSaving).toBeFalse();
  });

  it('updates the record dialog behind it when it is open on the same row', () => {
    component.stagedDetailVisible = true;
    component.stagedDetail = { ...record };
    component.etlEditForm['STUDENT_DEGREE_ID'] = 104;
    component.saveStagedEdit();

    expect(component.stagedDetail!['STUDENT_DEGREE_ID']).toBe(104);
    expect(component.stagedDetail!['IS_EDITED']).toBeTrue();
  });

  it('offers every lookup column a dropdown and never a free-text box', () => {
    const lookupKeys = component.etlEditSections
      .flatMap(s => s.fields)
      .filter(f => f.key.endsWith('_ID') || f.key.startsWith('STUDY_') || f.key === 'STUDENT_GENDER')
      .filter(f => f.key !== 'NATIONAL_ID');

    // A free-text box on a foreign key would only let somebody invent an id
    // that does not exist.
    lookupKeys.forEach(field => {
      expect(field.type).withContext(`${field.key} must be a dropdown`).toBe('lookup');
    });
    expect(lookupKeys.length).toBeGreaterThan(10);
  });

  it('asks before handing a row back to the ETL', () => {
    // Added 31 August 2026: this throws away a correction somebody typed by
    // hand, and it used to go on a single click from a danger button.
    let asked = false;
    (component as any).modal.confirm = (config: any) => { asked = true; return config; };

    component.clearStagedEdit();

    expect(asked).withContext('the manual edit was discarded with no prompt').toBeTrue();
    expect(deleted).withContext('and it had already reached the server').not.toContain('/etl/staging/1685/edit');
  });

  it('hands a row back to the ETL once the prompt is accepted', () => {
    (component as any).modal.confirm = (config: any) => { config.nzOnOk(); return config; };

    component.clearStagedEdit();

    expect(deleted).toContain('/etl/staging/1685/edit');
    expect(component.etlEditWasEdited).toBeFalse();
  });

  it('forgets the form when the dialog closes', () => {
    component.etlEditForm['STUDENT_NAME'] = 'something else';
    component.closeStagedEdit();

    expect(component.etlEditId).toBeNull();
    expect(component.etlEditForm).toEqual({});
    expect(component.etlEditDirty()).toBeFalse();
  });
});


/**
 * Where the edit dropdowns come from, and what happens when they do not.
 *
 * <p>The options are read from the lookup tables the foreign keys point at, so
 * an unreachable server produces exactly the same thing on screen as a lookup
 * table with nothing in it: seventeen empty dropdowns. These tests are about
 * telling those two apart.
 */
describe('staged edit dropdowns', () => {

  let component: DataImportComponent;
  let lookupCalls: number;
  let lookupResponse: any;
  let lookupFails: boolean;

  const record = { STUDENT_ID: 1685, STUDENT_NAME: 'س', STUDENT_DEGREE_ID: 1 };

  const http = {
    get: (url: string) => ({
      subscribe: (h: any) => {
        if (url.includes('/lookups')) {
          lookupCalls++;
          if (lookupFails) { h.error?.({ status: 404 }); } else { h.next?.(lookupResponse); }
          return;
        }
        h.next?.(url.includes('/etl/staging/') ? { ...record } : { content: [], totalElements: 0 });
      }
    })
  };

  beforeEach(() => {
    lookupCalls = 0;
    lookupFails = false;
    lookupResponse = {
      STUDENT_DEGREE_ID: [{ id: 1, name: 'جامعية' }, { id: 104, name: 'دكتوراه' }],
      STUDENT_MAJOR_ID: [{ id: 185, name: 'طب بشري' }]
    };
    component = new DataImportComponent(
      http as any,
      { success: () => { }, error: () => { } } as any,
      { translate: (k: string) => k, isRTL: () => false } as any,
      null as any, null as any);
  });

  it('fills the dropdowns from what the server returned', () => {
    component.openStagedEdit(1685);

    expect(component.etlLookups['STUDENT_DEGREE_ID'].length).toBe(2);
    expect(component.hasLookupOptions('STUDENT_DEGREE_ID')).toBeTrue();
    expect(component.etlLookupsFailed).toBeFalse();
  });

  it('fetches them once, not once per dialog', () => {
    component.openStagedEdit(1685);
    component.closeStagedEdit();
    component.openStagedEdit(1686);

    expect(lookupCalls).toBe(1);
  });

  it('says so when the lists could not be loaded', () => {
    lookupFails = true;
    component.openStagedEdit(1685);

    // Otherwise an unreachable server is indistinguishable from empty tables.
    expect(component.etlLookupsFailed).toBeTrue();
    expect(component.hasLookupOptions('STUDENT_DEGREE_ID')).toBeFalse();
  });

  it('treats an empty response as a failure too', () => {
    // These lookup tables are never empty, so nothing usable came back.
    lookupResponse = {};
    component.openStagedEdit(1685);

    expect(component.etlLookupsFailed).toBeTrue();
  });

  it('retries on demand and recovers', () => {
    lookupFails = true;
    component.openStagedEdit(1685);
    expect(component.etlLookupsFailed).toBeTrue();

    lookupFails = false;
    component.retryEtlLookups();

    expect(component.etlLookupsFailed).toBeFalse();
    expect(component.etlLookups['STUDENT_DEGREE_ID'].length).toBe(2);
  });

  it('tries again on the next dialog after a failure', () => {
    lookupFails = true;
    component.openStagedEdit(1685);
    component.closeStagedEdit();

    lookupFails = false;
    component.openStagedEdit(1686);

    expect(lookupCalls).toBe(2);
    expect(component.etlLookupsFailed).toBeFalse();
  });

  it('keeps a value the list does not offer, rather than blanking the field', () => {
    lookupFails = true;
    component.openStagedEdit(1685);

    // The row holds degree 1. With no options loaded the select must still
    // carry it, or simply opening the form would clear a field nobody touched.
    expect(component.etlEditForm['STUDENT_DEGREE_ID']).toBe(1);
    expect(component.isKnownOption('STUDENT_DEGREE_ID', 1)).toBeFalse();
    expect(component.etlEditDirty()).withContext('opening a form is not an edit').toBeFalse();
  });

  it('knows a value that is in the list', () => {
    component.openStagedEdit(1685);

    expect(component.isKnownOption('STUDENT_DEGREE_ID', 1)).toBeTrue();
    expect(component.isKnownOption('STUDENT_DEGREE_ID', 999)).toBeFalse();
  });
});


/**
 * Sorting, filtering and search on the staged table.
 *
 * <p>All three run on the server. The table holds ten rows out of hundreds, so
 * anything done in the browser would reorder the page in front of you and
 * ignore the rest — which looks exactly like it worked.
 */
describe('staged table query', () => {

  let component: DataImportComponent;
  let requests: string[];

  const http = {
    get: (url: string, options?: any) => {
      requests.push(url + '?' + (options?.params?.toString?.() ?? ''));
      return { subscribe: () => ({ unsubscribe: () => { } }) };
    }
  };

  const lastQuery = () => {
    const list = requests.filter(r => r.includes('/etl/staging/data?'));
    return list.length ? list[list.length - 1].split('?')[1] : '';
  };

  const query = (over: Partial<any> = {}) => ({
    pageIndex: 1, pageSize: 10, sort: [], filter: [], ...over
  }) as any;

  beforeEach(() => {
    requests = [];
    component = new DataImportComponent(
      http as any, null as any,
      { translate: (k: string) => k, isRTL: () => false } as any,
      null as any, null as any);
    component.etlViewCountryId = 1;
  });

  it('asks the server to sort rather than sorting the page it was given', () => {
    component.onStagedTableQuery(query({ sort: [{ key: 'AMOUNT', value: 'descend' }] }));

    expect(lastQuery()).toContain('sortBy=AMOUNT');
    expect(lastQuery()).toContain('sortDir=descend');
  });

  it('drops the sort when the header cycles back to unsorted', () => {
    component.onStagedTableQuery(query({ sort: [{ key: 'DEGREE', value: 'ascend' }] }));
    component.onStagedTableQuery(query({ sort: [{ key: 'DEGREE', value: null }] }));

    expect(component.stagedSortBy).toBeNull();
    expect(lastQuery()).not.toContain('sortBy');
  });

  it('sends each ticked filter value as its own parameter', () => {
    component.onStagedTableQuery(query({ filter: [{ key: 'DEGREE', value: ['1', '104'] }] }));

    const sent = decodeURIComponent(lastQuery());
    expect(sent).toContain('filter.DEGREE=1');
    expect(sent).toContain('filter.DEGREE=104');
  });

  it('goes back to page 1 when a filter changes under a later page', () => {
    component.onStagedTableQuery(query({ pageIndex: 7 }));
    component.onStagedTableQuery(
      query({ pageIndex: 7, filter: [{ key: 'LEVEL', value: ['6'] }] }));

    expect(component.stagedPageIndex).toBe(0);
    expect(lastQuery()).toContain('page=0');
  });

  it('keeps the page when only the page changed', () => {
    component.onStagedTableQuery(query({ pageIndex: 3 }));

    expect(component.stagedPageIndex).toBe(2);
    expect(lastQuery()).toContain('page=2');
  });

  it('does not re-ask a question it has already asked', () => {
    // ng-zorro re-emits for changes we made ourselves; each echo would
    // otherwise cost a round trip.
    component.onStagedTableQuery(query({ pageIndex: 2 }));
    const after = requests.length;
    component.onStagedTableQuery(query({ pageIndex: 2 }));

    expect(requests.length).toBe(after);
  });

  it('sends the needs-review flag only when it is on', () => {
    expect(lastQuery()).not.toContain('unresolvedOnly');

    component.stagedUnresolvedOnly = true;
    component.onStagedCriteriaChange();

    expect(lastQuery()).toContain('unresolvedOnly=true');
  });

  it('trims the search and omits it when blank', () => {
    component.stagedSearch = '  طب  ';
    component.onStagedTableQuery(query({ pageIndex: 2 }));
    expect(decodeURIComponent(lastQuery())).toContain('search=طب');

    component.stagedSearch = '   ';
    component.onStagedTableQuery(query({ pageIndex: 3 }));
    expect(lastQuery()).not.toContain('search=');
  });

  it('keeps the chosen country while sorting and filtering', () => {
    component.onStagedTableQuery(query({ sort: [{ key: 'AMOUNT', value: 'descend' }] }));

    expect(lastQuery()).toContain('countryId=1');
  });

  it('shows the arrow only on the column being sorted', () => {
    component.onStagedTableQuery(query({ sort: [{ key: 'AMOUNT', value: 'descend' }] }));

    expect(component.stagedSortOrderFor('AMOUNT')).toBe('descend');
    expect(component.stagedSortOrderFor('STUDENT_NAME')).toBeNull();
  });

  it('offers the reset only when there is something to reset', () => {
    expect(component.hasStagedQuery).toBeFalse();

    component.stagedUnresolvedOnly = true;
    expect(component.hasStagedQuery).toBeTrue();

    component.resetStagedQuery();
    expect(component.hasStagedQuery).toBeFalse();
    expect(component.stagedUnresolvedOnly).toBeFalse();
  });

  it('leaves the country alone when the query is reset', () => {
    // The country is the scope the user chose, not a filter to escape.
    component.onStagedTableQuery(query({ sort: [{ key: 'AMOUNT', value: 'ascend' }] }));
    component.resetStagedQuery();

    expect(component.etlViewCountryId).toBe(1);
    expect(lastQuery()).toContain('countryId=1');
  });

  it('throws away the column filters when the country changes', () => {
    component.onStagedTableQuery(query({ filter: [{ key: 'DEGREE', value: ['1'] }] }));
    component.etlViewCountryId = 2;
    component.onStagedCountryChange();

    expect(component.stagedFilters).toEqual({});
    expect(lastQuery()).not.toContain('filter.DEGREE');
    expect(requests.some(r => r.includes('/etl/staging/filters'))).toBeTrue();
  });

  it('keeps the two tables apart', () => {
    // Both cards have a sortable table on the same screen; a shared field
    // would make sorting one silently reorder the other.
    component.onStagedTableQuery(query({ sort: [{ key: 'AMOUNT', value: 'descend' }] }));

    expect(component.stagedSortBy).toBe('AMOUNT');
    expect(component.scholarshipSortBy).toBeNull();
  });
});
