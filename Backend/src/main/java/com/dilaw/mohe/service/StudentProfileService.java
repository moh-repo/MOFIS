package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.StudentProgressDto;
import com.dilaw.mohe.model.*;
import com.dilaw.mohe.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Transactional(readOnly = true)
@Service
public class StudentProfileService {

    @Autowired
    private StudentDataService studentDataService;

    @Autowired
    private StudentProgressService studentProgressService;

    @Autowired
    private StudentYearResultService studentYearResultService;

    @Autowired
    private StudentFinanceService studentFinanceService;

    @Autowired
    private StudentFinancePaymentRepository studentFinancePaymentRepository;

    @Autowired
    private StudentFinanceArchRepository studentFinanceArchRepository;

    @Autowired
    private StudentFinanceWafarRepository studentFinanceWafarRepository;

    @Autowired
    private StudentExtensionRepository studentExtensionRepository;

    @Autowired
    private StudentTravelTicketRepository studentTravelTicketRepository;

    @Autowired
    private StudyUniversityFeeRepository studyUniversityFeeRepository;

    @Autowired
    private StudyFinReceivedWhomRepository studyFinReceivedWhomRepository;

    @Autowired
    private StudentLoanRepository studentLoanRepository;

    @Autowired
    private StudentBooksAllowanceRepository studentBooksAllowanceRepository;

    @Autowired
    private StudentFamilyService studentFamilyService;

    @Autowired
    private StudentDeathService studentDeathService;

    /** Aggregated student 360 snapshot for UI /students/profile/:id. */
    public Map<String, Object> buildProfile(Integer studentId) {
        return build(studentId, false);
    }

    /**
     * The same snapshot for a student reading their own record through the portal.
     *
     * <p>The flag selects which authorisation resolves the three pieces this
     * builds on. All three ministry methods gate on the UMS SecurityContext,
     * which a portal student does not have — see
     * {@link StudentDataService#getStudentDataForPortal}. The assembled output is
     * identical either way.
     */
    public Map<String, Object> buildProfileForPortal(Integer studentId) {
        return build(studentId, true);
    }

    private Map<String, Object> build(Integer studentId, boolean portal) {
        if (studentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "student id required");
        }
        StudentDatum student = portal
                ? studentDataService.getStudentDataForPortal(studentId)
                : studentDataService.getStudentData(studentId);
        StudentProgressDto progress = portal
                ? studentProgressService.getProgressForPortal(studentId)
                : studentProgressService.getProgress(studentId);

        StudentFinance finance = null;
        try {
            finance = portal
                    ? studentFinanceService.getByIdForPortal(studentId)
                    : studentFinanceService.getById(studentId);
        } catch (Exception ignored) {
        }

        List<StudentFinancePayment> payments = studentFinancePaymentRepository.findByStudentIdOrderByPaymentIdAsc(studentId);
        List<StudentFinanceArch> financeArch = studentFinanceArchRepository.findByStudentIdOrderByFinanceArchEntryIdAsc(studentId);
        StudentFinanceWafar wafar = studentFinanceWafarRepository.findById(studentId).orElse(null);

        List<StudentExtension> extensions =
                studentExtensionRepository.findByFilters(studentId, null, null, null, null);
        List<StudentTravelTicket> travelTickets =
                studentTravelTicketRepository.findByStudentIdOrderByTravelDateDesc(studentId);
        List<StudyUniversityFee> universityFees =
                studyUniversityFeeRepository.findByStudentId(studentId);
        List<StudyFinReceivedWhom> delegationRecipients =
                studyFinReceivedWhomRepository.findByStudentForDelegationId(studentId);
        List<StudentLoan> loans = studentLoanRepository.findByStudentIdOrderByLoanIdAsc(studentId);
        List<StudentBooksAllowance> books = studentBooksAllowanceRepository.findByStudentIdOrderByBooksAllowIdAsc(studentId);

        int entitled = nz(student.getStudentAmount());
        int receivedMain = finance != null ? nz(finance.getStudentFinRecievedAmount()) : 0;
        int paymentsSum = payments.stream().mapToInt(p -> nz(p.getStudentFinRecievedAmount())).sum();
        int wafarAmt = wafar != null ? nz(wafar.getStudentFinRecievedAmount()) : 0;
        int hintedRemaining = entitled - Math.max(receivedMain, paymentsSum) - wafarAmt;

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("student", student);
        dto.put("progress", progress);
        dto.put("yearResults", studentYearResultService.getByStudentId(studentId));
        dto.put("finance", finance);
        dto.put("financePayments", payments);
        dto.put("financeHistory", financeArch);
        dto.put("wafar", wafar);
        dto.put("extensions", extensions);
        dto.put("travelTickets", travelTickets);
        dto.put("universityFees", universityFees);
        dto.put("delegationRecipients", delegationRecipients);
        dto.put("loans", loans);
        dto.put("booksAllowances", books);
        dto.put("remainingBalanceHint", Math.max(hintedRemaining, 0));
        dto.put("parents", portal
                ? studentFamilyService.getParentsForPortal(studentId)
                : studentFamilyService.getParents(studentId));
        dto.put("relatives", portal
                ? studentFamilyService.getRelativesForPortal(studentId)
                : studentFamilyService.getRelatives(studentId));
        (portal ? studentDeathService.getDeathCaseForPortal(studentId)
                : studentDeathService.getDeathCase(studentId))
                .ifPresent(deathCase -> dto.put("deathCase", deathCase));
        return dto;
    }

    private static int nz(Integer v) {
        return v != null ? v.intValue() : 0;
    }
}
