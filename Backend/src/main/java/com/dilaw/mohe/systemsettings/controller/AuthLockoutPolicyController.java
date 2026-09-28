package com.dilaw.mohe.systemsettings.controller;

import com.dilaw.mohe.config.MoheSecurityExpressions;
import com.dilaw.mohe.ums.model.AuthLockoutPolicy;
import com.dilaw.mohe.ums.repository.AuthLockoutPolicyRepository;
import com.dilaw.mohe.ums.service.LoginAttemptService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The sign-in lockout ladder, for the *System parameters* screen.
 *
 * <p>Stages are edited, never added or removed here: their number is a policy
 * decision that belongs with a migration, while the numbers on each rung are
 * something the ministry may want to retune on an ordinary afternoon.
 */
@RestController
@RequestMapping("api/v1/auth-lockout-policy")
@PreAuthorize(MoheSecurityExpressions.SYSTEM_ADMIN)
public class AuthLockoutPolicyController {

    /** A lock nobody could sit out would be indistinguishable from a broken account. */
    private static final int MAX_LOCK_MINUTES = 60 * 24 * 7;

    private final AuthLockoutPolicyRepository repository;
    private final LoginAttemptService loginAttempts;

    public AuthLockoutPolicyController(AuthLockoutPolicyRepository repository, LoginAttemptService loginAttempts) {
        this.repository = repository;
        this.loginAttempts = loginAttempts;
    }

    /** Body of a stage edit. {@code lockMinutes} null means "until an administrator unlocks". */
    public record StageUpdate(Integer maxAttempts, Integer lockMinutes, Boolean isActive) {
    }

    @GetMapping
    public List<AuthLockoutPolicy> list() {
        return repository.findAll(org.springframework.data.domain.Sort.by("stageOrder"));
    }

    @PutMapping("/{stageOrder}")
    @Transactional
    public AuthLockoutPolicy update(@PathVariable Integer stageOrder, @RequestBody StageUpdate body,
                                    Authentication auth) {
        AuthLockoutPolicy stage = repository.findById(stageOrder)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No lockout stage " + stageOrder));

        if (body.maxAttempts() != null) {
            if (body.maxAttempts() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attempts must be at least 1");
            }
            stage.setMaxAttempts(body.maxAttempts());
        }
        if (body.lockMinutes() != null) {
            if (body.lockMinutes() < 1 || body.lockMinutes() > MAX_LOCK_MINUTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Lock minutes must be between 1 and " + MAX_LOCK_MINUTES);
            }
            stage.setLockMinutes(body.lockMinutes());
        }
        if (body.isActive() != null) {
            stage.setIsActive(body.isActive());
        }

        stage.setUpdatedBy(auth == null ? null : auth.getName());
        stage.setUpdatedAt(LocalDateTime.now());
        AuthLockoutPolicy saved = repository.save(stage);

        assertLadderStillEndsSomewhere();
        loginAttempts.evictPolicy();
        return saved;
    }

    /**
     * The ladder must keep at least one active rung, or nothing would ever lock;
     * the check runs after the write and inside the transaction, so a change that
     * would empty it is rolled back rather than left to be discovered at a login.
     */
    private void assertLadderStillEndsSomewhere() {
        if (repository.findByIsActiveTrueOrderByStageOrderAsc().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one lockout stage must stay active");
        }
    }
}
