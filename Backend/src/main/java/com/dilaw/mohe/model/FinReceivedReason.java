package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "FIN_RECEIVED_REASON", schema = "MOHECORE")
public class FinReceivedReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REASON_ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "REASON_CODE", nullable = false, length = 50)
    private String reasonCode;

    @Nationalized
    @Column(name = "REASON_NAME", nullable = false, length = 200)
    private String reasonName;

    @Nationalized
    @Column(name = "REASON_NAME_AR", length = 200)
    private String reasonNameAr;

    @ColumnDefault("0")
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    @ColumnDefault("1")
    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;
}
