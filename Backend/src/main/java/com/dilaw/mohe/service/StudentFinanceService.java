package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.repository.spec.StudentFinanceSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import com.dilaw.mohe.util.StudentFinanceDraftMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataAccessException;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudentFinanceService {

    private static final Logger logger = LoggerFactory.getLogger(StudentFinanceService.class);

    /**
     * Result of seeding STUDENT_FINANCE from STUDENT_DATA then applying approval JSON overlays.
     * {@code newRowThisRun} is 1 when this execution created the finance row (JDBC or JPA fallback); 0 when the row already existed.
     */
    public record SeedAndMergeOutcome(int newRowThisRun, StudentFinance merged) {}

    /** One-row variant of scripts/ssms/MOHECORE_student_finance_seed_from_student_data.sql */
    static final String INSERT_FINANCE_FROM_STUDENT_SQL = ""
            + "INSERT INTO MOHECORE.STUDENT_FINANCE ("
            + "STUDENT_ID, STUDENT_FIN_NAME, STUDENT_FIN_RECIEVED_YEAR, STUDENT_FIN_RECIEVED_MONTH, "
            + "STUDENT_FIN_RECIEVED_YEAR_MONTH, STUDENT_FIN_RECIEVED_AMOUNT, STUDY_COUNTRY_IN_ID, "
            + "STUDY_GOV_IN_ID, STUDY_NOTE_1, STUDY_FINANCE_MIDATE) "
            + "SELECT "
            + "SD.STUDENT_ID, "
            + "CAST(ISNULL(CAST(SD.STUDENT_NAME AS NVARCHAR(MAX)), N'') AS NVARCHAR(MAX)), "
            + "ISNULL(SD.STUDENT_ADDED_QUARTER, 0) / 10, "
            + "ISNULL(SD.STUDENT_ADDED_QUARTER, 0) % 10, "
            + "ISNULL(SD.STUDENT_ADDED_QUARTER, 0), "
            + "ISNULL(SD.STUDENT_AMOUNT, 0), "
            + "ISNULL(SD.STUDY_COUNTRY_IN, 0), "
            + "ISNULL(SD.SYUDY_GOV_IN, 0), "
            + "CAST(COALESCE(SD.STUDENT_NOTE_1, N'') AS NVARCHAR(MAX)) "
            + "+ CASE WHEN SD.STUDENT_NOTE_2 IS NOT NULL "
            + "THEN N' | ' + CAST(SD.STUDENT_NOTE_2 AS NVARCHAR(MAX)) ELSE N'' END, "
            + "GETDATE() "
            + "FROM MOHECORE.STUDENT_DATA SD "
            + "WHERE SD.STUDENT_ID = ? "
            // No finance row for a deleted student (live QA H7): the delete removes it, and this keeps it removed.
            + "AND ISNULL(SD.IS_DELETED, 0) = 0 "
            + "AND NOT EXISTS ("
            + "SELECT 1 FROM MOHECORE.STUDENT_FINANCE SF WHERE SF.STUDENT_ID = SD.STUDENT_ID)";

    @Autowired
    private StudentFinanceRepository repository;

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Lazy
    @Autowired
    private StudentYearResultService yearResultService;

    @Autowired
    private CountryScopeService countryScopeService;

    @Lazy
    @Autowired
    private StudyFinReceivedStatusService receivedStatuses;

    private CountryScope currentScope() {
        return countryScopeService.getScope();
    }

    /** The ids that mean money moved, by CODE; the V072 ids when the lookup is not wired (unit tests). */
    private java.util.Set<Integer> paidStatusIds() {
        return receivedStatuses == null
                ? StudyFinReceivedStatusService.PAID_IDS_FALLBACK
                : receivedStatuses.paidStatusIds();
    }

    /** The id of NOT_SPECIFIED, by CODE; 99 when the lookup is not wired. */
    private int notSpecifiedStatusId() {
        return receivedStatuses == null
                ? StudyFinReceivedStatusService.NOT_SPECIFIED_ID_FALLBACK
                : receivedStatuses.notSpecifiedStatusId();
    }

    public List<StudentFinance> getAll() {
        Specification<StudentFinance> spec = StudentFinanceSpec.countryScope(currentScope())
                .and(StudentFinanceSpec.liveStudent());
        return repository.findAll(spec);
    }

    public Page<StudentFinance> getAll(Pageable pageable) {
        return repository.findAll(StudentFinanceSpec.countryScope(currentScope())
                .and(StudentFinanceSpec.liveStudent()), pageable);
    }

    /**
     * Finance for the student behind a portal session, with no UMS authorisation.
     *
     * <p>Counterpart to {@link #getById(int)}, which gates on
     * {@code CountryScopeService} — see
     * {@link StudentDataService#getStudentDataForPortal} for why that gate is
     * wrong on this path. The portal's own call was inside a {@code try/catch}
     * that swallowed the 403, so /me/data quietly reported finance as null for
     * every student rather than erroring.
     */
    public StudentFinance getByIdForPortal(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinance not found"));
    }

    public StudentFinance getById(int id) {
        StudentFinance finance = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinance not found"));
        Integer countryId = finance.getStudyCountryInId();
        if (countryId != null && !countryScopeService.canAccessCountry(countryId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: country not in scope");
        }
        return finance;
    }

    @Transactional
    public StudentFinance create(StudentFinance entity) {
        StudentFinanceInputRules.requireReceivedYear(entity);
        return repository.save(entity);
    }

    /**
     * Inserts STUDENT_FINANCE mapped from STUDENT_DATA when absent (approve-onboarding INSERT path).
     *
     * <p>STUDENT_FINANCE.STUDENT_ID is a plain key copied from STUDENT_DATA, not an
     * identity column, so no {@code SET IDENTITY_INSERT} is needed — and none may be
     * tried. This method used to "probe" with one and catch the failure. For the
     * least-privilege login {@code mohe_app} that statement fails with error 1088,
     * which SQL Server treats as batch-aborting: it <b>rolls back the whole open
     * transaction</b>, the student row of the approval being executed included. The
     * caught exception hid that, and every later statement ran in a new implicit
     * transaction, so an approval could commit finance rows for a student that no
     * longer existed (found 22 Sep 2026 while fixing live QA #1).
     */
    public int insertFromStudentDataIfMissing(int studentId) {
        return jdbcTemplate.update(INSERT_FINANCE_FROM_STUDENT_SQL, studentId);
    }

    @Transactional
    public StudentFinance update(Integer id, StudentFinance entity) {
        Optional<StudentFinance> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudentFinance not found");
        }
        // §5.3: Log a warning when status moves to a paid one (received, disbursed —
        // by CODE, not "non-zero": NOT_SPECIFIED is 99 since V135) and the student has
        // a failing year.
        Integer newStatus = entity.getStudyFinRecievedStatus();
        StudentFinance existing = existingOptional.get();
        java.util.Set<Integer> paid = paidStatusIds();
        if (newStatus != null && paid.contains(newStatus)
                && (existing.getStudyFinRecievedStatus() == null || !paid.contains(existing.getStudyFinRecievedStatus()))) {
            String warning = yearResultService.getPaymentWarning(id);
            if (warning != null) {
                logger.warn("Finance payment status change to {} for student {}: {}", newStatus, id, warning);
            }
        }
        entity.setId(id);
        StudentFinanceInputRules.requireReceivedYear(entity);
        return repository.save(entity);
    }

    @Transactional
    public void delete(Integer id) {
        repository.deleteById(id);
    }

    /**
     * Used by STUDENT_FINANCE approval EXECUTE: seed from STUDENT_DATA when absent,
     * then overlay non-null fields from the approval JSON so values match the onboarding draft / UI.
     */
    @Transactional
    public SeedAndMergeOutcome seedFromStudentDataAndMergeDraft(int studentId, StudentFinance draftFromApproval) {
        int jdbcRows = 0;
        try {
            jdbcRows = insertFromStudentDataIfMissing(studentId);
        } catch (DataAccessException ex) {
            logger.warn(
                    "STUDENT_FINANCE JDBC seed failed for studentId={} (will try JPA fallback if row still missing): {}",
                    studentId, ex.getMessage());
        }

        /*
         * If JDBC INSERT could not run (schema mismatch, constraint, etc.), Hibernate may still INSERT a row with
         * column defaults mapped from STUDENT_DATA so approval execution does not silently leave no finance row.
         */
        int newRowThisRun = jdbcRows;
        if (jdbcRows == 0 && !repository.existsById(studentId)) {
            StudentDatum sd = studentDataRepository.findById(studentId).orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Cannot seed STUDENT_FINANCE: STUDENT_DATA has no row for student id " + studentId));
            StudentFinance built = baselineFinanceMappedFromStudentData(sd);
            mergeNonNullDraftFields(built, draftFromApproval, notSpecifiedStatusId());
            built.setId(studentId);
            repository.save(built);
            newRowThisRun = 1;
            logger.info(
                    "STUDENT_FINANCE JDBC did not insert for studentId={}; created row via JPA from STUDENT_DATA + defaults",
                    studentId);
        }

        StudentFinance persisted = repository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "STUDENT_FINANCE row missing for student id " + studentId
                                + " after seed from STUDENT_DATA (student may not exist in MOHECORE.STUDENT_DATA)"));
        mergeNonNullDraftFields(persisted, draftFromApproval, notSpecifiedStatusId());
        persisted.setId(studentId);
        StudentFinanceInputRules.requireReceivedYear(persisted);
        return new SeedAndMergeOutcome(newRowThisRun, repository.save(persisted));
    }

    /** Same projection as INSERT_FINANCE_FROM_STUDENT_SQL, for JPA fallback when JDBC insert is not accepted by the DB. */
    private static StudentFinance baselineFinanceMappedFromStudentData(StudentDatum sd) {
        StudentFinance f = StudentFinanceDraftMapper.fromStudentDatum(sd);
        f.setId(sd.getStudentId());
        f.setStudentFinName(sd.getStudentName() != null ? sd.getStudentName() : "");
        Integer q = sd.getStudentAddedQuarter();
        int qv = q != null ? q : 0;
        f.setStudentFinRecievedYear(qv / 10);
        f.setStudentFinRecievedMonth(qv % 10);
        f.setStudentFinRecievedYearMonth(qv);
        f.setStudentFinRecievedAmount(sd.getStudentAmount() != null ? sd.getStudentAmount() : 0);
        f.setStudyCountryInId(sd.getStudyCountryIn() != null ? sd.getStudyCountryIn() : 0);
        f.setStudyGovInId(sd.getSyudyGovIn() != null ? sd.getSyudyGovIn() : 0);
        String n1 = sd.getStudentNote1();
        String n2 = sd.getStudentNote2();
        StringBuilder note = new StringBuilder();
        if (n1 != null && !n1.isBlank()) {
            note.append(n1);
        }
        if (n2 != null && !n2.isBlank()) {
            if (!note.isEmpty()) {
                note.append(" | ");
            }
            note.append(n2);
        }
        f.setStudyNote1(note.toString());
        f.setStudyFinanceMidate(Instant.now());
        return f;
    }

    /**
     * Copy only non-null properties from approval payload onto the persisted row.
     *
     * @param notSpecifiedStatusId the id of NOT_SPECIFIED, which the row lands on when
     *                             the draft names no positive status (live QA H2)
     */
    static void mergeNonNullDraftFields(StudentFinance target, StudentFinance draft, int notSpecifiedStatusId) {
        if (draft == null) {
            if (target.getStudyFinRecievedStatus() == null || target.getStudyFinRecievedStatus() <= 0) {
                target.setStudyFinRecievedStatus(notSpecifiedStatusId);
            }
            return;
        }
        if (draft.getStudentFinId() != null) {
            target.setStudentFinId(draft.getStudentFinId());
        }
        if (draft.getStudentFinName() != null && !draft.getStudentFinName().isBlank()) {
            target.setStudentFinName(draft.getStudentFinName());
        }
        Integer draftYear = draft.getStudentFinRecievedYear();
        if (draftYear != null && com.dilaw.mohe.util.StudentFinanceDraftMapper.isQuarterKey(draftYear)) {
            // A wizard draft raised before live QA H7 was fixed carries the YYYYQ key as the year.
            target.setStudentFinRecievedYear(draftYear / 10);
            target.setStudentFinRecievedMonth(draftYear % 10);
            target.setStudentFinRecievedYearMonth(draftYear);
        } else {
            if (draftYear != null) {
                target.setStudentFinRecievedYear(draftYear);
            }
            if (draft.getStudentFinRecievedMonth() != null) {
                target.setStudentFinRecievedMonth(draft.getStudentFinRecievedMonth());
            }
            if (draft.getStudentFinRecievedYearMonth() != null) {
                target.setStudentFinRecievedYearMonth(draft.getStudentFinRecievedYearMonth());
            }
        }
        if (draft.getStudentFinRecievedAmount() != null) {
            target.setStudentFinRecievedAmount(draft.getStudentFinRecievedAmount());
        }
        if (draft.getStudyCurrencyInId() != null) {
            target.setStudyCurrencyInId(draft.getStudyCurrencyInId());
        }
        if (draft.getStudyCountryInId() != null) {
            target.setStudyCountryInId(draft.getStudyCountryInId());
        }
        if (draft.getStudyGovInId() != null) {
            target.setStudyGovInId(draft.getStudyGovInId());
        }
        if (draft.getStudyFinanceBankeId() != null) {
            target.setStudyFinanceBankeId(draft.getStudyFinanceBankeId());
        }
        if (draft.getStudyFinanceBankBranchId() != null) {
            target.setStudyFinanceBankBranchId(draft.getStudyFinanceBankBranchId());
        }
        if (draft.getStudyFinanceDofId() != null) {
            target.setStudyFinanceDofId(draft.getStudyFinanceDofId());
        }
        if (draft.getStudyCheckId() != null) {
            target.setStudyCheckId(draft.getStudyCheckId());
        }
        if (draft.getStudyCheckDate() != null) {
            target.setStudyCheckDate(draft.getStudyCheckDate());
        }
        if (draft.getStudyCheckDetails() != null) {
            target.setStudyCheckDetails(draft.getStudyCheckDetails());
        }
        if (draft.getStudyCheckFormNumber() != null) {
            target.setStudyCheckFormNumber(draft.getStudyCheckFormNumber());
        }
        // A draft's 0 is "not set" (the wizard sends 0 for a new student), not a status:
        // it used to be written as 0, which is what put the row outside the form's own
        // rules (live QA H2). Positive ids are applied; anything else leaves the row on
        // NOT_SPECIFIED.
        if (draft.getStudyFinRecievedStatus() != null && draft.getStudyFinRecievedStatus() > 0) {
            target.setStudyFinRecievedStatus(draft.getStudyFinRecievedStatus());
        }
        if (target.getStudyFinRecievedStatus() == null || target.getStudyFinRecievedStatus() <= 0) {
            target.setStudyFinRecievedStatus(notSpecifiedStatusId);
        }
        if (draft.getStudyNote1() != null) {
            target.setStudyNote1(draft.getStudyNote1());
        }
        if (draft.getStudyReceivedWhomStatusId() != null) {
            target.setStudyReceivedWhomStatusId(draft.getStudyReceivedWhomStatusId());
        }
        if (draft.getStudyReceivedWhomId() != null) {
            target.setStudyReceivedWhomId(draft.getStudyReceivedWhomId());
        }
        if (draft.getWafarAmount() != null) {
            target.setWafarAmount(draft.getWafarAmount());
        }
        if (draft.getWafarTypeId() != null) {
            target.setWafarTypeId(draft.getWafarTypeId());
        }
        if (draft.getStudyFinanceMidate() != null) {
            target.setStudyFinanceMidate(draft.getStudyFinanceMidate());
        }
        if (draft.getStudentBankId() != null) {
            target.setStudentBankId(draft.getStudentBankId());
        }
        if (draft.getStudentAccountNumber() != null) {
            target.setStudentAccountNumber(draft.getStudentAccountNumber());
        }
        if (draft.getStudentAccountEnglishName() != null) {
            target.setStudentAccountEnglishName(draft.getStudentAccountEnglishName());
        }
        if (draft.getStudentAccountEndDate() != null) {
            target.setStudentAccountEndDate(draft.getStudentAccountEndDate());
        }
        if (draft.getStudentBankNotes() != null) {
            target.setStudentBankNotes(draft.getStudentBankNotes());
        }
    }

    /** Direct update of STUDY_FIN_RECIEVED_STATUS — bypasses approval workflow. */
    @Transactional
    public void updateReceivedStatus(Integer financeId, Integer statusId) {
        if (statusId == null) throw new IllegalArgumentException("statusId is required");
        StudentFinance f = getById(financeId);
        f.setStudyFinRecievedStatus(statusId);
        repository.save(f);
    }

    /** Direct update of received-whom FK + status — bypasses approval workflow (reference pointers only). */
    @Transactional
    public void updateReceivedWhomInfo(Integer financeId, Integer whomId, Integer statusId) {
        StudentFinance f = getById(financeId);
        if (whomId != null) {
            f.setStudyReceivedWhomId(whomId);
        }
        if (statusId != null) {
            f.setStudyReceivedWhomStatusId(statusId);
        }
        repository.save(f);
    }

    public Page<StudentFinance> search(String searchTerm, Integer status,
            java.time.Instant startDate, java.time.Instant endDate, Pageable pageable) {
        Specification<StudentFinance> spec = Specification
                .where(StudentFinanceSpec.countryScope(currentScope()))
                .and(StudentFinanceSpec.liveStudent())
                .and(StudentFinanceSpec.searchStatusAndDates(
                        (searchTerm != null && !searchTerm.trim().isEmpty()) ? searchTerm.trim() : null,
                        status, startDate, endDate));
        return repository.findAll(spec, pageable);
    }
} 