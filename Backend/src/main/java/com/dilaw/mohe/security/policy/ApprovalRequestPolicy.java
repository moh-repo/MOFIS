package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.model.ApprovalLevelApprover;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.ums.repository.UmsRoleRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Who may sign a workflow step (R-16, folding in O-01, O-02, N-03.c, N-04).
 *
 * <p>{@code APPROVE} requires, in order:
 * <ol>
 *   <li>the request's country in the caller's scope (a request with no
 *       country is open to anyone in scope, as before);</li>
 *   <li>the approver is not the requester (O-01) — the super admin is exempt,
 *       exemption {@link #SELF_APPROVAL_BYPASS}, except on a request that moves
 *       money ({@link #MONEY_ENTITY_TYPES}, FIN-17), which nobody approves alone;</li>
 *   <li>the approver did not sign an earlier level of the same request (live QA
 *       M3, decision D2-a: one HR Manager approved all four levels of a finance
 *       change) — the super admin is exempt, {@link #SEPARATE_LEVELS_BYPASS};</li>
 *   <li>for a country-owned entity type (Wafar, N-04) the caller is the
 *       attaché's own staff — the super admin is exempt under decision
 *       D-13(b), exemption {@link #WAFAR_SUPER_ADMIN};</li>
 *   <li>the level's required role (O-02) — super admin exempt, {@link #ROLE_BYPASS};</li>
 *   <li>the level's required position and, when approvers are named for the
 *       request's country (or for every country), one of them (N-03.c) — super
 *       admin exempt, {@link #POSITION_BYPASS};</li>
 *   <li>on a level that names no role, no position and no approver, the
 *       {@code APP_PENDING.APPROVE} permission (live QA H5, FIX-PLAN D1-a) — super
 *       admin exempt, {@link #ROLE_BYPASS}. Such a level used to be open to anyone
 *       who could view approvals.</li>
 * </ol>
 * {@code REJECT} requires the country scope and one of: the caller raised the
 * request (withdrawing it), holds {@code APP_PENDING.REJECT} (a colleague
 * stopping a wrong request at any level), or may sign the waiting level. Until
 * the round-4 QA it required the scope only, so an account that could merely
 * view approvals rejected anything in its countries.
 *
 * <p>The same decision answers "pending my approval" (N-03.d): a request is in
 * the caller's inbox when {@code APPROVE} would be allowed, exemptions included.
 * Every exemption is returned for the workflow to audit; the policy audits nothing.
 */
@Component
public class ApprovalRequestPolicy implements AccessPolicy<ApprovalDecisionContext> {

    public static final String NAME = "ApprovalRequest";
    public static final String SELF_APPROVAL_BYPASS = "SELF_APPROVAL_BYPASS";
    public static final String WAFAR_SUPER_ADMIN = "WAFAR_SUPER_ADMIN";
    public static final String ROLE_BYPASS = "ROLE_BYPASS";
    public static final String POSITION_BYPASS = "POSITION_BYPASS";
    public static final String SEPARATE_LEVELS_BYPASS = "SEPARATE_LEVELS_BYPASS";

    /** Signs a level that names nobody (D1-a). */
    static final String APPROVE_CODE = "APP_PENDING.APPROVE";
    /** Stops a request at any level. */
    static final String REJECT_CODE = "APP_PENDING.REJECT";

    /**
     * FIN-17: requests that move or redirect money. The super admin's exemption from
     * requester ≠ approver does not extend to these: one account raising a payment and
     * then signing every level of it is exactly what four eyes exist to stop. The
     * other exemptions (a level's role or position, signing more than one level of a
     * request somebody else raised) are unchanged.
     */
    public static final Set<String> MONEY_ENTITY_TYPES = Set.of(
            "STUDENT_FINANCE", "STUDENT_FINANCE_WAFAR", "WAFAR_CORRECTION", "STUDY_UNIVERSITY_FEE",
            "STUDENT_TRAVEL_TICKET", "STUDENT_LOAN", "STUDENT_BOOKS_ALLOWANCE", "STUDY_FIN_RECEIVED_WHOM");

    /** Entity types whose rows one attaché owns outright (N-04). */
    public static final Set<String> COUNTRY_OWNED_ENTITY_TYPES = Set.of("STUDENT_FINANCE_WAFAR", "WAFAR_CORRECTION");

    private final UmsRoleRepository roleRepository;
    private final WafarPolicy wafarPolicy;

    public ApprovalRequestPolicy(UmsRoleRepository roleRepository, WafarPolicy wafarPolicy) {
        this.roleRepository = roleRepository;
        this.wafarPolicy = wafarPolicy;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<ApprovalDecisionContext> resourceType() {
        return ApprovalDecisionContext.class;
    }

    @Override
    public Decision decide(AccessSubject subject, ApprovalDecisionContext ctx, String action) {
        if (!subject.isAuthenticated()) return Decision.deny("Access denied: not signed in");
        if (ctx == null || ctx.request() == null) return Decision.allow();
        ApprovalRequest request = ctx.request();

        // 1. Country scope.
        if (request.getCountryId() != null && !subject.holdsCountry(request.getCountryId())) {
            return Decision.deny("Access denied: country not in scope");
        }
        if ("REJECT".equals(action)) return decideReject(subject, request, ctx.level());
        if (!"APPROVE".equals(action)) return Decision.allow();

        List<String> exemptions = new ArrayList<>();
        boolean superAdmin = subject.isSuperAdmin();

        // 2. Requester is not the approver (O-01).
        if (subject.is(request.getRequestedBy())) {
            if (!superAdmin || MONEY_ENTITY_TYPES.contains(request.getEntityType())) {
                return Decision.deny("A request cannot be approved by the user who raised it.");
            }
            exemptions.add(SELF_APPROVAL_BYPASS);
        }

        // 2b. Each level by a different person (live QA M3, D2-a).
        if (subject.userId() != null && ctx.priorApproverIds().contains(subject.userId())) {
            if (!superAdmin) return Decision.deny("You approved an earlier level of this request; "
                    + "each level is approved by a different person.");
            exemptions.add(SEPARATE_LEVELS_BYPASS);
        }

        // 3. Country-owned entity types are approved by the attaché (N-04).
        if (COUNTRY_OWNED_ENTITY_TYPES.contains(request.getEntityType())) {
            Decision wafar = wafarPolicy.decide(subject, WafarWrite.finance(request.getCountryId()), "APPROVE");
            if (!wafar.allowed()) return wafar;
            if (wafar.via(WafarPolicy.SUPER_ADMIN_HATCH)) exemptions.add(WAFAR_SUPER_ADMIN);
        }

        ApprovalLevel level = ctx.level();
        if (level != null) {
            // 4. Required role (O-02).
            Long requiredRoleId = level.getRequiredRoleId();
            if (requiredRoleId != null) {
                String roleName = roleRepository.findById(requiredRoleId).map(r -> r.getRoleName()).orElse(null);
                if (roleName == null || !subject.hasRole(roleName)) {
                    if (!superAdmin) return Decision.deny("User does not have the required role to approve this step");
                    exemptions.add(ROLE_BYPASS);
                }
            }
            // 5. Position and named approvers (N-03.c).
            String refusal = positionOrNameRefusal(subject, request, level);
            if (refusal != null) {
                if (!superAdmin) return Decision.deny(refusal);
                exemptions.add(POSITION_BYPASS);
            }
            // 6. A level that names nobody needs the approve permission (live QA H5, D1-a).
            if (namesNobody(request, level) && !subject.hasCode(APPROVE_CODE)) {
                if (!superAdmin) return Decision.deny(
                        "This step names no approver role, so approving it needs the approve permission.");
                exemptions.add(ROLE_BYPASS);
            }
        }
        return Decision.allowVia(exemptions);
    }

    /**
     * REJECT (live QA H5): the requester withdrawing, a holder of the reject
     * permission, or someone who may sign the waiting level. The country scope was
     * checked by the caller of this method.
     */
    private Decision decideReject(AccessSubject subject, ApprovalRequest request, ApprovalLevel level) {
        if (subject.isSuperAdmin()) return Decision.allow();
        if (subject.is(request.getRequestedBy())) return Decision.allow();
        if (subject.hasCode(REJECT_CODE)) return Decision.allow();
        if (level != null && !namesNobody(request, level) && mayMeetLevel(subject, request, level)) {
            return Decision.allow();
        }
        return Decision.deny("Rejecting this request needs the reject permission or the role of its current step.");
    }

    /** Whether the caller holds what the level names: its role, position and (where named) is one of its approvers. */
    private boolean mayMeetLevel(AccessSubject subject, ApprovalRequest request, ApprovalLevel level) {
        Long requiredRoleId = level.getRequiredRoleId();
        if (requiredRoleId != null) {
            String roleName = roleRepository.findById(requiredRoleId).map(r -> r.getRoleName()).orElse(null);
            if (roleName == null || !subject.hasRole(roleName)) return false;
        }
        return positionOrNameRefusal(subject, request, level) == null;
    }

    /** A level with no required role, no position and no approver named for the request's country. */
    static boolean namesNobody(ApprovalRequest request, ApprovalLevel level) {
        if (level.getRequiredRoleId() != null) return false;
        String position = level.getRequiredPositionCode();
        if (position != null && !position.isBlank()) return false;
        List<ApprovalLevelApprover> named = level.getApprovers();
        return named == null || named.stream()
                .noneMatch(a -> a.getCountryId() == null || a.getCountryId().equals(request.getCountryId()));
    }

    /** The reason the caller is not the level's position or named approver, or null. */
    static String positionOrNameRefusal(AccessSubject subject, ApprovalRequest request, ApprovalLevel level) {
        String position = level.getRequiredPositionCode();
        if (position != null && !position.isBlank() && !position.equals(subject.positionCode())) {
            return "This step is approved by the " + position + " of the country; your account holds "
                    + (subject.positionCode() == null ? "no position" : subject.positionCode()) + ".";
        }
        List<ApprovalLevelApprover> named = level.getApprovers();
        if (named != null && !named.isEmpty()) {
            // Rows for the request's country, or rows for every country; a level
            // that names approvers only for other countries falls back to "anyone
            // meeting role and position" for this one.
            List<ApprovalLevelApprover> applicable = named.stream()
                    .filter(a -> a.getCountryId() == null || a.getCountryId().equals(request.getCountryId()))
                    .toList();
            if (!applicable.isEmpty() && applicable.stream().noneMatch(a -> subject.is(a.getUserId()))) {
                return "This step is approved by the staff named on it for this country.";
            }
        }
        return null;
    }
}
