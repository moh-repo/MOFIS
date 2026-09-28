package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "StudentInfoRequestStaging", schema = "MOHECORE")
public class StudentInfoRequestStaging {

    @Id
    @Column(name = "RequestId", nullable = false)
    private Integer requestId;

    @Column(name = "StudentDataJson", columnDefinition = "NVARCHAR(MAX)")
    private String studentDataJson;

    @Column(name = "StudentFinanceJson", columnDefinition = "NVARCHAR(MAX)")
    private String studentFinanceJson;

    @Column(name = "SubmittedAt", nullable = false)
    private Instant submittedAt = Instant.now();
}
