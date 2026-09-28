package com.dilaw.mohe.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The single-use tickets that replaced {@code ?token=} on the SSE alert stream.
 * The properties {@code JwtAuthFilter} relies on: a ticket yields exactly the
 * JWT it was minted with, exactly once.
 */
class AlertStreamTicketServiceTest {

    @Test
    @DisplayName("a ticket redeems to the JWT it was minted with — once")
    void singleUse() {
        AlertStreamTicketService service = new AlertStreamTicketService();
        String ticket = service.issue("jwt-abc");

        assertThat(service.redeem(ticket)).isEqualTo("jwt-abc");
        assertThat(service.redeem(ticket))
                .as("a replayed ticket — e.g. from an access log — is dead")
                .isNull();
    }

    @Test
    @DisplayName("unknown, null and blank tickets redeem to nothing")
    void unknownTickets() {
        AlertStreamTicketService service = new AlertStreamTicketService();
        assertThat(service.redeem("no-such-ticket")).isNull();
        assertThat(service.redeem(null)).isNull();
        assertThat(service.redeem("  ")).isNull();
    }

    @Test
    @DisplayName("a ticket cannot be minted without a bearer token")
    void requiresJwt() {
        AlertStreamTicketService service = new AlertStreamTicketService();
        assertThatThrownBy(() -> service.issue(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issue(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("two tickets are independent handles")
    void independentTickets() {
        AlertStreamTicketService service = new AlertStreamTicketService();
        String a = service.issue("jwt-a");
        String b = service.issue("jwt-b");

        assertThat(a).isNotEqualTo(b);
        assertThat(service.redeem(b)).isEqualTo("jwt-b");
        assertThat(service.redeem(a)).isEqualTo("jwt-a");
    }
}
