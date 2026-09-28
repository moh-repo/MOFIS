package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.model.DocumentType;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.repository.DocumentTypeRepository;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.service.StudentDocumentService;
import com.dilaw.mohe.service.StudentFileStorageService;
import com.dilaw.mohe.util.FileUploadValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Live QA L12 (P8, P9, P15, §7.2; 25 Sep 2026): portal documents. A PNG named
 * {@code .exe} and a free-text document type were accepted; the document list carried
 * the storage path and hash; another student's document answered 403 where a missing
 * one answered 404; a reply attachment downloaded with no name.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalDocumentsTest {

    private static final int ME = 2;
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Mock private StudentDocumentService studentDocumentService;
    @Mock private DocumentTypeRepository documentTypeRepository;
    @Mock private StudentFileStorageService studentFileStorage;
    @Mock private FileEncryptionService fileEncryptionService;
    @InjectMocks private StudentPortalMeController controller;

    private static MockHttpServletRequest me() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_ID, ME);
        return r;
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }

    @Test
    @DisplayName("P9: a picture whose name does not end in its type's extension is refused")
    void extensionMustMatch() {
        assertThatThrownBy(() -> FileUploadValidator.requireMatchingExtension(png("photo.exe")))
                .isInstanceOf(LocalizedArgumentException.class)
                .satisfies(e -> assertThat(((LocalizedArgumentException) e).messageKey()).isEqualTo("upload.extensionMismatch"));
        assertThatThrownBy(() -> FileUploadValidator.requireMatchingExtension(png("photo")))
                .isInstanceOf(LocalizedArgumentException.class);
        assertThatCode(() -> FileUploadValidator.requireMatchingExtension(png("photo.PNG"))).doesNotThrowAnyException();
        assertThatCode(() -> FileUploadValidator.requireMatchingExtension(
                new MockMultipartFile("file", "scan.jpeg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("P9: a document type that is not on the list is refused before anything is stored")
    void typeMustBeListed() {
        DocumentType passport = new DocumentType();
        passport.setDocumentTypeName("جواز سفر");
        passport.setDocumentTypeNameEn("Passport");
        when(documentTypeRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(passport));

        assertThatThrownBy(() -> controller.uploadDocument(png("photo.png"), "photo", "<b>mine</b>", me()))
                .isInstanceOf(LocalizedArgumentException.class)
                .satisfies(e -> assertThat(((LocalizedArgumentException) e).messageKey()).isEqualTo("portal.upload.badType"));
        verifyNoInteractions(studentFileStorage, fileEncryptionService);
    }

    @Test
    @DisplayName("the list carries neither the storage path nor the hash")
    void listHasNoPathOrHash() {
        StudentDocument d = new StudentDocument();
        d.setDocumentId(9);
        d.setStudentId(ME);
        d.setDocumentName("passport");
        d.setFilePath("country_1/student_2/171_passport.pdf");
        d.setFileHash("ab".repeat(32));
        when(studentDocumentService.getDocumentsByStudentId(ME)).thenReturn(List.of(d));

        List<PortalDocumentView> list = controller.getMyDocuments(me());

        assertThat(list).extracting(PortalDocumentView::documentId).containsExactly(9);
        assertThat(Arrays.stream(PortalDocumentView.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("filePath", "fileHash");
    }

    @Test
    @DisplayName("another student's document is not found, like a missing one")
    void anotherStudentsIsNotFound() {
        StudentDocument other = new StudentDocument();
        other.setDocumentId(5);
        other.setStudentId(3);
        when(studentDocumentService.getDocumentById(5)).thenReturn(Optional.of(other));
        when(studentDocumentService.getDocumentById(6)).thenReturn(Optional.empty());

        ResponseEntity<?> theirs = controller.downloadDocument(5, me());
        ResponseEntity<?> missing = controller.downloadDocument(6, me());

        assertThat(theirs.getStatusCode().value()).isEqualTo(404);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(fileEncryptionService);
    }

    @Test
    @DisplayName("P15: a download carries the file's own name, UTF-8 encoded")
    void downloadIsNamed() throws Exception {
        StudentDocument mine = new StudentDocument();
        mine.setDocumentId(7);
        mine.setStudentId(ME);
        mine.setFilePath("x");
        mine.setOriginalFilename("ردّي.pdf");
        when(studentDocumentService.getDocumentById(7)).thenReturn(Optional.of(mine));
        java.nio.file.Path file = java.nio.file.Files.createTempFile("portal-doc", ".bin");
        when(studentFileStorage.resolveStored("x")).thenReturn(file);
        when(fileEncryptionService.readDecrypted(any())).thenReturn(new byte[]{1, 2});

        ResponseEntity<?> answer = controller.downloadDocument(7, me());

        String disposition = answer.getHeaders().getFirst("Content-Disposition");
        assertThat(disposition).startsWith("attachment").contains("filename*=UTF-8''");
        assertThat(answer.getHeaders().getContentDisposition().getFilename()).isEqualTo("ردّي.pdf");
        java.nio.file.Files.deleteIfExists(file);
    }
}
