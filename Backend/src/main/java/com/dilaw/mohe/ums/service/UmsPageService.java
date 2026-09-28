package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.*;
import com.dilaw.mohe.ums.repository.*;
import com.dilaw.mohe.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UmsPageService {

    @Autowired
    private UmsPageRepository pageRepository;

    @Autowired
    private UmsPageSectionRepository sectionRepository;

    @Autowired
    private UmsFieldRepository fieldRepository;

    @Autowired
    private UmsPermissionRepository permissionRepository;

    // ========== Page CRUD ==========

    public List<UmsPage> getAllActivePages() {
        return pageRepository.findByIsActiveTrueOrderByDisplayOrder();
    }

    public List<UmsPage> getVisiblePages() {
        return pageRepository.findByIsActiveTrueAndIsVisibleTrueOrderByDisplayOrder();
    }

    public List<UmsPage> getPagesByParent(Long parentPageId) {
        if (parentPageId == null) {
            return pageRepository.findByParentResourceIsNullAndIsActiveTrueOrderByDisplayOrder();
        } else {
            return pageRepository.findByParentResourceIdAndIsActiveTrueOrderByDisplayOrder(parentPageId);
        }
    }

    public Page<UmsPage> listPages(String keyword, Boolean visible, Pageable pageable) {
        return pageRepository.listPages(trimNull(keyword), visible, pageable);
    }

    private static String trimNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public Page<UmsPage> getAllPages(Pageable pageable) {
        return listPages(null, null, pageable);
    }

    public Optional<UmsPage> getPageById(Long id) {
        return pageRepository.findById(id);
    }

    public Optional<UmsPage> getPageByCode(String pageCode) {
        return pageRepository.findByResourceCode(pageCode);
    }

    public Optional<UmsPage> getPageByRoute(String route) {
        return pageRepository.findByRoute(route);
    }

    @Transactional
    public UmsPage createPage(UmsPage page) {
        if (pageRepository.existsByResourceCode(page.getResourceCode())) {
            throw new IllegalStateException("Page code already exists: " + page.getPageCode());
        }

        if (page.getParentResource() != null && page.getParentResource().getId() != null) {
            UmsPage parent = pageRepository.findById(page.getParentResource().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent page not found: " + page.getParentResource().getId()));
            page.setParentResource(parent);
        }

        page.setCreatedAt(LocalDateTime.now());
        UmsPage savedPage = pageRepository.save(page);

        // Auto-generate permissions for the new page
        generatePermissionsForPage(savedPage);

        return savedPage;
    }

    @Transactional
    public UmsPage updatePage(Long id, UmsPage pageUpdate) {
        UmsPage existingPage = pageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Page not found: " + id));

        // pageCode (resourceCode) is immutable after creation

        if (pageUpdate.getPageName() != null) {
            existingPage.setPageName(pageUpdate.getPageName());
        }
        if (pageUpdate.getPageNameAr() != null) {
            existingPage.setPageNameAr(pageUpdate.getPageNameAr());
        }
        if (pageUpdate.getDescription() != null) {
            existingPage.setDescription(pageUpdate.getDescription());
        }
        if (pageUpdate.getRoute() != null) {
            existingPage.setRoute(pageUpdate.getRoute());
        }
        if (pageUpdate.getIcon() != null) {
            existingPage.setIcon(pageUpdate.getIcon());
        }
        if (pageUpdate.getDisplayOrder() != null) {
            existingPage.setDisplayOrder(pageUpdate.getDisplayOrder());
        }
        if (pageUpdate.getIsActive() != null) {
            existingPage.setIsActive(pageUpdate.getIsActive());
        }
        if (pageUpdate.getIsVisible() != null) {
            existingPage.setIsVisible(pageUpdate.getIsVisible());
        }

        Long parentPageId = pageUpdate.getParentPageId();
        if (parentPageId != null) {
            if (parentPageId.equals(id)) {
                throw new IllegalArgumentException("Page cannot be its own parent");
            }
            UmsPage parent = pageRepository.findById(parentPageId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent page not found: " + parentPageId));
            existingPage.setParentResource(parent);
        } else {
            existingPage.setParentResource(null);
        }

        existingPage.setUpdatedAt(LocalDateTime.now());

        return pageRepository.save(existingPage);
    }

    @Transactional
    public void deletePage(Long id) {
        UmsPage page = pageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Page not found: " + id));

        page.setIsActive(false);
        pageRepository.save(page);
    }

    // ========== Section Management ==========

    public List<UmsPageSection> getPageSections(Long pageId) {
        return sectionRepository.findByParentResourceIdAndIsActiveTrueOrderByDisplayOrder(pageId);
    }

    @Transactional
    public UmsPageSection createSection(UmsPageSection section) {
        section.setCreatedAt(LocalDateTime.now());
        return sectionRepository.save(section);
    }

    @Transactional
    public UmsPageSection updateSection(Long id, UmsPageSection sectionUpdate) {
        UmsPageSection existingSection = sectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section not found: " + id));

        if (sectionUpdate.getSectionName() != null) {
            existingSection.setSectionName(sectionUpdate.getSectionName());
        }
        if (sectionUpdate.getSectionNameAr() != null) {
            existingSection.setSectionNameAr(sectionUpdate.getSectionNameAr());
        }
        if (sectionUpdate.getDisplayOrder() != null) {
            existingSection.setDisplayOrder(sectionUpdate.getDisplayOrder());
        }
        if (sectionUpdate.getIsActive() != null) {
            existingSection.setIsActive(sectionUpdate.getIsActive());
        }

        existingSection.setUpdatedAt(LocalDateTime.now());

        return sectionRepository.save(existingSection);
    }

    // ========== Field Management ==========

    public List<UmsField> getPageFields(Long pageId) {
        return fieldRepository.findByParentResourceIdAndIsActiveTrue(pageId);
    }

    public List<UmsField> getSectionFields(Long sectionId) {
        return fieldRepository.findByParentResourceIdAndIsActiveTrue(sectionId);
    }

    @Transactional
    public UmsField createField(UmsField field) {
        // The page the request named. Without this the field is saved with no
        // parent and generatePermissionsForField dereferences a null page, which
        // is what made every Add on the Fields screen a 500.
        if (field.getPage() == null) {
            Long pageId = field.getRequestedPageId();
            if (pageId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The page this field belongs to is required (pageId).");
            }
            UmsPage page = pageRepository.findById(pageId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No page with id " + pageId + "."));
            field.setPage(page);
        }
        field.setCreatedAt(LocalDateTime.now());
        UmsField savedField = fieldRepository.save(field);

        // Auto-generate permissions for the field
        generatePermissionsForField(savedField);

        return savedField;
    }

    @Transactional
    public void deleteField(Long id) {
        UmsField field = fieldRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found: " + id));
        field.setIsActive(false);
        field.setUpdatedAt(LocalDateTime.now());
        fieldRepository.save(field);
    }

    @Transactional
    public UmsField updateField(Long id, UmsField fieldUpdate) {
        UmsField existingField = fieldRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found: " + id));

        if (fieldUpdate.getFieldName() != null) {
            existingField.setFieldName(fieldUpdate.getFieldName());
        }
        if (fieldUpdate.getFieldNameAr() != null) {
            existingField.setFieldNameAr(fieldUpdate.getFieldNameAr());
        }
        if (fieldUpdate.getFieldType() != null) {
            existingField.setFieldType(fieldUpdate.getFieldType());
        }
        if (fieldUpdate.getIsSensitive() != null) {
            existingField.setIsSensitive(fieldUpdate.getIsSensitive());
        }
        if (fieldUpdate.getIsActive() != null) {
            existingField.setIsActive(fieldUpdate.getIsActive());
        }

        existingField.setUpdatedAt(LocalDateTime.now());

        return fieldRepository.save(existingField);
    }

    // ========== Permission Generation ==========

    private void generatePermissionsForPage(UmsPage page) {
        String[] actions = { "SEARCH", "VIEW", "ADD", "EDIT", "DELETE", "EXPORT" };

        for (String action : actions) {
            String permissionCode = page.getPageCode() + "." + action;
            if (!permissionRepository.existsByPermissionCode(permissionCode)) {
                UmsPermission permission = new UmsPermission();
                permission.setPermissionCode(permissionCode);
                permission.setPermissionName(page.getPageName() + " - " + action);
                permission.setResourceType("PAGE");
                permission.setResource(page);
                permission.setActionType(action);
                permission.setDescription(action + " permission for " + page.getPageName());
                permission.setIsActive(true);
                permission.setCreatedAt(LocalDateTime.now());
                permissionRepository.save(permission);
            }
        }
    }

    private void generatePermissionsForField(UmsField field) {
        String[] actions = { "VIEW", "EDIT" };
        String pageCode = field.getPage().getPageCode();

        for (String action : actions) {
            String permissionCode = pageCode + "." + field.getFieldCode() + "." + action;
            if (!permissionRepository.existsByPermissionCode(permissionCode)) {
                UmsPermission permission = new UmsPermission();
                permission.setPermissionCode(permissionCode);
                permission.setPermissionName(field.getFieldName() + " - " + action);
                permission.setResourceType("FIELD");
                permission.setResource(field);
                permission.setActionType(action);
                permission.setDescription(action + " permission for field " + field.getFieldName());
                permission.setIsActive(true);
                permission.setCreatedAt(LocalDateTime.now());
                permissionRepository.save(permission);
            }
        }
    }
}
