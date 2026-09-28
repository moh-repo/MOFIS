package com.dilaw.mohe.ums.controler;

import com.dilaw.mohe.scope.AsUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DEC-15 (review M15, plan W4-06): the admin tool pages — pages and fields, the
 * report register, API links — belong to the super admin and the ministry
 * admin. A country admin holds UMS_ADMIN for its own staff and used to reach
 * them through the same codes.
 */
@Tag("integration")
@SpringBootTest
class AdminToolPagesTierTest {

    private static final List<String> UMS_CODES = List.of("UMS_ADMIN.VIEW", "UMS_ADMIN.ADD", "UMS_ADMIN.EDIT");

    @Autowired private UmsApiLinkController apiLinks;
    @Autowired private UmsPageController pages;
    @Autowired private UmsReportController reports;

    private static List<String> with(String tier) {
        return tier == null ? UMS_CODES
                : java.util.stream.Stream.concat(UMS_CODES.stream(), java.util.stream.Stream.of(tier)).toList();
    }

    @Test
    @DisplayName("a country admin with the UMS codes is refused the admin tool pages")
    void countryAdminIsRefused() {
        for (String tier : new String[] {null, "ROLE_COUNTRY_ADMIN", "ROLE_MINISTRY_STAFF"}) {
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, with(tier), apiLinks::getAllActiveApiLinks))
                    .as("API links, tier %s", tier).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, with(tier), reports::getAllActiveReports))
                    .as("report register, tier %s", tier).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, with(tier), () -> pages.getPageFields(1L)))
                    .as("page fields, tier %s", tier).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("the ministry admin and the super admin open them with the UMS codes")
    void ministryAndSuperAdminPass() {
        for (String tier : new String[] {"ROLE_MINISTRY_ADMIN", "ROLE_SUPER_ADMIN"}) {
            assertThat(AsUser.callWithAuthorities(AsUser.SUPERADMIN, with(tier), apiLinks::getAllActiveApiLinks)
                    .getStatusCode().value()).as("API links, %s", tier).isEqualTo(200);
            assertThat(AsUser.callWithAuthorities(AsUser.SUPERADMIN, with(tier), reports::getAllActiveReports)
                    .getStatusCode().value()).as("report register, %s", tier).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("the tier alone is not enough: the page's own code is still needed")
    void tierAloneIsNotEnough() {
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SUPERADMIN, List.of("ROLE_MINISTRY_ADMIN"),
                apiLinks::getAllActiveApiLinks)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("the report catalogue every screen reads stays open to any signed-in user")
    void catalogueStaysOpen() {
        assertThat(AsUser.callWithAuthorities(AsUser.JO_ADMIN, List.of("STUDENTS.VIEW"), reports::catalogue)
                .getStatusCode().value()).isEqualTo(200);
    }
}
