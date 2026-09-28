package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.WafarReconciliation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WafarReconciliationRepository extends JpaRepository<WafarReconciliation, Integer>,
        JpaSpecificationExecutor<WafarReconciliation> {

    Page<WafarReconciliation> findAll(Pageable pageable);

    List<WafarReconciliation> findByStudentId(Integer studentId);

    /**
     * Every row for the given students, oldest first, so a reconciliation run can
     * find the row it already opened for a student instead of adding another.
     */
    List<WafarReconciliation> findByStudentIdInOrderByReconIdAsc(Collection<Integer> studentIds);

    Page<WafarReconciliation> findByReconStatus(String status, Pageable pageable);

    @Query("SELECT r FROM WafarReconciliation r WHERE r.delta != 0 AND r.reconStatus != 'RESOLVED'")
    List<WafarReconciliation> findUnresolvedDiscrepancies();

    /** As {@link #findUnresolvedDiscrepancies()}, paged — the list is one row per student with a delta. */
    @Query("SELECT r FROM WafarReconciliation r WHERE r.delta != 0 AND r.reconStatus != 'RESOLVED'")
    Page<WafarReconciliation> findUnresolvedDiscrepancies(Pageable pageable);

    long countByReconStatus(String status);
}
