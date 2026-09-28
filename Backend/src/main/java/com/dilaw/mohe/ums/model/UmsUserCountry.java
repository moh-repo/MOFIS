package com.dilaw.mohe.ums.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "UserCountries", schema = "UMSCORE")
public class UmsUserCountry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id")
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserId", nullable = false)
    private UmsUser user;

    @Column(name = "CountryId", nullable = false)
    private Integer countryId; // References MOHECORE.STUDY_COUNTRY_IN

    @Column(name = "AssignedAt", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "AssignedBy")
    private Long assignedBy;

    @Column(name = "IsActive", nullable = false)
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        assignedAt = LocalDateTime.now();
    }
}
