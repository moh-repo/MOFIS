package com.dilaw.mohe.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * A {@link ResponseStatusException} whose reason is resolved in the reader's
 * language; see {@link LocalizedMessage}. The English reason stays for logs.
 */
public class LocalizedStatusException extends ResponseStatusException implements LocalizedMessage {

    private final String key;
    private final transient Object[] args;

    /**
     * @param status  the answer's status
     * @param english the reason in English, for logs
     * @param key     the message key the answer is built from
     * @param args    its arguments
     */
    public LocalizedStatusException(HttpStatusCode status, String english, String key, Object... args) {
        super(status, english);
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
