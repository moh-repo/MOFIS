package com.dilaw.mohe.controler;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.model.DocumentType;
import com.dilaw.mohe.service.DocumentTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/document-types")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class DocumentTypeController {

    @Autowired
    private DocumentTypeService service;

    @GetMapping("/")
    public ResponseEntity<List<DocumentType>> getAllDocumentTypes() {
        return ResponseEntity.ok(service.getAllDocumentTypes());
    }

    /**
     * The active document types, as a reference list.
     *
     * <p>Every screen that attaches a file offers this list — the finance
     * recipients screen among them — and the class-level gate is system admin,
     * so for a finance officer the call was a 403 the caller swallowed into an
     * empty dropdown. Read is opened the way the lookup tables are; the writes
     * below keep the class gate.
     */
    @PreAuthorize(MoheSecurityExpressions.LOOKUP_TABLE)
    @GetMapping("/active")
    public ResponseEntity<List<DocumentType>> getAllActiveDocumentTypes() {
        return ResponseEntity.ok(service.getAllActiveDocumentTypes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentType> getById(@PathVariable Integer id) {
        return service.getDocumentTypeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping({"/by-name/{name}", "/name/{name}"})
    public ResponseEntity<DocumentType> getByName(@PathVariable String name) {
        return service.getDocumentTypeByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/")
    public ResponseEntity<DocumentType> create(@RequestBody DocumentType entity) {
        return ResponseEntity.ok(service.saveDocumentType(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentType> update(@PathVariable Integer id, @RequestBody DocumentType entity) {
        return ResponseEntity.ok(service.updateDocumentType(id, entity));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.deleteDocumentType(id);
        return ResponseEntity.noContent().build();
    }
}