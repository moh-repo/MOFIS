package com.dilaw.mohe.security.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rule N-04: each attaché has full control of its Wafar; nobody else writes it.
 *
 * <p>Wafar is budget already transferred to one attaché and never disbursed, so
 * it stays there — Cairo's savings pay only Cairo. Ministry accounts of every
 * tier (super admin, ministry admin, ministry staff) read, report and reconcile
 * the statements and write nothing; the attaché's own staff holding the
 * {@code WAFAR.*} codes record, edit, cancel and approve.
 *
 * <p>Actions: {@code RECORD}, {@code EDIT}, {@code DELETE}, {@code RECONCILE},
 * {@code APPROVE}, {@code REJECT}. All of them refuse a ministry-level account
 * and require the row's attaché to be in the caller's scope; {@code APPROVE}
 * also keeps maker and checker apart. A null resource is the subject-only
 * question ("may this account write Wafar at all"), asked by the controllers
 * before a row exists.
 *
 * <p>One exemption, decision D-13(b): while an attaché may have a single finance
 * officer, the super admin may {@code APPROVE} or {@code REJECT} — never record
 * or edit — and each use is returned as the {@link #SUPER_ADMIN_HATCH} exemption
 * for the service to audit as {@code WAFAR_SUPERADMIN_APPROVE}. Turn it off with
 * {@code mohe.policy.wafar.super-admin-approves=false} once every attaché has
 * two finance users (D-13 a).
 *
 * <p>The same hatch covers {@code RECONCILE} since 23 Sep 2026 (live QA M2): the
 * reconciliation compares each student's recorded Wafar with what accounting
 * earmarked and writes the comparison, not money, and the only account able to
 * refresh a stale run for every attaché at once is the super admin. Its own switch
 * is {@code mohe.policy.wafar.super-admin-reconciles}; ministry admins and staff
 * stay readers.
 */
@Component
public class WafarPolicy implements AccessPolicy<WafarWrite> {

    public static final String NAME = "Wafar";
    public static final String SUPER_ADMIN_HATCH = "WAFAR_SUPER_ADMIN_HATCH";

    private final boolean superAdminMayApprove;
    private final boolean superAdminMayReconcile;

    @org.springframework.beans.factory.annotation.Autowired
    public WafarPolicy(
            @Value("${mohe.policy.wafar.super-admin-approves:true}") boolean superAdminMayApprove,
            @Value("${mohe.policy.wafar.super-admin-reconciles:true}") boolean superAdminMayReconcile) {
        this.superAdminMayApprove = superAdminMayApprove;
        this.superAdminMayReconcile = superAdminMayReconcile;
    }

    /** One switch for both hatches — the shape the tests written before the reconcile hatch use. */
    public WafarPolicy(boolean superAdminHatchOpen) {
        this(superAdminHatchOpen, superAdminHatchOpen);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<WafarWrite> resourceType() {
        return WafarWrite.class;
    }

    public boolean superAdminMayApprove() {
        return superAdminMayApprove;
    }

    public boolean superAdminMayReconcile() {
        return superAdminMayReconcile;
    }

    @Override
    public Decision decide(AccessSubject subject, WafarWrite write, String action) {
        if (!subject.isAuthenticated()) return Decision.deny("Access denied: not signed in");
        String what = write == null ? WafarWrite.SPENDING : write.what();
        boolean checking = "APPROVE".equals(action) || "REJECT".equals(action);
        boolean reconciling = "RECONCILE".equals(action);

        if (checking && subject.isSuperAdmin() && superAdminMayApprove) {
            return Decision.allowVia(List.of(SUPER_ADMIN_HATCH));
        }
        if (reconciling && subject.isSuperAdmin() && superAdminMayReconcile) {
            return Decision.allowVia(List.of(SUPER_ADMIN_HATCH));
        }
        if (subject.isMinistryLevel()) {
            return Decision.deny(what + " is recorded and approved by the attaché that holds it; ministry accounts may only view it.");
        }
        if (write == null) {
            // Subject-only: a country account with at least one attaché may write Wafar.
            return subject.allCountries() || !subject.countryIds().isEmpty()
                    ? Decision.allow()
                    : Decision.deny(what + " requires an attaché assignment.");
        }
        if (write.countryId() == null || !subject.holdsCountry(write.countryId())) {
            return Decision.deny(what + " belongs to an attaché outside your scope.");
        }
        if ("APPROVE".equals(action) && write.makerId() != null && subject.is(write.makerId())) {
            return Decision.deny("A Wafar spend cannot be approved by the user who recorded it.");
        }
        return Decision.allow();
    }
}
