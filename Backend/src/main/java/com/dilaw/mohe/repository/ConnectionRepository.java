package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.Connection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConnectionRepository extends JpaRepository<Connection, Integer> {

    List<Connection> findByIsActiveTrue();

    List<Connection> findBySourceNodeIdAndIsActiveTrue(Integer sourceNodeId);

    List<Connection> findByTargetNodeIdAndIsActiveTrue(Integer targetNodeId);

    @Query("SELECT c FROM Connection c WHERE c.isActive = true " +
           "AND (c.sourceNodeId = :nodeId OR c.targetNodeId = :nodeId)")
    List<Connection> findConnectionsByNodeId(@Param("nodeId") Integer nodeId);

    @Query("SELECT c FROM Connection c WHERE c.isActive = true " +
           "AND (:connectionType IS NULL OR c.connectionType = :connectionType) " +
           "AND (:minStrength IS NULL OR c.connectionStrength >= :minStrength)")
    List<Connection> searchConnections(@Param("connectionType") String connectionType,
                                        @Param("minStrength") Integer minStrength);

    @Query("SELECT DISTINCT c.connectionType FROM Connection c WHERE c.connectionType IS NOT NULL ORDER BY c.connectionType")
    List<String> findDistinctConnectionTypes();

    @Query("SELECT COUNT(c) FROM Connection c WHERE c.isActive = true AND (c.sourceNodeId = :nodeId OR c.targetNodeId = :nodeId)")
    Long countConnectionsByNodeId(@Param("nodeId") Integer nodeId);
}

