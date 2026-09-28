package com.dilaw.mohe.security.policy;

import java.util.List;

/**
 * The outcome of one policy question.
 *
 * <p>An allow may carry <em>exemptions</em>: the names of the rules the caller
 * was let past because of who they are (today only the super admin, decisions
 * D-12/D-13, O-01, O-02). The service that asked writes each one to the audit
 * log; the policy itself never audits, so a decision can be evaluated for a list
 * or a report without leaving a trail of bypasses that did not happen.
 *
 * @param allowed    whether the action may proceed
 * @param reason     the refusal, in the words the caller will read; null when allowed
 * @param exemptions the rules bypassed, in policy order; empty for an ordinary allow
 */
public record Decision(boolean allowed, String reason, List<String> exemptions) {

    private static final Decision ALLOW = new Decision(true, null, List.of());

    public static Decision allow() {
        return ALLOW;
    }

    public static Decision allowVia(List<String> exemptions) {
        return exemptions == null || exemptions.isEmpty() ? ALLOW : new Decision(true, null, List.copyOf(exemptions));
    }

    public static Decision deny(String reason) {
        return new Decision(false, reason, List.of());
    }

    public boolean via(String exemption) {
        return exemptions.contains(exemption);
    }

    public boolean exempted() {
        return !exemptions.isEmpty();
    }
}
