package com.dilaw.mohe.exception;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A refusal whose text is a message key, resolved in the reader's language when
 * it is answered (plan W3-08; review §2.2 "server messages in one language").
 *
 * <p>Rules such as the phone-number check and the finance input rules threw an
 * English sentence, which the screens showed as it stood — to Arabic readers too,
 * and with the Java field name in it ("studentCellphone: a mobile number…"). The
 * exception keeps that English sentence as its message, for logs and for callers
 * that read it, and carries the key and arguments the answer is built from.
 *
 * <p>An argument may itself be a {@link MessageSourceResolvable} (a field's name,
 * {@code field.studentCellphone}) or a collection of them, which is resolved and
 * joined with the language's list separator.
 */
public interface LocalizedMessage {

    String messageKey();

    Object[] messageArgs();

    /** A field's name as the reader knows it: {@code field.<name>}, the name itself when there is none. */
    static MessageSourceResolvable field(String name) {
        return new DefaultMessageSourceResolvable(new String[] {"field." + name}, name);
    }

    /** A key with an English fallback, for a list of reasons. */
    static MessageSourceResolvable key(String key, String fallback) {
        return new DefaultMessageSourceResolvable(new String[] {key}, fallback);
    }

    /** The text of {@code ex} in {@code locale}; its own message when it carries no key. */
    static String resolve(MessageSource messages, Throwable ex, Locale locale) {
        return resolve(messages, ex, ex.getMessage(), locale);
    }

    /** As above, with {@code fallback} when it carries no key or the key has no text. */
    static String resolve(MessageSource messages, Throwable ex, String fallback, Locale locale) {
        if (!(ex instanceof LocalizedMessage l) || messages == null) {
            return fallback;
        }
        Object[] args = l.messageArgs() == null ? new Object[0] : l.messageArgs().clone();
        String separator = "ar".equalsIgnoreCase(locale.getLanguage()) ? "، " : ", ";
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Number n) {
                args[i] = String.valueOf(n);
            } else if (args[i] instanceof MessageSourceResolvable r) {
                args[i] = messages.getMessage(r, locale);
            } else if (args[i] instanceof Collection<?> c) {
                args[i] = c.stream()
                        .map(o -> o instanceof MessageSourceResolvable r ? messages.getMessage(r, locale) : String.valueOf(o))
                        .collect(Collectors.joining(separator));
            }
        }
        return messages.getMessage(l.messageKey(), args, fallback, locale);
    }
}
