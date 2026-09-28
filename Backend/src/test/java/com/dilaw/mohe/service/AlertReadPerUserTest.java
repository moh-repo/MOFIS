package com.dilaw.mohe.service;

import com.dilaw.mohe.controler.AlertController;
import com.dilaw.mohe.model.Alert;
import com.dilaw.mohe.model.AlertRead;
import com.dilaw.mohe.repository.AlertReadRepository;
import com.dilaw.mohe.repository.AlertRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Live QA N10 (26 Sep 2026): marking an alert read set one flag for everybody, under a
 * {@code readBy} name the request supplied, and the unread count read that flag. Any
 * account could clear every account's alerts. Reads are per account now (V144), and the
 * reader is the signed-in account.
 */
class AlertReadPerUserTest {

    private static final long A = AsUser.ADMIN;
    private static final long B = AsUser.JO_ADMIN;

    private final AlertRepository alerts = mock(AlertRepository.class);
    private final AlertReadRepository reads = mock(AlertReadRepository.class);
    private final AlertService service = new AlertService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "alertRepository", alerts);
        ReflectionTestUtils.setField(service, "alertReadRepository", reads);
    }

    @AfterEach
    void signOut() {
        AsUser.signOut();
    }

    private static Alert alert(int id) {
        Alert a = new Alert("t", "m", "INFO", "LOW");
        a.setAlertId(id);
        return a;
    }

    @Test
    @DisplayName("marking read writes a row for that account only, once, and leaves the alert itself unchanged")
    void markIsPerAccount() {
        Alert five = alert(5);
        when(alerts.findById(5)).thenReturn(Optional.of(five));
        when(reads.existsByAlertIdAndUserId(5, A)).thenReturn(false, true);

        assertThat(service.markAsRead(5, A).getIsRead()).isTrue();
        service.markAsRead(5, A);

        verify(reads, times(1)).save(any(AlertRead.class));
        verify(alerts, never()).save(any());
        verify(reads, never()).existsByAlertIdAndUserId(5, B);
    }

    @Test
    @DisplayName("the unread count and mark-all are the account's own")
    void countAndMarkAllArePerAccount() {
        when(reads.countUnreadFor(A)).thenReturn(3L);
        when(reads.countUnreadFor(B)).thenReturn(7L);
        when(reads.markAllReadFor(A)).thenReturn(3);

        assertThat(service.countUnread(A)).isEqualTo(3);
        assertThat(service.countUnread(B)).isEqualTo(7);
        assertThat(service.markAllRead(A)).isEqualTo(3);
        verify(reads, never()).markAllReadFor(B);
    }

    @Test
    @DisplayName("a list shows each alert read or not for the signed-in account")
    void listsCarryTheCallersState() {
        when(alerts.findUnexpiredByStatus(any())).thenReturn(new ArrayList<>(List.of(alert(1), alert(2))));
        when(reads.findReadAmong(A, List.of(1, 2))).thenReturn(List.of(1));
        AsUser.signIn(A);

        List<Alert> seen = service.getActiveAlerts();

        assertThat(seen).extracting(Alert::getIsRead).containsExactly(true, false);
    }

    @Test
    @DisplayName("the read endpoints take no reader name: it is the signed-in account")
    void endpointsTakeNoReaderName() throws Exception {
        for (String name : List.of("markAsRead", "markAllRead")) {
            Method m = Arrays.stream(AlertController.class.getMethods())
                    .filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
            assertThat(Arrays.stream(m.getParameters()).noneMatch(p -> p.isAnnotationPresent(RequestParam.class)))
                    .as(name + " has no request parameter").isTrue();
        }
        AlertController controller = new AlertController();
        AlertService mocked = mock(AlertService.class);
        ReflectionTestUtils.setField(controller, "alertService", mocked);
        AsUser.signIn(B);
        controller.markAsRead(9);
        controller.markAllRead();
        verify(mocked).markAsRead(9, B);
        verify(mocked).markAllRead(B);
        verify(mocked, never()).markAllRead(A);
    }
}
