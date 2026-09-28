package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.service.codelist.CodeListService;

import com.dilaw.mohe.codes.InquiryType;

import com.dilaw.mohe.codes.InquiryStatus;

import com.dilaw.mohe.codes.InfoReviewStatus;

import com.dilaw.mohe.codes.InfoRequestStatus;
import com.dilaw.mohe.model.StudyFinReceivedStatus;
import com.dilaw.mohe.repository.StudyFinReceivedStatusRepository;

import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentInquiry;
import com.dilaw.mohe.model.StudentInfoRequest;
import com.dilaw.mohe.model.StudentYearResult;
import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.dto.StudentProgressDto;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentFileStorageService;
import com.dilaw.mohe.service.StudentDocumentService;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.util.FileUploadValidator;
import com.dilaw.mohe.service.StudentFinanceService;
import com.dilaw.mohe.service.StudentProgressService;
import com.dilaw.mohe.repository.StudentInquiryRepository;
import com.dilaw.mohe.repository.StudentInfoRequestRepository;
import com.dilaw.mohe.repository.StudentExtensionRepository;
import com.dilaw.mohe.repository.StudentTravelTicketRepository;
import com.dilaw.mohe.service.StudentInfoRequestService;
import com.dilaw.mohe.service.StudentYearResultService;
import com.dilaw.mohe.service.StudentProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.dilaw.mohe.service.StudentExtensionLocalizationService;

/**
 * The signed-in student's own data.
 *
 * <p>Every message a student can see comes from the {@code portal.*} bundle keys in
 * the request's locale; they were English literals on an Arabic portal. The two
 * upload handlers no longer catch everything and echo {@code e.getMessage()} as a
 * 400 — a storage fault now reaches {@code GlobalExceptionHandler}, which logs it
 * and classifies it, instead of being reported as the student's mistake with the
 * exception text on the wire.
 */
@RestController
@RequestMapping("api/v1/student-portal/me")
public class StudentPortalMeController {

    @Autowired
    private CodeListService codeLists;

    private static final Logger log = LoggerFactory.getLogger(StudentPortalMeController.class);

    @Autowired
    private StudentFileStorageService studentFileStorage;

    @Autowired
    private StudentUserAuthService studentUserAuthService;

    @Autowired
    private StudentDataService studentDataService;
    @Autowired
    private StudentFinanceService studentFinanceService;
    @Autowired
    private StudyFinReceivedStatusRepository finReceivedStatusRepository;

    @Autowired
    private com.dilaw.mohe.repository.StudyCurrencyInRepository currencyRepository;

    @Autowired
    private com.dilaw.mohe.repository.StudyPayFeeInStatusRepository feeStatusRepository;

    @Autowired
    private com.dilaw.mohe.repository.StudentMajorRepository majorRepository;
    @Autowired
    private StudentDocumentService studentDocumentService;
    @Autowired
    private FileEncryptionService fileEncryptionService;
    @Autowired
    private StudentInquiryRepository inquiryRepository;
    @Autowired
    private StudentInfoRequestRepository studentInfoRequestRepository;
    @Autowired
    private StudentInfoRequestService studentInfoRequestService;
    @Autowired
    private StudentProgressService studentProgressService;
    @Autowired
    private StudentYearResultService studentYearResultService;

    @Autowired
    private StudentExtensionRepository studentExtensionRepository;

    @Autowired
    private StudentExtensionLocalizationService extensionLocalization;

    @Autowired
    private com.dilaw.mohe.repository.DocumentTypeRepository documentTypeRepository;

    @Autowired
    private StudentTravelTicketRepository studentTravelTicketRepository;

    @Autowired
    private StudentProfileService studentProfileService;

    @Autowired
    private MessageSource messageSource;

    /**
     * Documents a student may hold before the portal refuses another upload
     * ({@code mohe.portal.upload.max-documents}, default 20). There was no ceiling:
     * a student account could fill the encrypted store indefinitely, one 10 MB
     * file at a time.
     */
    @Value("${mohe.portal.upload.max-documents:20}")
    private int maxDocuments;

    /**
     * The type a student files a document under must be one of {@code /document-types}
     * (live QA L12, P9: any text was stored, markup included). None given is GENERAL,
     * as before; the stored value is the list's own name.
     *
     * @throws com.dilaw.mohe.exception.LocalizedArgumentException (400) for any other text
     */
    private String documentTypeOrRefuse(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return "GENERAL";
        }
        String wanted = documentType.strip();
        return documentTypeRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .filter(t -> wanted.equalsIgnoreCase(strip(t.getDocumentTypeName()))
                        || wanted.equalsIgnoreCase(strip(t.getDocumentTypeNameEn())))
                .map(t -> t.getDocumentTypeName())
                .findFirst()
                .orElseThrow(() -> new com.dilaw.mohe.exception.LocalizedArgumentException(
                        "Choose a document type from the list.", "portal.upload.badType"));
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }

    /** The largest page a portal list returns. */
    static final int MAX_PORTAL_PAGE = 100;

    /**
     * A page request the portal can always build (live QA L13, P30): a size of 0 or a
     * negative page made Spring refuse with its own English sentence. Out-of-range values
     * are brought into range instead: page from 0, size from 1 to {@link #MAX_PORTAL_PAGE}.
     */
    static Pageable portalPage(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(MAX_PORTAL_PAGE, Math.max(1, size)));
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /**
     * The authenticated student, from the session token.
     *
     * <p>Every endpoint here keys its query off this id, never off a country
     * scope — which is why a portal student can only ever read their own rows.
     *
     * <p><b>Use the *ForPortal service methods only.</b> The ministry equivalents
     * additionally ask {@code CountryScopeService} whether the caller may see the
     * student's country and check {@code recordVisibility} against an admin flag,
     * both of which read the UMS SecurityContext. A portal student has no UMS
     * user, so those calls returned 403 for a correctly authenticated student —
     * except when a staff member happened to be logged in to the same browser, at
     * which point the student's access to their own record was decided by that
     * staff account's country scope.
     */
    private static int getStudentId(HttpServletRequest request) {
        Object attr = request.getAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_ID);
        if (attr instanceof Integer) return (Integer) attr;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No active student session");
    }

    private static int requireStudentUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_USER_ID);
        if (attr instanceof Integer id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again");
    }

    // ── S-11: account security (e-mail verification status, optional second factor) ──

    @org.springframework.beans.factory.annotation.Autowired
    private StudentPortalMfaService portalMfa;

    @GetMapping("/security")
    public StudentPortalMfaService.Status security(HttpServletRequest request) {
        return portalMfa.status(requireStudentUserId(request));
    }

    @PostMapping("/security/totp/start")
    public Map<String, String> totpStart(HttpServletRequest request) {
        return portalMfa.start(requireStudentUserId(request));
    }

    @PostMapping("/security/totp/confirm")
    public Map<String, Object> totpConfirm(@RequestBody Map<String, String> body, HttpServletRequest request) {
        portalMfa.confirm(requireStudentUserId(request), body.get("code"));
        return Map.of("totpEnabled", true);
    }

    @PostMapping("/security/totp/disable")
    public Map<String, Object> totpDisable(@RequestBody Map<String, String> body, HttpServletRequest request) {
        portalMfa.disable(requireStudentUserId(request), body.get("code"));
        return Map.of("totpEnabled", false);
    }

    /** The student's country, which decides the folder their upload lands in. */
    private Integer studyCountryIn(int studentId) {
        var student = studentDataService.getStudentDataForPortal(studentId);
        return student != null ? student.getStudyCountryIn() : null;
    }

    /** Refuses the upload once the student holds {@link #maxDocuments} active documents. */
    private void requireDocumentCapacity(int studentId) {
        int held = studentDocumentService.getDocumentsByStudentId(studentId).size();
        if (held >= maxDocuments) {
            log.warn("Student {} refused an upload: {} documents on file, limit {}", studentId, held, maxDocuments);
            throw new IllegalArgumentException(msg("portal.upload.limitReached", maxDocuments));
        }
    }

    /**
     * Change the signed-in student's own password.
     *
     * <p>Lived at {@code /auth/change-password} until August 2026 and never worked:
     * it read the student-user id the token interceptor sets, but the interceptor
     * is deliberately excluded from {@code /auth/**} (those endpoints are for
     * callers with no token yet), so the attribute was always null and the reply
     * always 401. Under {@code /me} the interceptor runs. Legacy tokens resolve a
     * student id but no student-user id; they cannot change a password and are
     * told so.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Object attr = request.getAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_USER_ID);
        if (!(attr instanceof Integer studentUserId)) {
            return ResponseEntity.status(401).body(Map.of("error", msg("portal.password.signInAgain")));
        }
        try {
            studentUserAuthService.changePassword(studentUserId, body.get("currentPassword"), body.get("newPassword"),
                    request.getHeader(StudentPortalTokenInterceptor.HEADER_TOKEN));
            return ResponseEntity.ok(Map.of("message", msg("portal.password.changed")));
        } catch (ResponseStatusException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason() != null ? ex.getReason() : msg("portal.password.changeFailed")));
        }
    }

    @GetMapping("")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        int studentId = getStudentId(request);
        StudentDatum student = studentDataService.getStudentDataForPortal(studentId);
        Map<String, Object> profile = new HashMap<>();
        profile.put("studentId", student.getStudentId());
        profile.put("studentName", student.getStudentName());
        return ResponseEntity.ok(profile);
    }

    /**
     * The "my data" page: the student's record and finance row, cut down to the
     * fields the page binds — see {@link PortalMyDataDto} for what is left out.
     */
    @GetMapping("/data")
    public ResponseEntity<PortalMyDataDto> getMyData(HttpServletRequest request) {
        int studentId = getStudentId(request);
        StudentDatum student = studentDataService.getStudentDataForPortal(studentId);
        StudentFinance finance = null;
        try {
            finance = studentFinanceService.getByIdForPortal(studentId);
        } catch (ResponseStatusException notFound) {
            // No finance row yet is a normal state for a new student.
        }
        PortalMyDataDto dto = PortalMyDataDto.of(student, finance);
        if (dto.getFinance() != null) {
            describeFinanceStatus(dto.getFinance());
        }
        if (dto.getStudent() != null) {
            nameStudentCodes(dto.getStudent());
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * The major, the currency and the fee status by name, in both languages (QA SP-004).
     *
     * <p>The major is the one lookup the page did not resolve: STUDENT_DATA keeps the
     * major's Arabic name as free text beside its id, the page printed that text, and
     * so English readers saw "طب بشري" though STUDENT_MAJOR names every row in English
     * (live QA M7). The lookup's names win when the id resolves; the record's own
     * text stays for an id that does not.
     */
    private void nameStudentCodes(PortalMyDataDto.Student s) {
        Integer majorId = s.getStudentMajorId();
        if (majorId != null && majorId > 0) {
            majorRepository.findById(majorId).ifPresent(m -> {
                if (org.springframework.util.StringUtils.hasText(m.getStudentMajorName())) {
                    s.setStudentMajorName(m.getStudentMajorName().trim());
                }
                s.setStudentMajorNameEn(m.getStudentMajorNameEn());
            });
        }
        Integer currencyId = s.getAmountCurrency();
        if (currencyId != null && currencyId > 0) {
            currencyRepository.findById(currencyId).ifPresent(c -> {
                s.setAmountCurrencyName(c.getStudyCurrencyInName());
                s.setAmountCurrencyNameEn(c.getStudyCurrencyInNameEn());
            });
        }
        Integer feeStatusId = s.getStudyUniversityFeeStatusId();
        if (feeStatusId != null && feeStatusId > 0) {
            feeStatusRepository.findById(feeStatusId).ifPresent(st -> {
                s.setStudyUniversityFeeStatusName(st.getStudyPayFeeInStatusName());
                s.setStudyUniversityFeeStatusNameEn(st.getStudyPayFeeInStatusNameEn());
            });
        }
    }

    /** Names the finance status for the page and says whether the money is received. */
    private void describeFinanceStatus(PortalMyDataDto.Finance f) {
        Integer id = f.getStudyFinRecievedStatus();
        StudyFinReceivedStatus status = id == null ? null : finReceivedStatusRepository.findById(id).orElse(null);
        f.describeStatus(status);
    }

    @GetMapping("/progress")
    public ResponseEntity<StudentProgressDto> getMyProgress(HttpServletRequest request) {
        int studentId = getStudentId(request);
        return ResponseEntity.ok(studentProgressService.getProgressForPortal(studentId));
    }

    @GetMapping("/year-results")
    public ResponseEntity<List<StudentYearResult>> getMyYearResults(HttpServletRequest request) {
        int studentId = getStudentId(request);
        return ResponseEntity.ok(studentYearResultService.getByStudentId(studentId));
    }

    /**
     * The document types a student may upload under, both names. The portal's
     * upload "Type" was a free-text box pre-filled GENERAL, and the list printed
     * whatever was stored — codes, Arabic names, file extensions (live QA #15).
     * The staff list sits behind a staff permission, so the portal gets its own.
     */
    @GetMapping("/document-types")
    public List<java.util.Map<String, Object>> getDocumentTypes() {
        return documentTypeRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
                .map(t -> {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", t.getDocumentTypeId());
                    row.put("name", t.getDocumentTypeName());
                    row.put("nameEn", t.getDocumentTypeNameEn());
                    return row;
                })
                .toList();
    }

    @GetMapping("/extensions")
    public List<PortalExtensionView> getMyExtensions(HttpServletRequest request) {
        int studentId = getStudentId(request);
        StudentExtensionLocalizationService.Localizer names =
                extensionLocalization.localizer(LocaleContextHolder.getLocale().getLanguage());
        return studentExtensionRepository.findByFilters(studentId, null, null, null, null).stream()
                .map(e -> PortalExtensionView.of(e, names))
                .toList();
    }

    @GetMapping("/travel-tickets")
    public List<StudentTravelTicket> getMyTravelTickets(HttpServletRequest request) {
        int studentId = getStudentId(request);
        return studentTravelTicketRepository.findByStudentIdOrderByTravelDateDesc(studentId);
    }

    /**
     * The PDF of one of the student's own tickets (review T3), by ticket: the ticket
     * must be theirs and must name a document, which must be theirs too.
     */
    @com.dilaw.mohe.audit.AuditedRead(resource = "PORTAL_TRAVEL_TICKET_DOCUMENT", format = "VIEW")
    @GetMapping("/travel-tickets/{ticketId}/document")
    public ResponseEntity<?> getMyTravelTicketDocument(@PathVariable Integer ticketId, HttpServletRequest request) {
        int studentId = getStudentId(request);
        StudentTravelTicket ticket = studentTravelTicketRepository.findById(ticketId).orElse(null);
        if (ticket == null || !Integer.valueOf(studentId).equals(ticket.getStudentId()) || ticket.getDocumentId() == null) {
            return ResponseEntity.notFound().build();
        }
        StudentDocument doc = studentDocumentService.getDocumentById(ticket.getDocumentId()).orElse(null);
        if (doc == null || !Integer.valueOf(studentId).equals(doc.getStudentId())) {
            return ResponseEntity.notFound().build();
        }
        Path filePath = studentFileStorage.resolveStored(doc.getFilePath());
        if (!Files.exists(filePath)) return ResponseEntity.notFound().build();
        try {
            byte[] content = fileEncryptionService.readDecrypted(filePath);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentLength(content.length);
            return ResponseEntity.ok().headers(headers).body(content);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/profile-summary")
    public ResponseEntity<Map<String, Object>> getProfileSummary(HttpServletRequest request) {
        int studentId = getStudentId(request);
        return ResponseEntity.ok(studentProfileService.buildProfileForPortal(studentId));
    }

    /** The student's documents, without the storage path and hash of the entity (L12). */
    @GetMapping("/documents")
    public List<PortalDocumentView> getMyDocuments(HttpServletRequest request) {
        int studentId = getStudentId(request);
        return studentDocumentService.getDocumentsByStudentId(studentId).stream()
                .map(PortalDocumentView::of)
                .toList();
    }

    // S-11 (c): a portal download is a read of personal data like any other (O-33).
    @com.dilaw.mohe.audit.AuditedRead(resource = "PORTAL_DOCUMENT", format = "DOWNLOAD")
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<?> downloadDocument(@PathVariable Integer id, HttpServletRequest request) {
        int studentId = getStudentId(request);
        Optional<StudentDocument> docOpt = studentDocumentService.getDocumentById(id);
        if (docOpt.isEmpty()) return ResponseEntity.notFound().build();
        StudentDocument doc = docOpt.get();
        // Another student's document is "not found", like a missing one (L12): a 403
        // told the caller that the id exists.
        if (!doc.getStudentId().equals(studentId)) return ResponseEntity.notFound().build();
        Path filePath = studentFileStorage.resolveStored(doc.getFilePath());
        if (!Files.exists(filePath)) return ResponseEntity.notFound().build();
        try {
            byte[] content = fileEncryptionService.readDecrypted(filePath);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            // The name the file was uploaded under, UTF-8 encoded so an Arabic name
            // survives (L12, P15: the reply attachment was saved with no name).
            headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                    .filename(doc.getOriginalFilename() != null ? doc.getOriginalFilename() : "document",
                            java.nio.charset.StandardCharsets.UTF_8)
                    .build());
            headers.setContentLength(content.length);
            return ResponseEntity.ok().headers(headers).body(content);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Uploads a document into the student's own folder. Validation and the document
     * ceiling raise {@code IllegalArgumentException} (400 with the message); anything
     * else — a storage fault above all — goes to the global handler as the 500 it is.
     */
    @PostMapping("/documents")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentName", required = false) String documentName,
            @RequestParam(value = "documentType", required = false) String documentType,
            HttpServletRequest request) throws IOException {
        int studentId = getStudentId(request);
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", msg("portal.upload.noFile")));
        FileUploadValidator.validate(file);
        FileUploadValidator.requireMatchingExtension(file);
        String type = documentTypeOrRefuse(documentType);
        requireDocumentCapacity(studentId);

        Path studentDir = studentFileStorage.studentDir(studentId, studyCountryIn(studentId));
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String safeName = Instant.now().toEpochMilli() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path targetPath = studentDir.resolve(safeName);
        fileEncryptionService.encryptMultipartFile(file, targetPath);
        String relativePath = studentFileStorage.relativise(targetPath);
        byte[] plaintext = fileEncryptionService.readDecrypted(targetPath);
        StudentDocument doc = new StudentDocument(
                studentId,
                documentName != null && !documentName.isBlank() ? documentName : originalFilename,
                type,
                relativePath,
                originalFilename,
                (long) plaintext.length
        );
        doc.setFileHash(fileEncryptionService.sha256(plaintext));
        doc.setIsEncrypted(true);
        doc.setVersionNo(1);
        studentDocumentService.saveDocument(doc);
        return ResponseEntity.ok(Map.of("id", doc.getDocumentId(), "message", msg("portal.upload.uploaded")));
    }

    @GetMapping("/inquiries")
    public Page<StudentInquiry> getMyInquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        int studentId = getStudentId(request);
        Pageable pageable = portalPage(page, size);
        return inquiryRepository.findByStudentId(studentId, pageable);
    }

    @GetMapping("/inquiries/{id}")
    public ResponseEntity<?> getInquiry(@PathVariable Integer id, HttpServletRequest request) {
        int studentId = getStudentId(request);
        return inquiryRepository.findByIdAndStudentId(id, studentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Body of {@code POST /inquiries}. A typed request rather than a {@code Map}: {@code @Valid}
     * on a raw map validates nothing, so the title check lived in the handler as an
     * English string. JSON names are unchanged.
     */
    public record InquiryBody(String type, @NotBlank String title, String description) {}

    @PostMapping("/inquiries")
    public ResponseEntity<?> createInquiry(@Valid @RequestBody InquiryBody body, HttpServletRequest request) {
        int studentId = getStudentId(request);
        StudentInquiry inquiry = new StudentInquiry();
        inquiry.setStudentId(studentId);
        // The type must be an active row of LU_INQUIRY_TYPE (V073); GENERAL when none is sent.
        inquiry.setType(body.type() != null && !body.type().isBlank()
                ? codeLists.require("inquiry-type", body.type()).code()
                : InquiryType.GENERAL);
        inquiry.setTitle(body.title().trim());
        inquiry.setDescription(body.description() != null ? body.description() : "");
        inquiry.setStatus(InquiryStatus.PENDING);
        inquiry.setCreatedAt(Instant.now());
        inquiry.setUpdatedAt(Instant.now());
        inquiryRepository.save(inquiry);
        return ResponseEntity.ok(inquiry);
    }

    @GetMapping("/info-requests")
    public Page<StudentInfoRequest> getMyInfoRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requestType,
            HttpServletRequest request) {
        int studentId = getStudentId(request);
        Pageable pageable = portalPage(page, size);
        return studentInfoRequestRepository.findByStudentIdAndFilters(studentId, status, requestType, pageable);
    }

    @GetMapping("/info-requests/{id}")
    public ResponseEntity<?> getInfoRequest(@PathVariable Integer id, HttpServletRequest request) {
        int studentId = getStudentId(request);
        return studentInfoRequestRepository.findByIdAndStudentId(id, studentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/info-requests/{id}/respond")
    public ResponseEntity<?> respondToInfoRequest(
            @PathVariable Integer id,
            @RequestParam(value = "responseText", required = false) String responseText,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "proposedStudentData", required = false) String proposedStudentDataJson,
            @RequestParam(value = "proposedFinanceData", required = false) String proposedFinanceDataJson,
            HttpServletRequest request) throws IOException {
        int studentId = getStudentId(request);
        Optional<StudentInfoRequest> opt = studentInfoRequestRepository.findByIdAndStudentId(id, studentId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        StudentInfoRequest req = opt.get();
        // Live QA M15: a cancelled request was answered, and so reopened.
        Optional<InfoRequestRespondRule.Refusal> refusal =
                InfoRequestRespondRule.refusal(req.getStatus(), req.getReviewStatus());
        if (refusal.isPresent()) {
            return ResponseEntity.status(refusal.get().status())
                    .body(Map.of("error", msg(refusal.get().messageKey())));
        }
        boolean hasFile = file != null && !file.isEmpty();
        // Live QA L14: an empty answer read, to the reviewer, as though the student had replied.
        if (!InfoRequestRespondRule.hasContent(responseText, hasFile)) {
            return ResponseEntity.badRequest().body(Map.of("error", msg("portal.infoRequest.emptyResponse")));
        }
        if (hasFile) {
            // Before anything is changed on the request row, so a refused upload leaves it as it was.
            FileUploadValidator.validate(file);
            FileUploadValidator.requireMatchingExtension(file);
            requireDocumentCapacity(studentId);
        }
        req.setResponseText(responseText != null ? responseText : "");
        req.setResponseAt(Instant.now());
        req.setStatus(InfoRequestStatus.RESPONDED);
        req.setReviewStatus(InfoReviewStatus.PENDING_REVIEW);
        req.setRejectionReason(null);
        if (hasFile) {
            Path studentDir = studentFileStorage.studentDir(studentId, studyCountryIn(studentId));
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String safeName = "info_req_" + id + "_" + Instant.now().toEpochMilli() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path targetPath = studentDir.resolve(safeName);
            fileEncryptionService.encryptMultipartFile(file, targetPath);
            String relativePath = studentFileStorage.relativise(targetPath);
            byte[] plaintext = fileEncryptionService.readDecrypted(targetPath);
            StudentDocument doc = new StudentDocument(
                    studentId,
                    "Info request response: " + req.getTitle(),
                    "INFO_REQUEST_RESPONSE",
                    relativePath,
                    originalFilename,
                    (long) plaintext.length
            );
            doc.setFileHash(fileEncryptionService.sha256(plaintext));
            doc.setIsEncrypted(true);
            doc.setVersionNo(1);
            studentDocumentService.saveDocument(doc);
            req.setResponseDocumentId(doc.getDocumentId());
        }
        studentInfoRequestRepository.save(req);
        // Save proposed data to staging for admin review (workflow)
        if ((proposedStudentDataJson != null && !proposedStudentDataJson.isBlank())
                || (proposedFinanceDataJson != null && !proposedFinanceDataJson.isBlank())) {
            studentInfoRequestService.saveStaging(id,
                    proposedStudentDataJson != null ? proposedStudentDataJson : null,
                    proposedFinanceDataJson != null ? proposedFinanceDataJson : null);
        }
        return ResponseEntity.ok(req);
    }
}
