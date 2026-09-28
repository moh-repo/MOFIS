package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.ExtensionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtensionTypeRepository extends JpaRepository<ExtensionType, Integer> {

    /**
     * Find extension type by code
     */
    Optional<ExtensionType> findByExtensionTypeCode(String code);

    /**
     * Resolve an extension type from whatever form was stored/submitted — its code or either
     * localized name — so lookups work regardless of which representation the client sent.
     */
    @Query("SELECT et FROM ExtensionType et WHERE " +
            "LOWER(TRIM(et.extensionTypeCode)) = LOWER(TRIM(:value)) OR " +
            "LOWER(TRIM(et.extensionTypeNameEn)) = LOWER(TRIM(:value)) OR " +
            "LOWER(TRIM(et.extensionTypeNameAr)) = LOWER(TRIM(:value))")
    List<ExtensionType> findByCodeOrName(@Param("value") String value);

    /**
     * Find all active extension types ordered by display order
     */
    @Query("SELECT et FROM ExtensionType et WHERE et.isActive = true ORDER BY et.displayOrder, et.extensionTypeNameEn")
    List<ExtensionType> findAllActiveOrderedByDisplay();

    /**
     * Find all extension types ordered by display order
     */
    @Query("SELECT et FROM ExtensionType et ORDER BY et.displayOrder, et.extensionTypeNameEn")
    List<ExtensionType> findAllOrderedByDisplay();

    /**
     * Check if code exists (for validation)
     */
    boolean existsByExtensionTypeCode(String code);

    /**
     * Check if code exists excluding specific ID (for update validation)
     */
    @Query("SELECT CASE WHEN COUNT(et) > 0 THEN true ELSE false END FROM ExtensionType et WHERE et.extensionTypeCode = :code AND et.extensionTypeId != :id")
    boolean existsByExtensionTypeCodeAndIdNot(String code, Integer id);
}

