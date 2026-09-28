package com.dilaw.mohe.studentportal;

import com.dilaw.mohe.codes.InfoRequestStatus;
import com.dilaw.mohe.model.StudentDocument;
import com.dilaw.mohe.model.StudentInfoRequest;
import com.dilaw.mohe.repository.StudentInfoRequestRepository;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.service.StudentDataService;
import com.dilaw.mohe.service.StudentDocumentService;
import com.dilaw.mohe.service.StudentFileStorageService;
import com.dilaw.mohe.service.StudentInfoRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * System test PRT-14: a portal upload writes its file, then its rows in one transaction;
 * when the rows fail the file is removed and the transaction rolled back, instead of an
 * orphan file on disk (and, for an answer, a request marked answered without its data).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalUploadAtomicityTest {

    private static final int ME = 4;
    private static final byte[] PDF = "%PDF-1.4 test".getBytes();

    @TempDir Path dir;

    @Mock private StudentFileStorageService studentFileStorage;
    @Mock private FileEncryptionService fileEncryptionService;
    @Mock private StudentDocumentService studentDocumentService;
    @Mock private StudentDataService studentDataService;
    @Mock private StudentInfoRequestRepository studentInfoRequestRepository;
    @Mock private StudentInfoRequestService studentInfoRequestService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private org.springframework.context.MessageSource messageSource;
    @InjectMocks private StudentPortalMeController controller;

    private final TransactionStatus tx = mock(TransactionStatus.class);

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(controller, "maxDocuments", 20);
        when(transactionManager.getTransaction(any())).thenReturn(tx);
        when(studentFileStorage.studentDir(anyInt(), any())).thenReturn(dir);
        when(studentFileStorage.relativise(any())).thenAnswer(inv -> inv.getArgument(0, Path.class).getFileName().toString());
        when(studentDocumentService.getDocumentsByStudentId(anyInt())).thenReturn(List.of());
        doAnswer(inv -> {
            Files.write(inv.getArgument(1, Path.class), PDF);
            return null;
        }).when(fileEncryptionService).encryptMultipartFile(any(), any());
        when(fileEncryptionService.readDecrypted(any())).thenReturn(PDF);
        when(fileEncryptionService.sha256(any())).thenReturn("hash");
    }

    private static MockHttpServletRequest me() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute(StudentPortalTokenInterceptor.ATTR_STUDENT_ID, ME);
        return r;
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "scan.pdf", "application/pdf", PDF);
    }

    private long filesOnDisk() throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    @Test
    @DisplayName("a document whose row cannot be saved leaves no file behind, and the transaction is rolled back")
    void uploadCleansUp() throws Exception {
        doThrow(new org.springframework.dao.DataIntegrityViolationException("insert failed"))
                .when(studentDocumentService).saveDocument(any());

        assertThatThrownBy(() -> controller.uploadDocument(pdf(), "scan", null, me()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(filesOnDisk()).isZero();
        verify(transactionManager).rollback(tx);
        verify(transactionManager, never()).commit(tx);
    }

    @Test
    @DisplayName("a document that saves keeps its file, committed")
    void uploadSucceeds() throws Exception {
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("uploaded");
        doAnswer(inv -> {
            inv.getArgument(0, StudentDocument.class).setDocumentId(77);
            return null;
        }).when(studentDocumentService).saveDocument(any());
        controller.uploadDocument(pdf(), "scan", null, me());
        assertThat(filesOnDisk()).isEqualTo(1);
        verify(transactionManager).commit(tx);
    }

    @Test
    @DisplayName("an answer whose staging fails removes the attached file and rolls back the document and the answer together")
    void answerIsAtomic() throws Exception {
        StudentInfoRequest req = new StudentInfoRequest();
        req.setId(8);
        req.setStudentId(ME);
        req.setTitle("Transcript");
        req.setStatus(InfoRequestStatus.PENDING);
        when(studentInfoRequestRepository.findByIdAndStudentId(8, ME)).thenReturn(Optional.of(req));
        when(studentInfoRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(studentInfoRequestService.checkPortalProposal(any(), any(), any()))
                .thenReturn(new StudentInfoRequestService.PortalProposal("{\"studentCellphone\":\"1\"}", null));
        doThrow(new IllegalStateException("staging table locked"))
                .when(studentInfoRequestService).saveStaging(anyInt(), anyString(), any());

        assertThatThrownBy(() -> controller.respondToInfoRequest(8, "here it is", pdf(), "{}", null, me()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(filesOnDisk()).isZero();
        verify(transactionManager).rollback(tx);
        verify(transactionManager, never()).commit(tx);
        verify(studentDocumentService).saveDocument(any(StudentDocument.class));
    }
}
