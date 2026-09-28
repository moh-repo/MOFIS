package com.dilaw.mohe.service;

import com.dilaw.mohe.accounting.repository.WafarEarmarkRepository;
import com.dilaw.mohe.codes.WafarReconciliationStatus;
import com.dilaw.mohe.config.tenant.WafarWriteKey;
import com.dilaw.mohe.security.policy.Decision;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.WafarReconciliation;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.WafarReconciliationRepository;
import com.dilaw.mohe.repository.spec.StudentIdCountrySpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.security.policy.WafarWrite;
import com.dilaw.mohe.util.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares the Wafar recorded on each student's finance row with what accounting
 * earmarked for that student, and keeps <em>one</em> reconciliation row per student.
 *
 * <p>The two sides (decision DEC-01, 22 Sep 2026):
 * <ul>
 *   <li>{@code MOHE_AMOUNT} — {@code STUDENT_FINANCE.WAFAR_AMOUNT}, the operations side;</li>
 *   <li>{@code WAFAR_AMOUNT} — the sum of {@code FINCORE.WAFAR_EARMARK.AMOUNT} for the
 *       student in the latest quarter they have earmarks for, the accounting side.</li>
 * </ul>
 * A student with Wafar on neither side is matched. Until then the pass compared a
 * finance row's grant with the Wafar on the <em>same</em> row, so every student
 * without Wafar was a "discrepancy" for their whole grant: 920 of 941 (QA FIN-002).
 * The finance row carries no quarter, so the latest earmarked quarter is the one it
 * is held to — summing every quarter would flag a student earmarked twice.
 *
 * <p>A run used to insert a fresh {@code WAFAR_RECONCILIATION} row for every finance
 * row it looked at, whatever was already there. Every run therefore appended 941
 * rows: the summary counted them cumulatively, the discrepancy list showed the same
 * student once per run, and resolving one of them left the rest open. The javadoc
 * promised "creates or updates a row per student"; this is that.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class WafarReconciliationService {

    public static final String STATUS_MATCHED = WafarReconciliationStatus.MATCHED;
    public static final String STATUS_DISCREPANCY = WafarReconciliationStatus.DISCREPANCY;
    public static final String STATUS_RESOLVED = WafarReconciliationStatus.RESOLVED;

    private final WafarReconciliationRepository reconciliationRepository;
    private final StudentFinanceRepository financeRepository;
    private final WafarEarmarkRepository earmarkRepository;
    private final CountryScopeService countryScopeService;
    private final AccessControl accessControl;
    private final WafarWriteKey wafarWriteKey;

    public Page<WafarReconciliation> getAll(Pageable pageable) {
        return reconciliationRepository.findAll(
                StudentIdCountrySpec.countryScope(countryScopeService.getScope()), pageable);
    }

    public Page<WafarReconciliation> getByStatus(String status, Pageable pageable) {
        Specification<WafarReconciliation> spec = Specification
                .where(StudentIdCountrySpec.<WafarReconciliation>countryScope(countryScopeService.getScope()))
                .and((root, query, cb) -> cb.equal(root.get("reconStatus"), status));
        return reconciliationRepository.findAll(spec, pageable);
    }

    public List<WafarReconciliation> getDiscrepancies() {
        return reconciliationRepository.findUnresolvedDiscrepancies();
    }

    /** Open discrepancies, paged like the sibling list — one row per student since the upsert. */
    public Page<WafarReconciliation> getDiscrepancies(Pageable pageable) {
        return reconciliationRepository.findUnresolvedDiscrepancies(pageable);
    }

    /** Counts of the current state: with one row per student these no longer grow with every run. */
    public Map<String, Long> getSummary() {
        return Map.of(
            "total",       reconciliationRepository.count(),
            "pending",     reconciliationRepository.countByReconStatus(WafarReconciliationStatus.PENDING),
            "matched",     reconciliationRepository.countByReconStatus(STATUS_MATCHED),
            "discrepancy", reconciliationRepository.countByReconStatus(STATUS_DISCREPANCY),
            "resolved",    reconciliationRepository.countByReconStatus(STATUS_RESOLVED)
        );
    }

    /**
     * Runs a reconciliation pass: compares MOHE finance records against Wafar amounts
     * and creates or updates the one {@code WAFAR_RECONCILIATION} row per student.
     *
     * <p>Upsert rules:
     * <ul>
     *   <li>No row for the student: one is created.</li>
     *   <li>An open row (anything but RESOLVED): its amounts, status, date and notes are
     *       refreshed in place. Where earlier runs left several open rows for a student,
     *       the oldest is the one kept current; the duplicates are not touched.</li>
     *   <li>Only resolved rows: the student is left alone. A resolution is an
     *       operator's decision and a run must not reopen it — the figures the
     *       operator resolved against are still on the row.</li>
     * </ul>
     *
     * @return the number of students whose current row is a discrepancy after this run
     */
    @Transactional
    public int runReconciliation() {
        // N-04.b: reconciliation writes follow Wafar ownership — an attaché reconciles
        // its own statements; the ministry reads the result. The rows themselves are
        // already narrowed to the caller's countries by RLS on the finance table, so
        // only the ministry-level refusal is needed here.
        Decision decision = accessControl.assertAllowed(WafarPolicy.NAME, null, "RECONCILE");
        if (decision.via(WafarPolicy.SUPER_ADMIN_HATCH)) {
            // D-13(b) extended to the run (live QA M2, 23 Sep 2026). WAFAR_RECONCILIATION
            // sits under the N-04.d block predicate, which refuses a ministry connection
            // unless the write key is raised for the transaction; the key logs each use.
            wafarWriteKey.elevateCurrentTransaction("super admin running the Wafar reconciliation (D-13 b)");
        }
        List<StudentFinance> finances = financeRepository.findAll();
        String operator = SecurityContextHelper.currentUsername();
        Instant now = Instant.now();

        // One query for the existing rows rather than one per student.
        List<Integer> studentIds = finances.stream().map(StudentFinance::getId).toList();
        Map<Integer, WafarReconciliation> open = new HashMap<>();
        Map<Integer, WafarReconciliation> resolved = new HashMap<>();
        if (!studentIds.isEmpty()) {
            for (WafarReconciliation existing : reconciliationRepository.findByStudentIdInOrderByReconIdAsc(studentIds)) {
                Map<Integer, WafarReconciliation> bucket =
                        STATUS_RESOLVED.equals(existing.getReconStatus()) ? resolved : open;
                bucket.putIfAbsent(existing.getStudentId(), existing);
            }
        }

        Map<Integer, BigDecimal> earmarked = latestQuarterEarmarks();

        List<WafarReconciliation> toSave = new ArrayList<>();
        int discrepancies = 0;
        for (StudentFinance sf : finances) {
            int recorded = sf.getWafarAmount() != null ? sf.getWafarAmount().intValue() : 0;
            BigDecimal earmark = earmarked.get(sf.getId());
            int earmarkedAmount = earmark == null ? 0 : earmark.setScale(0, RoundingMode.HALF_UP).intValue();
            int delta = recorded - earmarkedAmount;

            WafarReconciliation recon = open.get(sf.getId());
            if (recon == null) {
                if (resolved.containsKey(sf.getId())) {
                    continue;
                }
                recon = new WafarReconciliation();
                recon.setStudentId(sf.getId());
                recon.setCreatedBy(operator);
            }
            recon.setReconDate(now);
            recon.setMoheAmount(recorded);
            recon.setWafarAmount(earmarkedAmount);
            if (delta == 0) {
                recon.setReconStatus(STATUS_MATCHED);
                recon.setNotes(null);
            } else {
                recon.setReconStatus(STATUS_DISCREPANCY);
                recon.setNotes(discrepancyNote(recorded, earmark == null ? null : earmarkedAmount));
                discrepancies++;
            }
            toSave.add(recon);
        }

        reconciliationRepository.saveAll(toSave);
        return discrepancies;
    }

    /**
     * Earmarked total per student, for the latest quarter each student has earmarks
     * in. Read under the caller's tenant scope, like the finance rows it is set against.
     */
    Map<Integer, BigDecimal> latestQuarterEarmarks() {
        Map<Integer, Integer> latestQuarter = new HashMap<>();
        Map<Integer, BigDecimal> amount = new HashMap<>();
        for (Object[] r : earmarkRepository.sumByStudentAndQuarter()) {
            Integer student = ((Number) r[0]).intValue();
            Integer quarter = ((Number) r[1]).intValue();
            BigDecimal sum = r[2] instanceof BigDecimal b ? b : new BigDecimal(r[2].toString());
            Integer seen = latestQuarter.get(student);
            if (seen == null || quarter > seen) {
                latestQuarter.put(student, quarter);
                amount.put(student, sum);
            }
        }
        return amount;
    }

    /** Why a student is a discrepancy, in the terms an officer checks. */
    static String discrepancyNote(int recorded, Integer earmarked) {
        if (earmarked == null) {
            return "Wafar " + recorded + " is on the finance record but nothing is earmarked in accounting";
        }
        if (recorded == 0) {
            return "Accounting earmarked " + earmarked + " but the finance record has no Wafar";
        }
        return "Finance record " + recorded + ", earmarked " + earmarked + " (difference " + (recorded - earmarked) + ")";
    }

    @Transactional
    public WafarReconciliation resolve(Integer reconId, String notes) {
        WafarReconciliation r = reconciliationRepository.findById(reconId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Reconciliation record not found: " + reconId));
        // N-04.b: the student's attaché resolves its own discrepancy; ministry accounts are refused.
        Integer country = financeRepository.findById(r.getStudentId())
                .map(StudentFinance::getStudyCountryInId)
                .orElse(null);
        accessControl.assertAllowed(WafarWrite.reconciliation(country), "RECONCILE");
        r.setReconStatus(STATUS_RESOLVED);
        r.setNotes(notes);
        r.setResolvedBy(SecurityContextHelper.currentUsername());
        r.setResolvedDate(Instant.now());
        return reconciliationRepository.save(r);
    }
}
