package com.dilaw.mohe.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An approval level must name a role somebody can hold.
 *
 * <p>{@code ApprovalRequestPolicy} resolves {@code RequiredRoleId} to a role name
 * and denies the step when the lookup comes back empty:
 *
 * <pre>
 * String roleName = roleRepository.findById(requiredRoleId)...orElse(null);
 * if (roleName == null || !subject.hasRole(roleName)) {
 *     if (!superAdmin) return Decision.deny("User does not have the required role...");
 * </pre>
 *
 * <p>So a level pointing at an id that does not exist is not a cosmetic data fault:
 * it closes the step to everyone except a super admin, and because the entity types
 * behind it write nothing until the chain completes, it closes create, update and
 * delete on those screens. Ten such rows were found on 20 Sep 2026 — two each for
 * DEFAULT, STUDENT_BOOKS_ALLOWANCE, STUDENT_EXTENSION, STUDENT_LOAN and
 * STUDY_FIN_RECEIVED_WHOM — and V119 repointed them onto COUNTRY_ADMIN and
 * MINISTRY_ADMIN.
 *
 * <p>This is the check the codebase was missing. V093 already <em>printed</em> the
 * same list, and V066 already knew the orphans were there — it had to skip
 * {@code FK_ApprovalLevels_RequiredRole} because of them. Neither stopped the count
 * growing from four to ten, because a script that prints a warning during a
 * migration nobody is watching is not a gate. V119 adds the foreign key, which
 * makes a missing id unrepresentable; this test adds the half the key cannot
 * express, that the role is also still <em>active</em>.
 *
 * <p>Read-only.
 */
@Tag("integration")
@SpringBootTest
class ApprovalLevelRolesExistTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("no active approval level requires a role that does not exist")
    void noLevelRequiresAMissingRole() {
        List<Map<String, Object>> orphans = jdbc.queryForList("""
                SELECT c.EntityType, l.Id AS LevelId, l.LevelOrder, l.LevelName, l.RequiredRoleId
                  FROM ALERTCORE.ApprovalLevels l
                  JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
                 WHERE l.IsActive = 1
                   AND l.RequiredRoleId IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM UMSCORE.Roles r WHERE r.Id = l.RequiredRoleId)
                 ORDER BY c.EntityType, l.LevelOrder""");

        assertThat(orphans)
                .as("""
                    Each of these levels can be signed by nobody but a super admin, so every \
                    create, edit and delete routed through it is stuck. Repoint the level at a \
                    real role, as V119 did.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no active approval level requires a role that has been deactivated")
    void noLevelRequiresAnInactiveRole() {
        List<Map<String, Object>> inactive = jdbc.queryForList("""
                SELECT c.EntityType, l.Id AS LevelId, l.LevelOrder, r.RoleName, r.Id AS RoleId
                  FROM ALERTCORE.ApprovalLevels l
                  JOIN ALERTCORE.ApprovalConfigs c ON c.Id = l.ConfigId
                  JOIN UMSCORE.Roles r ON r.Id = l.RequiredRoleId
                 WHERE l.IsActive = 1 AND r.IsActive = 0
                 ORDER BY c.EntityType, l.LevelOrder""");

        assertThat(inactive)
                .as("""
                    The foreign key V119 adds cannot catch this one: the row exists, so the \
                    constraint is satisfied, but nobody can hold a deactivated role and the \
                    step is just as closed as if the id were missing.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the foreign key V066 had to skip is in place")
    void requiredRoleForeignKeyExists() {
        Integer present = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys.foreign_keys WHERE name = 'FK_ApprovalLevels_RequiredRole'",
                Integer.class);

        assertThat(present)
                .as("""
                    V066 listed FK_ApprovalLevels_RequiredRole and could not create it, because \
                    four rows already pointed at roles 13 and 15. V119 repoints them and creates \
                    it. Without the key the data can drift back.""")
                .isEqualTo(1);
    }
}
