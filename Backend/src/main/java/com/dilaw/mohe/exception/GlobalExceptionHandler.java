package com.dilaw.mohe.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @Autowired(required = false)
    private MessageSource messageSource;

    private String resolveMessage(String code, String defaultMsg, WebRequest request) {
        return resolveMessage(code, null, defaultMsg, request);
    }

    /** As above with {@code MessageFormat} arguments (so quotes in the bundle are doubled). */
    private String resolveMessage(String code, Object[] args, String defaultMsg, WebRequest request) {
        if (messageSource == null) return defaultMsg;
        Locale locale = resolveLocale(request);
        try {
            return messageSource.getMessage(code, args, defaultMsg, locale);
        } catch (Exception e) {
            return defaultMsg;
        }
    }

    private static Locale resolveLocale(WebRequest request) {
        String acceptLang = request.getHeader("Accept-Language");
        if (acceptLang != null && acceptLang.toLowerCase().startsWith("ar")) {
            return new Locale("ar");
        }
        return Locale.ENGLISH;
    }

    /**
     * A backup, restore or backup-file operation failed on the server. The driver's text
     * names the SQL statement, the SQL state and error number, and the server's backup
     * directory (round-5 QA R5-21), so it is logged and not echoed.
     */
    @ExceptionHandler(BackupOperationException.class)
    public ResponseEntity<?> backupOperationException(BackupOperationException ex, WebRequest request) {
        log.error("Backup operation failed on {}: {}", request.getDescription(false), ex.getMessage(), ex);
        String message = resolveMessage("error.backup.failed",
                "The backup operation failed. The details are in the server log.", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /*
     * Not-found answers say so in the reader's language and name nothing else. They used
     * to echo the service's text — "StudentGender not found", "PaymentAuth not found",
     * "User not found: 99999" — which named the entity classes behind the API, in
     * English under an Arabic screen (QA SEC-002). The specific text is logged.
     */
    private ResponseEntity<?> notFound(String detail, WebRequest request) {
        log.info("Not found on {}: {}", request.getDescription(false), detail);
        String message = resolveMessage("error.record.not.found", "Record not found", request);
        return new ResponseEntity<>(new ErrorDetails(new Date(), message, request.getDescription(false)),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> resourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> noHandlerFoundException(NoHandlerFoundException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(new Date(), "Endpoint not found", request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> noResourceFoundException(NoResourceFoundException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(new Date(), "Resource not found", request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> entityNotFoundException(EntityNotFoundException ex, WebRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(EmptyResultDataAccessException.class)
    public ResponseEntity<?> emptyResultDataAccessException(EmptyResultDataAccessException ex, WebRequest request) {
        String message = resolveMessage("error.record.not.found", "Record not found", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    /*
     * Request-shape faults that Spring MVC raises before the controller runs. Without these
     * handlers they fell to the catch-all below and were reported as 500 — a malformed JSON
     * body, a non-numeric path id, a missing parameter or a wrong HTTP verb all looked like
     * server faults, and were logged as such.
     */

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> httpMessageNotReadable(HttpMessageNotReadableException ex, WebRequest request) {
        String message = resolveMessage("error.request.body.unreadable", "Request body is missing or malformed", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> methodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        // No Java type name in the answer ("must be a valid Integer" was shown to users,
        // in English under both languages — QA STD-003 / SEC-002); it goes to the log.
        log.info("Bad parameter '{}' on {}: expected {}", ex.getName(), request.getDescription(false),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?");
        String message = resolveMessage("error.invalid.parameter", new Object[] {ex.getName()},
                "The value given for '" + ex.getName() + "' is not valid.", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> missingServletRequestParameter(MissingServletRequestParameterException ex, WebRequest request) {
        String message = resolveMessage("error.missing.parameter", new Object[] {ex.getParameterName()},
                "A required value ('" + ex.getParameterName() + "') is missing.", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> httpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        String message = resolveMessage("error.method.not.allowed", "This action is not available here.", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /** A body in a content type no converter accepts (e.g. form-encoded sent to a JSON endpoint). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> httpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, WebRequest request) {
        String message = resolveMessage("error.media.type.unsupported", "Unsupported request content type", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /** A multipart upload without the {@code @RequestPart} the endpoint requires. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<?> missingServletRequestPart(MissingServletRequestPartException ex, WebRequest request) {
        String message = resolveMessage("error.request.part.missing", new Object[] {ex.getRequestPartName()},
                "Required upload part '" + ex.getRequestPartName() + "' is missing", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    /**
     * A plain (non-multipart) body sent to an upload endpoint. Spring raises this while
     * resolving the arguments, before method security runs, so without a handler even
     * a caller with no right to the endpoint got a 500 (found by the authenticated ZAP
     * pass, O-37, 7 Sep 2026).
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<?> multipartException(MultipartException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(new Date(), "A multipart/form-data upload is required", request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    /**
     * A {@code @Version} check failed: someone else saved the row between this client's
     * read and its write. 409 tells the client to reload rather than retry blindly; the
     * exception text names the entity and id, so it is logged and not echoed.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<?> optimisticLockingFailure(ObjectOptimisticLockingFailureException ex, WebRequest request) {
        log.warn("Optimistic lock failure on {}: {}", request.getDescription(false), ex.getMessage());
        String message = resolveMessage("error.optimistic.lock",
                "The record was changed by someone else. Reload it and try again.", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }

    /**
     * A foreign-key, unique or NOT NULL constraint refused the write. The driver's message
     * names tables and columns, so it is logged and not echoed; the client gets a 409 and a
     * translatable sentence instead of the 500 this used to produce.
     *
     * <p>One family of these is not a conflict at all: SQL Server raises
     * {@code String or binary data would be truncated} (error 8152 / 2628, SQLState 22001)
     * when a value is longer than its column, and Spring wraps it in the same exception.
     * Answering 409 "a duplicate value or a record that is still referenced" told the user
     * to look for a clash that does not exist — a five-letter ISO code on
     * {@code STUDY_CURRENCY_IN.ISO_CODE char(3)} read as a duplicate currency. Too long is
     * the client's mistake and a fixable one, so it is a 400 that says so.
     *
     * <p>The bean-validation {@code @Size} annotations are the first line and give the
     * field name; this is the backstop for the columns that carry no annotation yet.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> dataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation on {}: {}", request.getDescription(false), cause);
        if (isValueTooLong(ex)) {
            String tooLong = resolveMessage("error.value.too.long",
                    "One of the values is longer than the field allows. Shorten it and try again.", request);
            return new ResponseEntity<>(new ErrorDetails(new Date(), tooLong, request.getDescription(false)),
                    HttpStatus.BAD_REQUEST);
        }
        // Which rule refused it, from the driver's error number (never the message
        // text, which is in the server's language): one sentence for every case sent
        // readers to guess whether to rename, switch off or give up (QA B15, CRUD [C]).
        // 547 is a foreign-key or CHECK refusal. On a delete it means the row is still
        // referenced; on a create or an edit, that a value names a record that does not
        // exist or breaks a rule of the table (round-5 QA R5-20: an allocation for a
        // missing quarter was answered "cannot be removed").
        String message = switch (sqlServerError(ex)) {
            case 547 -> isDelete(request)
                    ? resolveMessage("error.data.in.use",
                            "This record is still used by other records, so it cannot be removed. Switch it off instead.", request)
                    : resolveMessage("error.data.reference.missing",
                            "A value in this record refers to something that does not exist or is not allowed. Check the choices and try again.", request);
            case 2627, 2601 -> resolveMessage("error.data.duplicate",
                    "A record with the same value already exists.", request);
            case 515 -> resolveMessage("error.data.required",
                    "A required value is missing.", request);
            default -> resolveMessage("error.data.integrity",
                    "The change conflicts with existing data (a duplicate value or a record that is still referenced).", request);
        };
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }

    /** Whether the refused request was an HTTP DELETE. */
    private static boolean isDelete(WebRequest request) {
        return request instanceof org.springframework.web.context.request.ServletWebRequest servlet
                && "DELETE".equalsIgnoreCase(servlet.getRequest().getMethod());
    }

    /** SQL Server's own error number for the refusal, or 0 when the cause is not a driver error. */
    static int sqlServerError(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && sql.getErrorCode() != 0) {
                return sql.getErrorCode();
            }
            if (t.getCause() == t) break;
        }
        return 0;
    }

    /**
     * Whether the driver refused the write because a value did not fit its column.
     * Read from {@link java.sql.SQLException#getSQLState()} — {@code 22001} is the standard
     * "string data, right truncation" — walking the cause chain because Hibernate wraps the
     * driver's exception. Deliberately never matched on the message text: that string is
     * localised by the server's language setting, so a database installed in Arabic would
     * quietly send every truncation back to being a 409.
     */
    private static boolean isValueTooLong(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql) {
                if ("22001".equals(sql.getSQLState())) return true;
                if (sql.getErrorCode() == 8152 || sql.getErrorCode() == 2628) return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }

    /** In the reader's language (round-5 QA R5-17: it stayed English under Arabic). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> maxUploadSizeExceededException(MaxUploadSizeExceededException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                new Date(),
                resolveMessage("upload.tooLarge", "File too large. Maximum allowed size is 10 MB.", request),
                request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(FileEncryptionException.class)
    public ResponseEntity<?> fileEncryptionException(FileEncryptionException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(new Date(), ex.getMessage(), request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /*
     * IllegalArgument / IllegalState are thrown by services with a message written for the
     * caller, so the message is echoed as before. Logged at WARN: a 4xx raised deep in a
     * service is still worth a line in the log, and these two handlers wrote none.
     */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> illegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Illegal argument on {}: {}", request.getDescription(false), ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(new Date(), localized(ex, request), request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    /** A refusal that carries a message key, in the reader's language; any other as it stands (W3-08). */
    private String localized(RuntimeException ex, WebRequest request) {
        try {
            return LocalizedMessage.resolve(messageSource, ex, resolveLocale(request));
        } catch (RuntimeException e) {
            return ex.getMessage();
        }
    }

    /**
     * The record already has a request awaiting a decision (review F4, plan W4-08): 409
     * with {@code code: PENDING_EXISTS} and the request's id, in the reader's language.
     * Only the finance screen answered it that way; every other screen showed the raw
     * English sentence, or a 500 when a service had wrapped it.
     */
    @ExceptionHandler(com.dilaw.mohe.approval.service.PendingApprovalExistsException.class)
    public ResponseEntity<?> pendingApprovalExists(com.dilaw.mohe.approval.service.PendingApprovalExistsException ex,
                                                   WebRequest request) {
        log.info("Pending request exists on {}: {}", request.getDescription(false), ex.getPendingRequestId());
        return new ResponseEntity<>(ErrorDetails.pendingExists(new Date(), localized(ex, request),
                request.getDescription(false), ex.getPendingRequestId()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> illegalStateException(IllegalStateException ex, WebRequest request) {
        log.warn("Illegal state on {}: {}", request.getDescription(false), ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(new Date(), localized(ex, request), request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> securityException(SecurityException ex, WebRequest request) {
        auditAccessDenied(request, ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(new Date(), ex.getMessage(), request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> accessDeniedException(AccessDeniedException ex, WebRequest request) {
        auditAccessDenied(request, ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                new Date(),
                ex.getMessage() != null ? ex.getMessage() : "Access denied",
                request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.FORBIDDEN);
    }

    /**
     * Method security on a public path ({@code /api/v1/ums/auth/**} is permitAll so
     * the login can live there) refuses an anonymous caller with an
     * {@link AuthenticationException}, not an {@link AccessDeniedException}; before
     * the ZAP baseline (O-37) found it, that fell through to the 500 handler.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> authenticationException(AuthenticationException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(new Date(), "Authentication required", request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.UNAUTHORIZED);
    }

    /** Writes {@code ACCESS_DENIED} rows (security plan O-32). Optional so the handler still works in slices without UMS. */
    @Autowired(required = false)
    private com.dilaw.mohe.ums.service.UmsAuditLogService auditLogService;

    /**
     * Every 403 the application raises becomes an audit row: who, which endpoint,
     * and the refusal. A user probing endpoints they may not use left no trail
     * before the O-32 pass (6 Sep 2026) — only a log line with no user id. The
     * {@code SecurityException} and {@code ResponseStatusException(FORBIDDEN)} paths
     * come through here too. Never throws: an audit failure must not turn a 403
     * into a 500.
     */
    private void auditAccessDenied(WebRequest request, String reason) {
        if (auditLogService == null) return;
        try {
            Long userId = com.dilaw.mohe.config.SecurityRequestUtils.currentUserId().orElse(null);
            if (userId == null) return; // the audit table requires a user; anonymous 403s stay in the log
            String where = request.getDescription(false);
            if (request instanceof org.springframework.web.context.request.ServletWebRequest swr) {
                where = swr.getRequest().getMethod() + " " + swr.getRequest().getRequestURI();
            }
            auditLogService.log(userId, "ACCESS_DENIED", "ENDPOINT", null, where,
                    reason == null ? "Access denied" : reason);
        } catch (RuntimeException e) {
            log.warn("Could not audit ACCESS_DENIED on {}: {}", request.getDescription(false), e.getMessage());
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> methodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        Locale locale = resolveLocale(request);
        List<ErrorDetails.FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> {
                    String msg = err.getDefaultMessage();
                    if (messageSource != null && err.getCode() != null) {
                        try {
                            String resolved = messageSource.getMessage(err, locale);
                            if (resolved != null && !resolved.isBlank()) msg = resolved;
                        } catch (Exception ignored) {}
                    }
                    return new ErrorDetails.FieldErrorItem(err.getField(), msg);
                })
                .collect(Collectors.toList());
        String summary = resolveMessage("validation.failed", "Validation failed", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), summary, request.getDescription(false), fieldErrors);
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> constraintViolation(ConstraintViolationException ex, WebRequest request) {
        Locale locale = resolveLocale(request);
        List<ErrorDetails.FieldErrorItem> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorDetails.FieldErrorItem(
                        getConstraintViolationPropertyPath(v),
                        resolveConstraintViolationMessage(v, locale)))
                .collect(Collectors.toList());
        String summary = resolveMessage("validation.failed", "Validation failed", request);
        ErrorDetails errorDetails = new ErrorDetails(
                new Date(),
                summary,
                request.getDescription(false),
                fieldErrors);
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    private String resolveConstraintViolationMessage(ConstraintViolation<?> v, Locale locale) {
        if (messageSource != null) {
            try {
                String resolved = messageSource.getMessage(v.getMessageTemplate(), v.getExecutableParameters(), v.getMessage(), locale);
                if (resolved != null && !resolved.isBlank()) {
                    return resolved;
                }
            } catch (Exception ignored) {}
        }
        return v.getMessage();
    }

    private static String getConstraintViolationPropertyPath(ConstraintViolation<?> v) {
        String path = v.getPropertyPath() != null ? v.getPropertyPath().toString() : "unknown";
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    /**
     * Client disconnected mid-transfer (e.g. browser closed PDF tab before download finished).
     * The response is already committed and Content-Type is already set, so we must NOT write
     * any body — just log at WARN and return null to suppress Spring's error-response pipeline.
     */
    @ExceptionHandler(ClientAbortException.class)
    public ResponseEntity<?> clientAbortException(ClientAbortException ex) {
        log.warn("Client disconnected before response was fully sent: {}", ex.getMessage());
        return null;
    }

    /**
     * Report renders that could not produce a file (P7): the export row cap, a busy
     * or timed-out renderer, a template that would not fill. Each carries its own
     * status and a message from the bundle in the caller's language, plus a stable
     * {@code code} — which is what a download can act on, since the browser has only
     * a blob to read and no status text worth showing.
     */
    @ExceptionHandler(com.dilaw.mohe.report.ReportException.class)
    public ResponseEntity<?> reportException(com.dilaw.mohe.report.ReportException ex, WebRequest request) {
        com.dilaw.mohe.report.ReportException.Code code = ex.getCode();
        if (code.status().is5xxServerError()) {
            log.error("Report failed ({}): {}", code, request.getDescription(false), ex);
        } else {
            log.warn("Report refused ({}): {} - {}", code, request.getDescription(false), ex.getMessage());
        }
        String message = resolveMessage(code.messageKey(), ex.getArgs(), ex.getMessage(), request);
        ErrorDetails errorDetails = ErrorDetails.withCode(new Date(), message, request.getDescription(false), code.name());
        return ResponseEntity.status(code.status()).body(errorDetails);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> responseStatusException(ResponseStatusException ex, WebRequest request) {
        if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
            return notFound(ex.getReason(), request);
        }
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        if (ex instanceof LocalizedMessage) {
            try {
                message = LocalizedMessage.resolve(messageSource, ex, message, resolveLocale(request));
            } catch (RuntimeException e) {
                // keep the English reason
            }
        }
        if (ex.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
            auditAccessDenied(request, message); // O-32: the services' own 403s count too
        }
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return ResponseEntity.status(ex.getStatusCode()).body(errorDetails);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> globalExceptionHandler(Exception ex, WebRequest request) {
        // Unwrap broken-pipe / connection-reset IOExceptions that Tomcat wraps in a plain IOException.
        Throwable cause = ex.getCause();
        if (cause instanceof java.io.IOException) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset")
                    || msg.contains("aborted by the software"))) {
                log.warn("Client disconnected before response was fully sent: {}", msg);
                return null;
            }
        }
        log.error("Unexpected error: {}", request.getDescription(false), ex);
        String message = resolveMessage("error.unexpected",
                "An unexpected error occurred. Please try again or contact support.", request);
        ErrorDetails errorDetails = new ErrorDetails(new Date(), message, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}