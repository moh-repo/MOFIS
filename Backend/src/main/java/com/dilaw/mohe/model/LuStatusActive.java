package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_STATUS_ACTIVE; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_STATUS_ACTIVE", schema = "MOHECORE")
public class LuStatusActive extends AbstractLuLookup {
}
