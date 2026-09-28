package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.FeeTypeCode;
import com.dilaw.mohe.codes.ReportLabelCode;
import com.dilaw.mohe.config.AppTimeZone;
import com.dilaw.mohe.dto.StudentEntitlementRowDto;
import com.dilaw.mohe.dto.StudentEntitlementsMetadataDto;
import com.dilaw.mohe.model.StudentBooksAllowance;
import com.dilaw.mohe.model.StudentLoan;
import com.dilaw.mohe.model.StudentTravelTicket;
import com.dilaw.mohe.repository.LuBooksAllowancePayStatusRepository;
import com.dilaw.mohe.repository.LuLoanStatusRepository;
import com.dilaw.mohe.model.StudyCurrencyIn;
import com.dilaw.mohe.repository.StudentDataRepository;
import com.dilaw.mohe.repository.StudyCurrencyInRepository;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.ReportCountryScopeHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything a student has been granted, in one statement.
 *
 * <p>Loans, books allowance and travel tickets are three tables with three small
 * screens and no report between them. Nobody asks "show me the loans table" —
 * they ask what a student, or an attaché, has received. So the three are read
 * through their own scoped services and flattened into one line shape.
 *
 * <p><b>The three sources are read, never re-queried.</b>
 * {@code StudentLoanService.getAll}, {@code StudentBooksAllowanceService.getAll}
 * and {@code StudentTravelTicketService.getAll} already apply
 * {@code StudentIdCountrySpec} / {@code StudentTravelSpec}, which resolve a
 * student's country through a correlated subquery. Rebuilding that here would be
 * a second copy of the rule to keep in step.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class StudentEntitlementsReportService {

    public static final String KIND_LOAN = "LOAN";
    public static final String KIND_BOOKS = "BOOKS";
    public static final String KIND_TRAVEL = "TRAVEL";

    /**
     * The FINCORE.FEE_TYPE row each kind is named by. Loans have no fee type —
     * FEE_TYPE's SOURCE_TABLE column names STUDENT_FINANCE, STUDENT_BOOKS_ALLOWANCE,
     * STUDENT_TRAVEL_TICKET and STUDY_UNIVERSITY_FEE, never STUDENT_LOAN — so
     * {@link #KIND_LOAN} is named by LU_REPORT_LABEL.LBL_KIND_LOAN (V077) until a
     * loan fee type exists; add it here when it does.
     */
    private static final Map<String, String> KIND_FEE_TYPE = Map.of(
            KIND_BOOKS, FeeTypeCode.BOOKS,
            KIND_TRAVEL, FeeTypeCode.TRAVEL);

    private final StudentLoanService loanService;
    private final StudentBooksAllowanceService booksService;
    private final StudentTravelTicketService travelService;
    private final StudentDataRepository studentDataRepository;
    private final CountryNameService countryNameService;
    private final StudyCurrencyInRepository studyCurrencyInRepository;
    private final LuLoanStatusRepository loanStatusRepository;
    private final LuBooksAllowancePayStatusRepository booksPayStatusRepository;
    private final ReportCountryScopeHelper countryScopeHelper;
    private final CountryScopeService countryScopeService;
    private final AppTimeZone appTimeZone;
    private final CodeListService codeLists;

    public StudentEntitlementsMetadataDto metadata(String lang) {
        return StudentEntitlementsMetadataDto.builder()
                .kinds(List.of(
                        StudentEntitlementsMetadataDto.KindOption.builder()
                                .code(KIND_LOAN).name(kindName(KIND_LOAN, lang)).build(),
                        StudentEntitlementsMetadataDto.KindOption.builder()
                                .code(KIND_BOOKS).name(kindName(KIND_BOOKS, lang)).build(),
                        StudentEntitlementsMetadataDto.KindOption.builder()
                                .code(KIND_TRAVEL).name(kindName(KIND_TRAVEL, lang)).build()))
                .countries(visibleCountries(lang))
                .build();
    }

    /** The name of a kind in a language: the FEE_TYPE row's, or LBL_KIND_LOAN for loans. */
    private String kindName(String kind, String lang) {
        String l = isArabic(lang) ? "ar" : "en";
        String feeType = KIND_FEE_TYPE.get(kind);
        if (feeType != null) {
            return codeLists.label("fee-type", feeType, l);
        }
        return codeLists.label("report-label", ReportLabelCode.LBL_KIND_LOAN, l);
    }

    public List<StudentEntitlementRowDto> rows(Integer countryId, List<Integer> countryIds,
                                               String kind, Integer studentId, String lang) {
        List<Integer> resolved = countryScopeHelper.resolveCountryIds(countryId, countryIds);

        // The three sources first, so the student lookup can be restricted to the
        // students that actually appear on the report.
        List<StudentLoan> loans = kind == null || KIND_LOAN.equalsIgnoreCase(kind) ? loanService.getAll() : List.of();
        List<StudentBooksAllowance> booksRows =
                kind == null || KIND_BOOKS.equalsIgnoreCase(kind) ? booksService.getAll() : List.of();
        List<StudentTravelTicket> tickets =
                kind == null || KIND_TRAVEL.equalsIgnoreCase(kind) ? travelService.getAll() : List.of();

        Set<Integer> reportStudentIds = new LinkedHashSet<>();
        loans.forEach(l -> reportStudentIds.add(l.getStudentId()));
        booksRows.forEach(b -> reportStudentIds.add(b.getStudentId()));
        tickets.forEach(t -> reportStudentIds.add(t.getStudentId()));
        reportStudentIds.remove(null);

        Map<Integer, StudentDataRepository.NameAndCountry> students = studentIndex(reportStudentIds);
        Map<Integer, String> countryNames = countryNameMap(lang);
        Map<Integer, String> currencyNames = currencyNameMap(lang);
        /*
         * Status is a lookup id on both loans and books allowance. Printing "2"
         * is the RAW_ID defect the report audit exists to catch — it only misses
         * it here because the column is not named *Id.
         */
        Map<Integer, String> loanStatuses = lookupNames(loanStatusRepository.findAll(), lang);
        Map<Integer, String> booksStatuses = lookupNames(booksPayStatusRepository.findAll(), lang);
        Map<Integer, String> loanStatusCodes = lookupCodes(loanStatusRepository.findAll());
        Map<Integer, String> booksStatusCodes = lookupCodes(booksPayStatusRepository.findAll());
        // Resolved once per report, never per row.
        String loanKind = kindName(KIND_LOAN, lang);
        String booksKind = kindName(KIND_BOOKS, lang);
        String travelKind = kindName(KIND_TRAVEL, lang);

        List<StudentEntitlementRowDto> out = new ArrayList<>();

        {
            for (StudentLoan loan : loans) {
                out.add(StudentEntitlementRowDto.builder()
                        .kind(loanKind)
                        .studentId(loan.getStudentId())
                        .studentName(nameOf(students, loan.getStudentId()))
                        .studyCountryInId(countryOf(students, loan.getStudentId()))
                        .studyCountryName(countryNames.get(countryOf(students, loan.getStudentId())))
                        .studyYear(loan.getStudyYear())
                        .entitlementDate(toLocalDate(loan.getMidate()))
                        .amount(loan.getLoanAmount() == null ? null : BigDecimal.valueOf(loan.getLoanAmount()))
                        .currencyName(currencyNames.get(loan.getCurrencyId()))
                        .status(loanStatuses.get(loan.getLoanStatus()))
                        .statusCode(loanStatusCodes.get(loan.getLoanStatus()))
                        .detail(loan.getRepaymentNotes())
                        .build());
            }
        }

        {
            for (StudentBooksAllowance books : booksRows) {
                out.add(StudentEntitlementRowDto.builder()
                        .kind(booksKind)
                        .studentId(books.getStudentId())
                        .studentName(nameOf(students, books.getStudentId()))
                        .studyCountryInId(countryOf(students, books.getStudentId()))
                        .studyCountryName(countryNames.get(countryOf(students, books.getStudentId())))
                        .studyYear(books.getStudyYear())
                        .entitlementDate(toLocalDate(books.getReceiptDate() != null
                                ? books.getReceiptDate() : books.getMidate()))
                        .amount(books.getAllowanceAmount() == null
                                ? null : BigDecimal.valueOf(books.getAllowanceAmount()))
                        .currencyName(null)
                        .status(booksStatuses.get(books.getPayStatus()))
                        .statusCode(booksStatusCodes.get(books.getPayStatus()))
                        .detail(books.getNotes())
                        .build());
            }
        }

        {
            for (StudentTravelTicket ticket : tickets) {
                Integer sid = ticket.getStudentId();
                out.add(StudentEntitlementRowDto.builder()
                        .kind(travelKind)
                        .studentId(sid)
                        .studentName(nameOf(students, sid))
                        .studyCountryInId(countryOf(students, sid))
                        .studyCountryName(countryNames.get(countryOf(students, sid)))
                        .studyYear(null)
                        .entitlementDate(ticket.getTravelDate())
                        .amount(ticket.getTotalTicketCost() != null
                                ? ticket.getTotalTicketCost() : ticket.getTicketCost())
                        .currencyName(null)
                        .status(ticket.getTravelType())
                        .statusCode(ticket.getTravelType())
                        .detail(route(ticket))
                        .build());
            }
        }

        return out.stream()
                .filter(r -> studentId == null || studentId.equals(r.getStudentId()))
                .filter(r -> matchesCountry(r, resolved))
                .sorted(Comparator
                        .comparing(StudentEntitlementRowDto::getStudentName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(StudentEntitlementRowDto::getEntitlementDate,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Totals per kind, for the report footer and the screen's tiles. */
    public Map<String, BigDecimal> totalsByKind(List<StudentEntitlementRowDto> rows) {
        return rows.stream()
                .filter(r -> r.getKind() != null)
                .collect(Collectors.groupingBy(StudentEntitlementRowDto::getKind,
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> Objects.requireNonNullElse(r.getAmount(), BigDecimal.ZERO),
                                BigDecimal::add)));
    }

    private String route(StudentTravelTicket t) {
        String from = t.getDepartureCity() != null ? t.getDepartureCity() : t.getDepartureCountry();
        String to = t.getDestinationCity() != null ? t.getDestinationCity() : t.getDestinationCountry();
        if (from == null && to == null) {
            return t.getAirline();
        }
        return (from == null ? "?" : from) + " → " + (to == null ? "?" : to);
    }

    /**
     * A row whose student has no country is not any attaché's, so narrowing to a
     * country drops it — the same rule the rest of the system applies to a
     * missing country on an owned record.
     */
    private boolean matchesCountry(StudentEntitlementRowDto row, List<Integer> resolved) {
        if (resolved == null) {
            return true;
        }
        return row.getStudyCountryInId() != null && resolved.contains(row.getStudyCountryInId());
    }

    /**
     * Name and country for the report's students only, two columns, in batches
     * that stay under SQL Server's parameter limit.
     *
     * <p>This was {@code findAll()} on STUDENT_DATA: every column of every student
     * loaded as entities to read a name and a country for the few hundred that
     * appear on the report.
     */
    private Map<Integer, StudentDataRepository.NameAndCountry> studentIndex(Set<Integer> ids) {
        Map<Integer, StudentDataRepository.NameAndCountry> out = new LinkedHashMap<>();
        List<Integer> all = new ArrayList<>(ids);
        for (int from = 0; from < all.size(); from += ID_BATCH) {
            List<Integer> batch = all.subList(from, Math.min(from + ID_BATCH, all.size()));
            for (StudentDataRepository.NameAndCountry s : studentDataRepository.findByStudentIdIn(batch)) {
                if (s.getStudentId() != null) {
                    out.putIfAbsent(s.getStudentId(), s);
                }
            }
        }
        return out;
    }

    /** Below SQL Server's 2100-parameter limit for an {@code IN} list, with room to spare. */
    private static final int ID_BATCH = 1000;

    private String nameOf(Map<Integer, StudentDataRepository.NameAndCountry> students, Integer studentId) {
        StudentDataRepository.NameAndCountry s = studentId == null ? null : students.get(studentId);
        return s == null ? null : s.getStudentName();
    }

    private Integer countryOf(Map<Integer, StudentDataRepository.NameAndCountry> students, Integer studentId) {
        StudentDataRepository.NameAndCountry s = studentId == null ? null : students.get(studentId);
        return s == null ? null : s.getStudyCountryIn();
    }

    /** Calendar date in the application zone — the one the reports print in (B9). */
    private LocalDate toLocalDate(java.time.Instant instant) {
        return appTimeZone.toLocalDate(instant);
    }

    private List<StudentEntitlementsMetadataDto.CountryOption> visibleCountries(String lang) {
        var scope = countryScopeService.getScope();
        return countryNameService.all().stream()
                .filter(c -> scope.isMinistryUser() || scope.getAllowedCountryIds().contains(c.id()))
                .map(c -> StudentEntitlementsMetadataDto.CountryOption.builder()
                        .id(c.id())
                        .name(pick(isArabic(lang), c.nameAr(), c.nameEn()))
                        .build())
                .sorted(Comparator.comparing(StudentEntitlementsMetadataDto.CountryOption::getName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Map<Integer, String> countryNameMap(String lang) {
        return countryNameService.all().stream()
                .collect(Collectors.toMap(CountryNameService.CountryRef::id,
                        c -> pick(isArabic(lang), c.nameAr(), c.nameEn()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Integer, String> currencyNameMap(String lang) {
        return studyCurrencyInRepository.findAll().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(StudyCurrencyIn::getId,
                        c -> pick(isArabic(lang), c.getStudyCurrencyInName(), c.getStudyCurrencyInNameEn()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Lookup id to CODE — the key the screen colours its tag by. */
    private Map<Integer, String> lookupCodes(List<? extends com.dilaw.mohe.model.AbstractLuLookup> rows) {
        return rows.stream()
                .filter(r -> r.getId() != null && r.getCode() != null)
                .collect(Collectors.toMap(com.dilaw.mohe.model.AbstractLuLookup::getId,
                        com.dilaw.mohe.model.AbstractLuLookup::getCode, (a, b) -> a, LinkedHashMap::new));
    }

    /** Lookup id to name, so no report cell can print a bare foreign key. */
    private Map<Integer, String> lookupNames(List<? extends com.dilaw.mohe.model.AbstractLuLookup> rows, String lang) {
        return rows.stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(
                        com.dilaw.mohe.model.AbstractLuLookup::getId,
                        r -> pick(isArabic(lang), r.getNameAr(), r.getNameEn()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private static boolean isArabic(String lang) {
        return lang == null || "ar".equalsIgnoreCase(lang);
    }

    private static String pick(boolean arabic, String ar, String en) {
        String first = arabic ? ar : en;
        String second = arabic ? en : ar;
        return first != null && !first.isBlank() ? first : second;
    }
}
