package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_CHANGE_REQUEST_STATUS; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_CHANGE_REQUEST_STATUS", schema = "MOHECORE")
public class LuChangeRequestStatus extends AbstractLuLookup {
}
