package com.dilaw.mohe.studentportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "student_user_groups", schema = "STUDENTCORE")
public class StudentUserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "student_user_id", nullable = false)
    private Integer studentUserId;

    @Column(name = "group_id", nullable = false)
    private Integer groupId;
}
