package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.AdminTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S-08: the sign-in rules, read from one line of configuration. Off means off;
 * an address outside the block is refused; a time outside the window asks for
 * the second factor; the strictest matching rule decides.
 */
class AccessRestrictionsTest {

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    /** Tuesday 8 Sep 2026, 10:00 in Riyadh. */
    private static final ZonedDateTime OFFICE_HOURS = ZonedDateTime.of(2026, 9, 8, 10, 0, 0, 0, RIYADH);
    /** The same Tuesday at 23:30. */
    private static final ZonedDateTime NIGHT = ZonedDateTime.of(2026, 9, 8, 23, 30, 0, 0, RIYADH);
    /** Friday 11 Sep 2026, 10:00: a weekend day for a SUN-THU window. */
    private static final ZonedDateTime FRIDAY = ZonedDateTime.of(2026, 9, 11, 10, 0, 0, 0, RIYADH);

    private static AccessRestrictions of(String spec) {
        AdminTierService tiers = mock(AdminTierService.class);
        when(tiers.tierOf(anyLong())).thenReturn(AdminTier.MINISTRY_ADMIN);
        when(tiers.roleNamesOf(anyLong())).thenReturn(Set.of("MINISTRY_ADMIN", "REPORT_VIEWER"));
        return new AccessRestrictions(spec, tiers);
    }

    @Test
    @DisplayName("no configuration means no restriction at all")
    void offByDefault() {
        AccessRestrictions r = of("");
        assertThat(r.isActive()).isFalse();
        assertThat(r.evaluate(2L, "203.0.113.9", NIGHT)).isEqualTo(AccessRestrictions.Outcome.ALLOW);
    }

    @Test
    @DisplayName("an address outside every block is refused; inside, allowed")
    void ipAllowlist() {
        AccessRestrictions r = of("MINISTRY_ADMIN{ip=10.0.0.0/8, 192.168.10.0/24 203.0.113.7}");
        assertThat(r.evaluate(2L, "10.20.30.40", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.ALLOW);
        assertThat(r.evaluate(2L, "192.168.10.200", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.ALLOW);
        assertThat(r.evaluate(2L, "203.0.113.7", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.ALLOW);
        assertThat(r.evaluate(2L, "192.168.11.1", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.DENY_IP);
        assertThat(r.evaluate(2L, "203.0.113.8", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.DENY_IP);
        assertThat(r.evaluate(2L, null, OFFICE_HOURS)).as("no address is not inside").isEqualTo(AccessRestrictions.Outcome.DENY_IP);
        assertThat(r.evaluate(2L, "not-an-address", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.DENY_IP);
    }

    @Test
    @DisplayName("IPv6 blocks work the same way")
    void ipv6() {
        AccessRestrictions r = of("MINISTRY_ADMIN{ip=2001:db8::/32}");
        assertThat(r.evaluate(2L, "2001:db8:1::5", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.ALLOW);
        assertThat(r.evaluate(2L, "2001:db9::1", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.DENY_IP);
        assertThat(r.evaluate(2L, "10.0.0.1", OFFICE_HOURS)).as("a v4 address is not in a v6 block")
                .isEqualTo(AccessRestrictions.Outcome.DENY_IP);
    }

    @Test
    @DisplayName("outside the hours the second factor is required, not a refusal")
    void loginHours() {
        AccessRestrictions r = of("MINISTRY_ADMIN{hours=SUN-THU 07:00-19:00; zone=Asia/Riyadh}");
        assertThat(r.evaluate(2L, "203.0.113.9", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.ALLOW);
        assertThat(r.evaluate(2L, "203.0.113.9", NIGHT)).isEqualTo(AccessRestrictions.Outcome.REQUIRE_MFA);
        assertThat(r.evaluate(2L, "203.0.113.9", FRIDAY)).isEqualTo(AccessRestrictions.Outcome.REQUIRE_MFA);
        // The window is read in the rule's zone: 10:00 Riyadh is 07:00 UTC, still inside.
        assertThat(r.evaluate(2L, "203.0.113.9", OFFICE_HOURS.withZoneSameInstant(ZoneId.of("UTC"))))
                .isEqualTo(AccessRestrictions.Outcome.ALLOW);
    }

    @Test
    @DisplayName("several rules: the strictest matching one wins, and a role rule counts beside the tier rule")
    void strictestWins() {
        AccessRestrictions r = of("MINISTRY_ADMIN{hours=SUN-THU 07:00-19:00; zone=Asia/Riyadh} "
                + "REPORT_VIEWER{ip=10.0.0.0/8} COUNTRY_ADMIN{ip=192.0.2.0/24}");
        assertThat(r.evaluate(2L, "10.1.1.1", OFFICE_HOURS)).isEqualTo(AccessRestrictions.Outcome.ALLOW);
        assertThat(r.evaluate(2L, "10.1.1.1", NIGHT)).isEqualTo(AccessRestrictions.Outcome.REQUIRE_MFA);
        assertThat(r.evaluate(2L, "203.0.113.9", OFFICE_HOURS)).as("the role rule refuses the address")
                .isEqualTo(AccessRestrictions.Outcome.DENY_IP);
        assertThat(r.evaluate(Set.of("COUNTRY_STAFF"), "203.0.113.9", NIGHT))
                .as("no rule names this subject").isEqualTo(AccessRestrictions.Outcome.ALLOW);
    }

    @Test
    @DisplayName("the day list accepts ranges that wrap the week and comma lists")
    void days() {
        assertThat(AccessRestrictions.parseDays("SAT-MON")).containsExactlyInAnyOrder(
                java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY);
        assertThat(AccessRestrictions.parseDays("mon,wed,fri")).hasSize(3);
    }

    @Test
    @DisplayName("a malformed line refuses to start rather than restricting nothing quietly")
    void malformed() {
        assertThatThrownBy(() -> of("MINISTRY_ADMIN{}")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("restricts nothing");
        assertThatThrownBy(() -> of("just words")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("X{ip=300.1.1.1}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> of("X{hours=SUN-THU 19:00-07:00}")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("end after it starts");
        assertThatThrownBy(() -> of("X{hours=SUN-THU}")).isInstanceOf(IllegalArgumentException.class);
        Map<String, AccessRestrictions.Rule> ok = AccessRestrictions.parse("A{ip=10.0.0.1} b{hours=SUN 00:00-23:59}");
        assertThat(ok).containsKeys("A", "B");
    }
}
