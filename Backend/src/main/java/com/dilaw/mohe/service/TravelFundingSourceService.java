package com.dilaw.mohe.service;

import com.dilaw.mohe.dto.TravelFundingSourceDto;
import com.dilaw.mohe.model.LuTravelFundingSource;
import com.dilaw.mohe.repository.LuTravelFundingSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelFundingSourceService {

    private final LuTravelFundingSourceRepository repository;

    public List<TravelFundingSourceDto> getActiveFundingSources(String lang) {
        boolean arabic = lang != null && lang.trim().toLowerCase(Locale.ROOT).startsWith("ar");
        return repository.findAllActiveOrderedByDisplay().stream()
                .map(f -> toDto(f, arabic))
                .collect(Collectors.toList());
    }

    private TravelFundingSourceDto toDto(LuTravelFundingSource f, boolean arabic) {
        String nameEn = StringUtils.hasText(f.getFundingSourceNameEn()) ? f.getFundingSourceNameEn() : f.getFundingSourceCode();
        String nameAr = StringUtils.hasText(f.getFundingSourceNameAr()) ? f.getFundingSourceNameAr() : nameEn;
        return TravelFundingSourceDto.builder()
                .id(f.getFundingSourceId())
                .code(f.getFundingSourceCode())
                .name(arabic ? nameAr : nameEn)
                .nameEn(nameEn)
                .nameAr(nameAr)
                .build();
    }
}
