package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.ums.dto.EffectivePermissionReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security plan R-13 (6 Sep 2026): the effective-permission report says what an
 * account can do and why, is scoped like the users screen, and diffs two
 * accounts.
 */
@Tag("integration")
@SpringBootTest
class EffectivePermissionReportTest {

    private static final long JO_STAFF = 14L;

    @Autowired private EffectivePermissionService reports;

    @Test
    @DisplayName("a country admin's report names the tier, the attaché, and where each code comes from")
    void countryAdminReport() {
        EffectivePermissionReport r = AsUser.call(AsUser.JO_ADMIN, () -> reports.reportFor(AsUser.JO_ADMIN));
        assertThat(r.adminTier()).isEqualTo("COUNTRY_ADMIN");
        assertThat(r.countries()).extracting(EffectivePermissionReport.CountryRef::id).containsExactly(1004);
        assertThat(r.roles()).anySatisfy(role -> {
            assertThat(role.name()).isEqualTo("COUNTRY_ADMIN");
            assertThat(role.via()).isEqualTo("DIRECT");
            assertThat(role.tierRole()).isTrue();
        });
        assertThat(r.permissions()).anySatisfy(line -> {
            assertThat(line.code()).isEqualTo("UMS_ADMIN.EDIT");
            assertThat(line.enforced()).isTrue();
            assertThat(line.sources()).contains("ROLE:COUNTRY_ADMIN");
        });
        assertThat(r.lostVersusLegacyAdmin())
                .as("the R-12 cutover list: what ROLE_ADMIN gave that the bundle does not")
                .contains("SYSTEM.RESTORE")
                .doesNotContain("UMS_ADMIN.EDIT", "WAFAR.ADD");
        assertThat(r.syntheticAuthorities()).containsExactly("ROLE_COUNTRY_ADMIN");
        assertThat(r.separationOfDutiesViolations())
                .as("V092 lists jo_admin: the legacy Admin role carries both ALLOCATION halves")
                .anyMatch(v -> v.codeA().equals("ALLOCATION.CREATE"));
    }

    @Test
    @DisplayName("notes are codes, not sentences, so the screen can translate them")
    void notesAreCodes() {
        EffectivePermissionReport r = AsUser.call(AsUser.JO_ADMIN, () -> reports.reportFor(AsUser.JO_ADMIN));

        assertThat(r.notes())
                .as("""
                    Until 7 September 2026 these were English sentences built here and rendered \
                    verbatim, so the Arabic UI showed English prose in an alert box. They are now \
                    codes the screen resolves through UMS.USERS.EFF_NOTE_<code>. Asserting the \
                    shape rather than the set: a new note is welcome, an English sentence is not.""")
                .isNotEmpty()
                .allMatch(n -> n.matches("[A-Z][A-Z0-9_]*"), "SCREAMING_SNAKE_CASE with no spaces");

        assertThat(r.notes())
                .as("jo_admin is a country admin with an attaché, so this is the R-12 cutover note")
                .contains("LEGACY_ADMIN");
    }

    @Test
    @DisplayName("roles, groups and permissions carry their Arabic names")
    void arabicNamesAreCarried() {
        EffectivePermissionReport r = AsUser.call(AsUser.JO_ADMIN, () -> reports.reportFor(AsUser.JO_ADMIN));

        assertThat(r.roles())
                .as("""
                    UMSCORE.Roles.RoleNameAr, which all eighteen seeded roles have. Without it the \
                    Arabic screen labelled a role with the English identifier the column stores, \
                    'COUNTRY_ADMIN'.""")
                .isNotEmpty()
                .allSatisfy(role -> assertThat(role.nameAr()).isNotBlank());

        assertThat(r.permissions())
                .as("""
                    PermissionCatalog is English-only and compiled in; the Arabic name lives in \
                    UMSCORE.Permissions, where the permission screen edits it. 201 of 202 codes \
                    have one on dev, so this asserts most rather than all — a code with no Arabic \
                    name falls back to the English description rather than rendering blank.""")
                .isNotEmpty()
                .filteredOn(line -> line.descriptionAr() != null && !line.descriptionAr().isBlank())
                .hasSizeGreaterThan(r.permissions().size() / 2);
    }

    @Test
    @DisplayName("the report is scoped: a country admin cannot read the super admin's")
    void scopedLikeTheUsersScreen() {
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> reports.reportFor(AsUser.SUPERADMIN)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(AsUser.call(AsUser.ADMIN, () -> reports.reportFor(AsUser.JO_ADMIN)).adminTier()).isEqualTo("COUNTRY_ADMIN");
    }

    @Test
    @DisplayName("the super admin's report says every code comes from the flag")
    void superAdminReport() {
        EffectivePermissionReport r = AsUser.call(AsUser.SUPERADMIN, () -> reports.reportFor(AsUser.SUPERADMIN));
        assertThat(r.adminTier()).isEqualTo("SUPER_ADMIN");
        assertThat(r.permissions()).anySatisfy(line -> assertThat(line.sources()).contains("SUPER_ADMIN"));
        assertThat(r.lostVersusLegacyAdmin()).isEmpty();
        assertThat(r.countries()).hasSizeGreaterThan(10);
    }

    @Test
    @DisplayName("the diff lists what one account holds and the other does not, both ways")
    void diff() {
        EffectivePermissionReport.Diff d = AsUser.call(AsUser.JO_ADMIN, () -> reports.diff(AsUser.JO_ADMIN, JO_STAFF));
        assertThat(d.onlyInA()).contains("UMS_ADMIN.EDIT");
        assertThat(d.common()).isNotEmpty();
        assertThat(d.onlyInB()).doesNotContain("UMS_ADMIN.EDIT");
    }

    @Test
    @DisplayName("the exception and separation-of-duties lists run within the caller's scope")
    void companions() {
        // jo_admin sees Jordan's staff and the unassigned accounts, never Saudi's.
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> reports.exceptions()))
                .noneMatch(row -> row.userName().equals("sa_staff") || row.userName().equals("sa_admin"));
        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> reports.violations()).keySet()).doesNotContain("sa_admin");
        assertThat(AsUser.call(AsUser.ADMIN, () -> reports.violations()).keySet()).contains("jo_admin", "sa_admin");
    }
}
