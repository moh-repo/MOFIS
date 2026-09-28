package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinancePaymentRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.spec.StudentDataSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.util.StudentFinanceLedgerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Closes a MOHE quarter: archives the in-scope students and their finance rows, then
 * marks the quarter closed.
 *
 * <p>Country scope follows {@link MoheQuarterCreateService} exactly — ministry users
 * close for every student, country users only for the students of their assigned
 * study countries. The close used to page {@code STUDENT_DATA} unscoped, so a
 * country user closing their own quarter archived (and wrote ledger rows for) every
 * other attaché's students as well.
 */
@Transactional(readOnly = true)
@Service
public class MoheQuarterCloseService {

    private static final Logger log = LoggerFactory.getLogger(MoheQuarterCloseService.class);

    private static final int PAGE_SIZE = 200;

    @Autowired
    private MoheQuarterRepository moheQuarterRepository;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private StudentDataArchiveService studentDataArchiveService;

    @Autowired
    private StudentFinanceRepository studentFinanceRepository;

    @Autowired
    private StudentFinancePaymentRepository studentFinancePaymentRepository;

    @Autowired
    private StudentFinanceArchiveService studentFinanceArchiveService;

    @Autowired
    private StudentProfileService studentProfileService;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** Which received statuses count as paid, by CODE (live QA H2). */
    @Autowired
    private StudyFinReceivedStatusService receivedStatuses;

    /**
     * Serialise the full profile for the snapshot; never let it abort the archive.
     *
     * <p>A failure here is logged rather than swallowed: the archive row is still
     * written (with a null snapshot) so the close completes, but an operator can
     * see which students' snapshots are missing instead of discovering it later.
     */
    private String buildProfileSnapshot(Integer studentId) {
        try {
            return objectMapper.writeValueAsString(studentProfileService.buildProfile(studentId));
        } catch (Exception e) {
            log.warn("Profile snapshot for student {} could not be built; archiving without it: {}",
                    studentId, e.toString());
            return null;
        }
    }

    /**
     * Archive STUDENT_DATA and finance snapshots tied to MOHE_QUARTER, then mark it closed.
     * Pass {@code force=true} to skip the unpaid-records block warning.
     *
     * <p>Finance snapshots are upserts on (student, year, quarter) — see
     * {@link StudentFinanceArchiveService}. Quarter create may already have archived the
     * same quarter, and the historical 2024 quarters were loaded straight into the
     * archive, so a plain insert here hit V038 unique index and the close failed with
     * a 500. The ledger row is likewise written once per student per quarter.
     *
     * <p>Pages are sorted by student id. Unsorted offset paging on SQL Server has no
     * stable order, so a row could be visited twice or skipped between pages.
     */
    @Transactional
    public Map<String, Object> closeQuarter(int quarterId, boolean force) {
        MoheQuarter q = moheQuarterRepository.findById(quarterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MOHE quarter not found"));
        if (Boolean.TRUE.equals(q.getQuarterClosed())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quarter is already closed");
        }

        CountryScope scope = countryScopeService.getScope();
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            // Same rule as quarter create: a user with no scope must not close a
            // quarter, which would archive nothing and still flag it closed.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no country access assigned");
        }
        Specification<StudentDatum> spec = scope.isMinistryUser()
                ? Specification.where(null)
                : StudentDataSpec.countryScope(scope);

        if (!force) {
            // 1. Unpaid records check — "paid" by CODE (RECEIVED, DISBURSED, PARTIALLY
            // DISBURSED), not "status is 0": V135 moved the legacy 0/NULL rows onto
            // NOT_SPECIFIED, which is still unpaid (live QA H2).
            List<com.dilaw.mohe.model.StudentFinance> unpaid =
                studentFinanceRepository.findUnpaidForQuarter(quarterId, receivedStatuses.paidStatusIds());
            if (!unpaid.isEmpty()) {
                List<Integer> unpaidIds = unpaid.stream()
                    .map(com.dilaw.mohe.model.StudentFinance::getId)
                    .collect(Collectors.toList());
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Quarter has " + unpaid.size() + " unpaid finance record(s). "
                    + "Student IDs: " + unpaidIds + ". "
                    + "Resolve payments or call with ?force=true to close anyway.");
            }

            // 2. Budget cap check — only enforced when BUDGET_AMOUNT is set on the quarter.
            // Summed in the database over the students in scope; this used to load the
            // whole live finance table into memory and add up every country's money.
            Long budget = q.getBudgetAmount();
            if (budget != null && budget > 0) {
                long totalPaid = sumReceivedInScope(scope);
                if (totalPaid > budget) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Total paid amount (" + totalPaid + ") exceeds quarter budget (" + budget + "). "
                        + "Adjust payments or call with ?force=true to close anyway.");
                }
                q.setPaidAmount(totalPaid);
            }
        }

        AtomicInteger studentsArchived = new AtomicInteger();
        AtomicInteger financeArchived = new AtomicInteger();
        AtomicInteger financeRefreshed = new AtomicInteger();
        AtomicInteger paymentsArchived = new AtomicInteger();
        AtomicInteger paymentsSkipped = new AtomicInteger();

        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("studentId"));
        while (true) {
            Page<StudentDatum> pg = studentDataRepository.findAll(spec, pageable);
            if (pg.isEmpty()) {
                break;
            }
            for (StudentDatum sd : pg.getContent()) {
                // Upsert on (student, period), as the finance snapshot beside it is:
                // quarter create may already have archived the same period, and this
                // used to insert unconditionally, so closing after a create wrote a
                // second copy of every student.
                studentDataArchiveService.archive(sd, quarterId, buildProfileSnapshot(sd.getStudentId()));
                studentsArchived.incrementAndGet();

                java.util.Optional<StudentFinance> of = studentFinanceRepository.findById(sd.getStudentId());
                if (of.isPresent()) {
                    StudentFinance f = of.get();
                    // The period key, as quarter create writes it, not the row id: a
                    // quarter is pointed at by MOHE_QUARTER.QUARTER everywhere else in
                    // the schema, and the two flows must not fill this column with two
                    // different kinds of number.
                    if (studentFinanceArchiveService.archive(f, q.getQuarter()).refreshed()) {
                        financeRefreshed.incrementAndGet();
                    } else {
                        financeArchived.incrementAndGet();
                    }
                    if (studentFinancePaymentRepository.existsByStudentIdAndMoheQuarterId(f.getId(), quarterId)) {
                        paymentsSkipped.incrementAndGet();
                    } else {
                        studentFinancePaymentRepository.save(StudentFinanceLedgerMapper.paymentFromFinance(f, quarterId));
                        paymentsArchived.incrementAndGet();
                    }
                }
            }
            if (!pg.hasNext()) {
                break;
            }
            pageable = pg.nextPageable();
        }

        q.setQuarterClosed(true);
        moheQuarterRepository.save(q);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("quarterId", quarterId);
        summary.put("studentSnapshotsArchived", studentsArchived.get());
        summary.put("financeSnapshotsArchived", financeArchived.get() + financeRefreshed.get());
        summary.put("financeSnapshotsInserted", financeArchived.get());
        summary.put("financeSnapshotsRefreshed", financeRefreshed.get());
        summary.put("paymentLedgerRowsArchived", paymentsArchived.get());
        summary.put("paymentLedgerRowsSkipped", paymentsSkipped.get());
        summary.put("quarterClosed", true);
        return summary;
    }

    /** Sum of {@code STUDENT_FIN_RECIEVED_AMOUNT} over the students the caller may close. */
    private long sumReceivedInScope(CountryScope scope) {
        Long sum = scope.isMinistryUser()
                ? studentFinanceRepository.sumReceivedAmount()
                : studentFinanceRepository.sumReceivedAmountForStudyCountries(scope.getAllowedCountryIds());
        return sum != null ? sum : 0L;
    }
}
