package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O-16: who must enrol, and the life of one enrolment - confirm, verify once
 * per code, recovery once per code, lock after five wrong codes, admin reset.
 * Inside a rolled-back transaction: no factor outlives the test.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class MfaServiceTest {

    @Autowired private MfaService mfa;
    @Autowired private TotpService totp;
    @Autowired private JdbcTemplate jdbc;

    private static final long JO_STAFF = 14L;

    @Test
    @DisplayName("the privileged tiers must enrol; a plain account without privileged codes need not")
    void whoMustEnrol() {
        assertThat(mfa.isRequired(AsUser.SUPERADMIN)).as("super admin").isTrue();
        assertThat(mfa.isRequired(AsUser.ADMIN)).as("ministry admin").isTrue();
        assertThat(mfa.isRequired(AsUser.JO_ADMIN)).as("country admin").isTrue();

        /*
         * The gate is a function of two things — whether the tier requires a factor
         * and whether this account has one — so the test has to own both. It used to
         * assert ENROL for `superadmin` on the strength of that account happening not
         * to have enrolled. Somebody enrolled it on 7 Sep 2026 and the test began
         * failing on a correct answer: an enrolled account is asked to VERIFY, not to
         * enrol. Drive the state here instead, inside the rolled-back transaction, so
         * the assertion is about the rule rather than about the database's history.
         */
        jdbc.update("DELETE FROM UMSCORE.UserMfa WHERE UserId = ?", AsUser.JO_ADMIN);
        assertThat(mfa.gateFor(AsUser.JO_ADMIN))
                .as("required and not enrolled: the sign-in has to produce a factor")
                .isEqualTo(MfaService.Gate.ENROL);

        MfaService.Enrolment enrolment = mfa.startEnrolment(AsUser.JO_ADMIN);
        assertThat(mfa.gateFor(AsUser.JO_ADMIN))
                .as("a started enrolment is not a factor until a code confirms it")
                .isEqualTo(MfaService.Gate.ENROL);

        mfa.confirmEnrolment(AsUser.JO_ADMIN, totp.code(enrolment.secret(), totp.currentStep()));
        assertThat(mfa.gateFor(AsUser.JO_ADMIN))
                .as("required and enrolled: the sign-in asks for a code")
                .isEqualTo(MfaService.Gate.VERIFY);

        // Whether jo_staff must enrol depends only on its codes, and the rule is
        // the four-eyes one: a *.APPROVE, SYSTEM.RESTORE or UMS_ADMIN.* code.
        List<String> codes = jdbc.queryForList("""
                SELECT DISTINCT p.PermissionCode FROM UMSCORE.Permissions p
                JOIN UMSCORE.RolePermissions rp ON rp.PermissionId = p.Id
                JOIN UMSCORE.UserRoles ur ON ur.RoleId = rp.RoleId AND ur.IsActive = 1
                WHERE ur.UserId = ?""", String.class, JO_STAFF);
        boolean privileged = codes.stream().anyMatch(PrivilegedChangeService::isPrivilegedCode);
        assertThat(mfa.isRequired(JO_STAFF)).isEqualTo(privileged);
    }

    @Test
    @DisplayName("enrol, verify once per code, use a recovery code once, lock after five wrong codes")
    void lifeOfAnEnrolment() {
        MfaService.Enrolment e = mfa.startEnrolment(JO_STAFF);
        assertThat(e.secret()).matches("[A-Z2-7]{32}");
        assertThat(e.otpauthUri()).contains("secret=" + e.secret());
        assertThat(e.qrPngBase64()).isNotBlank();
        assertThat(mfa.isEnabled(JO_STAFF)).as("not on until a code proves the app holds the secret").isFalse();

        assertThatThrownBy(() -> mfa.confirmEnrolment(JO_STAFF, "000000"))
                .isInstanceOf(MfaService.MfaException.class);
        long step = totp.currentStep();
        List<String> recovery = mfa.confirmEnrolment(JO_STAFF, totp.code(e.secret(), step));
        assertThat(recovery).hasSize(10).allMatch(c -> c.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertThat(mfa.isEnabled(JO_STAFF)).isTrue();
        assertThat(mfa.gateFor(JO_STAFF)).isEqualTo(MfaService.Gate.VERIFY);
        assertThat(mfa.status(JO_STAFF).recoveryCodesLeft()).isEqualTo(10);

        // The confirming code was the code for this step; it is spent.
        assertThatThrownBy(() -> mfa.verify(JO_STAFF, totp.code(e.secret(), step)))
                .as("a code is good once").isInstanceOf(MfaService.MfaException.class);
        // The next step's code is within the window and unused.
        mfa.verify(JO_STAFF, totp.code(e.secret(), step + 1));
        assertThatThrownBy(() -> mfa.verify(JO_STAFF, totp.code(e.secret(), step + 1)))
                .isInstanceOf(MfaService.MfaException.class);

        // A recovery code, once; spaces and case do not matter.
        mfa.verify(JO_STAFF, recovery.get(0).toLowerCase().replace("-", " "));
        assertThat(mfa.status(JO_STAFF).recoveryCodesLeft()).isEqualTo(9);
        assertThatThrownBy(() -> mfa.verify(JO_STAFF, recovery.get(0))).isInstanceOf(MfaService.MfaException.class);

        // Five wrong codes since the last good one lock the factor; the right one
        // is then refused too. One has been counted since the recovery code was
        // accepted (its reuse), so three more still pass and the fifth locks.
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> mfa.verify(JO_STAFF, "111111")).isInstanceOf(MfaService.MfaException.class);
        }
        assertThat(mfa.status(JO_STAFF).locked()).isFalse();
        assertThatThrownBy(() -> mfa.verify(JO_STAFF, "111111")).hasMessageContaining("Too many");
        assertThat(mfa.status(JO_STAFF).locked()).isTrue();
        assertThatThrownBy(() -> mfa.verify(JO_STAFF, totp.code(e.secret(), step + 2)))
                .hasMessageContaining("Too many");

        Integer audited = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserActions WHERE UserId = ? AND [Action] IN ('MFA_ENROLLED','MFA_FAILED','MFA_RECOVERY_USED')",
                Integer.class, JO_STAFF);
        assertThat(audited).as("every event is on the account's record").isGreaterThanOrEqualTo(8);
    }

    @Test
    @DisplayName("an administrator resets the factor; the account is back to enrolling")
    void adminReset() {
        MfaService.Enrolment e = mfa.startEnrolment(JO_STAFF);
        mfa.confirmEnrolment(JO_STAFF, totp.code(e.secret(), totp.currentStep()));
        assertThat(mfa.isEnabled(JO_STAFF)).isTrue();

        // JO_ADMIN administers Jordan's staff (N-01); a Saudi admin may not.
        assertThatThrownBy(() -> AsUser.call(AsUser.SA_ADMIN, () -> { mfa.reset(JO_STAFF, AsUser.SA_ADMIN); return null; }))
                .as("out of scope reads as not found, never as forbidden")
                .hasMessageContaining("User not found");
        assertThat(mfa.isEnabled(JO_STAFF)).isTrue();

        AsUser.call(AsUser.JO_ADMIN, () -> { mfa.reset(JO_STAFF, AsUser.JO_ADMIN); return null; });
        assertThat(mfa.isEnabled(JO_STAFF)).isFalse();
        assertThat(mfa.status(JO_STAFF).recoveryCodesLeft()).isZero();
        Integer reset = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserActions WHERE UserId = ? AND [Action] = 'MFA_RESET' AND ResourceId = ?",
                Integer.class, AsUser.JO_ADMIN, JO_STAFF);
        assertThat(reset).isEqualTo(1);
    }

    @Test
    @DisplayName("an account that must have the factor cannot switch it off itself")
    void requiredAccountsCannotDisable() {
        MfaService.Enrolment e = mfa.startEnrolment(AsUser.JO_ADMIN);
        mfa.confirmEnrolment(AsUser.JO_ADMIN, totp.code(e.secret(), totp.currentStep()));
        assertThatThrownBy(() -> mfa.disable(AsUser.JO_ADMIN, totp.code(e.secret(), totp.currentStep() + 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("required");
        assertThat(mfa.isEnabled(AsUser.JO_ADMIN)).isTrue();
    }
}
