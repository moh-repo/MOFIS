package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.ums.model.UmsAccessReview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S-04: reviews open per attaché and for the ministry; the right administrator
 * attests; REMOVE disables the account; overdue items are flagged. Rolled back.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class AccessReviewTest {

    @Autowired private AccessReviewService reviews;
    @Autowired private AccessReviewJob job;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager em;

    private static final int JORDAN = 1004;
    private static final String PERIOD = "2099-Q1";

    @Test
    @DisplayName("opening creates one review per attaché and one for the ministry, each with its accounts' snapshots")
    void opening() {
        List<UmsAccessReview> created = AsUser.call(AsUser.ADMIN, () -> reviews.open(PERIOD, AsUser.ADMIN));
        assertThat(created).isNotEmpty();
        UmsAccessReview jordan = created.stream().filter(r -> Integer.valueOf(JORDAN).equals(r.getCountryId())).findFirst().orElseThrow();
        UmsAccessReview ministry = created.stream().filter(r -> r.getCountryId() == null).findFirst().orElseThrow();
        assertThat(jordan.getDueAt()).isAfter(LocalDateTime.now().plusDays(29));

        List<AccessReviewService.ItemView> jordanItems = AsUser.call(AsUser.JO_ADMIN, () -> reviews.items(jordan.getId()));
        assertThat(jordanItems).extracting(AccessReviewService.ItemView::userId).contains(AsUser.JO_ADMIN, 14L);
        AccessReviewService.ItemView staff = jordanItems.stream().filter(i -> i.userId() == 14L).findFirst().orElseThrow();
        assertThat(staff.snapshot().userName()).isEqualTo("jo_staff");
        assertThat(staff.snapshot().countries()).isNotEmpty();
        assertThat(staff.snapshot().tier()).isEqualTo("COUNTRY_STAFF");

        List<AccessReviewService.ItemView> ministryItems = AsUser.call(AsUser.ADMIN, () -> reviews.items(ministry.getId()));
        assertThat(ministryItems).extracting(AccessReviewService.ItemView::userId).contains(AsUser.SUPERADMIN, AsUser.ADMIN);

        // Opening the same period again adds nothing.
        assertThat(AsUser.call(AsUser.ADMIN, () -> reviews.open(PERIOD, AsUser.ADMIN))).isEmpty();
        assertThat(reviews.periodOf(LocalDate.of(2026, 9, 6))).isEqualTo("2026-Q3");
    }

    @Test
    @DisplayName("the attaché's administrator attests its list; the ministry admin and another attaché do not")
    void whoAttests() {
        AsUser.call(AsUser.ADMIN, () -> reviews.open(PERIOD, AsUser.ADMIN));
        Long jordanReview = jdbc.queryForObject("SELECT Id FROM UMSCORE.AccessReviews WHERE Period = ? AND CountryId = ?", Long.class, PERIOD, JORDAN);
        Long ministryReview = jdbc.queryForObject("SELECT Id FROM UMSCORE.AccessReviews WHERE Period = ? AND CountryId IS NULL", Long.class, PERIOD);

        assertThat(AsUser.call(AsUser.JO_ADMIN, () -> reviews.visible())).extracting(AccessReviewService.ReviewView::id).contains(jordanReview).doesNotContain(ministryReview);
        assertThat(AsUser.call(AsUser.ADMIN, () -> reviews.visible())).extracting(AccessReviewService.ReviewView::id).contains(jordanReview, ministryReview);

        assertThatThrownBy(() -> AsUser.call(AsUser.SA_ADMIN, () -> reviews.items(jordanReview)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> AsUser.call(AsUser.ADMIN, () -> reviews.items(jordanReview)))
                .as("the ministry admin reads the list but the attaché attests it").isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> reviews.items(ministryReview)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(AsUser.call(AsUser.SUPERADMIN, () -> reviews.items(jordanReview))).isNotEmpty();
    }

    @Test
    @DisplayName("KEEP is recorded, REMOVE disables the account, one's own account cannot be removed, close needs every decision")
    void decisions() {
        AsUser.call(AsUser.ADMIN, () -> reviews.open(PERIOD, AsUser.ADMIN));
        Long jordanReview = jdbc.queryForObject("SELECT Id FROM UMSCORE.AccessReviews WHERE Period = ? AND CountryId = ?", Long.class, PERIOD, JORDAN);
        List<AccessReviewService.ItemView> items = AsUser.call(AsUser.JO_ADMIN, () -> reviews.items(jordanReview));
        AccessReviewService.ItemView self = items.stream().filter(i -> i.userId() == AsUser.JO_ADMIN).findFirst().orElseThrow();
        AccessReviewService.ItemView staff = items.stream().filter(i -> i.userId() == 14L).findFirst().orElseThrow();

        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> reviews.close(jordanReview, AsUser.JO_ADMIN)))
                .hasMessageContaining("still need a decision");
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> reviews.decide(jordanReview, self.id(), "REMOVE", null, AsUser.JO_ADMIN)))
                .hasMessageContaining("own account");
        assertThatThrownBy(() -> AsUser.call(AsUser.JO_ADMIN, () -> reviews.decide(jordanReview, staff.id(), "MAYBE", null, AsUser.JO_ADMIN)))
                .hasMessageContaining("KEEP, CHANGE or REMOVE");

        AsUser.call(AsUser.JO_ADMIN, () -> reviews.decide(jordanReview, staff.id(), "REMOVE", "left the attaché", AsUser.JO_ADMIN));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT Status FROM UMSCORE.Users WHERE Id = 14", String.class)).isEqualTo(UserStatus.INACTIVE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE [Action] = 'ACCESS_REVIEW_DECIDED' AND ResourceId = 14", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE [Action] = 'ACCOUNT_REMOVED_BY_REVIEW' AND ResourceId = 14", Integer.class)).isEqualTo(1);

        for (AccessReviewService.ItemView i : items) {
            if (i.id().equals(staff.id())) continue;
            AsUser.call(AsUser.JO_ADMIN, () -> reviews.decide(jordanReview, i.id(), "KEEP", null, AsUser.JO_ADMIN));
        }
        AccessReviewService.ReviewView closed = AsUser.call(AsUser.JO_ADMIN, () -> reviews.close(jordanReview, AsUser.JO_ADMIN));
        assertThat(closed.status()).isEqualTo(com.dilaw.mohe.codes.AccessReviewStatus.CLOSED);
        assertThat(closed.undecided()).isZero();
    }

    @Test
    @DisplayName("an undecided item past the due date is flagged once")
    void overdue() {
        AsUser.call(AsUser.ADMIN, () -> reviews.open(PERIOD, AsUser.ADMIN));
        em.flush();
        jdbc.update("UPDATE UMSCORE.AccessReviews SET DueAt = DATEADD(day, -1, SYSDATETIME()) WHERE Period = ?", PERIOD);
        em.clear();
        AccessReviewService.OverdueOutcome first = reviews.flagOverdue(LocalDateTime.now());
        assertThat(first.flagged()).isGreaterThan(0);
        assertThat(first.disabled()).as("disable-unattested is off by default").isZero();
        AccessReviewService.OverdueOutcome second = reviews.flagOverdue(LocalDateTime.now());
        assertThat(second.flagged()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM UMSCORE.UserActions WHERE [Action] = 'ACCESS_REVIEW_OVERDUE' AND ResourceId = 14", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT Status FROM UMSCORE.Users WHERE Id = 14", String.class)).isEqualTo(UserStatus.ACTIVE);
        job.nightly();
    }
}
