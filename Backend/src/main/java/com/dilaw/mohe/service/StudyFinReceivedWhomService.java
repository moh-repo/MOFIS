package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyFinReceivedWhom;
import com.dilaw.mohe.repository.FinReceivedWhomDocumentRepository;
import com.dilaw.mohe.repository.StudyFinReceivedWhomRepository;
import com.dilaw.mohe.repository.spec.StudyFinReceivedWhomSpec;
import com.dilaw.mohe.ums.service.CountryScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Transactional(readOnly = true)
@Service
public class StudyFinReceivedWhomService {

    private static final Logger log = LoggerFactory.getLogger(StudyFinReceivedWhomService.class);

    /** Encrypted-at-rest storage root for finance-recipient documents. */
    private static final Path DOCUMENT_STORAGE =
            Paths.get("uploads/finance-recipients/documents").toAbsolutePath().normalize();

    @Autowired
    private StudyFinReceivedWhomRepository repository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private FinReceivedWhomDocumentRepository documentRepository;

    @Autowired
    private ObjectMapper json;

    public List<StudyFinReceivedWhom> getAll() {
        return repository.findAll(StudyFinReceivedWhomSpec.countryScope(countryScopeService.getScope()));
    }

    /**
     * One record, or 404 when it is not the caller's.
     *
     * <p>The {@code null} case is the point. This used to read
     * {@code if (countryId != null && !canAccess(countryId)) throw}, which let a
     * row with no country through to any caller — while the list, built on
     * {@code IN :allowed}, hid it. A row invisible in the list and readable by id
     * is the shape of leak the list was there to prevent, and the ids are
     * sequential.
     *
     * <p>404 rather than the previous 403, for the same reason: a 403 confirms
     * that a record exists at that id.
     */
    public StudyFinReceivedWhom getById(int id) {
        StudyFinReceivedWhom e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinReceivedWhom not found"));
        if (!mayAccess(e.getFinanceCountryId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinReceivedWhom not found");
        return e;
    }

    /** Scoped like the list; it was not scoped at all, and it names a student. */
    public List<StudyFinReceivedWhom> findForDelegatedStudent(Integer studentId) {
        return repository.findByStudentForDelegationId(studentId).stream()
                .filter(e -> mayAccess(e.getFinanceCountryId()))
                .toList();
    }

    /**
     * The key comes from the sequence V130 created, not from {@code MAX(id) + 1}
     * (plan W6-09). The old scheme reused the id of a deleted recipient, and two
     * approvals signed off at the same moment read the same maximum and raced for
     * the same key.
     */
    @Transactional
    public StudyFinReceivedWhom create(StudyFinReceivedWhom entity) {
        entity.setId(null);
        entity.setFinanceCountryId(resolveCountryForWrite(entity.getFinanceCountryId()));
        return repository.save(entity);
    }

    /**
     * An edit changes the columns it names and leaves the rest of the row alone.
     *
     * <p>This used to be {@code repository.save(body)}. The screen's dialog binds
     * 24 of the 27 columns, so every save wrote NULL over the three it does not —
     * {@code LETTER_REASON_ID}, {@code FINANCE_PAYMENT_ID} (the link to the
     * payment this money was received against) and {@code FIN_MANUAL_FORM_DATE}.
     * Nothing on the dev database used them yet, so nothing was lost; the defect
     * was in the path.
     *
     * <p>The body arrives as JSON rather than a bound entity for the reason the
     * bank and wafar-type fix records: <b>a field that is absent is not a field
     * set to nothing</b>, and a deserialized object cannot tell the two apart.
     * Reading the tree onto the loaded row keeps that distinction — so the dialog
     * can still clear a date it owns by sending it as null, which a null-guarded
     * copy would have made impossible.
     */
    @Transactional
    public StudyFinReceivedWhom update(Integer id, JsonNode body) {
        StudyFinReceivedWhom existing = getById(id); // validates existence and country scope
        try {
            json.readerForUpdating(existing).readValue(body);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed request body: " + e.getMessage());
        }
        existing.setId(id);
        existing.setFinanceCountryId(resolveCountryForWrite(existing.getFinanceCountryId()));
        return repository.save(existing);
    }

    /**
     * What a new record must hold before its approval request is raised (round-5 QA
     * R5-25, decision D-7): the recipient's name, the one field the dialog requires, and
     * an attaché the caller may write, resolved as {@link #create} will resolve it. A
     * request with every field empty used to enter the chain, to be refused or applied
     * only once an approver had signed it.
     */
    public void validateForRequest(StudyFinReceivedWhom entity) {
        if (entity == null || entity.getFinReceivedName() == null || entity.getFinReceivedName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The name of the person who received the money is required (finReceivedName).");
        }
        entity.setFinanceCountryId(resolveCountryForWrite(entity.getFinanceCountryId()));
    }

    /**
     * A null country is ministry-only here — it is a record whose country was
     * never written, not a deliberate "applies everywhere" marker. Contrast the
     * study-years and study-amount lookups, where a null <em>is</em> deliberate.
     */
    private boolean mayAccess(Integer countryId) {
        if (countryScopeService.getScope().isMinistryUser()) return true;
        return countryId != null && countryScopeService.canAccessCountry(countryId);
    }

    /**
     * Every new record names a country in the caller's scope.
     *
     * <p>Previously a null simply skipped the check, so any caller could create a
     * record that the list would then hide from them — data entered and apparently
     * lost. Ministry is not exempted: there is no useful country-less version of
     * "who collected this money".
     */
    private Integer resolveCountryForWrite(Integer requested) {
        // Zero is the dialog's empty selection, not an attaché: the column
        // defaults to 0, and a record stored under 0 is one no attaché's staff
        // can ever see — the same disappearance a null used to cause.
        if (requested == null || requested == 0) {
            var scope = countryScopeService.getScope();
            if (!scope.isMinistryUser() && scope.getAllowedCountryIds().size() == 1)
                return scope.getAllowedCountryIds().get(0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The attaché this payment was collected under is required (financeCountryId).");
        }
        countryScopeService.validateCountryAccess(requested);
        return requested;
    }

    /**
     * Deletes the recipient and the documents attached to it.
     *
     * <p>The documents used to stay. That is not only litter: ids here are handed
     * out as {@code MAX(id) + 1}, so the next recipient created after the highest
     * row is deleted takes the deleted one's id — and inherited its passport
     * scans, under a different person's name. The rows go here; the encrypted
     * files are removed by the controller, which owns the storage root.
     */
    @Transactional
    public void delete(Integer id) {
        getById(id); // validates existence and country scope
        documentRepository.deleteByWhomId(id);
        repository.deleteById(id);
        deleteDocumentFolder(id);
    }

    /**
     * Removes the recipient's encrypted uploads.
     *
     * <p>This lived in the controller until the screen went behind the approval
     * workflow, at which point the controller stopped deleting anything and the
     * files would have been left behind by the executor. It belongs with the
     * delete it accompanies.
     *
     * <p>Called after the rows are gone, and never fatal: a file that cannot be
     * removed leaves an unreachable blob, while a thrown exception here would
     * report a delete that in fact succeeded.
     */
    private void deleteDocumentFolder(Integer whomId) {
        Path folder = DOCUMENT_STORAGE.resolve("whom_" + whomId).normalize();
        if (!folder.startsWith(DOCUMENT_STORAGE) || !Files.exists(folder)) {
            return;
        }
        try (var paths = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not remove finance-recipient file {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not remove finance-recipient folder {}: {}", folder, e.getMessage());
        }
    }

    /** The document rows of a recipient the caller may see; 404 otherwise. */
    public List<com.dilaw.mohe.model.FinReceivedWhomDocument> documentsOf(Integer id) {
        getById(id); // validates existence and country scope
        return documentRepository.findByWhomIdAndIsActiveTrueOrderByUploadDateDesc(id);
    }
} 