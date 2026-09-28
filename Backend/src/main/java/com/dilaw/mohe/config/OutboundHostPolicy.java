package com.dilaw.mohe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Where this application is allowed to open outbound connections.
 *
 * <p>The SMTP relay and the SMS webhook are configured by administrators in the
 * database (per-country notification settings), which means a settings admin
 * can point the server at any address it can reach — including the loopback
 * interface, the link-local metadata range a cloud host answers on, and the
 * private network the database sits on. Security plan O-36 (6 Sep 2026): those
 * ranges are refused unless {@code mohe.outbound.allow-private=true}, which an
 * on-premises ministry whose relay is on 10.x sets once and deliberately.
 *
 * <p>The check resolves the name and tests every address it resolves to, so a
 * public hostname that points at 127.0.0.1 is caught as well. A name that does
 * not resolve is refused: an unreachable relay fails either way, and refusing
 * here gives the administrator the reason rather than a timeout.
 */
@Component
public class OutboundHostPolicy {

    private final boolean allowPrivate;

    public OutboundHostPolicy(@Value("${mohe.outbound.allow-private:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    /** @return null when the host may be used, otherwise the reason it may not */
    public String refusal(String host) {
        if (host == null || host.isBlank()) {
            return "no host configured";
        }
        String h = host.trim();
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (allowPrivate) {
            return null;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(h);
        } catch (UnknownHostException e) {
            return "host '" + h + "' does not resolve";
        }
        for (InetAddress a : addresses) {
            if (a.isLoopbackAddress() || a.isAnyLocalAddress()) {
                return "host '" + h + "' is the local machine; refused (set mohe.outbound.allow-private=true for an on-premises relay)";
            }
            if (a.isLinkLocalAddress()) {
                return "host '" + h + "' is a link-local address; refused";
            }
            if (a.isSiteLocalAddress() || isCarrierGradeNat(a) || isUniqueLocalIpv6(a)) {
                return "host '" + h + "' is on a private network; refused (set mohe.outbound.allow-private=true for an on-premises relay)";
            }
        }
        return null;
    }

    /** The same, for a full URL (the SMS webhook). */
    public String refusalForUrl(String url) {
        if (url == null || url.isBlank()) {
            return "no URL configured";
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return "URL is not valid";
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return "URL must be http or https";
        }
        return refusal(uri.getHost());
    }

    /** 100.64.0.0/10 — not covered by {@link InetAddress#isSiteLocalAddress()}. */
    private static boolean isCarrierGradeNat(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 0x40;
    }

    /** fc00::/7 — IPv6 unique local, the RFC 1918 equivalent. */
    private static boolean isUniqueLocalIpv6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }
}
