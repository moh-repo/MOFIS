package com.dilaw.mohe.service;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.model.StudyUniversityFeeArch;
import com.dilaw.mohe.repository.StudyUniversityFeeArchRepository;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fee log (سجل الرسوم) is keyed by its own id, not by the student (review F8).
 *
 * <p>The entity used to map STUDENT_ID as its @Id, so a student's second row was the
 * "same" entity as the first and every insert carried key 0. Runs inside a transaction
 * it rolls back itself, under the SYSTEM tenant scope set before the connection is
 * borrowed; nothing survives in STUDY_UNIVERSITY_FEE_ARCH. Needs V114.
 */
@Tag("integration")
@SpringBootTest
class StudyUniversityFeeArchKeyTest {

    @Autowired private StudyUniversityFeeArchService service;
    @Autowired private StudyUniversityFeeArchRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager txManager;

    private StudyUniversityFeeArch row(int studentId, int countryId, String year) {
        StudyUniversityFeeArch r = new StudyUniversityFeeArch();
        r.setStudyUniversityFeeId(0); // what the old screen sent; the sequence must win
        r.setStudentId(studentId);
        r.setStudyCountryInId(countryId);
        r.setStudyUniversityFeeStudentName("StudyUniversityFeeArchKeyTest - rolled back");
        r.setStudyUniversityFeeYear(year);
        r.setStudyUniversityFeeAmount(100);
        return r;
    }

    @Test
    @DisplayName("two rows for one student get their own keys; edit and delete reach one row each")
    void rowsAreAddressedByTheirOwnKey() {
        TenantContext.runAsSystem(() -> new TransactionTemplate(txManager).execute(status -> {
            status.setRollbackOnly();
            var student = jdbc.queryForMap(
                    "SELECT TOP 1 STUDENT_ID, STUDY_COUNTRY_IN FROM MOHECORE.STUDENT_DATA "
                            + "WHERE STUDY_COUNTRY_IN IS NOT NULL AND STUDY_COUNTRY_IN > 0 ORDER BY STUDENT_ID");
            int studentId = ((Number) student.get("STUDENT_ID")).intValue();
            int countryId = ((Number) student.get("STUDY_COUNTRY_IN")).intValue();

            AsUser.run(AsUser.SUPERADMIN, () -> {
                StudyUniversityFeeArch first = service.create(row(studentId, countryId, "2024/2025"));
                StudyUniversityFeeArch second = service.create(row(studentId, countryId, "2025/2026"));
                repository.flush();

                assertThat(first.getStudyUniversityFeeId()).isNotNull().isPositive();
                assertThat(second.getStudyUniversityFeeId()).isNotEqualTo(first.getStudyUniversityFeeId());

                StudyUniversityFeeArch change = row(studentId, countryId, "2025/2026");
                change.setStudyUniversityFeeAmount(777);
                service.update(second.getStudyUniversityFeeId(), change);
                repository.flush();

                assertThat(service.getById(second.getStudyUniversityFeeId()).getStudyUniversityFeeAmount())
                        .isEqualTo(777);
                assertThat(service.getById(first.getStudyUniversityFeeId()).getStudyUniversityFeeAmount())
                        .as("the student's other row is untouched").isEqualTo(100);

                service.delete(first.getStudyUniversityFeeId());
                repository.flush();

                assertThat(repository.findById(first.getStudyUniversityFeeId())).isEmpty();
                assertThat(repository.findById(second.getStudyUniversityFeeId())).isPresent();
            });
            return null;
        }));
    }
}
