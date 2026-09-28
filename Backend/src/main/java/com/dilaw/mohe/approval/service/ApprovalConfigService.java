package com.dilaw.mohe.approval.service;

import com.dilaw.mohe.approval.model.ApprovalConfig;
import com.dilaw.mohe.approval.model.ApprovalLevel;
import com.dilaw.mohe.approval.model.ApprovalLevelApprover;
import com.dilaw.mohe.approval.repository.ApprovalConfigRepository;
import com.dilaw.mohe.approval.repository.ApprovalLevelRepository;
import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserCountry;
import com.dilaw.mohe.ums.repository.UmsUserCountryRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.AdminTierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing approval configurations (admin functionality).
 *
 * <p>Security plan N-03 (6 Sep 2026): a level may require a position and may
 * name its approvers, both drawn from the staff table; saving a level checks
 * that the position exists and that every named approver is an active account
 * holding that position and, when a country is given, that country.
 */
@Service
public class ApprovalConfigService {

    @Autowired
    private ApprovalConfigRepository configRepository;

    @Autowired
    private ApprovalLevelRepository levelRepository;

    @Autowired
    private UmsUserRepository userRepository;

    @Autowired
    private UmsUserCountryRepository userCountryRepository;

    @Autowired
    private AdminTierService adminTierService;

    @Autowired
    private CodeListService codeLists;

    /**
     * Get all configurations.
     */
    public List<ApprovalConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    /**
     * Get all active configurations.
     */
    public List<ApprovalConfig> getActiveConfigs() {
        return configRepository.findByIsActiveTrue();
    }

    /**
     * Get configuration by ID.
     */
    public Optional<ApprovalConfig> getConfigById(Long id) {
        return configRepository.findById(id);
    }

    /**
     * Get configuration by entity type.
     */
    public Optional<ApprovalConfig> getConfigByEntityType(String entityType) {
        return configRepository.findByEntityType(entityType);
    }

    /**
     * Create a new configuration.
     */
    @Transactional
    public ApprovalConfig createConfig(ApprovalConfig config) {
        if (configRepository.existsByEntityType(config.getEntityType())) {
            throw new RuntimeException("Configuration already exists for entity type: " + config.getEntityType());
        }
        config.setCreatedAt(Instant.now());
        return configRepository.save(config);
    }

    /**
     * Update a configuration.
     */
    @Transactional
    public ApprovalConfig updateConfig(Long id, ApprovalConfig updated) {
        ApprovalConfig existing = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + id));

        existing.setEntityTypeAr(updated.getEntityTypeAr());
        existing.setDescription(updated.getDescription());
        existing.setDescriptionAr(updated.getDescriptionAr());
        existing.setTotalLevels(updated.getTotalLevels());
        existing.setIsActive(updated.getIsActive());
        existing.setRequireAllLevels(updated.getRequireAllLevels());
        existing.setUpdatedAt(Instant.now());

        return configRepository.save(existing);
    }

    /**
     * Delete a configuration (soft delete by setting isActive to false).
     */
    @Transactional
    public void deactivateConfig(Long id) {
        ApprovalConfig config = configRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + id));
        config.setIsActive(false);
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);
    }

    /**
     * Get levels for a configuration, approvers loaded and named.
     */
    @Transactional(readOnly = true)
    public List<ApprovalLevel> getLevelsForConfig(Long configId) {
        List<ApprovalLevel> levels = levelRepository.findByConfigIdOrderByLevelOrderAsc(configId);
        levels.forEach(this::nameApprovers);
        return levels;
    }

    /** Copies the relation into the transient JSON list, named (inside a transaction). */
    private void nameApprovers(ApprovalLevel level) {
        List<ApprovalLevelApprover> named = new ArrayList<>();
        for (ApprovalLevelApprover a : level.getApprovers()) {
            userRepository.findById(a.getUserId()).ifPresent(u -> {
                a.setUserName(u.getUserName());
                a.setFullName(u.getFullName());
            });
            named.add(a);
        }
        level.setNamedApprovers(named);
    }

    /**
     * Add a level to a configuration.
     */
    @Transactional
    public ApprovalLevel addLevel(Long configId, ApprovalLevel level) {
        ApprovalConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + configId));

        level.setConfig(config);
        level.setCreatedAt(Instant.now());
        level.setRequiredPositionCode(blankToNull(level.getRequiredPositionCode()));
        if (level.getLevelOrder() == null) {
            level.setLevelOrder(levelRepository.findByConfigIdOrderByLevelOrderAsc(configId).size() + 1);
        }
        List<ApprovalLevelApprover> incoming = level.getNamedApprovers() == null ? List.of()
                : new ArrayList<>(level.getNamedApprovers());
        validateWhoApproves(level, level.getRequiredPositionCode(), incoming);
        level.setApprovers(new ArrayList<>());
        attachApprovers(level, incoming);

        // Update total levels in config
        config.setTotalLevels(config.getTotalLevels() + 1);
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);

        ApprovalLevel saved = levelRepository.save(level);
        nameApprovers(saved);
        return saved;
    }

    /**
     * Update a level.
     */
    @Transactional
    public ApprovalLevel updateLevel(Long levelId, ApprovalLevel updated) {
        ApprovalLevel existing = levelRepository.findById(levelId)
                .orElseThrow(() -> new RuntimeException("Level not found: " + levelId));

        List<ApprovalLevelApprover> incoming = updated.getNamedApprovers() == null ? List.of()
                : new ArrayList<>(updated.getNamedApprovers());
        validateWhoApproves(existing, updated.getRequiredPositionCode(), incoming);

        existing.setLevelName(updated.getLevelName());
        existing.setLevelNameAr(updated.getLevelNameAr());
        // A body that names a role sets it. One that names a position and no role makes
        // the level a position-only one (FIX-PLAN P5-5, D25: the users holding the
        // position approve it). One that names neither keeps the stored role rather than
        // silently turning the level into one that names nobody (live QA H5).
        if (updated.getRequiredRoleId() != null) {
            existing.setRequiredRoleId(updated.getRequiredRoleId());
        } else if (blankToNull(updated.getRequiredPositionCode()) != null) {
            existing.setRequiredRoleId(null);
        }
        existing.setRequiredPositionCode(blankToNull(updated.getRequiredPositionCode()));
        existing.setCanSkip(updated.getCanSkip());
        existing.setIsActive(updated.getIsActive());
        // Named approvers are replaced as a set: orphanRemoval drops the rest.
        existing.getApprovers().clear();
        attachApprovers(existing, incoming);

        ApprovalLevel saved = levelRepository.save(existing);
        nameApprovers(saved);
        return saved;
    }

    /**
     * N-03.b: the position must be a live code and every named approver an
     * active account that holds the position (if set) and the country (if
     * given), unless the approver is ministry-level and so in every country.
     */
    private void validateWhoApproves(ApprovalLevel level, String positionCode, List<ApprovalLevelApprover> approvers) {
        String position = blankToNull(positionCode);
        if (position != null) {
            boolean known = codeLists.rows("position").stream()
                    .anyMatch(r -> position.equals(r.code()) && r.active());
            if (!known) {
                throw new IllegalArgumentException("Unknown or inactive position: " + position);
            }
        }
        if (approvers == null) return;
        Map<Long, UmsUser> seen = new HashMap<>();
        for (ApprovalLevelApprover a : approvers) {
            if (a.getUserId() == null) {
                throw new IllegalArgumentException("A named approver needs a user id.");
            }
            UmsUser user = seen.computeIfAbsent(a.getUserId(), id -> userRepository.findById(id)
                    .filter(u -> !Boolean.TRUE.equals(u.getIsDeleted()))
                    .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + id)));
            if (!UserStatus.ACTIVE.equals(user.getStatus())) {
                throw new IllegalArgumentException("Approver " + user.getUserName() + " is not an active account.");
            }
            if (position != null && !position.equals(user.getPositionCode())) {
                throw new IllegalArgumentException("Approver " + user.getUserName()
                        + " does not hold the position this level requires (" + position + ").");
            }
            if (a.getCountryId() != null && !adminTierService.tierOf(user.getId()).ministryLevel()) {
                boolean holds = userCountryRepository.findByUserIdAndIsActiveTrue(user.getId()).stream()
                        .map(UmsUserCountry::getCountryId)
                        .anyMatch(a.getCountryId()::equals);
                if (!holds) {
                    throw new IllegalArgumentException("Approver " + user.getUserName()
                            + " does not serve country " + a.getCountryId() + ".");
                }
            }
        }
    }

    private static void attachApprovers(ApprovalLevel level, List<ApprovalLevelApprover> approvers) {
        List<ApprovalLevelApprover> fresh = new ArrayList<>();
        if (approvers != null) {
            for (ApprovalLevelApprover a : approvers) {
                boolean duplicate = fresh.stream().anyMatch(f -> f.getUserId().equals(a.getUserId())
                        && java.util.Objects.equals(f.getCountryId(), a.getCountryId()));
                if (duplicate) continue;
                ApprovalLevelApprover row = new ApprovalLevelApprover();
                row.setLevel(level);
                row.setUserId(a.getUserId());
                row.setCountryId(a.getCountryId());
                fresh.add(row);
            }
        }
        level.getApprovers().addAll(fresh);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Reorder levels within a config.
     */
    @Transactional
    public void reorderLevels(Long configId, List<Long> levelIdsInOrder) {
        int order = 1;
        for (Long levelId : levelIdsInOrder) {
            ApprovalLevel level = levelRepository.findById(levelId)
                    .orElseThrow(() -> new RuntimeException("Level not found: " + levelId));
            if (!level.getConfig().getId().equals(configId)) {
                throw new RuntimeException("Level " + levelId + " does not belong to config " + configId);
            }
            level.setLevelOrder(order++);
            levelRepository.save(level);
        }

        // Update total levels
        ApprovalConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + configId));
        config.setTotalLevels(levelIdsInOrder.size());
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);
    }

    /**
     * Delete a level.
     */
    @Transactional
    public void deleteLevel(Long levelId) {
        ApprovalLevel level = levelRepository.findById(levelId)
                .orElseThrow(() -> new RuntimeException("Level not found: " + levelId));

        Long configId = level.getConfig().getId();
        levelRepository.delete(level);

        // Reorder remaining levels
        List<ApprovalLevel> remainingLevels = levelRepository.findByConfigIdOrderByLevelOrderAsc(configId);
        int order = 1;
        for (ApprovalLevel remainingLevel : remainingLevels) {
            remainingLevel.setLevelOrder(order++);
            levelRepository.save(remainingLevel);
        }

        // Update total levels
        ApprovalConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new RuntimeException("Configuration not found: " + configId));
        config.setTotalLevels(remainingLevels.size());
        config.setUpdatedAt(Instant.now());
        configRepository.save(config);
    }
}
