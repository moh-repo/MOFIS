package com.dilaw.mohe.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Reads go through {@link JpaSpecificationExecutor} so that
 * {@link MoheAuditLogSpec#countryScope} is part of every one of them.
 *
 * <p>The derived finders that used to live here — {@code findByTableName},
 * {@code findByRecordId}, {@code findByOperation}, {@code findByChangedBy} and
 * their paged twins — were removed rather than kept. Each returned every country's
 * rows, and leaving them next to the scoped path would mean the next caller picks
 * whichever name reads best.
 *
 * <p>The two projections below stay as queries because they are the exception: a
 * distinct list of a single column, where loading the entities to reduce them in
 * memory would mean fetching eleven thousand rows to produce six strings.
 */
@Repository
public interface MoheAuditLogRepository extends JpaRepository<MoheAuditLog, Long>,
        JpaSpecificationExecutor<MoheAuditLog> {

    /**
     * Table names present in the caller's scope, for the page's filter dropdown.
     *
     * <p>{@code countryIds} must never be empty — SQL Server rejects {@code IN ()}.
     * It is ignored when {@code unscoped} is true.
     */
    @Query("""
            SELECT DISTINCT a.tableName FROM MoheAuditLog a
            WHERE (:unscoped = TRUE OR a.countryId IN :countryIds)
            ORDER BY a.tableName
            """)
    List<String> findDistinctTableNames(@Param("unscoped") boolean unscoped,
                                        @Param("countryIds") Collection<Integer> countryIds);

    /** Users who changed something in the caller's scope. Same contract as above. */
    @Query("""
            SELECT DISTINCT a.changedBy FROM MoheAuditLog a
            WHERE a.changedBy IS NOT NULL
              AND (:unscoped = TRUE OR a.countryId IN :countryIds)
            ORDER BY a.changedBy
            """)
    List<String> findDistinctChangedBy(@Param("unscoped") boolean unscoped,
                                       @Param("countryIds") Collection<Integer> countryIds);
}
