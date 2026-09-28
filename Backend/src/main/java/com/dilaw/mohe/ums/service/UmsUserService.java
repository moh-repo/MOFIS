package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.ums.model.UmsUserPermission;
import com.dilaw.mohe.codes.UserStatus;

import com.dilaw.mohe.config.PermissionCatalog;
import com.dilaw.mohe.exception.ResourceNotFoundException;
import com.dilaw.mohe.ums.model.*;
import com.dilaw.mohe.ums.repository.*;
import com.dilaw.mohe.ums.model.UmsPermission;
import com.dilaw.mohe.ums.repository.spec.StaffCountrySpec;
import com.dilaw.mohe.ums.repository.spec.UmsUserSpecs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Set;
import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.exception.LocalizedMessage;

@Service
public class UmsUserService {

    @Autowired
    private UmsUserRepository userRepository;

    @Autowired
    private UmsUserGroupRepository userGroupRepository;

    @Autowired
    private UmsUserRoleRepository userRoleRepository;

    @Autowired
    private UmsUserCountryRepository userCountryRepository;

    @Autowired
    private UmsGroupRepository groupRepository;

    @Autowired
    private UmsRoleRepository roleRepository;

    @Autowired
    private UmsAuditLogService auditLogService;

    @Autowired
    private UmsUserPermissionRepository userPermissionRepository;

    @Autowired
    private UmsPermissionRepository permissionRepository;

    @Autowired
    private CountryScopeService countryScopeService;

    @Autowired
    private UmsPrivilegeGuard privilegeGuard;

    @Autowired
    private LoginAttemptService loginAttempts;

    /** S-05: a leaver's sessions end with the account; lazy because the session service reads users. */
    @Autowired
    @org.springframework.context.annotation.Lazy
    private UmsUserSessionService sessionService;

    /** R-12: tier from roles, with the flags kept in step. */
    @Autowired
    private AdminTierService adminTierService;

    /** R-19: evicted on every change this service makes to what an account holds. */
    @Autowired
    private UserAuthorityCache authorityCache;

    /** R-15: maker/checker pairs refused at assignment time. */
    @Autowired
    private SeparationOfDutiesService separationOfDuties;

    /** R-18: masked account fields (e-mail, phone) are refused on write to a caller who may not edit them. */
    @Autowired
    private com.dilaw.mohe.security.field.FieldAccessService fieldAccess;

    /** S-03: privileged grants are requested and approved by a second administrator. */
    @Autowired
    private PrivilegedChangeService privilegedChanges;

    // ── S-03: four-eyes wrappers, used by the controller ─────────────────────

    /**
     * Applies the role, or raises a {@code USER_PRIVILEGE_CHANGE} request when the
     * role is privileged and the caller is not the super admin. The requester is
     * checked for the right to make the change before the request exists.
     */
    @Transactional
    public Optional<com.dilaw.mohe.approval.model.ApprovalRequest> assignRoleToUserOrRequest(Long userId, Long roleId, Long by) {
        return assignRoleToUserOrRequest(userId, roleId, by, RoleWindow.NONE);
    }

    /**
     * R-21: the window in which a role row counts, and where it came from.
     *
     * @param validFrom     null: from now
     * @param validTo       null: until removed
     * @param delegatedFrom the account whose roles were copied, for a delegation
     * @param reason        why, recorded on the row and in the audit
     */
    public record RoleWindow(LocalDateTime validFrom, LocalDateTime validTo, Long delegatedFrom, String reason) {
        public static final RoleWindow NONE = new RoleWindow(null, null, null, null);

        public boolean isEmpty() {
            return validFrom == null && validTo == null && delegatedFrom == null && reason == null;
        }

        public boolean opensLater(LocalDateTime now) {
            return validFrom != null && validFrom.isAfter(now);
        }

        public String describe() {
            if (isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            if (validFrom != null) sb.append(" from ").append(validFrom.toLocalDate());
            if (validTo != null) sb.append(" until ").append(validTo.toLocalDate());
            if (delegatedFrom != null) sb.append(" delegated from user ").append(delegatedFrom);
            if (reason != null && !reason.isBlank()) sb.append(": ").append(reason);
            return sb.toString();
        }
    }

    public Optional<com.dilaw.mohe.approval.model.ApprovalRequest> assignRoleToUserOrRequest(Long userId, Long roleId, Long by,
                                                                                            RoleWindow window) {
        boolean privileged = privilegedChanges.roleIsPrivileged(roleId);
        if (privileged && privilegedChanges.requiresApproval(by)) {
            require(userId);
            privilegeGuard.assertMayAdminister(userId);
            privilegeGuard.assertMayGrantRole(roleId);
            UmsRole role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
            separationOfDuties.assertUserMayReceive(userId, separationOfDuties.codesOfRole(roleId),
                    "role " + role.getRoleName());
            validateWindow(window);
            return Optional.of(privilegedChanges.request(PrivilegedChangeService.Change.role(userId, roleId, window), by));
        }
        assignRoleToUser(userId, roleId, by, window);
        if (privileged) privilegedChanges.auditDirect(by, PrivilegedChangeService.Change.role(userId, roleId, window));
        return Optional.empty();
    }

    private static void validateWindow(RoleWindow window) {
        if (window == null || window.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        if (window.validTo() != null && !window.validTo().isAfter(now)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "The role's end date must be in the future.");
        }
        if (window.validFrom() != null && window.validTo() != null && !window.validTo().isAfter(window.validFrom())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "The role's end date must be after its start date.");
        }
    }

    @Transactional
    public Optional<com.dilaw.mohe.approval.model.ApprovalRequest> assignUserToGroupOrRequest(Long userId, Long groupId, Long by) {
        boolean privileged = privilegedChanges.groupIsPrivileged(groupId);
        if (privileged && privilegedChanges.requiresApproval(by)) {
            require(userId);
            privilegeGuard.assertMayAdminister(userId);
            privilegeGuard.assertMayGrantGroup(groupId);
            UmsGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
            separationOfDuties.assertUserMayReceive(userId, separationOfDuties.codesOfGroup(groupId),
                    "group " + group.getGroupName());
            return Optional.of(privilegedChanges.request(PrivilegedChangeService.Change.group(userId, groupId), by));
        }
        assignUserToGroup(userId, groupId, by);
        if (privileged) privilegedChanges.auditDirect(by, PrivilegedChangeService.Change.group(userId, groupId));
        return Optional.empty();
    }

    /**
     * Grants the codes that are not privileged at once and raises one request for
     * the privileged ones (when the caller is not the super admin). Returns the
     * request, if any.
     */
    @Transactional
    public Optional<com.dilaw.mohe.approval.model.ApprovalRequest> grantPermissionsToUserOrRequest(
            Long userId, List<Long> permissionIds, Long by, String reason, LocalDateTime expiresAt) {
        List<Long> privileged = permissionIds.stream().filter(privilegedChanges::permissionIsPrivileged).toList();
        List<Long> ordinary = permissionIds.stream().filter(id -> !privileged.contains(id)).toList();
        if (!privileged.isEmpty() && privilegedChanges.requiresApproval(by)) {
            require(userId);
            privilegeGuard.assertMayAdminister(userId);
            List<String> codes = new ArrayList<>();
            for (Long id : privileged) {
                UmsPermission p = permissionRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + id));
                privilegeGuard.assertMayGrantPermission(p.getPermissionCode());
                codes.add(p.getPermissionCode());
            }
            separationOfDuties.assertUserMayReceive(userId, codes, "permissions " + codes);
            grantPermissionsToUser(userId, ordinary, by, reason, expiresAt);
            return Optional.of(privilegedChanges.request(
                    PrivilegedChangeService.Change.permissions(userId, privileged, reason, expiresAt), by));
        }
        grantPermissionsToUser(userId, permissionIds, by, reason, expiresAt);
        if (!privileged.isEmpty()) {
            privilegedChanges.auditDirect(by, PrivilegedChangeService.Change.permissions(userId, privileged, reason, expiresAt));
        }
        return Optional.empty();
    }

    /**
     * Updates the account; a promotion to administrator by anyone but the super
     * admin is split off into a request, the rest of the edit is applied, and the
     * returned account carries the request id.
     */
    @Transactional
    public UmsUser updateUserOrRequest(Long id, UmsUser patch, Long by) {
        UmsUser existing = require(id);
        boolean promotion = Boolean.TRUE.equals(patch.getIsAdmin()) && !Boolean.TRUE.equals(existing.getIsAdmin());
        if (promotion && privilegedChanges.requiresApproval(by)) {
            privilegeGuard.assertMayAdminister(id);
            privilegeGuard.assertMayGrantAdmin(true);
            patch.setIsAdmin(null);
            UmsUser saved = updateUser(id, patch, by);
            com.dilaw.mohe.approval.model.ApprovalRequest request =
                    privilegedChanges.request(PrivilegedChangeService.Change.adminFlag(id), by);
            saved.setPendingApprovalRequestId(request.getId());
            return saved;
        }
        UmsUser saved = updateUser(id, patch, by);
        if (promotion) privilegedChanges.auditDirect(by, PrivilegedChangeService.Change.adminFlag(id));
        return saved;
    }

    /** N-02.c: a deleted account's sessions end with it. */
    @Autowired
    private UmsUserSessionRepository sessionRepository;

    /** R-14: the longest a direct grant may run before it must be renewed. */
    @Value("${ums.direct-grant.max-days:90}")
    private int directGrantMaxDays;

    /** O-25: the last few hashes, so a "new" password cannot be a recent one. */
    @Autowired
    private UmsUserPasswordHistoryRepository passwordHistoryRepository;

    /** How many previous passwords are refused. 0 disables the check. */
    @Value("${ums.password.history-size:5}")
    private int passwordHistorySize;

    /**
     * Refuses a password that matches the account's current hash or any of the
     * last {@code ums.password.history-size} it has had. BCrypt comparison, so
     * the history holds nothing an attacker could use that the account row did
     * not already hold.
     */
    private void rejectRecentlyUsed(UmsUser user, String newPassword) {
        if (passwordHistorySize <= 0) return;
        if (user.getPasswordHash() != null && passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new LocalizedArgumentException("Password must differ from the current password",
                    "ums.user.password.same");
        }
        List<UmsUserPasswordHistory> recent = passwordHistoryRepository.findByUserIdOrderByChangedAtDesc(user.getId());
        for (UmsUserPasswordHistory h : recent.subList(0, Math.min(recent.size(), passwordHistorySize))) {
            if (passwordEncoder.matches(newPassword, h.getPasswordHash())) {
                throw new LocalizedArgumentException(
                        "Password was used recently; choose one not among your last " + passwordHistorySize,
                        "ums.user.password.recent", passwordHistorySize);
            }
        }
    }

    /** Records the hash being replaced and prunes the history to the configured size. */
    private void rememberPassword(Long userId, String outgoingHash) {
        if (passwordHistorySize <= 0 || outgoingHash == null || outgoingHash.isBlank()) return;
        UmsUserPasswordHistory row = new UmsUserPasswordHistory();
        row.setUserId(userId);
        row.setPasswordHash(outgoingHash);
        passwordHistoryRepository.save(row);
        List<UmsUserPasswordHistory> all = passwordHistoryRepository.findByUserIdOrderByChangedAtDesc(userId);
        if (all.size() > passwordHistorySize) {
            passwordHistoryRepository.deleteAll(all.subList(passwordHistorySize, all.size()));
        }
    }

    @Autowired
    private com.dilaw.mohe.service.CountryNameService countryNameService;

    @Autowired
    private MustChangePasswordGate mustChangePasswordGate;

    private final BCryptPasswordEncoder passwordEncoder = com.dilaw.mohe.config.Passwords.encoder();

    // ========== Country scope ==========

    /**
     * The base of every read on this service: not deleted, and administrable by
     * the caller.
     *
     * <p>One place rather than a clause repeated per method, so a new finder
     * cannot be added without it.
     */
    private Specification<UmsUser> scoped() {
        return UmsUserSpecs.notDeleted()
                .and(StaffCountrySpec.administrableBy(countryScopeService.getScope()));
    }

    /**
     * One user by id, or 404 if the caller may not administer them.
     *
     * <p>Not found rather than forbidden, and for the usual reason: user ids are
     * sequential, so a 403 confirms an account exists at that id and tells an
     * enumerating caller exactly which ids are worth attacking. The 403s on this
     * service come from {@link UmsPrivilegeGuard}, which only fires on accounts
     * the caller can already see.
     */
    public UmsUser require(Long id) {
        return userRepository.findOne(scoped().and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    // ========== User CRUD ==========

    public Page<UmsUser> getAllUsers(Pageable pageable) {
        return withCountries(userRepository.findAll(scoped(), pageable));
    }

    /**
     * Optional keyword search (substring), optional status (= Users.Status),
     * optional privilege: ADMIN | REGULAR (elevated vs non‑elevated).
     */
    public Page<UmsUser> listUsers(String keyword, String status, String privilege, Pageable pageable) {
        return withCountries(userRepository.findAll(scoped()
                .and(UmsUserSpecs.keyword(keyword))
                .and(UmsUserSpecs.status(status))
                .and(UmsUserSpecs.privilege(privilege)), pageable));
    }

    public Page<UmsUser> searchUsers(String keyword, Pageable pageable) {
        return withCountries(userRepository.findAll(scoped().and(UmsUserSpecs.keyword(keyword)), pageable));
    }

    public Page<UmsUser> getAdminUsers(Pageable pageable) {
        return withCountries(userRepository.findAll(scoped().and(UmsUserSpecs.adminOnly()), pageable));
    }

    /** Every account the caller may administer, unpaged, for the reports of R-13. */
    public List<UmsUser> visibleUsers() {
        List<UmsUser> users = userRepository.findAll(scoped());
        withCountries(users);
        return users;
    }

    /**
     * N-03.b: active staff holding a position, optionally within one country,
     * among the accounts the caller can see. The approval-configuration screen
     * picks named approvers from this rather than typing names.
     */
    public List<UmsUser> approverCandidates(String positionCode, Integer countryId) {
        Specification<UmsUser> spec = scoped()
                .and(UmsUserSpecs.status(UserStatus.ACTIVE));
        if (positionCode != null && !positionCode.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("positionCode"), positionCode.trim()));
        }
        if (countryId != null) {
            spec = spec.and(UmsUserSpecs.holdsCountry(countryId));
        }
        List<UmsUser> users = userRepository.findAll(spec);
        withCountries(users);
        return users;
    }

    /**
     * Fills in each row's attachés for display, in two queries for the whole page.
     *
     * <p>The list has to show this. Country scope is otherwise invisible on
     * screen — rows are simply absent, with nothing explaining why, and an account
     * awaiting assignment is indistinguishable from a fully assigned one even
     * though the two are visible under completely different rules.
     */
    private Page<UmsUser> withCountries(Page<UmsUser> page) {
        withCountries(page.getContent());
        return page;
    }

    /**
     * The same, for any list of users — the page above, or the single account a
     * detail view asks for.
     */
    private void withCountries(List<UmsUser> users) {
        List<Long> ids = users.stream().map(UmsUser::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return;

        java.util.Map<Long, List<Integer>> byUser = new java.util.HashMap<>();
        for (UmsUserCountry uc : userCountryRepository.findActiveForUsers(ids)) {
            byUser.computeIfAbsent(uc.getUser().getId(), k -> new ArrayList<>()).add(uc.getCountryId());
        }

        java.util.Map<Integer, String> names = countryNameService.nameMap();

        users.forEach(u -> {
            List<Integer> countryIds = byUser.getOrDefault(u.getId(), List.of());
            u.setCountryIds(countryIds);
            // An id with no matching lookup row renders as the id rather than
            // silently disappearing — that is exactly the 1015 case the broken
            // country picker used to produce, and it should be visible.
            u.setCountryNames(countryIds.stream()
                    .map(id -> names.getOrDefault(id, String.valueOf(id)))
                    .toList());
        });
    }

    public Optional<UmsUser> getUserById(Long id) {
        return userRepository.findOne(scoped().and((root, q, cb) -> cb.equal(root.get("id"), id)));
    }

    /**
     * The same account, with its attachés named.
     *
     * <p>Separate from {@link #getUserById} because {@code countryNames} costs two
     * more queries and only one caller wants them: the detail view is what names a
     * user's whole scope — the list shows two of them and a count. Without this it
     * read the empty transient list and reported every account, however wide its
     * scope, as awaiting assignment.
     */
    public Optional<UmsUser> getUserDetail(Long id) {
        Optional<UmsUser> user = getUserById(id);
        user.ifPresent(u -> withCountries(List.of(u)));
        return user;
    }

    /**
     * Deliberately unscoped: this is the login lookup, and it runs before any
     * scope exists to filter by. Scoping it would mean a user has to already be
     * authenticated in order to authenticate.
     */
    public Optional<UmsUser> getUserByUserName(String userName) {
        return userRepository.findByUserNameAndIsDeletedFalse(userName);
    }

    /** As loose as Angular's Validators.email on the form: one @, no spaces, no dot required. */
    private static final java.util.regex.Pattern EMAIL_SHAPE =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+$");

    /**
     * Review M8: the account form took any text as an e-mail or a phone. Checked
     * only when the value is new or changed, so an old account with a malformed
     * number is not blocked from an unrelated edit.
     */
    static void requireContactShape(String email, String oldEmail, String phone, String oldPhone) {
        if (email != null && !email.isBlank() && !email.strip().equals(oldEmail)
                && !EMAIL_SHAPE.matcher(email.strip()).matches()) {
            throw new LocalizedArgumentException("The e-mail address is not valid: " + email.strip(),
                    "ums.user.email.invalid", email.strip());
        }
        if (phone != null && !phone.isBlank() && !phone.strip().equals(oldPhone)
                && !com.dilaw.mohe.util.PhoneNumbers.isValid(phone, false)) {
            throw new LocalizedArgumentException("The phone number must have "
                    + com.dilaw.mohe.util.PhoneNumbers.PHONE_MIN_DIGITS + " to "
                    + com.dilaw.mohe.util.PhoneNumbers.PHONE_MAX_DIGITS + " digits.",
                    "validation.phone", LocalizedMessage.field("phone"),
                    com.dilaw.mohe.util.PhoneNumbers.PHONE_MIN_DIGITS, com.dilaw.mohe.util.PhoneNumbers.PHONE_MAX_DIGITS);
        }
    }

    @Transactional
    public UmsUser createUser(UmsUser user, Long createdBy) {
        // Validate username uniqueness
        if (userRepository.existsByUserName(user.getUserName())) {
            throw new LocalizedArgumentException("Username already exists: " + user.getUserName(),
                    "ums.user.username.exists", user.getUserName());
        }

        // Never accept pre-hashed credentials or privilege escalation from client payload.
        user.setIsSuperAdmin(false);
        user.setIsMinistryUser(false);
        privilegeGuard.assertMayCreateStaff();
        privilegeGuard.assertMayGrantAdmin(user.getIsAdmin());

        // N-02.a (6 Sep 2026): the countries named on the request are validated
        // before the account exists. A country admin must name at least one of
        // its own attachés: an unassigned account is visible to every admin
        // (StaffCountrySpec clause 3), so a country admin creating one and
        // leaving it for anyone to claim was a way out of its own scope. A
        // ministry admin and the super admin may still create an unassigned
        // account and assign it afterwards.
        List<Integer> requestedCountries = user.getCountryIds() == null ? List.of()
                : user.getCountryIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        AdminTier callerTier = privilegeGuard.callerTier();
        if (callerTier == AdminTier.COUNTRY_ADMIN && requestedCountries.isEmpty()) {
            throw new LocalizedArgumentException(
                    "A country administrator must assign the new account to at least one of its own attachés.",
                    "ums.user.country.required");
        }
        for (Integer countryId : requestedCountries) {
            // You cannot hand out an attaché you do not hold yourself.
            countryScopeService.validateCountryAccess(countryId);
        }

        // Hash the password the request states. Always encoded, never taken as
        // given: the old rule skipped encoding for anything already shaped like a
        // bcrypt hash, which would have let a caller install a hash of their
        // choosing. A hash cannot be submitted at all now — `passwordHash` is not
        // a bindable property — so this is the only way in.
        requireContactShape(user.getEmail(), null, user.getPhone(), null);
        // M-8: the browser required both names, the server did not.
        if (user.getFirstName() == null || user.getFirstName().isBlank()
                || user.getLastName() == null || user.getLastName().isBlank()) {
            throw new LocalizedArgumentException("First and last name are required", "ums.user.name.required");
        }

        String submitted = user.getPassword();
        if (submitted == null || submitted.isBlank()) {
            throw new LocalizedArgumentException("A password is required to create an account",
                    "ums.user.password.required");
        }
        // SEC-001: account creation was the one path that skipped the policy every
        // change and reset applies, so an admin could still create "P@ssw0rd"
        // (reproduced live on 22 Sep 2026).
        validatePasswordStrength(submitted, user.getUserName());
        user.setPasswordHash(passwordEncoder.encode(submitted));
        user.setPassword(null);
        user.setPositionCode(blankToNull(user.getPositionCode()));

        user.setCreatedBy(createdBy);
        user.setCreatedAt(LocalDateTime.now());
        user.setPasswordChangedAt(LocalDateTime.now());
        // DEC-04: a password an administrator chose is temporary.
        user.setMustChangePassword(true);

        UmsUser savedUser = userRepository.save(user);

        for (Integer countryId : requestedCountries) {
            UmsUserCountry userCountry = new UmsUserCountry();
            userCountry.setUser(savedUser);
            userCountry.setCountryId(countryId);
            userCountry.setAssignedBy(createdBy);
            userCountry.setIsActive(true);
            userCountryRepository.save(userCountry);
        }
        savedUser.setCountryIds(requestedCountries);

        // R-12: the flag is shorthand for the COUNTRY_ADMIN tier role. A ministry
        // admin is made by granting MINISTRY_ADMIN explicitly afterwards.
        if (Boolean.TRUE.equals(savedUser.getIsAdmin())) {
            ensureTierRole(savedUser, AdminTier.COUNTRY_ADMIN, createdBy);
        }

        // Audit log
        auditLogService.log(createdBy, "CREATE_USER", "USER", savedUser.getId(), null,
                user.getUserName() + (requestedCountries.isEmpty() ? "" : " countries=" + requestedCountries));

        return savedUser;
    }

    @Transactional
    public UmsUser updateUser(Long id, UmsUser userUpdate, Long updatedBy) {
        UmsUser existingUser = require(id);
        privilegeGuard.assertMayAdminister(id);
        privilegeGuard.assertMayGrantAdmin(userUpdate.getIsAdmin());

        String oldValue = existingUser.getUserName();

        // Ignore credential fields and privilege escalation from request body.
        // A password is changed through changePassword or the admin reset, never
        // by editing the account.
        userUpdate.setPassword(null);
        userUpdate.setPasswordHash(null);
        userUpdate.setIsSuperAdmin(null);
        userUpdate.setIsMinistryUser(null);
        fieldAccess.assertMayEdit(UmsUser.class, existingUser, userUpdate, true);
        requireContactShape(userUpdate.getEmail(), existingUser.getEmail(),
                userUpdate.getPhone(), existingUser.getPhone());

        // Update fields
        if (userUpdate.getFirstName() != null) {
            existingUser.setFirstName(userUpdate.getFirstName());
        }
        if (userUpdate.getLastName() != null) {
            existingUser.setLastName(userUpdate.getLastName());
        }
        if (userUpdate.getEmail() != null) {
            existingUser.setEmail(userUpdate.getEmail());
        }
        if (userUpdate.getStatus() != null) {
            // Returning an account to ACTIVE has to lift the ladder with it, or
            // the very next mistyped password puts it straight back.
            boolean reactivating = UserStatus.ACTIVE.equals(userUpdate.getStatus())
                    && !UserStatus.ACTIVE.equals(existingUser.getStatus());
            // S-05: a leaver's sessions and exceptions end with the account.
            boolean leaving = UserStatus.INACTIVE.equals(userUpdate.getStatus())
                    && !UserStatus.INACTIVE.equals(existingUser.getStatus());
            // R6-03: a lock ends the sessions too, as both lockout ladders' last rungs do,
            // so the unlock opens a fresh sign-in and no old token. A lock is not a leaver:
            // the direct grants stay.
            boolean locking = UserStatus.LOCKED.equals(userUpdate.getStatus())
                    && !UserStatus.LOCKED.equals(existingUser.getStatus());
            existingUser.setStatus(userUpdate.getStatus());
            if (reactivating) {
                loginAttempts.clearLock(existingUser);
            }
            if (leaving) {
                leaverCleanup(existingUser, updatedBy, "status set to INACTIVE");
            }
            if (locking) {
                sessionService.endSessionsOf(existingUser.getId());
                auditLogService.log(updatedBy, "SESSION_TERMINATED", "USER", existingUser.getId(), null,
                        "status set to LOCKED");
            }
        }
        if (userUpdate.getIsAdmin() != null && !userUpdate.getIsAdmin().equals(existingUser.getIsAdmin())) {
            existingUser.setIsAdmin(userUpdate.getIsAdmin());
            // R-12: the flag and the tier role move together. Turning it on makes
            // a country admin; turning it off removes both admin tiers. A super
            // admin's flag is not reachable from here (assertMayAdminister).
            if (userUpdate.getIsAdmin()) {
                if (!adminTierService.roleNamesOf(id).contains(AdminTier.MINISTRY_ADMIN.name())) {
                    ensureTierRole(existingUser, AdminTier.COUNTRY_ADMIN, updatedBy);
                }
            } else {
                dropTierRole(existingUser, AdminTier.COUNTRY_ADMIN, updatedBy);
                dropTierRole(existingUser, AdminTier.MINISTRY_ADMIN, updatedBy);
            }
        }
        if (userUpdate.getPhone() != null) {
            existingUser.setPhone(userUpdate.getPhone().isBlank() ? null : userUpdate.getPhone());
        }
        if (userUpdate.getPositionCode() != null) {
            existingUser.setPositionCode(blankToNull(userUpdate.getPositionCode()));
        }

        existingUser.setUpdatedBy(updatedBy);
        existingUser.setUpdatedAt(LocalDateTime.now());

        UmsUser savedUser = userRepository.save(existingUser);
        authorityCache.evict(id);

        // Audit log
        auditLogService.log(updatedBy, "UPDATE_USER", "USER", id, oldValue, savedUser.getUserName());

        return savedUser;
    }

    @Transactional
    public void deleteUser(Long id, Long deletedBy) {
        UmsUser user = require(id);
        privilegeGuard.assertMayAdminister(id);
        privilegeGuard.assertNotSelf(id);

        // Soft delete (N-02.c: recoverable by the ministry; there is no hard delete)
        user.setIsDeleted(true);
        user.setUpdatedBy(deletedBy);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // N-02.c: a deleted account holds no session, and the audit row says
        // which attachés it served.
        List<UmsUserSession> live = sessionRepository.findByUserIdAndIsActiveTrue(id);
        live.forEach(s -> s.setIsActive(false));
        sessionRepository.saveAll(live);
        authorityCache.evict(id);

        List<Integer> countries = userCountryRepository.findByUserIdAndIsActiveTrue(id).stream()
                .map(UmsUserCountry::getCountryId).toList();
        auditLogService.log(deletedBy, "DELETE_USER", "USER", id, user.getUserName(),
                "countries=" + countries + " sessionsEnded=" + live.size());
    }

    /**
     * Minimum bar for any new password, applied at this choke point so the
     * self-service change, the admin reset under /auth and the users-screen
     * reset all get it. Matches the frontend's MIN_PASSWORD_LENGTH = 8
     * (profile.component.ts). Throws IllegalArgumentException, which the
     * GlobalExceptionHandler returns as a 400.
     */
    static void validatePasswordStrength(String newPassword) {
        validatePasswordStrength(newPassword, null);
    }

    /**
     * O-25 (6 Sep 2026): the rules live in {@link PasswordPolicy} — twelve
     * characters, a letter and a digit, not the username, not a common password.
     */
    static void validatePasswordStrength(String newPassword, String username) {
        PasswordPolicy.check(newPassword, username, PasswordPolicy.STAFF_MIN);
    }

    @Transactional
    public void changePassword(Long userId, String newPassword, Long changedBy) {
        // The endpoint behind this is the one that let jo_admin take the
        // superadmin account. Both gates, in the order they fail cheapest.
        UmsUser user = require(userId);
        privilegeGuard.assertMayAdminister(userId);
        // Strength AFTER authorization: a caller who may not administer the
        // account gets the guard's answer, not the password policy's.
        validatePasswordStrength(newPassword, user.getUserName());
        rejectRecentlyUsed(user, newPassword);

        String outgoingHash = user.getPasswordHash();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        // DEC-04: a password someone else set (an administrator's reset) must be
        // replaced at the next sign-in; the holder's own change ends that.
        user.setMustChangePassword(!java.util.Objects.equals(changedBy, userId));
        rememberPassword(user.getId(), outgoingHash);
        user.setUpdatedBy(changedBy);
        userRepository.save(user);
        mustChangePasswordGate.evict(userId);

        // Audit log
        auditLogService.log(changedBy, "CHANGE_PASSWORD", "USER", userId, null, null);
    }

    // ========== Group Assignment ==========

    @Transactional
    public void assignUserToGroup(Long userId, Long groupId, Long assignedBy) {
        UmsUser user = require(userId);
        privilegeGuard.assertMayAdminister(userId);
        privilegeGuard.assertMayGrantGroup(groupId);

        if (userGroupRepository.existsByUserIdAndGroupIdAndIsActiveTrue(userId, groupId)) {
            throw new LocalizedArgumentException("User already in group", "ums.user.group.exists");
        }

        UmsGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        separationOfDuties.assertUserMayReceive(userId, separationOfDuties.codesOfGroup(groupId),
                "group " + group.getGroupName());

        UmsUserGroup userGroup = new UmsUserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setAssignedBy(assignedBy);
        userGroup.setIsActive(true);
        userGroupRepository.save(userGroup);
        syncFlagsFromTierRoles(user, assignedBy);
        authorityCache.evict(userId);

        // Audit log
        auditLogService.log(assignedBy, "ASSIGN_USER_GROUP", "USER_GROUP", userId, null, group.getGroupName());
    }

    @Transactional
    public void removeUserFromGroup(Long userId, Long groupId, Long removedBy) {
        UmsUser user = require(userId);
        privilegeGuard.assertMayAdminister(userId);
        privilegeGuard.assertMayGrantGroup(groupId);

        UmsUserGroup userGroup = userGroupRepository.findByUserIdAndGroupId(userId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("User-Group relationship not found"));

        userGroup.setIsActive(false);
        userGroupRepository.save(userGroup);
        syncFlagsFromTierRoles(user, removedBy);
        authorityCache.evict(userId);

        // Audit log
        auditLogService.log(removedBy, "REMOVE_USER_GROUP", "USER_GROUP", userId, userGroup.getGroup().getGroupName(),
                null);
    }

    public List<UmsGroup> getUserGroups(Long userId) {
        require(userId);
        return groupRepository.findGroupsByUserId(userId);
    }

    // ========== Direct Role Assignment ==========

    @Transactional
    public void assignRoleToUser(Long userId, Long roleId, Long assignedBy) {
        assignRoleToUser(userId, roleId, assignedBy, RoleWindow.NONE);
    }

    /**
     * Assigns a role, with an optional window (R-21). A row whose window opens
     * later is stored inactive and switched on by {@link #openDueRoleWindows};
     * one whose window has closed is switched off by {@link #closeExpiredRoleWindows}.
     * Assigning an already active role with a window replaces the window.
     */
    @Transactional
    public void assignRoleToUser(Long userId, Long roleId, Long assignedBy, RoleWindow window) {
        UmsUser user = require(userId);
        privilegeGuard.assertMayAdminister(userId);
        privilegeGuard.assertMayGrantRole(roleId);
        if (window == null) window = RoleWindow.NONE;
        validateWindow(window);

        // Check if role is already assigned (active or inactive)
        Optional<UmsUserRole> existingUserRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId);

        if (existingUserRole.isPresent() && existingUserRole.get().getIsActive() && window.isEmpty()) {
            // Role already active, return silently (idempotent operation)
            return;
        }

        UmsRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));
        separationOfDuties.assertUserMayReceive(userId, separationOfDuties.codesOfRole(roleId),
                "role " + role.getRoleName());

        LocalDateTime now = LocalDateTime.now();
        boolean activeNow = !window.opensLater(now);
        UmsUserRole userRole;
        String action;
        if (existingUserRole.isPresent()) {
            userRole = existingUserRole.get();
            action = userRole.getIsActive() ? "UPDATE_USER_ROLE_WINDOW" : "REACTIVATE_USER_ROLE";
        } else {
            userRole = new UmsUserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            action = "ASSIGN_USER_ROLE";
        }
        userRole.setAssignedBy(assignedBy);
        userRole.setIsActive(activeNow);
        userRole.setValidFrom(window.validFrom());
        userRole.setValidTo(window.validTo());
        userRole.setDelegatedFrom(window.delegatedFrom());
        userRole.setReason(window.reason());
        userRoleRepository.save(userRole);
        auditLogService.log(assignedBy, action, "USER_ROLE", userId, null,
                role.getRoleName() + window.describe() + (activeNow ? "" : " (not yet open)"));
        syncFlagsFromTierRoles(user, assignedBy);
        authorityCache.evict(userId);
    }

    @Transactional
    public void removeRoleFromUser(Long userId, Long roleId, Long removedBy) {
        UmsUser user = require(userId);
        privilegeGuard.assertMayAdminister(userId);
        privilegeGuard.assertMayGrantRole(roleId);

        UmsUserRole userRole = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new ResourceNotFoundException("User-Role relationship not found"));

        userRole.setIsActive(false);
        // R-21: a row removed by hand has no window left, or the nightly job would open it again.
        userRole.setValidFrom(null);
        userRole.setValidTo(null);
        userRoleRepository.save(userRole);
        syncFlagsFromTierRoles(user, removedBy);
        authorityCache.evict(userId);

        // Audit log
        auditLogService.log(removedBy, "REMOVE_USER_ROLE", "USER_ROLE", userId, userRole.getRole().getRoleName(), null);
    }

    public List<UmsRole> getUserRoles(Long userId) {
        require(userId);
        List<UmsRole> rolesFromGroups = roleRepository.findRolesByUserIdThroughGroups(userId);
        List<UmsRole> directRoles = roleRepository.findRolesByUserIdDirect(userId);
        rolesFromGroups.addAll(directRoles);
        return rolesFromGroups.stream().distinct().toList();
    }

    // ========== Tier roles and flags (R-12) ==========

    /**
     * Keeps the two legacy flags {@code IsAdmin} and {@code IsMinistryUser}
     * equal to what the tier roles say, so code that still reads a flag
     * ({@code UmsUserSpecs.privilege}, the users screen) agrees with the roles.
     * {@code IsSuperAdmin} is never touched here: it is the break-glass column,
     * the only source of the super admin tier, and is changed by a super admin
     * editing the account. An account that is a super admin by flag keeps
     * both other flags set, whatever roles it holds.
     */
    private void syncFlagsFromTierRoles(UmsUser user, Long by) {
        Set<String> roles = adminTierService.roleNamesOf(user.getId());
        boolean superAdmin = Boolean.TRUE.equals(user.getIsSuperAdmin());
        boolean ministryAdmin = roles.contains(AdminTier.MINISTRY_ADMIN.name());
        boolean ministryStaff = roles.contains(AdminTier.MINISTRY_STAFF.name());
        boolean countryAdmin = roles.contains(AdminTier.COUNTRY_ADMIN.name());
        boolean changed = false;
        boolean admin = superAdmin || ministryAdmin || countryAdmin;
        boolean ministry = superAdmin || ministryAdmin || ministryStaff;
        if (!Boolean.valueOf(admin).equals(user.getIsAdmin())) {
            user.setIsAdmin(admin);
            changed = true;
        }
        if (!Boolean.valueOf(ministry).equals(user.getIsMinistryUser())) {
            user.setIsMinistryUser(ministry);
            changed = true;
        }
        if (changed) {
            user.setUpdatedBy(by);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    /** Grants a tier role directly (no guard: the caller has been checked for the flag). */
    private void ensureTierRole(UmsUser user, AdminTier tier, Long by) {
        Optional<UmsRole> role = tier.roleName().flatMap(roleRepository::findByRoleName);
        if (role.isEmpty()) return; // V094 not applied: the flag alone carries the tier
        Optional<UmsUserRole> existing = userRoleRepository.findByUserIdAndRoleId(user.getId(), role.get().getId());
        if (existing.isPresent()) {
            if (!existing.get().getIsActive()) {
                existing.get().setIsActive(true);
                existing.get().setAssignedBy(by);
                userRoleRepository.save(existing.get());
            }
        } else {
            UmsUserRole userRole = new UmsUserRole();
            userRole.setUser(user);
            userRole.setRole(role.get());
            userRole.setAssignedBy(by);
            userRole.setIsActive(true);
            userRoleRepository.save(userRole);
        }
        syncFlagsFromTierRoles(user, by);
        authorityCache.evict(user.getId());
    }

    private void dropTierRole(UmsUser user, AdminTier tier, Long by) {
        Optional<UmsRole> role = tier.roleName().flatMap(roleRepository::findByRoleName);
        if (role.isEmpty()) return;
        userRoleRepository.findByUserIdAndRoleId(user.getId(), role.get().getId())
                .filter(UmsUserRole::getIsActive)
                .ifPresent(ur -> {
                    ur.setIsActive(false);
                    userRoleRepository.save(ur);
                    auditLogService.log(by, "REMOVE_USER_ROLE", "USER_ROLE", user.getId(), role.get().getRoleName(), null);
                });
        authorityCache.evict(user.getId());
    }

    // ========== Country Scope Assignment ==========

    @Transactional
    public void assignCountryToUser(Long userId, Integer countryId, Long assignedBy) {
        UmsUser user = require(userId);
        privilegeGuard.assertMayAdminister(userId);
        // You cannot hand out an attaché you do not hold yourself, or the scope
        // gate could be widened from inside it.
        countryScopeService.validateCountryAccess(countryId);

        if (userCountryRepository.existsByUserIdAndCountryIdAndIsActiveTrue(userId, countryId)) {
            throw new LocalizedArgumentException("Country already assigned to user", "ums.user.country.exists");
        }

        UmsUserCountry userCountry = new UmsUserCountry();
        userCountry.setUser(user);
        userCountry.setCountryId(countryId);
        userCountry.setAssignedBy(assignedBy);
        userCountry.setIsActive(true);
        userCountryRepository.save(userCountry);
        authorityCache.evict(userId);

        // Audit log
        auditLogService.log(assignedBy, "ASSIGN_USER_COUNTRY", "USER_COUNTRY", userId, null, countryId.toString());
        scopeChanged(userId, assignedBy, "country " + countryId + " assigned");
    }

    @Transactional
    public void removeCountryFromUser(Long userId, Integer countryId, Long removedBy) {
        require(userId);
        privilegeGuard.assertMayAdminister(userId);

        UmsUserCountry userCountry = userCountryRepository.findByUserIdAndCountryId(userId, countryId)
                .orElseThrow(() -> new ResourceNotFoundException("User-Country relationship not found"));

        userCountry.setIsActive(false);
        userCountryRepository.save(userCountry);
        authorityCache.evict(userId);

        // Audit log
        auditLogService.log(removedBy, "REMOVE_USER_COUNTRY", "USER_COUNTRY", userId, countryId.toString(), null);
        scopeChanged(userId, removedBy, "country " + countryId + " removed");
    }

    public List<UmsUserCountry> getUserCountries(Long userId) {
        require(userId);
        return userCountryRepository.findByUserIdAndIsActiveTrue(userId);
    }

    // ========== Authentication ==========

    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * Lifts every lock and counter an account carries, and returns it to
     * {@code ACTIVE} if the ladder had closed it.
     *
     * <p>Until August 2026 an administrator could set the status back to
     * {@code ACTIVE} and the failure count stayed where it was, so the next
     * single mistyped password locked the account again — the unlock looked
     * like it had worked and had not.
     */
    @Transactional
    public UmsUser unlockUser(Long userId, Long unlockedBy) {
        UmsUser user = require(userId);
        privilegeGuard.assertMayAdminister(userId);
        if (UserStatus.LOCKED.equals(user.getStatus())) {
            user.setStatus(UserStatus.ACTIVE);
        }
        user.setUpdatedBy(unlockedBy);
        user.setUpdatedAt(LocalDateTime.now());
        loginAttempts.clearLock(user);
        auditLogService.log(unlockedBy, "UNLOCK_USER", "USER", userId, null, user.getUserName());
        return user;
    }

    // ========== Direct Permission Assignment (R-14: time-boxed exceptions) ==========

    /**
     * Grants one code directly. Unless the code is per-user configuration
     * ({@link PermissionCatalog#isPerUserConfiguration}), a reason and an expiry
     * no further away than {@code ums.direct-grant.max-days} are required: a
     * direct grant is an exception to the role model, and an exception without
     * an end date is a role nobody reviews.
     */
    @Transactional
    public void grantPermissionToUser(Long userId, Long permissionId, Long grantedBy,
                                      String reason, LocalDateTime expiresAt) {
        UmsUser user = require(userId);
        privilegeGuard.assertMayAdminister(userId);

        UmsPermission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionId));
        privilegeGuard.assertMayGrantPermission(permission.getPermissionCode());

        if (userPermissionRepository.existsEffective(userId, permissionId)) {
            throw new LocalizedArgumentException("Permission already granted to user", "ums.user.permission.exists");
        }

        boolean configuration = PermissionCatalog.isPerUserConfiguration(permission.getPermissionCode());
        if (!configuration) {
            if (reason == null || reason.isBlank()) {
                throw new LocalizedArgumentException(
                        "A direct grant is an exception and needs a reason (" + permission.getPermissionCode() + ").",
                        "ums.grant.reason.required", permission.getPermissionCode());
            }
            if (expiresAt == null) {
                throw new LocalizedArgumentException("A direct grant needs an expiry date, at most "
                        + directGrantMaxDays + " days ahead (" + permission.getPermissionCode() + ").",
                        "ums.grant.expiry.required", directGrantMaxDays, permission.getPermissionCode());
            }
            LocalDateTime now = LocalDateTime.now();
            if (!expiresAt.isAfter(now)) {
                throw new LocalizedArgumentException("The expiry date must be in the future.", "ums.grant.expiry.past");
            }
            if (expiresAt.isAfter(now.plusDays(directGrantMaxDays))) {
                throw new LocalizedArgumentException("A direct grant may run for at most " + directGrantMaxDays
                        + " days; renew it when it lapses.", "ums.grant.expiry.tooLong", directGrantMaxDays);
            }
        }
        separationOfDuties.assertUserMayReceive(userId, List.of(permission.getPermissionCode()),
                "permission " + permission.getPermissionCode());

        // A lapsed or revoked row for the same pair is reused (the pair is unique).
        UmsUserPermission userPermission = userPermissionRepository.findByUserIdAndPermissionId(userId, permissionId)
                .orElseGet(UmsUserPermission::new);
        userPermission.setUser(user);
        userPermission.setPermission(permission);
        userPermission.setGrantedBy(grantedBy);
        userPermission.setGrantedAt(LocalDateTime.now());
        userPermission.setIsActive(true);
        userPermission.setReason(configuration ? blankToNull(reason) : reason.trim());
        userPermission.setExpiresAt(configuration ? expiresAt : expiresAt);
        userPermissionRepository.save(userPermission);
        authorityCache.evict(userId);

        // Audit log
        auditLogService.log(grantedBy, "GRANT_USER_PERMISSION", "USER_PERMISSION", userId, null,
                permission.getPermissionCode() + (expiresAt == null ? "" : " until " + expiresAt.toLocalDate())
                        + (reason == null || reason.isBlank() ? "" : ": " + reason.trim()));
    }

    /** The pre-R-14 signature: allowed only for per-user configuration codes. */
    @Transactional
    public void grantPermissionToUser(Long userId, Long permissionId, Long grantedBy) {
        grantPermissionToUser(userId, permissionId, grantedBy, null, null);
    }

    @Transactional
    public void revokePermissionFromUser(Long userId, Long permissionId, Long revokedBy) {
        require(userId);
        privilegeGuard.assertMayAdminister(userId);

        UmsUserPermission userPermission = userPermissionRepository.findByUserIdAndPermissionId(userId, permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("User-Permission relationship not found"));

        userPermission.setIsActive(false);
        userPermissionRepository.save(userPermission);
        authorityCache.evict(userId);

        // Audit log
        auditLogService.log(revokedBy, "REVOKE_USER_PERMISSION", "USER_PERMISSION", userId, userPermission.getPermission().getPermissionCode(), null);
    }

    @Transactional
    public void grantPermissionsToUser(Long userId, List<Long> permissionIds, Long grantedBy,
                                       String reason, LocalDateTime expiresAt) {
        for (Long permissionId : permissionIds) {
            if (!userPermissionRepository.existsEffective(userId, permissionId)) {
                grantPermissionToUser(userId, permissionId, grantedBy, reason, expiresAt);
            }
        }
    }

    @Transactional
    public void grantPermissionsToUser(Long userId, List<Long> permissionIds, Long grantedBy) {
        grantPermissionsToUser(userId, permissionIds, grantedBy, null, null);
    }

    @Transactional
    public void revokePermissionsFromUser(Long userId, List<Long> permissionIds, Long revokedBy) {
        for (Long permissionId : permissionIds) {
            if (userPermissionRepository.existsByUserIdAndPermissionIdAndIsActiveTrue(userId, permissionId)) {
                revokePermissionFromUser(userId, permissionId, revokedBy);
            }
        }
    }

    /** The codes a user holds directly and effectively (R-14: lapsed exceptions excluded). */
    @Transactional(readOnly = true)
    public List<UmsPermission> getUserDirectPermissions(Long userId) {
        require(userId);
        // Fetch-joined: the permissions come back as entities, not proxies, which
        // is what the response is serialised from.
        return userPermissionRepository.findActiveWithPermission(userId)
                .stream()
                .filter(UmsUserPermission::isEffective)
                .map(UmsUserPermission::getPermission)
                .filter(p -> p.getIsActive())
                .toList();
    }

    /** The direct-grant rows themselves, with reason and expiry, for the R-13 report. */
    @Transactional(readOnly = true)
    public List<UmsUserPermission> getUserDirectGrants(Long userId) {
        require(userId);
        return userPermissionRepository.findActiveWithPermission(userId);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Shared 403 shape for the few refusals this service raises itself. */
    @SuppressWarnings("unused")
    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    // ========== R-21: role windows and delegation ==========

    /** What a delegation did: the roles copied at once, and the requests raised for the privileged ones. */
    public record DelegationResult(List<String> applied, List<Long> pendingRequestIds, List<String> skipped) {}

    /**
     * Copies {@code fromUserId}'s active roles to {@code toUserId} for a period
     * (R-21, decision D-11): a covering colleague gets exactly what the absent
     * officer has, and loses it again on the end date without anybody
     * remembering to take it back. The caller must be able to administer both
     * accounts; every role is assigned through the same path as a direct
     * assignment, so a privileged role becomes a four-eyes request (S-03) and a
     * separation-of-duties conflict is refused. The end date is required and
     * capped by {@code ums.delegation.max-days}.
     */
    @Transactional
    public DelegationResult delegateRoles(Long fromUserId, Long toUserId, LocalDateTime validFrom, LocalDateTime validTo,
                                          String reason, Long by, int maxDays) {
        if (fromUserId.equals(toUserId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "An account cannot delegate to itself.");
        }
        if (validTo == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "A delegation needs an end date.");
        }
        LocalDateTime start = validFrom == null ? LocalDateTime.now() : validFrom;
        if (validTo.isAfter(start.plusDays(maxDays))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "A delegation may last at most " + maxDays + " days.");
        }
        UmsUser from = require(fromUserId);
        UmsUser to = require(toUserId);
        privilegeGuard.assertMayAdminister(fromUserId);
        privilegeGuard.assertMayAdminister(toUserId);

        List<String> applied = new ArrayList<>();
        List<Long> pending = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        RoleWindow window = new RoleWindow(validFrom, validTo, fromUserId, reason);
        for (UmsUserRole ur : userRoleRepository.findByUserIdAndIsActiveTrue(fromUserId)) {
            UmsRole role = ur.getRole();
            Optional<UmsUserRole> already = userRoleRepository.findByUserIdAndRoleId(toUserId, role.getId());
            if (already.isPresent() && already.get().getIsActive() && already.get().getValidTo() == null) {
                skipped.add(role.getRoleName());
                continue;
            }
            Optional<com.dilaw.mohe.approval.model.ApprovalRequest> request =
                    assignRoleToUserOrRequest(toUserId, role.getId(), by, window);
            if (request.isPresent()) pending.add(request.get().getId());
            else applied.add(role.getRoleName());
        }
        auditLogService.log(by, "ROLE_DELEGATED", "USER", toUserId, from.getUserName(),
                "roles of " + from.getUserName() + " to " + to.getUserName() + window.describe()
                        + "; applied " + applied + ", pending " + pending + ", already held " + skipped);
        return new DelegationResult(applied, pending, skipped);
    }

    /** Switches off every role whose window has closed (nightly; R-21). Returns how many. */
    @Transactional
    public int closeExpiredRoleWindows(LocalDateTime now) {
        int n = 0;
        for (UmsUserRole ur : userRoleRepository.findByIsActiveTrueAndValidToBefore(now)) {
            ur.setIsActive(false);
            userRoleRepository.save(ur);
            Long userId = ur.getUser().getId();
            auditLogService.log(userId, "ROLE_WINDOW_CLOSED", "USER_ROLE", userId, ur.getRole().getRoleName(),
                    "ended " + ur.getValidTo().toLocalDate()
                            + (ur.getDelegatedFrom() != null ? " (delegated from user " + ur.getDelegatedFrom() + ")" : ""));
            syncFlagsFromTierRoles(ur.getUser(), userId);
            authorityCache.evict(userId);
            n++;
        }
        return n;
    }

    /** Switches on every role whose window has opened (nightly; R-21). Returns how many. */
    @Transactional
    public int openDueRoleWindows(LocalDateTime now) {
        int n = 0;
        for (UmsUserRole ur : userRoleRepository.findDueToOpen(now)) {
            ur.setIsActive(true);
            userRoleRepository.save(ur);
            Long userId = ur.getUser().getId();
            auditLogService.log(userId, "ROLE_WINDOW_OPENED", "USER_ROLE", userId, null, ur.getRole().getRoleName()
                    + " from " + ur.getValidFrom().toLocalDate()
                    + (ur.getValidTo() != null ? " until " + ur.getValidTo().toLocalDate() : ""));
            syncFlagsFromTierRoles(ur.getUser(), userId);
            authorityCache.evict(userId);
            n++;
        }
        return n;
    }

    // ========== S-05: joiner / mover / leaver ==========

    /** A moved officer's open sessions carry the old scope; they end here, and the account signs in afresh. */
    private void scopeChanged(Long userId, Long by, String detail) {
        sessionService.endSessionsOf(userId);
        auditLogService.log(by, "SESSION_TERMINATED", "USER", userId, null, "country scope changed: " + detail);
    }

    /** A leaver keeps nothing that lets the account act: sessions end, direct grants lapse, the cache forgets it. */
    private void leaverCleanup(UmsUser user, Long by, String why) {
        sessionService.endSessionsOf(user.getId());
        auditLogService.log(by, "SESSION_TERMINATED", "USER", user.getId(), null, why);
        List<String> cleared = new ArrayList<>();
        for (UmsUserPermission up : userPermissionRepository.findByUserIdAndIsActiveTrue(user.getId())) {
            up.setIsActive(false);
            userPermissionRepository.save(up);
            cleared.add(up.getPermission().getPermissionCode());
        }
        if (!cleared.isEmpty()) {
            auditLogService.log(by, "DIRECT_GRANT_CLEARED", "USER", user.getId(), String.join(",", cleared), null);
        }
        authorityCache.evict(user.getId());
    }

    /**
     * The dormant-account rule (S-05): no sign-in for the configured number of
     * days sets the account INACTIVE with the leaver cleanup. The super admin
     * is never disabled this way (the break-glass account must stay usable).
     */
    @Transactional
    public boolean disableDormant(Long userId, long idleDays) {
        return deactivate(userId, "ACCOUNT_DORMANT",
                "no sign-in for " + idleDays + " days; disabled by the dormant-account job", userId);
    }

    /**
     * Sets an active account INACTIVE with the leaver cleanup, audited as
     * {@code auditAction} by {@code by} (the account itself for a job). The
     * super admin is never disabled this way. Used by the dormant rule (S-05)
     * and the access review's REMOVE (S-04).
     */
    @Transactional
    public boolean deactivate(Long userId, String auditAction, String detail, Long by) {
        UmsUser user = userRepository.findById(userId).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getIsSuperAdmin()) || !UserStatus.ACTIVE.equals(user.getStatus())) {
            return false;
        }
        user.setStatus(UserStatus.INACTIVE);
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(by);
        userRepository.save(user);
        leaverCleanup(user, by, detail);
        auditLogService.log(by, auditAction, "USER", userId, UserStatus.ACTIVE, UserStatus.INACTIVE + " (" + detail + ")");
        return true;
    }

}
