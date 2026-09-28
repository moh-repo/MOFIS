package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "study_status", schema = "MOHECORE")
public class StudyStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_id", nullable = false)
    private Integer id;

    @Column(name = "status_name")
    private String statusName;

    @Column(name = "status_name_en")
    private String statusNameEn;

    /**
     * No field initialiser (live QA M12): an update body that left the flag out came in
     * as {@code true} and re-enabled a disabled row. A new row is active unless stated.
     */
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @jakarta.persistence.PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

    public String getStatusNameEn() {
        return statusNameEn;
    }

    public void setStatusNameEn(String statusNameEn) {
        this.statusNameEn = statusNameEn;
    }

}