package com.dilaw.mohe.audit;

import com.dilaw.mohe.ums.service.CountryScopeService;
import com.dilaw.mohe.ums.service.CountryScopeService.CountryScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reads of {@code AUDITLOG.AuditTrail}, filtered by the caller's country.
 *
 * <p>Until V047 this table had no country column, and every method here returned
 * every row. <b>11,076 of its 11,544 rows were individual student changes</b>, so
 * any holder of the {@code AUDIT} permission could read the full change history of
 * every student in every country. That was the largest of the ten scope gaps.
 *
 * <p>Every read now goes through a {@link Specification} carrying
 * {@link MoheAuditLogSpec#countryScope}. The derived query methods the repository
 * used to expose are gone rather than left alongside: an unscoped
 * {@code findByTableName} sitting next to a scoped one is an invitation, and the
 * next person to need a query would have had no reason to suspect the difference.
 */
@Service
public class MoheAuditLogService {

    @Autowired private MoheAuditLogRepository auditLogRepository;
    @Autowired private CountryScopeService countryScopeService;

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "changedAt");

    /** Country scope for the caller, as the base of every read. */
    private Specification<MoheAuditLog> scoped() {
        CountryScope scope = countryScopeService.getScope();
        return Specification.where(MoheAuditLogSpec.countryScope(scope));
    }

    /**
     * Writes are unscoped on purpose. Application code barely uses this — the
     * database triggers do the writing and stamp COUNTRY_ID themselves — and an
     * audit record must never be refused because of who is looking.
     */
    @Transactional
    public MoheAuditLog save(MoheAuditLog auditLog) {
        if (auditLog.getChangedAt() == null) {
            auditLog.setChangedAt(LocalDateTime.now());
        }
        return auditLogRepository.save(auditLog);
    }

    public Page<MoheAuditLog> getAllAuditLogs(int page, int size) {
        return getAllAuditLogs(page, size, NEWEST_FIRST);
    }

    /** The screen's column headers choose the order; {@code MoheAuditLogController} vets it. */
    public Page<MoheAuditLog> getAllAuditLogs(int page, int size, Sort sort) {
        return auditLogRepository.findAll(scoped(), PageRequest.of(page, size, sort == null ? NEWEST_FIRST : sort));
    }

    public Page<MoheAuditLog> getAuditLogsWithFilters(
            String tableName, String recordId, String operation, String changedBy,
            LocalDateTime startDate, LocalDateTime endDate, int page, int size) {
        return getAuditLogsWithFilters(tableName, recordId, operation, changedBy,
                startDate, endDate, page, size, NEWEST_FIRST);
    }

    public Page<MoheAuditLog> getAuditLogsWithFilters(
            String tableName, String recordId, String operation, String changedBy,
            LocalDateTime startDate, LocalDateTime endDate, int page, int size, Sort sort) {
        Pageable pageable = PageRequest.of(page, size, sort == null ? NEWEST_FIRST : sort);
        return auditLogRepository.findAll(
                scoped().and(MoheAuditLogSpec.tableName(tableName))
                        .and(MoheAuditLogSpec.recordId(recordId))
                        .and(MoheAuditLogSpec.operation(operation))
                        .and(MoheAuditLogSpec.changedBy(changedBy))
                        .and(MoheAuditLogSpec.changedBetween(startDate, endDate)),
                pageable);
    }

    public List<MoheAuditLog> getAuditLogsByTable(String tableName) {
        return auditLogRepository.findAll(scoped().and(MoheAuditLogSpec.tableName(tableName)), NEWEST_FIRST);
    }

    /**
     * The history of one record. Scoped like everything else — asking by record id
     * is the most direct way to read another country's student, and it was the one
     * path most obviously worth trying.
     */
    public List<MoheAuditLog> getAuditLogsByRecordId(String recordId) {
        return auditLogRepository.findAll(scoped().and(MoheAuditLogSpec.recordId(recordId)), NEWEST_FIRST);
    }

    public List<MoheAuditLog> getAuditLogsByOperation(String operation) {
        return auditLogRepository.findAll(scoped().and(MoheAuditLogSpec.operation(operation)), NEWEST_FIRST);
    }

    public List<MoheAuditLog> getAuditLogsByUser(String changedBy) {
        return auditLogRepository.findAll(scoped().and(MoheAuditLogSpec.changedBy(changedBy)), NEWEST_FIRST);
    }

    /**
     * The two metadata lists feed the page's filter dropdowns, and are scoped for
     * a subtler reason than the lists are: an unscoped set of table names or
     * usernames tells a country officer which tables and which colleagues exist
     * elsewhere, even when they can read none of the rows.
     */
    public List<String> getDistinctTableNames() {
        CountryScope scope = countryScopeService.getScope();
        if (noScopeAtAll(scope)) return List.of();
        return auditLogRepository.findDistinctTableNames(scope.isMinistryUser(), countryIds(scope));
    }

    public List<String> getDistinctChangedBy() {
        CountryScope scope = countryScopeService.getScope();
        if (noScopeAtAll(scope)) return List.of();
        return auditLogRepository.findDistinctChangedBy(scope.isMinistryUser(), countryIds(scope));
    }

    private static boolean noScopeAtAll(CountryScope scope) {
        return !scope.isMinistryUser() && scope.getAllowedCountryIds().isEmpty();
    }

    /** Never empty: SQL Server rejects {@code IN ()}. Ignored when the user is ministry. */
    private static List<Integer> countryIds(CountryScope scope) {
        return scope.isMinistryUser() ? List.of(-1) : scope.getAllowedCountryIds();
    }

    /** Returns null rather than another country's row when the id is out of scope. */
    public MoheAuditLog getById(Long id) {
        return auditLogRepository.findOne(
                scoped().and((root, query, cb) -> cb.equal(root.get("logId"), id))).orElse(null);
    }
}
