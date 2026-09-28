package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.ums.model.AdminTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reconcile hatch (D-13 b, extended 23 Sep 2026 for live QA M2): the super admin
 * may run the Wafar reconciliation, nobody else at the ministry may, and the hatch
 * opens nothing but the run.
 */
class WafarPolicyReconcileTest {

    private static AccessSubject of(AdminTier tier, boolean all, Integer... countries) {
        return new AccessSubject(7L, tier, all, List.of(countries), Set.of(), Set.of("WAFAR.EDIT"), null);
    }

    private static final AccessSubject SUPER = of(AdminTier.SUPER_ADMIN, true);
    private static final AccessSubject MINISTRY_ADMIN = of(AdminTier.MINISTRY_ADMIN, true);
    private static final AccessSubject MINISTRY_STAFF = of(AdminTier.MINISTRY_STAFF, true);
    private static final AccessSubject JO_STAFF = of(AdminTier.COUNTRY_STAFF, false, 1004);

    @Test
    @DisplayName("the super admin runs the reconciliation through the audited hatch")
    void superAdminRunsViaTheHatch() {
        Decision d = new WafarPolicy(true, true).decide(SUPER, null, "RECONCILE");
        assertThat(d.allowed()).isTrue();
        assertThat(d.via(WafarPolicy.SUPER_ADMIN_HATCH)).as("the service must raise the write key").isTrue();
    }

    @Test
    @DisplayName("the reconcile switch closes the run without touching approve/reject")
    void reconcileSwitchIsItsOwn() {
        WafarPolicy approveOnly = new WafarPolicy(true, false);
        assertThat(approveOnly.decide(SUPER, null, "RECONCILE").allowed()).isFalse();
        assertThat(approveOnly.decide(SUPER, null, "RECONCILE").reason()).contains("ministry accounts may only view it");
        assertThat(approveOnly.decide(SUPER, new WafarWrite(WafarWrite.SPENDING, 1004, 99), "APPROVE").allowed()).isTrue();

        WafarPolicy reconcileOnly = new WafarPolicy(false, true);
        assertThat(reconcileOnly.decide(SUPER, null, "RECONCILE").allowed()).isTrue();
        assertThat(reconcileOnly.decide(SUPER, new WafarWrite(WafarWrite.SPENDING, 1004, 99), "APPROVE").allowed()).isFalse();
    }

    @Test
    @DisplayName("ministry admins and staff stay readers, whatever codes they hold")
    void ministryTiersStillRefused() {
        WafarPolicy policy = new WafarPolicy(true, true);
        assertThat(policy.decide(MINISTRY_ADMIN, null, "RECONCILE").allowed()).isFalse();
        assertThat(policy.decide(MINISTRY_STAFF, null, "RECONCILE").allowed()).isFalse();
    }

    @Test
    @DisplayName("the hatch opens the run only: recording and editing stay refused for the super admin")
    void hatchDoesNotOpenRecording() {
        WafarPolicy policy = new WafarPolicy(true, true);
        assertThat(policy.decide(SUPER, null, "RECORD").allowed()).isFalse();
        assertThat(policy.decide(SUPER, new WafarWrite(WafarWrite.SPENDING, 1004, 99), "EDIT").allowed()).isFalse();
    }

    @Test
    @DisplayName("an attaché's own staff run it as before, with no exemption")
    void countryStaffRunsWithoutTheHatch() {
        Decision d = new WafarPolicy(true, true).decide(JO_STAFF, null, "RECONCILE");
        assertThat(d.allowed()).isTrue();
        assertThat(d.via(WafarPolicy.SUPER_ADMIN_HATCH)).isFalse();
    }

    @Test
    @DisplayName("the one-switch constructor the older tests use closes both hatches together")
    void legacyConstructorClosesBoth() {
        WafarPolicy closed = new WafarPolicy(false);
        assertThat(closed.superAdminMayApprove()).isFalse();
        assertThat(closed.superAdminMayReconcile()).isFalse();
        assertThat(closed.decide(SUPER, null, "RECONCILE").allowed()).isFalse();
    }
}
