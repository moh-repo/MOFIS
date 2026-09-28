package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.model.*;
import com.dilaw.mohe.ums.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.dilaw.mohe.exception.LocalizedStateException;

@Service
public class UmsGroupService {

    @Autowired
    private UmsGroupRepository groupRepository;

    @Autowired
    private UmsGroupRoleRepository groupRoleRepository;

    @Autowired
    private UmsRoleRepository roleRepository;

    @Autowired
    private UmsAuditLogService auditLogService;

    /** R-19: the roles of a group feed the authorities of every member. */
    @Autowired
    private UserAuthorityCache authorityCache;

    /** R-15: a group is checked through its roles. */
    @Autowired
    private SeparationOfDutiesService separationOfDuties;

    /** N-02.b: a role goes into a group only if the caller could grant it directly. */
    @Autowired
    private UmsPrivilegeGuard privilegeGuard;

    // ========== Group CRUD ==========

    public List<UmsGroup> getAllActiveGroups() {
        return groupRepository.findByIsActiveTrue();
    }

    public Page<UmsGroup> listGroups(String keyword, Boolean isSystem, Pageable pageable) {
        return groupRepository.listGroups(trimNull(keyword), isSystem, pageable);
    }

    private static String trimNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public Page<UmsGroup> getAllGroups(Pageable pageable) {
        return listGroups(null, null, pageable);
    }

    public Page<UmsGroup> searchGroups(String keyword, Pageable pageable) {
        return groupRepository.searchGroups(keyword, pageable);
    }

    public Optional<UmsGroup> getGroupById(Long id) {
        return groupRepository.findById(id);
    }

    public Optional<UmsGroup> getGroupByName(String groupName) {
        return groupRepository.findByGroupName(groupName);
    }

    @Transactional
    public UmsGroup createGroup(UmsGroup group, Long createdBy) {
        // SEC-01: never merge onto an existing group.
        group.setId(null);
        if (groupRepository.existsByGroupName(group.getGroupName())) {
            throw new IllegalArgumentException("Group name already exists: " + group.getGroupName());
        }

        group.setCreatedBy(createdBy);
        group.setCreatedAt(LocalDateTime.now());
        UmsGroup savedGroup = groupRepository.save(group);

        auditLogService.log(createdBy, "CREATE_GROUP", "GROUP", savedGroup.getId(), null, group.getGroupName());

        return savedGroup;
    }

    @Transactional
    public UmsGroup updateGroup(Long id, UmsGroup groupUpdate, Long updatedBy) {
        UmsGroup existingGroup = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id));

        if (existingGroup.getIsSystem()) {
            // 409, not 400 (review M9): the request is well formed; the row is the one that refuses it.
            throw new LocalizedStateException("A system group cannot be edited or deleted.", "ums.group.system");
        }

        String oldValue = existingGroup.getGroupName();

        if (groupUpdate.getGroupName() != null) {
            existingGroup.setGroupName(groupUpdate.getGroupName());
        }
        if (groupUpdate.getGroupNameAr() != null) {
            existingGroup.setGroupNameAr(groupUpdate.getGroupNameAr());
        }
        if (groupUpdate.getDescription() != null) {
            existingGroup.setDescription(groupUpdate.getDescription());
        }
        if (groupUpdate.getDescriptionAr() != null) {
            existingGroup.setDescriptionAr(groupUpdate.getDescriptionAr());
        }
        if (groupUpdate.getIsActive() != null) {
            existingGroup.setIsActive(groupUpdate.getIsActive());
        }

        existingGroup.setUpdatedBy(updatedBy);
        existingGroup.setUpdatedAt(LocalDateTime.now());

        UmsGroup savedGroup = groupRepository.save(existingGroup);
        // SEC-03: deactivating a group changes what every member holds.
        authorityCache.evictAll();

        auditLogService.log(updatedBy, "UPDATE_GROUP", "GROUP", id, oldValue, savedGroup.getGroupName());

        return savedGroup;
    }

    @Transactional
    public void deleteGroup(Long id, Long deletedBy) {
        UmsGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + id));

        if (group.getIsSystem()) {
            throw new LocalizedStateException("A system group cannot be edited or deleted.", "ums.group.system");
        }

        group.setIsActive(false);
        group.setUpdatedBy(deletedBy);
        groupRepository.save(group);
        authorityCache.evictAll();

        auditLogService.log(deletedBy, "DELETE_GROUP", "GROUP", id, group.getGroupName(), null);
    }

    // ========== Role Assignment to Group ==========

    @Transactional
    public void assignRoleToGroup(Long groupId, Long roleId, Long assignedBy) {
        privilegeGuard.assertMayGrantRole(roleId);
        if (groupRoleRepository.existsByGroupIdAndRoleIdAndIsActiveTrue(groupId, roleId)) {
            throw new IllegalArgumentException("Role already assigned to group");
        }
        separationOfDuties.assertGroupMayHold(groupId, roleId);

        UmsGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        UmsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

        UmsGroupRole groupRole = new UmsGroupRole();
        groupRole.setGroup(group);
        groupRole.setRole(role);
        groupRole.setAssignedBy(assignedBy);
        groupRole.setIsActive(true);
        groupRoleRepository.save(groupRole);
        authorityCache.evictAll();

        auditLogService.log(assignedBy, "ASSIGN_GROUP_ROLE", "GROUP_ROLE", groupId, null, role.getRoleName());
    }

    @Transactional
    public void removeRoleFromGroup(Long groupId, Long roleId, Long removedBy) {
        privilegeGuard.assertMayGrantRole(roleId);
        UmsGroupRole groupRole = groupRoleRepository.findByGroupIdAndRoleId(groupId, roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Group-Role relationship not found"));

        groupRole.setIsActive(false);
        groupRoleRepository.save(groupRole);
        authorityCache.evictAll();

        auditLogService.log(removedBy, "REMOVE_GROUP_ROLE", "GROUP_ROLE", groupId, groupRole.getRole().getRoleName(),
                null);
    }

    /** Loaded entities, not {@code UmsGroupRole.role} proxies — see the repository query. */
    @Transactional(readOnly = true)
    public List<UmsRole> getGroupRoles(Long groupId) {
        return roleRepository.findRolesByGroupId(groupId);
    }
}
