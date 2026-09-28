package com.dilaw.mohe.report;

import org.springframework.http.HttpStatus;

/**
 * A report run that could not produce a file, with a code the client can act on.
 *
 * <p>Review item P7. The four ways a render fails all reached the browser as
 * something the user could not read or act on: the export row cap as a bare 403
 * (which the frontend reports as "no permission", and which also made the staff app
 * re-fetch the profile), a busy or timed-out renderer as an English sentence from
 * {@code ResponseStatusException}, and a Jasper fill failure as the catch-all 500
 * "an unexpected error occurred". None of them said what to do next, and three of
 * them are ordinary, recoverable outcomes rather than faults.
 *
 * <p>Each code carries its own status and its own message in
 * {@code messages[_ar].properties}, and travels in the {@code code} field of
 * {@link com.dilaw.mohe.exception.ErrorDetails} so the frontend can phrase it in the
 * user's language even for a download, where it only has a blob to read.
 */
public class ReportException extends RuntimeException {

    /**
     * What went wrong, and the status that says it.
     *
     * <p>The row cap is 422 rather than 403 deliberately: nothing was refused on
     * grounds of permission — the same account may export the same report with a
     * narrower filter — and a 403 additionally triggers the client's "your
     * permissions may have changed" reload.
     */
    public enum Code {
        /** The result set is larger than the caller's export row cap. */
        REPORT_EXPORT_ROW_CAP(HttpStatus.UNPROCESSABLE_ENTITY, "report.error.row-cap"),
        /** Every render thread is busy and the queue is full. */
        REPORT_RENDER_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "report.error.busy"),
        /** The render was cancelled after the configured timeout. */
        REPORT_RENDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "report.error.timeout"),
        /** The template could not be filled or exported (a JRException). */
        REPORT_FILL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "report.error.fill-failed");

        private final HttpStatus status;
        private final String messageKey;

        Code(HttpStatus status, String messageKey) {
            this.status = status;
            this.messageKey = messageKey;
        }

        public HttpStatus status() {
            return status;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    private final Code code;
    private final transient Object[] args;

    public ReportException(Code code, String message, Object... args) {
        this(code, message, null, args);
    }

    public ReportException(Code code, String message, Throwable cause, Object... args) {
        super(message, cause);
        this.code = code;
        this.args = args == null ? new Object[0] : args.clone();
    }

    public Code getCode() {
        return code;
    }

    /** {@code MessageFormat} arguments for {@link Code#messageKey()}. */
    public Object[] getArgs() {
        return args.clone();
    }
}
