package com.dilaw.mohe.exception;

/**
 * A 409 whose text is resolved in the reader's language; see {@link LocalizedMessage}.
 * Still an {@link IllegalStateException}, so code that catches that goes on working.
 */
public class LocalizedStateException extends IllegalStateException implements LocalizedMessage {

    private final String key;
    private final transient Object[] args;

    public LocalizedStateException(String english, String key, Object... args) {
        super(english);
        this.key = key;
        this.args = args;
    }

    @Override
    public String messageKey() {
        return key;
    }

    @Override
    public Object[] messageArgs() {
        return args;
    }
}
