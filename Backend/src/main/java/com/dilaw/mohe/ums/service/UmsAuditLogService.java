package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.UmsAuditLog;
import com.dilaw.mohe.ums.repository.UmsAuditLogRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.repository.spec.StaffCountrySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * {@code UMSCORE.UserActions}: the staff audit trail — who did what, and the
 * reads that give it back.
 *
 * <p>The two halves answer to different rules. <b>Writing</b> is unconditional:
 * every action is recorded whoever took it, and a log that held only what its
 * subject was permitted to see would be no log at all. <b>Reading</b> is scoped
 * to the accounts the caller may administer ({@link #scoped()}), because a list
 * of who changed what is a list of accounts — the country scope of the users
 * screen undone from the side.
 */
@Service
public class UmsAuditLogService {

    /** The order an audit trail is read in, unless the caller says otherwise. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    @Autowired
    private UmsAuditLogRepository auditLogRepository;

    @Autowired
    private UmsUserRepository userRepository;

    @Autowired
    private com.dilaw.mohe.security.SecurityEventLog securityEventLog;

    @Autowired
    private CountryScopeService countryScopeService;

    public void log(Long userId, String action, String resourceType, Long resourceId,
            String oldValue, String newValue) {
        log(userId, action, resourceType, resourceId, oldValue, newValue, null, null);
    }

    public void log(Long userId, String action, String resourceType, Long resourceId,
            String oldValue, String newValue, String ipAddress, String userAgent) {
        // O-34: the monitoring copy goes out first, so an event without a user
        // (LOGIN_FAILED_USER_NOT_FOUND) still reaches the log the SIEM reads.
        String userName = userId == null ? null
                : userRepository.findById(userId).map(u -> u.getUserName()).orElse(null);
        securityEventLog.emitIfSecurity(action, userId, userName, resourceType, resourceId,
                newValue != null ? newValue : oldValue, ipAddress);

        // UserActions table has UserId NOT NULL – skip audit when userId is null (e.g. LOGIN_FAILED_USER_NOT_FOUND)
        if (userId == null) {
            return;
        }
        UmsAuditLog log = new UmsAuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setCreatedAt(LocalDateTime.now());

        log.setUserName(userName);

        auditLogRepository.save(log);
    }

    // ========== Reads ==========

    /**
     * The base of every read below: actions by an account the caller may
     * administer.
     *
     * <p>The writes above are deliberately unscoped — a log that recorded only
     * what the caller was allowed to see would not be a log. It is reading back
     * across attachés that is the leak, and the same rule that hides a staff
     * account from the users screen hides what that account did.
     *
     * <p>Scoped by specification rather than by {@code UmsUserService.require},
     * which is the idiom everywhere else a staff sub-resource is read by id:
     * {@code UmsUserService} logs through this service, so the reverse dependency
     * would close a cycle. The specification asks the same question of the same
     * clauses without the edge.
     */
    private Specification<UmsAuditLog> scoped() {
        return StaffCountrySpec.actionsAdministrableBy(countryScopeService.getScope());
    }

    /**
     * Newest first unless the caller asked for an order of its own.
     *
     * <p>The derived finders these reads replaced carried
     * {@code OrderByCreatedAtDesc} in their names, and a specification has no
     * such half. Losing it would leave an audit screen in insertion order, which
     * for an append-only table is nearly right and silently wrong after any
     * backfill.
     */
    private static Pageable newestFirst(Pageable pageable) {
        if (pageable == null) return PageRequest.of(0, 50, NEWEST_FIRST);
        return pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    }


    public Page<UmsAuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(scoped(), newestFirst(pageable));
    }

    /**
     * One account's actions, empty rather than 403 when it is out of scope: the
     * account is invisible to this caller, so its trail is too, and saying which
     * of the two it is would confirm the id exists.
     */
    public Page<UmsAuditLog> getAuditLogsByUserId(Long userId, Pageable pageable) {
        return auditLogRepository.findAll(
                scoped().and((root, query, cb) -> cb.equal(root.get("userId"), userId)),
                newestFirst(pageable));
    }

    public Page<UmsAuditLog> getAuditLogsByAction(String action, Pageable pageable) {
        return auditLogRepository.findAll(
                scoped().and((root, query, cb) -> cb.equal(root.get("action"), action)),
                newestFirst(pageable));
    }

    public Page<UmsAuditLog> getAuditLogsByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findAll(
                scoped().and((root, query, cb) -> cb.between(root.get("createdAt"), start, end)),
                newestFirst(pageable));
    }
}
