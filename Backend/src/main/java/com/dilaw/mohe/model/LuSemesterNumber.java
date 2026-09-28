package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_SEMESTER_NUMBER; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_SEMESTER_NUMBER", schema = "MOHECORE")
public class LuSemesterNumber extends AbstractLuLookup {
}
