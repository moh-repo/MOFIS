package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.repository.StudentDataRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression test for the change-request approval failure:
 *
 * <pre>
 *   The UPDATE statement conflicted with the FOREIGN KEY constraint
 *   "FK_STUDENT_DATA_GOV" ... column 'STUDENT_GOV_ID'
 * </pre>
 *
 * <p>The request being approved only changed a passport number, but Hibernate
 * emitted a full 50-column UPDATE, which re-sent the row's stale
 * STUDENT_GOV_ID and made SQL Server re-validate an untrusted foreign key the
 * edit never touched. {@code @DynamicUpdate} on {@link StudentDatum} limits the
 * statement to changed columns.
 *
 * <p>Runs inside a transaction that is rolled back, so the database is left as
 * found.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class StudentDataPartialUpdateTest {

    @Autowired
    private StudentDataRepository studentDataRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("the orphaned-FK scenario is impossible by construction, and a one-field edit stays a one-field edit")
    void editingOneField_doesNotTouchUnrelatedForeignKeys() {
        /*
         * Until the third revision pass (27 Aug 2026) this test looked for a student
         * with an orphaned STUDENT_GOV_ID and assumed itself away when V019 had
         * already repointed them — which was every run since V019, so the suite
         * reported it as skipped for ever. The defect cannot recur from data alone:
         * V019 removed the orphans and FK_STUDENT_DATA_GOV is enabled and trusted,
         * so SQL Server refuses a new one. That is asserted first; the second half
         * then checks the mechanism that fixed the original failure — the
         * single-column UPDATE from @DynamicUpdate — on an ordinary student.
         */
        Number orphans = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM MOHECORE.STUDENT_DATA sd
                        WHERE sd.STUDENT_GOV_ID IS NOT NULL
                          AND NOT EXISTS (SELECT 1 FROM MOHECORE.STUDENT_GOV g
                                          WHERE g.STUDENT_GOV_ID = sd.STUDENT_GOV_ID)
                        """).getSingleResult();
        assertThat(orphans.intValue())
                .as("V019 repointed every orphaned STUDENT_GOV_ID; none may come back")
                .isZero();

        Number fkTrusted = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*) FROM sys.foreign_keys
                        WHERE name = 'FK_STUDENT_DATA_GOV' AND is_disabled = 0 AND is_not_trusted = 0
                        """).getSingleResult();
        assertThat(fkTrusted.intValue())
                .as("FK_STUDENT_DATA_GOV must be enabled and trusted, or an orphan could be inserted again")
                .isEqualTo(1);

        List<?> anyStudent = entityManager.createNativeQuery(
                "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA WHERE STUDENT_GOV_ID IS NOT NULL ORDER BY STUDENT_ID")
                .getResultList();
        assumeTrue(!anyStudent.isEmpty(), "no student seeded");
        Integer studentId = ((Number) anyStudent.get(0)).intValue();

        StudentDatum student = studentDataRepository.findById(studentId).orElseThrow();
        String before = student.getStudentPassportNumber();
        student.setStudentPassportNumber("75757657");

        assertThatCode(() -> studentDataRepository.saveAndFlush(student))
                .as("a single-field edit must not fail on a foreign key it never touched")
                .doesNotThrowAnyException();
        entityManager.clear();

        assertThat(studentDataRepository.findById(studentId).orElseThrow().getStudentPassportNumber())
                .as("rolled back with the test transaction; the value before was " + before)
                .isEqualTo("75757657");
    }
}
