package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyFinReceivedStatus;
import com.dilaw.mohe.repository.StudyFinReceivedStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.dilaw.mohe.codes.FinReceivedStatusCode;

@Transactional(readOnly = true)
@Service
public class StudyFinReceivedStatusService {

    /**
     * The statuses that mean money moved, by {@code CODE}: a cheque date is required
     * for these and for no other (live QA H2, 23 Sep 2026). The same set is what the
     * quarter close counts as "paid".
     */
    public static final Set<String> PAID_CODES = Set.of(
            FinReceivedStatusCode.RECEIVED, FinReceivedStatusCode.DISBURSED, FinReceivedStatusCode.PARTIALLY_DISBURSED);

    /**
     * The ids V072 seeded for {@link #PAID_CODES}, used only when the lookup cannot be
     * read — the rules must not fall open because a table is empty.
     */
    public static final Set<Integer> PAID_IDS_FALLBACK = Set.of(1, 100, 101);

    /** The id V072 seeded for NOT_SPECIFIED; the same fallback rule. */
    public static final int NOT_SPECIFIED_ID_FALLBACK = 99;

    @Autowired
    private StudyFinReceivedStatusRepository repository;

    public List<StudyFinReceivedStatus> getAll() {
        return repository.findAll();
    }

    /** Ids of the statuses that mean money moved — the ones a cheque date is required for. */
    public Set<Integer> paidStatusIds() {
        Set<Integer> ids = repository.findAll().stream()
                .filter(s -> s.getId() != null && s.getCode() != null && PAID_CODES.contains(s.getCode().trim()))
                .map(StudyFinReceivedStatus::getId)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? PAID_IDS_FALLBACK : ids;
    }

    /** The id of NOT_SPECIFIED — what a row nobody has been paid for carries since V135. */
    public int notSpecifiedStatusId() {
        return repository.findAll().stream()
                .filter(s -> s.getId() != null && FinReceivedStatusCode.NOT_SPECIFIED.equals(
                        s.getCode() == null ? null : s.getCode().trim()))
                .map(StudyFinReceivedStatus::getId)
                .findFirst()
                .orElse(NOT_SPECIFIED_ID_FALLBACK);
    }

    public StudyFinReceivedStatus getById(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinReceivedStatus not found"));
    }

    @Transactional
    public StudyFinReceivedStatus create(StudyFinReceivedStatus entity) {
        return repository.save(entity);
    }

    @Transactional
    public StudyFinReceivedStatus update(Integer id, StudyFinReceivedStatus entity) {
        Optional<StudyFinReceivedStatus> existingOptional = repository.findById(id);
        if (existingOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "StudyFinReceivedStatus not found");
        }
        entity.setId(id);
        return repository.save(entity);
    }

    /** 404 for an id with no row (round-5 QA R5-22): deleteById is silent then, and the screen read success. */
    @Transactional
    public void delete(Integer id) {
        if (id == null || !repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Received status not found");
        }
        repository.deleteById(id);
    }
} 