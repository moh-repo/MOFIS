package com.dilaw.mohe.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Short-lived, single-use tickets for the SSE alert stream.
 *
 * <p>{@code EventSource} cannot send an {@code Authorization} header, so the
 * stream used to accept the full JWT as {@code ?token=} — which put a
 * full-privilege bearer token into proxy and web-server access logs. Instead the
 * client now POSTs (authenticated normally) for a ticket, and opens the stream
 * with {@code ?ticket=}. Redeeming the ticket yields the JWT the caller
 * presented when minting it, which then flows through the ordinary parse and
 * session-validation path in {@code JwtAuthFilter} — so revoking the session
 * still cuts the stream off at the next reconnect.
 *
 * <p>A ticket is single-use and lives {@value #TTL_SECONDS} seconds: long enough
 * to open the stream right after minting, short and single-use so a logged URL
 * is worthless. Reconnects mint a fresh ticket each time (the frontend's
 * reconnect loop re-enters the mint step). Compare {@link QueryReportTicketService},
 * which is the same idea for the PDF pipeline but deliberately multi-use.
 */
@Service
public class AlertStreamTicketService {

    static final long TTL_SECONDS = 60;
    private static final int MAX_TICKETS = 500;

    private final Cache<String, String> tickets = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(TTL_SECONDS))
            .maximumSize(MAX_TICKETS)
            .build();

    /** Stores the caller's JWT and returns the handle the stream endpoint accepts. */
    public String issue(String jwt) {
        if (jwt == null || jwt.isBlank()) {
            throw new IllegalArgumentException("Cannot issue a stream ticket without a bearer token");
        }
        String id = UUID.randomUUID().toString();
        tickets.put(id, jwt);
        return id;
    }

    /**
     * The JWT behind a ticket, or {@code null} if the ticket is unknown, expired
     * or already used. Removal is atomic, so a replayed ticket fails.
     */
    public String redeem(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return null;
        }
        return tickets.asMap().remove(ticketId);
    }
}
