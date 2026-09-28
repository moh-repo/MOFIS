package com.dilaw.mohe.controler;

import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.service.BackupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Who may restore the database, and what the endpoint refuses before any SQL runs.
 *
 * <p>Revision item 1.12: restore sat under the class-level settings gate, so
 * {@code SETTINGS.EDIT} — the permission for renaming the system — was also the
 * permission for replacing every row in it. It now needs {@code SYSTEM.RESTORE}
 * (V065, granted to nobody by default). The service is a mock: nothing here
 * touches the database, and the test proves the gate and the upload check run
 * before the service is reached.
 */
@Tag("integration")
@SpringBootTest
class BackupRestoreGuardTest {

    private static final byte[] TAPE = {'T', 'A', 'P', 'E', 0, 0, 0, 0};

    @Autowired private BackupController controller;
    @MockitoBean private BackupService backupService;

    @BeforeEach
    void currentRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mohe/api/v1/backup/restore");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockMultipartFile backupFile(byte[] bytes) {
        return new MockMultipartFile("file", "MOHE_Backup_20260827.bak", "application/octet-stream", bytes);
    }

    @Test
    @DisplayName("SETTINGS.EDIT alone — the rest of the backup screen's permission — cannot restore")
    void settingsEditIsDenied() {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, List.of("SETTINGS.EDIT"),
                () -> controller.restoreBackup(backupFile(TAPE))))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, List.of("SETTINGS.VIEW", "UMS_ADMIN.EDIT"),
                () -> controller.restoreBackup(backupFile(TAPE))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(backupService);
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @AfterEach
    void removeRestoreTickets() {
        com.dilaw.mohe.config.tenant.TenantContext.runAsSystem(() -> {
            jdbc.update("DELETE FROM ALERTCORE.ApprovalHistory WHERE RequestId IN (SELECT Id FROM ALERTCORE.ApprovalRequests WHERE EntityType = 'SYSTEM_RESTORE')");
            jdbc.update("DELETE FROM ALERTCORE.ApprovalSteps WHERE RequestId IN (SELECT Id FROM ALERTCORE.ApprovalRequests WHERE EntityType = 'SYSTEM_RESTORE')");
            jdbc.update("DELETE FROM ALERTCORE.ApprovalRequests WHERE EntityType = 'SYSTEM_RESTORE'");
            // After the delete, not before: the DELETE trigger on ApprovalRequests
            // writes an audit row of its own (V047), and that row has no country.
            jdbc.update("DELETE FROM AUDITLOG.AuditTrail WHERE TABLE_NAME = 'ApprovalRequests' AND (ISNULL(OLD_VALUES, '') LIKE '%\"EntityType\":\"SYSTEM_RESTORE\"%' OR ISNULL(NEW_VALUES, '') LIKE '%\"EntityType\":\"SYSTEM_RESTORE\"%')");
            jdbc.update("DELETE FROM UMSCORE.UserActions WHERE Action = 'SYSTEM_RESTORE_REQUESTED'");
            return null;
        });
    }

    @Test
    @DisplayName("SYSTEM.RESTORE passes the guard; the super admin restores at once, and the file reaches the service")
    void systemRestorePasses() {
        when(backupService.restoreBackup(any())).thenReturn(Map.of("success", true));

        var response = AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("SYSTEM.RESTORE"),
                () -> controller.restoreBackup(backupFile(TAPE)));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(backupService).restoreBackup(any());
    }

    @Test
    @DisplayName("S-03: anyone else with SYSTEM.RESTORE raises a ticket first; nothing is restored until a second administrator signs")
    void otherAdminsRaiseATicket() {
        var response = AsUser.callWithAuthorities(AsUser.ADMIN, List.of("SYSTEM.RESTORE"),
                () -> controller.restoreBackup(backupFile(TAPE)));

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry("status", "PENDING_APPROVAL");
        verifyNoInteractions(backupService);

        // The same upload again is still waiting, and still restores nothing.
        var again = AsUser.callWithAuthorities(AsUser.ADMIN, List.of("SYSTEM.RESTORE"),
                () -> controller.restoreBackup(backupFile(TAPE)));
        assertThat(again.getStatusCode().value()).isEqualTo(202);
        verifyNoInteractions(backupService);
    }

    @Test
    @DisplayName("a file that is not a backup is refused (400) before the service — and so before any SQL — runs")
    void nonBackupFileIsRefusedFirst() {
        MockMultipartFile html = backupFile("<html>nope</html>".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, List.of("SYSTEM.RESTORE"),
                () -> controller.restoreBackup(html)))
                .isInstanceOf(IllegalArgumentException.class);   // GlobalExceptionHandler → 400
        verifyNoInteractions(backupService);
    }
}
