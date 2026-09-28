package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.ReportLabelCode;
import com.dilaw.mohe.dto.StudentFinancialLineDto;
import com.dilaw.mohe.dto.StudentProfileDocDto;
import com.dilaw.mohe.model.StudentBooksAllowance;
import com.dilaw.mohe.model.StudentFinancialStatementRow;
import com.dilaw.mohe.model.StudentLoan;
import com.dilaw.mohe.model.StudentProfileDoc;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.StudentBooksAllowanceRepository;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinancialStatementRowRepository;
import com.dilaw.mohe.repository.StudentLoanRepository;
import com.dilaw.mohe.repository.StudentProfileDocRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeRepository;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.systemsettings.dto.SysSettingsDto;
import com.dilaw.mohe.systemsettings.service.SysSettingsService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Data provider for the four official single-student Jasper documents
 * (ID card, registration form, enrollment certificate, financial statement).
 * Reads the V016 views and resolves each bilingual name pair to the requested
 * language (EN falls back to AR at the view level already).
 */
@Service
@Transactional(readOnly = true)
public class StudentDocumentReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${mohe.report.timezone:Asia/Riyadh}")
    private String reportTimezone;

    @Autowired
    private StudentProfileDocRepository profileDocRepository;

    @Autowired
    private StudentFinancialStatementRowRepository statementRowRepository;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private StudyUniversityFeeRepository universityFeeRepository;

    @Autowired
    private StudentBooksAllowanceRepository booksAllowanceRepository;

    @Autowired
    private StudentLoanRepository loanRepository;

    @Autowired
    private CodeListService codeLists;

    @Autowired
    private SysSettingsService sysSettingsService;

    @Autowired
    private StudentAccessGuard studentAccessGuard;

    /** What a placeholder prints when the row carries no value. */
    static final String MISSING = "—";

    // ── Profile document ──────────────────────────────────────────────────────

    public StudentProfileDocDto getProfileDoc(Integer studentId, String lang) {
        StudentProfileDoc doc = requireInScope(studentId);
        boolean en = "en".equalsIgnoreCase(lang);

        StudentProfileDocDto dto = new StudentProfileDocDto();
        dto.setStudentId(doc.getStudentId());
        dto.setStudentName(doc.getStudentName());

        dto.setGenderName(pick(en, doc.getGenderNameAr(), doc.getGenderNameEn()));
        dto.setBirthDate(formatDate(doc.getStudentBirthdate()));
        dto.setBirthPlace(doc.getStudentBirthdatePlace());
        dto.setAge(doc.getStudentAge());

        dto.setHomeCountryName(pick(en, doc.getHomeCountryNameAr(), doc.getHomeCountryNameEn()));
        dto.setHomeGovName(pick(en, doc.getHomeGovNameAr(), doc.getHomeGovNameEn()));
        dto.setHomeMudName(pick(en, doc.getHomeMudNameAr(), doc.getHomeMudNameEn()));

        dto.setPassportNumber(doc.getStudentPassportNumber());
        dto.setPassportIssueDate(formatDate(doc.getStudentPassportIssueDate()));
        dto.setPassportExpireDate(formatDate(doc.getStudentPassportExpireDate()));
        dto.setPassportCountryName(pick(en, doc.getPassportCountryNameAr(), doc.getPassportCountryNameEn()));

        dto.setDegreeName(pick(en, doc.getDegreeNameAr(), doc.getDegreeNameEn()));
        dto.setMajorName(pick(en, doc.getMajorNameAr(), doc.getMajorNameEn()));
        dto.setLevelName(pick(en, doc.getLevelNameAr(), doc.getLevelNameEn()));
        dto.setStudyTypeName(pick(en, doc.getStudyTypeNameAr(), doc.getStudyTypeNameEn()));
        dto.setStudyStatusName(pick(en, doc.getStudyStatusNameAr(), doc.getStudyStatusNameEn()));
        dto.setUniversityTypeName(pick(en, doc.getUniversityTypeNameAr(), doc.getUniversityTypeNameEn()));
        dto.setAgencyTypeName(pick(en, doc.getAgencyTypeNameAr(), doc.getAgencyTypeNameEn()));
        dto.setMinistryTypeName(pick(en, doc.getMinistryTypeNameAr(), doc.getMinistryTypeNameEn()));
        dto.setStudyNoOfYears(doc.getStudentStudyNoOfYears());

        dto.setDelegateDecisionNo(doc.getStudentDelegateDecisionNo());
        dto.setDelegationDecisionYear(doc.getStudentDelegationDecisionYear());

        dto.setStudyCountryName(pick(en, doc.getStudyCountryNameAr(), doc.getStudyCountryNameEn()));
        dto.setStudyGovName(pick(en, doc.getStudyGovNameAr(), doc.getStudyGovNameEn()));
        dto.setStudyUniversityName(pick(en, doc.getStudyUniversityNameAr(), doc.getStudyUniversityNameEn()));
        dto.setStudyCollegeName(pick(en, doc.getStudyCollegeNameAr(), doc.getStudyCollegeNameEn()));
        dto.setStudyEducationName(pick(en, doc.getStudyEducationNameAr(), doc.getStudyEducationNameEn()));
        dto.setStudyMajorInName(pick(en, doc.getStudyMajorInNameAr(), doc.getStudyMajorInNameEn()));

        dto.setAmount(doc.getStudentAmount());
        dto.setCurrencyName(pick(en, doc.getCurrencyNameAr(), doc.getCurrencyNameEn()));

        dto.setEmail(doc.getStudentEmail());
        dto.setCellphone(doc.getStudentCellphone());
        dto.setHomeNumber(doc.getStudentHomeNumber());
        dto.setEmergencyContact(doc.getStudentEmergencyContact());

        dto.setAddedQuarterLabel(quarterLabel(doc.getStudentAddedQuarter()));
        dto.setLastQuarterLabel(quarterLabel(doc.getStudentLastQuarter()));
        dto.setCurrentQuarterLabel(quarterLabelFromCode(doc.getCurrentQuarter()));

        return dto;
    }

    /**
     * The enrollment-certificate body paragraph: the ministry's sentence is the
     * LU_REPORT_LABEL row {@code TPL_ENROLLMENT_CERT_BODY} (V076) in the requested
     * language, with {@code {ministry}}, {@code {student}}, {@code {university}},
     * {@code {college}}, {@code {major}}, {@code {degree}} and {@code {year}}
     * replaced from the profile row. A value the row does not carry prints as
     * {@value #MISSING}.
     *
     * @param ministryName the resolved first masthead line; when blank the ministry
     *                     name comes from SYS_SETTINGS
     */
    public String buildCertificateBody(StudentProfileDocDto d, String lang, String ministryName) {
        boolean en = "en".equalsIgnoreCase(lang);
        String l = en ? "en" : "ar";
        String template = codeLists.label("report-label", ReportLabelCode.TPL_ENROLLMENT_CERT_BODY, l);
        String ministry = firstNonBlankStr(ministryName, ministryNameFromSettings(en));
        return template
                .replace("{ministry}", orMissing(ministry))
                .replace("{student}", orMissing(d.getStudentName()))
                .replace("{university}", orMissing(d.getStudyUniversityName()))
                .replace("{college}", orMissing(d.getStudyCollegeName()))
                .replace("{major}", orMissing(d.getMajorName()))
                .replace("{degree}", orMissing(d.getDegreeName()))
                .replace("{year}", orMissing(academicYear(d)));
    }

    private String ministryNameFromSettings(boolean en) {
        SysSettingsDto settings = sysSettingsService.getSettings();
        if (settings == null) {
            return null;
        }
        return en ? firstNonBlankStr(settings.getSysNameEn(), settings.getSysNameAr())
                  : firstNonBlankStr(settings.getSysNameAr(), settings.getSysNameEn());
    }

    /**
     * The academic year the certificate speaks of: the year of the student's
     * current quarter ("2026 Q2" → "2026"), which is the only "now" the profile
     * row records; null when the student has no current quarter.
     */
    private static String academicYear(StudentProfileDocDto d) {
        String q = d.getCurrentQuarterLabel();
        if (q != null && q.length() >= 4 && q.substring(0, 4).chars().allMatch(Character::isDigit)) {
            return q.substring(0, 4);
        }
        return null;
    }

    private static String orMissing(String s) {
        return (s != null && !s.isBlank()) ? s : MISSING;
    }

    // ── Financial statement ───────────────────────────────────────────────────

    public List<StudentFinancialLineDto> getFinancialStatement(Integer studentId, String lang) {
        requireInScope(studentId);
        boolean en = "en".equalsIgnoreCase(lang);
        return statementRowRepository
                .findByStudentIdOrderByMoheQuarterIdAscReceivedYearAscReceivedMonthAsc(studentId)
                .stream()
                .map(r -> toLineDto(r, en))
                .toList();
    }

    /** Grand totals + fee/books/loan sums passed to the statement template as params. */
    public FinancialTotals getFinancialTotals(Integer studentId, List<StudentFinancialLineDto> lines) {
        long received = lines.stream().map(StudentFinancialLineDto::getReceivedAmount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long wafar = lines.stream().map(StudentFinancialLineDto::getWafarAmount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long net = lines.stream().map(StudentFinancialLineDto::getNetAmount).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();

        long fees = universityFeeRepository.findByStudentId(studentId).stream()
                .map(f -> firstNonNull(f.getStudyUniversityRealFeeAmount(), f.getStudyUniversityFeeAmount()))
                .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long books = booksAllowanceRepository.findByStudentIdOrderByBooksAllowIdAsc(studentId).stream()
                .map(StudentBooksAllowance::getAllowanceAmount)
                .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        long loans = loanRepository.findByStudentIdOrderByLoanIdAsc(studentId).stream()
                .map(StudentLoan::getLoanAmount)
                .filter(Objects::nonNull).mapToLong(Integer::longValue).sum();

        return new FinancialTotals(received, wafar, net, fees, books, loans);
    }

    public record FinancialTotals(long received, long wafar, long net, long fees, long books, long loans) {
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Load the profile view row, 404 when the student doesn't exist and 403
     * when the caller's country scope excludes the student's study country
     * (same contract as IndividualStudentReportService).
     */
    private StudentProfileDoc requireInScope(Integer studentId) {
        if (studentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "studentId is required");
        }
        // The student first, by the student record's own rule (N14): the country used to be
        // read with .orElse(null) and a null let through.
        studentAccessGuard.requireStudent(studentId);
        return profileDocRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private StudentFinancialLineDto toLineDto(StudentFinancialStatementRow r, boolean en) {
        StudentFinancialLineDto dto = new StudentFinancialLineDto();
        dto.setRowKey(r.getRowKey());
        dto.setSourceType(r.getSourceType());
        dto.setStudentId(r.getStudentId());
        dto.setQuarterCode(r.getMoheQuarterId());
        String name = pick(en, r.getQuarterNameAr(), r.getQuarterNameEn());
        dto.setQuarterLabel(name != null && !name.isBlank() ? name : quarterLabel(r.getMoheQuarterId()));
        dto.setReceivedYear(r.getReceivedYear());
        dto.setReceivedMonth(r.getReceivedMonth());
        dto.setReceivedAmount(r.getReceivedAmount());
        dto.setWafarAmount(r.getWafarAmount());
        dto.setWafarTypeName(pick(en, r.getWafarTypeNameAr(), r.getWafarTypeNameEn()));
        dto.setNetAmount(r.getNetAmount());
        dto.setCurrencyName(pick(en, r.getCurrencyNameAr(), r.getCurrencyNameEn()));
        dto.setBankName(pick(en, r.getBankNameAr(), r.getBankNameEn()));
        dto.setBankBranchName(pick(en, r.getBankBranchNameAr(), r.getBankBranchNameEn()));
        dto.setReceivedStatusName(pick(en, r.getReceivedStatusNameAr(), r.getReceivedStatusNameEn()));
        dto.setCheckNo(r.getStudyCheckId());
        dto.setCheckDate(formatDate(r.getStudyCheckDate()));
        dto.setCheckFormNumber(r.getStudyCheckFormNumber());
        return dto;
    }

    private static String pick(boolean en, String ar, String enName) {
        return en ? firstNonBlankStr(enName, ar) : firstNonBlankStr(ar, enName);
    }

    private static String firstNonBlankStr(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }

    private static Integer firstNonNull(Integer primary, Integer fallback) {
        return primary != null ? primary : fallback;
    }

    private String formatDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.of(reportTimezone)).toLocalDate().format(DATE_FMT);
    }

    /** YYYYQ quarter code → "2024 Q2"; passthrough for anything unparseable. */
    private static String quarterLabel(Integer code) {
        if (code == null || code == 0) {
            return null;
        }
        String s = String.valueOf(code);
        if (s.length() == 5) {
            return s.substring(0, 4) + " Q" + s.substring(4);
        }
        return s;
    }

    private static String quarterLabelFromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.matches("\\d{5}")) {
            return trimmed.substring(0, 4) + " Q" + trimmed.substring(4);
        }
        return trimmed;
    }

}
