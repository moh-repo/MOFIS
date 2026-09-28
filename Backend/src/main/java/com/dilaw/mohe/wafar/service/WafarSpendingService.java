package com.dilaw.mohe.wafar.service;

import com.dilaw.mohe.codes.WafarSpendingStatus;

import com.dilaw.mohe.codes.WafarBeneficiaryType;

import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.config.tenant.WafarWriteKey;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.security.policy.Decision;
import com.dilaw.mohe.security.policy.WafarPolicy;
import com.dilaw.mohe.security.policy.WafarWrite;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.wafar.dto.WafarSpendingCategoryDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingCreateDto;
import com.dilaw.mohe.wafar.dto.WafarSpendingDto;
import com.dilaw.mohe.wafar.dto.WafarStudentFeePaymentDto;
import com.dilaw.mohe.wafar.model.WafarSpending;
import com.dilaw.mohe.wafar.model.WafarSpendingCategory;
import com.dilaw.mohe.wafar.model.WafarStudentFeePayment;
import com.dilaw.mohe.wafar.repository.WafarSpendingCategoryRepository;
import com.dilaw.mohe.wafar.repository.WafarSpendingRepository;
import com.dilaw.mohe.wafar.repository.WafarStudentFeePaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import com.dilaw.mohe.wafar.dto.WafarCorrectionRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Wafar transactions, scoped to the attaché that holds the money.
 *
 * <p>Wafar is budget already transferred to one attaché and never disbursed, so
 * it stays there: Cairo's savings pay only Cairo. Nothing in this service enforced
 * that — every method returned and accepted every country's rows — which is what
 * this change fixes.
 *
 * <p>Categories are deliberately left unscoped. They are a shared catalogue
 * (rent, salaries, university fee), not anybody's money.
 */
@Service
public class WafarSpendingService {

    @Autowired
    private com.dilaw.mohe.service.StudentAccessGuard studentAccessGuard;

    @Autowired
    private WafarBalanceService balanceService;

    @Autowired
    private WafarSpendingRepository spendingRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private WafarSpendingCategoryRepository categoryRepository;

    @Autowired
    private WafarStudentFeePaymentRepository feePaymentRepository;

    @Autowired
    private StudentDataRepository studentDataRepository;

    /** R-16: every ownership and maker-checker question goes to {@code WafarPolicy}. */
    @Autowired
    private AccessControl accessControl;

    /** N-04.d: the row-level write key, raised only for the audited super-admin hatch. */
    @Autowired
    private WafarWriteKey wafarWriteKey;

    /** Records the super admin's use of the approval escape hatch (decision D-13). */
    @Autowired
    private com.dilaw.mohe.ums.service.UmsAuditLogService auditLogService;


    // ── Categories ───────────────────────────────────────────────────────────

    public List<WafarSpendingCategoryDto> getActiveCategories() {
        return categoryRepository.findByIsActiveTrue().stream()
                .map(this::toCategoryDto)
                .collect(Collectors.toList());
    }

    // ── Transactions ─────────────────────────────────────────────────────────

    public List<WafarSpendingDto> getAll(Integer categoryId, String status, Integer studentId,
                                         LocalDate dateFrom, LocalDate dateTo) {
        CountryScope scope = countryScopeService.getScope();
        if (!scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty()) {
            return List.of();
        }
        boolean unscoped = scope.isMinistryUser();
        List<Integer> countryIds = unscoped ? List.of(-1) : scope.getAllowedCountryIds();

        return spendingRepository.findFiltered(categoryId, status, studentId, dateFrom, dateTo,
                        unscoped, countryIds)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public WafarSpendingDto getById(Integer id) {
        return toDto(findOrThrow(id));
    }

    /**
     * Records a spend as PENDING, stamped with the signed-in user as its maker.
     *
     * <p>The maker used to arrive as an optional {@code createdBy} request
     * parameter, so CREATED_BY was whatever the client said — or null, which is
     * what the frontend sent. {@link #approve} compares the approver against it
     * (plan item 1.1), and a comparison against a client-chosen value is no
     * separation of duties at all.
     */
    /** A spend names its category; findById(null) used to throw and answer 500 (R5-18). */
    private static void requireCategory(WafarSpendingCreateDto dto) {
        if (dto == null || dto.getCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the spending category (categoryId).");
        }
        requirePositiveAmount(dto.getAmount());
    }

    /**
     * FIN-01: a spend is strictly positive. {@link WafarBalanceService#requireCovers}
     * lets an amount of zero or less through (it has nothing to cover), and nothing else
     * refused one, so a spend of -5000 was recorded, approved, and raised the attaché's
     * balance by 5000. Checked here as well as on the DTO because the correction path
     * reads its amount back from stored request data, which no validator sees.
     */
    static void requirePositiveAmount(java.math.BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A Wafar spend must be a positive amount.");
        }
    }

    @Transactional
    public WafarSpendingDto create(WafarSpendingCreateDto dto) {
        requireCategory(dto);
        WafarSpendingCategory category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));

        // N-04: the subject-only question first, so a ministry-level account gets
        // the ownership refusal rather than "name the attaché".
        accessControl.assertAllowed(WafarPolicy.NAME, null, "RECORD");
        WafarSpending entity = new WafarSpending();
        entity.setCategory(category);
        mapCreateDtoToEntity(dto, entity);
        Integer country = resolveCountry(dto);
        accessControl.assertAllowed(WafarWrite.spending(country), "RECORD");
        entity.setStudyCountryInId(country);
        // Live QA M1 (D6-a): checked when recorded and again when approved.
        balanceService.requireCovers(country, entity.getAmount());
        entity.setStatus(WafarSpendingStatus.PENDING);
        entity.setCreatedBy(currentApprover());
        return toDto(spendingRepository.save(entity));
    }

    /**
     * Which attaché's Wafar this comes out of.
     *
     * <p>Resolved rather than trusted, in descending order of authority:
     *
     * <ol>
     *   <li>the named student's country — Wafar is spent where the student is, and
     *       the student record is the authority on that;
     *   <li>an explicit country on the request, checked against the caller's scope;
     *   <li>the caller's country, when they have exactly one.
     * </ol>
     *
     * <p>Otherwise this refuses. A ministry user spending on a non-student expense
     * has to say which attaché is paying — there is no sensible default, and
     * guessing is how the eleven unattributable rows already in the table came to
     * exist.
     */
    private Integer resolveCountry(WafarSpendingCreateDto dto) {
        CountryScope scope = countryScopeService.getScope();

        if (dto.getStudentId() != null && WafarBeneficiaryType.STUDENT.equals(dto.getBeneficiaryType())) {
            // A student the caller may not reach is refused here (N14): read through the
            // repository, a hidden student came back empty and the spend fell through to
            // the caller's own attaché below.
            Integer fromStudent = studentAccessGuard.requireStudent(dto.getStudentId()).getStudyCountryIn();
            if (fromStudent != null) {
                // Spending another attaché's Wafar on your own student is exactly the
                // cross-attaché move the rule forbids.
                countryScopeService.validateCountryAccess(fromStudent);
                return fromStudent;
            }
        }

        if (dto.getStudyCountryInId() != null) {
            countryScopeService.validateCountryAccess(dto.getStudyCountryInId());
            return dto.getStudyCountryInId();
        }

        List<Integer> allowed = scope.getAllowedCountryIds();
        if (!scope.isMinistryUser() && allowed.size() == 1) {
            return allowed.get(0);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The attaché paying for this must be given (studyCountryInId): "
                + "Wafar is held per attaché and cannot be spent from no one.");
    }

    @Transactional
    public WafarSpendingDto update(Integer id, WafarSpendingCreateDto dto) {
        requireCategory(dto);
        WafarSpending entity = findOrThrow(id);
        accessControl.assertAllowed(WafarWrite.of(entity), "EDIT");
        if (!WafarSpendingStatus.PENDING.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING transactions can be updated");
        }
        WafarSpendingCategory category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));
        entity.setCategory(category);
        mapCreateDtoToEntity(dto, entity);
        balanceService.requireCovers(entity.getStudyCountryInId(), entity.getAmount());
        return toDto(spendingRepository.save(entity));
    }

    /**
     * Records the signed-in user as the approver. The controller used to accept an optional
     * {@code approvedBy} parameter and this method wrote it as-is, so the approver of record
     * was whatever the client said — or null, which is what the frontend always sent.
     *
     * <p>Maker and checker must be two people, as in {@code AllocationService.approve}.
     * {@code WAFAR_APPROVE} is the same authority as {@code WAFAR.EDIT} today, so
     * anyone who can record a spend can also approve one; this check is what keeps
     * them from approving their own.
     *
     * <p>Since the N-04 pass (6 Sep 2026) the approver must also be the attaché's
     * own staff — {@code assertCountryOwnedWrite} — with one exception, decision
     * D-13(b): the super admin may approve or reject, because Wafar is spent at
     * attachés where one officer may be the whole finance desk and somebody has to
     * be the second pair of eyes. The super admin cannot record or edit a spend,
     * so the maker-checker rule needs no exemption for them any more; every use
     * of the hatch is written to the audit log as {@code WAFAR_SUPERADMIN_APPROVE}.
     */
    @Transactional
    public WafarSpendingDto approve(Integer id) {
        WafarSpending entity = findOrThrow(id);
        if (!WafarSpendingStatus.PENDING.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING transactions can be approved");
        }
        Integer approver = currentApprover();
        // R-16: ownership, the ministry refusal and maker-checker are WafarPolicy; the
        // super admin comes back with the D-13(b) exemption, audited here, and the
        // row-level write key is raised for this one transaction (N-04.d).
        Decision decision = accessControl.assertAllowed(WafarWrite.of(entity), "APPROVE");
        if (decision.via(WafarPolicy.SUPER_ADMIN_HATCH)) {
            auditLogService.log(approver.longValue(), "WAFAR_SUPERADMIN_APPROVE", "WAFAR_SPENDING",
                    id.longValue(), entity.getStatus(), WafarSpendingStatus.APPROVED);
            wafarWriteKey.elevateCurrentTransaction("super admin approving Wafar spend " + id + " (D-13 b)");
        }
        // Live QA M1: a spend was approved with a balance of 0. FIN-14: requireCovers
        // first takes the attaché's balance lock, so two approvals that each fit the
        // balance on their own cannot both pass it; the second waits and re-reads.
        requirePositiveAmount(entity.getAmount()); // FIN-01: a legacy row of 0 or less is not approved
        balanceService.requireCovers(entity.getStudyCountryInId(), entity.getAmount());
        entity.setStatus(WafarSpendingStatus.APPROVED);
        entity.setApprovedBy(approver);
        entity.setApprovedAt(Instant.now());
        spendingRepository.save(entity);

        if ("UNIVERSITY_FEE".equals(entity.getCategory().getCategoryCode()) && entity.getStudentId() != null) {
            updateStudentUniversityFee(entity);
        }
        return toDto(entity);
    }

    /** The signed-in user's id, as the CREATED_BY / APPROVED_BY columns store it. 401 if there is none. */
    private static Integer currentApprover() {
        return Math.toIntExact(SecurityRequestUtils.requireUserId());
    }

    @Transactional
    public WafarSpendingDto reject(Integer id, String reason) {
        WafarSpending entity = findOrThrow(id);
        if (!WafarSpendingStatus.PENDING.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING transactions can be rejected");
        }
        // Same rule as approve: the attaché's own staff, or the audited super admin (D-13).
        Integer rejecter = currentApprover();
        Decision decision = accessControl.assertAllowed(WafarWrite.of(entity), "REJECT");
        if (decision.via(WafarPolicy.SUPER_ADMIN_HATCH)) {
            auditLogService.log(rejecter.longValue(), "WAFAR_SUPERADMIN_REJECT", "WAFAR_SPENDING",
                    id.longValue(), entity.getStatus(), WafarSpendingStatus.REJECTED);
            wafarWriteKey.elevateCurrentTransaction("super admin rejecting Wafar spend " + id + " (D-13 b)");
        }
        entity.setStatus(WafarSpendingStatus.REJECTED);
        entity.setApprovedBy(rejecter);
        entity.setApprovedAt(Instant.now());
        entity.setRejectionReason(reason);
        return toDto(spendingRepository.save(entity));
    }

    @Transactional
    public void cancel(Integer id) {
        WafarSpending entity = findOrThrow(id);
        accessControl.assertAllowed(WafarWrite.of(entity), "DELETE");
        if (WafarSpendingStatus.APPROVED.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved transactions cannot be cancelled");
        }
        entity.setStatus(WafarSpendingStatus.CANCELLED);
        spendingRepository.save(entity);
    }

    // ── Student fee payments ──────────────────────────────────────────────────

    /**
     * Fee payments made from Wafar for one student. Scoped through the student
     * rather than the payment: asking by student id is the direct way to read
     * another attaché's spending, and the payment rows are only reachable that way.
     */
    public List<WafarStudentFeePaymentDto> getStudentFeePayments(Integer studentId) {
        // N14: a hidden student read as "no country", and canAccessCountry(null) is true.
        studentAccessGuard.requireStudent(studentId);
        List<WafarStudentFeePayment> payments = feePaymentRepository.findByStudentId(studentId);
        // One fetch for the spending rows the payments point at, not one per payment.
        List<Integer> spendingIds = payments.stream()
                .map(WafarStudentFeePayment::getSpendingId).filter(Objects::nonNull).distinct().toList();
        Map<Integer, WafarSpending> spendingById = spendingIds.isEmpty() ? Map.of()
                : spendingRepository.findAllById(spendingIds).stream()
                        .collect(Collectors.toMap(WafarSpending::getId, Function.identity()));
        return payments.stream().map(p -> {
            WafarStudentFeePaymentDto dto = new WafarStudentFeePaymentDto();
            dto.setId(p.getId());
            dto.setSpendingId(p.getSpendingId());
            dto.setStudentId(p.getStudentId());
            dto.setAcademicYear(p.getAcademicYear());
            dto.setNotes(p.getNotes());
            dto.setCreatedAt(p.getCreatedAt());
            WafarSpending s = spendingById.get(p.getSpendingId());
            if (s != null) {
                dto.setAmount(s.getAmount());
                dto.setSpendingDate(s.getSpendingDate());
                dto.setReferenceNumber(s.getReferenceNumber());
                dto.setStatus(s.getStatus());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * The single gate for every read and mutation by id: {@code getById},
     * {@code update}, {@code approve}, {@code reject} and {@code cancel} all come
     * through here, so the country check cannot be applied to some and forgotten
     * on others.
     *
     * <p>Out of scope returns 404, not 403. A 403 confirms the record exists,
     * which is enough to enumerate another attaché's transaction ids.
     */
    private WafarSpending findOrThrow(Integer id) {
        WafarSpending entity = spendingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spending record not found"));
        if (!countryScopeService.canAccessCountry(entity.getStudyCountryInId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Spending record not found");
        }
        // canAccessCountry passes a null through as "no country to check". On this
        // table a null is missing data, so it has to be ministry-only explicitly.
        if (entity.getStudyCountryInId() == null && !countryScopeService.canAccessAllCountries()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Spending record not found");
        }
        return entity;
    }

    private void mapCreateDtoToEntity(WafarSpendingCreateDto dto, WafarSpending entity) {
        entity.setAmount(dto.getAmount());
        entity.setSpendingDate(dto.getSpendingDate());
        entity.setReferenceNumber(dto.getReferenceNumber());
        entity.setDescriptionAr(dto.getDescriptionAr());
        entity.setDescriptionEn(dto.getDescriptionEn());
        entity.setBeneficiaryType(dto.getBeneficiaryType());
        // Only a STUDENT beneficiary keeps a studentId / payee fields; clear the rest so
        // stale values from a previous beneficiary type don't linger on the record.
        if (WafarBeneficiaryType.STUDENT.equals(dto.getBeneficiaryType())) {
            entity.setStudentId(dto.getStudentId());
            entity.setPayeeNameAr(null);
            entity.setPayeeNameEn(null);
        } else if ("OTHER".equals(dto.getBeneficiaryType())) {
            entity.setStudentId(null);
            entity.setPayeeNameAr(dto.getPayeeNameAr());
            entity.setPayeeNameEn(dto.getPayeeNameEn());
        } else if (WafarBeneficiaryType.MINISTRY.equals(dto.getBeneficiaryType())
                || WafarBeneficiaryType.CULTURAL_AFFAIRS.equals(dto.getBeneficiaryType())) {
            // Fixed institutional beneficiary — no student, name is derived on display.
            entity.setStudentId(null);
            entity.setPayeeNameAr(null);
            entity.setPayeeNameEn(null);
        } else {
            // No beneficiary type supplied (legacy callers): keep the old behaviour.
            entity.setStudentId(dto.getStudentId());
            entity.setPayeeNameAr(dto.getPayeeNameAr());
            entity.setPayeeNameEn(dto.getPayeeNameEn());
        }
        entity.setNotes(dto.getNotes());
    }

    private void updateStudentUniversityFee(WafarSpending spending) {
        studentDataRepository.findById(spending.getStudentId()).ifPresent(student -> {
            student.setStudyUniversityFeeStatusId(1);
            studentDataRepository.save(student);
        });

        WafarStudentFeePayment bridge = new WafarStudentFeePayment();
        bridge.setSpendingId(spending.getId());
        bridge.setStudentId(spending.getStudentId());
        feePaymentRepository.save(bridge);
    }

    private WafarSpendingDto toDto(WafarSpending e) {
        WafarSpendingDto dto = new WafarSpendingDto();
        dto.setId(e.getId());
        dto.setCategory(toCategoryDto(e.getCategory()));
        dto.setAmount(e.getAmount());
        dto.setSpendingDate(e.getSpendingDate());
        dto.setReferenceNumber(e.getReferenceNumber());
        dto.setDescriptionAr(e.getDescriptionAr());
        dto.setDescriptionEn(e.getDescriptionEn());
        dto.setPayeeNameAr(e.getPayeeNameAr());
        dto.setPayeeNameEn(e.getPayeeNameEn());
        dto.setStudentId(e.getStudentId());
        dto.setStudyCountryInId(e.getStudyCountryInId());
        dto.setBeneficiaryType(e.getBeneficiaryType());
        dto.setStatus(e.getStatus());
        dto.setNotes(e.getNotes());
        dto.setCreatedBy(e.getCreatedBy());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setApprovedBy(e.getApprovedBy());
        dto.setApprovedAt(e.getApprovedAt());
        dto.setRejectionReason(e.getRejectionReason());

        if (e.getStudentId() != null) {
            studentDataRepository.findById(e.getStudentId()).ifPresent(s ->
                    dto.setStudentName(s.getStudentName()));
        }
        return dto;
    }

    private WafarSpendingCategoryDto toCategoryDto(WafarSpendingCategory c) {
        return new WafarSpendingCategoryDto(c.getId(), c.getCategoryCode(),
                c.getCategoryNameAr(), c.getCategoryNameEn(), c.getIsActive());
    }

    // ========== N-04.e: the ministry's correction path ==========

    public static final String CORRECTION_ENTITY_TYPE = "WAFAR_CORRECTION";

    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.dilaw.mohe.approval.service.ApprovalWorkflowService approvalWorkflowService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private com.dilaw.mohe.ums.service.UmsAuditLogService umsAuditLogService;

    /**
     * A ministry account that finds a wrong Wafar row cannot edit it (N-04);
     * it raises a correction request instead, routed to the attaché's
     * administrator (V098). The attaché's own staff do not need this path:
     * for them the same change is an ordinary edit.
     */
    @Transactional
    public com.dilaw.mohe.approval.model.ApprovalRequest requestCorrection(Integer id, WafarCorrectionRequest correction) {
        if (!countryScopeService.isMinistryLevelActor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Corrections are for ministry accounts; the attaché's own staff edit the row directly.");
        }
        if (correction.changesNothing()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The correction changes nothing.");
        }
        if (correction.amount() != null) {
            requirePositiveAmount(correction.amount()); // FIN-01
        }
        WafarSpending entity = findOrThrow(id);
        if (entity.getStudyCountryInId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This row has no attaché on record, so nobody can approve a correction to it.");
        }
        if (Boolean.TRUE.equals(correction.cancel()) && !WafarSpendingStatus.PENDING.equals(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a PENDING transaction can be cancelled.");
        }
        long requester = com.dilaw.mohe.config.SecurityRequestUtils.requireUserId();
        try {
            String proposed = objectMapper.writeValueAsString(correction);
            String original = objectMapper.writeValueAsString(toDto(entity));
            com.dilaw.mohe.approval.model.ApprovalRequest request = approvalWorkflowService.createRequest(
                    CORRECTION_ENTITY_TYPE, com.dilaw.mohe.approval.model.RequestType.UPDATE, String.valueOf(id),
                    proposed, original, requester, entity.getStudyCountryInId());
            umsAuditLogService.log(requester, "WAFAR_CORRECTION_REQUESTED", "WAFAR_SPENDING", id.longValue(), null,
                    "approval request " + request.getId() + ": " + correction.reason());
            return request;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise the correction", e);
        }
    }

    /** Applies an approved correction as the approver (the attaché's administrator, or the audited super-admin hatch). */
    @Transactional
    public WafarSpendingDto applyCorrection(com.dilaw.mohe.approval.model.ApprovalRequest request) {
        WafarCorrectionRequest correction;
        try {
            correction = objectMapper.readValue(request.getRequestData(), WafarCorrectionRequest.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("cannot read the correction of request " + request.getId(), e);
        }
        WafarSpending entity = findOrThrow(Integer.valueOf(request.getEntityId()));
        List<String> changed = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(correction.cancel())) {
            if (WafarSpendingStatus.PENDING.equals(entity.getStatus())) {
                entity.setStatus(WafarSpendingStatus.CANCELLED);
                changed.add("status=CANCELLED");
            }
        } else {
            if (correction.amount() != null) {
                requirePositiveAmount(correction.amount()); // FIN-01: request data is not re-validated
                // An approved spend draws only the increase; a pending one is checked in full, as when recorded (M1).
                java.math.BigDecimal old = entity.getAmount() == null ? java.math.BigDecimal.ZERO : entity.getAmount();
                balanceService.requireCovers(entity.getStudyCountryInId(),
                        WafarSpendingStatus.APPROVED.equals(entity.getStatus()) ? correction.amount().subtract(old) : correction.amount());
                entity.setAmount(correction.amount());
                changed.add("amount=" + correction.amount());
            }
            if (correction.spendingDate() != null) { entity.setSpendingDate(correction.spendingDate()); changed.add("spendingDate=" + correction.spendingDate()); }
            if (correction.descriptionAr() != null) { entity.setDescriptionAr(correction.descriptionAr()); changed.add("descriptionAr"); }
            if (correction.descriptionEn() != null) { entity.setDescriptionEn(correction.descriptionEn()); changed.add("descriptionEn"); }
            if (correction.notes() != null) { entity.setNotes(correction.notes()); changed.add("notes"); }
        }
        WafarSpending saved = spendingRepository.save(entity);
        long by = com.dilaw.mohe.config.SecurityRequestUtils.currentUserIdOrDefault(
                request.getExecutedBy() != null ? request.getExecutedBy() : request.getRequestedBy());
        umsAuditLogService.log(by, "WAFAR_CORRECTED", "WAFAR_SPENDING", saved.getId().longValue(), null,
                "approval request " + request.getId() + " by user " + request.getRequestedBy() + ": " + String.join(", ", changed)
                        + " - " + correction.reason());
        return toDto(saved);
    }

}
