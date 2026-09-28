package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_YES_NO; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_YES_NO", schema = "MOHECORE")
public class LuYesNo extends AbstractLuLookup {
}
