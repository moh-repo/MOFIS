package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.QuerySpecDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RPT-08: report tickets are signed and stateless, so a PDF link works on any node
 * and no amount of other users' tickets can evict it.
 */
class QueryReportTicketServiceTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-chars";
    private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signedInAs(String user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, "n/a", "X"));
    }

    private static QueryReportTicketService at(Instant now, String secret) {
        return new QueryReportTicketService(secret, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static QuerySpecDto spec() {
        QuerySpecDto spec = new QuerySpecDto();
        spec.setSource("STUDENT_DATA");
        spec.setColumns(List.of("STUDENT_ID", "STUDENT_NAME"));
        QuerySpecDto.Join join = new QuerySpecDto.Join();
        join.setTable("STUDENT_LOAN");
        join.setType("LEFT");
        join.setLeftColumn("STUDENT_ID");
        join.setColumn("STUDENT_ID");
        spec.setJoins(List.of(join));
        QuerySpecDto.Filter filter = new QuerySpecDto.Filter();
        filter.setColumn("STUDENT_ID");
        filter.setOperator("IN");
        filter.setValues(List.of("1", "2"));
        spec.setFilters(List.of(filter));
        spec.setLimit(50);
        return spec;
    }

    @Test
    @DisplayName("a ticket issued on one node is redeemed on another that shares the secret")
    void redeemsAcrossNodes() {
        signedInAs("officer");
        String ticket = at(NOW, SECRET).issue(spec());

        QuerySpecDto back = at(NOW.plusSeconds(60), SECRET).redeem(ticket);

        assertThat(back.getSource()).isEqualTo("STUDENT_DATA");
        assertThat(back.getColumns()).containsExactly("STUDENT_ID", "STUDENT_NAME");
        assertThat(back.getJoins()).singleElement().satisfies(j -> assertThat(j.getTable()).isEqualTo("STUDENT_LOAN"));
        assertThat(back.getFilters()).singleElement().satisfies(f -> assertThat(f.getValues()).containsExactly("1", "2"));
        assertThat(back.getLimit()).isEqualTo(50);
        assertThat(ticket).as("travels in a URL").matches("[A-Za-z0-9._-]+");
    }

    @Test
    @DisplayName("issuing thousands of tickets does not invalidate an earlier one (the old map cleared at 500)")
    void noEviction() {
        signedInAs("officer");
        QueryReportTicketService service = at(NOW, SECRET);
        String first = service.issue(spec());
        List<String> others = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) others.add(service.issue(spec()));

        assertThat(service.redeem(first).getSource()).isEqualTo("STUDENT_DATA");
        assertThat(others).hasSize(1_000);
    }

    @Test
    @DisplayName("a ticket expires after its time to live")
    void expires() {
        signedInAs("officer");
        String ticket = at(NOW, SECRET).issue(spec());

        assertThatThrownBy(() -> at(NOW.plus(QueryReportTicketService.TICKET_TTL).plus(Duration.ofSeconds(1)), SECRET)
                .redeem(ticket))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("only the user who issued a ticket may redeem it")
    void ownerOnly() {
        signedInAs("officer");
        String ticket = at(NOW, SECRET).issue(spec());
        signedInAs("someone-else");

        assertThatThrownBy(() -> at(NOW, SECRET).redeem(ticket))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("a tampered, foreign-key or garbage ticket is refused, not trusted")
    void forgeriesAreRefused() {
        signedInAs("officer");
        String ticket = at(NOW, SECRET).issue(spec());
        String[] parts = ticket.split("\\.");
        String otherBody = at(NOW, SECRET).issue(new QuerySpecDto() {{ setSource("AUDIT_LOG"); }}).split("\\.")[1];
        String spliced = parts[0] + "." + otherBody + "." + parts[2];

        for (String bad : List.of(spliced, "v1.abc.def", "nonsense", "", ticket + "x")) {
            assertThatThrownBy(() -> at(NOW, SECRET).redeem(bad))
                    .as(bad).isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> at(NOW, "another-secret-another-secret-12345").redeem(ticket))
                .as("signed with another key").isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> at(NOW, SECRET).redeem(null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a spec with no source is refused at issue")
    void noSource() {
        assertThatThrownBy(() -> at(NOW, SECRET).issue(new QuerySpecDto()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
