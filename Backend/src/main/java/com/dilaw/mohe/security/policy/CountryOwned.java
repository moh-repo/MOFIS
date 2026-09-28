package com.dilaw.mohe.security.policy;

/**
 * Any row that belongs to one attaché, for the coarse country rule.
 *
 * @param what      short noun for the refusal, e.g. {@code "Student record"}
 * @param countryId the study country the row is filed under; null means nobody's but the ministry's
 */
public record CountryOwned(String what, Integer countryId) {
}
