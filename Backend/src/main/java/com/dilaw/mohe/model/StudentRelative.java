package com.dilaw.mohe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_RELATIVE", schema = "MOHECORE")
@Where(clause = "IS_DELETED = 0 OR IS_DELETED IS NULL")
public class StudentRelative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RELATIVE_ID", nullable = false)
    private Integer relativeId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Column(name = "RELATED_STUDENT_ID", nullable = false)
    private Integer relatedStudentId;

    @Column(name = "RELATIONSHIP_ID", nullable = false)
    private Integer relationshipId;

    @Nationalized
    @Column(name = "NOTES", length = 1000)
    private String notes;

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

    @ColumnDefault("0")
    @Column(name = "IS_DELETED", nullable = false)
    private Boolean isDeleted = false;
}
