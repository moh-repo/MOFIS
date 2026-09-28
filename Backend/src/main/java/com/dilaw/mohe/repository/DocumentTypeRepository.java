package com.dilaw.mohe.repository;

import com.dilaw.mohe.model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentType, Integer> {
    
    List<DocumentType> findByIsActiveTrueOrderBySortOrderAsc();
    
    Optional<DocumentType> findByDocumentTypeNameAndIsActiveTrue(String documentTypeName);
    
    @Query("SELECT dt FROM DocumentType dt WHERE dt.isActive = true ORDER BY dt.sortOrder ASC, dt.documentTypeName ASC")
    List<DocumentType> findAllActiveOrdered();
    
    @Query("SELECT dt FROM DocumentType dt WHERE dt.documentTypeName = :name AND dt.isActive = true")
    Optional<DocumentType> findActiveByName(@Param("name") String documentTypeName);
    
    boolean existsByDocumentTypeNameAndIsActiveTrue(String documentTypeName);
}
