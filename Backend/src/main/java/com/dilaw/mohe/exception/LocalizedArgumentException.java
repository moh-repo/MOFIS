package com.dilaw.mohe.exception;

/**
 * A 400 whose text is resolved in the reader's language; see {@link LocalizedMessage}.
 * Still an {@link IllegalArgumentException}, so code that catches that goes on working.
 */
public class LocalizedArgumentException extends IllegalArgumentException implements LocalizedMessage {

    private final String key;
    private final transient Object[] args;

    /**
     * @param english the sentence in English — the exception's message, for logs
     * @param key     the message key the answer is built from
     * @param args    its arguments
     */
    public LocalizedArgumentException(String english, String key, Object... args) {
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
