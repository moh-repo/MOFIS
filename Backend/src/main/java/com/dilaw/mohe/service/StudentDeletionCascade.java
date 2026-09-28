package com.dilaw.mohe.service;

import com.dilaw.mohe.codes.PortalAccountStatus;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.model.StudentFinancePayment;
import com.dilaw.mohe.repository.StudentFinancePaymentRepository;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import com.dilaw.mohe.studentportal.repository.StudentSessionRepository;
import com.dilaw.mohe.studentportal.repository.StudentUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * What else goes when a student is deleted (live QA H7, decision D5-a).
 *
 * <p>A student delete is a soft delete ({@code IS_DELETED}); nothing else was
 * touched, so the student's finance row stayed and was counted by the finance
 * list, the dashboard trend and every finance view (student 10116, 24 Sep 2026).
 * Now, in the delete's own transaction:
 * <ul>
 *   <li>the STUDENT_FINANCE row and its STUDENT_FINANCE_PAYMENT rows are deleted
 *       (the finance audit trigger keeps a copy of the row);</li>
 *   <li>the portal account is set INACTIVE and its sessions end.</li>
 * </ul>
 * The other child records (loans, allowances, extensions, received-by, fees,
 * tickets, Wafar deposits, the finance archive) stay as history; reports exclude
 * them through the deleted student, except the finance archive, which is the record
 * of what was paid and is read whole (FIX-PLAN P5-11). The finance row itself is
 * archived before it is removed.
 */
@Component
public class StudentDeletionCascade {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(StudentDeletionCascade.class);

    @Autowired private StudentFinanceRepository financeRepository;
    @Autowired private StudentFinancePaymentRepository paymentRepository;
    @Autowired private StudentUserRepository studentUserRepository;
    @Autowired private StudentSessionRepository sessionRepository;
    @Autowired private StudentFinanceArchiveService financeArchive;

    @Transactional(propagation = Propagation.MANDATORY)
    public void onStudentDeleted(Integer studentId) {
        List<StudentFinancePayment> payments = paymentRepository.findByStudentIdOrderByPaymentIdAsc(studentId);
        paymentRepository.deleteAll(payments);
        // The finance row goes to the archive first (decision of 26 Sep 2026, FIX-PLAN P5-11:
        // "they must be archived even if they are deleted"); the archive is filled at quarter
        // close, so a student deleted mid-quarter lost that quarter from it. Same upsert as the
        // quarter close, keyed by student, year and quarter; a row that cannot be keyed is left
        // to the audit trail's copy.
        StudentFinance finance = financeRepository.findById(studentId).orElse(null);
        boolean hadFinance = finance != null;
        boolean archived = false;
        if (hadFinance) {
            if (archivable(finance)) {
                financeArchive.archive(finance, finance.getMoheQuarterId());
                archived = true;
            } else {
                log.warn("Student {} deleted: finance row not archived (received year {} / quarter {} cannot key the archive)",
                        studentId, finance.getStudentFinRecievedYear(), finance.getStudentFinRecievedMonth());
            }
            financeRepository.delete(finance);
        }

        studentUserRepository.findByStudentIdAndIsDeletedFalse(studentId).ifPresent(u -> {
            u.setStatus(PortalAccountStatus.INACTIVE);
            u.setUpdatedAt(Instant.now());
            studentUserRepository.save(u);
            sessionRepository.deleteByStudentUserId(u.getStudentUserId());
        });
        log.info("Student {} deleted: finance row {}, {} payment row(s) removed; portal account deactivated if any",
                studentId, hadFinance ? (archived ? "archived and removed" : "removed") : "absent", payments.size());
    }

    /**
     * A row the archive can key and hold: a real received year (the finance rule's 1990-2100),
     * a quarter, and the two other columns the archive requires (STUDENT_FIN_NAME and
     * STUDENT_FIN_RECIEVED_YEAR_MONTH are NOT NULL there). A row that fails this is not
     * archived, so the insert can never fail and take the student's delete with it.
     */
    static boolean archivable(StudentFinance f) {
        Integer year = f.getStudentFinRecievedYear();
        Integer quarter = f.getStudentFinRecievedMonth();
        return year != null && year >= 1990 && year <= 2100 && quarter != null && quarter >= 1 && quarter <= 4
                && f.getStudentFinRecievedYearMonth() != null && f.getStudentFinName() != null;
    }
}
