package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_NOTICE_STATUS (V074); shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_NOTICE_STATUS", schema = "MOHECORE")
public class LuNoticeStatus extends AbstractLuLookup {
}
