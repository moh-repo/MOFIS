package com.dilaw.mohe.security.policy;

/**
 * A per-country configuration row (S-06): notification settings, bank branches.
 *
 * @param what              the noun of the refusal
 * @param countryId         the attaché the row belongs to; null is the ministry's global row
 * @param ministryMayManage true when ministry-level accounts may also write the attachés' rows,
 *                          not only read them (notification settings, since 19 Sep 2026)
 */
public record CountryOwnedConfig(String what, Integer countryId, boolean ministryMayManage) {

    /** A row only its own attaché writes — the S-06 default. */
    public CountryOwnedConfig(String what, Integer countryId) {
        this(what, countryId, false);
    }
}
