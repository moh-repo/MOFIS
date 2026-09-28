package com.dilaw.mohe.service;

import com.dilaw.mohe.model.DocumentType;
import com.dilaw.mohe.repository.DocumentTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Updating a document type copies every editable field, the English name included
 * (live QA M1, 23 Sep 2026: {@code PUT /api/v1/document-types/{id}} applied the
 * description, order and active flag and silently dropped {@code documentTypeNameEn}).
 */
@ExtendWith(MockitoExtension.class)
class DocumentTypeServiceTest {

    @Mock private DocumentTypeRepository repository;
    @InjectMocks private DocumentTypeService service;

    @Test
    @DisplayName("the English name is updated along with the other four editable fields")
    void copiesTheEnglishName() {
        DocumentType stored = new DocumentType();
        stored.setDocumentTypeId(5);
        stored.setDocumentTypeName("جواز سفر");
        stored.setDocumentTypeNameEn("Pasport");
        stored.setDocumentTypeDescription("old");
        stored.setSortOrder(1);
        stored.setIsActive(true);
        when(repository.findById(5)).thenReturn(Optional.of(stored));
        when(repository.save(any(DocumentType.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentType body = new DocumentType();
        body.setDocumentTypeName("جواز السفر");
        body.setDocumentTypeNameEn("Passport");
        body.setDocumentTypeDescription("new");
        body.setSortOrder(2);
        body.setIsActive(false);

        DocumentType saved = service.updateDocumentType(5, body);

        assertThat(saved.getDocumentTypeNameEn()).as("the field the update used to drop").isEqualTo("Passport");
        assertThat(saved.getDocumentTypeName()).isEqualTo("جواز السفر");
        assertThat(saved.getDocumentTypeDescription()).isEqualTo("new");
        assertThat(saved.getSortOrder()).isEqualTo(2);
        assertThat(saved.getIsActive()).isFalse();
        assertThat(saved.getDocumentTypeId()).as("the path names the row").isEqualTo(5);
    }
}
