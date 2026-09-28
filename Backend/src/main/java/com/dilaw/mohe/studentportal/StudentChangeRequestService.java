package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.ChangeRequestStatus;

import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.model.ChangeRequestField;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.security.field.FieldAccessService;
import com.dilaw.mohe.util.PhoneNumbers;
import com.dilaw.mohe.repository.spec.StudentChangeRequestSpec;
import com.dilaw.mohe.repository.spec.StudentIdCountrySpec;
import com.dilaw.mohe.studentportal.model.StudentChangeRequest;
import com.dilaw.mohe.studentportal.repository.StudentChangeRequestRepository;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.CountryScopeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StudentChangeRequestService {

    /** Fields students are permitted to request changes for. */
    public static final Set<String> ALLOWED_FIELDS = Set.of(
            "studentName",
            "studentGender",
            "studentBirthdate",
            "studentBirthdatePlace",
            "studentPassportNumber",
            "studentPassportIssueDate",
            "studentPassportExpireDate",
            "studentPassportIssueCountryId",
            "studentEmail",
            "studentCellphone",
            "studentHomeNumber",
            "studentEmergencyContact",
            "studyCountryIn",
            "studyUniversityIn",
            "studyCollegeIn",
            "studyMajorIn",
            "studyEducationIn",
            "studentCurrentLevelId"
    );

    @Autowired private StudentChangeRequestRepository requestRepository;
    @Autowired private StudentDataRepository studentDataRepository;
    @Autowired private UmsUserRepository umsUserRepository;
    @Autowired private CountryScopeService countryScopeService;
    @Autowired private AppTimeZone appTimeZone;
    @Autowired private MessageSource messageSource;
    @Autowired private FieldAccessService fieldAccessService;
    @Autowired private com.dilaw.mohe.service.StudentReferenceValidator referenceValidator;
    @Autowired private com.dilaw.mohe.service.StudentAccessGuard studentAccessGuard;
    @Autowired private com.dilaw.mohe.service.StudentDocumentCountryMigrationService documentCountryMigration;
    @Autowired private com.dilaw.mohe.studentportal.repository.StudentSessionRepository sessionRepository;
    @PersistenceContext private EntityManager entityManager;

    public record FieldChangeInput(String fieldName, String fieldLabel, String newValue) {}

    @Transactional
    public List<StudentChangeRequest> submitRequest(Integer studentId, Integer studentUserId, List<FieldChangeInput> changes) {
        if (changes == null || changes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No changes provided");
        }

        for (FieldChangeInput c : changes) {
            if (!ALLOWED_FIELDS.contains(c.fieldName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Field '" + c.fieldName() + "' is not allowed for change requests");
            }
            if (c.newValue() == null || c.newValue().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "New value for '" + c.fieldName() + "' cannot be empty");
            }
        }
        Map<String, ChangeRequestField> fields = fieldCatalogue();
        List<FieldChangeInput> normalised = changes.stream()
                .map(c -> new FieldChangeInput(c.fieldName(), c.fieldLabel(),
                        normalise(c.fieldName(), c.newValue(), fields)))
                .toList();
        for (FieldChangeInput c : normalised) {
            requireValidPhone(c.fieldName(), c.newValue(), fields);
            requireExistingReference(c.fieldName(), c.newValue(), fields);
        }

        StudentDatum student = studentDataRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));

        String submissionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant now = Instant.now();

        return normalised.stream().map(change -> {
            StudentChangeRequest req = new StudentChangeRequest();
            req.setStudentId(studentId);
            req.setStudentUserId(studentUserId);
            req.setSubmissionId(submissionId);
            req.setFieldName(change.fieldName());
            req.setFieldLabel(change.fieldLabel());
            req.setOldValue(getCurrentFieldValue(student, change.fieldName()));
            req.setNewValue(change.newValue());
            req.setStatus(ChangeRequestStatus.PENDING);
            req.setCreatedAt(now);
            req.setIsDeleted(false);
            return requestRepository.save(req);
        }).toList();
    }

    public List<StudentChangeRequest> getMyRequests(Integer studentId) {
        return requestRepository.findByStudentIdAndIsDeletedFalseOrderByCreatedAtDesc(studentId);
    }

    @Transactional
    public void cancelRequest(Integer requestId, Integer studentUserId) {
        StudentChangeRequest req = requestRepository
                .findByRequestIdAndStudentUserIdAndIsDeletedFalse(requestId, studentUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        if (!ChangeRequestStatus.PENDING.equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING requests can be cancelled");
        }
        req.setIsDeleted(true);
        requestRepository.save(req);
    }

    // Admin methods

    /** Returns change requests enriched with student name, study context, and reviewer name. */
    public Page<StudentChangeRequestDto> getPendingRequests(
            String status, Integer studentId, String studentName, Pageable pageable) {

        Set<Integer> nameMatchIds = null;
        if (studentName != null && !studentName.isBlank()) {
            nameMatchIds = studentDataRepository.findCandidatesForFuzzyMatching(studentName)
                    .stream()
                    .map(StudentDatum::getStudentId)
                    .collect(Collectors.toSet());
            if (nameMatchIds.isEmpty()) {
                return org.springframework.data.domain.Page.empty(pageable);
            }
        }

        Specification<StudentChangeRequest> spec = Specification
                .where(StudentChangeRequestSpec.filter(status, studentId, nameMatchIds))
                .and(StudentIdCountrySpec.countryScope(countryScopeService.getScope()));

        Page<StudentChangeRequest> page = requestRepository.findAll(spec, pageable);

        Set<Integer> studentIds = page.getContent().stream()
                .map(StudentChangeRequest::getStudentId)
                .collect(Collectors.toSet());

        Map<Integer, StudentDatum> studentMap = studentDataRepository.findAllById(studentIds)
                .stream()
                .collect(Collectors.toMap(StudentDatum::getStudentId, s -> s));

        Set<Long> reviewerIds = page.getContent().stream()
                .map(StudentChangeRequest::getReviewedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // A HashMap, not Map.of(): a pending row has no reviewer, and Map.of().get(null)
        // throws, so a page of pending requests only failed to load.
        Map<Long, String> reviewerNames = reviewerIds.isEmpty() ? new HashMap<>()
                : umsUserRepository.findAllById(reviewerIds).stream()
                        .collect(Collectors.toMap(
                                UmsUser::getId,
                                u -> {
                                    String name = "";
                                    if (u.getFirstName() != null) name += u.getFirstName() + " ";
                                    if (u.getLastName() != null) name += u.getLastName();
                                    return name.isBlank() ? u.getUserName() : name.trim();
                                }));

        Map<String, ChangeRequestField> fields = fieldCatalogue();
        return page.map(req -> {
            StudentChangeRequestDto dto = StudentChangeRequestDto.from(req, studentMap.get(req.getStudentId()));
            dto.setReviewedByName(req.getReviewedBy() == null ? null : reviewerNames.get(req.getReviewedBy()));
            ChangeRequestField field = fields.get(req.getFieldName());
            dto.setFieldType(field != null ? field.getDataType() : ChangeRequestValues.TEXT);
            maskValues(dto);
            return dto;
        });
    }

    /** The masking rules of the student record, by property name — the names change requests use. */
    private static final Map<String, FieldAccessService.Rule> STUDENT_FIELD_RULES =
            FieldAccessService.rulesOf(StudentDatum.class);

    /**
     * Hides the values of a field the caller may not see on the student record
     * (QA retest N3, decision DEC-05). The page is gated by the broad STUDENT_PII, so
     * a reviewer without the unmasking code read here — passport number, birth date,
     * birthplace — what the student screens mask for them. The rule is the record's
     * own, so the two screens cannot disagree.
     */
    public StudentChangeRequestDto maskValues(StudentChangeRequestDto dto) {
        FieldAccessService.Rule rule = STUDENT_FIELD_RULES.get(dto.getFieldName());
        if (rule != null && !fieldAccessService.mayView(rule, null)) {
            dto.setOldValue(null);
            dto.setNewValue(null);
            dto.setAppliedValue(null);
            dto.setValuesMasked(true);
        }
        return dto;
    }

    /** A decided request as the reviewer may see it: the same masking as the list. */
    public StudentChangeRequestDto toReviewDto(StudentChangeRequest req) {
        return maskValues(StudentChangeRequestDto.from(req, null));
    }

    @Transactional
    public StudentChangeRequest approve(Integer requestId, Long reviewedBy, String employeeComment) {
        StudentChangeRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        validateCountryAccess(req.getStudentId());
        if (!ChangeRequestStatus.PENDING.equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is not in PENDING status");
        }

        // Read again by the field's type, so a request stored before that check
        // ("1-1-2000") is approved as the date it names instead of failing.
        String valueToApply = normalise(req.getFieldName(),
                req.getAppliedValue() != null ? req.getAppliedValue() : req.getNewValue(), fieldCatalogue());
        applyFieldToStudent(req.getStudentId(), req.getFieldName(), valueToApply);

        req.setStatus(ChangeRequestStatus.APPROVED);
        req.setAppliedValue(valueToApply);
        req.setEmployeeComment(employeeComment);
        req.setReviewedBy(reviewedBy);
        req.setReviewedAt(Instant.now());
        req.setAppliedAt(Instant.now());
        return requestRepository.save(req);
    }

    @Transactional
    public StudentChangeRequest reject(Integer requestId, Long reviewedBy, String rejectionReason) {
        StudentChangeRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        validateCountryAccess(req.getStudentId());
        if (!ChangeRequestStatus.PENDING.equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is not in PENDING status");
        }
        // The student is shown the reason; a rejection without one tells them nothing (review R2c).
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg("changeRequest.rejectionReasonRequired"));
        }
        req.setStatus(ChangeRequestStatus.REJECTED);
        req.setRejectionReason(rejectionReason.strip());
        req.setReviewedBy(reviewedBy);
        req.setReviewedAt(Instant.now());
        return requestRepository.save(req);
    }

    @Transactional
    public StudentChangeRequest modifyAndApprove(Integer requestId, Long reviewedBy, String modifiedValue, String employeeComment) {
        StudentChangeRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
        validateCountryAccess(req.getStudentId());
        if (!ChangeRequestStatus.PENDING.equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is not in PENDING status");
        }
        Map<String, ChangeRequestField> catalogue = fieldCatalogue();
        modifiedValue = normalise(req.getFieldName(), modifiedValue, catalogue);
        requireValidPhone(req.getFieldName(), modifiedValue, catalogue);
        requireExistingReference(req.getFieldName(), modifiedValue, catalogue);
        applyFieldToStudent(req.getStudentId(), req.getFieldName(), modifiedValue);

        req.setStatus("MODIFIED");
        req.setAppliedValue(modifiedValue);
        req.setEmployeeComment(employeeComment);
        req.setReviewedBy(reviewedBy);
        req.setReviewedAt(Instant.now());
        req.setAppliedAt(Instant.now());
        return requestRepository.save(req);
    }

    // ── helpers ────────────────────────────────────────────────

    /** CHANGE_REQUEST_FIELD rows by code (the student field name), deleted ones included. */
    public Map<String, ChangeRequestField> fieldCatalogue() {
        Map<String, ChangeRequestField> byCode = new HashMap<>();
        for (ChangeRequestField f : entityManager
                .createQuery("select f from ChangeRequestField f", ChangeRequestField.class)
                .getResultList()) {
            byCode.put(f.getCode(), f);
        }
        return byCode;
    }

    /** DATA_TYPE by field code, for the active fields a student may change. */
    public Map<String, String> fieldTypes() {
        Map<String, String> types = new HashMap<>();
        fieldCatalogue().forEach((code, f) -> {
            if (ALLOWED_FIELDS.contains(code) && !Boolean.TRUE.equals(f.getIsDeleted())) {
                types.put(code, f.getDataType() == null ? ChangeRequestValues.TEXT : f.getDataType());
            }
        });
        return types;
    }

    /**
     * {@code value} in the form the request keeps it, read by the student field's
     * Java type (review R2b). A value that field cannot hold is a 400 naming the
     * field in the reader's language.
     */
    private String normalise(String fieldName, String value, Map<String, ChangeRequestField> fields) {
        ChangeRequestField field = fields.get(fieldName);
        try {
            return ChangeRequestValues.normalize(fieldType(fieldName),
                    field != null ? field.getDataType() : null, value, appTimeZone.zone());
        } catch (ChangeRequestValues.InvalidValue e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    msg("changeRequest.invalid." + e.kind(), label(fieldName, field), value));
        }
    }

    private static Class<?> fieldType(String fieldName) {
        try {
            return StudentDatum.class.getDeclaredField(fieldName).getType();
        } catch (NoSuchFieldException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown field '" + fieldName + "'");
        }
    }

    private static String label(String fieldName, ChangeRequestField field) {
        if (field == null) return fieldName;
        boolean english = "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
        String name = english ? field.getNameEn() : field.getNameAr();
        return name == null || name.isBlank() ? fieldName : name;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    /**
     * Contact numbers follow the same rule as the staff forms; see PhoneNumbers. The
     * refusal names the field as the student reads it, in their language: it used to
     * pass PhoneNumbers' English sentence through, which starts with the Java field
     * name ("studentPhone: a mobile number must have…", live QA #26).
     */
    private void requireValidPhone(String fieldName, String value, Map<String, ChangeRequestField> fields) {
        if (!PhoneNumbers.isContactField(fieldName)) return;
        boolean mobile = PhoneNumbers.isMobileField(fieldName);
        if (!PhoneNumbers.isValid(value, mobile)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    msg("changeRequest.invalid.PHONE", label(fieldName, fields.get(fieldName)), value,
                            mobile ? PhoneNumbers.MOBILE_MIN_DIGITS : PhoneNumbers.PHONE_MIN_DIGITS,
                            PhoneNumbers.PHONE_MAX_DIGITS));
        }
    }

    /**
     * A lookup-backed value names a row that exists and is in use (live QA M18): it used
     * to be caught only at approval, by the foreign key.
     */
    private void requireExistingReference(String fieldName, String value, Map<String, ChangeRequestField> fields) {
        com.dilaw.mohe.service.StudentReferenceValidator.Reference found = referenceValidator.check(fieldName, value);
        if (found == com.dilaw.mohe.service.StudentReferenceValidator.Reference.MISSING
                || found == com.dilaw.mohe.service.StudentReferenceValidator.Reference.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    msg("changeRequest.invalid.REFERENCE", label(fieldName, fields.get(fieldName)), value));
        }
    }

    /**
     * The reviewer may reach the request's student (live QA N14, 26 Sep 2026): 404 for a
     * student hidden by row-level security, 403 out of scope. The country used to be read
     * with {@code .orElse(null)} and a null let through; approve and modify then failed
     * later by accident, but a reject went through for another country's student.
     */
    private void validateCountryAccess(Integer studentId) {
        studentAccessGuard.requireStudent(studentId);
    }

    private String getCurrentFieldValue(StudentDatum student, String fieldName) {
        try {
            Field f = StudentDatum.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return ChangeRequestValues.display(f.get(student), appTimeZone.zone());
        } catch (Exception e) {
            return null;
        }
    }

    private void applyFieldToStudent(Integer studentId, String fieldName, String value) {
        StudentDatum student = studentDataRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        Integer previousCountry = student.getStudyCountryIn();
        Field f;
        try {
            f = StudentDatum.class.getDeclaredField(fieldName);
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // The field name comes from the stored request, so an unknown one is
            // bad data rather than a server fault.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown field '" + fieldName + "'");
        }
        try {
            f.set(student, ChangeRequestValues.toFieldValue(f.getType(), value, appTimeZone.zone()));
        } catch (ChangeRequestValues.InvalidValue e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    msg("changeRequest.invalid.value", label(fieldName, fieldCatalogue().get(fieldName)), value));
        } catch (IllegalAccessException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not apply field '" + fieldName + "': " + e.getMessage());
        }

        try {
            // Flush here so a rejected value surfaces as a 400 from this call
            // rather than as an unhandled commit failure after the response.
            studentDataRepository.saveAndFlush(student);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Value '" + value + "' for field '" + fieldName
                            + "' is not an accepted value for this student", e);
        }
        followCountryChange(studentId, previousCountry, student.getStudyCountryIn());
    }

    /**
     * PRT-03: an approved {@code studyCountryIn} change does what the staff edit does
     * ({@code StudentDataService}): the student's files move to the new country's folder
     * and their rows are repointed. It also ends the student's portal sessions, which
     * carry the old country pinned at sign-in (V036) and would otherwise keep reading
     * under it until they expired.
     */
    void followCountryChange(Integer studentId, Integer previousCountry, Integer newCountry) {
        if (Objects.equals(previousCountry, newCountry)) return;
        documentCountryMigration.relocateForCountryChange(studentId, previousCountry, newCountry);
        sessionRepository.deleteByStudentId(studentId);
    }
}
