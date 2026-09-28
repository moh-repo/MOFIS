package com.dilaw.mohe.service.codelist;

import com.dilaw.mohe.model.AbstractLuLookup;
import com.dilaw.mohe.model.ChangeRequestField;
import com.dilaw.mohe.model.LuAcademicYear;
import com.dilaw.mohe.model.LuAlertSeverity;
import com.dilaw.mohe.model.LuAlertStatus;
import com.dilaw.mohe.model.LuAlertType;
import com.dilaw.mohe.model.LuAllocationStatus;
import com.dilaw.mohe.model.LuAllocationType;
import com.dilaw.mohe.model.LuApprovalAction;
import com.dilaw.mohe.model.LuApprovalRequestType;
import com.dilaw.mohe.model.LuApprovalStatus;
import com.dilaw.mohe.model.LuAppropriationStatus;
import com.dilaw.mohe.model.LuBooksAllowancePayStatus;
import com.dilaw.mohe.model.LuBooleanFilter;
import com.dilaw.mohe.model.LuChangeRequestStatus;
import com.dilaw.mohe.model.LuDeathProcedure;
import com.dilaw.mohe.model.LuFeeCheckPayor;
import com.dilaw.mohe.model.LuFeeSource;
import com.dilaw.mohe.model.LuFieldType;
import com.dilaw.mohe.model.LuFiscalPeriodStatus;
import com.dilaw.mohe.model.LuFiscalYearStatus;
import com.dilaw.mohe.model.LuGraphCategory;
import com.dilaw.mohe.model.LuHttpMethod;
import com.dilaw.mohe.model.LuInfoRequestStatus;
import com.dilaw.mohe.model.LuInfoReviewStatus;
import com.dilaw.mohe.model.LuInquiryStatus;
import com.dilaw.mohe.model.LuInquiryType;
import com.dilaw.mohe.model.LuLoanStatus;
import com.dilaw.mohe.model.LuPaymentMethod;
import com.dilaw.mohe.model.LuNotificationChannel;
import com.dilaw.mohe.model.LuNotificationRecipientType;
import com.dilaw.mohe.model.LuNotificationScope;
import com.dilaw.mohe.model.LuNotificationSendStatus;
import com.dilaw.mohe.model.LuPaymentAmountSource;
import com.dilaw.mohe.model.LuPaymentAuthStatus;
import com.dilaw.mohe.model.LuPaymentBatchStatus;
import com.dilaw.mohe.model.LuPermissionActionType;
import com.dilaw.mohe.model.LuPermissionResourceType;
import com.dilaw.mohe.model.LuQuarterNumber;
import com.dilaw.mohe.model.LuReportLabel;
import com.dilaw.mohe.model.LuResourceKind;
import com.dilaw.mohe.model.LuSemesterNumber;
import com.dilaw.mohe.model.LuStatusActive;
import com.dilaw.mohe.model.LuStudentPortalAccountStatus;
import com.dilaw.mohe.model.LuUniversityFeeStatus;
import com.dilaw.mohe.model.LuUserPrivilege;
import com.dilaw.mohe.model.LuUserStatus;
import com.dilaw.mohe.model.LuVisibilityFilter;
import com.dilaw.mohe.model.LuWafarBeneficiaryType;
import com.dilaw.mohe.model.LuWafarReconciliationStatus;
import com.dilaw.mohe.model.LuWafarSpendingStatus;
import com.dilaw.mohe.model.LuWhatsAppMessageMode;
import com.dilaw.mohe.model.LuYesNo;
import com.dilaw.mohe.model.LuAccountNature;
import com.dilaw.mohe.model.LuNoticeStatus;
import com.dilaw.mohe.model.LuPosition;
import com.dilaw.mohe.model.LuDeathCaseStatus;
import com.dilaw.mohe.model.LuScheduleStatus;
import com.dilaw.mohe.model.LuRunStatus;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every code list the application knows, by the name the API and the
 * frontend use ({@code alert-severity}, {@code approval-status} …).
 *
 * <p>Two kinds. An {@link LuList} is backed by an {@code AbstractLuLookup}
 * entity and can be edited through the code-list API. An {@link Adapter}
 * reads a table of another shape (the study statuses, the fee types, the
 * approval entity types …) through a SELECT that projects the same columns,
 * and is read-only here — those tables have their own screens.
 *
 * <p>The SELECT of every entry yields, in this order: id, code, name_ar,
 * name_en, sort_order, ui_severity, icon, is_system, active, ui_color.
 */
public final class CodeListRegistry {

    private CodeListRegistry() {
    }

    /** A list backed by a MOHECORE.LU_* entity. */
    public record LuList(String name, Class<? extends AbstractLuLookup> entity, String extraColumn) {
        public String table() {
            Table t = entity.getAnnotation(Table.class);
            return t.schema() + "." + t.name();
        }
    }

    /** A read-only projection of a table of another shape. */
    public record Adapter(String name, String table, List<String> requiredColumns, String sql) {
    }

    private static final Map<String, LuList> LU = new LinkedHashMap<>();
    private static final Map<String, Adapter> ADAPTERS = new LinkedHashMap<>();

    private static void lu(String name, Class<? extends AbstractLuLookup> entity) {
        LU.put(name, new LuList(name, entity, null));
    }

    private static void lu(String name, Class<? extends AbstractLuLookup> entity, String extraColumn) {
        LU.put(name, new LuList(name, entity, extraColumn));
    }

    static {
        // Names that existed on /api/v1/enums before V072 keep their spelling.
        lu("request-type", LuApprovalRequestType.class);
        lu("approval-status", LuApprovalStatus.class);
        lu("ums-user-status", LuUserStatus.class);
        lu("ums-user-privilege", LuUserPrivilege.class);
        lu("http-method", LuHttpMethod.class);
        lu("permission-resource-type", LuPermissionResourceType.class);
        lu("permission-action-type", LuPermissionActionType.class);
        lu("ums-role-kind", LuResourceKind.class);
        lu("ums-group-kind", LuResourceKind.class);
        lu("ums-field-type", LuFieldType.class);
        lu("boolean-filter", LuBooleanFilter.class);
        lu("visibility-filter", LuVisibilityFilter.class);
        lu("death-procedure", LuDeathProcedure.class);
        // The lists business code used to hard-code (V072).
        lu("alert-severity", LuAlertSeverity.class);
        lu("alert-status", LuAlertStatus.class);
        lu("wafar-spending-status", LuWafarSpendingStatus.class);
        lu("wafar-reconciliation-status", LuWafarReconciliationStatus.class);
        lu("payment-auth-status", LuPaymentAuthStatus.class);
        lu("payment-amount-source", LuPaymentAmountSource.class);
        lu("change-request-status", LuChangeRequestStatus.class);
        lu("portal-account-status", LuStudentPortalAccountStatus.class);
        lu("notification-channel", LuNotificationChannel.class);
        lu("whatsapp-message-mode", LuWhatsAppMessageMode.class);
        lu("notification-recipient-type", LuNotificationRecipientType.class);
        lu("notification-scope", LuNotificationScope.class);
        lu("quarter-number", LuQuarterNumber.class);
        lu("semester-number", LuSemesterNumber.class);
        lu("academic-year", LuAcademicYear.class);
        lu("loan-status", LuLoanStatus.class);
        lu("payment-method", LuPaymentMethod.class);
        lu("books-allowance-pay-status", LuBooksAllowancePayStatus.class);
        lu("university-fee-status", LuUniversityFeeStatus.class);
        lu("fee-source", LuFeeSource.class);
        lu("fee-check-payor", LuFeeCheckPayor.class);
        lu("status-active", LuStatusActive.class);
        lu("yes-no", LuYesNo.class);
        lu("report-label", LuReportLabel.class);
        // New in V073.
        lu("alert-type", LuAlertType.class);
        lu("wafar-beneficiary-type", LuWafarBeneficiaryType.class);
        lu("notification-send-status", LuNotificationSendStatus.class);
        lu("approval-action", LuApprovalAction.class);
        lu("account-nature", LuAccountNature.class, "NORMAL_BALANCE");
        lu("allocation-type", LuAllocationType.class);
        lu("allocation-status", LuAllocationStatus.class);
        lu("payment-batch-status", LuPaymentBatchStatus.class);
        lu("appropriation-status", LuAppropriationStatus.class);
        lu("fiscal-year-status", LuFiscalYearStatus.class);
        lu("fiscal-period-status", LuFiscalPeriodStatus.class);
        lu("inquiry-type", LuInquiryType.class);
        lu("inquiry-status", LuInquiryStatus.class);
        lu("info-request-status", LuInfoRequestStatus.class);
        lu("info-review-status", LuInfoReviewStatus.class);
        lu("graph-category", LuGraphCategory.class, "UI_COLOR");
        lu("change-request-field", ChangeRequestField.class, "LOOKUP_LIST");
        // New in V074.
        lu("notice-status", LuNoticeStatus.class);
        lu("death-case-status", LuDeathCaseStatus.class);
        lu("schedule-status", LuScheduleStatus.class);
        lu("run-status", LuRunStatus.class);
        // New in V093 (security plan N-03): staff positions, rank = SORT_ORDER.
        lu("position", LuPosition.class);

        ADAPTERS.put("study-status", new Adapter("study-status", "MOHECORE.STUDENT_STUDY_STATUS",
                List.of("CODE", "UI_COLOR", "UI_SEVERITY", "ICON"),
                "SELECT STUDENT_STUDY_STATUS_ID, CODE, STUDENT_STUDY_STATUS_NAME, STUDENT_STUDY_STATUS_NAME_EN, "
                        + "STUDENT_STUDY_STATUS_ID, UI_SEVERITY, ICON, 0, ISNULL(IS_ACTIVE, 1), UI_COLOR "
                        + "FROM MOHECORE.STUDENT_STUDY_STATUS WHERE CODE IS NOT NULL ORDER BY 1"));
        ADAPTERS.put("fin-received-status", new Adapter("fin-received-status", "MOHECORE.STUDY_FIN_RECEIVED_STATUS",
                List.of("CODE", "UI_SEVERITY", "ICON"),
                "SELECT STUDY_FIN_RECEIVED_STATUS_ID, CODE, STUDY_FIN_RECEIVED_STATUS_NAME, STUDY_FIN_RECEIVED_STATUS_NAME_EN, "
                        + "STUDY_FIN_RECEIVED_STATUS_ID, UI_SEVERITY, ICON, 0, ISNULL(IS_ACTIVE, 1), NULL "
                        + "FROM MOHECORE.STUDY_FIN_RECEIVED_STATUS WHERE CODE IS NOT NULL ORDER BY 1"));
        ADAPTERS.put("pay-fee-status", new Adapter("pay-fee-status", "MOHECORE.STUDY_PAY_FEE_IN_STATUS",
                List.of("CODE"),
                "SELECT STUDY_PAY_FEE_IN_STATUS_ID, CODE, STUDY_PAY_FEE_IN_STATUS_NAME, STUDY_PAY_FEE_IN_STATUS_NAME_EN, "
                        + "STUDY_PAY_FEE_IN_STATUS_ID, NULL, NULL, 0, ISNULL(IS_ACTIVE, 1), NULL "
                        + "FROM MOHECORE.STUDY_PAY_FEE_IN_STATUS WHERE CODE IS NOT NULL ORDER BY 1"));
        ADAPTERS.put("university-type", new Adapter("university-type", "MOHECORE.STUDENT_UNIVERSITY_TYPE",
                List.of("CODE"),
                "SELECT STUDENT_UNIVERSITY_TYPE_ID, CODE, STUDENT_UNIVERSITY_TYPE_NAME, STUDENT_UNIVERSITY_TYPE_NAME_EN, "
                        + "STUDENT_UNIVERSITY_TYPE_ID, NULL, NULL, 0, 1, NULL "
                        + "FROM MOHECORE.STUDENT_UNIVERSITY_TYPE WHERE CODE IS NOT NULL ORDER BY 1"));
        ADAPTERS.put("ministry-type", new Adapter("ministry-type", "MOHECORE.STUDENT_MINISTRY_TYPE",
                List.of("CODE"),
                "SELECT STUDENT_MINISTRY_TYPE_ID, CODE, STUDENT_MINISTRY_TYPE_NAME, STUDENT_MINISTRY_TYPE_NAME_EN, "
                        + "STUDENT_MINISTRY_TYPE_ID, NULL, NULL, 0, 1, NULL "
                        + "FROM MOHECORE.STUDENT_MINISTRY_TYPE WHERE CODE IS NOT NULL ORDER BY 1"));
        // The kind of a study extension (QA SP-009): the portal names it from this
        // list, by the code the server resolves from the stored English name.
        ADAPTERS.put("extension-type", new Adapter("extension-type", "MOHECORE.EXTENSION_TYPE",
                List.of("EXTENSION_TYPE_CODE"),
                "SELECT EXTENSION_TYPE_ID, EXTENSION_TYPE_CODE, EXTENSION_TYPE_NAME_AR, EXTENSION_TYPE_NAME_EN, "
                        + "ISNULL(DISPLAY_ORDER, EXTENSION_TYPE_ID), NULL, NULL, 0, ISNULL(IS_ACTIVE, 1), NULL "
                        + "FROM MOHECORE.EXTENSION_TYPE ORDER BY 5, 1"));
        ADAPTERS.put("extension-status", new Adapter("extension-status", "MOHECORE.EXTENSION_STATUS",
                List.of("EXTENSION_STATUS_CODE"),
                "SELECT EXTENSION_STATUS_ID, EXTENSION_STATUS_CODE, EXTENSION_STATUS_NAME_AR, EXTENSION_STATUS_NAME_EN, "
                        + "ISNULL(DISPLAY_ORDER, EXTENSION_STATUS_ID), NULL, NULL, 0, ISNULL(IS_ACTIVE, 1), STATUS_COLOR "
                        + "FROM MOHECORE.EXTENSION_STATUS ORDER BY 5, 1"));
        ADAPTERS.put("fee-type", new Adapter("fee-type", "FINCORE.FEE_TYPE",
                List.of("FEE_TYPE_CODE", "NAME_AR", "NAME_EN"),
                "SELECT FEE_TYPE_ID, FEE_TYPE_CODE, NAME_AR, NAME_EN, FEE_TYPE_ID, NULL, NULL, 0, ISNULL(IS_ACTIVE, 1), NULL "
                        + "FROM FINCORE.FEE_TYPE ORDER BY 1"));
        ADAPTERS.put("travel-type", new Adapter("travel-type", "MOHECORE.LU_TRAVEL_TYPE",
                List.of("TYPE_CODE", "TYPE_NAME", "TYPE_NAME_AR"),
                "SELECT TRAVEL_TYPE_ID, TYPE_CODE, TYPE_NAME_AR, TYPE_NAME, ISNULL(SORT_ORDER, TRAVEL_TYPE_ID), "
                        + "NULL, NULL, 0, ISNULL(ACTIVE, 1), NULL "
                        + "FROM MOHECORE.LU_TRAVEL_TYPE ORDER BY 5, 1"));
        // The kind of an information request (QA SP-007): the portal printed
        // STATUS_CERT. English from DISPLAY_NAME, which V123 fills.
        ADAPTERS.put("info-request-kind", new Adapter("info-request-kind", "MOHECORE.STUDENT_INFO_REQUEST_KIND",
                List.of("KIND_CODE", "DISPLAY_NAME", "DISPLAY_NAME_AR"),
                "SELECT KIND_ID, KIND_CODE, ISNULL(NULLIF(DISPLAY_NAME_AR, ''), DISPLAY_NAME), "
                        + "ISNULL(NULLIF(DISPLAY_NAME, ''), KIND_CODE), ISNULL(SORT_ORDER, KIND_ID), NULL, NULL, 0, "
                        + "ISNULL(IS_ACTIVE, 1), NULL "
                        + "FROM MOHECORE.STUDENT_INFO_REQUEST_KIND ORDER BY 5, 1"));
        // English from EntityTypeEn (V122): the code itself served as the English
        // label, so English screens printed STUDENT_FINANCE_WAFAR (QA B11).
        ADAPTERS.put("approval-entity-type", new Adapter("approval-entity-type", "ALERTCORE.ApprovalConfigs",
                List.of("EntityType", "EntityTypeAr", "EntityTypeEn"),
                "SELECT Id, EntityType, EntityTypeAr, ISNULL(NULLIF(EntityTypeEn, ''), EntityType), Id, NULL, NULL, 0, "
                        + "ISNULL(IsActive, 1), NULL "
                        + "FROM ALERTCORE.ApprovalConfigs ORDER BY 1"));
    }

    public static Map<String, LuList> luLists() {
        return Collections.unmodifiableMap(LU);
    }

    public static Map<String, Adapter> adapters() {
        return Collections.unmodifiableMap(ADAPTERS);
    }

    public static Optional<LuList> lu(String name) {
        return Optional.ofNullable(LU.get(name));
    }

    public static Optional<Adapter> adapter(String name) {
        return Optional.ofNullable(ADAPTERS.get(name));
    }

    public static boolean exists(String name) {
        return LU.containsKey(name) || ADAPTERS.containsKey(name);
    }

    /** Every list name, LU lists first, in registration order. */
    public static List<String> names() {
        List<String> all = new java.util.ArrayList<>(LU.keySet());
        all.addAll(ADAPTERS.keySet());
        return all;
    }

    /** The SELECT that loads a list, in the shared column order. */
    public static String selectFor(String name) {
        LuList lu = LU.get(name);
        if (lu != null) {
            String extra = lu.extraColumn() == null ? "NULL" : lu.extraColumn();
            return "SELECT ID, CODE, NAME_AR, NAME_EN, SORT_ORDER, UI_SEVERITY, ICON, IS_SYSTEM, "
                    + "CASE WHEN IS_DELETED = 1 THEN 0 ELSE 1 END, " + extra
                    + " FROM " + lu.table() + " ORDER BY SORT_ORDER, ID";
        }
        Adapter a = ADAPTERS.get(name);
        if (a != null) {
            return a.sql();
        }
        throw new IllegalArgumentException("Unknown code list: " + name);
    }
}
