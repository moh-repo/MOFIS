package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.repository.UmsUserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Ties a session to the address and device it was issued to (security plan S-09).
 *
 * <p>The bearer token sits in the browser's storage (O-07 option A), so a copy
 * of it is as good as the original until its use is compared with its issue.
 * A request whose address <em>and</em> user agent both differ from the
 * session's is an anomaly: it is audited as {@code SESSION_ANOMALY}, and for a
 * privileged account (the MFA-required set of O-16: the administering tiers
 * and any privileged code) the session is ended so the owner must sign in
 * again. Any other account keeps its session the first time, rebound to the new
 * address and device, because a phone changing networks changes only the address
 * and a browser upgrade changes only the agent; the "and" is what keeps the rule
 * from logging everyone out at the office door. A second such move on the same
 * session ends it (DEC-17, plan W5-02): once is a journey, twice is a token in
 * two places.
 */
@Service
public class SessionBindingService {

    private static final Logger log = LoggerFactory.getLogger(SessionBindingService.class);

    public enum Verdict { OK, REBOUND, ENDED }

    private final UmsUserSessionRepository sessionRepository;
    private final UmsAuditLogService auditLogService;
    private final MfaService mfaService;
    private final boolean enabled;

    public SessionBindingService(UmsUserSessionRepository sessionRepository,
                                 UmsAuditLogService auditLogService,
                                 MfaService mfaService,
                                 @Value("${mohe.session-binding.enabled:true}") boolean enabled) {
        this.sessionRepository = sessionRepository;
        this.auditLogService = auditLogService;
        this.mfaService = mfaService;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Lower-case hex SHA-256 of the user agent; null for a missing agent. */
    public static String hash(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(userAgent.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Compares the request with the session and acts on a mismatch of both
     * address and device. Never throws: a fault here must not turn a valid
     * request into a 500, so it is logged and the request goes on as OK.
     */
    @Transactional
    public Verdict check(UmsUserSession session, String ip, String userAgent) {
        if (!enabled || session == null) return Verdict.OK;
        try {
            boolean ipDiffers = session.getIpAddress() != null && !session.getIpAddress().isBlank()
                    && ip != null && !ip.isBlank() && !session.getIpAddress().equals(ip);
            boolean agentDiffers = agentDiffers(session, userAgent);
            if (!(ipDiffers && agentDiffers)) return Verdict.OK;

            Long userId = session.getUser().getId();
            boolean privileged = mfaService.isRequired(userId);
            boolean repeated = session.getAnomalyCount() != null && session.getAnomalyCount() >= 1;
            String before = session.getIpAddress() + " / " + abbreviate(session.getUserAgent());
            String after = ip + " / " + abbreviate(userAgent);
            if (privileged || repeated) {
                session.setIsActive(false);
                session.setAnomalyCount(session.getAnomalyCount() == null ? 1 : session.getAnomalyCount() + 1);
                sessionRepository.saveAndFlush(session);
                auditLogService.log(userId, "SESSION_ANOMALY_ENDED", "Session", session.getId(), before, after, ip, userAgent);
                log.warn("Session {} of user {} ended: presented from a different address and device ({})",
                        session.getId(), userId, privileged ? "privileged account" : "second time");
                return Verdict.ENDED;
            }
            session.setIpAddress(ip);
            session.setUserAgent(userAgent);
            session.setUserAgentHash(hash(userAgent));
            session.setAnomalyCount(session.getAnomalyCount() == null ? 1 : session.getAnomalyCount() + 1);
            sessionRepository.saveAndFlush(session);
            auditLogService.log(userId, "SESSION_ANOMALY", "Session", session.getId(), before, after, ip, userAgent);
            log.info("Session {} of user {} rebound to a new address and device", session.getId(), userId);
            return Verdict.REBOUND;
        } catch (RuntimeException e) {
            log.error("Session binding check failed for session {}: {}", session.getId(), e.getMessage());
            return Verdict.OK;
        }
    }

    private static boolean agentDiffers(UmsUserSession session, String userAgent) {
        String presented = hash(userAgent);
        if (session.getUserAgentHash() != null && !session.getUserAgentHash().isBlank()) {
            return presented != null && !session.getUserAgentHash().equalsIgnoreCase(presented);
        }
        // A session issued before V100: compare the stored text, which may be truncated.
        String stored = session.getUserAgent();
        if (stored == null || stored.isBlank() || userAgent == null || userAgent.isBlank()) return false;
        String head = userAgent.length() > stored.length() ? userAgent.substring(0, stored.length()) : userAgent;
        return !Objects.equals(stored, head);
    }

    private static String abbreviate(String s) {
        if (s == null) return "-";
        return s.length() > 80 ? s.substring(0, 77) + "..." : s;
    }
}
