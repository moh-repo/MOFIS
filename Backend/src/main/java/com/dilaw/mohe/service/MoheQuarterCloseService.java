package com.dilaw.mohe.service;

import com.dilaw.mohe.model.MoheQuarter;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.repository.MoheQuarterRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinancePaymentRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Closes a MOHE quarter: archives the in-scope students and their finance rows, then
 * marks the quarter closed.
 *
 * <p>Who may close follows {@link MoheQuarterCreateService} (FIN-07): a quarter is one
 * calendar for every attaché, so only a ministry-level account closes it, and the close
 * covers every student. A country user's close used to archive only their own students
 * and still flag the whole quarter closed for everyone.
 *
 * <p>Only the current quarter — the latest on record — can be closed (FIN-09). The live
 * rows the close archives are the current quarter's; closing an earlier one filed the
 * next quarter's data under the old key.
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

    /** FIN-08: what reached the attachés for the quarter, per currency. */
    @Autowired
    private com.dilaw.mohe.accounting.repository.AttacheAllocationRepository allocationRepository;

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
        // FIN-07: the ministry closes a quarter, for every student.
        MoheQuarterCreateService.requireMinistry(scope);
        // FIN-09: only the latest quarter on record holds the live data being archived.
        int period = q.getQuarter() != null ? q.getQuarter()
                : QuarterFinanceTotalsService.quarterKey(q.getQuarterYear(), q.getQuarterNumber());
        Integer latest = moheQuarterRepository.findMaxYearQuarter();
        if (latest != null && period != latest) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only the current quarter (" + latest + ") can be closed; " + period + " is not current. "
                            + "Its students have already moved on, and closing it would file the current "
                            + "quarter's data under " + period + ".");
        }
        Specification<StudentDatum> spec = Specification.where(null);

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
            // FIN-08: per currency and for this quarter only. It used to set the whole
            // live table's received amounts, every quarter and every currency added
            // together, against BUDGET_AMOUNT, itself USD + GBP + SAR added together.
            // Now each currency the quarter was funded in is set against what was
            // transferred in that currency for this quarter.
            Long budget = q.getBudgetAmount();
            if (budget != null && budget > 0) {
                Map<Integer, BigDecimal> received = receivedByCurrency(q);
                for (Object[] row : allocationRepository.sumTransferredForQuarterByCurrency(period)) {
                    Integer currency = (Integer) row[0];
                    BigDecimal cap = (BigDecimal) row[1];
                    BigDecimal paid = received.getOrDefault(currency, BigDecimal.ZERO);
                    if (cap != null && paid.compareTo(cap) > 0) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Total paid amount (" + paid.toPlainString() + ") exceeds quarter budget ("
                            + cap.toPlainString() + ") in currency " + currency + ". "
                            + "Adjust payments or call with ?force=true to close anyway.");
                    }
                }
                // The quarter's own received total. Still one column for every currency,
                // as the screen shows it; the check above is what is per currency.
                q.setPaidAmount(received.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).longValue());
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

    /** FIN-08: {@code STUDENT_FIN_RECIEVED_AMOUNT} of this quarter's finance rows, per currency. */
    private Map<Integer, BigDecimal> receivedByCurrency(MoheQuarter q) {
        Map<Integer, BigDecimal> out = new java.util.HashMap<>();
        for (Object[] row : studentFinanceRepository.sumReceivedByCurrencyForQuarter(q.getQuarterYear(), q.getQuarterNumber())) {
            BigDecimal amount = row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString());
            out.merge((Integer) row[0], amount, BigDecimal::add);
        }
        return out;
    }
}
