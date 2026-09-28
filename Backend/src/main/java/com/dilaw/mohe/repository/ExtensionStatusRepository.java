package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ExtensionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtensionStatusRepository extends JpaRepository<ExtensionStatus, Integer> {

    /**
     * Find extension status by code
     */
    Optional<ExtensionStatus> findByExtensionStatusCode(String code);

    /**
     * Find all active extension statuses ordered by display order
     */
    @Query("SELECT es FROM ExtensionStatus es WHERE es.isActive = true ORDER BY es.displayOrder, es.extensionStatusNameEn")
    List<ExtensionStatus> findAllActiveOrderedByDisplay();

    /**
     * Find all extension statuses ordered by display order
     */
    @Query("SELECT es FROM ExtensionStatus es ORDER BY es.displayOrder, es.extensionStatusNameEn")
    List<ExtensionStatus> findAllOrderedByDisplay();

    /**
     * Check if code exists (for validation)
     */
    boolean existsByExtensionStatusCode(String code);

    /**
     * Check if code exists excluding specific ID (for update validation)
     */
    @Query("SELECT CASE WHEN COUNT(es) > 0 THEN true ELSE false END FROM ExtensionStatus es WHERE es.extensionStatusCode = :code AND es.extensionStatusId != :id")
    boolean existsByExtensionStatusCodeAndIdNot(String code, Integer id);
}

