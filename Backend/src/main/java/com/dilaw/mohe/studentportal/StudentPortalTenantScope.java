package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.repository.StudentDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The country scope a student-portal request runs under.
 *
 * <p>Portal users do not authenticate through UMS. They present an
 * {@code X-Student-Portal-Token}, which {@link StudentPortalTokenInterceptor}
 * resolves to a student id — after {@code TenantContextFilter} has already run
 * and, finding no UMS principal, settled on
 * {@link TenantContext.Scope#DENY}. That is the correct answer for the filter
 * (an unidentified caller gets nothing) but the wrong one for the request: with
 * the V031 policies enabled the student's own record is invisible to them, and
 * every portal screen renders empty against a 200 response.
 *
 * <p>So the portal resolves its own scope, from the one fact its token
 * establishes: which student is asking. The scope is that student's study
 * country. It is an outer bound, not the authorisation — the portal controllers
 * already restrict every query to the student's own id, and this does not widen
 * that. It exists so the database predicate agrees with what the controller was
 * always going to allow.
 *
 * <p>Since V036 the country is pinned on the session row at login, so the common
 * path is {@link #of(Integer)} — no query at all, because the interceptor has
 * already loaded that row to validate the token. {@link #forStudent(Integer)}
 * remains for the legacy session table, which has no such column; it reads
 * {@code STUDENT_DATA} as system, since a read that determines the scope cannot
 * itself be scoped by it — the same bootstrap {@code TenantContextFilter}
 * performs for UMS callers.
 */
@Component
public class StudentPortalTenantScope {

    private static final Logger log = LoggerFactory.getLogger(StudentPortalTenantScope.class);

    private final StudentDataRepository studentDataRepository;

    public StudentPortalTenantScope(StudentDataRepository studentDataRepository) {
        this.studentDataRepository = studentDataRepository;
    }

    /**
     * The scope for a session that already carries its country (V036).
     *
     * <p>No database access: the value came from the session row the interceptor
     * read to validate the token.
     */
    public TenantContext.Scope of(Integer studyCountryInId) {
        if (studyCountryInId == null || studyCountryInId == 0) {
            return TenantContext.Scope.DENY;
        }
        return TenantContext.Scope.of(false, java.util.List.of(studyCountryInId));
    }

    /**
     * The scope for a portal request, or {@link TenantContext.Scope#DENY} if the
     * student's study country cannot be established.
     *
     * <p>Denying on an unresolvable country is deliberate. A student row with no
     * study country is a data problem, and the safe reading of it is "we do not
     * know which tenant this is" rather than "therefore, all of them".
     */
    public TenantContext.Scope forStudent(Integer studentId) {
        if (studentId == null) {
            return TenantContext.Scope.DENY;
        }
        try {
            return TenantContext.runAsSystem(() ->
                    studentDataRepository.findStudyCountryInByStudentId(studentId)
                            .filter(countryId -> countryId != 0)
                            .map(countryId -> TenantContext.Scope.of(false, java.util.List.of(countryId)))
                            .orElseGet(() -> {
                                log.warn("Student {} has no study country; portal request denied tenant access", studentId);
                                return TenantContext.Scope.DENY;
                            }));
        } catch (RuntimeException ex) {
            log.warn("Could not resolve tenant scope for student {}; denying", studentId, ex);
            return TenantContext.Scope.DENY;
        }
    }
}
