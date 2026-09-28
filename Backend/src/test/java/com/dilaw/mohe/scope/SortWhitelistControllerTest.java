package com.dilaw.mohe.scope;

import com.dilaw.mohe.controler.PaymentAuthController;
import com.dilaw.mohe.controler.StudyUniversityFeeArchController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The sort allow-list on a real list endpoint (plan item 2.4): an unknown
 * {@code sortBy} is a 400 before any query runs, and a listed one still pages.
 *
 * <p>Through the controller bean rather than MockMvc, in the manner of
 * {@link WriteAuthorizationTest}: the point is the exception type and status
 * the handler raises, which is what {@code GlobalExceptionHandler} turns into
 * the response.
 */
@Tag("integration")
@SpringBootTest
class SortWhitelistControllerTest {

    @Autowired private PaymentAuthController paymentAuth;
    @Autowired private StudyUniversityFeeArchController universityFeeArch;

    private static void assert400(Runnable call) {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.ADMIN, List.of("FINANCE.VIEW"), () -> {
            call.run();
            return null;
        }))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("an unknown sortBy is a 400, not a PropertyReferenceException and a 500")
    void unknownSortIs400() {
        assert400(() -> paymentAuth.getAll(0, 5, "createdBy.passwordHash", "asc", null, null));
        assert400(() -> paymentAuth.getAll(0, 5, "no_such_column", "desc", null, null));
        // The old default here was "id", which this entity does not have.
        assert400(() -> universityFeeArch.getAll(0, 5, "id", "desc"));
    }

    @Test
    @DisplayName("positive control: a listed property still pages")
    void listedSortPages() {
        assertThat(AsUser.callWithAuthorities(AsUser.ADMIN, List.of("FINANCE.VIEW"),
                () -> paymentAuth.getAll(0, 5, "authDate", "desc", null, null))).isNotNull();
        assertThat(AsUser.callWithAuthorities(AsUser.ADMIN, List.of("FINANCE.VIEW"),
                () -> universityFeeArch.getAll(0, 5, "studentId", "desc"))).isNotNull();
    }
}
