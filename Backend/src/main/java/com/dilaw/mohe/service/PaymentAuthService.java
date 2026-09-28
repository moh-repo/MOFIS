package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.PaymentAuthStatus;

import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.repository.PaymentAuthRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.StudentFinanceWafarRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeRepository;
import com.dilaw.mohe.repository.spec.StudentIdCountrySpec;
import com.dilaw.mohe.service.StudentYearResultService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.MakerChecker;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Transactional(readOnly = true)
@Service
public class PaymentAuthService {

    @Autowired private PaymentAuthRepository repository;
    @Autowired private StudentFinanceRepository financeRepository;
    @Autowired private StudentFinanceWafarRepository wafarRepository;
    @Autowired private StudyUniversityFeeRepository universityFeeRepository;
    @Autowired private StudentYearResultService yearResultService;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private AccessControl accessControl;
    @Autowired private com.dilaw.mohe.service.StudentAccessGuard studentAccessGuard;

    public Page<PaymentAuth> getAll(Pageable pageable) {
        return repository.findAll(StudentIdCountrySpec.countryScope(countryScopeService.getScope()), pageable);
    }

    public Page<PaymentAuth> search(String searchTerm, String status, Pageable pageable) {
        Specification<PaymentAuth> spec = Specification
                .where(paymentTextSearch(searchTerm))
                .and(paymentStatusFilter(status))
                .and(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));
        return repository.findAll(spec, pageable);
    }

    public List<PaymentAuth> getAllList() {
        return repository.findAll(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));
    }

    public PaymentAuth getById(Integer id) {
        PaymentAuth e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PaymentAuth not found"));
        requireInScope(e);
        return e;
    }

    /**
     * The caller may reach the payment's student (live QA N14, 26 Sep 2026): 404 for a
     * student hidden by row-level security or missing, 403 out of scope. The country used
     * to be read with {@code .orElse(null)} and a null let through, which is what a
     * hidden student returns.
     */
    private void requireInScope(PaymentAuth e) {
        studentAccessGuard.requireStudent(e.getStudentId());
    }

    /** A student's payments, for a student in the caller's scope (it read any student's; W4-03). */
    public List<PaymentAuth> getByStudentId(Integer studentId) {
        requireStudentInScope(studentId);
        return repository.findByStudentIdOrderByAuthMidateDesc(studentId);
    }

    /**
     * A new authorisation is always a draft (review F6): the status is not the
     * form's to set. It moves on only through {@link #bulkAuthorize} (release)
     * and {@link #markPaid}, each behind its own permission.
     */
    @Transactional
    public PaymentAuth create(PaymentAuth entity) {
        requireValid(entity);
        String studentName = requireStudentInScope(entity.getStudentId());
        entity.setId(null);
        entity.setStudentName(studentName);
        // Who created it is the signed-in user, not whatever the form sent (review G4, W4-03).
        entity.setCreatedBy((int) SecurityRequestUtils.requireUserId());
        entity.setAuthStatus(PaymentAuthStatus.DRAFT);
        entity.setAuthorizedBy(null);
        entity.setAuthMidate(Instant.now());
        computeNetAmount(entity);
        return repository.save(entity);
    }

    /**
     * Edits a draft (review F6). Once released or paid the record is the
     * statutory voucher and is not edited; its status, who released it, who
     * created it and when are kept from the stored row whatever the body says.
     */
    @Transactional
    public PaymentAuth update(Integer id, PaymentAuth entity) {
        PaymentAuth existing = getById(id); // validates existence and country scope
        requireDraft(existing, "edited");
        requireValid(entity);
        String studentName = requireStudentInScope(entity.getStudentId());
        entity.setId(id);
        entity.setStudentName(studentName);
        entity.setAuthStatus(existing.getAuthStatus());
        entity.setAuthorizedBy(existing.getAuthorizedBy());
        entity.setCreatedBy(existing.getCreatedBy());
        entity.setAuthMidate(existing.getAuthMidate());
        computeNetAmount(entity);
        return repository.save(entity);
    }

    @Transactional
    public void delete(Integer id) {
        PaymentAuth existing = getById(id); // validates existence and country scope
        requireDraft(existing, "deleted");
        repository.deleteById(id);
    }

    /**
     * Records that a released payment was paid out: APPROVED to PAID (review F6).
     * Until now PAID could only be picked from the form's free status list.
     */
    @Transactional
    public PaymentAuth markPaid(Integer id) {
        PaymentAuth existing = getById(id);
        if (!PaymentAuthStatus.APPROVED.equalsIgnoreCase(existing.getAuthStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an approved payment authorization can be marked as paid (current status: "
                            + existing.getAuthStatus() + ")");
        }
        existing.setAuthStatus(PaymentAuthStatus.PAID);
        existing.setAuthMidate(Instant.now());
        return repository.save(existing);
    }

    private static void requireDraft(PaymentAuth existing, String action) {
        if (!PaymentAuthStatus.DRAFT.equalsIgnoreCase(existing.getAuthStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a draft payment authorization can be " + action + " (current status: "
                            + existing.getAuthStatus() + ")");
        }
    }

    /** The fields the voucher cannot be issued without (review F6). */
    static void requireValid(PaymentAuth e) {
        List<String> problems = new ArrayList<>();
        if (e.getStudentId() == null || e.getStudentId() <= 0) problems.add("student");
        if (e.getAuthDate() == null) problems.add("date");
        BigDecimal original = e.getOriginalAmount();
        BigDecimal deductions = e.getDeductions() != null ? e.getDeductions() : BigDecimal.ZERO;
        if (original == null || original.signum() <= 0) problems.add("original amount (must be greater than 0)");
        if (deductions.signum() < 0) problems.add("deductions (must not be negative)");
        if (original != null && deductions.compareTo(original) > 0) problems.add("deductions (must not exceed the original amount)");
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Payment authorization is incomplete or invalid: " + String.join(", ", problems));
        }
    }

    /**
     * The student exists and is one the caller may reach; returns the name the voucher
     * prints. The same rule as the student record (N14): a student with no study country
     * is refused to a country account, which {@code canAccessCountry(null)} let through.
     */
    private String requireStudentInScope(Integer studentId) {
        return studentAccessGuard.requireStudent(studentId).getStudentName();
    }

    /**
     * Releases a set of DRAFT / PENDING payment records by id, best effort per row.
     *
     * <p><b>Not all-or-nothing</b>, whatever the previous javadoc said. Each id is
     * handled on its own: a row that is missing or already past DRAFT/PENDING is
     * reported under {@code failed} with a reason and the loop moves on, so the
     * caller learns which of a hundred payments did not go out rather than
     * getting the whole batch bounced by one stale row. The rows that did
     * succeed are committed together when this method returns.
     *
     * <p>The one thing that is <em>not</em> best effort is authorisation. A
     * scope refusal ({@link ResponseStatusException}) or an
     * {@link AccessDeniedException} is rethrown, and because this method is
     * transactional the rethrow rolls the batch back. Swallowing those into
     * {@code failed} would turn "you may not touch this attaché's payments"
     * into a line in a list.
     *
     * <p>The authoriser is the signed-in user, stamped on every released row
     * ({@code AUTHORIZED_BY}, V065). It used to be computed and returned in the
     * response only — never written — so the answer to "who released this?" was
     * whatever the client remembered (plan item 1.2).
     *
     * @return {@code authorized} (count), {@code failed} (list of {id, reason}),
     *         {@code authorizedBy}, {@code authorizedAt}
     */
    @Transactional
    public Map<String, Object> bulkAuthorize(List<Integer> paymentIds) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentIds must not be empty");
        }
        long operator = SecurityRequestUtils.requireUserId();
        Instant now = Instant.now();
        int authorized = 0;
        List<Map<String, Object>> failed = new ArrayList<>();

        for (Integer id : paymentIds) {
            try {
                PaymentAuth p = repository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Payment ID " + id + " not found"));
                // Another attaché's payment is refused, and the whole batch with it (review G4,
                // W4-03): the rows were loaded with no scope check, so any id could be released.
                requireInScope(p);
                if (!PaymentAuthStatus.DRAFT.equalsIgnoreCase(p.getAuthStatus())) {
                    failed.add(Map.of("id", id, "reason",
                            "Cannot authorize — current status: " + p.getAuthStatus()));
                    continue;
                }
                // Maker is not checker (live QA M2, decision D8-a): the creator's own
                // voucher fails on its own row and the rest of the batch goes on — the
                // rule Wafar spending, allocations and payment batches already follow.
                if (!accessControl.isAllowed(
                        MakerChecker.paymentAuth(p.getCreatedBy(), (int) operator), "APPROVE")) {
                    failed.add(Map.of("id", id, "reason", "Cannot authorize own voucher"));
                    continue;
                }
                // §5.3: Year-result guard — warn (but do not hard-block) if student has a failing year
                String yearWarning = p.getStudentId() != null
                        ? yearResultService.getPaymentWarning(p.getStudentId()) : null;
                // Must be a code from LU_PAYMENT_AUTH_STATUS: anything else has no
                // label and no filter entry, so the record would show a raw string
                // in the list and be unreachable from the status dropdown.
                p.setAuthStatus(PaymentAuthStatus.APPROVED);
                p.setAuthorizedBy(operator);
                p.setAuthMidate(now);
                if (yearWarning != null && p.getNotes() != null) {
                    p.setNotes(p.getNotes() + " | WARNING: " + yearWarning);
                } else if (yearWarning != null) {
                    p.setNotes("WARNING: " + yearWarning);
                }
                repository.save(p);
                authorized++;
            } catch (ResponseStatusException | AccessDeniedException refused) {
                throw refused;
            } catch (Exception ex) {
                // Map.of refuses a null value, and an exception with no message is
                // common (NPE, a bare IllegalStateException): the batch used to die
                // right here with a 500 instead of reporting the row.
                failed.add(Map.of("id", id, "reason", String.valueOf(ex.getMessage())));
            }
        }

        return Map.of(
                "authorized", authorized,
                "failed", failed,
                "authorizedBy", operator,
                "authorizedAt", now.toString()
        );
    }

    /**
     * Calculate the breakdown of amounts for a student based on the requested sources.
     * Sources: FINANCE, WAFAR, UNIVERSITY_FEE, COMBINATION, ALL
     * Returns a map with keys: financeAmount, wafarAmount, universityFeeAmount, totalAmount.
     */
    public Map<String, Object> calculateAmount(Integer studentId, String sourcesParam) {
        // The amounts are the student's finance, Wafar and fees: the caller must reach
        // the student (N14: this read any student's by id).
        studentAccessGuard.requireStudent(studentId);
        List<String> sources = sourcesParam != null
                ? Arrays.asList(sourcesParam.toUpperCase().split(","))
                : List.of("ALL");

        boolean includeFinance = sources.contains("ALL") || sources.contains("FINANCE") || sources.contains("COMBINATION");
        boolean includeWafar = sources.contains("ALL") || sources.contains("WAFAR") || sources.contains("COMBINATION");
        boolean includeUniFee = sources.contains("ALL") || sources.contains("UNIVERSITY_FEE") || sources.contains("COMBINATION");

        BigDecimal financeAmount = BigDecimal.ZERO;
        BigDecimal wafarAmount = BigDecimal.ZERO;
        BigDecimal universityFeeAmount = BigDecimal.ZERO;

        if (includeFinance) {
            financeRepository.findById(studentId).ifPresent(f -> {
                // already declared final inside lambda is fine with effectively-final trick via wrapper
            });
            var finOpt = financeRepository.findById(studentId);
            if (finOpt.isPresent() && finOpt.get().getStudentFinRecievedAmount() != null) {
                financeAmount = BigDecimal.valueOf(finOpt.get().getStudentFinRecievedAmount());
            }
        }

        final BigDecimal[] wafarRef = {BigDecimal.ZERO};
        if (includeWafar) {
            wafarRepository.findById(studentId).ifPresent(w -> {
                if (w.getStudentFinRecievedAmount() != null) {
                    wafarRef[0] = BigDecimal.valueOf(w.getStudentFinRecievedAmount());
                }
            });
            wafarAmount = wafarRef[0];
        }

        final BigDecimal[] feeRef = {BigDecimal.ZERO};
        if (includeUniFee) {
            universityFeeRepository.findTopByStudentIdOrderByStudyUniversityFeeMidateDesc(studentId)
                    .ifPresent(uf -> {
                        int amount = uf.getStudyUniversityFeeAmount() != null ? uf.getStudyUniversityFeeAmount() : 0;
                        int discount = uf.getStudyUniversityFeeDiscount() != null ? uf.getStudyUniversityFeeDiscount() : 0;
                        feeRef[0] = BigDecimal.valueOf(Math.max(0, amount - discount));
                    });
            universityFeeAmount = feeRef[0];
        }

        BigDecimal total = financeAmount.add(wafarAmount).add(universityFeeAmount);

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("financeAmount", financeAmount);
        result.put("wafarAmount", wafarAmount);
        result.put("universityFeeAmount", universityFeeAmount);
        result.put("totalAmount", total);
        result.put("sources", sources);
        return result;
    }

    private void computeNetAmount(PaymentAuth entity) {
        BigDecimal original = entity.getOriginalAmount() != null ? entity.getOriginalAmount() : BigDecimal.ZERO;
        BigDecimal deductions = entity.getDeductions() != null ? entity.getDeductions() : BigDecimal.ZERO;
        entity.setNetAmount(original.subtract(deductions));
    }

    private static Specification<PaymentAuth> paymentTextSearch(String term) {
        if (term == null || term.isBlank()) return null;
        return (root, query, cb) -> {
            String like = "%" + term + "%";
            return cb.or(
                    cb.like(root.get("studentName"), like),
                    cb.like(root.get("authFormNumber"), like),
                    cb.like(root.get("recipientName"), like),
                    cb.like(root.get("ministryName"), like)
            );
        };
    }

    private static Specification<PaymentAuth> paymentStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("authStatus"), status);
    }
}
