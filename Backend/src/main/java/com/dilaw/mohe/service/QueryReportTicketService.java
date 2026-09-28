package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.QuerySpecDto;
import com.dilaw.mohe.util.SecurityContextHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds an ad-hoc query spec long enough to render it as a PDF.
 *
 * <p>The PDF pipeline is entirely GET-based — the viewer uses an iframe and the
 * "open in new tab" path is a direct navigation, both to get past ad blockers
 * that refuse XHR to anything called a report. A spec with joins and filters is
 * far too big for a query string, so the page posts it once, gets a ticket, and
 * puts the ticket in the URL.
 *
 * <p>A ticket is a handle, not a capability. It carries no permission of its
 * own: redeeming one re-runs the spec through {@link QueryExecutionService},
 * which applies the country scope of whoever is asking, and a ticket can only be
 * redeemed by the user who created it. So the worst a leaked ticket can do is
 * let its owner see what they could already see.
 */
@Service
public class QueryReportTicketService {

    /**
     * How long a ticket lives.
     *
     * <p>Long enough to preview a report and then download it, short enough that
     * specs do not accumulate in memory. Nothing depends on a ticket surviving —
     * the page still holds the spec and can ask for another.
     */
    private static final Duration TICKET_TTL = Duration.ofMinutes(15);

    /** Bounds the map if tickets are minted faster than they expire. */
    private static final int MAX_TICKETS = 500;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    private record Ticket(String owner, QuerySpecDto spec, Instant expiresAt) {
        private boolean isLive(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    /** Stores a spec and returns the handle the PDF endpoints accept. */
    public String issue(QuerySpecDto spec) {
        if (spec == null || spec.getSource() == null || spec.getSource().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A query with no source cannot be reported on");
        }
        Instant now = Instant.now();
        evictExpired(now);
        if (tickets.size() >= MAX_TICKETS) {
            tickets.clear();
        }
        String id = UUID.randomUUID().toString();
        tickets.put(id, new Ticket(SecurityContextHelper.currentUsername(), spec,
                now.plus(TICKET_TTL)));
        return id;
    }

    /**
     * The spec behind a ticket.
     *
     * <p>Deliberately does not consume the ticket: previewing a report and then
     * downloading it are two requests for the same document, and making the
     * second one fail would be a bug rather than a safeguard.
     */
    public QuerySpecDto redeem(String ticketId) {
        Instant now = Instant.now();
        evictExpired(now);
        Ticket ticket = ticketId == null ? null : tickets.get(ticketId);
        if (ticket == null || !ticket.isLive(now)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "This report link has expired. Run the query again to produce a new one.");
        }
        if (!ticket.owner().equals(SecurityContextHelper.currentUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This report link belongs to another user");
        }
        return ticket.spec();
    }

    private void evictExpired(Instant now) {
        Iterator<Map.Entry<String, Ticket>> entries = tickets.entrySet().iterator();
        while (entries.hasNext()) {
            if (!entries.next().getValue().isLive(now)) {
                entries.remove();
            }
        }
    }
}
