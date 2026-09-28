package com.dilaw.mohe.service;

import com.dilaw.mohe.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import com.dilaw.mohe.model.LuNotificationScope;
import com.dilaw.mohe.repository.LuNotificationScopeRepository;
import com.dilaw.mohe.util.LuLookupWrites;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Admin CRUD for {@code MOHECORE.LU_NOTIFICATION_SCOPE}, as reached from the Lookup Tables screen.
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
public class LuNotificationScopeService {

    /** Restores a deleted code instead of refusing it as a duplicate; checks the code's width (QA B15). */
    @Autowired private LuLookupCreates luCreates;

    @Autowired
    private LuNotificationScopeRepository repository;

    public List<LuNotificationScope> getAll() {
        return repository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    public LuNotificationScope getById(int id) {
        return require(id);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuNotificationScope create(LuNotificationScope entity) {
        return luCreates.create(entity, repository::save);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuNotificationScope update(Integer id, LuNotificationScope entity) {
        LuNotificationScope existing = require(id);
        LuLookupWrites.copyEditable(entity, existing);
        return repository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void delete(Integer id) {
        LuNotificationScope existing = require(id);
        LuLookupWrites.requireDeletable(existing);
        existing.setIsDeleted(true);
        repository.save(existing);
    }

    private LuNotificationScope require(Integer id) {
        return repository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LuNotificationScope not found"));
    }
}
