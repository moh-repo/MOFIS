package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Code list LU_PAYMENT_METHOD (V078): how a finance row was paid out, inferred by
 * {@code PaymentSummaryRepository} from the bank / cheque references the row
 * carries. Shape, colours and system flag come from {@link AbstractLuLookup}.
 */
@Entity
@Table(name = "LU_PAYMENT_METHOD", schema = "MOHECORE")
public class LuPaymentMethod extends AbstractLuLookup {
}
