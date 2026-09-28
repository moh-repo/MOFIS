package com.dilaw.mohe.dto;

import com.dilaw.mohe.wafar.dto.WafarSpendingCategoryDto;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * What the Wafar statement's filter bar needs to render itself.
 *
 * <p>Categories come from the shared catalogue and are deliberately unscoped —
 * they are a list of things money can be spent on (rent, salaries, university
 * fee), not anybody's money. The attaché list, by contrast, is whatever the
 * caller may see.
 */
@Value
@Builder
public class WafarReportMetadataDto {

    List<WafarSpendingCategoryDto> categories;

    /** The statuses present in the caller's own data, not every status in the enum. */
    List<String> statuses;

    List<CountryOption> countries;

    @Value
    @Builder
    public static class CountryOption {
        Integer id;
        String name;
    }
}
