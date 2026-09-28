package com.dilaw.mohe.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetails {
    private Date timestamp;
    private String message;
    private String details;
    private List<FieldErrorItem> fieldErrors;
    /**
     * Stable identifier for failures the client handles by kind rather than by
     * reading the sentence — the report errors (P7) are the first of them. Absent
     * from the JSON for every other error, so nothing else changes shape.
     */
    private String code;
    /** The request a PENDING_EXISTS refusal names, so a screen can open it (W4-08). */
    private Long requestId;

    public ErrorDetails(Date timestamp, String message, String details) {
        this(timestamp, message, details, null);
    }

    public ErrorDetails(Date timestamp, String message, String details, List<FieldErrorItem> fieldErrors) {
        this.timestamp = timestamp;
        this.message = message;
        this.details = details;
        this.fieldErrors = fieldErrors != null ? fieldErrors : Collections.emptyList();
    }

    /** As above, for an error that carries a code. */
    public static ErrorDetails withCode(Date timestamp, String message, String details, String code) {
        ErrorDetails error = new ErrorDetails(timestamp, message, details);
        error.code = code;
        return error;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public List<FieldErrorItem> getFieldErrors() {
        return fieldErrors;
    }

    public Long getRequestId() {
        return requestId;
    }

    /** A PENDING_EXISTS refusal: the message and the request it names. */
    public static ErrorDetails pendingExists(Date timestamp, String message, String details, Long requestId) {
        ErrorDetails error = withCode(timestamp, message, details, "PENDING_EXISTS");
        error.requestId = requestId;
        return error;
    }

    public String getCode() {
        return code;
    }

    public static final class FieldErrorItem {
        private final String field;
        private final String message;

        public FieldErrorItem(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}