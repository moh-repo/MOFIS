package com.dilaw.mohe.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Request-shape faults that Spring raises before or around the controller. Without a
 * mapping they fell to the catch-all and were reported — and logged — as 500s: a
 * form-encoded body on a JSON endpoint, an upload without its file part, and a
 * concurrent edit rejected by a {@code @Version} check.
 *
 * <p>The real bundles are used, so a key missing from either language file fails here
 * rather than falling back to English in production.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void wireRealBundles() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        ReflectionTestUtils.setField(handler, "messageSource", messages);
    }

    private static WebRequest request(String acceptLanguage) {
        return request(acceptLanguage, "POST");
    }

    private static WebRequest request(String acceptLanguage, String method) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, "/mohe/api/v1/x");
        if (acceptLanguage != null) {
            req.addHeader("Accept-Language", acceptLanguage);
        }
        return new ServletWebRequest(req);
    }

    private static ErrorDetails body(ResponseEntity<?> response) {
        assertThat(response.getBody()).isInstanceOf(ErrorDetails.class);
        return (ErrorDetails) response.getBody();
    }

    @Test
    @DisplayName("unsupported content type is 415 with a translated message")
    void unsupportedMediaType() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_FORM_URLENCODED, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<?> en = handler.httpMediaTypeNotSupported(ex, request("en"));
        ResponseEntity<?> ar = handler.httpMediaTypeNotSupported(ex, request("ar"));

        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(body(en).getMessage()).isEqualTo("Unsupported request content type");
        assertThat(ar.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(body(ar).getMessage()).isNotBlank().isNotEqualTo(body(en).getMessage());
    }

    @Test
    @DisplayName("missing multipart part is 400 and names the part in both languages")
    void missingRequestPart() {
        MissingServletRequestPartException ex = new MissingServletRequestPartException("file");

        ResponseEntity<?> en = handler.missingServletRequestPart(ex, request(null));
        ResponseEntity<?> ar = handler.missingServletRequestPart(ex, request("ar-SA"));

        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(en).getMessage()).isEqualTo("Required upload part 'file' is missing");
        assertThat(ar.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(ar).getMessage()).contains("'file'").isNotEqualTo(body(en).getMessage());
    }

    @Test
    @DisplayName("optimistic lock failure is 409 and does not echo the entity/id from the exception")
    void optimisticLock() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("com.dilaw.mohe.model.StudentDatum", 42);

        ResponseEntity<?> en = handler.optimisticLockingFailure(ex, request("en"));
        ResponseEntity<?> ar = handler.optimisticLockingFailure(ex, request("ar"));

        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(en).getMessage())
                .isEqualTo("The record was changed by someone else. Reload it and try again.")
                .doesNotContain("StudentDatum");
        assertThat(ar.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(ar).getMessage()).isNotBlank().isNotEqualTo(body(en).getMessage());
    }

    @Test
    @DisplayName("a non-multipart body on an upload endpoint is 400, not 500")
    void nonMultipartBodyIs400() {
        ResponseEntity<?> r = handler.multipartException(
                new MultipartException("Current request is not a multipart request"), request("en"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(r).getMessage()).isEqualTo("A multipart/form-data upload is required");
    }

    @Test
    @DisplayName("without a message source the English defaults are used")
    void defaultsWithoutMessageSource() {
        ReflectionTestUtils.setField(handler, "messageSource", null);

        ResponseEntity<?> r = handler.optimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("x", 1), request("ar"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(r).getMessage()).isEqualTo("The record was changed by someone else. Reload it and try again.");
    }

    @Test
    @DisplayName("a bad path id names the parameter, not a Java type, in the reader's language (QA STD-003)")
    void badParameterHasNoTypeName() throws Exception {
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex =
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                        "1.5", Integer.class, "id", null, new NumberFormatException("1.5"));

        ResponseEntity<?> en = handler.methodArgumentTypeMismatch(ex, request("en"));
        ResponseEntity<?> ar = handler.methodArgumentTypeMismatch(ex, request("ar"));

        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(en).getMessage()).isEqualTo("The value given for 'id' is not valid.")
                .doesNotContain("Integer");
        assertThat(body(ar).getMessage()).contains("'id'").isNotEqualTo(body(en).getMessage());
    }

    @Test
    @DisplayName("a 404 says the record was not found and names no entity class (QA SEC-002)")
    void notFoundIsGeneric() {
        ResponseEntity<?> en = handler.responseStatusException(
                new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "StudentGender not found"),
                request("en"));
        ResponseEntity<?> ar = handler.entityNotFoundException(
                new jakarta.persistence.EntityNotFoundException("PaymentAuth not found"), request("ar"));

        assertThat(en.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(en).getMessage()).isEqualTo("Record not found");
        assertThat(ar.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(ar).getMessage()).doesNotContain("PaymentAuth").isNotEqualTo("Record not found");
    }

    @Test
    @DisplayName("a missing parameter is named in both languages")
    void missingParameter() {
        var ex = new org.springframework.web.bind.MissingServletRequestParameterException("fiscalYearId", "Integer");

        assertThat(body(handler.missingServletRequestParameter(ex, request("en"))).getMessage())
                .isEqualTo("A required value ('fiscalYearId') is missing.");
        assertThat(body(handler.missingServletRequestParameter(ex, request("ar"))).getMessage())
                .contains("'fiscalYearId'");
    }

    @Test
    @DisplayName("IllegalArgument / IllegalState keep echoing the service's own message")
    void illegalArgumentAndStateKeepTheirMessages() {
        ResponseEntity<?> arg = handler.illegalArgumentException(
                new IllegalArgumentException("Quarter number must be 1-4"), request("en"));
        ResponseEntity<?> state = handler.illegalStateException(
                new IllegalStateException("Quarter already closed"), request("en"));

        assertThat(arg.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(arg).getMessage()).isEqualTo("Quarter number must be 1-4");
        assertThat(state.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(state).getMessage()).isEqualTo("Quarter already closed");
    }

    @Test
    @DisplayName("a constraint conflict says which rule refused it, in the reader's language (QA B15)")
    void conflictSaysWhichRule() {
        java.util.function.IntFunction<ResponseEntity<?>> refusedBy = code -> handler.dataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException("refused",
                        new java.sql.SQLException("server text, in the server's language", "23000", code)),
                request("en"));
        java.util.function.IntFunction<ResponseEntity<?>> deleteRefusedBy = code -> handler.dataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException("refused",
                        new java.sql.SQLException("server text, in the server's language", "23000", code)),
                request("en", "DELETE"));

        assertThat(body(deleteRefusedBy.apply(547)).getMessage()).contains("still used by other records");
        assertThat(body(refusedBy.apply(2627)).getMessage()).contains("already exists");
        assertThat(body(refusedBy.apply(2601)).getMessage()).contains("already exists");
        assertThat(body(refusedBy.apply(515)).getMessage()).contains("required value");
        assertThat(refusedBy.apply(547).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<?> ar = handler.dataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException("refused",
                        new java.sql.SQLException("x", "23000", 547)), request("ar", "DELETE"));
        assertThat(body(ar).getMessage()).isNotBlank().isNotEqualTo(body(deleteRefusedBy.apply(547)).getMessage());
    }

    @Test
    @DisplayName("R5-17: a file over the upload limit is refused in the reader's language")
    void uploadTooLargeInTheReadersLanguage() {
        var tooLarge = new org.springframework.web.multipart.MaxUploadSizeExceededException(10L * 1024 * 1024);
        assertThat(body(handler.maxUploadSizeExceededException(tooLarge, request("ar"))).getMessage()).contains("الملف كبير");
        assertThat(body(handler.maxUploadSizeExceededException(tooLarge, request("en"))).getMessage()).contains("File too large");
    }

    @Test
    @DisplayName("R5-21: a failed backup names no SQL, error code or server path, in the reader's language")
    void backupFailureIsGeneric() {
        BackupOperationException failed = new BackupOperationException(
                "Backup failed: StatementCallback; uncategorized SQLException for SQL [BACKUP DATABASE [MOHE] TO DISK = "
                        + "'C:\\MOHE_Backups\\MOHE_Backup_20260926.bak' WITH FORMAT]; SQL state [S0001]; error code [262]",
                new java.sql.SQLException("BACKUP DATABASE permission denied in database 'MOHE'.", "S0001", 262));
        for (String lang : new String[] {"en", "ar"}) {
            ResponseEntity<?> r = handler.backupOperationException(failed, request(lang));
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(body(r).getMessage()).as(lang).isNotBlank()
                    .doesNotContain("BACKUP DATABASE").doesNotContain("MOHE_Backups").doesNotContain("262")
                    .doesNotContain("SQL state");
        }
        assertThat(body(handler.backupOperationException(failed, request("ar"))).getMessage()).contains("النسخ الاحتياطي");
    }

    @Test
    @DisplayName("R5-20: a foreign key refusing a create or an edit names a missing reference, not a removal")
    void missingReferenceOnWrite() {
        for (String method : new String[] {"POST", "PUT"}) {
            ResponseEntity<?> r = handler.dataIntegrityViolation(
                    new org.springframework.dao.DataIntegrityViolationException("refused",
                            new java.sql.SQLException("FK_ALLOCATION_QUARTER", "23000", 547)), request("en", method));
            assertThat(body(r).getMessage()).as(method)
                    .contains("does not exist").doesNotContain("cannot be removed");
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
        ResponseEntity<?> ar = handler.dataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException("refused",
                        new java.sql.SQLException("x", "23000", 547)), request("ar", "POST"));
        assertThat(body(ar).getMessage()).contains("غير موجود");
    }
}
