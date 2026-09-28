package com.dilaw.mohe.service;

import com.dilaw.mohe.util.FileUploadValidator;
import com.dilaw.mohe.util.crypto.FileCipher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The parts of backup and restore that can be checked without a database: the
 * statement the backup runs, the upload gate, and the decision the restore makes
 * from the backup's own header. Nothing here executes SQL (revision item 1.12).
 */
class BackupServiceUnitTest {

    private static final byte[] TAPE = {'T', 'A', 'P', 'E', 0, 0, 0, 0};

    private static Map<String, Object> headerRow(String database, int type, int vendor, int major) {
        Map<String, Object> row = new HashMap<>();
        row.put("databasename", database);
        row.put("backuptype", type);
        row.put("softwarevendorid", vendor);
        row.put("softwareversionmajor", major);
        row.put("servername", "DBHOST");
        row.put("backupfinishdate", "2026-08-27 01:00:00");
        return row;
    }

    @Test
    @DisplayName("the backup statement names the database in brackets and the file as a literal with quotes doubled")
    void backupStatement() {
        String sql = BackupService.backupStatement("MOHE", Path.of("C:/MOHE_Backups/it's.bak"));
        assertThat(sql).startsWith("BACKUP DATABASE [MOHE] TO DISK = '")
                .contains("it''s.bak'")
                .contains("WITH FORMAT, INIT");
        assertThatThrownBy(() -> BackupService.backupStatement("MOHE]; DROP DATABASE x; --", Path.of("x.bak")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the database name comes from the JDBC URL and must be a plain identifier")
    void databaseNameFromUrl() {
        assertThat(BackupService.extractDatabaseName(
                "jdbc:sqlserver://localhost:1433;databaseName=MOHE;trustServerCertificate=true")).isEqualTo("MOHE");
        assertThat(BackupService.extractDatabaseName("jdbc:sqlserver://localhost:1433")).isEqualTo("MOHE");
        assertThatThrownBy(() -> BackupService.extractDatabaseName("jdbc:sqlserver://h;databaseName=a b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a HEADERONLY row is parsed by column name, case-insensitively")
    void headerParsing() {
        BackupService.BackupHeader h = BackupService.headerFrom(headerRow("MOHE", 1, 4608, 13));
        assertThat(h.databaseName()).isEqualTo("MOHE");
        assertThat(h.backupType()).isEqualTo(1);
        assertThat(h.softwareVendorId()).isEqualTo(BackupService.VENDOR_MICROSOFT);
        assertThat(h.softwareVersionMajor()).isEqualTo(13);
        assertThat(h.serverName()).isEqualTo("DBHOST");
    }

    @Test
    @DisplayName("only a full SQL Server backup of this database from a server no newer than this one is restorable")
    void restoreDecision() {
        assertThatCode(() -> BackupService.assertRestorable(
                BackupService.headerFrom(headerRow("mohe", 1, 4608, 13)), "MOHE", 13)).doesNotThrowAnyException();
        assertThatCode(() -> BackupService.assertRestorable(
                BackupService.headerFrom(headerRow("MOHE", 1, 4608, 12)), "MOHE", 13))
                .as("older server is fine").doesNotThrowAnyException();

        assertThatThrownBy(() -> BackupService.assertRestorable(
                BackupService.headerFrom(headerRow("OtherDb", 1, 4608, 13)), "MOHE", 13))
                .as("another database").isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OtherDb");
        assertThatThrownBy(() -> BackupService.assertRestorable(
                BackupService.headerFrom(headerRow("MOHE", 2, 4608, 13)), "MOHE", 13))
                .as("a log backup").isInstanceOf(IllegalArgumentException.class).hasMessageContaining("full");
        assertThatThrownBy(() -> BackupService.assertRestorable(
                BackupService.headerFrom(headerRow("MOHE", 1, 4608, 16)), "MOHE", 13))
                .as("from a newer server").isInstanceOf(IllegalArgumentException.class).hasMessageContaining("newer");
        assertThatThrownBy(() -> BackupService.assertRestorable(
                BackupService.headerFrom(headerRow("MOHE", 1, 1, 13)), "MOHE", 13))
                .as("not SQL Server").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BackupService.assertRestorable(
                BackupService.headerFrom(new HashMap<>()), "MOHE", 13))
                .as("an empty row").isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the upload gate: a bare .bak name and a backup or encrypted-backup signature, nothing else")
    void uploadGate() {
        assertThatCode(() -> FileUploadValidator.validateBackupUpload(
                new MockMultipartFile("file", "MOHE_Backup_1.bak", "application/octet-stream", TAPE)))
                .doesNotThrowAnyException();
        byte[] encrypted = new byte[32];
        System.arraycopy(FileCipher.MAGIC, 0, encrypted, 0, FileCipher.MAGIC.length);
        assertThatCode(() -> FileUploadValidator.validateBackupUpload(
                new MockMultipartFile("file", "MOHE_Backup_1.bak", "application/octet-stream", encrypted)))
                .as("one of ours").doesNotThrowAnyException();

        assertThatThrownBy(() -> FileUploadValidator.validateBackupUpload(
                new MockMultipartFile("file", "evil.bak", "application/octet-stream",
                        "<html>not a backup</html>".getBytes(StandardCharsets.UTF_8))))
                .as("HTML in a .bak").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileUploadValidator.validateBackupUpload(
                new MockMultipartFile("file", "x.zip", "application/octet-stream", TAPE)))
                .as("wrong suffix").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileUploadValidator.validateBackupUpload(
                new MockMultipartFile("file", "../x.bak", "application/octet-stream", TAPE)))
                .as("path in the name").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FileUploadValidator.validateBackupUpload(
                new MockMultipartFile("file", "x.bak", "application/octet-stream", new byte[0])))
                .as("empty").isInstanceOf(IllegalArgumentException.class);
    }
}
