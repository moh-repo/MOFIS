package com.dilaw.mohe.config;

import com.dilaw.mohe.notification.model.NotificationLog;
import com.dilaw.mohe.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review G5, plan W4-04: an {@code Instant} is stored as UTC, whatever zone the
 * JVM runs in. Approval, audit and notification times are Instants since A3;
 * if Hibernate bound them in the JVM's zone, a server outside UTC would write
 * them hours off and the screens, which convert from UTC, would show them so.
 *
 * <p>Meaningful on a machine outside UTC — the development one runs in Cairo.
 * The row is written through JPA and read back as the raw column text.
 */
@Tag("integration")
@SpringBootTest
class InstantStoredAsUtcTest {

    private static final String BATCH = "instant-utc-test";

    @Autowired private NotificationLogRepository logs;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void remove() {
        jdbc.update("DELETE FROM MOHECORE.NOTIFICATION_LOG WHERE BATCH_ID = ?", BATCH);
    }

    @Test
    @DisplayName("an Instant is written as its UTC wall-clock time, not the JVM's")
    void instantIsWrittenInUtc() {
        Instant noonUtc = Instant.parse("2041-06-15T12:00:00Z");
        NotificationLog log = new NotificationLog();
        log.setBatchId(BATCH);
        log.setChannel("EMAIL");
        log.setRecipientType("STUDENT");
        log.setScope("ALL");
        log.setSubject(BATCH);
        log.setBody("fixture");
        log.setStatus("SENT");
        log.setCreatedAt(noonUtc);
        NotificationLog saved = logs.save(log);

        String stored = jdbc.queryForObject(
                "SELECT CONVERT(varchar(19), CREATED_AT, 126) FROM MOHECORE.NOTIFICATION_LOG WHERE ID = ?",
                String.class, saved.getId());

        assertThat(stored).as("JVM zone %s", ZoneId.systemDefault()).isEqualTo("2041-06-15T12:00:00");
        assertThat(logs.findById(saved.getId()).orElseThrow().getCreatedAt())
                .as("and it reads back as the same instant").isEqualTo(noonUtc);
    }
}
