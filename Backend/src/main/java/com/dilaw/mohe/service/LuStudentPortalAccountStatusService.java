package com.dilaw.mohe.service;

import com.dilaw.mohe.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import com.dilaw.mohe.model.LuStudentPortalAccountStatus;
import com.dilaw.mohe.repository.LuStudentPortalAccountStatusRepository;
import com.dilaw.mohe.util.LuLookupWrites;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Admin CRUD for {@code MOHECORE.LU_STUDENT_PORTAL_ACCOUNT_STATUS}, as reached from the Lookup Tables screen.
 *
 * <p>The reads and writes here follow {@link LuExtendedLookupAdminService}: a row
 * is retired by setting {@code IS_DELETED}, never removed, and the list is the live
 * rows in {@code SORT_ORDER}. This service used to call {@code findAll()} and
 * {@code deleteById()}, which showed retired rows and destroyed live ones — and
 * every row of some of these tables is a system row that a Java constant names.
 * See {@link LuLookupWrites}.
 */
@Transactional(readOnly = true)
@Service
public class LuStudentPortalAccountStatusService {

    /** Restores a deleted code instead of refusing it as a duplicate; checks the code's width (QA B15). */
    @Autowired private LuLookupCreates luCreates;

    @Autowired
    private LuStudentPortalAccountStatusRepository repository;

    public List<LuStudentPortalAccountStatus> getAll() {
        return repository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public LuStudentPortalAccountStatus getById(int id) {
        return require(id);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuStudentPortalAccountStatus create(LuStudentPortalAccountStatus entity) {
        return luCreates.create(entity, repository::save);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuStudentPortalAccountStatus update(Integer id, LuStudentPortalAccountStatus entity) {
        LuStudentPortalAccountStatus existing = require(id);
        LuLookupWrites.copyEditable(entity, existing);
        return repository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void delete(Integer id) {
        LuStudentPortalAccountStatus existing = require(id);
        LuLookupWrites.requireDeletable(existing);
        existing.setIsDeleted(true);
        repository.save(existing);
    }

    private LuStudentPortalAccountStatus require(Integer id) {
        return repository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LuStudentPortalAccountStatus not found"));
    }
}
