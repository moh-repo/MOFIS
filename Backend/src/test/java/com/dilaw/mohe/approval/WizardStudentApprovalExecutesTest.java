package com.dilaw.mohe.approval;

import com.dilaw.mohe.approval.event.ApprovalExecutionEvent;
import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.RequestType;
import com.dilaw.mohe.approval.service.ApprovalIntegrationService;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudentFinancePayment;
import com.dilaw.mohe.scope.AsUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The last sign-off on a student added through the wizard writes the student, their
 * finance row and their first payment row.
 *
 * <p>Until 22 Sep 2026 it wrote none of them: the payment row went out with NULL in
 * STUDY_FIN_RECIEVED_STATUS (and the bank, branch and received-by columns after it),
 * all NOT NULL, because the onboarding draft carries none of them yet and the table
 * default of 0 never fires for a column the INSERT names. The request stayed
 * "in progress 3/3" and the approver saw the SQL (live QA, request 20868).
 *
 * <p>Fixing that exposed a second fault behind it: the finance seed "probed" with
 * {@code SET IDENTITY_INSERT MOHECORE.STUDENT_FINANCE ON}, which the application login
 * {@code mohe_app} may not run. Error 1088 aborts the batch and rolls back the whole
 * transaction — the new student with it — while the code caught the exception and
 * carried on in a fresh implicit transaction. The student-row assertion catches that.
 *
 * <p>The payload below is what the Add Student wizard sends: the student, and a finance
 * draft whose status is 0. Everything runs in one transaction that is rolled back, so
 * nothing survives, and the after-commit chaining of the finance request never fires.
 * Needs the tests to run as {@code mohe_app}, as the dev profile does.
 */
@Tag("integration")
@SpringBootTest
class WizardStudentApprovalExecutesTest {

    private static final String MARKER = "ZT-wizard-approval WizardStudentApprovalExecutesTest";

    @Autowired private ApprovalIntegrationService integration;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;
    @PersistenceContext private EntityManager em;

    private static String wizardPayload(int countryId) {
        return """
                {"studentData":{"studentId":null,"studentName":"%s","studyCountryIn":%d,
                  "studentAmount":1500,"studentStudyNoOfYears":4,"studentStudyStatusId":1},
                 "linkedFinanceDraft":{"studyCountryInId":%d,"studyGovInId":0,
                  "studentFinRecievedAmount":1500,"studentFinRecievedYear":2024,
                  "studentFinRecievedMonth":0,"studentFinRecievedYearMonth":0,
                  "studyFinRecievedStatus":0,"studentFinName":"%s"},
                 "pendingUploadSessionId":null}
                """.formatted(MARKER, countryId, countryId, MARKER);
    }

    @Test
    @DisplayName("an approved wizard student is created with finance and payment rows")
    void approvedWizardStudentIsWritten() {
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            int countryId = jdbc.queryForObject(
                    "SELECT TOP 1 STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA "
                            + "WHERE STUDY_COUNTRY_IN IS NOT NULL AND STUDY_COUNTRY_IN > 0 ORDER BY STUDENT_ID",
                    Integer.class);

            ApprovalRequest request = new ApprovalRequest();
            request.setId(1L);
            request.setEntityType("STUDENT_DATA");
            request.setRequestType(RequestType.INSERT);
            request.setRequestData(wizardPayload(countryId));
            request.setRequestedBy(1L);
            request.setCountryId(countryId);

            AsUser.run(AsUser.SUPERADMIN, () -> {
                integration.handleApprovalExecution(new ApprovalExecutionEvent(this, request, 1L));

                List<Integer> ids = em.createQuery(
                                "select s.studentId from StudentDatum s where s.studentName = :n", Integer.class)
                        .setParameter("n", MARKER).getResultList();
                // Also the guard for the silent rollback: a batch-aborting error anywhere in
                // the execution (SET IDENTITY_INSERT as mohe_app did) rolls the transaction
                // back and the student vanishes, while later statements carry on regardless.
                assertThat(ids).as("the student row, still there at the end of the execution").hasSize(1);
                int sid = ids.get(0);

                assertThat(em.createQuery("select count(f) from StudentFinance f where f.id = :sid", Long.class)
                        .setParameter("sid", sid).getSingleResult())
                        .as("the finance row").isEqualTo(1L);

                List<StudentFinancePayment> payments = em.createQuery(
                                "select p from StudentFinancePayment p where p.studentId = :sid",
                                StudentFinancePayment.class)
                        .setParameter("sid", sid).getResultList();
                assertThat(payments).as("the first payment row").hasSize(1);
                StudentFinancePayment payment = payments.get(0);
                assertThat(payment.getStudyFinRecievedStatus())
                        .as("no status yet is 0, the table's own default").isZero();
                assertThat(payment.getStudyFinanceBankeId()).isZero();
                assertThat(payment.getStudyReceivedWhomId()).isZero();
                assertThat(payment.getStudentFinRecievedAmount()).isEqualTo(1500);
            });
            return null;
        }));
    }

    @Test
    @DisplayName("the parents typed into the wizard are written when the student is approved")
    void wizardParentsAreWrittenOnApproval() {
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            int countryId = jdbc.queryForObject(
                    "SELECT TOP 1 STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA "
                            + "WHERE STUDY_COUNTRY_IN IS NOT NULL AND STUDY_COUNTRY_IN > 0 ORDER BY STUDENT_ID",
                    Integer.class);
            int relationshipId = jdbc.queryForObject(
                    "SELECT TOP 1 REL_ID FROM MOHECORE.LU_FAMILY_RELATIONSHIP ORDER BY SORT_ORDER", Integer.class);

            // Before 22 Sep the wizard saved parents only after it had a student id, which
            // a new student does not have until approval: they were silently dropped.
            String payload = wizardPayload(countryId).replace("\"pendingUploadSessionId\":null",
                    "\"pendingUploadSessionId\":null,\"parents\":[{\"relationshipId\":%d,\"parentName\":\"ZT parent\"}]"
                            .formatted(relationshipId));

            ApprovalRequest request = new ApprovalRequest();
            request.setId(1L);
            request.setEntityType("STUDENT_DATA");
            request.setRequestType(RequestType.INSERT);
            request.setRequestData(payload);
            request.setRequestedBy(1L);
            request.setCountryId(countryId);

            AsUser.run(AsUser.SUPERADMIN, () -> {
                integration.handleApprovalExecution(new ApprovalExecutionEvent(this, request, 1L));

                Integer sid = em.createQuery(
                                "select s.studentId from StudentDatum s where s.studentName = :n", Integer.class)
                        .setParameter("n", MARKER).getSingleResult();
                List<String> parents = em.createQuery(
                                "select p.parentName from StudentParent p where p.studentId = :sid and p.isDeleted = false",
                                String.class)
                        .setParameter("sid", sid).getResultList();
                assertThat(parents).containsExactly("ZT parent");
            });
            return null;
        }));
    }
}
