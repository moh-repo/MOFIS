package com.dilaw.mohe.scope;

import com.dilaw.mohe.model.StudyFinanceBankBranch;
import com.dilaw.mohe.model.StudyYearsLookup;
import com.dilaw.mohe.service.StudyAmountLookupService;
import com.dilaw.mohe.service.StudyFinReceivedWhomService;
import com.dilaw.mohe.service.StudyFinanceBankBranchService;
import com.dilaw.mohe.service.StudyFinanceBankService;
import com.dilaw.mohe.service.StudyYearsLookupService;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reference data, where the two null rules sit side by side.
 *
 * <p>The study-years and study-amount lookups use <b>shared defaults</b>: a row
 * with no country applies wherever an attaché has not set its own, and must stay
 * visible to everyone. The banks, branches and received-whom records use the
 * opposite rule: a null country is missing data and is ministry-only.
 *
 * <p>Getting the first one wrong does not leak, it <em>breaks</em> — a country
 * officer opens an empty screen and student-add stops auto-filling. Getting the
 * second wrong leaks. Both directions are asserted here, which is why the two
 * live in one class.
 *
 * <p>Every denial is paired with a ministry positive control, so a query that is
 * simply broken cannot pass as a working filter.
 */
@Tag("integration")
@SpringBootTest
class ReferenceDataScopeTest {

    @Autowired private StudyYearsLookupService yearsService;
    @Autowired private StudyAmountLookupService amountService;
    @Autowired private StudyFinanceBankService bankService;
    @Autowired private StudyFinanceBankBranchService branchService;
    @Autowired private StudyFinReceivedWhomService receivedWhomService;
    @Autowired private JdbcTemplate jdbc;

    private static final int JORDAN = 1004;
    private static final int SAUDI = 1005;
    private static final int EGYPT = 1;

    /** hr_manager, an Egyptian country-tier account: S-06 lets the attaché, not the ministry, write its branches. */
    private static final long EGYPT_OFFICER = 3L;

    // ── The shared defaults must stay visible ───────────────────────────────

    @Test
    @DisplayName("the shared defaults are visible to every attaché, and nobody else's rows are")
    void sharedDefaultsAreVisibleToAll() {
        int ministry = AsUser.call(AsUser.SUPERADMIN, () -> yearsService.getAll()).size();
        assumeTrue(ministry > 0, "no study-years rows");

        // This used to assert that an attaché sees the same *number* of rows as the
        // ministry, which held only while every row was a shared default. Since the
        // entitlement figures were derived per country (WD-15), Egypt has rows of its
        // own that Jordan must not see — so an equal count would now mean the filter
        // had stopped working. The rule the title states is the one asserted: every
        // shared default reaches every attaché, and nothing belonging to another does.
        Long sharedInDb = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDY_COUNTRY_IN_ID IS NULL",
                Long.class);

        List<StudyYearsLookup> jordan = AsUser.call(AsUser.JO_ADMIN, () -> yearsService.getAll());

        assertThat(jordan.stream().filter(y -> y.getStudyCountryInId() == null).count())
                .as("""
                    Fail-closed here does not leak, it breaks: a missing shared default means a \
                    country officer has no rule to see and student-add stops filling the years \
                    field. Same call as the unified chart of accounts.""")
                .isEqualTo(sharedInDb);

        assertThat(jordan)
                .as("an attaché sees the shared defaults and its own rows, and no other country's")
                .allSatisfy(y -> assertThat(y.getStudyCountryInId())
                        .satisfiesAnyOf(c -> assertThat(c).isNull(),
                                        c -> assertThat(c).isEqualTo(JORDAN)));

        assertThat(jordan.size())
                .as("and never more than the ministry, which sees everything")
                .isLessThanOrEqualTo(ministry);

        // The amount lookup answers to the same rule.
        Long sharedAmounts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MOHECORE.STUDY_AMOUNT_LOOKUP WHERE STUDY_COUNTRY_IN_ID IS NULL",
                Long.class);
        assertThat(AsUser.call(AsUser.SA_ADMIN, () -> amountService.getAll()).stream()
                        .filter(a -> a.getStudyCountryInId() == null).count())
                .isEqualTo(sharedAmounts);
    }

    @Test
    @DisplayName("an attaché's override wins for it, and nobody else sees it")
    void overrideWinsForItsOwnCountryOnly() {
        Integer degree = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_DEGREE_ID FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDY_COUNTRY_IN_ID IS NULL",
                Integer.class);
        Integer major = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_MAJOR_ID FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDY_COUNTRY_IN_ID IS NULL "
              + "AND STUDENT_DEGREE_ID = ?", Integer.class, degree);
        assumeTrue(degree != null && major != null, "no default rows to override");

        int defaultYears = AsUser.call(AsUser.SUPERADMIN,
                () -> yearsService.resolve(degree, major, null)).orElseThrow().getStudyYears();

        jdbc.update("""
                INSERT INTO MOHECORE.STUDY_YEARS_LOOKUP
                    (STUDENT_DEGREE_ID, STUDENT_MAJOR_ID, STUDY_YEARS, STUDY_COUNTRY_IN_ID, MIDATE)
                VALUES (?, ?, ?, ?, SYSUTCDATETIME())
                """, degree, major, defaultYears + 3, JORDAN);
        Integer overrideId = jdbc.queryForObject(
                "SELECT MAX(STUDY_YEARS_ID) FROM MOHECORE.STUDY_YEARS_LOOKUP", Integer.class);

        try {
            assertThat(AsUser.call(AsUser.SUPERADMIN,
                    () -> yearsService.resolve(degree, major, JORDAN)).orElseThrow().getStudyYears())
                    .as("the ORDER BY is the override: the country row sorts ahead of the default")
                    .isEqualTo(defaultYears + 3);

            assertThat(AsUser.call(AsUser.SUPERADMIN,
                    () -> yearsService.resolve(degree, major, SAUDI)).orElseThrow().getStudyYears())
                    .as("Saudi set no rule of its own, so it still gets the default")
                    .isEqualTo(defaultYears);

            assertThat(AsUser.call(AsUser.SUPERADMIN,
                    () -> yearsService.resolve(degree, major, EGYPT)).orElseThrow().getStudyYears())
                    .isEqualTo(defaultYears);

            // And Saudi's officer cannot see Jordan's rule on the admin screen.
            assertThat(AsUser.call(AsUser.SA_ADMIN, () -> yearsService.getAll()))
                    .extracting(StudyYearsLookup::getId)
                    .doesNotContain(overrideId);
            assertThat(AsUser.call(AsUser.JO_ADMIN, () -> yearsService.getAll()))
                    .extracting(StudyYearsLookup::getId)
                    .as("Jordan's own rule is Jordan's to see")
                    .contains(overrideId);
        } finally {
            jdbc.update("DELETE FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDY_YEARS_ID = ?", overrideId);
        }
    }

    @Test
    @DisplayName("only one shared default may exist per key")
    void oneDefaultPerKey() {
        Integer degree = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_DEGREE_ID FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDY_COUNTRY_IN_ID IS NULL",
                Integer.class);
        Integer major = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_MAJOR_ID FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDY_COUNTRY_IN_ID IS NULL "
              + "AND STUDENT_DEGREE_ID = ?", Integer.class, degree);
        assumeTrue(degree != null && major != null, "no default rows");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO MOHECORE.STUDY_YEARS_LOOKUP
                    (STUDENT_DEGREE_ID, STUDENT_MAJOR_ID, STUDY_YEARS, STUDY_COUNTRY_IN_ID, MIDATE)
                VALUES (?, ?, 99, NULL, SYSUTCDATETIME())
                """, degree, major))
                .as("""
                    SQL Server treats nulls as equal inside a unique constraint, and here that \
                    is the feature: it is what makes "there is exactly one default" a database \
                    guarantee rather than a convention in a service.""")
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("a country officer cannot set the shared default")
    void officerCannotWriteTheSharedDefault() {
        StudyYearsLookup draft = new StudyYearsLookup();
        draft.setStudentDegreeId(9911);
        draft.setStudentMajorId(9911);
        draft.setStudyYears(7);
        draft.setStudyCountryInId(null);

        // jo_admin serves exactly one attaché, so a null is stamped rather than
        // refused - but it must become Jordan's rule, never everybody's.
        Integer created = null;
        try {
            created = AsUser.call(AsUser.JO_ADMIN, () -> yearsService.create(draft)).getId();
            assertThat(jdbc.queryForObject(
                    "SELECT STUDY_COUNTRY_IN_ID FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDY_YEARS_ID = ?",
                    Integer.class, created))
                    .as("""
                        The write side is what makes the permissive read safe. An officer who \
                        could create a null-country row could set the number of study years for \
                        every attaché in the ministry.""")
                    .isEqualTo(JORDAN);
        } finally {
            if (created != null)
                jdbc.update("DELETE FROM MOHECORE.STUDY_YEARS_LOOKUP WHERE STUDY_YEARS_ID = ?", created);
        }
    }

    @Test
    @DisplayName("a country officer cannot write a rule for another attaché")
    void officerCannotWriteAnotherCountrysRule() {
        StudyYearsLookup draft = new StudyYearsLookup();
        draft.setStudentDegreeId(9912);
        draft.setStudentMajorId(9912);
        draft.setStudyYears(7);
        draft.setStudyCountryInId(SAUDI);

        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> yearsService.create(draft)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    // ── Banks and branches: null is ministry-only ───────────────────────────

    @Test
    @DisplayName("banks are scoped to the attaché that uses them")
    void banksAreScoped() {
        int ministry = AsUser.call(AsUser.SUPERADMIN, () -> bankService.getAll()).size();
        int jordan = AsUser.call(AsUser.JO_ADMIN, () -> bankService.getAll()).size();
        int saudi = AsUser.call(AsUser.SA_ADMIN, () -> bankService.getAll()).size();

        assertThat(ministry).as("positive control").isPositive();
        assertThat(jordan).isPositive().isLessThan(ministry);
        assertThat(saudi).isPositive().isLessThan(ministry);
    }

    @Test
    @DisplayName("branches are scoped: an attaché sees only its own banks' branches")
    void branchesAreScoped() {
        var ministry = AsUser.call(AsUser.SUPERADMIN, () -> branchService.getAll());
        assertThat(ministry).as("positive control").isNotEmpty();

        var jordan = AsUser.call(AsUser.JO_ADMIN, () -> branchService.getAll());
        // Until V055 every branch was Egypt's and Jordan legitimately saw none; V055
        // gives each of Jordan's banks a Main Branch, so the scope now has something
        // to show — and everything it shows must be Jordanian.
        assertThat(jordan)
                .as("V055 seeds a Main Branch per Jordanian bank")
                .isNotEmpty()
                .hasSizeLessThan(ministry.size())
                .allSatisfy(b -> assertThat(b.getStudyCountryInId()).isEqualTo(1004));
    }

    @Test
    @DisplayName("a branch takes its country from the bank it belongs to")
    void branchInheritsItsBank() {
        Integer egyptBank = jdbc.queryForObject(
                "SELECT TOP 1 STUDY_FINANCE_BANKE_ID FROM MOHECORE.STUDY_FINANCE_BANK WHERE STUDY_COUNTRY_IN_ID = 1",
                Integer.class);
        assumeTrue(egyptBank != null, "no Egyptian bank");

        // Seeded through JDBC rather than branchService.create, then updated
        // through the service. STUDY_FINANCE_BANK_BRANCH_ID is not an identity
        // column but is mapped @GeneratedValue(IDENTITY), so creating a branch
        // through JPA cannot work at all - a pre-existing defect, reported and
        // deliberately not fixed here. The country inheritance is what this test
        // is about, and update exercises the same resolveCountry path.
        jdbc.update("""
                INSERT INTO MOHECORE.STUDY_FINANCE_BANK_BRANCH
                    (STUDY_FINANCE_BANK_BRANCH_ID, STUDY_FINANCE_BANK_BRANCH_NAME,
                     STUDY_FINANCE_BANKE_ID, STUDY_COUNTRY_IN_ID, IS_ACTIVE)
                VALUES (99123, N'scope probe', ?, 1, 1)
                """, egyptBank);

        try {
            StudyFinanceBankBranch draft = new StudyFinanceBankBranch();
            draft.setStudyFinanceBankBranchName("scope probe");
            draft.setStudyFinanceBankeId(egyptBank);
            draft.setStudyCountryInId(null);

            StudyFinanceBankBranch saved =
                    AsUser.call(EGYPT_OFFICER, () -> branchService.update(99123, draft));

            assertThat(saved.getStudyCountryInId())
                    .as("""
                        The two columns are redundant and agree on every row. Letting a branch \
                        drift out of its bank's country would make it invisible to the officers \
                        who use that bank, which reads as the branch having been deleted.""")
                    .isEqualTo(EGYPT);
        } finally {
            jdbc.update("DELETE FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH WHERE STUDY_FINANCE_BANK_BRANCH_ID = 99123");
        }
    }

    @Test
    @DisplayName("a branch whose country contradicts its bank is refused")
    void branchCannotContradictItsBank() {
        Integer egyptBank = jdbc.queryForObject(
                "SELECT TOP 1 STUDY_FINANCE_BANKE_ID FROM MOHECORE.STUDY_FINANCE_BANK WHERE STUDY_COUNTRY_IN_ID = 1",
                Integer.class);
        assumeTrue(egyptBank != null, "no Egyptian bank");

        jdbc.update("""
                INSERT INTO MOHECORE.STUDY_FINANCE_BANK_BRANCH
                    (STUDY_FINANCE_BANK_BRANCH_ID, STUDY_FINANCE_BANK_BRANCH_NAME,
                     STUDY_FINANCE_BANKE_ID, STUDY_COUNTRY_IN_ID, IS_ACTIVE)
                VALUES (99124, N'scope probe 2', ?, 1, 1)
                """, egyptBank);

        try {
            StudyFinanceBankBranch draft = new StudyFinanceBankBranch();
            draft.setStudyFinanceBankBranchName("scope probe 2");
            draft.setStudyFinanceBankeId(egyptBank);
            draft.setStudyCountryInId(JORDAN);

            assertThatThrownBy(() -> AsUser.call(EGYPT_OFFICER, () -> branchService.update(99124, draft)))
                    .as("an Egyptian bank cannot have a Jordanian branch")
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400");
        } finally {
            jdbc.update("DELETE FROM MOHECORE.STUDY_FINANCE_BANK_BRANCH WHERE STUDY_FINANCE_BANK_BRANCH_ID = 99124");
        }
    }

    // ── finance-received-whom: the null holes ──────────────────────────────

    @Test
    @DisplayName("a record with no country is not readable by id")
    void nullCountryRecordIsNotReadableById() {
        // The id is supplied explicitly: STUDY_RECEIVED_WHOM_ID is not an identity
        // column either, the same defect as the bank and branch tables.
        Integer id = jdbc.queryForObject(
                "SELECT ISNULL(MAX(STUDY_RECEIVED_WHOM_ID), 0) + 1 FROM MOHECORE.STUDY_FIN_RECEIVED_WHOM",
                Integer.class);
        jdbc.update("""
                INSERT INTO MOHECORE.STUDY_FIN_RECEIVED_WHOM
                    (STUDY_RECEIVED_WHOM_ID, FIN_RECEIVED_NAME, FINANCE_COUNTRY_ID)
                VALUES (?, N'scope probe', NULL)
                """, id);

        try {
            assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> receivedWhomService.getById(id)))
                    .as("""
                        The list has always hidden this row - IN never matches null - while \
                        getById waved it through on `countryId != null`. Readable by id and \
                        absent from the list is the leak the list was there to prevent, and \
                        the ids are sequential.""")
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("404");

            assertThat(AsUser.call(AsUser.SUPERADMIN, () -> receivedWhomService.getById(id)))
                    .as("positive control - ministry still reads it")
                    .isNotNull();
        } finally {
            // By name, not by id. STUDY_RECEIVED_WHOM_ID is not an identity column,
            // so a row inserted without one lands at the column default of 0 rather
            // than where this test expects it - and a teardown keyed on the computed
            // id would leave it behind, permanently, as a null-country record that
            // only ministry can see.
            jdbc.update("DELETE FROM MOHECORE.STUDY_FIN_RECEIVED_WHOM WHERE FIN_RECEIVED_NAME = N'scope probe'");
        }
    }

    @Test
    @DisplayName("received-whom records are scoped, list and delegated-student alike")
    void receivedWhomIsScoped() {
        int ministry = AsUser.call(AsUser.SUPERADMIN, () -> receivedWhomService.getAll()).size();
        assumeTrue(ministry > 0, "no received-whom records");

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> receivedWhomService.getAll()).size())
                .isLessThan(ministry);

        Integer delegated = jdbc.queryForObject(
                "SELECT TOP 1 STUDENT_FOR_DELEGATION_ID FROM MOHECORE.STUDY_FIN_RECEIVED_WHOM "
              + "WHERE STUDENT_FOR_DELEGATION_ID IS NOT NULL AND FINANCE_COUNTRY_ID = 1", Integer.class);
        assumeTrue(delegated != null, "no delegated Egyptian record");

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> receivedWhomService.findForDelegatedStudent(delegated)))
                .as("this path had no country check at all, and it names a student")
                .isEmpty();
        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> receivedWhomService.findForDelegatedStudent(delegated)))
                .as("positive control")
                .isNotEmpty();
    }
}
