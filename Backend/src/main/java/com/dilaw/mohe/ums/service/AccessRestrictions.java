package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.AdminTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where and when an account may sign in (security plan S-08).
 *
 * <p>Off unless {@code MOHE_ACCESS_RULES} names a rule. One rule per tier or
 * role, in braces:
 *
 * <pre>
 * MINISTRY_ADMIN{ip=10.0.0.0/8 192.168.10.0/24; hours=SUN-THU 07:00-19:00; zone=Asia/Riyadh}
 * SUPER_ADMIN{ip=10.0.0.0/8}
 * </pre>
 *
 * <p>{@code ip} is a list of addresses or CIDR blocks (space or comma
 * separated); a sign-in from outside every block is refused. {@code hours} is a
 * day set and a time window; a sign-in outside it is not refused but must
 * present the second factor, whatever the account's MFA status, because the
 * plan's reading is that an administrator called in at night is a real event
 * and a stolen password used at night is the likelier one. The key is a tier
 * ({@code SUPER_ADMIN}, {@code MINISTRY_ADMIN}, {@code MINISTRY_STAFF},
 * {@code COUNTRY_ADMIN}) or any role name; every rule that matches the
 * account applies and the strictest outcome wins.
 *
 * <p>The address is the one {@code ClientIpResolver} resolved, so behind a
 * proxy the allowlist is only as honest as {@code MOHE_TRUST_FORWARDED_FOR}.
 */
@Component
public class AccessRestrictions {

    private static final Logger log = LoggerFactory.getLogger(AccessRestrictions.class);
    private static final Pattern RULE = Pattern.compile("([A-Za-z0-9_.-]+)\\s*\\{([^}]*)\\}");

    /** Strictest first. */
    public enum Outcome { DENY_IP, REQUIRE_MFA, ALLOW }

    public record Rule(String key, List<Cidr> allowlist, Set<DayOfWeek> days, LocalTime from, LocalTime to, ZoneId zone) {

        boolean allowsAddress(String ip) {
            if (allowlist.isEmpty()) return true;
            if (ip == null || ip.isBlank()) return false;
            InetAddress address;
            try {
                address = InetAddress.getByName(ip.trim());
            } catch (UnknownHostException e) {
                return false;
            }
            return allowlist.stream().anyMatch(c -> c.contains(address));
        }

        boolean insideHours(ZonedDateTime now) {
            if (days == null) return true;
            ZonedDateTime local = now.withZoneSameInstant(zone);
            if (!days.contains(local.getDayOfWeek())) return false;
            LocalTime t = local.toLocalTime();
            return !t.isBefore(from) && !t.isAfter(to);
        }
    }

    /** An address block; a bare address is a /32 or /128. */
    public record Cidr(byte[] network, int prefix) {
        static Cidr parse(String text) {
            String s = text.trim();
            int slash = s.indexOf('/');
            String host = slash < 0 ? s : s.substring(0, slash);
            try {
                byte[] bytes = InetAddress.getByName(host).getAddress();
                int prefix = slash < 0 ? bytes.length * 8 : Integer.parseInt(s.substring(slash + 1));
                if (prefix < 0 || prefix > bytes.length * 8) {
                    throw new IllegalArgumentException("prefix out of range in " + text);
                }
                return new Cidr(bytes, prefix);
            } catch (UnknownHostException | NumberFormatException e) {
                throw new IllegalArgumentException("not an address or CIDR block: " + text, e);
            }
        }

        boolean contains(InetAddress address) {
            byte[] a = address.getAddress();
            if (a.length != network.length) return false;
            int full = prefix / 8;
            for (int i = 0; i < full; i++) {
                if (a[i] != network[i]) return false;
            }
            int rest = prefix % 8;
            if (rest == 0) return true;
            int mask = (0xFF << (8 - rest)) & 0xFF;
            return (a[full] & mask) == (network[full] & mask);
        }
    }

    private final Map<String, Rule> rules;
    private final AdminTierService adminTierService;

    public AccessRestrictions(@Value("${mohe.access.rules:}") String spec, AdminTierService adminTierService) {
        this.adminTierService = adminTierService;
        this.rules = parse(spec);
        if (!rules.isEmpty()) {
            log.info("Sign-in restrictions active for: {}", rules.keySet());
        }
    }

    public boolean isActive() {
        return !rules.isEmpty();
    }

    public Map<String, Rule> rules() {
        return Collections.unmodifiableMap(rules);
    }

    /** The outcome for an account signing in from {@code ip} at {@code now}. */
    public Outcome evaluate(Long userId, String ip, ZonedDateTime now) {
        if (rules.isEmpty() || userId == null) return Outcome.ALLOW;
        Set<String> keys = new LinkedHashSet<>();
        AdminTier tier = adminTierService.tierOf(userId);
        keys.add(tier.name());
        keys.addAll(adminTierService.roleNamesOf(userId));
        return evaluate(keys, ip, now);
    }

    /** The same, for a caller that already knows the account's tier and roles. */
    public Outcome evaluate(Set<String> subjectKeys, String ip, ZonedDateTime now) {
        Outcome outcome = Outcome.ALLOW;
        for (String key : subjectKeys) {
            Rule rule = rules.get(key.toUpperCase(Locale.ROOT));
            if (rule == null) continue;
            if (!rule.allowsAddress(ip)) return Outcome.DENY_IP;
            if (!rule.insideHours(now)) outcome = Outcome.REQUIRE_MFA;
        }
        return outcome;
    }

    static Map<String, Rule> parse(String spec) {
        Map<String, Rule> out = new LinkedHashMap<>();
        if (spec == null || spec.isBlank()) return out;
        Matcher m = RULE.matcher(spec);
        int matched = 0;
        while (m.find()) {
            matched++;
            String key = m.group(1).toUpperCase(Locale.ROOT);
            Map<String, String> fields = new LinkedHashMap<>();
            for (String part : m.group(2).split(";")) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    if (!part.isBlank()) throw new IllegalArgumentException("MOHE_ACCESS_RULES: expected key=value in " + part);
                    continue;
                }
                fields.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT), part.substring(eq + 1).trim());
            }
            List<Cidr> allow = new ArrayList<>();
            String ips = fields.get("ip");
            if (ips != null && !ips.isBlank()) {
                for (String s : ips.split("[,\\s]+")) {
                    if (!s.isBlank()) allow.add(Cidr.parse(s));
                }
            }
            ZoneId zone = fields.containsKey("zone") ? ZoneId.of(fields.get("zone")) : ZoneId.systemDefault();
            Set<DayOfWeek> days = null;
            LocalTime from = null;
            LocalTime to = null;
            String hours = fields.get("hours");
            if (hours != null && !hours.isBlank()) {
                String[] tokens = hours.trim().split("\\s+");
                if (tokens.length != 2) {
                    throw new IllegalArgumentException("MOHE_ACCESS_RULES: hours must be like SUN-THU 07:00-19:00, got " + hours);
                }
                days = parseDays(tokens[0]);
                String[] window = tokens[1].split("-");
                if (window.length != 2) {
                    throw new IllegalArgumentException("MOHE_ACCESS_RULES: time window must be HH:mm-HH:mm, got " + tokens[1]);
                }
                from = LocalTime.parse(window[0]);
                to = LocalTime.parse(window[1]);
                if (to.isBefore(from)) {
                    throw new IllegalArgumentException("MOHE_ACCESS_RULES: the window must end after it starts: " + tokens[1]);
                }
            }
            if (allow.isEmpty() && days == null) {
                throw new IllegalArgumentException("MOHE_ACCESS_RULES: rule " + key + " restricts nothing (give ip= or hours=)");
            }
            out.put(key, new Rule(key, List.copyOf(allow), days == null ? null : Collections.unmodifiableSet(days), from, to, zone));
        }
        if (matched == 0) {
            throw new IllegalArgumentException("MOHE_ACCESS_RULES is set but holds no KEY{...} rule: " + spec);
        }
        return out;
    }

    /** {@code SUN-THU}, {@code MON,WED,FRI} or {@code SAT}; ranges wrap through the week. */
    static Set<DayOfWeek> parseDays(String text) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String part : text.toUpperCase(Locale.ROOT).split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int dash = p.indexOf('-');
            if (dash < 0) {
                days.add(day(p));
            } else {
                DayOfWeek start = day(p.substring(0, dash));
                DayOfWeek end = day(p.substring(dash + 1));
                DayOfWeek d = start;
                days.add(d);
                while (d != end) {
                    d = d.plus(1);
                    days.add(d);
                }
            }
        }
        if (days.isEmpty()) throw new IllegalArgumentException("MOHE_ACCESS_RULES: no days in " + text);
        return days;
    }

    private static DayOfWeek day(String abbrev) {
        return switch (abbrev.trim()) {
            case "MON" -> DayOfWeek.MONDAY;
            case "TUE" -> DayOfWeek.TUESDAY;
            case "WED" -> DayOfWeek.WEDNESDAY;
            case "THU" -> DayOfWeek.THURSDAY;
            case "FRI" -> DayOfWeek.FRIDAY;
            case "SAT" -> DayOfWeek.SATURDAY;
            case "SUN" -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("MOHE_ACCESS_RULES: unknown day " + abbrev);
        };
    }
}
