package com.dilaw.mohe.ums.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One rung of the sign-in lockout ladder ({@code MOHECORE.AUTH_LOCKOUT_POLICY},
 * V080).
 *
 * <p>The ministry's policy is not a single threshold but a sequence: so many
 * tries then a short lock, so many more then a longer one, and finally a lock
 * only an administrator opens. Each stage is a row so the numbers can be
 * retuned in Settings without a migration or a redeploy — which is the whole
 * point of moving the count off the browser, where it was a variable anyone
 * could delete.
 *
 * <p>{@link #lockMinutes} of {@code null} is the last rung: locked until an
 * administrator unlocks. The migration's verification refuses more than one
 * such stage, so the ladder always ends somewhere a person can reach.
 */
@Getter
@Setter
@Entity
@Table(name = "AUTH_LOCKOUT_POLICY", schema = "MOHECORE")
public class AuthLockoutPolicy {

    @Id
    @Column(name = "STAGE_ORDER")
    private Integer stageOrder;

    @Column(name = "MAX_ATTEMPTS", nullable = false)
    private Integer maxAttempts;

    /** Minutes the account stays locked, or {@code null} for "until unlocked". */
    @Column(name = "LOCK_MINUTES")
    private Integer lockMinutes;

    @Column(name = "NAME_AR", nullable = false, length = 200)
    private String nameAr;

    @Column(name = "NAME_EN", nullable = false, length = 200)
    private String nameEn;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    @Column(name = "UPDATED_BY", length = 100)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** Whether this rung locks until an administrator unlocks. */
    public boolean isOpenEnded() {
        return lockMinutes == null;
    }
}
