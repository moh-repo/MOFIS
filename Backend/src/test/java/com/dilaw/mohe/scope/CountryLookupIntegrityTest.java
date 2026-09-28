package com.dilaw.mohe.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The country lookup is now the thing that decides who sees what — these are the
 * assertions that fail loudly if it drifts again.
 *
 * <p>Background: {@code MOHECORE.STUDY_COUNTRY_IN} carried two rows named Egypt,
 * ids 1 and 1011. Everything in the system used id 1; id 1011 had exactly one
 * reference, {@code admin}'s row in {@code UserCountries}. Nine screens offer this
 * list as a dropdown, so anything saved against 1011 would have been invisible to
 * every user scoped to Egypt — failing silently and looking like missing data.
 * V048 repointed 1011 to 1, removed the duplicate, and added two constraints.
 *
 * <p>These test the constraints and the invariants, not the migration. A
 * migration runs once; the defect it fixed can come back through any seed script.
 *
 * <p>Read-only apart from two inserts that are expected to be rejected.
 */
@Tag("integration")
@SpringBootTest
class CountryLookupIntegrityTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    @DisplayName("no two countries share an English name")
    void noDuplicateCountryNames() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT STUDY_COUNTRY_IN_NAME_EN FROM MOHECORE.STUDY_COUNTRY_IN
                    WHERE STUDY_COUNTRY_IN_NAME_EN IS NOT NULL
                    GROUP BY STUDY_COUNTRY_IN_NAME_EN HAVING COUNT(*) > 1) d
                """, Integer.class))
                .as("""
                    Two rows called Egypt is what started this. The duplicate is not visibly \
                    wrong on any screen - both read "Egypt" - and only one of them is the id \
                    every student, payment and audit row actually uses.""")
                .isZero();
    }

    @Test
    @DisplayName("a second country with the same name is rejected")
    void duplicateNameIsRejected() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO MOHECORE.STUDY_COUNTRY_IN
                    (STUDY_COUNTRY_IN_ID, STUDY_COUNTRY_IN_NAME_EN, STUDY_COUNTRY_IN_NAME, IS_ACTIVE)
                VALUES (987654, N'Egypt', N'test', 1)
                """))
                .as("UX_StudyCountryIn_NameEn - the guard, not just the cleanup")
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 987654",
                Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("a staff assignment cannot point at a country that does not exist")
    void danglingAssignmentIsRejected() {
        // `admin` held CountryId 1015, which existed in no row of the lookup. It
        // granted nothing, silently, because there was no foreign key.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO UMSCORE.UserCountries (UserId, CountryId, AssignedAt, IsActive)
                VALUES (?, 987654, SYSUTCDATETIME(), 1)
                """, AsUser.ADMIN))
                .as("FK_UserCountries_StudyCountryIn")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("every existing assignment resolves to a real country")
    void noDanglingAssignmentsRemain() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM UMSCORE.UserCountries uc
                WHERE NOT EXISTS (SELECT 1 FROM MOHECORE.STUDY_COUNTRY_IN s
                                  WHERE s.STUDY_COUNTRY_IN_ID = uc.CountryId)
                """, Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("id 1 is Egypt, and it is the id the data uses")
    void egyptIsIdOne() {
        assertThat(jdbc.queryForObject(
                "SELECT STUDY_COUNTRY_IN_NAME_EN FROM MOHECORE.STUDY_COUNTRY_IN WHERE STUDY_COUNTRY_IN_ID = 1",
                String.class))
                .isEqualTo("Egypt");

        // The guard against a future cleanup deleting the wrong twin. Whichever id
        // survives has to be the one the student records point at.
        Integer egyptStudents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN = 1", Integer.class);
        assertThat(egyptStudents)
                .as("id 1 must remain the id STUDENT_DATA uses, not merely a row named Egypt")
                .isPositive();
    }

    @Test
    @DisplayName("admin still covers every attaché, so the staff-scope result is unchanged")
    void adminStillCoversEverything() {
        // admin sees all 15 staff accounts because every other account's countries
        // fall inside its own. Removing an assignment could have broken that
        // quietly, and it would look like a scope bug rather than a data change.
        Integer countries = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDY_COUNTRY_IN", Integer.class);
        Integer adminHolds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM UMSCORE.UserCountries WHERE UserId = ? AND IsActive = 1",
                Integer.class, AsUser.ADMIN);

        assertThat(adminHolds)
                .as("admin is the ministry-wide administrator by virtue of holding every attaché")
                .isEqualTo(countries);
    }
}
