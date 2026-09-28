package com.dilaw.mohe.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O-34: the security events reach their own logger, at WARN, with their fields
 * in the MDC; everything else audited stays out of it.
 */
class SecurityEventLogTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(SecurityEventLog.LOGGER);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("a security action is written with its fields; an ordinary one is not")
    void securityEventsOnly() {
        SecurityEventLog events = new SecurityEventLog(List.of("LOGIN_FAILED", "ACCESS_DENIED", "PRIVILEGE_CHANGE"));

        events.emitIfSecurity("UPDATE_USER", 2L, "admin", "USER", 14L, "phone", "10.0.0.1");
        assertThat(appender.list).as("an ordinary audit row is not a security event").isEmpty();

        events.emitIfSecurity("ACCESS_DENIED", 13L, "jo_admin", "STUDENT", 1L, "GET /students/1", "10.0.0.2");
        events.emitIfSecurity("LOGIN_FAILED_USER_NOT_FOUND", null, null, "AUTH", null, "nobody", "10.0.0.3");

        assertThat(appender.list).hasSize(2);
        ILoggingEvent denied = appender.list.get(0);
        assertThat(denied.getLevel()).isEqualTo(Level.WARN);
        assertThat(denied.getMDCPropertyMap())
                .containsEntry(SecurityEventLog.KEY_EVENT, "ACCESS_DENIED")
                .containsEntry(SecurityEventLog.KEY_USER_ID, "13")
                .containsEntry(SecurityEventLog.KEY_USER_NAME, "jo_admin")
                .containsEntry(SecurityEventLog.KEY_RESOURCE_TYPE, "STUDENT")
                .containsEntry(SecurityEventLog.KEY_RESOURCE_ID, "1")
                .containsEntry(SecurityEventLog.KEY_CLIENT_IP, "10.0.0.2");
        assertThat(denied.getFormattedMessage()).contains("ACCESS_DENIED").contains("jo_admin");

        ILoggingEvent noUser = appender.list.get(1);
        assertThat(noUser.getMDCPropertyMap())
                .as("an event with no account still goes out - the database audit cannot hold it, the SIEM can")
                .containsEntry(SecurityEventLog.KEY_EVENT, "LOGIN_FAILED_USER_NOT_FOUND")
                .containsEntry(SecurityEventLog.KEY_USER_ID, "");
        assertThat(org.slf4j.MDC.getCopyOfContextMap() == null || !org.slf4j.MDC.getCopyOfContextMap().containsKey(SecurityEventLog.KEY_EVENT))
                .as("the MDC is clean afterwards")
                .isTrue();
    }

    @Test
    @DisplayName("the default prefix list covers the events the plan names")
    void defaultSetCoversThePlan() {
        SecurityEventLog events = new SecurityEventLog(List.of(
                "LOGIN_FAILED", "ACCOUNT_", "ACCESS_DENIED", "APPROVAL_ROLE_BYPASS", "APPROVAL_SELF_APPROVAL_BYPASS",
                "WAFAR_SUPERADMIN", "WAFAR_WRITE_ELEVATED", "SYSTEM_RESTORE", "PRIVILEGE_CHANGE", "CREATE_USER",
                "SESSION_", "MFA_"));
        for (String a : List.of("LOGIN_FAILED_ACCOUNT_LOCKED", "ACCESS_DENIED", "APPROVAL_ROLE_BYPASS",
                "WAFAR_SUPERADMIN_APPROVE", "WAFAR_WRITE_ELEVATED", "SYSTEM_RESTORE_REQUESTED",
                "PRIVILEGE_CHANGE_EXECUTED", "CREATE_USER", "SESSION_EVICTED", "MFA_RESET")) {
            assertThat(events.isSecurityEvent(a)).as(a).isTrue();
        }
        for (String a : java.util.Arrays.asList("LOGIN_SUCCESS", "LOGOUT", "UPDATE_USER", "ASSIGN_USER_COUNTRY", null)) {
            assertThat(events.isSecurityEvent(a)).as(String.valueOf(a)).isFalse();
        }
    }
}
