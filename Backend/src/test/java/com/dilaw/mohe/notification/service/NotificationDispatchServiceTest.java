package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.codes.NotificationChannel;
import com.dilaw.mohe.codes.NotificationSendStatus;
import com.dilaw.mohe.codes.NotificationRecipientType;
import com.dilaw.mohe.notification.model.NotificationLogRecipient;
import com.dilaw.mohe.notification.model.NotificationSettings;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The dispatch loop's accounting, exercised by calling {@code dispatch}
 * directly (synchronously — {@code @Async} is a proxy concern, not the loop's).
 * The outcome must always reach {@link NotificationService#recordOutcome}: SENT
 * when everything goes, PARTIAL on a mix, and FAILED even when the loop itself
 * blows up — a batch must never stay PENDING forever.
 *
 * <p>Since V140 the outcome also carries one row per recipient — who, the
 * address the channel used, and whether it went — which is what the history
 * page's view lists. The rows are captured here and read back.
 */
class NotificationDispatchServiceTest {

    private static RecipientContact student(long id, String name, String email, String phone) {
        return new RecipientContact(NotificationRecipientType.STUDENT, id, name, email, phone);
    }

    /** The recipient rows handed to recordOutcome, in dispatch order. */
    private List<NotificationLogRecipient> recordedRows() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationLogRecipient>> rows = ArgumentCaptor.forClass(List.class);
        verify(notificationService).recordOutcome(any(), anyInt(), anyInt(), anyString(), any(), rows.capture());
        return rows.getValue();
    }

    private EmailService emailService;
    private SmsService smsService;
    private WhatsAppService whatsAppService;
    private NotificationService notificationService;
    private NotificationDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        smsService = mock(SmsService.class);
        whatsAppService = mock(WhatsAppService.class);
        notificationService = mock(NotificationService.class);
        dispatchService = new NotificationDispatchService(emailService, smsService, whatsAppService, notificationService);
    }

    @Test
    @DisplayName("all sends succeed → SENT with the full count")
    void allSent() {
        when(emailService.send(any(), anyString(), anyString(), anyString())).thenReturn(null);

        dispatchService.dispatch(9L, NotificationChannel.EMAIL, new NotificationSettings(), "s", "b",
                List.of(student(1, "Amal", "a@x", null), student(2, "Basim", "b@x", null)));

        verify(notificationService).recordOutcome(eq(9L), eq(2), eq(0),
                eq(NotificationSendStatus.SENT), isNull(), anyList());

        List<NotificationLogRecipient> rows = recordedRows();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getRecipientName()).isEqualTo("Amal");
        assertThat(rows.get(0).getRecipientId()).isEqualTo(1L);
        assertThat(rows.get(0).getRecipientType()).isEqualTo(NotificationRecipientType.STUDENT);
        assertThat(rows.get(0).getAddress()).isEqualTo("a@x");
        assertThat(rows.get(0).getStatus()).isEqualTo(NotificationSendStatus.SENT);
        assertThat(rows.get(0).getError()).isNull();
        assertThat(rows.get(1).getAddress()).isEqualTo("b@x");
    }

    @Test
    @DisplayName("a mix of success and failure → PARTIAL, with the error summary")
    void partial() {
        when(emailService.send(any(), eq("a@x"), anyString(), anyString())).thenReturn(null);
        when(emailService.send(any(), eq("b@x"), anyString(), anyString())).thenReturn("mailbox full");

        dispatchService.dispatch(9L, NotificationChannel.EMAIL, new NotificationSettings(), "s", "b",
                List.of(student(1, "Amal", "a@x", null), student(2, "Basim", "b@x", null)));

        verify(notificationService).recordOutcome(eq(9L), eq(1), eq(1),
                eq(NotificationSendStatus.PARTIAL), eq("b@x: mailbox full"), anyList());

        List<NotificationLogRecipient> rows = recordedRows();
        assertThat(rows.get(1).getStatus()).isEqualTo(NotificationSendStatus.FAILED);
        assertThat(rows.get(1).getError()).isEqualTo("mailbox full");
        assertThat(rows.get(0).getStatus()).isEqualTo(NotificationSendStatus.SENT);
    }

    @Test
    @DisplayName("the loop itself blowing up still records FAILED — never a permanent PENDING")
    void abortRecordsFailed() {
        when(emailService.send(any(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("SMTP config exploded"));

        dispatchService.dispatch(9L, NotificationChannel.EMAIL, new NotificationSettings(), "s", "b",
                List.of(student(1, "Amal", "a@x", null)));

        // The abort path records the counts alone: no per-recipient row can be trusted.
        verify(notificationService).recordOutcome(eq(9L), eq(0), anyInt(),
                eq(NotificationSendStatus.FAILED), anyString());
    }

    @Test
    @DisplayName("WhatsApp goes to the phone number, as plain text, and a missing number is a failure")
    void whatsappUsesPhoneAndPlainText() {
        when(whatsAppService.send(any(), anyString(), anyString())).thenReturn(null);

        dispatchService.dispatch(4L, NotificationChannel.WHATSAPP, new NotificationSettings(), "s",
                "<p>Hello <b>there</b></p>",
                List.of(student(1, "Amal", "a@x", "+962790000000"), student(2, "Basim", "b@x", null)));

        verify(whatsAppService).send(any(), eq("+962790000000"), eq("Hello there"));
        verify(emailService, never()).send(any(), anyString(), anyString(), anyString());
        verify(notificationService).recordOutcome(eq(4L), eq(1), eq(1),
                eq(NotificationSendStatus.PARTIAL), eq("(missing phone for recipient)"), anyList());

        // The one with no number is still recorded: a name with no address and why it failed.
        List<NotificationLogRecipient> rows = recordedRows();
        assertThat(rows.get(0).getAddress()).isEqualTo("+962790000000");
        assertThat(rows.get(1).getAddress()).isNull();
        assertThat(rows.get(1).getStatus()).isEqualTo(NotificationSendStatus.FAILED);
        assertThat(rows.get(1).getError()).isEqualTo("(missing phone for recipient)");
    }
}
