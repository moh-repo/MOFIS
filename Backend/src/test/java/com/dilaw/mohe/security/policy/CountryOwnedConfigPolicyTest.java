package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.ums.model.AdminTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** S-06: an attaché's settings are the attaché's to change; the global row is the ministry's. */
class CountryOwnedConfigPolicyTest {

    private final CountryOwnedConfigPolicy policy = new CountryOwnedConfigPolicy();

    private static AccessSubject of(AdminTier tier, boolean all, Integer... countries) {
        return new AccessSubject(7L, tier, all, List.of(countries), Set.of(), Set.of("SETTINGS.EDIT"), null);
    }

    @Test
    @DisplayName("the attaché's administrator writes its own row and nobody else's")
    void countryAdmin() {
        AccessSubject jordan = of(AdminTier.COUNTRY_ADMIN, false, 1004);
        assertThat(policy.decide(jordan, new CountryOwnedConfig("SMTP settings", 1004), "WRITE").allowed()).isTrue();
        assertThat(policy.decide(jordan, new CountryOwnedConfig("SMTP settings", 1005), "WRITE").reason()).contains("not in scope");
        assertThat(policy.decide(jordan, new CountryOwnedConfig("SMTP settings", null), "WRITE").reason()).contains("ministry's");
        assertThat(policy.decide(jordan, new CountryOwnedConfig("SMTP settings", null), "VIEW").allowed()).isTrue();
    }

    @Test
    @DisplayName("ministry accounts write the global row and read the rest; the super admin has no hatch")
    void ministry() {
        for (AdminTier tier : List.of(AdminTier.MINISTRY_ADMIN, AdminTier.MINISTRY_STAFF, AdminTier.SUPER_ADMIN)) {
            AccessSubject m = of(tier, true);
            assertThat(policy.decide(m, new CountryOwnedConfig("SMTP settings", null), "WRITE").allowed()).as(tier.name()).isTrue();
            assertThat(policy.decide(m, new CountryOwnedConfig("SMTP settings", 1004), "VIEW").allowed()).as(tier.name()).isTrue();
            assertThat(policy.decide(m, new CountryOwnedConfig("SMTP settings", 1004), "WRITE").reason())
                    .as(tier.name()).contains("changed by the attaché");
        }
    }

    @Test
    @DisplayName("a row the ministry may manage (notification settings): ministry writes any attaché's; the attaché still only its own")
    void ministryMayManage() {
        for (AdminTier tier : List.of(AdminTier.MINISTRY_ADMIN, AdminTier.MINISTRY_STAFF, AdminTier.SUPER_ADMIN)) {
            AccessSubject m = of(tier, true);
            assertThat(policy.decide(m, new CountryOwnedConfig("notification settings", 1004, true), "WRITE").allowed())
                    .as(tier.name()).isTrue();
            assertThat(policy.decide(m, new CountryOwnedConfig("notification settings", null, true), "WRITE").allowed())
                    .as(tier.name()).isTrue();
        }
        AccessSubject jordan = of(AdminTier.COUNTRY_ADMIN, false, 1004);
        assertThat(policy.decide(jordan, new CountryOwnedConfig("notification settings", 1004, true), "WRITE").allowed()).isTrue();
        assertThat(policy.decide(jordan, new CountryOwnedConfig("notification settings", 1005, true), "WRITE").reason()).contains("not in scope");
        assertThat(policy.decide(jordan, new CountryOwnedConfig("notification settings", null, true), "WRITE").reason()).contains("ministry's");
    }
}
