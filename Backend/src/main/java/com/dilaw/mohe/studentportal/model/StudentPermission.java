package com.dilaw.mohe.studentportal.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "student_permissions", schema = "STUDENTCORE")
public class StudentPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Integer permissionId;

    @Column(name = "permission_code", nullable = false, unique = true, length = 100)
    private String permissionCode;

    @Column(name = "permission_name", nullable = false, length = 200)
    private String permissionName;

    @Column(name = "permission_name_ar", length = 200)
    private String permissionNameAr;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
