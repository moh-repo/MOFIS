package com.dilaw.mohe.notification;

import com.dilaw.mohe.notification.model.NotificationLog;
import com.dilaw.mohe.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filters behind the notification history's filter bar.
 *
 * <p>Six optional clauses on one query. What is worth pinning is not that a
 * clause narrows — that much is obvious from reading it — but the two ways a
 * query of this shape usually goes wrong: a null parameter that stops being
 * inert and empties the page, and filters that stop combining once there is
 * more than one of them.
 *
 * <p>It also pins the ordering, which moved. The query used to carry
 * {@code ORDER BY n.createdAt DESC} itself; Spring Data appends a
 * {@code Pageable}'s {@code Sort} after any {@code ORDER BY} already in the
 * JPQL rather than replacing it, so the clause in the query always won and a
 * sorted page request could never take effect. The order now comes from the
 * {@code Pageable}, and this states both that the default is still newest-first
 * and that asking for the other direction is now honoured — the second half is
 * the part that was silently impossible.
 *
 * <p>The fixture is four throwaway rows under a batch id nothing else uses, so
 * the assertions are made against the rows this test wrote rather than against
 * whatever the database happens to hold.
 */
@Tag("integration")
@SpringBootTest
class NotificationHistoryFilterTest {

    private static final String BATCH = "test-history-filter";

    @Autowired private NotificationLogRepository repo;
    @Autowired private JdbcTemplate jdbc;

    private Instant now;

    /** As a ministry user, so the country-scope clause admits everything. */
    private Page<NotificationLog> find(String channel, String recipientType, String scopeCode,
                                       String status, Instant from, Instant to) {
        return find(channel, recipientType, scopeCode, status, from, to,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    private Page<NotificationLog> find(String channel, String recipientType, String scopeCode,
                                       String status, Instant from, Instant to, Pageable pageable) {
        return repo.findScopedHistory(null, true, List.of(-1),
                channel, recipientType, scopeCode, status, from, to, pageable);
    }

    /** Only the rows this test wrote, in the order the query returned them. */
    private List<NotificationLog> mine(Page<NotificationLog> page) {
        return page.getContent().stream().filter(n -> BATCH.equals(n.getBatchId())).toList();
    }

    /**
     * Written through the repository rather than with a raw INSERT.
     *
     * <p>Not a preference. A {@code java.sql.Timestamp} handed to JdbcTemplate is
     * written as local wall-clock time, while Hibernate maps {@code Instant} in
     * UTC — so on a machine three hours from UTC every fixture row landed three
     * hours later than intended and the range assertions failed against a query
     * that was correct. Writing the fixture the way the application writes it
     * removes the discrepancy instead of compensating for it.
     */
    private void insert(String channel, String recipientType, String scopeCode, String status, Instant at) {
        NotificationLog entry = new NotificationLog();
        entry.setBatchId(BATCH);
        entry.setChannel(channel);
        entry.setRecipientType(recipientType);
        entry.setScope(scopeCode);
        entry.setSubject("subject");
        entry.setBody("body");
        entry.setTotalCount(1);
        entry.setSentCount(1);
        entry.setFailedCount(0);
        entry.setStatus(status);
        entry.setCreatedAt(at);
        repo.save(entry);
    }

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM MOHECORE.NOTIFICATION_LOG WHERE BATCH_ID = ?", BATCH);
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        insert("EMAIL", "STUDENT", "ALL", "SENT", now.minus(1, ChronoUnit.DAYS));
        insert("SMS", "STUDENT", "ONE", "FAILED", now.minus(3, ChronoUnit.DAYS));
        insert("BOTH", "EMPLOYEE", "GROUP", "SENT", now.minus(5, ChronoUnit.DAYS));
        insert("EMAIL", "EMPLOYEE", "ALL", "PARTIAL", now.minus(10, ChronoUnit.DAYS));
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM MOHECORE.NOTIFICATION_LOG WHERE BATCH_ID = ?", BATCH);
    }

    @Test
    @DisplayName("no filter is not an empty filter: all four rows come back")
    void nullsAreInert() {
        assertThat(mine(find(null, null, null, null, null, null))).hasSize(4);
    }

    @Test
    @DisplayName("each of the four code filters narrows to its own rows")
    void eachCodeFilter() {
        assertThat(mine(find("EMAIL", null, null, null, null, null)))
                .as("channel").hasSize(2)
                .allMatch(n -> "EMAIL".equals(n.getChannel()));

        assertThat(mine(find(null, "EMPLOYEE", null, null, null, null)))
                .as("recipient type").hasSize(2)
                .allMatch(n -> "EMPLOYEE".equals(n.getRecipientType()));

        assertThat(mine(find(null, null, "ALL", null, null, null)))
                .as("scope").hasSize(2)
                .allMatch(n -> "ALL".equals(n.getScope()));

        assertThat(mine(find(null, null, null, "SENT", null, null)))
                .as("status").hasSize(2)
                .allMatch(n -> "SENT".equals(n.getStatus()));
    }

    @Test
    @DisplayName("filters combine rather than replace one another")
    void filtersCombine() {
        // EMAIL alone matches two, EMPLOYEE alone matches two, and exactly one
        // row is both. A query whose clauses had been written as OR, or one
        // where a later parameter overwrote an earlier one, would return two.
        List<NotificationLog> both = mine(find("EMAIL", "EMPLOYEE", null, null, null, null));

        assertThat(both).hasSize(1);
        assertThat(both.get(0).getStatus()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("a code nothing carries matches nothing, rather than everything")
    void unknownCodeMatchesNothing() {
        assertThat(mine(find("CARRIER_PIGEON", null, null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("the date range is inclusive at both ends")
    void dateRange() {
        Instant from = now.minus(5, ChronoUnit.DAYS);
        Instant to = now.minus(1, ChronoUnit.DAYS);

        // The rows at exactly `from` and exactly `to` are inside the range: a
        // reader who picks the day a message was sent expects to see it.
        assertThat(mine(find(null, null, null, null, from, to))).hasSize(3);
        assertThat(mine(find(null, null, null, null, now.minus(4, ChronoUnit.DAYS), null))).hasSize(2);
        assertThat(mine(find(null, null, null, null, null, now.minus(6, ChronoUnit.DAYS)))).hasSize(1);
    }

    @Test
    @DisplayName("newest first by default")
    void defaultOrder() {
        List<Instant> dates = mine(find(null, null, null, null, null, null))
                .stream().map(NotificationLog::getCreatedAt).toList();

        assertThat(dates).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }

    @Test
    @DisplayName("the Pageable decides the order, which an ORDER BY in the query would not have allowed")
    void pageableCanReverseIt() {
        List<Instant> dates = mine(find(null, null, null, null, null, null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "createdAt"))))
                .stream().map(NotificationLog::getCreatedAt).toList();

        assertThat(dates).as("oldest first was requested").isSorted();
    }
}
