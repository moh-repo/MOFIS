package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinanceWafar;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.repository.spec.StudentWafarSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.wafar.service.WafarBalanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentFinanceWafarService {

    @Autowired
    private StudentFinanceWafarRepository repository;

    @Autowired
    private StudentFinanceRepository financeRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private com.dilaw.mohe.security.policy.AccessControl accessControl;

    @Autowired
    private WafarBalanceService balanceService;

    /**
     * Refuses (409) a change to a student's Wafar row that takes Wafar away from its
     * attaché while approved spending there already uses it (round-5 QA R5-04). A deposit
     * that is deleted or moves to another attaché takes its whole amount from the old one;
     * one that stays takes what it drops by. Checked before every write below, and by
     * the approval integration when the request is raised.
     *
     * @param before the row on file, or null when there is none
     * @param countryAfter the attaché the row will belong to, null when it goes
     * @param amountAfter what the row will hold, 0 when it goes
     */
    public void requireWithdrawable(StudentFinanceWafar before, Integer countryAfter, int amountAfter) {
        if (before == null) return;
        requireWithdrawable(before.getStudyCountryInId(), nz(before.getStudentFinRecievedAmount()), countryAfter, amountAfter);
    }

    /**
     * The same check for a finance request, made when it is raised (round-6 QA R6-01): a
     * finance edit that drops the Wafar was accepted and refused only at level 4, after
     * three approvers had acted. Mirrors what {@link #syncFromFinance} and
     * {@link #deleteIfExists} do to the student's Wafar row once the request executes.
     *
     * @param studentId the finance record's student
     * @param proposed  the finance record as the request would save it; null for a delete
     */
    public void requireWithdrawableByFinance(Integer studentId, StudentFinance proposed) {
        if (studentId == null) return;
        repository.findById(studentId).ifPresent(before -> {
            if (proposed == null || nz(proposed.getWafarAmount()) <= 0) {
                requireWithdrawable(before, null, 0);
            } else {
                requireWithdrawable(before, proposed.getStudyCountryInId(), nz(proposed.getWafarAmount()));
            }
        });
    }

    private void requireWithdrawable(Integer countryBefore, int amountBefore, Integer countryAfter, int amountAfter) {
        if (countryBefore == null) return; // counted in no attaché's balance
        int taken = Objects.equals(countryBefore, countryAfter) ? amountBefore - amountAfter : amountBefore;
        balanceService.requireWithdrawable(countryBefore, BigDecimal.valueOf(taken));
    }

    public List<StudentFinanceWafar> getAll() {
        return repository.findAll(StudentWafarSpec.countryScope(countryScopeService.getScope()));
    }

    public StudentFinanceWafar getById(Integer id) {
        StudentFinanceWafar wafar = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinanceWafar not found"));
        Integer countryId = wafar.getStudyCountryInId();
        // R-17: the country rule through CountryScopedPolicy; a null country stays readable
        // in scope as before (the write side, WafarPolicy, treats it as nobody's).
        if (countryId != null) {
            accessControl.assertAllowed(new com.dilaw.mohe.security.policy.CountryOwned("Wafar finance", countryId), "VIEW");
        }
        return wafar;
    }

    /**
     * STUDENT_FINANCE_WAFAR.STUDENT_ID is the primary key and is assigned by hand — there
     * is no identity generator behind it. Saving with a null id makes Hibernate raise
     * "ids for this class must be manually assigned before calling save()", which says
     * nothing about the actual mistake (no student was picked), so it is caught here.
     */
    @Transactional
    public StudentFinanceWafar create(StudentFinanceWafar entity) {
        if (entity == null || entity.getId() == null || entity.getId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A wafar record must be attached to a student: the student id is missing");
        }
        if (entity.getStudyFinanceMidate() == null) {
            entity.setStudyFinanceMidate(Instant.now());
        }
        // A row already on file is overwritten by the save.
        repository.findById(entity.getId()).ifPresent(before ->
                requireWithdrawable(before, entity.getStudyCountryInId(), nz(entity.getStudentFinRecievedAmount())));
        return repository.save(entity);
    }

    @Transactional
    public StudentFinanceWafar update(Integer id, StudentFinanceWafar entity) {
        Optional<StudentFinanceWafar> existing = repository.findById(id);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinanceWafar not found");
        }
        requireWithdrawable(existing.get(), entity.getStudyCountryInId(), nz(entity.getStudentFinRecievedAmount()));
        entity.setId(id);
        if (entity.getStudyFinanceMidate() == null) {
            entity.setStudyFinanceMidate(existing.get().getStudyFinanceMidate());
        }
        return repository.save(entity);
    }

    @Transactional
    public StudentFinanceWafar allocateUniversityFeeDiscount(StudyUniversityFee fee) {
        if (fee == null || fee.getStudentId() == null) {
            return null;
        }
        int discount = nz(fee.getStudyUniversityFeeDiscount());
        if (discount <= 0) {
            return null;
        }
        StudentFinanceWafar w = repository.findById(fee.getStudentId()).orElseGet(StudentFinanceWafar::new);
        // The discount replaces whatever the row held.
        requireWithdrawable(w.getStudyCountryInId(), nz(w.getStudentFinRecievedAmount()), w.getStudyCountryInId(), discount);
        w.setId(fee.getStudentId());
        w.setStudentFinName(fee.getStudyUniversityFeeStudentName());
        w.setStudyNote1("University fee discount (year " + (fee.getStudyUniversityFeeYear() != null ? fee.getStudyUniversityFeeYear() : "")
                + "), fee #" + fee.getId());
        w.setStudentFinRecievedAmount(discount);
        if (w.getStudyFinanceMidate() == null) {
            w.setStudyFinanceMidate(Instant.now());
        }
        return repository.save(w);
    }

    /**
     * Mirrors a finance record's wafar into STUDENT_FINANCE_WAFAR. Called from the
     * approval executor once a STUDENT_FINANCE request is approved — the finance form
     * no longer writes the wafar row itself, so this is the only path from that screen
     * into the wafar table.
     *
     * A wafar amount of zero (or none) means "Has wafar" was unticked, so any existing
     * row is removed rather than left behind at a stale amount.
     *
     * @param finance  the persisted finance record, for the resolved column values
     * @param wafarNote note from the approval payload; STUDENT_FINANCE has no column for it
     */
    @Transactional
    public StudentFinanceWafar syncFromFinance(StudentFinance finance, String wafarNote) {
        if (finance == null || finance.getId() == null || finance.getId() <= 0) {
            return null;
        }
        Integer studentId = finance.getId();

        if (nz(finance.getWafarAmount()) <= 0) {
            repository.findById(studentId).ifPresent(existing -> {
                requireWithdrawable(existing, null, 0);
                repository.deleteById(studentId);
            });
            return null;
        }

        StudentFinanceWafar w = repository.findById(studentId).orElseGet(StudentFinanceWafar::new);
        requireWithdrawable(w.getStudyCountryInId(), nz(w.getStudentFinRecievedAmount()),
                finance.getStudyCountryInId(), nz(finance.getWafarAmount()));
        w.setId(studentId);
        w.setStudentFinId(finance.getStudentFinId());
        w.setStudentFinName(finance.getStudentFinName());
        w.setStudentFinRecievedYear(finance.getStudentFinRecievedYear());
        w.setStudentFinRecievedMonth(finance.getStudentFinRecievedMonth());
        w.setStudentFinRecievedYearMonth(finance.getStudentFinRecievedYearMonth());
        // On the wafar row this column holds the wafar amount, not the finance amount.
        w.setStudentFinRecievedAmount(finance.getWafarAmount());
        /*
         * getAll() filters through StudentWafarSpec.countryScope(...), so a row without
         * a country is invisible on the wafar page to every country-scoped user.
         */
        w.setStudyCountryInId(finance.getStudyCountryInId());
        w.setStudyGovInId(finance.getStudyGovInId());
        w.setStudyFinanceBankeId(finance.getStudyFinanceBankeId());
        w.setStudyFinanceBankBranchId(finance.getStudyFinanceBankBranchId());
        w.setStudyFinanceDofId(finance.getStudyFinanceDofId());
        w.setStudyFinRecievedStatus(finance.getStudyFinRecievedStatus());
        w.setWafarTypeId(finance.getWafarTypeId());
        if (wafarNote != null) {
            w.setStudyNote1(wafarNote);
        }
        if (w.getStudyFinanceMidate() == null) {
            w.setStudyFinanceMidate(Instant.now());
        }
        return repository.save(w);
    }

    /** The wafar note on file for a student, or null. STUDENT_FINANCE has no column for it. */
    @Transactional(readOnly = true)
    public String noteOf(Integer studentId) {
        return studentId == null ? null
                : repository.findById(studentId).map(StudentFinanceWafar::getStudyNote1).orElse(null);
    }

    /** Removes a student's wafar row if present. Used when the finance record is deleted. */
    @Transactional
    public void deleteIfExists(Integer id) {
        if (id == null) return;
        repository.findById(id).ifPresent(existing -> {
            requireWithdrawable(existing, null, 0);
            repository.deleteById(id);
        });
    }

    // ── Wafar page -> STUDENT_FINANCE ─────────────────────────────────────────
    //
    // The mirror of syncFromFinance above. A wafar entered on the wafar page has to reach
    // the finance record too, or the finance screen and every report would keep reporting
    // an amount the student never received. NET_AMOUNT is a computed column, so writing
    // WAFAR_AMOUNT here is enough — the net follows on its own.

    /**
     * Checks a proposed wafar against the student's finance record. Called before the
     * approval request is raised so the user is told immediately, and again at execution
     * because the finance record can move between submitting and approving.
     *
     * @return the finance record the wafar belongs to
     * @throws ResponseStatusException with a message meant to be read by the user
     */
    public StudentFinance validateAgainstFinance(StudentFinanceWafar wafar) {
        if (wafar == null || wafar.getId() == null || wafar.getId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A wafar record must be attached to a student: the student id is missing");
        }
        StudentFinance finance = financeRepository.findById(wafar.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No student finance record exists for student " + wafar.getId()
                                + ", so a wafar cannot be recorded against it"));

        /*
         * The finance record is the anchor: a wafar always belongs to the period the money
         * was disbursed in. Without this a Q1 wafar could be deducted from a Q3 payment and
         * the mismatch would only ever show up as a wrong total in a report.
         */
        int financeYear = nz(finance.getStudentFinRecievedYear());
        int financeQuarter = nz(finance.getStudentFinRecievedMonth());
        int wafarYear = nz(wafar.getStudentFinRecievedYear());
        int wafarQuarter = nz(wafar.getStudentFinRecievedMonth());

        if (financeYear <= 0 || financeQuarter <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The finance record for student " + wafar.getId() + " has no year and quarter recorded ("
                            + financeYear + " Q" + financeQuarter + "), so a wafar cannot be matched to a period."
                            + " Set the period on the finance record first.");
        }
        if (wafarYear != financeYear || wafarQuarter != financeQuarter) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The wafar period (" + wafarYear + " Q" + wafarQuarter + ") does not match the finance record for"
                            + " student " + wafar.getId() + " (" + financeYear + " Q" + financeQuarter + ")."
                            + " A wafar can only be recorded against the same year and quarter.");
        }
        return finance;
    }

    /**
     * Writes an approved wafar back onto the student's finance record, so the finance
     * screen and every report see the deduction.
     *
     * Only the wafar columns are touched — the finance record's own amount and period are
     * left exactly as they are, since it is the anchor the wafar was validated against.
     */
    @Transactional
    public StudentFinance applyToFinance(StudentFinanceWafar wafar) {
        StudentFinance finance = validateAgainstFinance(wafar);
        finance.setWafarAmount(nz(wafar.getStudentFinRecievedAmount()));
        finance.setWafarTypeId(wafar.getWafarTypeId());
        return financeRepository.save(finance);
    }

    /**
     * Clears the wafar off a student's finance record. Used when the wafar row itself is
     * deleted — otherwise the finance record would keep deducting a wafar that no longer
     * exists, and NET_AMOUNT would stay permanently short.
     */
    @Transactional
    public void clearFinanceWafar(Integer studentId) {
        if (studentId == null) {
            return;
        }
        financeRepository.findById(studentId).ifPresent(finance -> {
            finance.setWafarAmount(0);
            finance.setWafarTypeId(0);
            financeRepository.save(finance);
        });
    }

    @Transactional
    public void delete(Integer id) {
        StudentFinanceWafar existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinanceWafar not found"));
        requireWithdrawable(existing, null, 0);
        repository.deleteById(id);
    }

    private static int nz(Integer v) {
        return v != null ? v.intValue() : 0;
    }
}
