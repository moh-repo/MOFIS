package com.dilaw.mohe.scope;

import com.dilaw.mohe.model.DocumentType;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import com.dilaw.mohe.service.DocumentTypeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Delete on the Document Types screen removes the row.
 *
 * <p>It used to set {@code IS_ACTIVE = false}, while the screen lists every row
 * with an unfiltered {@code findAll()}. So the page said "deleted successfully",
 * re-read, and showed the same row with Active = No. The same dialog offers an
 * Active switch, which made Delete and that switch two names for one action.
 *
 * <p>Nothing references a type by id — there is no foreign key, and the three
 * document tables store the type's <em>name</em> as text — so the delete is real
 * and a count by name stands in for the constraint the database does not have.
 *
 * <p><b>Why the in-use case mocks a count.</b> Proving it for real would mean
 * inserting a document, and there is no safe row to insert on this database:
 * {@code STUDENT_DOCUMENTS.STUDENT_ID} is NOT NULL under a tenant trigger, and
 * {@code FIN_RECEIVED_WHOM_DOCUMENTS} has a foreign key to a table that is empty.
 * The guard is the thing under test, so the count it reads is stubbed; the
 * repository method itself is a derived Spring Data query, exercised for real by
 * the two cases below returning zero.
 */
@Tag("integration")
@SpringBootTest
class DocumentTypeDeleteTest {

    @Autowired private DocumentTypeService documentTypes;

    @MockitoBean private StudentDocumentRepository studentDocuments;

    private DocumentType newType() {
        DocumentType type = new DocumentType();
        type.setDocumentTypeName("DT-TEST " + UUID.randomUUID());
        type.setDocumentTypeDescription("created by DocumentTypeDeleteTest");
        type.setIsActive(true);
        type.setSortOrder(0);
        return documentTypes.saveDocumentType(type);
    }

    @Test
    @DisplayName("a deleted type is gone from the list, not merely switched off")
    void deleteRemovesTheRow() {
        when(studentDocuments.countByDocumentType(anyString())).thenReturn(0L);

        AsUser.run(AsUser.SUPERADMIN, () -> {
            DocumentType type = newType();
            Integer id = type.getDocumentTypeId();

            documentTypes.deleteDocumentType(id);

            assertThat(documentTypes.getDocumentTypeById(id))
                    .as("the row itself must be gone").isEmpty();
            assertThat(documentTypes.getAllDocumentTypes())
                    .as("and gone from the list the screen renders, which filters nothing")
                    .noneMatch(t -> id.equals(t.getDocumentTypeId()));
        });
    }

    @Test
    @DisplayName("a type still carried by a document is refused with a conflict")
    void deleteRefusesATypeInUse() {
        when(studentDocuments.countByDocumentType(anyString())).thenReturn(3L);

        AsUser.run(AsUser.SUPERADMIN, () -> {
            DocumentType type = newType();
            Integer id = type.getDocumentTypeId();
            try {
                assertThatThrownBy(() -> documentTypes.deleteDocumentType(id))
                        .isInstanceOf(ResponseStatusException.class)
                        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT);

                assertThat(documentTypes.getDocumentTypeById(id))
                        .as("a refused delete leaves the type alone").isPresent();
            } finally {
                when(studentDocuments.countByDocumentType(anyString())).thenReturn(0L);
                documentTypes.deleteDocumentType(id);
            }
        });
    }

    @Test
    @DisplayName("deleting a type that is not there is a 404, not a silent success")
    void deleteOfAMissingTypeIs404() {
        when(studentDocuments.countByDocumentType(anyString())).thenReturn(0L);

        AsUser.run(AsUser.SUPERADMIN, () ->
                assertThatThrownBy(() -> documentTypes.deleteDocumentType(-424242))
                        .isInstanceOf(ResponseStatusException.class)
                        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
