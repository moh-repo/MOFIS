package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.ums.model.UmsUserSession;
import com.dilaw.mohe.ums.repository.UmsUserSessionRepository;
import com.dilaw.mohe.ums.repository.spec.StaffCountrySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UmsUserSessionService {

    @Autowired
    private UmsUserSessionRepository sessionRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    /** O-26: how long after login a session may still be refreshed, whatever the per-token TTL. */
    @org.springframework.beans.factory.annotation.Value("${jwt.absolute-session-hours:12}")
    private long absoluteSessionHours;

    /** O-26: live sessions a user may hold; the oldest is evicted when a new login exceeds it. 0 = unlimited. */
    @org.springframework.beans.factory.annotation.Value("${jwt.max-concurrent-sessions:3}")
    private int maxConcurrentSessions;

    @Autowired
    private UmsAuditLogService auditLogService;

    @Value("${jwt.expiration-seconds:86400}")
    private long jwtExpirationSeconds;

    /**
     * A session is visible to whoever may administer the user who opened it.
     *
     * <p>Only the admin-facing reads below go through this. The authentication
     * paths further down deliberately do not — see the note there.
     */
    private Specification<UmsUserSession> scoped() {
        return StaffCountrySpec.sessionsAdministrableBy(countryScopeService.getScope());
    }

    public Page<UmsUserSession> listSessions(String keyword, Boolean active, Pageable pageable) {
        return sessionRepository.findAll(scoped().and(keywordAndActive(trimNull(keyword), active)), pageable);
    }

    /** The filters the admin session list offers, kept as they were. */
    private static Specification<UmsUserSession> keywordAndActive(String keyword, Boolean active) {
        return (root, query, cb) -> {
            var where = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase() + "%";
                var user = root.get("user");
                where.add(cb.or(
                        cb.like(cb.lower(user.get("userName")), like),
                        cb.like(cb.lower(cb.coalesce(user.get("email"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("ipAddress"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("deviceInfo"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("userAgent"), "")), like)));
            }
            if (active != null) {
                where.add(cb.equal(root.get("isActive"), active));
            }
            return where.isEmpty() ? cb.conjunction() : cb.and(where.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private static String trimNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public Page<UmsUserSession> getAllSessions(Pageable pageable) {
        return listSessions(null, null, pageable);
    }

    public List<UmsUserSession> getActiveSessions() {
        return sessionRepository.findAll(scoped().and((root, q, cb) -> cb.isTrue(root.get("isActive"))));
    }

    public List<UmsUserSession> getSessionsByUserId(Long userId) {
        return sessionRepository.findAll(scoped()
                .and((root, q, cb) -> cb.and(
                        cb.equal(root.get("user").get("id"), userId),
                        cb.isTrue(root.get("isActive")))));
    }

    public Optional<UmsUserSession> getSessionById(Long id) {
        return sessionRepository.findOne(scoped().and((root, q, cb) -> cb.equal(root.get("id"), id)));
    }

    // ── Authentication paths, deliberately unscoped ──────────────────────────
    //
    // Everything below runs while a request is being authenticated: the token is
    // being validated, rotated, or issued, and there is no CountryScope yet
    // because resolving one requires the very identity these methods establish.
    // Scoping them would mean a user has to be logged in before they can log in.
    // They are all keyed on a userId that came from the token itself, never from
    // a client-supplied parameter, so there is nothing here to widen.

    public boolean isSessionActive(Long userId, String jti) {
        return findActive(userId, jti).isPresent();
    }

    /**
     * The live session behind a token, for the filter's binding check (S-09). Unscoped, like the check above.
     *
     * <p>Live QA R6-03 (28 Sep 2026): the account must be {@code ACTIVE} as well, the rule sign-in and
     * refresh apply ({@code UmsAuthService.refusalForStatus}). A locked or disabled account's token
     * worked until it expired, up to {@code jwt.expiration-seconds}; now it stops at the next request.
     * Read here rather than cached with the authorities, so a change of status needs no cache eviction.
     */
    public Optional<UmsUserSession> findActive(Long userId, String jti) {
        if (userId == null || jti == null || jti.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findLiveByUserIdAndJti(userId, jti, LocalDateTime.now(), UserStatus.ACTIVE);
    }

    public boolean hasActiveSession(Long userId) {
        if (userId == null) {
            return false;
        }
        return sessionRepository.hasActiveSession(userId, LocalDateTime.now());
    }

    @Transactional
    public void terminateSession(Long id) {
        getSessionById(id).ifPresent(session -> {
            session.setIsActive(false);
            sessionRepository.save(session);
        });
    }

    /**
     * Ends every active session for one user.
     *
     * <p>Scoped like the reads, and logout still works through it: {@code
     * UmsAuthService.logout} passes the caller's own id, which clause 4 of
     * {@code StaffCountrySpec} always admits. Kicking someone <em>else</em> off
     * is an administrative act and goes through the same gate as editing them.
     */
    @Transactional
    public void terminateAllUserSessions(Long userId) {
        List<UmsUserSession> sessions = getSessionsByUserId(userId);
        sessions.forEach(session -> session.setIsActive(false));
        sessionRepository.saveAll(sessions);
    }

    /**
     * S-05: ends every active session of an account regardless of who is
     * signed in - the leaver cleanup runs from a nightly job with no caller,
     * and a mover's cleanup runs inside an administrative change whose right
     * to touch the account has already been checked. Not scoped, on purpose.
     */
    @Transactional
    public int endSessionsOf(Long userId) {
        List<UmsUserSession> sessions = sessionRepository.findByUserIdAndIsActiveTrue(userId);
        sessions.forEach(session -> session.setIsActive(false));
        sessionRepository.saveAll(sessions);
        return sessions.size();
    }

    /**
     * SEC-10: ends every active session of an account except the one {@code keepJti}
     * names (none kept when it is null). After a password change a token stolen
     * before it must stop working; the holder's own session, the one that made the
     * change, carries on. Unscoped like {@link #endSessionsOf}: the caller's right
     * to change the password has already been checked.
     *
     * @return the number of sessions ended
     */
    @Transactional
    public int endSessionsOfExcept(Long userId, String keepJti) {
        if (userId == null) return 0;
        List<UmsUserSession> ended = sessionRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .filter(s -> keepJti == null || !keepJti.equals(s.getTokenHash()))
                .toList();
        ended.forEach(session -> session.setIsActive(false));
        sessionRepository.saveAll(ended);
        return ended.size();
    }

    /**
     * Moves the session that {@code oldJti} identifies onto {@code newJti} and
     * extends it. True only when that exact session is still active.
     *
     * <p>The presented token names one session, and only that session may be
     * renewed. Until August 2026 (revision item 1.8) a jti that matched no active
     * session fell back to <em>any</em> active session of the user — so a token
     * that had been logged out or terminated by an administrator could be
     * presented to {@code /refresh} and come back holding a different, still-live
     * session's slot: the revocation was undone by the very endpoint that should
     * honour it. Every token the application issues carries a jti
     * ({@code JwtTokenService.createToken}), and the only caller is
     * {@code UmsAuthController.refresh}, which passes the jti from the token, so
     * the fallback served no legitimate case and is gone: no jti, no rotation.
     */
    @Transactional
    public boolean rotateSessionToken(Long userId, String oldJti, String newJti) {
        if (userId == null || newJti == null || newJti.isBlank() || oldJti == null || oldJti.isBlank()) {
            return false;
        }
        Optional<UmsUserSession> session =
                sessionRepository.findActiveByUserIdAndJti(userId, oldJti, LocalDateTime.now());
        if (session.isEmpty()) {
            return false;
        }
        UmsUserSession active = session.get();
        // O-26: a session is born at login (CreatedAt is never moved by rotation)
        // and may be renewed only until absoluteSessionHours after that. Beyond it
        // the caller signs in again; before this a token could be refreshed for as
        // long as the account stayed active, which is to say for ever.
        if (absoluteSessionHours > 0 && active.getCreatedAt() != null
                && active.getCreatedAt().plusHours(absoluteSessionHours).isBefore(LocalDateTime.now())) {
            active.setIsActive(false);
            sessionRepository.save(active);
            auditLogService.log(userId, "SESSION_ABSOLUTE_EXPIRY", "SESSION", active.getId(), oldJti, null);
            return false;
        }
        active.setTokenHash(newJti);
        active.setExpiresAt(LocalDateTime.now().plusSeconds(jwtExpirationSeconds));
        sessionRepository.save(active);
        return true;
    }

    public UmsUserSession createSession(UmsUserSession session) {
        return sessionRepository.save(session);
    }

    /**
     * O-26: keeps a user at no more than {@code jwt.max-concurrent-sessions} live
     * sessions by ending the oldest. Called right after a login creates its
     * session, so the new one is always among those kept. Unscoped like the rest
     * of this section: it runs during authentication, keyed on the id the
     * credentials just proved.
     *
     * @return the number of sessions evicted
     */
    @Transactional
    public int evictBeyondLimit(Long userId) {
        if (userId == null || maxConcurrentSessions <= 0) return 0;
        List<UmsUserSession> live = sessionRepository.findByUserIdAndIsActiveTrue(userId).stream()
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(LocalDateTime.now()))
                .sorted(java.util.Comparator.comparing(UmsUserSession::getCreatedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .toList();
        int excess = live.size() - maxConcurrentSessions;
        if (excess <= 0) return 0;
        for (UmsUserSession oldest : live.subList(0, excess)) {
            oldest.setIsActive(false);
            sessionRepository.save(oldest);
            auditLogService.log(userId, "SESSION_EVICTED", "SESSION", oldest.getId(),
                    oldest.getDeviceInfo(), "max " + maxConcurrentSessions + " concurrent sessions");
        }
        return excess;
    }
}
