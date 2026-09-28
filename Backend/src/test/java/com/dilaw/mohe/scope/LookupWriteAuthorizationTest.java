package com.dilaw.mohe.scope;

import com.dilaw.mohe.controler.StudentGenderController;
import com.dilaw.mohe.controler.StudyCountryInController;
import com.dilaw.mohe.controler.StudyFinanceBankController;
import com.dilaw.mohe.model.StudentGender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reference data is read by everyone who has the page and edited by nobody
 * who does not administer settings.
 *
 * <p>Thirty-odd per-table lookup controllers ({@code /studentGender},
 * {@code /studyCountryIn}, {@code /studyFinanceBank}, …) sit under
 * {@code STUDENT_PII} or {@code FINANCE} at class level, which is right for the
 * dropdowns and was, until the 27 Aug 2026 pass (plan item 1.6), the only gate
 * on their POST/PUT/DELETE — so any student-screen user could rename a country.
 * Writes now need {@code SETTINGS_WRITE}, the authority
 * {@code LookupTableController} has always required.
 *
 * <p>Nothing here writes: the positive controls delete an id that does not
 * exist, which the services either refuse (404) or treat as a no-op.
 */
@Tag("integration")
@SpringBootTest
class LookupWriteAuthorizationTest {

    private static final int NO_SUCH_ID = -424242;

    @Autowired private StudentGenderController genders;
    @Autowired private StudyCountryInController countries;
    @Autowired private StudyFinanceBankController banks;

    private static void assertDenied(String what, List<String> authorities, Runnable call) {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities, returning(call)))
                .as("%s with only %s", what, authorities)
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void assertPermitted(String what, List<String> authorities, Runnable call) {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, authorities, returning(call)))
                .as("positive control: %s with %s must get past authorization", what, authorities)
                .isNotInstanceOf(AccessDeniedException.class);
    }

    /** Runs the call and always throws, so a no-op delete is as visible as a 404. */
    private static Supplier<Object> returning(Runnable call) {
        return () -> {
            call.run();
            throw new IllegalStateException("past authorization: returned normally");
        };
    }

    @Test
    @DisplayName("STUDENTS.EDIT reads the student lookups but cannot write them; SETTINGS.EDIT can")
    void studentLookups() {
        List<String> studentEditor = List.of("STUDENTS.VIEW", "STUDENTS.EDIT", "STUDENTS.ADD");
        assertDenied("gender create", studentEditor, () -> genders.create(new StudentGender()));
        assertDenied("gender delete", studentEditor, () -> genders.delete(NO_SUCH_ID));
        assertDenied("country delete", studentEditor, () -> countries.delete(NO_SUCH_ID));
        assertPermitted("gender read", studentEditor, () -> genders.getAll());

        assertPermitted("gender delete", List.of("SETTINGS.EDIT"), () -> genders.delete(NO_SUCH_ID));
        assertPermitted("country delete", List.of("SETTINGS.EDIT"), () -> countries.delete(NO_SUCH_ID));
    }

    @Test
    @DisplayName("FINANCE.EDIT reads the finance lookups but cannot write them; SETTINGS.EDIT can")
    void financeLookups() {
        List<String> financeEditor = List.of("FINANCE.VIEW", "FINANCE.EDIT");
        assertDenied("bank delete", financeEditor, () -> banks.delete(NO_SUCH_ID));
        assertPermitted("bank read", financeEditor, () -> banks.getAll());
        assertPermitted("bank delete", List.of("SETTINGS.EDIT"), () -> banks.delete(NO_SUCH_ID));
    }
}
