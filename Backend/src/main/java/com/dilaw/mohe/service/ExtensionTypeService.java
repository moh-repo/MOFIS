package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.ExtensionTypeDto;
import com.dilaw.mohe.dto.ExtensionTypeRequest;
import com.dilaw.mohe.model.ExtensionType;
import com.dilaw.mohe.repository.ExtensionTypeRepository;
import com.dilaw.mohe.util.SecurityContextHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class ExtensionTypeService {

    private final ExtensionTypeRepository extensionTypeRepository;

    /**
     * Get all extension types
     */
    public List<ExtensionTypeDto> getAllExtensionTypes() {
        return extensionTypeRepository.findAllOrderedByDisplay().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get all active extension types
     */
    public List<ExtensionTypeDto> getAllActiveExtensionTypes() {
        return extensionTypeRepository.findAllActiveOrderedByDisplay().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get extension type by ID
     */
    public ExtensionTypeDto getExtensionTypeById(Integer id) {
        ExtensionType extensionType = extensionTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Extension type not found: " + id));
        return mapToDto(extensionType);
    }

    /**
     * Get extension type by code
     */
    public ExtensionTypeDto getExtensionTypeByCode(String code) {
        ExtensionType extensionType = extensionTypeRepository.findByExtensionTypeCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Extension type not found: " + code));
        return mapToDto(extensionType);
    }

    /**
     * Create new extension type
     */
    @Transactional
    public ExtensionTypeDto createExtensionType(ExtensionTypeRequest request) {
        // Check if code already exists
        if (extensionTypeRepository.existsByExtensionTypeCode(request.getExtensionTypeCode())) {
            throw new IllegalArgumentException("Extension type code already exists: " + request.getExtensionTypeCode());
        }

        ExtensionType extensionType = new ExtensionType();
        applyRequest(extensionType, request, true);
        
        ExtensionType saved = extensionTypeRepository.save(extensionType);
        return mapToDto(saved);
    }

    /**
     * Update extension type
     */
    @Transactional
    public ExtensionTypeDto updateExtensionType(Integer id, ExtensionTypeRequest request) {
        ExtensionType extensionType = extensionTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Extension type not found: " + id));

        // Check if code already exists for another record
        if (extensionTypeRepository.existsByExtensionTypeCodeAndIdNot(request.getExtensionTypeCode(), id)) {
            throw new IllegalArgumentException("Extension type code already exists: " + request.getExtensionTypeCode());
        }

        applyRequest(extensionType, request, false);
        
        ExtensionType saved = extensionTypeRepository.save(extensionType);
        return mapToDto(saved);
    }

    /**
     * Delete extension type
     */
    @Transactional
    public void deleteExtensionType(Integer id) {
        ExtensionType extensionType = extensionTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Extension type not found: " + id));
        extensionTypeRepository.delete(extensionType);
    }

    /**
     * Toggle active status
     */
    @Transactional
    public ExtensionTypeDto toggleActive(Integer id) {
        ExtensionType extensionType = extensionTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Extension type not found: " + id));
        
        extensionType.setIsActive(!extensionType.getIsActive());
        extensionType.setModifiedDate(LocalDateTime.now());
        extensionType.setModifiedBy(SecurityContextHelper.currentUsername());
        
        ExtensionType saved = extensionTypeRepository.save(extensionType);
        return mapToDto(saved);
    }

    /**
     * Apply request data to entity
     */
    private void applyRequest(ExtensionType entity, ExtensionTypeRequest request, boolean isNew) {
        entity.setExtensionTypeCode(request.getExtensionTypeCode());
        entity.setExtensionTypeNameEn(request.getExtensionTypeNameEn());
        entity.setExtensionTypeNameAr(request.getExtensionTypeNameAr());
        entity.setDescription(request.getDescription());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        entity.setDisplayOrder(request.getDisplayOrder());

        if (isNew) {
            entity.setCreatedDate(LocalDateTime.now());
            entity.setCreatedBy(SecurityContextHelper.currentUsername());
        }
        entity.setModifiedDate(LocalDateTime.now());
        entity.setModifiedBy(SecurityContextHelper.currentUsername());
    }

    /**
     * Map entity to DTO
     */
    private ExtensionTypeDto mapToDto(ExtensionType entity) {
        return ExtensionTypeDto.builder()
                .extensionTypeId(entity.getExtensionTypeId())
                .extensionTypeCode(entity.getExtensionTypeCode())
                .extensionTypeNameEn(entity.getExtensionTypeNameEn())
                .extensionTypeNameAr(entity.getExtensionTypeNameAr())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .displayOrder(entity.getDisplayOrder())
                .createdDate(entity.getCreatedDate())
                .createdBy(entity.getCreatedBy())
                .modifiedDate(entity.getModifiedDate())
                .modifiedBy(entity.getModifiedBy())
                .build();
    }
}

