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
@Table(name = "STUDENT_GOV_MUD", schema = "MOHECORE")
public class StudentGovMud {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ColumnDefault("0")
    @Column(name = "STUDENT_MUD_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "STUDENT_MUD_NAME")
    private String studentMudName;

    @Column(name = "STUDENT_MUD_NAME_EN")
    private String studentMudNameEn;

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

    /**
      * The governorate this municipality belongs to. Held as text until V129,
      * which converted it: STUDENT_GOV's key is an int, and SQL Server will not
      * build a foreign key between an nvarchar and an int (plan W6-06).
      */
    @Column(name = "STUDENT_GOV_ID")
    private Integer studentGovId;

    @ColumnDefault("getdate()")
    @Column(name = "STUDENT_MIDATE")
    private Instant studentMidate;

}