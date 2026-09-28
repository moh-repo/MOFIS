package com.dilaw.mohe.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security plan O-13 (6 Sep 2026): the notification body is sanitised before
 * it is sent, so the quill advisory cannot reach a mail client.
 */
class EmailServiceSanitizeTest {

    @Test
    @DisplayName("scripts, event handlers and javascript: URLs are removed")
    void activeContentRemoved() {
        String dirty = "<p onclick=\"steal()\">Hello <script>alert(1)</script>"
                + "<a href=\"javascript:alert(1)\">link</a>"
                + "<img src=\"x\" onerror=\"alert(1)\"></p>";
        String clean = EmailService.sanitize(dirty);
        assertThat(clean).doesNotContain("<script", "onclick", "onerror", "javascript:");
        assertThat(clean).contains("Hello");
    }

    @Test
    @DisplayName("the formatting a notification needs survives")
    void formattingKept() {
        String body = "<h2 dir=\"rtl\">عنوان</h2><p style=\"color:#333\">نص <b>مهم</b></p>"
                + "<ul><li>one</li></ul><a href=\"https://example.org/x\">site</a>"
                + "<table><tr><td>cell</td></tr></table>";
        String clean = EmailService.sanitize(body);
        assertThat(clean).contains("<h2", "dir=\"rtl\"", "<b>", "<ul>", "<li>", "href=\"https://example.org/x\"", "<table>", "style=");
    }

    @Test
    @DisplayName("null becomes an empty body rather than a failure")
    void nullBody() {
        assertThat(EmailService.sanitize(null)).isEmpty();
    }
}
