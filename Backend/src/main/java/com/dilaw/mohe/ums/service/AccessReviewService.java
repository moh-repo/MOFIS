package com.dilaw.mohe.ums.service;

import com.dilaw.mohe.codes.UserStatus;
import com.dilaw.mohe.model.StudyCountryIn;
import com.dilaw.mohe.repository.StudyCountryInRepository;
import com.dilaw.mohe.security.SecurityEventLog;
import com.dilaw.mohe.security.policy.AccessSubject;
import com.dilaw.mohe.security.policy.AccessSubjectResolver;
import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsAccessReview;
import com.dilaw.mohe.ums.model.UmsAccessReviewItem;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.model.UmsUserCountry;
import com.dilaw.mohe.ums.model.UmsUserPermission;
import com.dilaw.mohe.ums.model.UmsUserRole;
import com.dilaw.mohe.ums.repository.UmsAccessReviewItemRepository;
import com.dilaw.mohe.ums.repository.UmsAccessReviewRepository;
import com.dilaw.mohe.ums.repository.UmsUserCountryRepository;
import com.dilaw.mohe.ums.repository.UmsUserPermissionRepository;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.repository.UmsUserRoleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The quarterly access review (security plan S-04).
 *
 * <p>Each period, one review opens per attaché with active staff, plus one for
 * the ministry's own accounts. Every item holds a snapshot of what the account
 * had when the review opened - tier, position, roles, countries, direct grants,
 * last sign-in - and waits for an attestation: KEEP, CHANGE (the administrator
 * adjusts the account separately) or REMOVE (the account is set INACTIVE with
 * the leaver cleanup, S-05). The country admin attests their own attaché's
 * list and the ministry admin the ministry's; the super admin may attest
 * either. After the due date an undecided item is flagged ({@code ACCESS_REVIEW_OVERDUE})
 * and, when {@code ums.access-review.disable-unattested} is on, the account is
 * disabled. Every attestation is audited on the account it concerns.
 */
@Service
public class AccessReviewService {

    public static final Set<String> DECISIONS = Set.of("KEEP", "CHANGE", "REMOVE");

    public record Snapshot(String userName, String fullName, String status, String tier, String positionCode,
                           List<String> roles, List<String> countries, LocalDateTime lastLoginAt,
                           List<String> directGrants) {}

    public record ReviewView(Long id, String period, Integer countryId, String countryName, String status,
                             LocalDateTime openedAt, LocalDateTime dueAt, LocalDateTime closedAt,
                             long total, long undecided) {}

    public record ItemView(Long id, Long userId, Snapshot snapshot, String decision, Long decidedBy,
                           LocalDateTime decidedAt, String note, boolean overdue) {}

    public record OverdueOutcome(int flagged, int disabled) {}

    private final UmsAccessReviewRepository reviews;
    private final UmsAccessReviewItemRepository items;
    private final UmsUserRepository users;
    private final UmsUserRoleRepository userRoles;
    private final UmsUserCountryRepository userCountries;
    private final UmsUserPermissionRepository userPermissions;
    private final StudyCountryInRepository countries;
    private final AdminTierService adminTierService;
    private final AccessSubjectResolver subjects;
    private final UmsUserService userService;
    private final UmsAuditLogService auditLogService;
    private final SecurityEventLog securityEventLog;
    private final ObjectMapper objectMapper;
    private final int dueDays;
    private final boolean disableUnattested;

    public AccessReviewService(UmsAccessReviewRepository reviews, UmsAccessReviewItemRepository items,
                               UmsUserRepository users, UmsUserRoleRepository userRoles,
                               UmsUserCountryRepository userCountries, UmsUserPermissionRepository userPermissions,
                               StudyCountryInRepository countries, AdminTierService adminTierService,
                               AccessSubjectResolver subjects,
                               @org.springframework.context.annotation.Lazy UmsUserService userService,
                               UmsAuditLogService auditLogService, SecurityEventLog securityEventLog,
                               ObjectMapper objectMapper,
                               @Value("${ums.access-review.due-days:30}") int dueDays,
                               @Value("${ums.access-review.disable-unattested:false}") boolean disableUnattested) {
        this.reviews = reviews;
        this.items = items;
        this.users = users;
        this.userRoles = userRoles;
        this.userCountries = userCountries;
        this.userPermissions = userPermissions;
        this.countries = countries;
        this.adminTierService = adminTierService;
        this.subjects = subjects;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.securityEventLog = securityEventLog;
        this.objectMapper = objectMapper;
        this.dueDays = Math.max(1, dueDays);
        this.disableUnattested = disableUnattested;
    }

    /** {@code 2026-Q3} for a date in the third quarter of 2026. */
    public static String periodOf(LocalDate date) {
        return date.getYear() + "-Q" + ((date.getMonthValue() - 1) / 3 + 1);
    }

    // ── opening ──────────────────────────────────────────────────────────────

    /**
     * Opens the period's reviews: one per attaché that has active staff and one
     * for the ministry. A review that already exists for the period is left
     * alone, so the job and the button are both safe to repeat. Returns the
     * reviews created.
     */
    @Transactional
    public List<UmsAccessReview> open(String period, Long by) {
        LocalDateTime now = LocalDateTime.now();
        Map<Integer, List<UmsUser>> byCountry = new LinkedHashMap<>();
        List<UmsUser> ministry = new ArrayList<>();
        for (UmsUser user : users.findAll()) {
            if (Boolean.TRUE.equals(user.getIsDeleted()) || !UserStatus.ACTIVE.equals(user.getStatus())) continue;
            AdminTier tier = adminTierService.tierOf(user.getId());
            List<UmsUserCountry> held = userCountries.findByUserIdAndIsActiveTrue(user.getId());
            if (tier.ministryLevel() || Boolean.TRUE.equals(user.getIsMinistryUser()) || held.isEmpty()) {
                ministry.add(user);
            } else {
                for (UmsUserCountry uc : held) byCountry.computeIfAbsent(uc.getCountryId(), k -> new ArrayList<>()).add(user);
            }
        }
        List<UmsAccessReview> created = new ArrayList<>();
        for (Map.Entry<Integer, List<UmsUser>> e : byCountry.entrySet()) {
            openOne(period, e.getKey(), e.getValue(), by, now).ifPresent(created::add);
        }
        openOne(period, null, ministry, by, now).ifPresent(created::add);
        return created;
    }

    private Optional<UmsAccessReview> openOne(String period, Integer countryId, List<UmsUser> accounts, Long by, LocalDateTime now) {
        boolean exists = countryId == null ? reviews.findByPeriodAndCountryIdIsNull(period).isPresent()
                : reviews.findByPeriodAndCountryId(period, countryId).isPresent();
        if (exists || accounts.isEmpty()) return Optional.empty();
        UmsAccessReview review = new UmsAccessReview();
        review.setPeriod(period);
        review.setCountryId(countryId);
        review.setOpenedAt(now);
        review.setOpenedBy(by);
        review.setDueAt(now.plusDays(dueDays));
        review = reviews.save(review);
        for (UmsUser user : accounts) {
            UmsAccessReviewItem item = new UmsAccessReviewItem();
            item.setReviewId(review.getId());
            item.setUserId(user.getId());
            item.setSnapshot(toJson(snapshotOf(user)));
            items.save(item);
        }
        String what = "access review " + period + " for " + (countryId == null ? "the ministry" : "attaché " + countryId)
                + ": " + accounts.size() + " account(s), due " + review.getDueAt().toLocalDate();
        if (by != null) auditLogService.log(by, "ACCESS_REVIEW_OPENED", "ACCESS_REVIEW", review.getId(), null, what);
        else securityEventLog.emit("ACCESS_REVIEW_OPENED", null, null, "ACCESS_REVIEW", review.getId(), what, null);
        return Optional.of(review);
    }

    public Snapshot snapshotOf(UmsUser user) {
        List<String> roles = new ArrayList<>();
        for (UmsUserRole ur : userRoles.findByUserIdAndIsActiveTrue(user.getId())) {
            roles.add(ur.getRole().getRoleName() + (ur.getValidTo() != null ? " (until " + ur.getValidTo().toLocalDate() + ")" : ""));
        }
        List<String> held = new ArrayList<>();
        for (UmsUserCountry uc : userCountries.findByUserIdAndIsActiveTrue(user.getId())) held.add(countryName(uc.getCountryId()));
        List<String> grants = new ArrayList<>();
        for (UmsUserPermission up : userPermissions.findByUserIdAndIsActiveTrue(user.getId())) {
            grants.add(up.getPermission().getPermissionCode() + (up.getExpiresAt() != null ? " (until " + up.getExpiresAt().toLocalDate() + ")" : ""));
        }
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return new Snapshot(user.getUserName(), fullName, user.getStatus(), adminTierService.tierOf(user.getId()).name(),
                user.getPositionCode(), roles, held, user.getLastLoginAt(), grants);
    }

    private String countryName(Integer id) {
        return countries.findById(id).map(StudyCountryIn::getStudyCountryInName).orElse(String.valueOf(id));
    }

    // ── reading ──────────────────────────────────────────────────────────────

    /** The reviews the caller may attest: all for ministry-level accounts, their own attachés' for a country admin. */
    public List<ReviewView> visible() {
        AccessSubject me = subjects.current();
        List<UmsAccessReview> list = me.isMinistryLevel() ? reviews.findAllByOrderByOpenedAtDescCountryIdAsc()
                : me.countryIds().isEmpty() ? List.of() : reviews.findByCountryIdInOrderByOpenedAtDesc(me.countryIds());
        List<ReviewView> out = new ArrayList<>();
        for (UmsAccessReview r : list) out.add(view(r));
        return out;
    }

    public ReviewView view(UmsAccessReview r) {
        return new ReviewView(r.getId(), r.getPeriod(), r.getCountryId(),
                r.getCountryId() == null ? null : countryName(r.getCountryId()), r.getStatus(), r.getOpenedAt(),
                r.getDueAt(), r.getClosedAt(), items.countByReviewId(r.getId()), items.countByReviewIdAndDecisionIsNull(r.getId()));
    }

    public UmsAccessReview require(Long reviewId) {
        UmsAccessReview review = reviews.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Access review not found"));
        assertMayAttest(review);
        return review;
    }

    public List<ItemView> items(Long reviewId) {
        UmsAccessReview review = require(reviewId);
        boolean overdue = com.dilaw.mohe.codes.AccessReviewStatus.OPEN.equals(review.getStatus()) && review.getDueAt().isBefore(LocalDateTime.now());
        List<ItemView> out = new ArrayList<>();
        for (UmsAccessReviewItem i : items.findByReviewIdOrderByUserId(reviewId)) {
            out.add(new ItemView(i.getId(), i.getUserId(), fromJson(i.getSnapshot()), i.getDecision(), i.getDecidedBy(),
                    i.getDecidedAt(), i.getNote(), overdue && i.getDecision() == null));
        }
        return out;
    }

    /**
     * Who attests: the attaché's own administrator for an attaché's review, the
     * ministry admin for the ministry's; the super admin either. A ministry
     * admin does not attest an attaché's list - the review exists to make the
     * attaché accountable for it (N-01).
     */
    void assertMayAttest(UmsAccessReview review) {
        AccessSubject me = subjects.current();
        if (!me.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in");
        if (me.isSuperAdmin()) return;
        if (review.getCountryId() == null) {
            if (me.tier() == AdminTier.MINISTRY_ADMIN) return;
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Access review not found");
        }
        if (me.tier() == AdminTier.COUNTRY_ADMIN && me.countryIds().contains(review.getCountryId())) return;
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Access review not found");
    }

    // ── attesting ────────────────────────────────────────────────────────────

    @Transactional
    public ItemView decide(Long reviewId, Long itemId, String decision, String note, Long by) {
        UmsAccessReview review = require(reviewId);
        if (!com.dilaw.mohe.codes.AccessReviewStatus.OPEN.equals(review.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This review is closed.");
        }
        if (decision == null || !DECISIONS.contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be KEEP, CHANGE or REMOVE.");
        }
        UmsAccessReviewItem item = items.findById(itemId).filter(i -> i.getReviewId().equals(reviewId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review item not found"));
        if ("REMOVE".equals(decision) && item.getUserId().equals(by)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remove your own account.");
        }
        if ("REMOVE".equals(decision)) {
            userService.deactivate(item.getUserId(), "ACCOUNT_REMOVED_BY_REVIEW",
                    "access review " + review.getPeriod() + (note == null ? "" : ": " + note), by);
        }
        item.setDecision(decision);
        item.setDecidedBy(by);
        item.setDecidedAt(LocalDateTime.now());
        item.setNote(note);
        items.saveAndFlush(item);
        auditLogService.log(by, "ACCESS_REVIEW_DECIDED", "USER", item.getUserId(), null,
                decision + " in access review " + review.getPeriod() + (note == null || note.isBlank() ? "" : ": " + note));
        return new ItemView(item.getId(), item.getUserId(), fromJson(item.getSnapshot()), decision, by,
                item.getDecidedAt(), note, false);
    }

    @Transactional
    public ReviewView close(Long reviewId, Long by) {
        UmsAccessReview review = require(reviewId);
        long undecided = items.countByReviewIdAndDecisionIsNull(reviewId);
        if (undecided > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, undecided + " account(s) still need a decision.");
        }
        review.setStatus(com.dilaw.mohe.codes.AccessReviewStatus.CLOSED);
        review.setClosedAt(LocalDateTime.now());
        review.setClosedBy(by);
        reviews.save(review);
        auditLogService.log(by, "ACCESS_REVIEW_CLOSED", "ACCESS_REVIEW", reviewId, null,
                review.getPeriod() + (review.getCountryId() == null ? " (ministry)" : " (attaché " + review.getCountryId() + ")"));
        return view(review);
    }

    // ── overdue ──────────────────────────────────────────────────────────────

    /** Flags every undecided item past its review's due date once; disables the account when configured to. */
    @Transactional
    public OverdueOutcome flagOverdue(LocalDateTime now) {
        int flagged = 0, disabled = 0;
        for (UmsAccessReviewItem item : items.findOverdue(now)) {
            item.setOverdueFlaggedAt(now);
            items.save(item);
            UmsAccessReview review = reviews.findById(item.getReviewId()).orElse(null);
            String period = review == null ? "?" : review.getPeriod();
            auditLogService.log(item.getUserId(), "ACCESS_REVIEW_OVERDUE", "USER", item.getUserId(), null,
                    "not attested in access review " + period + " by its due date");
            flagged++;
            if (disableUnattested && userService.deactivate(item.getUserId(), "ACCOUNT_REMOVED_BY_REVIEW",
                    "not attested in access review " + period + " by its due date", item.getUserId())) {
                disabled++;
            }
        }
        return new OverdueOutcome(flagged, disabled);
    }

    // ── json ─────────────────────────────────────────────────────────────────

    private String toJson(Snapshot s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Snapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, Snapshot.class);
        } catch (JsonProcessingException e) {
            return new Snapshot(null, null, null, null, null, List.of(), List.of(), null, List.of());
        }
    }

    /** Distinct attachés with active staff, for the job's log line. */
    public Map<String, Object> summary() {
        Map<String, Object> m = new HashMap<>();
        m.put("open", reviews.findByStatus(com.dilaw.mohe.codes.AccessReviewStatus.OPEN).size());
        return m;
    }

    public static Set<String> decisions() {
        return new TreeSet<>(DECISIONS);
    }
}
