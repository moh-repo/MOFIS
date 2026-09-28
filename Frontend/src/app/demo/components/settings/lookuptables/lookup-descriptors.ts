/**
 * What each of the 33 lookups on `/settings/lookuptables` actually has.
 *
 * <p>The page used to carry this knowledge five times over — `BILINGUAL_TABLE_IDS`,
 * `getIdField`, `getNameFieldAr`, `getNameFieldEn` and `setupColumns` each held
 * their own copy of the same 33-way switch — and none of them described a table
 * fully enough to build a form from. So the list, the add/edit dialog and the view
 * dialog each guessed: they showed `description`, `createdDate` and `modifiedDate`
 * on every table although **no lookup entity has any of them** (the Description
 * column was blank in all 33 lists and the textarea's contents were dropped by the
 * server), and they showed none of `sortOrder`, `isSystem`, `moheMainId` or
 * `studentAgencyTypeId`, which every one of those tables does have.
 *
 * <p>One descriptor per lookup, then, and the three views read from it. A field
 * listed here exists in the database; a field that is not listed is not shown and
 * not written.
 *
 * <p>Deliberately absent:
 * <ul>
 *   <li>`uiSeverity` and `icon`, on the bilingual tables. They exist, but a code's
 *       colour and icon belong to the Code Lists screen, which is built for them.
 *       A code created here has them null until that screen sets them.</li>
 *   <li>`studentEductionId` on Student Major. There is no education lookup among
 *       the 33 to choose from, and the column is NULL on all 444 rows.</li>
 * </ul>
 */

/** Where a foreign-key field gets its options. */
export type LookupOptionSource = 'countries' | 'governorates' | 'moheMains' | 'agencyTypes';

export interface LookupOptionList {
    /** The lookup id to fetch the options from. */
    table: string;
    /** The option row's key, which is what gets written to the field. */
    value: string;
    labelAr: string;
    labelEn: string;
    /**
     * The option row's own foreign key, when this list can be narrowed. Paired
     * with a field's `parent`: the options kept are those whose `parentKey`
     * equals the value of the edited row's `parent` field.
     */
    parentKey?: string;
}

export const LOOKUP_OPTION_LISTS: Record<LookupOptionSource, LookupOptionList> = {
    countries: {
        table: 'country', value: 'studentCountryId',
        labelAr: 'studentCountryName', labelEn: 'studentCountryNameEn'
    },
    governorates: {
        table: 'governorate', value: 'id',
        labelAr: 'studentGovName', labelEn: 'studentGovNameEn',
        parentKey: 'studentCountryId'
    },
    moheMains: {
        table: 'mohe-main', value: 'id',
        labelAr: 'moheMainName', labelEn: 'moheMainNameEn'
    },
    agencyTypes: {
        table: 'student-agency-type', value: 'id',
        labelAr: 'studentAgencyTypeName', labelEn: 'studentAgencyTypeNameEn'
    }
};

export type LookupFieldKind = 'id' | 'code' | 'text' | 'number' | 'boolean' | 'system' | 'ref';

export interface LookupField {
    /** The JSON property on the row. */
    key: string;
    /** Translation key for the label, in the list and in both dialogs. */
    label: string;
    kind: LookupFieldKind;
    /** Column width in the list. A field with none is free text and wraps. */
    width?: string;
    /** Blocks Save when empty, and shows the message under the field. */
    required?: boolean;
    /**
      * Kept out of the list. The table has to fit inside the page, and a flag
      * that is either a lock or nothing does not need a column of its own — the
      * `code` cell carries it, which is where it means something.
      */
    formOnly?: boolean;
    /** Shown, never written: the key column and the system flag. */
    readonly?: boolean;
    /** Renders and stores right-to-left (the Arabic name) or left-to-right. */
    dir?: 'rtl' | 'ltr';
    /**
     * The value is one long unbroken token — a code, a group code — so the cell
     * may break it across lines. Without this the column cannot be narrower than
     * its longest value: `LBL_DOC_TITLE@STUDENT_FINANCIAL_STATEMENT` took 361px
     * of a 981px table and left 113px each for the two name columns, which are
     * the ones anyone reads.
     */
    wrap?: boolean;
    /** Foreign key: where the options come from, and what narrows them. */
    ref?: {
        source: LookupOptionSource;
        /** The field on *this* row whose value narrows the option list. */
        parent?: string;
        /** The column is a string although the option's key is a number. */
        asString?: boolean;
    };
    /**
     * The column's width, where it is narrower than a name. Stated under the field
     * and enforced by it: a longer code used to reach the database and come back
     * as an error that did not say which field (live QA #20).
     */
    maxLength?: number;
}

export interface LookupDescriptor {
    id: string;
    /** The row's key column — `studentCountryId` on Country, `id` on the rest. */
    idField: string;
    /** A `MOHECORE.LU_*` code list: code + names, and a system flag to respect. */
    bilingual: boolean;
    fields: LookupField[];
}

// Labels are written out in full rather than built from a prefix: the i18n
// validator greps the source for the literal key, and a concatenated one reads to
// it as a translation nobody uses.
/** The key column. Shown, never edited: it is the database's to hand out. */
const idOf = (key: string): LookupField =>
    ({ key, label: 'LOOKUP_TABLES.COLUMN_HEADERS.ID', kind: 'id', width: '64px', readonly: true });

const activeField: LookupField =
    { key: 'isActive', label: 'LOOKUP_TABLES.COLUMN_HEADERS.ACTIVE', kind: 'boolean', width: '92px' };

/**
 * A standard lookup: its own key column and a `<prefix>Name` / `<prefix>NameEn`
 * pair, plus `IS_ACTIVE`. Every one of the seventeen fits that shape, including
 * the three whose prefix is not their id — Study Status is `status…`, Governorate
 * is `studentGov…`, Municipality is `studentMud…`.
 */
const standard = (id: string, prefix: string, extra: LookupField[] = [],
                  idField = 'id'): LookupDescriptor => ({
    id, idField, bilingual: false,
    fields: [
        idOf(idField),
        { key: prefix + 'Name', label: 'LOOKUP_TABLES.NAME_AR', kind: 'text', required: true, dir: 'rtl' },
        // Required, as the server requires it on these tables (M11, D20-a); unmarked, a
        // blank English name was refused only by the server's message (live QA R5-24).
        { key: prefix + 'NameEn', label: 'LOOKUP_TABLES.NAME_EN', kind: 'text', required: true, dir: 'ltr' },
        ...extra,
        activeField
    ]
});

/**
 * A bilingual code list. `sortOrder` is in the form because these lists are read
 * back in that order — a code created without one landed at 0, at the top and out
 * of sequence. `isSystem` is in the list and the view because it is the difference
 * between a row that may be deleted and one the application will not start
 * without.
 */
const bilingual = (id: string, extra: LookupField[] = [], codeMax?: number, englishOptional = false): LookupDescriptor => ({
    id, idField: 'id', bilingual: true,
    fields: [
        idOf('id'),
        { key: 'code', label: 'LOOKUP_TABLES.CODE', kind: 'code', width: '150px', required: true, dir: 'ltr', wrap: true,
          maxLength: codeMax },
        { key: 'nameAr', label: 'LOOKUP_TABLES.NAME_AR', kind: 'text', required: true, dir: 'rtl' },
        // Optional only where the server's table allows it (@EnglishNameOptional: report labels, P5-6).
        { key: 'nameEn', label: 'LOOKUP_TABLES.NAME_EN', kind: 'text', required: !englishOptional, dir: 'ltr' },
        ...extra,
        { key: 'sortOrder', label: 'LOOKUP_TABLES.SORT_ORDER', kind: 'number', width: '84px' },
        // Not a column of its own: the lock rides on the code cell. Still listed
        // in the view dialog, where there is room to name it.
        { key: 'isSystem', label: 'LOOKUP_TABLES.COLUMN_HEADERS.SYSTEM', kind: 'system', readonly: true, formOnly: true }
    ]
});

const orderNo = (key: string): LookupField =>
    ({ key, label: 'LOOKUP_TABLES.ORDER_NO', kind: 'number', width: '84px' });

/** Agency type: what a ministry type or a university type belongs to. */
const agencyRef: LookupField = {
    key: 'studentAgencyTypeId', label: 'LOOKUP_TABLES.AGENCY_TYPE', kind: 'ref', width: '150px',
    required: true, ref: { source: 'agencyTypes' }
};

export const LOOKUP_DESCRIPTORS: LookupDescriptor[] = [
    // ── Tab 0, Student ───────────────────────────────────────────────────────
    // STUDENT_GENDER.CODE is what eleven report views split the male and female
    // columns on (V075), so it is required here: a gender with no code is counted
    // in neither column. The entity did not map it until 1 Sep 2026, which is how
    // a row with CODE NULL reached the table through this very screen.
    standard('student-gender', 'studentGender',
        [{ key: 'code', label: 'LOOKUP_TABLES.CODE', kind: 'code', width: '140px', required: true, dir: 'ltr', wrap: true }]),
    standard('student-degree', 'studentDegree'),
    standard('student-current-level', 'studentCurrentLevel', [orderNo('studentCurrentLevelOrderNo')]),
    standard('student-agency-type', 'studentAgencyType', [orderNo('studentAgencyTypeOrder')]),
    standard('student-ministry-type', 'studentMinistryType', [agencyRef]),
    standard('student-study-type', 'studentStudyType'),
    // STUDENT_STUDY_STATUS carries a CODE as well: CodeListRegistry adapts it as
    // the "study-status" code list, so the column is real and worth showing.
    standard('student-study-status', 'studentStudyStatus',
        [{ key: 'code', label: 'LOOKUP_TABLES.CODE', kind: 'code', width: '140px', dir: 'ltr', wrap: true }]),
    bilingual('death-procedures'),

    // ── Tab 1, Study & University ────────────────────────────────────────────
    standard('study-status', 'status'),
    standard('study-degree-in', 'studyDegreeIn'),
    standard('student-university-type', 'studentUniversityType', [agencyRef]),
    standard('student-major', 'studentMajor'),
    standard('study-received-whom-status', 'studyReceivedWhomStatus'),

    // ── Tab 2, Finance ───────────────────────────────────────────────────────
    bilingual('wafar-spending-statuses'),
    bilingual('wafar-reconciliation-statuses'),
    bilingual('payment-auth-statuses'),
    bilingual('payment-amount-sources'),
    // LU_BOOKS_ALLOWANCE_PAY_STATUS.CODE and LU_LOAN_STATUS.CODE are NVARCHAR(10).
    bilingual('books-allowance-pay-statuses', [], 10),
    bilingual('loan-statuses', [], 10),

    // ── Tab 3, Operations ────────────────────────────────────────────────────
    bilingual('change-request-statuses'),
    bilingual('quarter-numbers', [], 10),
    standard('mohe-main', 'moheMain'),
    standard('mohe-sub-main', 'moheSubMain', [
        {
            key: 'moheMainId', label: 'LOOKUP_TABLES.MOHE_MAIN', kind: 'ref', width: '160px',
            required: true, ref: { source: 'moheMains' }
        },
        orderNo('moheSubMainOrder')
    ]),

    // ── Tab 4, System ────────────────────────────────────────────────────────
    bilingual('notification-channels'),
    bilingual('notification-recipient-types'),
    bilingual('notification-scopes'),
    bilingual('student-portal-account-statuses'),
    bilingual('report-dimensions', [
        { key: 'groupCode', label: 'LOOKUP_TABLES.COLUMN_HEADERS.GROUP_CODE', kind: 'text', width: '140px', required: true, dir: 'ltr', wrap: true }
    ]),
    bilingual('trend-classes', [
        { key: 'category', label: 'LOOKUP_TABLES.COLUMN_HEADERS.CATEGORY', kind: 'text', width: '130px', required: true, dir: 'ltr', wrap: true }
    ]),
    // A caption may have no English text (LBL_YEAR_SUFFIX "م"): FIX-PLAN P5-6.
    bilingual('report-labels', [], undefined, true),

    // ── Tab 5, Location ──────────────────────────────────────────────────────
    standard('country', 'studentCountry', [orderNo('studentCountryOrderNo')], 'studentCountryId'),
    standard('governorate', 'studentGov', [
        {
            key: 'studentCountryId', label: 'LOOKUP_TABLES.COUNTRY', kind: 'ref', width: '150px',
            required: true, ref: { source: 'countries' }
        }
    ]),
    standard('municipality', 'studentMud', [
        {
            key: 'studentCountryId', label: 'LOOKUP_TABLES.COUNTRY', kind: 'ref', width: '150px',
            required: true, ref: { source: 'countries' }
        },
        {
            // STUDENT_GOV_MUD.STUDENT_GOV_ID is a varchar while STUDENT_GOV's key
            // is an int, which is why the view dialog used to say "N/A" for every
            // municipality's governorate: it compared the two with ===.
            key: 'studentGovId', label: 'LOOKUP_TABLES.GOVERNORATE', kind: 'ref', width: '150px',
            required: true,
            ref: { source: 'governorates', parent: 'studentCountryId', asString: true }
        }
    ])
];

const BY_ID = new Map(LOOKUP_DESCRIPTORS.map(d => [d.id, d]));

export const describeLookup = (id: string | undefined | null): LookupDescriptor | undefined =>
    id ? BY_ID.get(id) : undefined;
