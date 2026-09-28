package com.dilaw.mohe.codes;

/**
 * LU_REPORT_LABEL: the labels Java asks for by name outside the templates
 * (the templates' own COL_* / LBL_* parameters are proven by
 * ReportLabelsAreSeededTest). The quarter ordinals are the words the financial
 * statement head writes — "الربع: الثالث" — not the quarter names.
 */
@CodeList(value = "report-label", mode = CodeList.Mode.SUBSET)
public final class ReportLabelCode {

    private ReportLabelCode() {
    }

    public static final String LBL_GENERATED = "LBL_GENERATED";
    public static final String LBL_PAGE = "LBL_PAGE";
    public static final String LBL_OF = "LBL_OF";
    public static final String LBL_UNSPECIFIED = "LBL_UNSPECIFIED";
    public static final String LBL_QUARTER_ORDINAL_1 = "LBL_QUARTER_ORDINAL_1";
    public static final String LBL_QUARTER_ORDINAL_2 = "LBL_QUARTER_ORDINAL_2";
    public static final String LBL_QUARTER_ORDINAL_3 = "LBL_QUARTER_ORDINAL_3";
    public static final String LBL_QUARTER_ORDINAL_4 = "LBL_QUARTER_ORDINAL_4";
    public static final String TPL_ENROLLMENT_CERT_BODY = "TPL_ENROLLMENT_CERT_BODY";
    /** The two buckets of the approvals report (V077). */
    public static final String LBL_APPROVALS_STATE_PENDING = "LBL_APPROVALS_STATE_PENDING";
    public static final String LBL_APPROVALS_STATE_COMPLETED = "LBL_APPROVALS_STATE_COMPLETED";
    /** The entitlements report names loans here: FINCORE.FEE_TYPE has no loan row (V077). */
    public static final String LBL_KIND_LOAN = "LBL_KIND_LOAN";

    /** The ordinal label code of quarter 1..4; null outside that range. */
    public static String quarterOrdinal(Integer quarter) {
        if (quarter == null) return null;
        return switch (quarter) {
            case 1 -> LBL_QUARTER_ORDINAL_1;
            case 2 -> LBL_QUARTER_ORDINAL_2;
            case 3 -> LBL_QUARTER_ORDINAL_3;
            case 4 -> LBL_QUARTER_ORDINAL_4;
            default -> null;
        };
    }
}
