package com.dilaw.mohe.approval.repository;

import com.dilaw.mohe.approval.model.ApprovalRequest;
import com.dilaw.mohe.approval.model.ApprovalStatus;
import com.dilaw.mohe.approval.model.RequestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

        // Find all requests by status
        List<ApprovalRequest> findByStatus(ApprovalStatus status);

        Page<ApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);

        /*
         * The unscoped `List<ApprovalRequest> findByRequestedBy(Long)` that used to
         * sit here is gone. It returned every country's requests for a user, and
         * left an unscoped path beside the scoped one with a friendlier name.
         * ApprovalWorkflowService.getMyRequests(Long) now goes through the paged
         * query below.
         */

        /**
         * The caller's own requests, within the caller's own country scope.
         *
         * <p>Two filters, and both are needed for different reasons. {@code userId}
         * is identity — it comes from the token, never the query string. The country
         * filter is scope, and it matters when the two diverge: an officer moved from
         * one attaché to another would otherwise keep reading the requests they
         * raised at the old one, including the student data inside them.
         *
         * <p>{@code countryIds} must never be empty — SQL Server rejects {@code IN ()}
         * — and is ignored when {@code unscoped} is true.
         */
        @Query(value = "SELECT r FROM ApprovalRequest r LEFT JOIN FETCH r.config LEFT JOIN FETCH r.requester " +
                       "WHERE r.requestedBy = :userId " +
                       "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                       "AND (:entityType IS NULL OR r.entityType = :entityType) " +
                       "AND (:status IS NULL OR r.status = :status) " +
                       "AND (:unscoped = TRUE OR r.countryId IN :countryIds)",
               countQuery = "SELECT COUNT(r) FROM ApprovalRequest r WHERE r.requestedBy = :userId " +
                       "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                       "AND (:entityType IS NULL OR r.entityType = :entityType) " +
                       "AND (:status IS NULL OR r.status = :status) " +
                       "AND (:unscoped = TRUE OR r.countryId IN :countryIds)")
        Page<ApprovalRequest> findByRequestedBy(@Param("userId") Long userId, Pageable pageable,
                       @Param("requestType") RequestType requestType,
                       @Param("entityType") String entityType,
                       @Param("status") ApprovalStatus status,
                       @Param("unscoped") boolean unscoped,
                       @Param("countryIds") java.util.Collection<Integer> countryIds);

        // Find requests by entity type
        List<ApprovalRequest> findByEntityType(String entityType);

        Page<ApprovalRequest> findByEntityType(String entityType, Pageable pageable);

        // Find pending requests at a specific level for a specific config
        @Query("SELECT r FROM ApprovalRequest r WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                        "AND r.currentLevel = :level AND r.config.id = :configId")
        List<ApprovalRequest> findPendingAtLevel(@Param("configId") Long configId, @Param("level") Integer level);

        // Find all pending/in-progress requests
        @Query("SELECT r FROM ApprovalRequest r WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN}")
        List<ApprovalRequest> findAllPending();

        @Query(value = "SELECT r FROM ApprovalRequest r LEFT JOIN FETCH r.config LEFT JOIN FETCH r.requester " +
                       "WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                       "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                       "AND (:entityType IS NULL OR r.entityType = :entityType)",
               countQuery = "SELECT COUNT(r) FROM ApprovalRequest r WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                       "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                       "AND (:entityType IS NULL OR r.entityType = :entityType)")
        Page<ApprovalRequest> findAllPending(Pageable pageable,
                       @Param("requestType") RequestType requestType,
                       @Param("entityType") String entityType);

        /*
         * The country-filtered queries below used to read
         * "(r.countryId IS NULL OR r.countryId IN :countryIds)", which showed every
         * request with no country to every officer. Nine such rows existed and all
         * nine were somebody's — V046 derived a country for each of them, and
         * ApprovalWorkflowService now stamps the requester's own attaché when a
         * caller supplies none, so a remaining NULL means the request belongs to
         * no country and is ministry-only.
         *
         * This has to agree with ApprovalWorkflowService.inScope(). If the lists
         * admitted a NULL that the detail path refuses, a request would appear in
         * the pending list and 404 when opened.
         */
        @Query(value = "SELECT r FROM ApprovalRequest r LEFT JOIN FETCH r.config LEFT JOIN FETCH r.requester " +
                        "WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                        "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                        "AND (:entityType IS NULL OR r.entityType = :entityType) " +
                        "AND r.countryId IN :countryIds",
               countQuery = "SELECT COUNT(r) FROM ApprovalRequest r WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                        "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                        "AND (:entityType IS NULL OR r.entityType = :entityType) " +
                        "AND r.countryId IN :countryIds")
        Page<ApprovalRequest> findAllPendingWithCountryFilter(Pageable pageable,
                        @Param("countryIds") List<Integer> countryIds,
                        @Param("requestType") RequestType requestType,
                        @Param("entityType") String entityType);

        /*
         * The two "every request" listings behind GET /api/v1/approvals.
         *
         * They carry the same LEFT JOIN FETCH as every other listing above, and for
         * the same reason: ApprovalRequest.config is LAZY and Jackson serialises it,
         * so a row reaching the message converter with an uninitialised proxy throws
         * LazyInitializationException and the endpoint answers 500. This listing was
         * the only one without the fetch — the unscoped branch used the inherited
         * findAll(Pageable), the scoped branch the bare query below — so it failed
         * for every caller whose page held at least one row. An empty page serialises
         * nothing and so appeared to work, which is why an attaché with no requests
         * saw 200 while the ministry saw 500.
         */
        @Query(value = "SELECT r FROM ApprovalRequest r LEFT JOIN FETCH r.config LEFT JOIN FETCH r.requester",
               countQuery = "SELECT COUNT(r) FROM ApprovalRequest r")
        Page<ApprovalRequest> findAllWithDetails(Pageable pageable);

        @Query(value = "SELECT r FROM ApprovalRequest r LEFT JOIN FETCH r.config LEFT JOIN FETCH r.requester " +
                        "WHERE r.countryId IN :countryIds",
               countQuery = "SELECT COUNT(r) FROM ApprovalRequest r WHERE r.countryId IN :countryIds")
        Page<ApprovalRequest> findAllWithCountryFilter(Pageable pageable, @Param("countryIds") List<Integer> countryIds);

        @Query(value = "SELECT r FROM ApprovalRequest r LEFT JOIN FETCH r.config LEFT JOIN FETCH r.requester " +
                       "WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).FINAL} " +
                       "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                       "AND (:entityType IS NULL OR r.entityType = :entityType) " +
                       "AND (:status IS NULL OR r.status = :status)",
               countQuery = "SELECT COUNT(r) FROM ApprovalRequest r WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).FINAL} " +
                       "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                       "AND (:entityType IS NULL OR r.entityType = :entityType) " +
                       "AND (:status IS NULL OR r.status = :status)")
        Page<ApprovalRequest> findCompletedHistory(Pageable pageable,
                       @Param("requestType") RequestType requestType,
                       @Param("entityType") String entityType,
                       @Param("status") ApprovalStatus status);

        @Query(value = "SELECT r FROM ApprovalRequest r LEFT JOIN FETCH r.config LEFT JOIN FETCH r.requester " +
                        "WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).FINAL} " +
                        "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                        "AND (:entityType IS NULL OR r.entityType = :entityType) " +
                        "AND (:status IS NULL OR r.status = :status) " +
                        "AND r.countryId IN :countryIds",
               countQuery = "SELECT COUNT(r) FROM ApprovalRequest r WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).FINAL} " +
                        "AND (:requestType IS NULL OR r.requestType = :requestType) " +
                        "AND (:entityType IS NULL OR r.entityType = :entityType) " +
                        "AND (:status IS NULL OR r.status = :status) " +
                        "AND r.countryId IN :countryIds")
        Page<ApprovalRequest> findCompletedHistoryWithCountryFilter(Pageable pageable,
                        @Param("countryIds") List<Integer> countryIds,
                        @Param("requestType") RequestType requestType,
                        @Param("entityType") String entityType,
                        @Param("status") ApprovalStatus status);

        // Find requests that need approval from a user with a specific role
        @Query("SELECT DISTINCT r FROM ApprovalRequest r " +
                        "JOIN r.steps s " +
                        "JOIN s.level l " +
                        "WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                        "AND s.status = :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).PENDING} " +
                        "AND s.levelOrder = r.currentLevel " +
                        "AND l.requiredRoleId = :roleId")
        List<ApprovalRequest> findPendingForRole(@Param("roleId") Long roleId);

        @Query("SELECT DISTINCT r FROM ApprovalRequest r " +
                        "JOIN r.steps s " +
                        "JOIN s.level l " +
                        "WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                        "AND s.status = :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).PENDING} " +
                        "AND s.levelOrder = r.currentLevel " +
                        "AND l.requiredRoleId IN :roleIds")
        List<ApprovalRequest> findPendingForRoles(@Param("roleIds") List<Long> roleIds);

        // Count pending requests for dashboard
        long countByStatus(ApprovalStatus status);

        @Query("SELECT COUNT(r) FROM ApprovalRequest r WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN}")
        long countPending();

        /**
         * Open requests per entity type, for the dashboard (V070). Same scope rule
         * as {@link #findByRequestedBy}: {@code unscoped} for the ministry, else the
         * caller's countries — a NULL-country row stays ministry-only, as in
         * {@code ApprovalWorkflowService.inScope}.
         */
        @Query("SELECT r.entityType, COUNT(r) FROM ApprovalRequest r " +
                        "WHERE r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                        "AND (:unscoped = true OR r.countryId IN :countryIds) " +
                        "GROUP BY r.entityType ORDER BY COUNT(r) DESC")
        List<Object[]> countPendingByEntityType(@Param("unscoped") boolean unscoped,
                        @Param("countryIds") List<Integer> countryIds);

        @Query("SELECT COUNT(r) FROM ApprovalRequest r WHERE r.requestedBy = :userId " +
                        "AND r.status IN :#{T(com.dilaw.mohe.approval.model.ApprovalStatus).OPEN} " +
                        "AND (:unscoped = true OR r.countryId IN :countryIds)")
        long countOpenRequestedBy(@Param("userId") Long userId, @Param("unscoped") boolean unscoped,
                        @Param("countryIds") List<Integer> countryIds);

        // Find by entity (for checking if there's already a pending request for same
        // entity)
        List<ApprovalRequest> findByEntityTypeAndEntityIdAndStatusIn(String entityType, String entityId,
                        List<ApprovalStatus> statuses);

        // Find all requests (any status) for a given entity
        List<ApprovalRequest> findByEntityTypeAndEntityIdOrderByRequestedAtDesc(String entityType, String entityId);

        // Find all pending requests for a specific entity type
        List<ApprovalRequest> findByEntityTypeAndStatusIn(String entityType, List<ApprovalStatus> statuses);

        @Query("SELECT r FROM ApprovalRequest r LEFT JOIN FETCH r.config WHERE r.id = :id")
        Optional<ApprovalRequest> findByIdWithConfig(@Param("id") Long id);
}
