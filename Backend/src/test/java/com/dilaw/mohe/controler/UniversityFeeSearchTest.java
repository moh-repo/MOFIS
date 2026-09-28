package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code GET /studyUniversityFee/search}, the fee list as the screen shows it
 * (review F7; plan W4-10).
 *
 * <p>The screen always sent a search term, a status and a pay-date range, and the
 * endpoint it called ignored all three and returned every row, so the search box did
 * nothing. This pins each filter on its own — the term against the student's name,
 * the check number, the academic year and the student id; the status; the date range,
 * inclusive at both ends in the application's days — and the paging, the sort
 * allow-list and the country scope around them.
 *
 * <p>Three fee rows are written against an existing student inside a transaction the
 * test rolls back itself; their check numbers share a prefix nothing else uses, and
 * their years and pay dates are far in the future, so each search can be narrowed to
 * them. Nothing survives in {@code STUDY_UNIVERSITY_FEE}.
 */
@Tag("integration")
@SpringBootTest
class UniversityFeeSearchTest {

    private static final String MARK = "ZT-W410-";
    private static final List<String> FINANCE_READER = List.of("FINANCE.VIEW");

    @Autowired private StudyUniversityFeeController controller;
    @Autowired private AppTimeZone appTimeZone;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    /** The three rows, by what tells them apart. */
    private record Fixture(int studentId, int alpha, int beta, int gamma) {}

    private void withFixture(Consumer<Fixture> body) {
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            int studentId = jdbc.queryForObject(
                    "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN IS NOT NULL ORDER BY STUDENT_ID",
                    Integer.class);
            int alpha = fee(studentId, "Alpha", "2091/2092", 1, LocalDate.of(2041, 5, 10), 100);
            int beta = fee(studentId, "Beta", "2091/2092", 2, LocalDate.of(2041, 5, 20), 200);
            int gamma = fee(studentId, "Gamma", "2092/2093", 1, LocalDate.of(2041, 6, 1), 300);
            body.accept(new Fixture(studentId, alpha, beta, gamma));
            return null;
        }));
    }

    /**
     * One fee row, written through JDBC with an assigned id. Not through the repository:
     * the entity maps the key as IDENTITY and the column is a plain {@code int}, so a JPA
     * insert fails with "null identifier" ({@code IdentityMappingAgreementTest} lists it).
     * The pay date is bound as its UTC wall-clock time, which is how Hibernate writes and
     * compares an Instant ({@code InstantStoredAsUtcTest}).
     */
    private int fee(int studentId, String tag, String year, int payStatus, LocalDate paidOn, int amount) {
        // Mid-morning in the application's zone: well inside the day, whatever the JVM's zone.
        Instant paidAt = appTimeZone.startOfDay(paidOn).plusSeconds(10 * 3600);
        String utc = LocalDateTime.ofInstant(paidAt, ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        int id = jdbc.queryForObject(
                "SELECT ISNULL(MAX(STUDY_UNIVERSITY_FEE_ID), 0) + 1 FROM MOHECORE.STUDY_UNIVERSITY_FEE WITH (UPDLOCK, HOLDLOCK)",
                Integer.class);
        jdbc.update("""
                INSERT INTO MOHECORE.STUDY_UNIVERSITY_FEE
                       (STUDY_UNIVERSITY_FEE_ID, STUDENT_ID, STUDY_UNIVERSITY_FEE_STUDENT_NAME, STUDY_UNIVERSITY_CHECK_NO,
                        STUDY_UNIVERSITY_FEE_YEAR, STUDY_UNIVERSITY_FEE_PAY_STATUS, STUDY_UNIVERSITY_FEE_PAY_DATE,
                        STUDY_UNIVERSITY_FEE_AMOUNT, STUDY_UNIVERSITY_FEE_DISCOUNT, STUDY_UNIVERSITY_REAL_FEE_AMOUNT,
                        STUDY_UNIVERSITY_FEE_SEMESTER_NUMBER, STUDY_UNIVERSITY_FEE_SEMESTER_SCOPE_ID,
                        STUDY_UNIVERSITY_FEE_YEAR_SCOPE_ID, STUDY_UNIVERSITY_FEE_SOURCE_ID,
                        STUDY_UNIVERSITY_CHECK_PAYOR_TYPE_ID, STUDY_UNIVERSITY_FEE_MIDATE)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS DATETIME2), ?, 0, ?, 1, 1, 1, 1, 1, SYSUTCDATETIME())
                """, id, studentId, MARK + tag + " student", MARK + tag, year, payStatus, utc, amount, amount);
        return id;
    }

    private Page<StudyUniversityFee> search(long userId, int page, int size, String sortBy, String sortDir,
                                            String term, Integer status, LocalDate from, LocalDate to) {
        return AsUser.callWithAuthorities(userId, FINANCE_READER,
                () -> controller.search(page, size, sortBy, sortDir, term, status, from, to));
    }

    private List<Integer> ids(String term, Integer status, LocalDate from, LocalDate to) {
        return search(AsUser.SUPERADMIN, 0, 50, "id", "desc", term, status, from, to)
                .getContent().stream().map(StudyUniversityFee::getId).toList();
    }

    @Test
    @DisplayName("the term finds a row by check number, by the student's name, by the academic year and by student id")
    void termSearchesFourColumns() {
        withFixture(f -> {
            assertThat(ids(MARK, null, null, null)).as("check number").containsExactlyInAnyOrder(f.alpha(), f.beta(), f.gamma());
            assertThat(ids(MARK + "Beta student", null, null, null)).as("name").containsExactly(f.beta());
            assertThat(ids("2092/2093", null, null, null)).as("academic year").containsExactly(f.gamma());
            assertThat(ids(String.valueOf(f.studentId()), null, null, null))
                    .as("a number is also read as the student id")
                    .contains(f.alpha(), f.beta(), f.gamma());
            assertThat(ids("  " + MARK + "Alpha  ", null, null, null)).as("the term is trimmed").containsExactly(f.alpha());
        });
    }

    @Test
    @DisplayName("the status filter keeps only rows with that pay status")
    void statusFilters() {
        withFixture(f -> {
            assertThat(ids(MARK, 2, null, null)).containsExactly(f.beta());
            assertThat(ids(MARK, 1, null, null)).containsExactlyInAnyOrder(f.alpha(), f.gamma());
        });
    }

    @Test
    @DisplayName("the pay-date range is inclusive at both ends, in the application's days")
    void dateRangeIsInclusive() {
        withFixture(f -> {
            assertThat(ids(MARK, null, LocalDate.of(2041, 5, 15), LocalDate.of(2041, 5, 31)))
                    .containsExactly(f.beta());
            assertThat(ids(MARK, null, LocalDate.of(2041, 5, 20), LocalDate.of(2041, 5, 20)))
                    .as("a one-day range holds that day's payment").containsExactly(f.beta());
            assertThat(ids(MARK, null, LocalDate.of(2041, 5, 10), null))
                    .as("from only; the first day counts").containsExactlyInAnyOrder(f.alpha(), f.beta(), f.gamma());
            assertThat(ids(MARK, null, null, LocalDate.of(2041, 5, 10)))
                    .as("to only; the last day counts").containsExactly(f.alpha());
            assertThat(ids(MARK, 1, LocalDate.of(2041, 5, 1), LocalDate.of(2041, 5, 31)))
                    .as("filters combine").containsExactly(f.alpha());
        });
    }

    @Test
    @DisplayName("the result is paged and ordered on the server, and the page size is capped")
    void pagedAndSorted() {
        withFixture(f -> {
            Page<StudyUniversityFee> first = search(AsUser.SUPERADMIN, 0, 2, "studyUniversityFeeAmount", "asc",
                    MARK, null, null, null);
            Page<StudyUniversityFee> second = search(AsUser.SUPERADMIN, 1, 2, "studyUniversityFeeAmount", "asc",
                    MARK, null, null, null);

            assertThat(first.getTotalElements()).isEqualTo(3);
            assertThat(first.getTotalPages()).isEqualTo(2);
            assertThat(first.getContent()).extracting(StudyUniversityFee::getId).containsExactly(f.alpha(), f.beta());
            assertThat(second.getContent()).extracting(StudyUniversityFee::getId).containsExactly(f.gamma());

            assertThat(search(AsUser.SUPERADMIN, 0, 10, "studyUniversityFeePayDate", "desc", MARK, null, null, null)
                    .getContent()).extracting(StudyUniversityFee::getId).containsExactly(f.gamma(), f.beta(), f.alpha());

            assertThat(search(AsUser.SUPERADMIN, 0, 100_000, "id", "desc", MARK, null, null, null).getSize())
                    .as("one request cannot ask for the whole table").isEqualTo(200);
        });
    }

    @Test
    @DisplayName("a column that is not on the sort list is a 400, not a query")
    void sortIsAllowListed() {
        for (String column : List.of("studyUniversityCheckNotes", "studentId desc; --", "no_such_column")) {
            assertThatThrownBy(() -> search(AsUser.SUPERADMIN, 0, 10, column, "asc", MARK, null, null, null))
                    .as(column)
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("the search stays inside the caller's countries, and needs a finance permission")
    void scopedAndGated() {
        withFixture(f -> {
            AsUser.run(AsUser.SUPERADMIN, () -> assertThat(jdbc.queryForObject(
                    "SELECT STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA WHERE STUDENT_ID = ?", Integer.class, f.studentId()))
                    .as("the fixture student would have to be outside Jordan for this to mean anything")
                    .isNotEqualTo(1004));

            assertThat(search(AsUser.JO_ADMIN, 0, 50, "id", "desc", MARK, null, null, null).getTotalElements())
                    .as("Jordan's officer finds none of another attaché's fees").isZero();
            assertThat(search(AsUser.ADMIN, 0, 50, "id", "desc", MARK, null, null, null).getTotalElements())
                    .as("positive control: an officer holding every country finds all three").isEqualTo(3);
        });

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, List.of("STUDENTS.VIEW"),
                () -> controller.search(0, 10, "id", "desc", MARK, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }
}
