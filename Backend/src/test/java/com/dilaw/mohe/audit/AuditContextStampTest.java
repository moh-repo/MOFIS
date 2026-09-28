package com.dilaw.mohe.audit;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The audit triggers read who is writing from {@code CONTEXT_INFO()}, which the
 * aspect stamps on the transaction's connection before every repository write.
 * Since O-11 the value is bound as a parameter rather than pasted into the
 * statement; this proves the bound form reaches the session and survives a
 * quote in the value.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class AuditContextStampTest {

    @Autowired private AuditContextAspect aspect;
    @Autowired private EntityManager entityManager;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("the packed user, address and session reach CONTEXT_INFO on the transaction's connection")
    void stampsContextInfoThroughABoundParameter() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("AUDIT_USER_ID", "O'Brien");
        request.setAttribute("AUDIT_IP", "10.1.2.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        aspect.setAuditContext(joinPoint);

        byte[] raw = (byte[]) entityManager
                .createNativeQuery("SELECT CAST(CONTEXT_INFO() AS VARBINARY(128))")
                .getSingleResult();
        assertThat(raw).as("CONTEXT_INFO is set").isNotNull();
        String stamped = new String(raw, StandardCharsets.UTF_16LE).replace("\0", "").trim();
        assertThat(stamped).isEqualTo("O'Brien|10.1.2.3|");
    }
}
