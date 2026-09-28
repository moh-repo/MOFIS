package com.dilaw.mohe.approval;

import com.dilaw.mohe.approval.event.ApprovalRequestCreatedEvent;
import com.dilaw.mohe.approval.event.ApprovalRequestDecidedEvent;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalAlertListener;
import com.dilaw.mohe.codes.AlertSeverity;
import com.dilaw.mohe.codes.AlertType;
import com.dilaw.mohe.service.AlertService;
import com.dilaw.mohe.service.codelist.CodeListService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Review round 2, H2: a new approval request is announced in the system notices,
 * in both languages (W3-08), and the notice is resolved once the request is decided.
 */
class ApprovalAlertListenerTest {

    /** The code lists as V122 left them: Arabic and English names; unknown codes come back as the code. */
    private static CodeListService codeLists() {
        CodeListService codes = mock(CodeListService.class);
        when(codes.label(anyString(), anyString(), anyString())).thenAnswer(i -> i.getArgument(1));
        when(codes.label("approval-entity-type", "STUDENT_DATA", "ar")).thenReturn("بيانات الطالب");
        when(codes.label("approval-entity-type", "STUDENT_DATA", "en")).thenReturn("Student data");
        when(codes.label("request-type", "UPDATE", "ar")).thenReturn("تحديث");
        when(codes.label("request-type", "UPDATE", "en")).thenReturn("Update");
        when(codes.label("request-type", "INSERT", "ar")).thenReturn("إضافة");
        when(codes.label("request-type", "INSERT", "en")).thenReturn("Add");
        return codes;
    }

    @Test
    @DisplayName("a new request becomes one notice, in Arabic and English, naming its number, operation and kind of record")
    void announcesTheRequest() {
        AlertService alerts = mock(AlertService.class);
        new ApprovalAlertListener(alerts, codeLists()).onRequestCreated(
                new ApprovalRequestCreatedEvent(42L, "STUDENT_DATA", RequestType.UPDATE));

        verify(alerts).createAlert(eq("طلب موافقة جديد"),
                eq("طلب رقم 42: تحديث — بيانات الطالب. بانتظار الموافقة في صفحة الموافقات المعلقة."),
                eq("New approval request"),
                eq("Request 42: Update — Student data. Awaiting approval on the pending approvals page."),
                eq(AlertType.INFO), eq(AlertSeverity.MEDIUM), eq("APPROVAL:42"));
    }

    @Test
    @DisplayName("an entity type without a name is shown by its code, not dropped")
    void unknownEntityTypeFallsBackToItsCode() {
        AlertService alerts = mock(AlertService.class);
        new ApprovalAlertListener(alerts, codeLists()).onRequestCreated(
                new ApprovalRequestCreatedEvent(7L, "SOMETHING_NEW", RequestType.INSERT));

        verify(alerts).createAlert(anyString(),
                eq("طلب رقم 7: إضافة — SOMETHING_NEW. بانتظار الموافقة في صفحة الموافقات المعلقة."),
                anyString(),
                eq("Request 7: Add — SOMETHING_NEW. Awaiting approval on the pending approvals page."),
                anyString(), anyString(), eq("APPROVAL:7"));
    }

    @Test
    @DisplayName("a notice that cannot be saved does not surface as an error")
    void aFailedNoticeIsSwallowed() {
        AlertService alerts = mock(AlertService.class);
        when(alerts.createAlert(anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> new ApprovalAlertListener(alerts, codeLists()).onRequestCreated(
                new ApprovalRequestCreatedEvent(1L, "STUDENT_LOAN", RequestType.DELETE)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a decided request resolves the notice that announced it")
    void aDecidedRequestResolvesItsNotice() {
        AlertService alerts = mock(AlertService.class);
        new ApprovalAlertListener(alerts, codeLists()).onRequestDecided(
                new ApprovalRequestDecidedEvent(42L, ApprovalStatus.EXECUTED));

        verify(alerts).resolveBySource("APPROVAL:42");
    }

    @Test
    @DisplayName("a notice that cannot be resolved does not undo or fail the decision")
    void aFailedResolveIsSwallowed() {
        AlertService alerts = mock(AlertService.class);
        when(alerts.resolveBySource(anyString())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> new ApprovalAlertListener(alerts, codeLists()).onRequestDecided(
                new ApprovalRequestDecidedEvent(9L, ApprovalStatus.REJECTED)))
                .doesNotThrowAnyException();
    }
}
