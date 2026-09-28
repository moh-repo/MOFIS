package com.dilaw.mohe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security plan O-36 (6 Sep 2026): administrator-configured SMTP and SMS hosts
 * may not point the server at itself or at the private network.
 */
class OutboundHostPolicyTest {

    private final OutboundHostPolicy strict = new OutboundHostPolicy(false);
    private final OutboundHostPolicy onPrem = new OutboundHostPolicy(true);

    @Test
    @DisplayName("loopback, link-local, RFC 1918 and carrier-grade NAT are refused by default")
    void privateRangesRefused() {
        assertThat(strict.refusal("127.0.0.1")).contains("local machine");
        assertThat(strict.refusal("localhost")).contains("local machine");
        assertThat(strict.refusal("0.0.0.0")).contains("local machine");
        assertThat(strict.refusal("169.254.169.254")).contains("link-local");
        assertThat(strict.refusal("10.1.2.3")).contains("private network");
        assertThat(strict.refusal("172.16.0.9")).contains("private network");
        assertThat(strict.refusal("192.168.1.1")).contains("private network");
        assertThat(strict.refusal("100.64.0.1")).contains("private network");
        assertThat(strict.refusal("[::1]")).contains("local machine");
        assertThat(strict.refusal("fd12::1")).contains("private network");
    }

    @Test
    @DisplayName("a public address is allowed; an empty host is not")
    void publicAllowed() {
        assertThat(strict.refusal("198.51.100.7")).isNull();
        assertThat(strict.refusal("")).isNotNull();
        assertThat(strict.refusal(null)).isNotNull();
    }

    @Test
    @DisplayName("an on-premises deployment may opt in to private relays")
    void allowPrivateOptIn() {
        assertThat(onPrem.refusal("10.1.2.3")).isNull();
        assertThat(onPrem.refusal("127.0.0.1")).isNull();
        assertThat(onPrem.refusal("")).as("still needs a host").isNotNull();
    }

    @Test
    @DisplayName("the webhook URL must be http(s) and its host passes the same test")
    void webhookUrl() {
        assertThat(strict.refusalForUrl("https://198.51.100.7/sms")).isNull();
        assertThat(strict.refusalForUrl("http://127.0.0.1:8080/sms")).contains("local machine");
        assertThat(strict.refusalForUrl("ftp://198.51.100.7/sms")).contains("http or https");
        assertThat(strict.refusalForUrl("file:///etc/passwd")).contains("http or https");
        assertThat(strict.refusalForUrl("not a url")).isNotNull();
        assertThat(strict.refusalForUrl(null)).isNotNull();
    }
}
