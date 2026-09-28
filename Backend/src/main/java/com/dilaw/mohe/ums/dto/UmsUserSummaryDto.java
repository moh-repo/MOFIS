package com.dilaw.mohe.ums.dto;

import com.dilaw.mohe.ums.model.UmsUser;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A staff account as the users list, the search and the admins page show it
 * (review H5a, decision DEC-16, plan W5-01).
 *
 * <p>These endpoints returned the whole account entity for every row: lockout
 * counters and stages, password and failed-login times, the must-change flag,
 * the raw admin flags, who created and changed it. The list needs a name, the
 * status, the privilege tag, the attachés and the last sign-in; the edit and
 * detail dialogs read the full record by id.
 *
 * @param privilege SUPER_ADMIN, ADMIN or USER, from the account's flags: what the
 *                  list's tag shows, in place of the flags themselves
 */
public record UmsUserSummaryDto(
        Long id,
        String userName,
        String firstName,
        String lastName,
        String fullName,
        String email,
        String status,
        String privilege,
        List<String> countryNames,
        LocalDateTime lastLoginAt) {

    public static UmsUserSummaryDto of(UmsUser u) {
        String privilege = Boolean.TRUE.equals(u.getIsSuperAdmin()) ? "SUPER_ADMIN"
                : Boolean.TRUE.equals(u.getIsAdmin()) ? "ADMIN" : "USER";
        return new UmsUserSummaryDto(u.getId(), u.getUserName(), u.getFirstName(), u.getLastName(), u.getFullName(),
                u.getEmail(), u.getStatus(), privilege,
                u.getCountryNames() == null ? List.of() : List.copyOf(u.getCountryNames()), u.getLastLoginAt());
    }
}
