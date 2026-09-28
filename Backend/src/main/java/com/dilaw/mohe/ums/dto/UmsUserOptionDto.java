package com.dilaw.mohe.ums.dto;

import com.dilaw.mohe.ums.model.UmsUser;

import java.util.List;

/**
 * A staff account offered as a choice: the approval configuration's named
 * approvers (review H5a, plan W5-01). The picker shows who it is and where they
 * serve; it received the whole account entity.
 */
public record UmsUserOptionDto(
        Long id,
        String userName,
        String firstName,
        String lastName,
        String fullName,
        String positionCode,
        List<String> countryNames) {

    public static UmsUserOptionDto of(UmsUser u) {
        return new UmsUserOptionDto(u.getId(), u.getUserName(), u.getFirstName(), u.getLastName(), u.getFullName(),
                u.getPositionCode(), u.getCountryNames() == null ? List.of() : List.copyOf(u.getCountryNames()));
    }
}
