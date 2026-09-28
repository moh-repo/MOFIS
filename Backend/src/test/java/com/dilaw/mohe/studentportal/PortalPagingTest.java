package com.dilaw.mohe.studentportal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live QA L13 (P30, 25 Sep 2026): a size of 0 or a negative page on a portal list was
 * refused by Spring with its own English sentence, whatever the student's language. The
 * values are brought into range instead.
 */
class PortalPagingTest {

    @Test
    @DisplayName("an impossible page is brought into range instead of refused")
    void clamped() {
        Pageable p = StudentPortalMeController.portalPage(-3, 0);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(1);
        assertThat(StudentPortalMeController.portalPage(2, 5000).getPageSize())
                .isEqualTo(StudentPortalMeController.MAX_PORTAL_PAGE);
        assertThat(StudentPortalMeController.portalPage(1, 10).getPageSize()).isEqualTo(10);
    }
}
