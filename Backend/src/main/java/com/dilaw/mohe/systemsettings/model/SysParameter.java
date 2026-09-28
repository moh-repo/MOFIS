package com.dilaw.mohe.systemsettings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/**
 * MOHECORE.SYS_PARAMETER (V072): a named, typed, described value that used to
 * be a constant in Java — the dashboard windows, the document-expiry alert
 * ladder, the audit-trail default window, the duplicate-match floor.
 */
@Getter
@Setter
@Entity
@Table(name = "SYS_PARAMETER", schema = "MOHECORE")
public class SysParameter {

    @Id
    @Nationalized
    @Column(name = "PARAM_KEY", nullable = false, length = 80)
    private String key;

    @Nationalized
    @Column(name = "PARAM_VALUE", nullable = false, length = 400)
    private String value;

    /** INT, DECIMAL, TEXT or BOOL. */
    @Nationalized
    @Column(name = "VALUE_TYPE", nullable = false, length = 10)
    @Size(max = 10)
    private String valueType;

    @Nationalized
    @Column(name = "NAME_AR", nullable = false, length = 200)
    private String nameAr;

    @Nationalized
    @Column(name = "NAME_EN", nullable = false, length = 200)
    private String nameEn;

    @Nationalized
    @Column(name = "DESCRIPTION", length = 1000)
    private String description;

    @Nationalized
    @Column(name = "UPDATED_BY", length = 100)
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
