package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.codes.NotificationChannel;
import com.dilaw.mohe.codes.NotificationSendStatus;
import com.dilaw.mohe.notification.model.NotificationLogRecipient;
import com.dilaw.mohe.notification.model.NotificationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the SMTP/SMS/WhatsApp loop for a notification batch, off the request thread and
 * outside any database transaction.
 *
 * <p>Two problems with the old {@code NotificationService.dispatchAsync}:
 *
 * <ul>
 *   <li>It was called through {@code this.} — a self-invocation, so the Spring
 *       proxy never saw it and both {@code @Async} and
 *       {@code @Transactional(REQUIRES_NEW)} were silently ignored. Every send
 *       actually ran synchronously inside the caller's transaction.</li>
 *   <li>Had the annotations worked, it would have held one Hikari connection
 *       open for the whole serial send loop — one blocking SMTP round-trip per
 *       recipient. A broadcast to hundreds of recipients pins a connection for
 *       minutes; a few concurrent broadcasts exhaust the prod pool of 10.</li>
 * </ul>
 *
 * <p>This bean is the cross-bean boundary that makes {@code @Async} real, and it
 * carries no {@code @Transactional}: the send loop runs with no connection held,
 * and the outcome is written in a short transaction via
 * {@link NotificationService#recordOutcome} — the same shape as
 * {@code ScheduledReportExecutionService}'s non-transactional orchestration.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final EmailService emailService;
    private final SmsService smsService;
    private final WhatsAppService whatsAppService;
    private final NotificationService notificationService;

    public NotificationDispatchService(EmailService emailService, SmsService smsService, WhatsAppService whatsAppService,
                                       @org.springframework.context.annotation.Lazy NotificationService notificationService) {
        this.emailService = emailService;
        this.smsService = smsService;
        this.whatsAppService = whatsAppService;
        this.notificationService = notificationService;
    }

    /**
     * Sends one batch on one channel and records the outcome on that channel's log
     * row. A send on several channels is one call per channel, each with its own
     * row, so each channel's count and errors stand on their own.
     *
     * <p>Every recipient is recorded with the batch's outcome (V140): who, the
     * address this channel used, and whether it went. The history page's view
     * reads those rows to say to whom a message was sent.
     *
     * @param recipients who the batch addresses, with the address each channel uses
     */
    @Async
    public void dispatch(Long logId, String channel, NotificationSettings cfg,
                         String subject, String body, List<RecipientContact> recipients) {
        try {
            int sent = 0, failed = 0;
            List<String> errors = new ArrayList<>();
            List<NotificationLogRecipient> outcomes = new ArrayList<>();
            boolean byPhone = NotificationChannel.usesPhone(channel);
            // SMS and WhatsApp cannot show the e-mail editor's HTML.
            String text = byPhone ? MessageText.plain(body) : body;

            for (RecipientContact r : recipients) {
                String to = r.addressFor(channel);
                String err;
                if (to == null || to.isBlank()) {
                    to = null;
                    err = byPhone ? "(missing phone for recipient)" : "(missing email for recipient)";
                } else {
                    err = switch (channel) {
                        case NotificationChannel.EMAIL -> emailService.send(cfg, to, subject, body);
                        case NotificationChannel.SMS -> smsService.send(cfg, to, text);
                        case NotificationChannel.WHATSAPP -> whatsAppService.send(cfg, to, text);
                        default -> "unknown channel " + channel;
                    };
                }
                if (err == null) sent++; else { failed++; errors.add(to == null ? err : to + ": " + err); }
                outcomes.add(outcome(logId, r, to, err));
            }

            String status = failed == 0 ? NotificationSendStatus.SENT
                    : (sent == 0 ? NotificationSendStatus.FAILED : NotificationSendStatus.PARTIAL);
            String errorSummary = errors.isEmpty() ? null : String.join("\n", errors);
            notificationService.recordOutcome(logId, sent, failed, status, errorSummary, outcomes);
            log.info("Notification batch {} ({}) done: sent={} failed={}", logId, channel, sent, failed);
        } catch (Exception e) {
            // The loop itself blew up (not an individual send): the log row must
            // not stay PENDING forever.
            log.error("Notification batch {} aborted: {}", logId, e.getMessage(), e);
            try {
                notificationService.recordOutcome(logId, 0, recipients != null ? recipients.size() : 0,
                        NotificationSendStatus.FAILED, "Dispatch aborted: " + e.getMessage());
            } catch (Exception secondary) {
                log.error("Could not record FAILED outcome for batch {}: {}", logId, secondary.toString());
            }
        }
    }

    /** The row the history keeps for one recipient of one channel. */
    static NotificationLogRecipient outcome(Long logId, RecipientContact r, String address, String error) {
        NotificationLogRecipient row = new NotificationLogRecipient();
        row.setLogId(logId);
        row.setRecipientType(r.type());
        row.setRecipientId(r.id());
        row.setRecipientName(r.name());
        row.setAddress(address);
        row.setStatus(error == null ? NotificationSendStatus.SENT : NotificationSendStatus.FAILED);
        row.setError(error == null ? null : error.length() > 1000 ? error.substring(0, 1000) : error);
        return row;
    }
}
