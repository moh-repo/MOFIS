package com.dilaw.mohe.security.policy;

import com.dilaw.mohe.wafar.model.WafarSpending;

/**
 * A write to one attaché's Wafar, as {@link WafarPolicy} sees it.
 *
 * @param what      noun for the refusal: {@code "Wafar spending"}, {@code "Wafar finance"}, {@code "Wafar reconciliation"}
 * @param countryId the attaché whose money it is; null is missing data and belongs to nobody
 * @param makerId   who recorded the row, for the maker-checker rule on APPROVE; null when not applicable
 */
public record WafarWrite(String what, Integer countryId, Integer makerId) {

    public static final String SPENDING = "Wafar spending";
    public static final String FINANCE = "Wafar finance";
    public static final String RECONCILIATION = "Wafar reconciliation";

    public static WafarWrite of(WafarSpending spending) {
        return new WafarWrite(SPENDING, spending.getStudyCountryInId(), spending.getCreatedBy());
    }

    public static WafarWrite spending(Integer countryId) {
        return new WafarWrite(SPENDING, countryId, null);
    }

    public static WafarWrite finance(Integer countryId) {
        return new WafarWrite(FINANCE, countryId, null);
    }

    public static WafarWrite reconciliation(Integer countryId) {
        return new WafarWrite(RECONCILIATION, countryId, null);
    }
}
