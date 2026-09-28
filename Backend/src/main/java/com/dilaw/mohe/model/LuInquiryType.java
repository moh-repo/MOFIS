package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_INQUIRY_TYPE (V073); shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_INQUIRY_TYPE", schema = "MOHECORE")
public class LuInquiryType extends AbstractLuLookup {
}
