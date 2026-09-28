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
@Table(name = "STUDENT_PARENT", schema = "MOHECORE")
@Where(clause = "IS_DELETED = 0 OR IS_DELETED IS NULL")
public class StudentParent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PARENT_ID", nullable = false)
    private Integer parentId;

    @Column(name = "STUDENT_ID", nullable = false)
    private Integer studentId;

    @Column(name = "RELATIONSHIP_ID", nullable = false)
    private Integer relationshipId;

    @Nationalized
    @Column(name = "PARENT_NAME", nullable = false, length = 255)
    private String parentName;

    @Nationalized
    @Column(name = "NATIONAL_ID", length = 50)
    private String nationalId;

    @Nationalized
    @Column(name = "OCCUPATION", length = 200)
    private String occupation;

    @Nationalized
    @Column(name = "PHONE", length = 50)
    private String phone;

    @Nationalized
    @Column(name = "ADDRESS", length = 500)
    private String address;

    @ColumnDefault("1")
    @Column(name = "IS_ALIVE", nullable = false)
    private Boolean isAlive = true;

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
