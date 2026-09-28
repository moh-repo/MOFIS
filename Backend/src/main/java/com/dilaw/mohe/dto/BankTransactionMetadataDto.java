package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransactionMetadataDto {

    private List<LookupOptionDto> transactionTypes;
    private List<LookupOptionDto> banks;
    private List<LookupOptionDto> bankBranches;
    private List<LookupOptionDto> currencies;
}


