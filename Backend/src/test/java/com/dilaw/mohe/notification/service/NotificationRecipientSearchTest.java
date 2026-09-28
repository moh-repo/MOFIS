package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.notification.dto.RecipientOptionDto;
import com.dilaw.mohe.notification.dto.SendMessageRequest;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The "send to one person" picker and send-by-email (review R4). Read-only:
 * only the search and the recipient count are called, never a send.
 */
@Tag("integration")
@SpringBootTest
class NotificationRecipientSearchTest {

    @Autowired private NotificationService service;
    @Autowired private JdbcTemplate jdbc;

    private Map<String, Object> egyptianStudentWithEmail() {
        var rows = jdbc.queryForList(
                "SELECT TOP 1 STUDENT_ID, STUDENT_NAME, STUDENT_EMAIL FROM MOHECORE.STUDENT_DATA "
                        + "WHERE STUDY_COUNTRY_IN = 1 AND STUDENT_EMAIL LIKE '%_@_%' "
                        + "AND STUDENT_EMAIL NOT IN (SELECT STUDENT_EMAIL FROM MOHECORE.STUDENT_DATA "
                        + "WHERE STUDENT_EMAIL IS NOT NULL GROUP BY STUDENT_EMAIL HAVING COUNT(*) > 1) "
                        + "ORDER BY STUDENT_ID");
        assumeTrue(!rows.isEmpty(), "no Egyptian student with a unique email");
        return rows.get(0);
    }

    @Test
    @DisplayName("a student is found by id and by email, and only inside the caller's countries")
    void studentSearchIsScoped() {
        Map<String, Object> s = egyptianStudentWithEmail();
        long id = ((Number) s.get("STUDENT_ID")).longValue();
        String email = (String) s.get("STUDENT_EMAIL");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> service.searchRecipients("STUDENT", String.valueOf(id))))
                .extracting(RecipientOptionDto::id).contains(id);
        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> service.searchRecipients("STUDENT", email.toUpperCase())))
                .extracting(RecipientOptionDto::id).contains(id);

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> service.searchRecipients("STUDENT", String.valueOf(id))))
                .as("an officer of another attaché does not see the student")
                .extracting(RecipientOptionDto::id).doesNotContain(id);
    }

    @Test
    @DisplayName("scope ONE accepts an email on file instead of an id")
    void sendByEmailResolves() {
        Map<String, Object> s = egyptianStudentWithEmail();
        SendMessageRequest req = new SendMessageRequest();
        req.setChannel("SMS");
        req.setRecipientType("STUDENT");
        req.setScope("ONE");
        req.setRecipientEmail(((String) s.get("STUDENT_EMAIL")).toUpperCase());
        req.setBody("preview only");

        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> service.previewCount(req))).isEqualTo(1);
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> service.previewCount(req)))
                .as("the email does not reach past the caller's countries").isZero();
    }
}
