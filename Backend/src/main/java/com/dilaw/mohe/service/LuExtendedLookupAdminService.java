package com.dilaw.mohe.service;

import com.dilaw.mohe.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.model.*;
import com.dilaw.mohe.repository.*;
import com.dilaw.mohe.util.LuLookupWrites;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin CRUD for V004/V005 bilingual lookup tables exposed on LookupTableController.
 *
 * <p>The writes go through {@link LuLookupWrites}, which is where the rule lives
 * that a row a Java constant names cannot be renamed or deleted — the same rule
 * the Code Lists screen enforces, and the reason the application can still start
 * after someone has been through this screen.
 */
@Transactional(readOnly = true)
@Service
public class LuExtendedLookupAdminService {

    /** Restores a deleted code instead of refusing it as a duplicate; checks the code's width (QA B15). */
    @Autowired private LuLookupCreates luCreates;

    @Autowired private LuBooksAllowancePayStatusRepository booksAllowancePayStatusRepository;
    @Autowired private LuLoanStatusRepository loanStatusRepository;
    @Autowired private LuReportDimensionRepository reportDimensionRepository;
    @Autowired private LuTrendClassRepository trendClassRepository;
    @Autowired private LuReportLabelRepository reportLabelRepository;
    @Autowired private LuDeathProcedureRepository deathProcedureRepository;

    public List<LuBooksAllowancePayStatus> getAllBooksAllowancePayStatuses() {
        return booksAllowancePayStatusRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuBooksAllowancePayStatus createBooksAllowancePayStatus(LuBooksAllowancePayStatus entity) {
        return luCreates.create(entity, booksAllowancePayStatusRepository::save);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuBooksAllowancePayStatus updateBooksAllowancePayStatus(Integer id, LuBooksAllowancePayStatus entity) {
        LuBooksAllowancePayStatus existing = requireBooksAllowancePayStatus(id);
        LuLookupWrites.copyEditable(entity, existing);
        return booksAllowancePayStatusRepository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void deleteBooksAllowancePayStatus(Integer id) {
        LuBooksAllowancePayStatus existing = requireBooksAllowancePayStatus(id);
        LuLookupWrites.requireDeletable(existing);
        existing.setIsDeleted(true);
        booksAllowancePayStatusRepository.save(existing);
    }

    public List<LuLoanStatus> getAllLoanStatuses() {
        return loanStatusRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuLoanStatus createLoanStatus(LuLoanStatus entity) {
        return luCreates.create(entity, loanStatusRepository::save);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuLoanStatus updateLoanStatus(Integer id, LuLoanStatus entity) {
        LuLoanStatus existing = requireLoanStatus(id);
        LuLookupWrites.copyEditable(entity, existing);
        return loanStatusRepository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void deleteLoanStatus(Integer id) {
        LuLoanStatus existing = requireLoanStatus(id);
        LuLookupWrites.requireDeletable(existing);
        existing.setIsDeleted(true);
        loanStatusRepository.save(existing);
    }

    public List<LuReportDimension> getAllReportDimensions() {
        return reportDimensionRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuReportDimension createReportDimension(LuReportDimension entity) {
        return luCreates.create(entity, reportDimensionRepository::save);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuReportDimension updateReportDimension(Integer id, LuReportDimension entity) {
        LuReportDimension existing = requireReportDimension(id);
        LuLookupWrites.copyEditable(entity, existing);
        existing.setGroupCode(entity.getGroupCode());
        return reportDimensionRepository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void deleteReportDimension(Integer id) {
        LuReportDimension existing = requireReportDimension(id);
        LuLookupWrites.requireDeletable(existing);
        existing.setIsDeleted(true);
        reportDimensionRepository.save(existing);
    }

    public List<LuTrendClass> getAllTrendClasses() {
        return trendClassRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuTrendClass createTrendClass(LuTrendClass entity) {
        return luCreates.create(entity, trendClassRepository::save);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuTrendClass updateTrendClass(Integer id, LuTrendClass entity) {
        LuTrendClass existing = requireTrendClass(id);
        LuLookupWrites.copyEditable(entity, existing);
        existing.setCategory(entity.getCategory());
        return trendClassRepository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void deleteTrendClass(Integer id) {
        LuTrendClass existing = requireTrendClass(id);
        LuLookupWrites.requireDeletable(existing);
        existing.setIsDeleted(true);
        trendClassRepository.save(existing);
    }

    public List<LuReportLabel> getAllReportLabels() {
        return reportLabelRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuReportLabel createReportLabel(LuReportLabel entity) {
        return luCreates.create(entity, reportLabelRepository::save);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuReportLabel updateReportLabel(Integer id, LuReportLabel entity) {
        LuReportLabel existing = requireReportLabel(id);
        LuLookupWrites.copyEditable(entity, existing);
        return reportLabelRepository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void deleteReportLabel(Integer id) {
        LuReportLabel existing = requireReportLabel(id);
        LuLookupWrites.requireDeletable(existing);
        existing.setIsDeleted(true);
        reportLabelRepository.save(existing);
    }

    public List<LuDeathProcedure> getAllDeathProcedures() {
        return deathProcedureRepository.findByIsDeletedFalseOrderBySortOrderAsc();
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuDeathProcedure createDeathProcedure(LuDeathProcedure entity) {
        return luCreates.create(entity, deathProcedureRepository::save);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public LuDeathProcedure updateDeathProcedure(Integer id, LuDeathProcedure entity) {
        LuDeathProcedure existing = requireDeathProcedure(id);
        LuLookupWrites.copyEditable(entity, existing);
        return deathProcedureRepository.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void deleteDeathProcedure(Integer id) {
        LuDeathProcedure existing = requireDeathProcedure(id);
        LuLookupWrites.requireDeletable(existing);
        existing.setIsDeleted(true);
        deathProcedureRepository.save(existing);
    }

    /*
     * Read-by-id for the six lists this service fronts (live test L9, 23 Sep 2026:
     * GET /{id} answered 405). Each is the lookup the update makes, so a retired
     * row is a 404 here too.
     */
    public LuBooksAllowancePayStatus getBooksAllowancePayStatusById(Integer id) {
        return requireBooksAllowancePayStatus(id);
    }

    public LuLoanStatus getLoanStatusById(Integer id) {
        return requireLoanStatus(id);
    }

    public LuReportDimension getReportDimensionById(Integer id) {
        return requireReportDimension(id);
    }

    public LuTrendClass getTrendClassById(Integer id) {
        return requireTrendClass(id);
    }

    public LuReportLabel getReportLabelById(Integer id) {
        return requireReportLabel(id);
    }

    public LuDeathProcedure getDeathProcedureById(Integer id) {
        return requireDeathProcedure(id);
    }

    private LuBooksAllowancePayStatus requireBooksAllowancePayStatus(Integer id) {
        return booksAllowancePayStatusRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Books allowance pay status not found: " + id));
    }

    private LuLoanStatus requireLoanStatus(Integer id) {
        return loanStatusRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Loan status not found: " + id));
    }

    private LuReportDimension requireReportDimension(Integer id) {
        return reportDimensionRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Report dimension not found: " + id));
    }

    private LuTrendClass requireTrendClass(Integer id) {
        return trendClassRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Trend class not found: " + id));
    }

    private LuReportLabel requireReportLabel(Integer id) {
        return reportLabelRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Report label not found: " + id));
    }

    private LuDeathProcedure requireDeathProcedure(Integer id) {
        return deathProcedureRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Death procedure not found: " + id));
    }
}
