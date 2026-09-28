package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.InfoReviewStatus;

import com.dilaw.mohe.codes.InfoRequestStatus;
import com.dilaw.mohe.config.SecurityRequestUtils;

import com.dilaw.mohe.model.StudentInfoRequest;
import com.dilaw.mohe.model.StudentInfoRequestStaging;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentInfoRequestRepository;
import com.dilaw.mohe.repository.StudentInfoRequestStagingRepository;
import com.dilaw.mohe.repository.spec.StudentIdCountrySpec;
import com.dilaw.mohe.repository.spec.StudentInfoRequestSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.util.PhoneNumbers;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentInfoRequestService {

    @Autowired
    private StudentInfoRequestRepository repository;
    @Autowired
    private StudentInfoRequestStagingRepository stagingRepository;
    @Autowired
    private StudentDataRepository studentDataRepository;
    @Autowired
    private StudentDataService studentDataService;
    @Autowired
    private CountryScopeService countryScopeService;
    @Autowired
    private StudentAccessGuard studentAccessGuard;

    @Autowired
    private com.dilaw.mohe.approval.service.ApprovalIntegrationService approvalIntegrationService;

    @Autowired
    private com.dilaw.mohe.repository.StudentFinanceRepository studentFinanceRepository;

    @Autowired
    private com.dilaw.mohe.security.field.FieldAccessService fieldAccess;

    @Autowired
    private StudentReferenceValidator studentReferenceValidator;

    @Autowired
    private StudyFinReceivedStatusService receivedStatuses;

    /** Spring's mapper for the detached copies: it ignores the portal's extra keys. */
    @Autowired
    private ObjectMapper jsonMapper;

    private final ObjectMapper objectMapper = new ObjectMapper() {{
        registerModule(new JavaTimeModule());
    }};

    @Autowired
    private com.dilaw.mohe.repository.StudentInfoRequestKindRepository kindRepository;

    /** The type of a request that names none (decision D-8); a seeded kind (V123). */
    static final String DEFAULT_KIND = "OTHER";

    /**
     * The request's type: one of the active kinds (round-5 QA R5-26). Any string used to
     * be stored, and a type off the list (QA's {@code GENERAL}) reached the portal as a
     * raw code in both languages; a request that named none was stored as
     * {@code CUSTOM}, which is not a kind either.
     */
    String resolveKind(String requested) {
        if (requested == null || requested.isBlank()) {
            return DEFAULT_KIND;
        }
        String code = requested.trim();
        List<String> active = kindRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(k -> !Boolean.FALSE.equals(k.getIsActive()))
                .map(com.dilaw.mohe.model.StudentInfoRequestKind::getKindCode)
                .toList();
        if (!active.contains(code)) {
            // Keyed, so an Arabic caller is answered in Arabic like every other refusal (round-6 QA R6-02).
            throw new com.dilaw.mohe.exception.LocalizedStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unknown request type '" + code + "'. Choose one of: " + String.join(", ", active) + ".",
                    "infoRequest.unknownType", code, active);
        }
        return code;
    }

    @Transactional
    public List<StudentInfoRequest> createRequests(String requestType, String title, String description,
                                                    Instant dueDate, String createdBy,
                                                    List<Integer> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) return List.of();
        String kind = resolveKind(requestType); // before anything is saved
        List<StudentInfoRequest> created = new java.util.ArrayList<>();
        for (Integer studentId : studentIds) {
            StudentInfoRequest r = new StudentInfoRequest();
            r.setStudentId(studentId);
            r.setRequestType(kind);
            r.setTitle(title != null ? title : "Request");
            r.setDescription(description);
            r.setCreatedBy(createdBy);
            r.setCreatedAt(Instant.now());
            r.setDueDate(dueDate);
            r.setStatus(InfoRequestStatus.PENDING);
            created.add(repository.save(r));
        }
        return created;
    }

    /**
     * Resolve target student ids: from an explicit list, or "all" with an optional
     * countryId/statusId filter. Every id returned is one the caller may reach.
     *
     * <p>Live QA H2 (25 Sep 2026): a Jordan account raised a request for an Egypt
     * student. Row-level security did not stop it: the request row takes its
     * country from a trigger <em>after</em> the insert, so the block predicate saw
     * none. An explicit list is therefore checked here, student by student and
     * before anything is saved, through the same guard as every other per-student
     * endpoint. "All" is narrowed to the caller's study countries in the query
     * itself.
     *
     * <p>{@code countryId} is the <b>study</b> country (FIX-PLAN P5-4, 26 Sep 2026: it was
     * the nationality, and the dialog listed home countries). A country account naming a
     * study country outside its scope is refused (403) before anything is saved.
     */
    public List<Integer> resolveTargetStudentIds(List<Integer> studentIds, boolean all,
                                                  Integer countryId, Integer statusId) {
        if (studentIds != null && !studentIds.isEmpty()) {
            List<Integer> distinct = studentIds.stream().distinct().toList();
            distinct.forEach(studentAccessGuard::requireStudent);
            return distinct;
        }
        if (!all) return List.of();
        CountryScope scope = countryScopeService.getScope();
        if (scope.isMinistryUser()) {
            return studentDataRepository.findStudentIdsByOptionalFilters(countryId, statusId);
        }
        if (scope.getAllowedCountryIds().isEmpty()) return List.of();
        if (countryId != null && !scope.getAllowedCountryIds().contains(countryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
        }
        return studentDataRepository.findStudentIdsByOptionalFiltersInStudyCountries(
                countryId, statusId, scope.getAllowedCountryIds());
    }

    public Page<StudentInfoRequest> findWithFilters(String createdBy, String status, String requestType,
                                                     Integer studentId, Instant dateFrom, Instant dateTo,
                                                     Pageable pageable) {
        Specification<StudentInfoRequest> spec = Specification
                .where(StudentInfoRequestSpec.filter(createdBy, status, requestType, studentId, dateFrom, dateTo))
                .and(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));
        return repository.findAll(spec, pageable);
    }

    public Page<Map<String, Object>> findWithFiltersEnriched(String createdBy, String status, String requestType,
                                                              Integer studentId, Instant dateFrom, Instant dateTo,
                                                              Pageable pageable) {
        Page<StudentInfoRequest> page = findWithFilters(createdBy, status, requestType, studentId, dateFrom, dateTo, pageable);
        java.util.Set<Integer> ids = page.getContent().stream()
                .map(StudentInfoRequest::getStudentId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet());
        Map<Integer, String> nameMap = ids.isEmpty() ? Map.of() :
                studentDataRepository.findStudentOptionsByIds(ids).stream()
                        .filter(o -> o.getId() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                o -> o.getId().intValue(),
                                o -> o.getName() != null ? o.getName() : "",
                                (a, b) -> a));
        return page.map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("studentId", r.getStudentId());
            m.put("studentName", nameMap.getOrDefault(r.getStudentId(), ""));
            m.put("requestType", r.getRequestType());
            m.put("title", r.getTitle());
            m.put("description", r.getDescription());
            m.put("createdBy", r.getCreatedBy());
            m.put("createdAt", r.getCreatedAt());
            m.put("dueDate", r.getDueDate());
            m.put("status", r.getStatus());
            m.put("responseText", r.getResponseText());
            m.put("responseAt", r.getResponseAt());
            m.put("responseDocumentId", r.getResponseDocumentId());
            m.put("reviewStatus", r.getReviewStatus());
            m.put("reviewedBy", r.getReviewedBy());
            m.put("reviewedAt", r.getReviewedAt());
            m.put("rejectionReason", r.getRejectionReason());
            return m;
        });
    }

    public Optional<StudentInfoRequest> findById(Integer id) {
        return findInScope(id);
    }

    /**
     * The request, provided the caller may reach its student; empty when there is
     * no such request (or row-level security hides it).
     *
     * <p>Live QA H1/H2 (25 Sep 2026). The request row is under row-level security,
     * its staging row is not (it has no country column), and the Java check here
     * used to read the student's country with {@code orElse(null)} and let a null
     * through — which is exactly what a student hidden by that same policy
     * returns. Every read and decision on a request goes through this instead.
     *
     * @throws ResponseStatusException 403 when the student is outside the caller's scope
     */
    private Optional<StudentInfoRequest> findInScope(Integer id) {
        return repository.findById(id).map(r -> {
            studentAccessGuard.requireStudent(r.getStudentId());
            return r;
        });
    }

    /** Per-student read: the student must be in the caller's country scope (403 otherwise). */
    public Page<StudentInfoRequest> findByStudentId(Integer studentId, Pageable pageable) {
        studentAccessGuard.requireStudent(studentId);
        return repository.findByStudentId(studentId, pageable);
    }

    @Transactional
    public Optional<StudentInfoRequest> cancel(Integer id, Long cancelledBy) {
        return findInScope(id).map(r -> {
            r.setStatus(InfoRequestStatus.CANCELLED);
            return repository.save(r);
        });
    }

    @Transactional
    public Optional<StudentInfoRequest> reject(Integer id, String rejectionReason, Long reviewedBy) {
        return findInScope(id).map(r -> {
            r.setReviewStatus(InfoReviewStatus.REJECTED);
            r.setRejectionReason(rejectionReason);
            r.setReviewedBy(reviewedBy);
            r.setReviewedAt(Instant.now());
            return repository.save(r);
        });
    }

    @Transactional
    public Optional<StudentInfoRequest> approve(Integer id, Long reviewedBy) {
        return findInScope(id).map(r -> {
            // Only an answer can be approved; approve-and-apply already refused
            // anything else, this path accepted a pending or cancelled request.
            if (!InfoRequestStatus.RESPONDED.equals(r.getStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Only a request the student has answered can be approved.");
            r.setReviewStatus(InfoReviewStatus.APPROVED);
            r.setRejectionReason(null);
            r.setReviewedBy(reviewedBy);
            r.setReviewedAt(Instant.now());
            return repository.save(r);
        });
    }

    @Transactional
    public StudentInfoRequest save(StudentInfoRequest r) {
        return repository.save(r);
    }

    /** The student's submitted data for a request the caller may reach (live QA H1). */
    public Optional<StudentInfoRequestStaging> getStaging(Integer requestId) {
        return findInScope(requestId).flatMap(r -> stagingRepository.findById(requestId));
    }

    // ── PRT-05: what a student may propose through an answer ────────────────────

    private static final java.util.Set<String> CONTACT_FIELDS = java.util.Set.of(
            "studentEmail", "studentCellphone", "studentHomeNumber", "studentEmergencyContact");
    private static final java.util.Set<String> IDENTITY_FIELDS = java.util.Set.of(
            "studentName", "studentGender", "studentBirthdate", "studentBirthdatePlace", "studentPassportNumber",
            "studentPassportIssueDate", "studentPassportExpireDate", "studentPassportIssueCountryId");
    private static final java.util.Set<String> STUDY_FIELDS = java.util.Set.of(
            "studyUniversityIn", "studyCollegeIn", "studyMajorIn", "studyEducationIn", "studentCurrentLevelId");

    private static java.util.Set<String> union(java.util.Set<?>... sets) {
        java.util.Set<String> all = new java.util.HashSet<>();
        for (java.util.Set<?> set : sets) set.forEach(f -> all.add((String) f));
        return java.util.Set.copyOf(all);
    }

    /**
     * PRT-05: the student-record fields an answer to each kind of request may propose. An
     * answer's proposed data used to be staged whole, and staff approval raised whatever
     * differed — {@code studentAmount}, a status, the study country — as though the student
     * were entitled to ask for it. These are the fields the change-request form offers
     * ({@code StudentChangeRequestService.ALLOWED_FIELDS}), less the study country (a move
     * relocates files and ends sessions; it goes through a change request), narrowed to what
     * each kind of request is about. A kind not listed here gets the contact fields.
     */
    static final Map<String, java.util.Set<String>> STUDENT_FIELDS_BY_KIND = Map.of(
            "STATUS_CERT", union(CONTACT_FIELDS, STUDY_FIELDS),
            "TRANSCRIPT", union(CONTACT_FIELDS, STUDY_FIELDS),
            "BANK_COVER_LETTER", union(CONTACT_FIELDS, IDENTITY_FIELDS),
            "FIN_SUMMARY", union(CONTACT_FIELDS, IDENTITY_FIELDS),
            DEFAULT_KIND, union(CONTACT_FIELDS, IDENTITY_FIELDS, STUDY_FIELDS));

    /**
     * PRT-05: no finance field may be proposed from the portal: amounts, dates and payment
     * status are the finance office's to set, whatever the request asked for.
     */
    static final java.util.Set<String> FINANCE_FIELDS_FROM_PORTAL = java.util.Set.of();

    static java.util.Set<String> studentFieldsFor(String kind) {
        return STUDENT_FIELDS_BY_KIND.getOrDefault(kind, CONTACT_FIELDS);
    }

    /** The proposal as it is staged: only permitted fields, null for a half with nothing in it. */
    public record PortalProposal(String studentDataJson, String studentFinanceJson) {
        public boolean isEmpty() {
            return studentDataJson == null && studentFinanceJson == null;
        }
    }

    /**
     * PRT-05: checks the data a student sends with an answer, before anything is stored.
     *
     * <p>The portal's "include my data" button sends the student's whole record back, so a
     * field outside the request kind's list is not refused for being present: it is dropped
     * when it holds the value already stored, and refused (400, naming the fields) when it
     * would change it. Keys the record does not have (display names) are dropped.
     *
     * @throws com.dilaw.mohe.exception.LocalizedStatusException 400 for a field the request
     *         may not change, or for data that is not a JSON object
     */
    public PortalProposal checkPortalProposal(StudentInfoRequest req, String studentDataJson, String studentFinanceJson) {
        List<String> refused = new java.util.ArrayList<>();
        StudentDatum student = isBlank(studentDataJson) ? null
                : studentDataRepository.findById(req.getStudentId()).orElse(null);
        String studentOut = filterProposal(studentDataJson, StudentDatum.class, student,
                studentFieldsFor(req.getRequestType()), refused);
        StudentFinance finance = isBlank(studentFinanceJson) ? null
                : studentFinanceRepository.findById(req.getStudentId()).orElse(null);
        String financeOut = filterProposal(studentFinanceJson, StudentFinance.class, finance,
                FINANCE_FIELDS_FROM_PORTAL, refused);
        if (!refused.isEmpty()) {
            throw new com.dilaw.mohe.exception.LocalizedStatusException(HttpStatus.BAD_REQUEST,
                    "These fields cannot be changed through this request: " + String.join(", ", refused),
                    "portal.infoRequest.fieldNotAllowed", String.join(", ", refused));
        }
        return new PortalProposal(studentOut, financeOut);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || "null".equals(s.strip());
    }

    private <T> String filterProposal(String json, Class<T> type, T stored, java.util.Set<String> allowed,
                                      List<String> refused) {
        if (isBlank(json)) return null;
        com.fasterxml.jackson.databind.JsonNode node;
        try {
            node = jsonMapper.readTree(json);
        } catch (JsonProcessingException e) {
            node = null;
        }
        if (node == null || !node.isObject()) {
            throw new com.dilaw.mohe.exception.LocalizedStatusException(HttpStatus.BAD_REQUEST,
                    "The submitted data could not be read.", "portal.infoRequest.proposalInvalid");
        }
        com.fasterxml.jackson.databind.node.ObjectNode kept = jsonMapper.createObjectNode();
        java.util.Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> e = fields.next();
            java.lang.reflect.Field field = entityField(type, e.getKey());
            if (field == null) continue;
            if (allowed.contains(e.getKey())) {
                kept.set(e.getKey(), e.getValue());
            } else if (changes(field, stored, e.getValue())) {
                refused.add(e.getKey());
            }
        }
        return kept.isEmpty() ? null : kept.toString();
    }

    private static java.lang.reflect.Field entityField(Class<?> type, String name) {
        try {
            java.lang.reflect.Field f = type.getDeclaredField(name);
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) return null;
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** Whether {@code proposed} differs from the stored value; unreadable counts as a change. */
    private boolean changes(java.lang.reflect.Field field, Object stored, com.fasterxml.jackson.databind.JsonNode proposed) {
        try {
            Object current = stored == null ? null : field.get(stored);
            Object wanted = proposed == null || proposed.isNull() ? null : jsonMapper.treeToValue(proposed, field.getType());
            if (current instanceof java.math.BigDecimal a && wanted instanceof java.math.BigDecimal b) {
                return a.compareTo(b) != 0;
            }
            return !java.util.Objects.equals(current, wanted);
        } catch (Exception unreadable) {
            return true;
        }
    }

    @Transactional
    public void saveStaging(Integer requestId, String studentDataJson, String studentFinanceJson) {
        StudentInfoRequestStaging staging = stagingRepository.findById(requestId)
                .orElse(new StudentInfoRequestStaging());
        staging.setRequestId(requestId);
        staging.setStudentDataJson(studentDataJson);
        staging.setStudentFinanceJson(studentFinanceJson);
        staging.setSubmittedAt(Instant.now());
        stagingRepository.save(staging);
    }

    /**
     * Apply to main tables: from body if provided, otherwise from staging. Then mark request as APPROVED.
     */
    @Transactional
    public Optional<Map<String, Object>> approveAndApply(Integer id, Map<String, Object> body, Long reviewedBy) {
        Optional<StudentInfoRequest> opt = findInScope(id);
        if (opt.isEmpty()) return Optional.empty();
        StudentInfoRequest req = opt.get();
        if (!InfoRequestStatus.RESPONDED.equals(req.getStatus())) return Optional.empty();
        Integer studentId = req.getStudentId();

        boolean fromStaging = (body == null || body.isEmpty() || (!body.containsKey("student") && !body.containsKey("finance")));
        if (fromStaging) {
            Optional<StudentInfoRequestStaging> stagingOpt = stagingRepository.findById(id);
            if (stagingOpt.isEmpty())
                throw new IllegalStateException("No staging data to apply. Student must submit data for review first.");
            StudentInfoRequestStaging staging = stagingOpt.get();
            body = new java.util.HashMap<>();
            try {
                if (staging.getStudentDataJson() != null && !staging.getStudentDataJson().isBlank())
                    body.put("student", objectMapper.readValue(staging.getStudentDataJson(), Map.class));
                if (staging.getStudentFinanceJson() != null && !staging.getStudentFinanceJson().isBlank())
                    body.put("finance", objectMapper.readValue(staging.getStudentFinanceJson(), Map.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Invalid staging JSON: " + e.getMessage(), e);
            }
        }

        // The finance half changes STUDENT_FINANCE, which needs the finance write
        // right; the controller already required the student one (live QA H3).
        if (body.get("finance") != null
                && !SecurityRequestUtils.hasAuthority("FINANCE.EDIT"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Applying finance data needs the finance edit permission.");

        // H3-b (D4-a): the answer's data goes through the STUDENT_DATA / STUDENT_FINANCE
        // approval chains like any other edit, instead of being written here. Only what
        // differs from the stored record is raised; nothing is raised for no change.
        Long studentApproval = body.get("student") instanceof Map<?, ?> student
                ? raiseStudentChange(studentId, student, reviewedBy) : null;
        Long financeApproval = body.get("finance") instanceof Map<?, ?> finance
                ? raiseFinanceChange(studentId, finance, reviewedBy) : null;

        req.setReviewStatus(InfoReviewStatus.APPROVED);
        req.setReviewedBy(reviewedBy);
        req.setReviewedAt(Instant.now());
        req.setRejectionReason(null);
        StudentInfoRequest saved = repository.save(req);
        Map<String, Object> outcome = new java.util.LinkedHashMap<>();
        outcome.put("request", saved);
        outcome.put("studentApprovalRequestId", studentApproval);
        outcome.put("financeApprovalRequestId", financeApproval);
        outcome.put("status", studentApproval != null || financeApproval != null ? "SUBMITTED_FOR_APPROVAL" : "NO_CHANGES");
        return Optional.of(outcome);
    }

    /**
     * Raises a STUDENT_DATA UPDATE request for the fields the answer changes, with the
     * checks of a staff edit (field masks, phone numbers, lookup references). Keys the
     * student record does not have are ignored. Null when nothing changes.
     */
    private Long raiseStudentChange(Integer studentId, Map<?, ?> proposedFields, Long by) {
        StudentDatum stored = studentDataService.getStudentData(studentId);
        StudentDatum original = copy(stored, StudentDatum.class);
        StudentDatum proposed = overlay(stored, proposedFields, StudentDatum.class);
        proposed.setStudentId(studentId);
        if (jsonMapper.valueToTree(original).equals(jsonMapper.valueToTree(proposed))) return null;

        fieldAccess.assertMayEdit(StudentDatum.class, original, proposed, false);
        PhoneNumbers.requireIfChanged("studentCellphone", original.getStudentCellphone(), proposed.getStudentCellphone(), true);
        PhoneNumbers.requireIfChanged("studentHomeNumber", original.getStudentHomeNumber(), proposed.getStudentHomeNumber(), false);
        PhoneNumbers.requireIfChanged("studentEmergencyContact",
                original.getStudentEmergencyContact(), proposed.getStudentEmergencyContact(), false);
        studentReferenceValidator.requireExistingReferences(proposed);
        return approvalIntegrationService.createStudentDataRequest(
                com.dilaw.mohe.approval.model.RequestType.UPDATE, proposed, original, by).getId();
    }

    /** As {@link #raiseStudentChange}, for the finance record, with the finance form's rules. */
    private Long raiseFinanceChange(Integer studentId, Map<?, ?> proposedFields, Long by) {
        StudentFinance stored = studentFinanceRepository.findById(studentId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "This student has no finance record to update; add it from Finance Records first."));
        StudentFinance original = copy(stored, StudentFinance.class);
        StudentFinance proposed = overlay(stored, proposedFields, StudentFinance.class);
        proposed.setId(studentId);
        if (jsonMapper.valueToTree(original).equals(jsonMapper.valueToTree(proposed))) return null;

        fieldAccess.assertMayEdit(StudentFinance.class, original, proposed, false);
        StudentFinanceInputRules.require(proposed, receivedStatuses.paidStatusIds()::contains);
        return approvalIntegrationService.createStudentFinanceRequest(
                com.dilaw.mohe.approval.model.RequestType.UPDATE, proposed, original, by).getId();
    }

    /** A detached copy, so the managed row is never changed outside an approval. */
    private <T> T copy(T source, Class<T> type) {
        try {
            return jsonMapper.treeToValue(jsonMapper.valueToTree(source), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot copy " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** A detached copy of {@code source} with the proposed fields it has laid over it. */
    private <T> T overlay(T source, Map<?, ?> proposedFields, Class<T> type) {
        com.fasterxml.jackson.databind.node.ObjectNode node = jsonMapper.valueToTree(source);
        com.fasterxml.jackson.databind.JsonNode proposed = jsonMapper.valueToTree(proposedFields);
        proposed.fieldNames().forEachRemaining(k -> {
            if (node.has(k)) node.set(k, proposed.get(k));
        });
        try {
            return jsonMapper.treeToValue(node, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The proposed " + type.getSimpleName() + " data cannot be read: "
                    + e.getOriginalMessage(), e);
        }
    }
}
