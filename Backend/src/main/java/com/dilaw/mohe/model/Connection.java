package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "CONNECTION", schema = "MOHENTracer")
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CONNECTION_ID")
    private Integer connectionId;

    @Column(name = "SOURCE_NODE_ID", nullable = false)
    private Integer sourceNodeId;

    @Column(name = "TARGET_NODE_ID", nullable = false)
    private Integer targetNodeId;

    @Nationalized
    @Column(name = "CONNECTION_TYPE", nullable = false, length = 100)
    private String connectionType;

    @Column(name = "CONNECTION_STRENGTH")
    private Integer connectionStrength;

    @Column(name = "RELATIONSHIP_START_DATE")
    private LocalDate relationshipStartDate;

    @Column(name = "RELATIONSHIP_END_DATE")
    private LocalDate relationshipEndDate;

    @Nationalized
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Nationalized
    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "MODIFIED_DATE")
    private LocalDateTime modifiedDate;

    @Nationalized
    @Column(name = "MODIFIED_BY", length = 100)
    private String modifiedBy;
}

