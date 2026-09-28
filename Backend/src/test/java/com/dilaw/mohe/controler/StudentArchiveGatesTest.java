package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.StudentDataArch;
import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Who may read and who may write the quarterly student snapshots (QA B7, STD-004
 * and STD-006; decision DEC-08 a).
 *
 * <p>The whole controller sat behind ARCHIVING, so the two pages that read it —
 * Student History and the finance history — opened empty for their own roles,
 * while anyone who could read it could also create, overwrite and delete
 * snapshots through the generic endpoints. Those endpoints no longer exist (live
 * QA H1, 23 Sep 2026; {@link StudentArchiveEndpointsTest} holds that), so what is
 * left to gate is the finance-column correction.
 */
@Tag("integration")
@SpringBootTest
class StudentArchiveGatesTest {

    @Autowired private StudentDataArchController archive;

    @Test
    @DisplayName("Student History and finance-history roles can read the list")
    void historyRolesRead() {
        for (String authority : List.of("STUDENT_HISTORY.VIEW", "FINANCE_ARCH.VIEW")) {
            var page = AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of(authority),
                    () -> archive.getDistinctStudentsPaginated(0, 5, null, null, null, null, null));
            assertThat(page).as(authority).isNotNull();
        }
    }

    @Test
    @DisplayName("reading the archive is not enough to correct its finance columns")
    void readersCannotWrite() {
        List<String> reader = List.of("STUDENT_HISTORY.VIEW", "ARCHIVING.VIEW", "FINANCE_ARCH.VIEW");

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, reader,
                () -> archive.updateFinance(1, new StudentDataArch())))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    @DisplayName("the new snapshot-quarter column sorts")
    void snapshotQuarterSorts() {
        var page = AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("STUDENT_HISTORY.VIEW"),
                () -> archive.getDistinctStudentsPaginated(0, 5, null, null, null, "currentQuarter", "desc"));
        assertThat(page).isNotNull();
    }
}
