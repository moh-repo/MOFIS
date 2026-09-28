package com.dilaw.mohe;

import com.dilaw.mohe.util.FileUploadValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FileUploadValidator — no Spring context required.
 */
class FileUploadValidatorUnitTest {

    // JPEG magic bytes: FF D8 FF
    private static final byte[] JPEG_MAGIC = new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, 0x00};
    // PNG magic bytes: 89 50 4E 47
    private static final byte[] PNG_MAGIC  = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    // PDF magic bytes: %PDF
    private static final byte[] PDF_MAGIC  = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};

    @Test
    @DisplayName("Null file is accepted without exception")
    void nullFile_isAccepted() {
        assertThatNoException().isThrownBy(() -> FileUploadValidator.validate(null));
    }

    @Test
    @DisplayName("JPEG file with correct magic bytes is accepted")
    void validJpeg_isAccepted() {
        MockMultipartFile file = new MockMultipartFile("f", "img.jpg", "image/jpeg", JPEG_MAGIC);
        assertThatNoException().isThrownBy(() -> FileUploadValidator.validate(file));
    }

    @Test
    @DisplayName("PNG file with correct magic bytes is accepted")
    void validPng_isAccepted() {
        MockMultipartFile file = new MockMultipartFile("f", "img.png", "image/png", PNG_MAGIC);
        assertThatNoException().isThrownBy(() -> FileUploadValidator.validate(file));
    }

    @Test
    @DisplayName("PDF file with correct magic bytes is accepted")
    void validPdf_isAccepted() {
        MockMultipartFile file = new MockMultipartFile("f", "doc.pdf", "application/pdf", PDF_MAGIC);
        assertThatNoException().isThrownBy(() -> FileUploadValidator.validate(file));
    }

    @Test
    @DisplayName("Disallowed content-type (text/plain) is rejected")
    void textPlain_isRejected() {
        MockMultipartFile file = new MockMultipartFile("f", "bad.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> FileUploadValidator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("JPEG content-type with PDF magic bytes is rejected (spoofed extension)")
    void spoofedExtension_isRejected() {
        MockMultipartFile file = new MockMultipartFile("f", "img.jpg", "image/jpeg", PDF_MAGIC);
        assertThatThrownBy(() -> FileUploadValidator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("Executable content-type is rejected")
    void executableType_isRejected() {
        MockMultipartFile file = new MockMultipartFile("f", "mal.exe", "application/octet-stream", new byte[]{0x4D, 0x5A});
        assertThatThrownBy(() -> FileUploadValidator.validate(file))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
