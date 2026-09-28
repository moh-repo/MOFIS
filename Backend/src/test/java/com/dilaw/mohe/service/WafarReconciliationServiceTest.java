package com.dilaw.mohe.service;

import com.dilaw.mohe.model.WafarReconciliation;
import com.dilaw.mohe.repository.WafarReconciliationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A reconciliation run keeps one row per student.
 *
 * <p>Every run used to append a full set of rows — 941 per click on "run" — so the
 * summary counted history rather than state, the discrepancy list repeated each
 * student once per run, and resolving one row left its duplicates open. The run is
 * now an upsert keyed on the student, and a row an operator resolved stays resolved.
 *
 * <p>Runs against the live database inside a rolled-back transaction: every row the
 * run writes is gone when the test ends.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class WafarReconciliationServiceTest {

    @Autowired private WafarReconciliationService service;
    @Autowired private WafarReconciliationRepository repository;
    @Autowired private JdbcTemplate jdbc;

    /**
     * N-04.b / R-16 (6 Sep 2026): a reconciliation run, like resolving one of its
     * rows, is a Wafar write and so needs an attaché of the statements' own
     * country. hr_manager (id 3) holds Egypt and is not a ministry-level account.
     * Most finance students are Egyptian, but no longer all of them — the scope
     * seed puts students in Amman and Riyadh — so a test that resolves a row picks
     * one in Egypt rather than the first it is handed. The transaction's
     * connection was opened under the SYSTEM scope, so the run still sees every
     * row; only the policy's subject changes.
     */
    private static final long EGYPT_OFFICER = 3L;

    private int run() {
        return com.dilaw.mohe.scope.AsUser.call(EGYPT_OFFICER, service::runReconciliation);
    }

    private long financeRows() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM MOHECORE.STUDENT_FINANCE", Long.class);
        return n != null ? n : 0L;
    }

    /**
     * The students this officer may act on. The tenant key is the country studied
     * in, not the country of origin — see {@code TenantKeyIsStudyCountryTest}.
     */
    private Set<Integer> egyptianStudents() {
        return Set.copyOf(jdbc.queryForList(
                "SELECT STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDY_COUNTRY_IN = 1",
                Integer.class));
    }

    private long distinctStudentsInReconciliation() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT STUDENT_ID) FROM MOHECORE.WAFAR_RECONCILIATION", Long.class);
        return n != null ? n : 0L;
    }

    @Test
    @DisplayName("running twice leaves the same number of rows: one per student, not one per run")
    void secondRunDoesNotAppend() {
        assumeTrue(financeRows() > 0, "no finance rows to reconcile");

        run();
        long afterFirst = repository.count();
        assertThat(afterFirst)
                .as("after one run every finance student has exactly one row")
                .isEqualTo(distinctStudentsInReconciliation());

        run();
        assertThat(repository.count())
                .as("a second run refreshes the rows it already opened")
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("a row an operator resolved is not reopened by the next run")
    void resolvedRowStaysResolved() {
        run();
        List<WafarReconciliation> discrepancies = repository.findUnresolvedDiscrepancies();
        assumeTrue(!discrepancies.isEmpty(), "no discrepancy to resolve");

        // Not discrepancies.get(0) any more. Until students were seeded outside
        // Egypt, every unresolved row was Egyptian and the first one was always
        // this officer's to resolve; now the first can belong to Amman or Riyadh,
        // and resolving it is refused — which is the policy doing its job, not a
        // failure. The test wants a row in its own country so that it goes on
        // testing what it was written to test: that resolving one makes it stay
        // resolved.
        WafarReconciliation mine = discrepancies.stream()
                .filter(r -> egyptianStudents().contains(r.getStudentId()))
                .findFirst()
                .orElse(null);
        assumeTrue(mine != null, "no discrepancy inside this officer's country");

        WafarReconciliation resolved = com.dilaw.mohe.scope.AsUser.call(EGYPT_OFFICER,
                () -> service.resolve(mine.getReconId(), "checked by hand"));
        long countBefore = repository.count();

        run();

        WafarReconciliation again = repository.findById(resolved.getReconId()).orElseThrow();
        assertThat(again.getReconStatus()).isEqualTo(WafarReconciliationService.STATUS_RESOLVED);
        assertThat(again.getNotes()).isEqualTo("checked by hand");
        assertThat(repository.findByStudentId(resolved.getStudentId()))
                .as("the run must not open a second row beside the resolved one")
                .allSatisfy(r -> assertThat(r.getReconId()).isEqualTo(resolved.getReconId()));
        assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("summary and discrepancy page describe the current state, not the run history")
    void summaryCountsCurrentState() {
        assumeTrue(financeRows() > 0, "no finance rows to reconcile");

        int discrepancies = run();
        run();

        Map<String, Long> summary = service.getSummary();
        assertThat(summary.get("total")).isEqualTo(repository.count());
        assertThat(summary.get("discrepancy")).isEqualTo((long) discrepancies);
        assertThat(summary.get("matched") + summary.get("discrepancy") + summary.get("resolved") + summary.get("pending"))
                .as("every row is in exactly one state")
                .isEqualTo(summary.get("total"));

        assertThat(service.getDiscrepancies(PageRequest.of(0, 5)).getTotalElements())
                .as("the paged discrepancy list is the same population as the summary count")
                .isEqualTo(summary.get("discrepancy"));
    }
}
