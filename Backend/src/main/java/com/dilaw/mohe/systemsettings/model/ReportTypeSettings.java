package com.dilaw.mohe.systemsettings.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "REPORT_TYPE_SETTINGS", schema = "MOHECORE")
public class ReportTypeSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REPORT_TYPE_ID", nullable = false)
    private Integer id;

    @Column(name = "REPORT_TYPE_CODE", nullable = false, unique = true)
    private String reportTypeCode;

    @Nationalized
    @Column(name = "REPORT_TYPE_NAME_AR")
    private String reportTypeNameAr;

    @Column(name = "REPORT_TYPE_NAME_EN")
    private String reportTypeNameEn;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    // ── Header titles ─────────────────────────────────────────────────────────
    @Nationalized @Column(name = "TITLE_01_AR") private String title01Ar;
    @Column(name = "TITLE_01_EN") private String title01En;

    @Nationalized @Column(name = "TITLE_02_AR") private String title02Ar;
    @Column(name = "TITLE_02_EN") private String title02En;

    @Nationalized @Column(name = "TITLE_03_AR") private String title03Ar;
    @Column(name = "TITLE_03_EN") private String title03En;

    @Nationalized @Column(name = "TITLE_04_AR") private String title04Ar;
    @Column(name = "TITLE_04_EN") private String title04En;

    @Nationalized @Column(name = "TITLE_05_AR") private String title05Ar;
    @Column(name = "TITLE_05_EN") private String title05En;

    // ── Footer text ───────────────────────────────────────────────────────────
    @Nationalized @Column(name = "FOOTER_TEXT_AR") private String footerTextAr;
    @Column(name = "FOOTER_TEXT_EN") private String footerTextEn;

    // ── Signatories ───────────────────────────────────────────────────────────
    @Column(name = "SIGNATORY_COUNT", nullable = false)
    private Integer signatoryCount = 0;

    @Nationalized @Column(name = "SIGNATORY_NAME_01_AR") private String signatoryName01Ar;
    @Column(name = "SIGNATORY_NAME_01_EN") private String signatoryName01En;
    @Nationalized @Column(name = "SIGNATORY_POS_01_AR")  private String signatoryPos01Ar;
    @Column(name = "SIGNATORY_POS_01_EN")  private String signatoryPos01En;

    @Nationalized @Column(name = "SIGNATORY_NAME_02_AR") private String signatoryName02Ar;
    @Column(name = "SIGNATORY_NAME_02_EN") private String signatoryName02En;
    @Nationalized @Column(name = "SIGNATORY_POS_02_AR")  private String signatoryPos02Ar;
    @Column(name = "SIGNATORY_POS_02_EN")  private String signatoryPos02En;

    @Nationalized @Column(name = "SIGNATORY_NAME_03_AR") private String signatoryName03Ar;
    @Column(name = "SIGNATORY_NAME_03_EN") private String signatoryName03En;
    @Nationalized @Column(name = "SIGNATORY_POS_03_AR")  private String signatoryPos03Ar;
    @Column(name = "SIGNATORY_POS_03_EN")  private String signatoryPos03En;

    @Nationalized @Column(name = "SIGNATORY_NAME_04_AR") private String signatoryName04Ar;
    @Column(name = "SIGNATORY_NAME_04_EN") private String signatoryName04En;
    @Nationalized @Column(name = "SIGNATORY_POS_04_AR")  private String signatoryPos04Ar;
    @Column(name = "SIGNATORY_POS_04_EN")  private String signatoryPos04En;

    @Nationalized @Column(name = "SIGNATORY_NAME_05_AR") private String signatoryName05Ar;
    @Column(name = "SIGNATORY_NAME_05_EN") private String signatoryName05En;
    @Nationalized @Column(name = "SIGNATORY_POS_05_AR")  private String signatoryPos05Ar;
    @Column(name = "SIGNATORY_POS_05_EN")  private String signatoryPos05En;

    @Column(name = "UPDATED_BY") private Integer updatedBy;
    @Column(name = "UPDATED_AT") private Instant updatedAt;

    @PreUpdate
    @PrePersist
    public void touch() {
        updatedAt = Instant.now();
    }
}
