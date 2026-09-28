package com.dilaw.mohe.notification.repository;

import com.dilaw.mohe.notification.model.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    Page<NotificationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Notification history the caller is allowed to see.
     *
     * <p>Scope is recorded in {@code NOTIFICATION_LOG_COUNTRY}, one row per country
     * a message was sent to. That junction table is <b>empty</b> for the eight logs
     * that predate it, so the {@code EXISTS} alone hid all eight from every country
     * user — the filter was correct and the data behind it had never been written.
     *
     * <p>Rather than invent country rows for them, the query now admits a log whose
     * {@code SCOPE} is {@code 'ALL'} and which has no country rows at all: such a
     * message went to everyone, so everyone may see that it was sent. Three of the
     * eight qualify.
     *
     * <p>The other five were sent to a {@code GROUP} or to {@code ONE} recipient and
     * nothing records who. They stay ministry-only — a message addressed to
     * somebody in particular is not something to show every attaché on the grounds
     * that we have lost track of which.
     *
     * <p>The clauses after the scope one are the screen's filter bar: each is
     * inert while its parameter is null, so one query serves the unfiltered list
     * and every combination of the six. They are compared to the codes the
     * screen sends, which are the codes of {@code LU_NOTIFICATION_CHANNEL},
     * {@code LU_NOTIFICATION_RECIPIENT_TYPE}, {@code LU_NOTIFICATION_SCOPE} and
     * {@code LU_NOTIFICATION_SEND_STATUS}; a code no row carries simply matches
     * nothing.
     *
     * <p>The ordering is <b>not</b> in the query. It used to be
     * {@code ORDER BY n.createdAt DESC} here, which Spring Data appends its own
     * {@code Sort} after — so the JPQL clause always won and a sorted
     * {@code Pageable} could never take effect. The caller passes the order in
     * the {@code Pageable} instead, and the default lives in
     * {@link com.dilaw.mohe.notification.service.NotificationService#getHistory}.
     */
    @Query("""
            SELECT n FROM NotificationLog n
            WHERE (:countryId IS NULL OR EXISTS (
                SELECT 1 FROM NotificationLogCountry nlc
                WHERE nlc.logId = n.id AND nlc.countryId = :countryId
            ))
            AND (
                :ministry = true
                OR EXISTS (
                    SELECT 1 FROM NotificationLogCountry nlc3
                    WHERE nlc3.logId = n.id AND nlc3.countryId IN :allowedIds
                )
                OR (
                    n.scope = 'ALL'
                    AND NOT EXISTS (SELECT 1 FROM NotificationLogCountry nlc4 WHERE nlc4.logId = n.id)
                )
            )
            AND (:channel IS NULL OR n.channel = :channel)
            AND (:recipientType IS NULL OR n.recipientType = :recipientType)
            AND (:scopeCode IS NULL OR n.scope = :scopeCode)
            AND (:status IS NULL OR n.status = :status)
            AND (:sentFrom IS NULL OR n.createdAt >= :sentFrom)
            AND (:sentTo IS NULL OR n.createdAt <= :sentTo)
            """)
    Page<NotificationLog> findScopedHistory(
            @Param("countryId") Integer countryId,
            @Param("ministry") boolean ministry,
            @Param("allowedIds") List<Integer> allowedIds,
            @Param("channel") String channel,
            @Param("recipientType") String recipientType,
            @Param("scopeCode") String scopeCode,
            @Param("status") String status,
            @Param("sentFrom") Instant sentFrom,
            @Param("sentTo") Instant sentTo,
            Pageable pageable);

    /**
     * One history row, under exactly the scope clause {@link #findScopedHistory}
     * applies — so a row the caller could not see in the list cannot be reached
     * by its id either (the recipients endpoint, V140).
     */
    @Query("""
            SELECT n FROM NotificationLog n
            WHERE n.id = :id
            AND (
                :ministry = true
                OR EXISTS (
                    SELECT 1 FROM NotificationLogCountry nlc3
                    WHERE nlc3.logId = n.id AND nlc3.countryId IN :allowedIds
                )
                OR (
                    n.scope = 'ALL'
                    AND NOT EXISTS (SELECT 1 FROM NotificationLogCountry nlc4 WHERE nlc4.logId = n.id)
                )
            )
            """)
    Optional<NotificationLog> findScopedById(
            @Param("id") Long id,
            @Param("ministry") boolean ministry,
            @Param("allowedIds") List<Integer> allowedIds);
}
