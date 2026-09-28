package com.dilaw.mohe.systemsettings.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportTypeSettingsDto {

    private Integer id;
    private String reportTypeCode;
    private String reportTypeNameAr;
    private String reportTypeNameEn;
    private Boolean isActive;

    // Header titles
    private String title01Ar; private String title01En;
    private String title02Ar; private String title02En;
    private String title03Ar; private String title03En;
    private String title04Ar; private String title04En;
    private String title05Ar; private String title05En;

    // Footer text
    private String footerTextAr;
    private String footerTextEn;

    // Signatories
    private Integer signatoryCount;

    private String signatoryName01Ar; private String signatoryName01En;
    private String signatoryPos01Ar;  private String signatoryPos01En;

    private String signatoryName02Ar; private String signatoryName02En;
    private String signatoryPos02Ar;  private String signatoryPos02En;

    private String signatoryName03Ar; private String signatoryName03En;
    private String signatoryPos03Ar;  private String signatoryPos03En;

    private String signatoryName04Ar; private String signatoryName04En;
    private String signatoryPos04Ar;  private String signatoryPos04En;

    private String signatoryName05Ar; private String signatoryName05En;
    private String signatoryPos05Ar;  private String signatoryPos05En;
}
