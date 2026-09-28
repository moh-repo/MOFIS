package com.dilaw.mohe.systemsettings.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "SYS_SETTINGS", schema = "MOHECORE")
public class SysSettings {

    @Id
    @Column(name = "SYS_SETTINGS_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "SYS_NAME_AR")
    private String sysNameAr;

    @Column(name = "SYS_NAME_EN")
    private String sysNameEn;

    @Column(name = "SYS_LOGO_PATH")
    private String sysLogoPath;

    @Column(name = "SYS_LOGO_FILENAME")
    private String sysLogoFilename;

    @Column(name = "UPDATED_BY")
    private Integer updatedBy;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PreUpdate
    @PrePersist
    public void touch() {
        updatedAt = Instant.now();
    }
}
