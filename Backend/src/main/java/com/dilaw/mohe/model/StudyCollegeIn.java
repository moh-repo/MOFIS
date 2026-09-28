package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "STUDY_COLLEGE_IN", schema = "MOHECORE")
public class StudyCollegeIn {
    /**
     * Not an IDENTITY column in SQL Server - the baseline schema declares it
     * {@code int NOT NULL DEFAULT ((0))} - so it must be assigned before insert;
     * the service sets it from {@code repository.nextId()}. Same situation as
     * {@link StudyFinanceBank}.
     */
    @Id
    @ColumnDefault("0")
    @Column(name = "STUDY_COLLEGE_IN_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDY_COLLEGE_IN_NAME")
    private String studyCollegeInName;

    @Column(name = "STUDY_COLLEGE_IN_NAME_EN")
    private String studyCollegeInNameEn;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}; a new row is active unless stated (L1). */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

    @ColumnDefault("0")
    @Column(name = "STUDY_UNIVERSITY_IN_ID")
    private Integer studyUniversityInId;

    @ColumnDefault("0")
    @Column(name = "STUDY_GOV_IN_ID")
    private Integer studyGovInId;

    @ColumnDefault("0")
    @Column(name = "STUDY_COUNTRY_IN_ID")
    private Integer studyCountryInId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDY_COLLEGE_IN_MIDATE")
    private Instant studyCollegeInMidate;

}