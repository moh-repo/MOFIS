package com.dilaw.mohe.codes;

import java.util.List;
import java.util.Locale;

/**
 * LU_NOTIFICATION_CHANNEL (V117): the channels the dispatcher knows how to send on.
 *
 * <p>{@link #BOTH} is the pre-V117 way of asking for e-mail and SMS together. The
 * send screen now picks any set of channels instead, but old history rows carry it
 * and an old client may still send it, so it stays a code and expands on arrival.
 */
@CodeList(value = "notification-channel", mode = CodeList.Mode.EXACT)
public final class NotificationChannel {

    private NotificationChannel() {
    }

    public static final String EMAIL = "EMAIL";
    public static final String SMS = "SMS";
    public static final String WHATSAPP = "WHATSAPP";
    public static final String BOTH = "BOTH";

    /** The channels a message can actually be delivered on, in display order. */
    public static final List<String> DELIVERABLE = List.of(EMAIL, SMS, WHATSAPP);

    /**
     * One requested code as the deliverable channels it stands for: BOTH is e-mail
     * and SMS, a deliverable code is itself, anything else is nothing.
     */
    public static List<String> expand(String code) {
        if (code == null) return List.of();
        String c = code.trim().toUpperCase(Locale.ROOT);
        if (BOTH.equals(c)) return List.of(EMAIL, SMS);
        return DELIVERABLE.contains(c) ? List.of(c) : List.of();
    }

    /** Channels that reach the recipient's phone number rather than the e-mail address. */
    public static boolean usesPhone(String channel) {
        return SMS.equals(channel) || WHATSAPP.equals(channel);
    }
}
