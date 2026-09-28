package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.dilaw.mohe.repository.spec.StudentIdCountrySpec;
import com.dilaw.mohe.studentportal.model.StudentSession;
import com.dilaw.mohe.studentportal.model.StudentUser;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Country scope for {@code /student-admin/*} — the portal accounts, not the
 * portal itself.
 *
 * <p>The two admin controllers held their repository calls inline, so there was
 * no layer for a scope check to live in. That is the whole reason a service
 * exists here: the gate has to be somewhere both the list and the six writes go
 * through, or the list gets filtered and
 * {@code POST /student-admin/users/{id}/reset-password} keeps answering for
 * every student in the country next door.
 *
 * <p>Not to be confused with {@link StudentPortalTenantScope}, which resolves the
 * scope a <em>student</em> browses under. This one is about the ministry staff
 * looking at those accounts.
 *
 * <h2>Where the country comes from</h2>
 *
 * <ul>
 *   <li><b>Portal users</b> hold only a {@code studentId}, so the country is the
 *       student's, live, through {@link StudentIdCountrySpec}. There is no
 *       "awaiting assignment" case to handle: a portal account cannot exist
 *       without a student, and a student without a study country is a data fault
 *       that already fails closed everywhere else.
 *   <li><b>Sessions</b> carry {@code study_country_in_id}, pinned at login by
 *       V036. A null there means a session predating V036 and is ministry-only —
 *       the missing-data rule, because it is genuinely unknown rather than
 *       deliberately global.
 * </ul>
 */
@Service
public class StudentPortalAdminService {

    @Autowired private StudentUserRepository userRepository;
    @Autowired private StudentSessionRepository sessionRepository;
    @Autowired private CountryScopeService countryScopeService;

    // ── Portal users ─────────────────────────────────────────────────────────

    /** Every read starts here: not deleted, and within the caller's countries. */
    private Specification<StudentUser> scopedUsers() {
        return Specification.<StudentUser>where((root, q, cb) -> cb.isFalse(root.get("isDeleted")))
                .and(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));
    }

    public Page<StudentUser> users(String search, String status, Pageable pageable) {
        Specification<StudentUser> spec = scopedUsers();
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("username")), like));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        return userRepository.findAll(spec, pageable);
    }

    /**
     * One portal account, or 404 when it belongs to another attaché.
     *
     * <p>Not found rather than forbidden: these ids are sequential, and a 403
     * would confirm which of them are real. The account holder is a student, so
     * what a 403 would be confirming is that a particular person studies abroad.
     */
    public StudentUser requireUser(Integer id) {
        return userRepository.findOne(scopedUsers().and((root, q, cb) -> cb.equal(root.get("studentUserId"), id)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student user not found"));
    }

    /**
     * Sets a portal account's status; any status but ACTIVE ends the student's open
     * sessions at once (live QA H4, 25 Sep 2026: an account set to INACTIVE kept
     * working until its seven-day session expired).
     */
    @Transactional
    public StudentUser updateStatus(Integer id, String status) {
        StudentUser u = requireUser(id);
        u.setStatus(status);
        if (PortalAccountStatus.ACTIVE.equals(status)) u.setFailedLoginAttempts(0);
        u.setUpdatedAt(Instant.now());
        StudentUser saved = userRepository.save(u);
        if (!PortalAccountStatus.ACTIVE.equals(status)) {
            sessionRepository.deleteByStudentUserId(id);
        }
        return saved;
    }

    /**
     * Stores an administrator-set password and ends every session of the account.
     * In a transaction: the session delete is a {@code @Modifying} query, which the
     * controller used to run outside one.
     *
     * <p>The password is the administrator's, so the student must replace it at the
     * next sign-in (QA SEC-001, D9-a): the date of the student's own last change is
     * cleared, as it is on an account that never had one.
     */
    @Transactional
    public void resetPassword(StudentUser u, String passwordHash) {
        u.setPasswordHash(passwordHash);
        u.setPasswordChangedAt(null);
        u.setUpdatedAt(Instant.now());
        u.setFailedLoginAttempts(0);
        if (PortalAccountStatus.LOCKED.equals(u.getStatus())) u.setStatus(PortalAccountStatus.ACTIVE);
        userRepository.save(u);
        sessionRepository.deleteByStudentUserId(u.getStudentUserId());
    }

    // ── Sessions ─────────────────────────────────────────────────────────────

    public Page<StudentSessionDto> sessions(Integer studentUserId, boolean activeOnly, Pageable pageable) {
        CountryScope scope = countryScopeService.getScope();
        boolean unscoped = scope.isMinistryUser();
        List<Integer> countryIds = countryIdsFor(scope);

        if (studentUserId != null) {
            // Gate the user first, so asking about someone else's account is a
            // 404 rather than a silently empty page.
            requireUser(studentUserId);
            return sessionRepository.findDtoByStudentUserId(studentUserId, unscoped, countryIds, pageable);
        }
        return activeOnly
                ? sessionRepository.findAllActiveDto(java.time.Instant.now(), unscoped, countryIds, pageable)
                : sessionRepository.findAllDto(unscoped, countryIds, pageable);
    }

    /** One session, gated by the country pinned on it. */
    public StudentSession requireSession(Integer id) {
        StudentSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        CountryScope scope = countryScopeService.getScope();
        if (!scope.isMinistryUser()
                && (session.getStudyCountryInId() == null
                    || !scope.getAllowedCountryIds().contains(session.getStudyCountryInId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return session;
    }

    /** Ministry-only, and it throws rather than silently narrowing. */
    public void requireMinistry(String action) {
        if (!countryScopeService.getScope().isMinistryUser()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, action + " covers every attaché and is ministry-only.");
        }
    }

    /**
     * SQL Server rejects {@code IN ()}, so an empty allow-list has to become a
     * value that matches nothing rather than no list at all.
     */
    private static List<Integer> countryIdsFor(CountryScope scope) {
        List<Integer> allowed = scope.getAllowedCountryIds();
        return allowed.isEmpty() ? List.of(Integer.MIN_VALUE) : allowed;
    }
}
