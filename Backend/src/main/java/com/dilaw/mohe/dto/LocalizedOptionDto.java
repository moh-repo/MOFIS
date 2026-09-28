package com.dilaw.mohe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A selectable option whose stored {@code value} is language-neutral while the display
 * {@code label} is resolved for the caller's language. {@code labelEn}/{@code labelAr}
 * are also carried so the client can re-localize without another round trip.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalizedOptionDto {
    private String value;
    private String label;
    private String labelEn;
    private String labelAr;
}
