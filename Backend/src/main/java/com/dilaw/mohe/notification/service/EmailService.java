package com.dilaw.mohe.notification.service;

import com.dilaw.mohe.notification.model.NotificationSettings;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Sends an HTML email using SMTP settings loaded from the database row at send-time.
     * @return null on success, error message on failure
     */
    public String send(NotificationSettings cfg, String toAddress, String subject, String htmlBody) {
        if (cfg.getSmtpHost() == null || cfg.getSmtpHost().isBlank()) {
            return "SMTP host not configured";
        }
        try {
            String refused = outboundHostPolicy.refusal(cfg.getSmtpHost());
            if (refused != null) {
                log.warn("SMTP host refused by outbound policy: {}", refused);
                return "SMTP host refused: " + refused;
            }
            JavaMailSenderImpl sender = buildSender(cfg);
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            String from = cfg.getSmtpFromAddress();
            String fromName = cfg.getSmtpFromName();
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(from, fromName);
            } else {
                helper.setFrom(from);
            }
            helper.setTo(toAddress);
            helper.setSubject(subject != null ? subject : "(no subject)");
            helper.setText(sanitize(htmlBody), true);
            sender.send(msg);
            return null;
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", toAddress, e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * Sends an HTML email with a single binary attachment.
     * @return null on success, error message on failure
     */
    public String sendWithAttachment(NotificationSettings cfg, String toAddress, String subject,
                                     String htmlBody, byte[] attachment, String attachmentFilename) {
        if (cfg.getSmtpHost() == null || cfg.getSmtpHost().isBlank()) {
            return "SMTP host not configured";
        }
        try {
            String refused = outboundHostPolicy.refusal(cfg.getSmtpHost());
            if (refused != null) {
                log.warn("SMTP host refused by outbound policy: {}", refused);
                return "SMTP host refused: " + refused;
            }
            JavaMailSenderImpl sender = buildSender(cfg);
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            String from = cfg.getSmtpFromAddress();
            String fromName = cfg.getSmtpFromName();
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(from, fromName);
            } else {
                helper.setFrom(from);
            }
            helper.setTo(toAddress);
            helper.setSubject(subject != null ? subject : "(no subject)");
            helper.setText(sanitize(htmlBody), true);
            if (attachment != null && attachmentFilename != null && attachment.length > 0) {
                helper.addAttachment(attachmentFilename, new ByteArrayResource(attachment));
            }
            sender.send(msg);
            return null;
        } catch (Exception e) {
            log.warn("Failed to send email with attachment to {}: {}", toAddress, e.getMessage());
            return e.getMessage();
        }
    }

    /** O-36: refuses relays on loopback, link-local or private ranges unless allowed by property. */
    @org.springframework.beans.factory.annotation.Autowired
    private com.dilaw.mohe.config.OutboundHostPolicy outboundHostPolicy;

    /**
     * O-13: the body comes from the notification editor (quill) and went on the
     * wire verbatim. jsoup's relaxed safelist keeps the formatting an e-mail
     * needs — headings, lists, links, images, tables — and drops scripts, event
     * handlers and {@code javascript:} URLs, so the quill advisory in
     * DEPENDENCY_ADVISORIES.md is closed here regardless of what quill does.
     */
    static String sanitize(String htmlBody) {
        if (htmlBody == null) return "";
        return org.jsoup.Jsoup.clean(htmlBody, org.jsoup.safety.Safelist.relaxed()
                .addAttributes(":all", "style", "dir", "lang"));
    }

    private JavaMailSenderImpl buildSender(NotificationSettings cfg) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getSmtpHost());
        sender.setPort(cfg.getSmtpPort() != null ? cfg.getSmtpPort() : 587);
        sender.setUsername(cfg.getSmtpUsername());
        sender.setPassword(cfg.getSmtpPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");

        boolean useSsl = Boolean.TRUE.equals(cfg.getSmtpUseSsl());
        boolean useTls = Boolean.TRUE.equals(cfg.getSmtpUseTls());

        if (useSsl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
