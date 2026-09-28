package com.dilaw.mohe.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.Date;

@Entity
@Table(name = "STUDENT_COUNTRY", schema = "MOHECORE")
public class StudentCountry {

    /**
     * Not an IDENTITY column in SQL Server - the baseline schema declares it
     * {@code int NOT NULL DEFAULT ((0))} - so it must be assigned before insert;
     * the service sets it from {@code repository.nextId()}. Same situation as
     * {@link StudyFinanceBank}.
     */
    @Id
    @Column(name = "STUDENT_COUNTRY_ID", nullable = false)
    private Integer studentCountryId;

    @Column(name = "STUDENT_COUNTRY_NAME")
    private String studentCountryName;

    @Column(name = "STUDENT_COUNTRY_NAME_EN")
    private String studentCountryNameEn;

    @Column(name = "STUDENT_COUNTRY_ORDER_NO")
    private Integer studentCountryOrderNo;

    @Column(name = "STUDENT_COUNTRY_STATUS")
    private Integer studentCountryStatus;

    /** No field initialiser — see {@link StudyFinanceBank#getIsActive()}; a new row is active unless stated (L1). */
    @ColumnDefault("1")
    @Column(name = "IS_ACTIVE")
    private Boolean isActive;

    @PrePersist
    void defaultActive() {
        if (isActive == null) isActive = true;
    }

    @Column(name = "STUDENT_COUNTRY_INSERTED_DATE")
    private Date studentCountryInsertedDate;

    public StudentCountry() {
    }

    public Integer getStudentCountryId() {
        return studentCountryId;
    }

    public void setStudentCountryId(Integer studentCountryId) {
        this.studentCountryId = studentCountryId;
    }

    public String getStudentCountryName() {
        return studentCountryName;
    }

    public void setStudentCountryName(String studentCountryName) {
        this.studentCountryName = studentCountryName;
    }

    public String getStudentCountryNameEn() {
        return studentCountryNameEn;
    }

    public void setStudentCountryNameEn(String studentCountryNameEn) {
        this.studentCountryNameEn = studentCountryNameEn;
    }

    public Integer getStudentCountryOrderNo() {
        return studentCountryOrderNo;
    }

    public void setStudentCountryOrderNo(Integer studentCountryOrderNo) {
        this.studentCountryOrderNo = studentCountryOrderNo;
    }

    public Integer getStudentCountryStatus() {
        return studentCountryStatus;
    }

    public void setStudentCountryStatus(Integer studentCountryStatus) {
        this.studentCountryStatus = studentCountryStatus;
    }

    public Date getStudentCountryInsertedDate() {
        return studentCountryInsertedDate;
    }

    public void setStudentCountryInsertedDate(Date studentCountryInsertedDate) {
        this.studentCountryInsertedDate = studentCountryInsertedDate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
