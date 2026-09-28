package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_FEE_SOURCE; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_FEE_SOURCE", schema = "MOHECORE")
public class LuFeeSource extends AbstractLuLookup {
}
