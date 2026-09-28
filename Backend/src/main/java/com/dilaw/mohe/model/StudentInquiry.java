package com.dilaw.mohe.model;

import com.dilaw.mohe.codes.InquiryStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "StudentInquiry", schema = "MOHECORE")
public class StudentInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "Id", nullable = false)
    private Integer id;

    @Column(name = "StudentId", nullable = false)
    private Integer studentId;

    @Nationalized
    @Column(name = "Type", nullable = false, length = 50)
    private String type;

    @Nationalized
    @Column(name = "Title", nullable = false, length = 255)
    private String title;

    @Nationalized
    @Column(name = "Description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    @Nationalized
    @Column(name = "Status", nullable = false, length = 50)
    private String status = InquiryStatus.PENDING;

    @Nationalized
    @Column(name = "Response", columnDefinition = "NVARCHAR(MAX)")
    private String response;

    @Column(name = "RespondedAt")
    private Instant respondedAt;

    @Column(name = "RespondedBy", length = 100)
    private String respondedBy;

    @Column(name = "CreatedAt", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "UpdatedAt", nullable = false)
    private Instant updatedAt = Instant.now();
}
