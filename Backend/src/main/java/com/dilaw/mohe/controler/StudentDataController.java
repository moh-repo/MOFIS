package com.dilaw.mohe.controler;

import com.dilaw.mohe.approval.model.ApprovalStatus;

import com.dilaw.mohe.dto.StudentDatumDto;
import com.dilaw.mohe.dto.StudentOptionDto;
import com.dilaw.mohe.dto.StudentParentDto;
import com.dilaw.mohe.dto.StudentRelativeDto;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentCountry;
import com.dilaw.mohe.model.StudentYearResult;
import com.dilaw.mohe.dto.StudentProgressDto;
import com.dilaw.mohe.service.StudentAccessGuard;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentFamilyService;
import com.dilaw.mohe.service.StudentDocumentService;
import com.dilaw.mohe.service.StudentDocumentHistoryService;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.model.StudentDocumentHistory;
import com.dilaw.mohe.util.SafePathUtils;
import com.dilaw.mohe.service.StudentProgressService;
import com.dilaw.mohe.service.StudentYearResultService;
import com.dilaw.mohe.service.StudentProfileService;
import com.dilaw.mohe.service.StudentTimelineService;
import com.dilaw.mohe.service.PendingOnboardingUploadService;
import com.dilaw.mohe.service.StudentFileStorageService;
import com.dilaw.mohe.service.StudentPhotoService;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.ums.service.UmsAuthService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.security.policy.AccessControl;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import com.dilaw.mohe.ums.util.UmsPageableUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import com.dilaw.mohe.util.FileUploadValidator;
import com.dilaw.mohe.util.PhoneNumbers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/studentdata")
@PreAuthorize(MoheSecurityExpressions.STUDENT_PII)
public class StudentDataController {

    @Autowired
    private AccessControl accessControl;

    @Autowired
    private com.dilaw.mohe.security.field.FieldAccessService fieldAccess;

    // Sorting is server-side, so a column is only sortable if it is named here —
    // UmsPageableUtil drops anything else and falls back to studentId. Degree
    // sorts by id like major does, which groups the degrees rather than ordering
    // them alphabetically by name.
    /** Ceiling on one picker page: a dropdown, not a bulk export. */
    private static final int MAX_OPTIONS = 200;

    private static final java.util.Set<String> STUDENT_SORT_FIELDS = java.util.Set.of(
            "studentId", "studentName", "studentPassportNumber", "studentBirthdate",
            "studentCountryId", "studentMajorId", "studentDegreeId", "studentStudyStatusId", "studentAmount");

    private static final Logger log = LoggerFactory.getLogger(StudentDataController.class);

    @Autowired
    StudentDataService studentDataService;

    @Autowired
    com.dilaw.mohe.config.AppTimeZone appTimeZone;

    @Autowired
    StudentDocumentService studentDocumentService;

    @Autowired
    StudentDocumentHistoryService studentDocumentHistoryService;

    @Autowired
    FileEncryptionService fileEncryptionService;

    @Autowired
    StudentProgressService studentProgressService;

    @Autowired
    StudentYearResultService studentYearResultService;

    @Autowired
    ApprovalIntegrationService approvalIntegrationService;

    @Autowired
    UmsAuthService authService;

    @Autowired
    CountryScopeService countryScopeService;

    @Autowired
    PendingOnboardingUploadService pendingOnboardingUploadService;

    @Autowired
    StudentFamilyService studentFamilyService;

    @Autowired
    StudentProfileService studentProfileService;

    @Autowired
    StudentTimelineService studentTimelineService;

    @Autowired
    StudentPhotoService studentPhotoService;

    @Autowired
    StudentAccessGuard studentAccessGuard;

    @Autowired
    StudentFileStorageService studentFileStorage;

    @Autowired
    com.dilaw.mohe.service.StudentDocumentStorageService studentDocumentStorage;

    @Autowired
    com.dilaw.mohe.service.StudentReferenceValidator studentReferenceValidator;

    /** The student's folder inside their country, created on first use. */
    private Path getStudentDocumentPath(Integer studentId, Integer studyCountryIn) {
        try {
            return studentFileStorage.studentDir(studentId, studyCountryIn);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create student document directory.", ex);
        }
    }

    private Long getCurrentUserIdFromRequest(HttpServletRequest request) {
        return SecurityRequestUtils.requireUserId();
    }

    @Autowired
    private org.springframework.context.MessageSource messageSource;

    @Autowired
    private com.dilaw.mohe.approval.service.ApprovalWorkflowService approvalWorkflowService;

    /**
     * Students with a change awaiting a decision, for the "pending" marker on the student
     * list and in the wizard (live QA M6). The approvals endpoint with the same answer is
     * behind APPROVALS, which a student viewer need not hold, so the list showed "Access
     * denied". Ids only, in the caller's scope as there; the Wafar screen's
     * {@code /pending} is the precedent.
     */
    @GetMapping("/pending-ids")
    public List<String> getPendingStudentIds() {
        return approvalWorkflowService.getPendingEntityIds("STUDENT_DATA");
    }

    /**
     * What a failed write or file operation answers (QA STD-003 / SEC-002).
     *
     * <p>These endpoints used to catch everything and answer 400 with
     * {@code e.getMessage()} — the driver's SQL, a file path, a Java class name —
     * as though the user had made a mistake. A runtime exception now goes to
     * {@code GlobalExceptionHandler}, which tells a validation message (400) from a
     * conflict (409), a refusal (403) and a fault (500) and logs the detail. What
     * is left is checked: an unreadable JSON part is a 400, anything else is logged
     * here and answered with the generic message, in the caller's language.
     */
    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> failed(String what, Exception e) {
        if (e instanceof RuntimeException re) {
            throw re;
        }
        java.util.Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale();
        if (e instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            log.warn("{}: unreadable request data: {}", what, e.getMessage());
            String msg = messageSource.getMessage("error.request.body.unreadable", null,
                    "Request body is missing or malformed", locale);
            return (ResponseEntity<T>) (ResponseEntity<?>) ResponseEntity.badRequest().body(Map.of("message", msg));
        }
        log.error("{} failed", what, e);
        String msg = messageSource.getMessage("error.unexpected", null,
                "An unexpected error occurred. Please try again or contact support.", locale);
        return (ResponseEntity<T>) (ResponseEntity<?>) ResponseEntity.internalServerError().body(Map.of("message", msg));
    }

    /**
     * UMS scope is tied to STUDY_COUNTRY_IN (study destination), not nationality.
     *
     * <p>A student with no country on record is ministry-only. Guarding with
     * {@code country != null} made such a record readable by every officer, which
     * is the opposite of what a missing country should mean here.
     */
    private void validateStudyCountryScope(StudentDatum student) {
        if (student == null) return;
        // R-16/R-17 (6 Sep 2026): the rule is StudentDatumPolicy — the study country
        // must be in the caller's scope, and a student with no country is ministry-only.
        accessControl.assertAllowed(student, "VIEW");
    }

    @GetMapping("/")
    public List<StudentDatumDto> getStudentData() {
        return studentDataService.getStudentData().stream().map(StudentDatumDto::fromEntity).toList();
    }

    /**
     * Students as dropdown options — an id and a name, searched by either.
     *
     * <p>For pickers on screens that are not student screens. It answers with
     * {@link StudentOptionDto} rather than the full record, and is gated on
     * {@link MoheSecurityExpressions#STUDENT_PICKER} so the finance roles, which
     * hold no student permission, can name a student without being handed one's
     * passport number. The country scope is the list's own.
     *
     * <p>Bounded: at most {@code MAX_OPTIONS} rows come back, so a caller cannot
     * pull the whole table through it. The client searches as the user types.
     */
    @GetMapping("/options")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_PICKER)
    public List<StudentOptionDto> getStudentOptions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<Integer> ids,
            @RequestParam(defaultValue = "50") int limit) {

        // ids= resolves the names a saved list is already showing; they are almost
        // never among the page the picker holds. Out-of-scope ids simply do not
        // come back, so this cannot be used to confirm a student exists elsewhere.
        int size = Math.max(1, Math.min(ids != null ? ids.size() : limit, MAX_OPTIONS));
        Pageable paging = PageRequest.of(0, size, Sort.by("studentName").ascending());
        return studentDataService.findOptions(search, ids, paging)
                .map(StudentOptionDto::fromEntity)
                .getContent();
    }

    @GetMapping("/studentall")
    public Page<StudentDatumDto> getStudentDataList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) Integer statusId,
            @RequestParam(required = false) Integer filterStudentId,
            @RequestParam(required = false) String filterName,
            @RequestParam(required = false) String filterPassport) {

        // A field outside the allow-list used to fall back silently to studentId
        // (live QA L8, 23 Sep 2026); it is a 400 naming the sortable fields now, as
        // on the archive and approval lists. No field at all still means studentId.
        if (sortField != null && !sortField.isBlank()) {
            com.dilaw.mohe.util.SortWhitelist.order(sortField, sortOrder, STUDENT_SORT_FIELDS);
        }
        Pageable paging = UmsPageableUtil.of(page, size, sortField, sortOrder, STUDENT_SORT_FIELDS, "studentId");

        boolean hasFieldFilters = filterStudentId != null
                || (filterName != null && !filterName.trim().isEmpty())
                || (filterPassport != null && !filterPassport.trim().isEmpty());

        if (hasFieldFilters) {
            return StudentDatumDto.fromPage(studentDataService.findByFieldFilters(
                    filterStudentId,
                    (filterName != null && !filterName.trim().isEmpty()) ? filterName.trim() : null,
                    (filterPassport != null && !filterPassport.trim().isEmpty()) ? filterPassport.trim() : null,
                    statusId,
                    paging));
        }

        if (search != null && !search.trim().isEmpty()) {
            if (statusId != null) {
                return StudentDatumDto.fromPage(studentDataService.searchStudentsWithStatusFilter(search.trim(), statusId, paging));
            } else {
                return StudentDatumDto.fromPage(studentDataService.searchStudents(search.trim(), paging));
            }
        } else {
            if (statusId != null) {
                return StudentDatumDto.fromPage(studentDataService.findStudentDataAllWithStatusFilter(statusId, paging));
            } else {
                return StudentDatumDto.fromPage(studentDataService.findStudentDataAll(paging));
            }
        }
    }

    /**
     * One student for the edit wizard. Answers the detail DTO, not the list one: the
     * wizard used to load the 27-field list summary, show its other fields blank and
     * submit them as cleared (live QA H3, 23 Sep 2026).
     */
    @GetMapping(value = "/{id}")
    @com.dilaw.mohe.audit.AuditedRead(resource = "STUDENT_DETAIL", format = "VIEW", detailView = true)
    public StudentDatumDto getStudentData(@PathVariable Integer id) {
        return com.dilaw.mohe.dto.StudentDatumDetailDto.fromEntity(studentDataService.getStudentData(id));
    }

    @GetMapping(value = "/{id}/progress")
    public StudentProgressDto getStudentProgress(@PathVariable Integer id) {
        return studentProgressService.getProgress(id);
    }

    /** Combined student + finance + extensions + delegation + loans (360° view). */
    @GetMapping(value = "/{id}/profile")
    public ResponseEntity<Map<String, Object>> getStudentProfile(@PathVariable Integer id) {
        StudentDatum existing = studentDataService.getStudentData(id);
        validateStudyCountryScope(existing);
        return ResponseEntity.ok(studentProfileService.buildProfile(id));
    }

    /** Chronological timeline of approvals, year results, extensions, and travel tickets. */
    @GetMapping(value = "/{id}/timeline")
    public ResponseEntity<List<Map<String, Object>>> getStudentTimeline(@PathVariable Integer id) {
        studentAccessGuard.requireStudent(id);
        return ResponseEntity.ok(studentTimelineService.buildTimeline(id));
    }

    @GetMapping(value = "/{id}/year-results")
    public List<StudentYearResult> getStudentYearResults(@PathVariable Integer id) {
        studentAccessGuard.requireStudent(id);
        return studentYearResultService.getByStudentId(id);
    }

    @PutMapping(value = "/{id}/year-results")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public List<StudentYearResult> putStudentYearResults(@PathVariable Integer id, @Valid @RequestBody List<StudentYearResult> body) {
        studentAccessGuard.requireStudent(id);
        return studentYearResultService.replaceForStudent(id, body);
    }

    @PostMapping(value = "/new")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> saveStudentDatum(
            @RequestParam("studentData") String studentDataJson,
            @RequestParam(value = "linkedFinanceDraft", required = false) String linkedFinanceDraftJson,
            @RequestParam(value = "parents", required = false) String parentsJson,
            @RequestParam(value = "relatives", required = false) String relativesJson,
            @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture,
            @RequestParam(value = "documents", required = false) List<MultipartFile> documents,
            @RequestParam(value = "documentTypes", required = false) List<String> documentTypes,
            HttpServletRequest request) {

        try {
            FileUploadValidator.validate(profilePicture);
            if (documents != null) {
                for (MultipartFile doc : documents) {
                    FileUploadValidator.validate(doc);
                }
            }

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            JsonNode jsonNode = objectMapper.readTree(studentDataJson);
            StudentDatum studentDatum = objectMapper.treeToValue(jsonNode, StudentDatum.class);
            // The database assigns the id (DEC-02); a manual id could only be written
            // with IDENTITY_INSERT, which the application login may not use.
            studentDatum.setStudentId(null);
            PhoneNumbers.require("studentCellphone", studentDatum.getStudentCellphone(), true);
            // L9 (D11): a new student has a mobile number.
            PhoneNumbers.requireMobilePresent(studentDatum.getStudentCellphone());
            PhoneNumbers.require("studentHomeNumber", studentDatum.getStudentHomeNumber(), false);
            PhoneNumbers.require("studentEmergencyContact", studentDatum.getStudentEmergencyContact(), false);

            if (jsonNode.has("studentCountryId")) {
                Integer countryId = jsonNode.get("studentCountryId").asInt();
                studentDatum.setStudentCountryId(countryId);
                StudentCountry country = new StudentCountry();
                country.setStudentCountryId(countryId);
                studentDatum.setStudentCountry(country);
            }
            Integer studentCountryId = studentDatum.getStudentCountryId();
            validateStudyCountryScope(studentDatum);
            studentReferenceValidator.requireExistingReferences(studentDatum);

            if (studentDatum.getStudentCurrentLevelId() == null || studentDatum.getStudentCurrentLevelId() == 0) {
                studentDatum.setStudentCurrentLevelId(1);
            }
            if (studentDatum.getStudentAddedQuarter() == null) studentDatum.setStudentAddedQuarter(0);
            if (studentDatum.getStudentLastQuarter() == null) studentDatum.setStudentLastQuarter(0);
            if (studentDatum.getStudyUniversityFeeId() == null) studentDatum.setStudyUniversityFeeId(0);
            if (studentDatum.getStudyUniversityFeeStatusId() == null) studentDatum.setStudyUniversityFeeStatusId(0);

            studentDatum.setStudentAge(computeStudentAge(studentDatum.getStudentBirthdate()));

            Long currentUserId = getCurrentUserIdFromRequest(request);

            StudentFinance linkedFinanceDraft = null;
            if (linkedFinanceDraftJson != null && !linkedFinanceDraftJson.isBlank()) {
                linkedFinanceDraft = objectMapper.readValue(linkedFinanceDraftJson, StudentFinance.class);
            }

            // The wizard's family step. Written when the student is approved (the student
            // has no id before that), so it is checked now, while the user can fix it.
            List<StudentParentDto> parents = parentsJson == null || parentsJson.isBlank() ? List.of()
                    : objectMapper.readValue(parentsJson, new TypeReference<List<StudentParentDto>>() {});
            List<StudentRelativeDto> relatives = relativesJson == null || relativesJson.isBlank() ? List.of()
                    : objectMapper.readValue(relativesJson, new TypeReference<List<StudentRelativeDto>>() {});
            studentFamilyService.validateForOnboarding(parents, relatives);

            String pendingSessionId = pendingOnboardingUploadService.stageFiles(profilePicture, documents, documentTypes);

            approvalIntegrationService.createStudentDataRequest(
                    RequestType.INSERT,
                    studentDatum,
                    null,
                    linkedFinanceDraft,
                    pendingSessionId,
                    parents,
                    relatives,
                    currentUserId);
            // Round-5 QA R5-27: the reply promised "a separate finance approval", which is
            // never raised. The finance row is written, with the form's finance details,
            // when the student is approved (ApprovalIntegrationService, STUDENT_DATA INSERT).
            return ResponseEntity.ok(Map.of(
                    "message", "Request submitted for approval. The student's finance record is created when the request is approved.",
                    "status", ApprovalStatus.PENDING.name()));

        } catch (Exception e) {
            return failed("Student create request", e);
        }
    }

    @PutMapping(value = "/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> updateStudentDatum(
            @PathVariable Integer id,
            @RequestParam("studentData") String studentDataJson,
            @RequestParam(value = "profilePicture", required = false) MultipartFile profilePicture,
            @RequestParam(value = "documents", required = false) List<MultipartFile> documents,
            @RequestParam(value = "documentTypes", required = false) List<String> documentTypes,
            HttpServletRequest request) {

        try {
            FileUploadValidator.validate(profilePicture);
            if (documents != null) {
                for (MultipartFile doc : documents) {
                    FileUploadValidator.validate(doc);
                }
            }

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            objectMapper.configure(
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            JsonNode jsonNode = objectMapper.readTree(studentDataJson);

            StudentDatum existing = studentDataService.getStudentData(id);
            if (existing == null) return ResponseEntity.notFound().build();
            validateStudyCountryScope(existing);

            /*
             * Merge the submitted fields onto the stored record instead of deserializing the
             * payload on its own.
             *
             * The form posts only the fields it edits, so a standalone deserialization leaves
             * every other column null — and since the update applies all columns, approving
             * such a request wiped values the user never touched (email, nationality, …) and
             * listed them as changes for the approver to sign off. Overlaying keeps unsent
             * fields at their current values, so the request describes exactly what changes.
             */
            com.fasterxml.jackson.databind.node.ObjectNode merged =
                    (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(existing);
            // R-18 (D-9 a): a masked field the caller may not EDIT is refused, not ignored.
            // Only fields whose submitted value differs from the stored one count as changed.
            List<String> changedFields = new java.util.ArrayList<>();
            jsonNode.fields().forEachRemaining(field -> {
                if (!java.util.Objects.equals(field.getValue(), merged.get(field.getKey()))) changedFields.add(field.getKey());
            });
            fieldAccess.assertMayEdit(StudentDatum.class, changedFields);
            // Only numbers this request changes are checked; see PhoneNumbers.
            for (String field : changedFields) {
                if (PhoneNumbers.isContactField(field)) {
                    JsonNode value = jsonNode.get(field);
                    PhoneNumbers.require(field, value == null || value.isNull() ? null : value.asText(),
                            PhoneNumbers.isMobileField(field));
                }
            }
            jsonNode.fields().forEachRemaining(field -> merged.set(field.getKey(), field.getValue()));

            StudentDatum studentDatum = objectMapper.treeToValue(merged, StudentDatum.class);
            // L9 (D11): a student saved from the form has a mobile number; one stored
            // without it is completed at this edit.
            PhoneNumbers.requireMobilePresent(studentDatum.getStudentCellphone());

            if (jsonNode.has("studentCountryId")) {
                Integer countryId = jsonNode.get("studentCountryId").asInt();
                studentDatum.setStudentCountryId(countryId);
                StudentCountry country = new StudentCountry();
                country.setStudentCountryId(countryId);
                studentDatum.setStudentCountry(country);
            }
            Integer updateCountryId = studentDatum.getStudentCountryId();
            validateStudyCountryScope(studentDatum);
            studentReferenceValidator.requireExistingReferences(studentDatum);

            if (studentDatum.getStudentCurrentLevelId() == null || studentDatum.getStudentCurrentLevelId() == 0) {
                studentDatum.setStudentCurrentLevelId(1);
            }
            if (studentDatum.getStudentAddedQuarter() == null) studentDatum.setStudentAddedQuarter(0);
            if (studentDatum.getStudentLastQuarter() == null) studentDatum.setStudentLastQuarter(0);
            if (studentDatum.getStudyUniversityFeeId() == null) studentDatum.setStudyUniversityFeeId(0);
            if (studentDatum.getStudyUniversityFeeStatusId() == null) studentDatum.setStudyUniversityFeeStatusId(0);

            studentDatum.setStudentAge(computeStudentAge(studentDatum.getStudentBirthdate()));

            studentDatum.setStudentId(id);
            Long currentUserId = getCurrentUserIdFromRequest(request);

            /*
             * Uploads are staged, not written: a replacement photo and any new documents are
             * part of the edit and must be reviewed with it. They are promoted onto the
             * student when the request is approved, and discarded with it if it is rejected.
             */
            String pendingSessionId = pendingOnboardingUploadService.stageFiles(
                    profilePicture, documents, documentTypes);
            boolean hasStagedUploads = pendingSessionId != null;

            /*
             * Walking the wizard to the end without touching anything must not queue an
             * approval: reviewers would see a request with an empty diff, and it would block
             * the student from further edits until someone acted on it. A new photo or
             * document is itself a change, so it counts here.
             */
            if (!hasStagedUploads && !hasFieldChanges(objectMapper, jsonNode, studentDatum, existing)) {
                log.info("No changes for student {}; skipping approval request", id);
                return ResponseEntity.ok(Map.of(
                        "message", "No changes detected. Nothing was submitted for approval.",
                        "status", "NO_CHANGES",
                        "documentsUploaded", 0));
            }

            approvalIntegrationService.createStudentDataRequest(
                    RequestType.UPDATE,
                    studentDatum,
                    existing,
                    null,
                    pendingSessionId,
                    currentUserId);
            return ResponseEntity.ok(Map.of(
                    "message", hasStagedUploads
                            ? "Update request submitted for approval. Photo and documents will be saved once approved."
                            : "Update request submitted for approval",
                    "status", ApprovalStatus.PENDING.name(),
                    "documentsPending", hasStagedUploads));

        } catch (Exception e) {
            return failed("Student update request", e);
        }
    }

    /**
     * Fields excluded from the change comparison: identity, the relation object (compared via its
     * id column instead), and values the server derives rather than accepts from the client.
     */
    private static final java.util.Set<String> CHANGE_CHECK_IGNORED_FIELDS = java.util.Set.of(
            "studentId", "studentCountry", "studentAge");

    /**
     * True when the payload changes at least one field of the stored record.
     *
     * Only fields actually present in the request body are compared, so a client that omits
     * fields is never reported as clearing them. Comparison runs on both objects serialized
     * through the same mapper, which normalizes the string/number/date representations the
     * client may send.
     */
    private boolean hasFieldChanges(ObjectMapper objectMapper, JsonNode requestBody,
            StudentDatum proposed, StudentDatum existing) {
        JsonNode proposedNode = objectMapper.valueToTree(proposed);
        JsonNode existingNode = objectMapper.valueToTree(existing);

        java.util.Iterator<String> fields = requestBody.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (CHANGE_CHECK_IGNORED_FIELDS.contains(field)) {
                continue;
            }
            JsonNode proposedValue = proposedNode.get(field);
            // Skip unknown names and relations — only scalar columns are compared.
            if (proposedValue == null || proposedValue.isObject() || proposedValue.isArray()) {
                continue;
            }
            if (!valuesEquivalent(proposedValue, existingNode.get(field))) {
                log.debug("Student update change detected on field '{}': '{}' -> '{}'",
                        field, existingNode.get(field), proposedValue);
                return true;
            }
        }
        return false;
    }

    /**
     * Empty-ish values are treated as equal to one another because the client normalizes
     * unset fields to 0 or "" while the column holds NULL — a difference that carries no
     * intent to change. Replacing a real value with 0 still counts as a change.
     */
    private static boolean isEmptyValue(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode()
                || (node.isTextual() && node.asText().isBlank())
                || (node.isNumber() && node.asDouble() == 0d);
    }

    private static boolean valuesEquivalent(JsonNode a, JsonNode b) {
        boolean aEmpty = isEmptyValue(a);
        boolean bEmpty = isEmptyValue(b);
        if (aEmpty || bEmpty) {
            return aEmpty && bEmpty;
        }
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        if (a.isBoolean() || b.isBoolean()) {
            return a.asBoolean() == b.asBoolean();
        }
        return a.asText().equals(b.asText());
    }

    @DeleteMapping(value = "/{id}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_DELETE)
    public ResponseEntity<?> deleteStudentDatum(@PathVariable Integer id, HttpServletRequest request) {
        try {
            StudentDatum existing = studentDataService.getStudentData(id);
            if (existing == null) return ResponseEntity.notFound().build();
            validateStudyCountryScope(existing);
            Long currentUserId = getCurrentUserIdFromRequest(request);
            approvalIntegrationService.createStudentDataRequest(
                    RequestType.DELETE, existing, existing, currentUserId);
            return ResponseEntity.ok(Map.of("message", "Delete request submitted for approval", "status", ApprovalStatus.PENDING.name()));
        } catch (Exception e) {
            return failed("Student delete request", e);
        }
    }

    @GetMapping("/recent")
    public List<StudentDatumDto> getRecentStudents() {
        return studentDataService.getRecentStudents().stream().map(StudentDatumDto::fromEntity).toList();
    }

    @GetMapping("/{id}/documents")
    public List<StudentDocument> getStudentDocuments(@PathVariable Integer id) {
        studentAccessGuard.requireStudent(id);
        return studentDocumentService.getDocumentsByStudentId(id);
    }

    /** Archived document versions for a student: replaced files and removed documents. */
    @GetMapping("/{id}/documents/history")
    public List<StudentDocumentHistory> getStudentDocumentHistory(@PathVariable Integer id) {
        studentAccessGuard.requireStudent(id);
        return studentDocumentHistoryService.getHistoryByStudentId(id);
    }

    /** Uploads a new document for an existing student (encrypted at rest, same layout as onboarding). */
    @PostMapping("/{id}/documents")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> uploadStudentDocument(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "expiryDate", required = false) String expiryDate) {
        /*
         * Outside the try on purpose, as replaceDocument and getStudentPhoto already do:
         * the catch below turns every exception into a 400, so a refusal for a student
         * outside the caller's countries came back as "Error uploading document:
         * Access denied" — a bad request — instead of the 403 it is.
         */
        StudentDatum student = studentDataService.getStudentData(id);
        if (student == null) return ResponseEntity.notFound().build();
        validateStudyCountryScope(student);
        try {
            FileUploadValidator.validate(file);
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("No file provided");
            }

            StudentDocument doc = storeStudentDocument(id, student.getStudyCountryIn(), file, documentName, documentType);
            if (expiryDate != null && !expiryDate.isBlank()) {
                doc.setExpiryDate(LocalDate.parse(expiryDate.substring(0, 10)));
            }
            return ResponseEntity.ok(studentDocumentService.saveDocument(doc));
        } catch (Exception e) {
            return failed("Document upload", e);
        }
    }

    /** Encrypts the upload into the student's folder and persists the STUDENT_DOCUMENTS row. */
    private StudentDocument storeStudentDocument(Integer studentId, Integer studyCountryIn, MultipartFile file,
            String documentName, String documentType) throws IOException {
        return studentDocumentStorage.store(studentId, studyCountryIn, file, documentName, documentType);
    }


    /**
     * Raises a removal request rather than deleting.
     *
     * Deleting outright meant the change never reached the approvals queue: reviewers could
     * not see it coming and could not stop it, while every other change to a student is
     * reviewed. The document stays in place and active until the request is approved.
     */
    @DeleteMapping("/document/{docId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> deleteDocument(@PathVariable Integer docId) {
        try {
            var docOpt = studentDocumentService.getDocumentById(docId);
            if (docOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            StudentDocument doc = docOpt.get();
            StudentDatum student = studentDataService.getStudentData(doc.getStudentId());
            validateStudyCountryScope(student);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            String snapshot = mapper.writeValueAsString(doc);

            approvalIntegrationService.createDocumentDeleteRequest(
                    docId,
                    snapshot,
                    student != null ? student.getStudyCountryIn() : null,
                    SecurityRequestUtils.requireUserId());

            return ResponseEntity.ok(Map.of(
                    "message", "Removal request submitted for approval",
                    "status", ApprovalStatus.PENDING.name()));
        } catch (Exception e) {
            return failed("Document removal request", e);
        }
    }


    @GetMapping("/document/{docId}/history")
    public ResponseEntity<?> getDocumentHistory(@PathVariable Integer docId) {
        studentAccessGuard.requireDocument(docId);
        return ResponseEntity.ok(studentDocumentHistoryService.getHistoryByDocumentId(docId));
    }

    @GetMapping("/document/history/{historyId}/download")
    @com.dilaw.mohe.audit.AuditedRead(resource = "STUDENT_DOCUMENT_HISTORY", format = "DOWNLOAD")
    public ResponseEntity<?> downloadDocumentHistory(@PathVariable Integer historyId) {
        return serveHistoryContent(historyId, true);
    }

    @GetMapping("/document/history/{historyId}/view")
    public ResponseEntity<?> viewDocumentHistory(@PathVariable Integer historyId) {
        return serveHistoryContent(historyId, false);
    }

    private ResponseEntity<?> serveHistoryContent(Integer historyId, boolean attachment) {
        StudentDocumentHistory history = studentAccessGuard.requireDocumentHistory(historyId);
        Path filePath = studentFileStorage.resolveStored(history.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        byte[] fileContent = fileEncryptionService.readDecrypted(filePath);
        HttpHeaders headers = new HttpHeaders();
        String filename = history.getOriginalFilename() != null ? history.getOriginalFilename() : "document";
        if (attachment) {
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
        } else {
            headers.setContentType(resolveMediaType(filename));
        }
        headers.setContentLength(fileContent.length);
        return ResponseEntity.ok().headers(headers).body(fileContent);
    }

    private MediaType resolveMediaType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * The row exists but its file does not (review item S7): uploads are not in git, so a
     * server set up from a database copy, or one where the country-folder migration has
     * not run, has rows pointing at nothing. Logged so the gap can be reconciled.
     */
    private void logMissingDocumentFile(StudentDocument doc, Path filePath) {
        log.warn("Document {} (student {}) has no file on disk: stored path '{}', resolved '{}'",
                doc.getDocumentId(), doc.getStudentId(), doc.getFilePath(), filePath);
    }

    @GetMapping("/document/download/{docId}")
    @com.dilaw.mohe.audit.AuditedRead(resource = "STUDENT_DOCUMENT", format = "DOWNLOAD")
    public ResponseEntity<?> downloadDocument(@PathVariable Integer docId) {
        StudentDocument doc = studentAccessGuard.requireDocument(docId);
        try {
            Path filePath = studentFileStorage.resolveStored(doc.getFilePath());
            if (Files.exists(filePath)) {
                byte[] fileContent = fileEncryptionService.readDecrypted(filePath);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", doc.getOriginalFilename());
                headers.setContentLength(fileContent.length);
                return ResponseEntity.ok().headers(headers).body(fileContent);
            }
            logMissingDocumentFile(doc, filePath);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return failed("Document download", e);
        }
    }

    @GetMapping("/document/view/{docId}")
    public ResponseEntity<?> viewDocument(@PathVariable Integer docId) {
        StudentDocument doc = studentAccessGuard.requireDocument(docId);
        try {
            Path filePath = studentFileStorage.resolveStored(doc.getFilePath());
            if (Files.exists(filePath)) {
                byte[] fileContent = fileEncryptionService.readDecrypted(filePath);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(resolveMediaType(doc.getOriginalFilename()));
                headers.setContentLength(fileContent.length);
                return ResponseEntity.ok().headers(headers).body(fileContent);
            }
            logMissingDocumentFile(doc, filePath);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return failed("Document view", e);
        }
    }

    @PutMapping("/document/{docId}")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> updateDocument(@PathVariable Integer docId, @Valid @RequestBody Map<String, String> updateData) {
        studentAccessGuard.requireDocument(docId);
        /*
         * No catch-all here. It used to echo e.getMessage() as a 400, which reported a
         * malformed expiry date, a constraint violation and a database outage alike as
         * the client's fault, and put the exception text on the wire. Whatever is thrown
         * now goes to GlobalExceptionHandler, which classifies and translates it.
         */
        var document = studentDocumentService.getDocumentById(docId);
        if (document.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StudentDocument doc = document.get();
        if (updateData.containsKey("documentName")) {
            doc.setDocumentName(updateData.get("documentName"));
        }
        if (updateData.containsKey("documentType")) {
            String type = updateData.get("documentType");
            doc.setDocumentType(type != null && !type.isBlank() ? type.trim() : null);
        }
        if (updateData.containsKey("expiryDate")) {
            String expiry = updateData.get("expiryDate");
            try {
                doc.setExpiryDate(expiry != null && !expiry.isBlank()
                        ? LocalDate.parse(expiry.substring(0, 10))
                        : null);
            } catch (RuntimeException e) {
                log.warn("Rejected expiry date '{}' on document {}: {}", expiry, docId, e.toString());
                throw new IllegalArgumentException("expiryDate must be an ISO date (yyyy-MM-dd)");
            }
        }
        return ResponseEntity.ok(studentDocumentService.saveDocument(doc));
    }

    @PostMapping("/document/{docId}/replace")
    @PreAuthorize(MoheSecurityExpressions.STUDENT_WRITE)
    public ResponseEntity<?> replaceDocument(@PathVariable Integer docId,
            @RequestParam("file") MultipartFile file) {
        /*
         * Outside the try on purpose: the catch below turns every exception into a
         * 400, which would have reported a refusal as a bad request.
         */
        StudentDocument doc = studentAccessGuard.requireDocument(docId);
        StudentDatum student = studentDataService.getStudentData(doc.getStudentId());
        try {
            FileUploadValidator.validate(file);
            Integer studyCountryIn = student != null ? student.getStudyCountryIn() : null;

            String historyPath = doc.getFilePath();
            try {
                historyPath = pendingOnboardingUploadService.moveToHistory(
                        doc.getFilePath(), doc.getStudentId(), studyCountryIn);
            } catch (Exception e) {
                // The replacement must proceed either way; the snapshot just
                // keeps pointing at the original path.
                log.warn("Could not move document {} to history: {}", doc.getDocumentId(), e.toString());
            }
            Long changedBy = SecurityRequestUtils.requireUserId();
            studentDocumentHistoryService.recordSnapshot(
                    doc, historyPath, StudentDocumentHistoryService.ACTION_REPLACED, changedBy);

            String datePart = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            String origName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String timestampedName = PendingOnboardingUploadService.buildTimestampedFilename(origName);
            Path destDir = getStudentDocumentPath(doc.getStudentId(), studyCountryIn).resolve(datePart);
            Files.createDirectories(destDir);
            Path destFile = destDir.resolve(timestampedName);
            fileEncryptionService.encryptMultipartFile(file, destFile);
            String relPath = studentFileStorage.relativise(destFile);

            byte[] plaintext = fileEncryptionService.readDecrypted(destFile);
            doc.setFilePath(relPath);
            doc.setOriginalFilename(origName);
            doc.setFileSize((long) plaintext.length);
            doc.setFileHash(fileEncryptionService.sha256(plaintext));
            doc.setIsEncrypted(true);
            doc.setVersionNo((doc.getVersionNo() != null ? doc.getVersionNo() : 1) + 1);

            return ResponseEntity.ok(studentDocumentService.saveDocument(doc));
        } catch (Exception e) {
            return failed("Document replace", e);
        }
    }

    @GetMapping("/{studentId}/photo")
    public ResponseEntity<?> getStudentPhoto(@PathVariable Integer studentId) {
        // Resolved before the try: the catch below reported a refusal as a 400.
        StudentDatum student = studentAccessGuard.requireStudent(studentId);
        try {
            /*
             * 204, not 404: having no photo on record is a normal state, and reporting it as
             * an error made every browser log a failed request for students without one.
             */
            if (student == null || student.getPicturePath() == null || student.getPicturePath().isEmpty()) {
                return ResponseEntity.noContent().build();
            }
            byte[] fileContent = studentPhotoService.getPhotoBytesForPath(student.getPicturePath());
            if (fileContent != null) {
                HttpHeaders headers = new HttpHeaders();
                MediaType mediaType = studentPhotoService.isPng(student.getPicturePath())
                        ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
                headers.setContentType(mediaType);
                headers.setContentLength(fileContent.length);
                return ResponseEntity.ok().headers(headers).body(fileContent);
            }
            // Path recorded but file absent — still "no photo" from the caller's point of view.
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return failed("Student photo", e);
        }
    }

    @GetMapping("/check-duplicate-name")
    public ResponseEntity<List<StudentDatumDto>> checkDuplicateName(@RequestParam("name") String name) {
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(List.of());
        }
        return ResponseEntity.ok(
                studentDataService.checkDuplicateName(name).stream().map(StudentDatumDto::fromEntity).toList());
    }

    private String computeStudentAge(Instant birthdate) {
        if (birthdate == null) return null;
        // Age in the application's time zone, the same one the reports print in.
        LocalDate birth = appTimeZone.toLocalDate(birthdate);
        LocalDate now = appTimeZone.today();
        long totalMonths = ChronoUnit.MONTHS.between(birth, now);
        long years = totalMonths / 12;
        long months = totalMonths % 12;
        return years + "." + months;
    }

    private String getDocumentType(String filename) {
        if (filename == null) return "other";
        String extension = filename.toLowerCase().substring(filename.lastIndexOf(".") + 1);
        switch (extension) {
            case "pdf":  return "pdf";
            case "doc":
            case "docx": return "document";
            case "jpg":
            case "jpeg":
            case "png":  return "image";
            default:     return "other";
        }
    }
}
