package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_ALERT_TYPE (V073); shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_ALERT_TYPE", schema = "MOHECORE")
public class LuAlertType extends AbstractLuLookup {
}
