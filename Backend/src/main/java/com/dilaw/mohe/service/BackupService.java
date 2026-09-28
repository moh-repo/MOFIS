package com.dilaw.mohe.service;

import com.dilaw.mohe.exception.BackupOperationException;
import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.util.FileUploadValidator;
import com.dilaw.mohe.util.SafePathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Full database backup to the backup directory, and restore from an uploaded file.
 *
 * <p>Backup and restore run with {@code Propagation.NOT_SUPPORTED}: SQL Server
 * refuses both inside a transaction ("Cannot perform a backup or restore
 * operation within a transaction"), and the class-level read-only transaction
 * used to wrap them.
 *
 * <p>The restore (revision item 1.12) runs on a connection of its own, opened
 * with {@link DriverManager} from the datasource URL and credentials and closed
 * afterwards — never a pooled one. It has to {@code USE master} (a connection
 * sitting in the database being restored is one of the users
 * {@code SINGLE_USER WITH ROLLBACK IMMEDIATE} evicts), and a pooled connection
 * whose current database had been switched to {@code master} would go back into
 * the pool that way and serve the next request against the wrong database.
 * Before anything is altered, {@code RESTORE HEADERONLY} reads the file's own
 * header and the restore is refused unless it is a full backup of this database
 * from a server no newer than this one. The upload lands in a
 * {@code Files.createTempFile} name, not a fixed one, so two administrators
 * cannot overwrite each other's file mid-restore.
 */
@Transactional(readOnly = true)
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    /** A database name is an identifier; anything else in the JDBC URL is a misconfiguration, not a target. */
    private static final Pattern DATABASE_NAME = Pattern.compile("[A-Za-z0-9_]+");

    /** {@code RESTORE HEADERONLY}: {@code SoftwareVendorId} of Microsoft SQL Server. */
    static final int VENDOR_MICROSOFT = 4608;

    /** {@code RESTORE HEADERONLY}: {@code BackupType} 1 is a full database backup. */
    static final int BACKUP_TYPE_FULL = 1;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FileEncryptionService fileEncryptionService;

    @Value("${app.backup.directory:C:/MOHE_Backups}")
    private String backupDirectory;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    /**
     * S-01 (security plan, 6 Sep 2026): BACKUP DATABASE and RESTORE are server-level
     * rights the least-privilege application login (V090) does not hold. When these
     * are set the dedicated connection below uses them; when blank it falls back to
     * the application credentials, which is the situation before V090 is applied.
     */
    @Value("${mohe.admin-datasource.username:}")
    private String adminUsername;

    @Value("${mohe.admin-datasource.password:}")
    private String adminPassword;

    /**
     * Create a database backup
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> createBackup() {
        try {
            Path backupPath = Paths.get(backupDirectory);
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "MOHE_Backup_" + timestamp + ".bak";
            Path fullBackupPath = backupPath.resolve(backupFileName);
            String databaseName = extractDatabaseName(datasourceUrl);

            // On the dedicated connection, as the restore does (round-5 QA R5-21): BACKUP
            // DATABASE is a right the pooled least-privilege login does not hold (S-01), so
            // every backup failed with SQL Server error 262.
            try (Connection conn = openDedicatedConnection(); Statement backup = conn.createStatement()) {
                backup.execute(backupStatement(databaseName, fullBackupPath));
            }
            fileEncryptionService.encryptFileInPlace(fullBackupPath);

            // No server path in the answer (R5-21): the file name is what the screen shows.
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("filename", backupFileName);
            result.put("timestamp", LocalDateTime.now().toString());
            result.put("message", "Backup created successfully");
            result.put("encrypted", true);
            return result;
        } catch (IOException e) {
            throw new BackupOperationException("Backup encryption error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new BackupOperationException("Backup failed: " + e.getMessage(), e);
        }
    }

    /** The {@code BACKUP DATABASE} statement; the path is a string literal, so its quotes are doubled. */
    static String backupStatement(String databaseName, Path file) {
        requireIdentifier(databaseName);
        return String.format(
                "BACKUP DATABASE [%s] TO DISK = '%s' WITH FORMAT, INIT, NAME = 'MOHE Full Backup', SKIP, NOREWIND, NOUNLOAD, STATS = 10",
                databaseName, file.toString().replace("'", "''"));
    }

    /**
     * Get list of available backups
     */
    public List<Map<String, Object>> getBackupHistory() {
        Path backupPath = Paths.get(backupDirectory);
        List<Map<String, Object>> backups = new ArrayList<>();

        if (!Files.exists(backupPath)) {
            return backups;
        }

        try (Stream<Path> files = Files.list(backupPath)) {
            backups = files
                    .filter(file -> file.toString().endsWith(".bak"))
                    .map(file -> {
                        Map<String, Object> backup = new HashMap<>();
                        backup.put("filename", file.getFileName().toString());
                        try {
                            backup.put("size", Files.size(file));
                            backup.put("sizeFormatted", formatFileSize(Files.size(file)));
                            backup.put("lastModified", Files.getLastModifiedTime(file).toString());
                            backup.put("encrypted", fileEncryptionService.isEncrypted(file));
                        } catch (IOException e) {
                            backup.put("size", 0);
                            backup.put("sizeFormatted", "Unknown");
                            backup.put("lastModified", "Unknown");
                            backup.put("encrypted", false);
                        }
                        return backup;
                    })
                    .sorted((a, b) -> ((String) b.get("lastModified")).compareTo((String) a.get("lastModified")))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new BackupOperationException("Failed to list backups: " + e.getMessage(), e);
        }

        return backups;
    }

    /**
     * Download a backup file (decrypted for restore compatibility)
     */
    public Resource downloadBackup(String filename) {
        Path root = Paths.get(backupDirectory).toAbsolutePath().normalize();
        Path filePath = SafePathUtils.resolveFilenameUnderRoot(root, filename);

        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("Backup file not found: " + filename);
        }

        try {
            byte[] content = fileEncryptionService.readDecrypted(filePath);
            return new ByteArrayResource(content);
        } catch (Exception e) {
            throw new BackupOperationException("Could not read backup file: " + filename, e);
        }
    }

    /**
     * Restore the database from an uploaded backup file. See the class comment.
     *
     * @throws IllegalArgumentException (→ 400) when the file is not a full backup of this database
     * @throws BackupOperationException (→ 500) when the restore itself fails
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> restoreBackup(MultipartFile file) {
        FileUploadValidator.validateBackupUpload(file);
        String databaseName = extractDatabaseName(datasourceUrl);

        Path staged = null;
        try {
            staged = stageForRestore(file);

            try (Connection conn = openDedicatedConnection()) {
                try (Statement use = conn.createStatement()) {
                    use.execute("USE master");
                }
                BackupHeader header = readHeader(conn, staged);
                assertRestorable(header, databaseName, serverMajorVersion(conn));

                log.warn("Restoring database [{}] from uploaded backup '{}' (backup of [{}] taken {} on {})",
                        databaseName, file.getOriginalFilename(), header.databaseName(),
                        header.backupFinishDate(), header.serverName());
                runRestore(conn, databaseName, staged);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Database restored successfully from: " + file.getOriginalFilename());
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new BackupOperationException("Restore file error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new BackupOperationException("Restore failed: " + e.getMessage(), e);
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    /**
     * Writes the upload to a fresh temp file in the backup directory (where the
     * SQL Server service can read it), decrypting it if it is one of ours, and
     * checks the plaintext really begins with the backup signature.
     */
    private Path stageForRestore(MultipartFile file) throws IOException {
        Path dir = Paths.get(backupDirectory).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path staged = Files.createTempFile(dir, "restore-", ".bak");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
        }
        if (fileEncryptionService.isEncrypted(staged)) {
            byte[] decrypted = fileEncryptionService.readDecrypted(staged);
            Files.write(staged, decrypted);
        }
        try (InputStream in = Files.newInputStream(staged)) {
            if (!FileUploadValidator.isSqlServerBackup(in.readNBytes(4))) {
                throw new IllegalArgumentException("File is not a SQL Server backup. Restore refused.");
            }
        }
        return staged;
    }

    /** A connection that is nobody else's: not from the pool, closed when the restore ends. */
    private Connection openDedicatedConnection() throws SQLException {
        boolean adminConfigured = adminUsername != null && !adminUsername.isBlank();
        return DriverManager.getConnection(datasourceUrl,
                adminConfigured ? adminUsername : datasourceUsername,
                adminConfigured ? adminPassword : datasourcePassword);
    }

    /** What {@code RESTORE HEADERONLY} says about the file; the columns the restore decision needs. */
    record BackupHeader(String databaseName, int backupType, int softwareVendorId, int softwareVersionMajor,
                        String serverName, Object backupFinishDate) {
    }

    /** Reads the backup's own header. A file that is not a backup fails here, before anything is altered. */
    static BackupHeader readHeader(Connection conn, Path file) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("RESTORE HEADERONLY FROM DISK = ?")) {
            ps.setString(1, file.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("The file contains no backup set. Restore refused.");
                }
                return headerFrom(rowAsMap(rs));
            }
        } catch (SQLException e) {
            throw new IllegalArgumentException("The file is not a readable SQL Server backup: " + e.getMessage(), e);
        }
    }

    /** One {@code HEADERONLY} row by (case-insensitive) column name. Package-private so a test can feed a fake row. */
    static BackupHeader headerFrom(Map<String, Object> row) {
        return new BackupHeader(
                stringOf(row.get("databasename")),
                intOf(row.get("backuptype")),
                intOf(row.get("softwarevendorid")),
                intOf(row.get("softwareversionmajor")),
                stringOf(row.get("servername")),
                row.get("backupfinishdate"));
    }

    /**
     * The restore decision. Refuses anything but a full SQL Server backup of the
     * configured database from a server no newer than this one — a backup of some
     * other database would replace this one wholesale under this one's name, and a
     * backup from a newer version cannot be restored at all (the server says so,
     * but only after the database has been put in single-user mode).
     */
    static void assertRestorable(BackupHeader header, String expectedDatabase, int serverMajorVersion) {
        if (header.softwareVendorId() != VENDOR_MICROSOFT) {
            throw new IllegalArgumentException("The file is not a SQL Server backup. Restore refused.");
        }
        if (header.backupType() != BACKUP_TYPE_FULL) {
            throw new IllegalArgumentException(
                    "Only a full database backup can be restored here (backup type " + header.backupType() + "). Restore refused.");
        }
        if (header.databaseName() == null || !header.databaseName().equalsIgnoreCase(expectedDatabase)) {
            throw new IllegalArgumentException(
                    "The backup is of database [" + header.databaseName() + "], not [" + expectedDatabase + "]. Restore refused.");
        }
        if (header.softwareVersionMajor() > serverMajorVersion) {
            throw new IllegalArgumentException(
                    "The backup was taken on SQL Server version " + header.softwareVersionMajor()
                            + ", newer than this server (" + serverMajorVersion + "). Restore refused.");
        }
    }

    private static int serverMajorVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT CAST(SERVERPROPERTY('ProductMajorVersion') AS int)")) {
            return rs.next() ? rs.getInt(1) : Integer.MAX_VALUE;
        }
    }

    /**
     * Single-user, restore, multi-user — three statements, and the last runs even
     * when the second fails so a refused restore does not leave the database
     * single-user. (A restore that fails half-way leaves it RESTORING, which the
     * final ALTER cannot fix; that is a DBA's job and is logged as such.)
     */
    private static void runRestore(Connection conn, String databaseName, Path file) throws SQLException {
        requireIdentifier(databaseName);
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER DATABASE [" + databaseName + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
        }
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "RESTORE DATABASE [" + databaseName + "] FROM DISK = ? WITH REPLACE")) {
                ps.setString(1, file.toString());
                ps.execute();
            }
        } finally {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER DATABASE [" + databaseName + "] SET MULTI_USER");
            } catch (SQLException e) {
                log.error("Database [{}] could not be returned to MULTI_USER after restore: {}", databaseName, e.getMessage());
            }
        }
    }

    /**
     * Delete a backup file
     */
    public void deleteBackup(String filename) {
        try {
            Path root = Paths.get(backupDirectory).toAbsolutePath().normalize();
            Path filePath = SafePathUtils.resolveFilenameUnderRoot(root, filename);
            // A name with no file is a 404 (round-5 QA R5-22), not "Backup deleted successfully".
            if (!Files.deleteIfExists(filePath)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Backup file not found");
            }
        } catch (IOException e) {
            throw new BackupOperationException("Failed to delete backup: " + e.getMessage(), e);
        }
    }

    static String extractDatabaseName(String jdbcUrl) {
        String[] parts = jdbcUrl.split(";");
        for (String part : parts) {
            if (part.toLowerCase(Locale.ROOT).contains("databasename=")) {
                return requireIdentifier(part.substring(part.indexOf('=') + 1).trim());
            }
        }
        return "MOHE";
    }

    private static String requireIdentifier(String databaseName) {
        if (databaseName == null || !DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalStateException("Database name in the datasource URL is not a plain identifier: " + databaseName);
        }
        return databaseName;
    }

    private static Map<String, Object> rowAsMap(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        Map<String, Object> row = new HashMap<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
        }
        return row;
    }

    private static String stringOf(Object v) {
        return v == null ? null : v.toString();
    }

    private static int intOf(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return -1;
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
