package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "STUDENT_TOTAL_REPORT_ALL", schema = "MOHECORE")
public class StudentTotalReportAll {
    @Id
    @Nationalized
    @Column(name = "STUDENT_TOTAL_REPORT_NAME", nullable = false)
    private String studentTotalReportName;

    @Nationalized
    @Column(name = "STUDENT_TOTAL_NAME")
    private String studentTotalName;

}