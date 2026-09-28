package com.dilaw.mohe.service;

import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.dto.StudentFinancialLineDto;
import com.dilaw.mohe.dto.StudentGraphNodeDto;
import com.dilaw.mohe.dto.StudentProfileDocDto;
import com.dilaw.mohe.model.AbstractLuLookup;
import com.dilaw.mohe.model.PaymentAuth;
import com.dilaw.mohe.model.StudentBooksAllowance;
import com.dilaw.mohe.model.StudentExtension;
import com.dilaw.mohe.model.StudentLoan;
import com.dilaw.mohe.model.StudentYearResult;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.LuBooksAllowancePayStatusRepository;
import com.dilaw.mohe.repository.LuLoanStatusRepository;
import com.dilaw.mohe.repository.PaymentAuthRepository;
import com.dilaw.mohe.repository.StudentBooksAllowanceRepository;
import com.dilaw.mohe.repository.StudentExtensionRepository;
import com.dilaw.mohe.repository.StudentLoanRepository;
import com.dilaw.mohe.repository.StudyCurrencyInRepository;
import com.dilaw.mohe.repository.StudyUniversityFeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds the expandable node tree behind the student relationship graph.
 *
 * <p>Everything is resolved here rather than in the browser: the page used to
 * fetch raw entities plus a metadata bundle and match lookup ids client-side,
 * which printed bare numeric ids whenever a lookup row was missing. The profile
 * view already exposes every foreign key as a paired AR/EN name, so the graph
 * is assembled from that and the values arrive ready to display.
 *
 * <p>Finance branches are omitted for callers without the finance report
 * permission, so the page degrades to the academic view instead of failing.
 *
 * <p>Labels come from the {@code graph.*} keys of the message bundles through
 * {@link #label(String, Object...)}. They were some fifty inline
 * {@code en ? "Gender" : "الجنس"} pairs, a third copy of wording the bundles
 * already carry for the reports. The caller's {@code lang} sets the locale for
 * the build — the same language the lookup names are chosen in — and it is
 * restored afterwards.
 */
@Transactional(readOnly = true)
@Service
public class StudentGraphService {

    /** Beyond this a branch is summarised, so one long history cannot swamp the canvas. */
    private static final int MAX_CHILDREN = 12;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Locale ARABIC = new Locale("ar");

    @Autowired private StudentDocumentReportService documentService;
    @Autowired private StudentYearResultService yearResultService;
    @Autowired private PaymentAuthRepository paymentAuthRepository;
    @Autowired private StudyUniversityFeeRepository universityFeeRepository;
    @Autowired private StudentBooksAllowanceRepository booksAllowanceRepository;
    @Autowired private StudentLoanRepository loanRepository;
    @Autowired private StudentExtensionRepository extensionRepository;
    @Autowired private StudyCurrencyInRepository currencyRepository;
    @Autowired private LuLoanStatusRepository loanStatusRepository;
    @Autowired private LuBooksAllowancePayStatusRepository booksPayStatusRepository;
    @Autowired private MessageSource messageSource;
    @Autowired private AppTimeZone appTimeZone;

    /**
     * @param includeFinance whether the caller may see money; when false the
     *                       finance, payment, fee, books and loan branches are
     *                       left out entirely rather than shown empty.
     */
    public StudentGraphNodeDto buildGraph(Integer studentId, String lang, boolean includeFinance) {
        boolean en = "en".equalsIgnoreCase(lang);
        Locale previous = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(en ? Locale.ENGLISH : ARABIC);
        try {
            return build(studentId, lang, en, includeFinance);
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }

    private StudentGraphNodeDto build(Integer studentId, String lang, boolean en, boolean includeFinance) {
        // Also performs the existence and country-scope check (404 / 403).
        StudentProfileDocDto p = documentService.getProfileDoc(studentId, lang);

        StudentGraphNodeDto root = new StudentGraphNodeDto(
                "student", p.getStudentName(), null, "student");

        root.add(personal(p));
        root.add(passport(p));
        root.add(academic(p));
        root.add(destination(p));
        root.add(delegation(p));
        root.add(contacts(p));
        root.add(yearResults(studentId));
        root.add(extensions(studentId));

        if (includeFinance) {
            root.add(finance(studentId, p, lang));
            root.add(payments(studentId));
            root.add(universityFees(studentId, en));
            root.add(booksAllowances(studentId, en));
            root.add(loans(studentId, en));
        }
        return root;
    }

    // ── Profile categories ────────────────────────────────────────────────────

    private StudentGraphNodeDto personal(StudentProfileDocDto p) {
        StudentGraphNodeDto c = category("cat_personal", label("cat.personal"));
        leaf(c, label("label.gender"), p.getGenderName());
        leaf(c, label("label.birthDate"), p.getBirthDate());
        leaf(c, label("label.birthPlace"), p.getBirthPlace());
        leaf(c, label("label.age"), p.getAge());
        leaf(c, label("label.country"), p.getHomeCountryName());
        leaf(c, label("label.governorate"), p.getHomeGovName());
        leaf(c, label("label.district"), p.getHomeMudName());
        return prune(c);
    }

    private StudentGraphNodeDto passport(StudentProfileDocDto p) {
        StudentGraphNodeDto c = category("cat_passport", label("cat.passport"));
        leaf(c, label("label.passportNo"), p.getPassportNumber());
        leaf(c, label("label.issueDate"), p.getPassportIssueDate());
        leaf(c, label("label.expiryDate"), p.getPassportExpireDate());
        leaf(c, label("label.issuingCountry"), p.getPassportCountryName());
        return prune(c);
    }

    private StudentGraphNodeDto academic(StudentProfileDocDto p) {
        StudentGraphNodeDto c = category("cat_education", label("cat.education"));
        leaf(c, label("label.degree"), p.getDegreeName());
        leaf(c, label("label.major"), p.getMajorName());
        leaf(c, label("label.currentLevel"), p.getLevelName());
        leaf(c, label("label.studyType"), p.getStudyTypeName());
        leaf(c, label("label.universityType"), p.getUniversityTypeName());
        leaf(c, label("label.agencyType"), p.getAgencyTypeName());
        leaf(c, label("label.ministry"), p.getMinistryTypeName());
        leaf(c, label("label.yearsOfStudy"), str(p.getStudyNoOfYears()));
        return prune(c);
    }

    private StudentGraphNodeDto destination(StudentProfileDocDto p) {
        StudentGraphNodeDto c = category("cat_location", label("cat.location"));
        leaf(c, label("label.countryOfStudy"), p.getStudyCountryName());
        leaf(c, label("label.cityRegion"), p.getStudyGovName());
        leaf(c, label("label.university"), p.getStudyUniversityName());
        leaf(c, label("label.college"), p.getStudyCollegeName());
        leaf(c, label("label.department"), p.getStudyEducationName());
        leaf(c, label("label.specialization"), p.getStudyMajorInName());
        return prune(c);
    }

    private StudentGraphNodeDto delegation(StudentProfileDocDto p) {
        StudentGraphNodeDto c = category("cat_delegation", label("cat.delegation"));
        leaf(c, label("label.decisionNo"), str(p.getDelegateDecisionNo()));
        leaf(c, label("label.decisionYear"), p.getDelegationDecisionYear());
        leaf(c, label("label.addedQuarter"), p.getAddedQuarterLabel());
        leaf(c, label("label.lastQuarter"), p.getLastQuarterLabel());
        leaf(c, label("label.currentQuarter"), p.getCurrentQuarterLabel());
        return prune(c);
    }

    private StudentGraphNodeDto contacts(StudentProfileDocDto p) {
        StudentGraphNodeDto c = category("cat_contacts", label("cat.contacts"));
        leaf(c, label("label.email"), p.getEmail());
        leaf(c, label("label.mobile"), p.getCellphone());
        leaf(c, label("label.homePhone"), p.getHomeNumber());
        leaf(c, label("label.emergencyContact"), p.getEmergencyContact());
        return prune(c);
    }

    // ── Repeating records: each becomes an expandable child ────────────────────

    private StudentGraphNodeDto finance(Integer studentId, StudentProfileDocDto p, String lang) {
        StudentGraphNodeDto c = category("cat_finance", label("cat.finance"));
        leaf(c, label("label.allocatedAmount"), str(p.getAmount()));
        leaf(c, label("label.currency"), p.getCurrencyName());

        List<StudentFinancialLineDto> lines = documentService.getFinancialStatement(studentId, lang);
        var totals = documentService.getFinancialTotals(studentId, lines);
        leaf(c, label("label.totalReceived"), str(totals.received()));
        leaf(c, label("label.totalWafar"), str(totals.wafar()));
        leaf(c, label("label.totalNet"), str(totals.net()));

        addEach(c, lines, (line) -> {
            StudentGraphNodeDto q = new StudentGraphNodeDto(
                    null, financePeriodLabel(line), null, "cat_finance");
            leaf(q, label("label.received"), str(line.getReceivedAmount()));
            leaf(q, label("label.wafar"), str(line.getWafarAmount()));
            leaf(q, label("label.wafarType"), line.getWafarTypeName());
            leaf(q, label("label.net"), str(line.getNetAmount()));
            leaf(q, label("label.currency"), line.getCurrencyName());
            leaf(q, label("label.bank"), line.getBankName());
            leaf(q, label("label.branch"), line.getBankBranchName());
            leaf(q, label("label.status"), line.getReceivedStatusName());
            leaf(q, label("label.chequeNo"), line.getCheckNo());
            leaf(q, label("label.chequeDate"), line.getCheckDate());
            return q;
        });
        return prune(c);
    }

    private StudentGraphNodeDto payments(Integer studentId) {
        StudentGraphNodeDto c = category("cat_payments", label("cat.payments"));
        List<PaymentAuth> rows = paymentAuthRepository.findByStudentIdOrderByAuthMidateDesc(studentId);
        addEach(c, rows, (a) -> {
            StudentGraphNodeDto n = new StudentGraphNodeDto(
                    null, firstNonBlank(a.getAuthFormNumber(), str(a.getId())), null, "cat_payments");
            leaf(n, label("label.date"), fmt(a.getAuthDate()));
            leaf(n, label("label.originalAmount"), str(a.getOriginalAmount()));
            leaf(n, label("label.deductions"), str(a.getDeductions()));
            leaf(n, label("label.netAmount"), str(a.getNetAmount()));
            leaf(n, label("label.bank"), a.getBankName());
            leaf(n, label("label.branch"), a.getBankBranch());
            leaf(n, label("label.chequeNo"), a.getCheckNumber());
            leaf(n, label("label.chequeDate"), fmt(a.getCheckDate()));
            leaf(n, label("label.recipient"), a.getRecipientName());
            leaf(n, label("label.purpose"), a.getPaymentPurpose());
            return n;
        });
        return prune(c);
    }

    private StudentGraphNodeDto universityFees(Integer studentId, boolean en) {
        StudentGraphNodeDto c = category("cat_fees", label("cat.fees"));
        Map<Integer, String> currencies = currencyNames(en);
        List<StudyUniversityFee> rows = universityFeeRepository.findByStudentId(studentId);
        addEach(c, rows, (f) -> {
            StudentGraphNodeDto n = new StudentGraphNodeDto(
                    null, firstNonBlank(f.getStudyUniversityFeeYear(), label("label.fee")), null, "cat_fees");
            leaf(n, label("label.amount"), str(f.getStudyUniversityFeeAmount()));
            leaf(n, label("label.discount"), str(f.getStudyUniversityFeeDiscount()));
            leaf(n, label("label.netFee"), str(f.getStudyUniversityRealFeeAmount()));
            leaf(n, label("label.currency"), currencies.get(f.getStudyUniversityFeeCurrency()));
            leaf(n, label("label.paymentDate"), fmt(f.getStudyUniversityFeePayDate()));
            leaf(n, label("label.firstYear"), f.getStudyUniversityFeeFirstYear());
            return n;
        });
        return prune(c);
    }

    private StudentGraphNodeDto booksAllowances(Integer studentId, boolean en) {
        StudentGraphNodeDto c = category("cat_books", label("cat.books"));
        Map<Integer, String> statuses = lookupNames(booksPayStatusRepository.findByIsDeletedFalseOrderBySortOrderAsc(), en);
        List<StudentBooksAllowance> rows = booksAllowanceRepository.findByStudentIdOrderByBooksAllowIdAsc(studentId);
        addEach(c, rows, (b) -> {
            StudentGraphNodeDto n = new StudentGraphNodeDto(
                    null, firstNonBlank(b.getStudyYear(), label("label.allowance")), null, "cat_books");
            leaf(n, label("label.amount"), str(b.getAllowanceAmount()));
            leaf(n, label("label.status"), statuses.get(b.getPayStatus()));
            leaf(n, label("label.receiptDate"), fmt(b.getReceiptDate()));
            leaf(n, label("label.notes"), b.getNotes());
            return n;
        });
        return prune(c);
    }

    private StudentGraphNodeDto loans(Integer studentId, boolean en) {
        StudentGraphNodeDto c = category("cat_loans", label("cat.loans"));
        Map<Integer, String> currencies = currencyNames(en);
        Map<Integer, String> statuses = lookupNames(loanStatusRepository.findByIsDeletedFalseOrderBySortOrderAsc(), en);
        List<StudentLoan> rows = loanRepository.findByStudentIdOrderByLoanIdAsc(studentId);
        addEach(c, rows, (l) -> {
            StudentGraphNodeDto n = new StudentGraphNodeDto(
                    null, firstNonBlank(l.getStudyYear(), label("label.loan")), null, "cat_loans");
            leaf(n, label("label.amount"), str(l.getLoanAmount()));
            leaf(n, label("label.currency"), currencies.get(l.getCurrencyId()));
            leaf(n, label("label.status"), statuses.get(l.getLoanStatus()));
            leaf(n, label("label.notes"), l.getRepaymentNotes());
            return n;
        });
        return prune(c);
    }

    private StudentGraphNodeDto yearResults(Integer studentId) {
        StudentGraphNodeDto c = category("cat_yearResults", label("cat.yearResults"));
        List<StudentYearResult> rows = yearResultService.getByStudentId(studentId);
        addEach(c, rows, (y) -> {
            StudentGraphNodeDto n = new StudentGraphNodeDto(
                    null, label("label.yearN", str(y.getYearNumber())), null, "cat_yearResults");
            leaf(n, label("label.academicYear"), y.getAcademicYear());
            leaf(n, label("label.grade"), y.getGrade());
            leaf(n, label("label.notes"), y.getNotes());
            return n;
        });
        return prune(c);
    }

    private StudentGraphNodeDto extensions(Integer studentId) {
        StudentGraphNodeDto c = category("cat_extensions", label("cat.extensions"));
        List<StudentExtension> rows = extensionRepository.findByStudentIdOrderByExtensionIdAsc(studentId);
        addEach(c, rows, (x) -> {
            StudentGraphNodeDto n = new StudentGraphNodeDto(
                    null, firstNonBlank(x.getExtensionType(), label("label.extension")), null, "cat_extensions");
            leaf(n, label("label.quarters"), str(x.getExtensionQuarters()));
            leaf(n, label("label.startDate"), fmt(x.getExtensionStartDate()));
            leaf(n, label("label.endDate"), fmt(x.getExtensionEndDate()));
            leaf(n, label("label.reason"), x.getExtensionReason());
            leaf(n, label("label.decisionNo"), x.getExtensionDecisionNo());
            leaf(n, label("label.decisionDate"), fmt(x.getExtensionDecisionDate()));
            leaf(n, label("label.status"), x.getExtensionStatus());
            return n;
        });
        return prune(c);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * A graph label from the bundle, in the locale of the current build.
     *
     * @param key the part after {@code graph.}, e.g. {@code cat.personal} or {@code label.gender}
     */
    private String label(String key, Object... args) {
        return messageSource.getMessage("graph." + key, args, LocaleContextHolder.getLocale());
    }

    private static StudentGraphNodeDto category(String id, String label) {
        return new StudentGraphNodeDto(id, label, null, id);
    }

    /**
     * Names a finance row for its node. Many rows carry no quarter link at all
     * (MOHE_QUARTER_ID and the quarter names are null), so falling back to the
     * quarter label alone would render every row as an identical "Quarter" node
     * and the branch would be unreadable. The received year/month is always
     * present and keeps the rows distinguishable.
     */
    private String financePeriodLabel(StudentFinancialLineDto line) {
        if (line.getQuarterLabel() != null && !line.getQuarterLabel().isBlank()) {
            return line.getQuarterLabel();
        }
        if (line.getReceivedYear() != null) {
            return line.getReceivedMonth() != null
                    ? String.format("%d-%02d", line.getReceivedYear(), line.getReceivedMonth())
                    : String.valueOf(line.getReceivedYear());
        }
        return label("label.period");
    }

    /** Adds a value node, skipping blanks so the graph shows only what exists. */
    private static void leaf(StudentGraphNodeDto parent, String label, String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return;
        }
        parent.add(new StudentGraphNodeDto(null, label, value, parent.getCategory()));
    }

    /**
     * Maps each record to a child node, capping the branch and replacing the
     * remainder with a count so a long history stays readable.
     */
    private <T> void addEach(StudentGraphNodeDto parent, List<T> rows, Function<T, StudentGraphNodeDto> mapper) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        int shown = Math.min(rows.size(), MAX_CHILDREN);
        for (int i = 0; i < shown; i++) {
            StudentGraphNodeDto child = mapper.apply(rows.get(i));
            if (child != null && (child.hasChildren() || child.getValue() != null)) {
                parent.add(child);
            }
        }
        int hidden = rows.size() - shown;
        if (hidden > 0) {
            parent.add(new StudentGraphNodeDto(null, label("label.more", hidden), null, parent.getCategory()));
        }
    }

    /** Drops a category that ended up with nothing to show. */
    private static StudentGraphNodeDto prune(StudentGraphNodeDto category) {
        return category.hasChildren() ? category : null;
    }

    private Map<Integer, String> currencyNames(boolean en) {
        Map<Integer, String> out = new HashMap<>();
        for (var c : currencyRepository.findAll()) {
            String name = en
                    ? firstNonBlank(c.getStudyCurrencyInNameEn(), c.getStudyCurrencyInName())
                    : firstNonBlank(c.getStudyCurrencyInName(), c.getStudyCurrencyInNameEn());
            if (c.getId() != null && name != null) {
                out.put(c.getId(), name);
            }
        }
        return out;
    }

    private static Map<Integer, String> lookupNames(List<? extends AbstractLuLookup> rows, boolean en) {
        Map<Integer, String> out = new HashMap<>();
        for (AbstractLuLookup r : rows) {
            String name = en ? firstNonBlank(r.getNameEn(), r.getNameAr())
                             : firstNonBlank(r.getNameAr(), r.getNameEn());
            if (r.getId() != null && name != null) {
                out.put(r.getId(), name);
            }
        }
        return out;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return (b != null && !b.isBlank()) ? b : null;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String fmt(LocalDate d) {
        return d == null ? null : d.format(DATE_FMT);
    }

    /** Calendar date in the application zone — the one the reports print in (B9). */
    private String fmt(Instant i) {
        return i == null ? null : appTimeZone.toLocalDate(i).format(DATE_FMT);
    }

    /** Assigns stable ids depth-first, so the client can address every node. */
    public static void assignIds(StudentGraphNodeDto root) {
        List<StudentGraphNodeDto> stack = new ArrayList<>();
        stack.add(root);
        int seq = 0;
        while (!stack.isEmpty()) {
            StudentGraphNodeDto n = stack.remove(stack.size() - 1);
            if (n.getId() == null) {
                n.setId("n" + (seq++));
            }
            stack.addAll(n.getChildren());
        }
    }
}
