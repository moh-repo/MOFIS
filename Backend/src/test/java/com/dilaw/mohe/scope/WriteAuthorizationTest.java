package com.dilaw.mohe.scope;

import com.dilaw.mohe.controler.PaymentAuthController;
import com.dilaw.mohe.controler.StudentBooksAllowanceController;
import com.dilaw.mohe.controler.StudentDataController;
import com.dilaw.mohe.controler.StudentExtensionController;
import com.dilaw.mohe.controler.StudentFinanceController;
import com.dilaw.mohe.controler.StudentLoanController;
import com.dilaw.mohe.controler.StudentTravelTicketController;
import com.dilaw.mohe.controler.StudyUniversityFeeController;
import com.dilaw.mohe.dto.PaymentAuthBulkAuthorizeRequest;
import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.studentportal.StudentChangeRequestReviewController;
import com.dilaw.mohe.studentportal.StudentUserAdminController;
import com.dilaw.mohe.systemsettings.controller.SysSettingsController;
import com.dilaw.mohe.systemsettings.dto.SysSettingsDto;
import com.dilaw.mohe.ums.controler.UmsAuthController;
import com.dilaw.mohe.wafar.controller.WafarController;
import com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reads and writes are two different permissions.
 *
 * <p>The class-level gates on the finance, student and settings controllers are
 * "VIEW or EDIT", which is right for reads and, until August 2026, was also the
 * only gate on the writes. Seven roles hold {@code FINANCE.VIEW} and four hold
 * {@code FINANCE.EDIT}; the other three could post and delete finance rows
 * through the API while the UI merely hid the buttons. The same for
 * {@code STUDENTS.VIEW} against student deletion, and {@code SETTINGS.VIEW}
 * against settings writes — and the admin reset-password endpoint under the
 * open {@code /auth} prefix had no gate at all.
 *
 * <p>Each denial is paired with the same call under the write authority, which
 * must get <em>past</em> authorization (and then fail on the nonexistent id it
 * is given, or on the request body) — so a gate that refuses everything cannot
 * pass as a working one. Nothing here writes to the database: every id is one
 * that does not exist.
 *
 * <p>The third pass (27 Aug 2026, plan items 1.1, 1.2, 1.4, 1.5) closed the
 * same gap on Wafar spending, payment authorisation, the portal-account
 * administration screens, and the finance / travel / extension sub-resources;
 * the tests from {@link #wafarWrites} on cover those.
 */
@Tag("integration")
@SpringBootTest
class WriteAuthorizationTest {

    private static final int NO_SUCH_ID = -424242;

    @Autowired private StudentFinanceController finance;
    @Autowired private StudentDataController students;
    @Autowired private SysSettingsController settings;
    @Autowired private UmsAuthController umsAuth;
    @Autowired private WafarController wafar;
    @Autowired private PaymentAuthController paymentAuth;
    @Autowired private StudentUserAdminController portalUsers;
    @Autowired private StudentChangeRequestReviewController changeRequests;
    @Autowired private StudentLoanController loans;
    @Autowired private StudentBooksAllowanceController books;
    @Autowired private StudyUniversityFeeController universityFees;
    @Autowired private StudentTravelTicketController travel;
    @Autowired private StudentExtensionController extensions;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** {@code @httpMethodSecurity.isGetRequest()} reads the current request; give it one. */
    private static void currentRequest(String method) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/mohe/api/v1/x");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    private static void assertDenied(String what, List<String> authorities, Supplier<?> call) {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities, call))
                .as("%s with only %s", what, authorities)
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Past the gate: whatever fails next, it is not authorization. */
    private static void assertPermitted(String what, List<String> authorities, Supplier<?> call) {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities, call))
                .as("positive control: %s with %s must get past authorization", what, authorities)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("FINANCE.VIEW cannot delete or create a finance row; FINANCE.EDIT can")
    void financeWrites() {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/mohe/api/v1/studentFinance/" + NO_SUCH_ID);
        assertDenied("finance delete", List.of("FINANCE.VIEW"), () -> finance.delete(NO_SUCH_ID, req));
        assertPermitted("finance delete", List.of("FINANCE.EDIT"), () -> {
            Object r = finance.delete(NO_SUCH_ID, req);
            // The controller catches the not-found and answers 400; make that visible as a throwable.
            throw new IllegalStateException("past authorization: " + r);
        });
        assertDenied("finance status", List.of("FINANCE.VIEW"), () -> finance.updateStatus(NO_SUCH_ID, 1, req));
    }

    @Test
    @DisplayName("STUDENTS.VIEW cannot delete a student; STUDENTS.DELETE can")
    void studentDelete() {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/mohe/api/v1/studentdata/" + NO_SUCH_ID);
        assertDenied("student delete", List.of("STUDENTS.VIEW"), () -> students.deleteStudentDatum(NO_SUCH_ID, req));
        assertDenied("student delete", List.of("STUDENTS.EDIT"), () -> students.deleteStudentDatum(NO_SUCH_ID, req));
        assertPermitted("student delete", List.of("STUDENTS.DELETE"), () -> {
            Object r = students.deleteStudentDatum(NO_SUCH_ID, req);
            throw new IllegalStateException("past authorization: " + r);
        });
    }

    @Test
    @DisplayName("SETTINGS.VIEW reads settings but cannot write them; SETTINGS.EDIT can")
    void settingsWrites() {
        currentRequest("PUT");
        assertDenied("settings update", List.of("SETTINGS.VIEW"), () -> settings.updateSettings(new SysSettingsDto()));
        currentRequest("GET");
        assertPermitted("settings read", List.of("SETTINGS.VIEW"), () -> {
            Object r = settings.getSettings();
            throw new IllegalStateException("past authorization: " + r);
        });
        // The write clause on its own: a GET without VIEW is admitted only by SETTINGS.EDIT.
        // (Proved on a read so the positive control never writes a blank settings row.)
        assertPermitted("settings write clause", List.of("SETTINGS.EDIT"), () -> {
            Object r = settings.getSettings();
            throw new IllegalStateException("past authorization: " + r);
        });
    }

    @Test
    @DisplayName("the admin reset-password endpoint needs UMS_ADMIN.EDIT, not merely a login")
    void adminResetPassword() {
        Map<String, Object> body = Map.of("userId", NO_SUCH_ID, "newPassword", "irrelevant");
        assertDenied("reset-password", List.of(), () -> umsAuth.resetPassword(body));
        assertDenied("reset-password", List.of("FINANCE.EDIT", "STUDENTS.EDIT"), () -> umsAuth.resetPassword(body));
        assertPermitted("reset-password", List.of("UMS_ADMIN.EDIT"), () -> umsAuth.resetPassword(body));
    }

    /** For controller methods that return void: the call itself is the positive control. */
    private static Supplier<Object> run(Runnable body) {
        return () -> {
            body.run();
            throw new IllegalStateException("past authorization: returned normally");
        };
    }

    @Test
    @DisplayName("WAFAR.VIEW cannot record, approve or cancel a spend; WAFAR.EDIT can (1.1)")
    void wafarWrites() {
        WafarSpendingCreateDto dto = new WafarSpendingCreateDto();
        dto.setCategoryId(NO_SUCH_ID); // refused by the service before anything is written
        List<String> viewOnly = List.of("WAFAR.VIEW", "WAFAR.EXPORT");
        assertDenied("wafar create", viewOnly, () -> wafar.createTransaction(dto));
        assertDenied("wafar approve", viewOnly, () -> wafar.approveTransaction(NO_SUCH_ID));
        assertDenied("wafar reject", viewOnly,
                () -> wafar.rejectTransaction(NO_SUCH_ID, new com.dilaw.mohe.wafar.dto.WafarRejectRequest("no")));
        assertDenied("wafar cancel", viewOnly, () -> wafar.cancelTransaction(NO_SUCH_ID));
        assertPermitted("wafar create", List.of("WAFAR.ADD"), () -> wafar.createTransaction(dto));
        assertPermitted("wafar approve", List.of("WAFAR.EDIT"), () -> wafar.approveTransaction(NO_SUCH_ID));
        assertPermitted("wafar cancel", List.of("WAFAR.DELETE"), () -> wafar.cancelTransaction(NO_SUCH_ID));
    }

    @Test
    @DisplayName("FINANCE.VIEW cannot create, edit or release a payment authorisation (1.2)")
    void paymentAuthWrites() {
        List<String> viewOnly = List.of("FINANCE.VIEW", "PAYMENT_AUTH.VIEW");
        PaymentAuthBulkAuthorizeRequest release = new PaymentAuthBulkAuthorizeRequest(List.of(NO_SUCH_ID));
        assertDenied("payment-auth create", viewOnly, () -> paymentAuth.create(new PaymentAuth()));
        assertDenied("payment-auth update", viewOnly, () -> paymentAuth.update(NO_SUCH_ID, new PaymentAuth()));
        assertDenied("payment-auth delete", viewOnly, () -> paymentAuth.delete(NO_SUCH_ID));
        assertDenied("bulk-authorize", viewOnly, () -> paymentAuth.bulkAuthorize(release));
        // EDIT writes records but does not release money: the APPROVE code is its own gate.
        assertDenied("bulk-authorize", List.of("PAYMENT_AUTH.EDIT", "FINANCE.EDIT"), () -> paymentAuth.bulkAuthorize(release));
        assertPermitted("payment-auth update", List.of("PAYMENT_AUTH.EDIT"), () -> paymentAuth.update(NO_SUCH_ID, new PaymentAuth()));
        assertPermitted("payment-auth delete", List.of("FINANCE.EDIT"), () -> paymentAuth.delete(NO_SUCH_ID));
        // A batch of one unknown id is reported under "failed", not thrown: make the return visible.
        assertPermitted("bulk-authorize", List.of("PAYMENT_AUTH.APPROVE"), run(() -> paymentAuth.bulkAuthorize(release)));
    }

    @Test
    @DisplayName("STUDENTS.VIEW cannot reset a portal password or decide a change request (1.4)")
    void portalAdministrationWrites() {
        Map<String, String> newPassword = Map.of("newPassword", "irrelevant-1");
        assertDenied("portal reset-password", List.of("STUDENTS.VIEW"), () -> portalUsers.resetPassword(NO_SUCH_ID, newPassword));
        assertDenied("portal status", List.of("STUDENTS.VIEW"), () -> portalUsers.updateStatus(NO_SUCH_ID, Map.of("status", "LOCKED")));
        assertPermitted("portal reset-password", List.of("STUDENTS.EDIT"), () -> portalUsers.resetPassword(NO_SUCH_ID, newPassword));

        assertDenied("change-request approve", List.of("STUDENTS.VIEW"), () -> changeRequests.approve(NO_SUCH_ID, null));
        assertDenied("change-request reject", List.of("STUDENTS.VIEW"), () -> changeRequests.reject(NO_SUCH_ID, Map.of()));
        // The controller turns the 404 into a ResponseEntity; surface it as "returned normally".
        assertPermitted("change-request approve", List.of("STUDENTS.EDIT"), run(() -> changeRequests.approve(NO_SUCH_ID, null)));
    }

    @Test
    @DisplayName("finance, travel and extension sub-resources: VIEW reads, only the write codes write (1.5)")
    void financeTravelExtensionWrites() {
        MockHttpServletRequest req = new MockHttpServletRequest("DELETE", "/mohe/api/v1/x");
        // The finance and fee archives no longer have a delete to gate: the write
        // endpoints went with live QA H2 (24 Sep 2026); StudentArchiveEndpointsTest
        // holds that they stay gone.

        assertDenied("loan delete", List.of("FINANCE.VIEW"), run(() -> loans.delete(NO_SUCH_ID)));
        assertPermitted("loan delete", List.of("FINANCE.EDIT"), run(() -> loans.delete(NO_SUCH_ID)));

        assertDenied("books delete", List.of("FINANCE.VIEW"), run(() -> books.delete(NO_SUCH_ID)));
        assertPermitted("books delete", List.of("FINANCE.EDIT"), run(() -> books.delete(NO_SUCH_ID)));

        assertDenied("university-fee delete", List.of("FINANCE.VIEW"), () -> universityFees.delete(NO_SUCH_ID, req));
        assertPermitted("university-fee delete", List.of("FINANCE.EDIT"), run(() -> universityFees.delete(NO_SUCH_ID, req)));

        assertDenied("travel delete", List.of("STUDENT_TRAVEL.VIEW", "STUDENT_TRAVEL.EXPORT"), () -> travel.delete(NO_SUCH_ID));
        assertPermitted("travel delete", List.of("STUDENT_TRAVEL.DELETE"), run(() -> travel.delete(NO_SUCH_ID)));

        assertDenied("extension delete", List.of("STUDENT_EXTENSION.VIEW", "STUDENT_EXTENSION.EXPORT"), () -> extensions.delete(NO_SUCH_ID, req));
        assertPermitted("extension delete", List.of("STUDENT_EXTENSION.DELETE"), run(() -> extensions.delete(NO_SUCH_ID, req)));
    }
}
