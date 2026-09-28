package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.ums.model.AdminTier;
import com.dilaw.mohe.ums.model.UmsUser;
import com.dilaw.mohe.ums.repository.UmsUserRepository;
import com.dilaw.mohe.ums.service.AdminTierService;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

/**
 * Who may act on whose account: the matrix of security plan B.3, in one place
 * (N-01.c).
 *
 * <ul>
 *   <li>a super admin administers anyone;</li>
 *   <li>a ministry admin administers anyone but a super admin;</li>
 *   <li>ministry staff administer nobody but themselves (N-01);</li>
 *   <li>a country admin administers the country admins and staff it can see,
 *       never a ministry account (N-02);</li>
 *   <li>country staff administer their own row only.</li>
 * </ul>
 *
 * <p>Actions on a target account: {@code ADMINISTER} (edit, reset, assign,
 * grant), {@code DELETE} (administer, and never yourself). Subject-only
 * actions: {@code CREATE} (any tier that administers staff), {@code GRANT_ADMIN}
 * (the same — whoever it promotes still lands inside the caller's own
 * countries, so the elevation cannot reach further than the caller does).
 *
 * <p>This is the second of two barriers. {@code StaffCountrySpec.administrableBy}
 * hides accounts outside the caller's countries first; this one does not depend
 * on the country data being right, which is what a misassigned country would
 * otherwise reopen. 403 rather than 404 here: reaching the policy means the
 * caller can already see the account.
 */
@Component
public class UmsUserPolicy implements AccessPolicy<UmsUser> {

    public static final String NAME = "UmsUser";

    private final AdminTierService adminTierService;
    private final UmsUserRepository userRepository;

    public UmsUserPolicy(AdminTierService adminTierService, UmsUserRepository userRepository) {
        this.adminTierService = adminTierService;
        this.userRepository = userRepository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<UmsUser> resourceType() {
        return UmsUser.class;
    }

    @Override
    public Decision decide(AccessSubject subject, UmsUser target, String action) {
        if (!subject.isAuthenticated()) return Decision.deny("Access denied: not signed in");
        AdminTier caller = subject.tier();
        switch (action) {
            case "CREATE" -> {
                if (caller == AdminTier.MINISTRY_STAFF) {
                    return Decision.deny("Ministry users may view staff accounts but not create them.");
                }
                return caller.administersStaff() ? Decision.allow()
                        : Decision.deny("Creating staff accounts requires an administrator tier.");
            }
            case "GRANT_ADMIN" -> {
                return caller.administersStaff() ? Decision.allow()
                        : Decision.deny("Granting administrator rights requires holding them.");
            }
            case "DELETE" -> {
                if (target != null && subject.is(target.getId())) {
                    return Decision.deny("You cannot delete your own account.");
                }
                return administer(subject, target);
            }
            default -> {
                return administer(subject, target);
            }
        }
    }

    private Decision administer(AccessSubject subject, UmsUser target) {
        if (target == null) return Decision.allow();
        if (subject.is(target.getId())) return Decision.allow(); // always yourself (StaffCountrySpec clause 4)
        AdminTier caller = subject.tier();
        AdminTier targetTier = adminTierService.tierOf(target.getId());
        if (caller.mayAdminister(targetTier)) return Decision.allow();
        if (targetTier == AdminTier.SUPER_ADMIN) {
            return Decision.deny("Only a super administrator may administer a super administrator account.");
        }
        if (caller == AdminTier.MINISTRY_STAFF) {
            return Decision.deny("Ministry users may view staff accounts but not administer them.");
        }
        if (caller == AdminTier.COUNTRY_ADMIN) {
            return Decision.deny("A country administrator may not administer a ministry account.");
        }
        return Decision.deny("Your account may administer no staff account but its own.");
    }

    @Override
    public Optional<UmsUser> load(Serializable id) {
        if (id instanceof Number n) return userRepository.findById(n.longValue());
        return Optional.empty();
    }
}
