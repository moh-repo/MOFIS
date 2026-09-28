package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_PAYMENT_AUTH_STATUS; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_PAYMENT_AUTH_STATUS", schema = "MOHECORE")
public class LuPaymentAuthStatus extends AbstractLuLookup {
}
