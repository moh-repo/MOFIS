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
@Table(name = "STUDENT_GOV", schema = "MOHECORE")
public class StudentGov {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDENT_GOV_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_GOV_NAME")
    private String studentGovName;

    @Column(name = "STUDENT_GOV_NAME_EN")
    private String studentGovNameEn;

    /**
     * No field initialiser (live QA M12): an update body that left the flag out came in
     * as {@code true} and re-enabled a disabled row. A new row is active unless stated.
     */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @jakarta.persistence.PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

    @ColumnDefault("0")
    @Column(name = "STUDENT_COUNTRY_ID")
    private Integer studentCountryId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_MIDATE")
    private Instant studentMidate;

}