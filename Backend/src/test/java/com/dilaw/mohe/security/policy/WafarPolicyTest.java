package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.ums.model.AdminTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule N-04 as a table: who may do what to whose Wafar (security plan R-16).
 *
 * <p>Pure: no Spring, no database. The policy is the rule; the services only
 * ask it and audit what comes back, so this is where the rule is pinned down.
 */
class WafarPolicyTest {

    private static final int JORDAN = 1004;
    private static final int SAUDI = 1005;

    private final WafarPolicy policy = new WafarPolicy(true);

    private static AccessSubject of(AdminTier tier, boolean all, Integer... countries) {
        return new AccessSubject(7L, tier, all, List.of(countries), Set.of(), Set.of("WAFAR.EDIT"), null);
    }

    private static final AccessSubject SUPER = of(AdminTier.SUPER_ADMIN, true);
    private static final AccessSubject MINISTRY_ADMIN = of(AdminTier.MINISTRY_ADMIN, true);
    private static final AccessSubject MINISTRY_STAFF = of(AdminTier.MINISTRY_STAFF, true);
    private static final AccessSubject JO_ADMIN = of(AdminTier.COUNTRY_ADMIN, false, JORDAN);
    private static final AccessSubject JO_STAFF = of(AdminTier.COUNTRY_STAFF, false, JORDAN);
    private static final AccessSubject SA_STAFF = of(AdminTier.COUNTRY_STAFF, false, SAUDI);

    private static final WafarWrite JORDAN_SPEND = new WafarWrite(WafarWrite.SPENDING, JORDAN, 99);
    private static final WafarWrite OWN_SPEND = new WafarWrite(WafarWrite.SPENDING, JORDAN, 7);

    @Test
    @DisplayName("no ministry tier writes Wafar, whatever codes it holds")
    void ministryTiersNeverWrite() {
        for (AccessSubject s : List.of(MINISTRY_ADMIN, MINISTRY_STAFF)) {
            for (String action : List.of("RECORD", "EDIT", "DELETE", "RECONCILE")) {
                assertThat(policy.decide(s, JORDAN_SPEND, action).allowed())
                        .as("%s %s", s.tier(), action).isFalse();
                assertThat(policy.decide(s, null, action).allowed())
                        .as("%s %s (subject-only)", s.tier(), action).isFalse();
            }
        }
        // The super admin records, edits and deletes nothing either; the run is the
        // one write the D-13(b) hatch opens for it (live QA M2 — WafarPolicyReconcileTest).
        for (String action : List.of("RECORD", "EDIT", "DELETE")) {
            assertThat(policy.decide(SUPER, JORDAN_SPEND, action).allowed()).as("SUPER %s", action).isFalse();
            assertThat(policy.decide(SUPER, null, action).allowed()).as("SUPER %s (subject-only)", action).isFalse();
        }
        assertThat(policy.decide(SUPER, null, "RECONCILE").via(WafarPolicy.SUPER_ADMIN_HATCH)).isTrue();
    }

    @Test
    @DisplayName("the attaché's own staff and admin have full control; another attaché has none")
    void attacheOwnsItsWafar() {
        for (AccessSubject s : List.of(JO_ADMIN, JO_STAFF)) {
            for (String action : List.of("RECORD", "EDIT", "DELETE", "RECONCILE", "APPROVE", "REJECT")) {
                assertThat(policy.decide(s, JORDAN_SPEND, action).allowed()).as("%s %s", s.tier(), action).isTrue();
            }
            assertThat(policy.decide(s, null, "RECORD").allowed()).isTrue();
        }
        for (String action : List.of("EDIT", "DELETE", "APPROVE", "REJECT", "RECONCILE")) {
            Decision d = policy.decide(SA_STAFF, JORDAN_SPEND, action);
            assertThat(d.allowed()).as("Saudi staff %s Jordan", action).isFalse();
            assertThat(d.reason()).contains("outside your scope");
        }
    }

    @Test
    @DisplayName("a row with no attaché belongs to nobody: not even a country account writes it")
    void unattributedRowsAreNobodys() {
        assertThat(policy.decide(JO_ADMIN, new WafarWrite(WafarWrite.SPENDING, null, null), "EDIT").allowed()).isFalse();
    }

    @Test
    @DisplayName("maker and checker are two people")
    void makerChecker() {
        assertThat(policy.decide(JO_STAFF, OWN_SPEND, "APPROVE").allowed()).isFalse();
        assertThat(policy.decide(JO_STAFF, OWN_SPEND, "APPROVE").reason()).contains("recorded it");
        assertThat(policy.decide(JO_STAFF, OWN_SPEND, "REJECT").allowed()).as("withdrawing your own is fine").isTrue();
        assertThat(policy.decide(JO_STAFF, JORDAN_SPEND, "APPROVE").allowed()).isTrue();
    }

    @Test
    @DisplayName("D-13(b): the super admin may approve or reject, flagged as the hatch; never record")
    void superAdminHatch() {
        Decision approve = policy.decide(SUPER, JORDAN_SPEND, "APPROVE");
        assertThat(approve.allowed()).isTrue();
        assertThat(approve.via(WafarPolicy.SUPER_ADMIN_HATCH)).isTrue();
        assertThat(policy.decide(SUPER, JORDAN_SPEND, "REJECT").via(WafarPolicy.SUPER_ADMIN_HATCH)).isTrue();
        assertThat(policy.decide(SUPER, JORDAN_SPEND, "RECORD").allowed()).isFalse();
        // Only the super admin: a ministry admin has no hatch.
        assertThat(policy.decide(MINISTRY_ADMIN, JORDAN_SPEND, "APPROVE").allowed()).isFalse();
    }

    @Test
    @DisplayName("D-13(a): with the hatch closed the super admin is refused like any ministry account")
    void hatchClosed() {
        WafarPolicy closed = new WafarPolicy(false);
        Decision d = closed.decide(SUPER, JORDAN_SPEND, "APPROVE");
        assertThat(d.allowed()).isFalse();
        assertThat(d.exempted()).isFalse();
    }

    @Test
    @DisplayName("an anonymous caller is refused everything")
    void anonymous() {
        assertThat(policy.decide(AccessSubject.anonymous(), JORDAN_SPEND, "EDIT").allowed()).isFalse();
    }
}
