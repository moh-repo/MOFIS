package com.dilaw.mohe.service;

import com.dilaw.mohe.model.DocumentType;
import com.dilaw.mohe.repository.DocumentTypeRepository;
import com.dilaw.mohe.repository.FinReceivedWhomDocumentRepository;
import com.dilaw.mohe.repository.StudentDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Transactional(readOnly = true)
@Service
public class DocumentTypeService {

    @Autowired
    private DocumentTypeRepository documentTypeRepository;

    @Autowired
    private StudentDocumentRepository studentDocumentRepository;

    @Autowired
    private FinReceivedWhomDocumentRepository finReceivedWhomDocumentRepository;

    public List<DocumentType> getAllActiveDocumentTypes() {
        return documentTypeRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }

    public List<DocumentType> getAllDocumentTypes() {
        return documentTypeRepository.findAll();
    }

    public Optional<DocumentType> getDocumentTypeById(Integer id) {
        return documentTypeRepository.findById(id);
    }

    public Optional<DocumentType> getDocumentTypeByName(String name) {
        return documentTypeRepository.findActiveByName(name);
    }

    @Transactional
    public DocumentType saveDocumentType(DocumentType documentType) {
        return documentTypeRepository.save(documentType);
    }

    @Transactional
    public DocumentType updateDocumentType(Integer id, DocumentType documentType) {
        Optional<DocumentType> existingDocumentType = documentTypeRepository.findById(id);
        if (existingDocumentType.isPresent()) {
            DocumentType existing = existingDocumentType.get();
            existing.setDocumentTypeName(documentType.getDocumentTypeName());
            // The English name was the one editable field this copy left out, so it
            // could never be changed after creation (live QA M1, 23 Sep 2026).
            existing.setDocumentTypeNameEn(documentType.getDocumentTypeNameEn());
            existing.setDocumentTypeDescription(documentType.getDocumentTypeDescription());
            existing.setSortOrder(documentType.getSortOrder());
            existing.setIsActive(documentType.getIsActive());
            return documentTypeRepository.save(existing);
        } else {
            throw new ResponseStatusException(NOT_FOUND, "Document type not found");
        }
    }

    /**
     * Deletes the type, unless documents still claim it.
     *
     * <p>This used to set {@code IS_ACTIVE = false} while the screen's list is an
     * unfiltered {@code findAll()} — so the row came back in the very same refresh,
     * flipped to Active = No, after the page had said "deleted". Worse, Active is a
     * switch the same dialog offers, so Delete and switching Active off did the
     * same thing and neither could be told from the other.
     *
     * <p>It is a real delete now. Nothing references a type by id — no foreign key
     * exists, and {@code STUDENT_DOCUMENTS}, {@code STUDENT_DOCUMENT_HISTORY} and
     * {@code FIN_RECEIVED_WHOM_DOCUMENTS} each store the type's <em>name</em> as
     * text — so the count by name below is what a foreign key would otherwise do,
     * and the caller gets the same 409 the database would have raised. A type no
     * longer wanted but still carried by documents is what the Active switch is
     * for.
     */
    @Transactional
    public void deleteDocumentType(Integer id) {
        DocumentType docType = documentTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Document type not found"));

        long inUse = studentDocumentRepository.countByDocumentType(docType.getDocumentTypeName())
                + finReceivedWhomDocumentRepository.countByDocumentType(docType.getDocumentTypeName());
        if (inUse > 0) {
            throw new ResponseStatusException(CONFLICT,
                    "'" + docType.getDocumentTypeName() + "' is still used by " + inUse
                            + " document(s) and cannot be deleted. Switch it to inactive instead"
                            + " to stop it being offered on new uploads.");
        }

        documentTypeRepository.delete(docType);
    }

    public boolean existsByName(String name) {
        return documentTypeRepository.existsByDocumentTypeNameAndIsActiveTrue(name);
    }

    // The document types are seeded by V072 (Backend/docs/scripts), bilingual; the
    // ten English-only rows this service used to generate at runtime are gone.
}
