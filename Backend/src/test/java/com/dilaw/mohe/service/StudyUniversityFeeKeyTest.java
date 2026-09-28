package com.dilaw.mohe.service;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudyUniversityFee;
import com.dilaw.mohe.repository.StudyUniversityFeeRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A new university fee gets a key (plan W4-10, V128). The key column is a plain INT
 * and the entity mapped it as IDENTITY, so every save failed with "null identifier"
 * — an approved "add fee" request included. Rolled back; the sequence it draws from
 * moves on regardless, which is harmless.
 */
@Tag("integration")
@SpringBootTest
class StudyUniversityFeeKeyTest {

    @Autowired private StudyUniversityFeeRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private static StudyUniversityFee fee(int studentId, String checkNo) {
        StudyUniversityFee f = new StudyUniversityFee();
        f.setStudentId(studentId);
        f.setStudyUniversityFeeStudentName("StudyUniversityFeeKeyTest - rolled back");
        f.setStudyUniversityCheckNo(checkNo);
        f.setStudyUniversityFeeYear("2025/2026");
        f.setStudyUniversityFeeAmount(100);
        f.setStudyUniversityFeeDiscount(0);
        f.setStudyUniversityRealFeeAmount(100);
        f.setStudyUniversityFeeSemesterNumber(1);
        f.setStudyUniversityFeeSemesterScopeId(1);
        f.setStudyUniversityFeeYearScopeId(1);
        f.setStudyUniversityFeeSourceId(1);
        f.setStudyUniversityCheckPayorTypeId(1);
        f.setStudyUniversityFeeMidate(Instant.now());
        return f;
    }

    @Test
    @DisplayName("two new fees are saved with keys of their own")
    void newFeesGetKeys() {
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            int studentId = jdbc.queryForObject(
                    "SELECT TOP 1 STUDENT_ID FROM MOHECORE.STUDENT_DATA ORDER BY STUDENT_ID", Integer.class);
            AsUser.run(AsUser.SUPERADMIN, () -> {
                StudyUniversityFee first = repository.saveAndFlush(fee(studentId, "W410-A"));
                StudyUniversityFee second = repository.saveAndFlush(fee(studentId, "W410-B"));

                assertThat(first.getId()).isNotNull().isPositive();
                assertThat(second.getId()).isNotNull().isNotEqualTo(first.getId());
                Integer max = jdbc.queryForObject(
                        "SELECT MAX(STUDY_UNIVERSITY_FEE_ID) FROM MOHECORE.STUDY_UNIVERSITY_FEE", Integer.class);
                assertThat(second.getId()).as("no existing key is reused").isEqualTo(max);
            });
            return null;
        }));
    }
}
