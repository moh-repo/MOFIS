package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_ACADEMIC_YEAR; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_ACADEMIC_YEAR", schema = "MOHECORE")
public class LuAcademicYear extends AbstractLuLookup {
}
