package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_NOTIFICATION_RECIPIENT_TYPE; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_NOTIFICATION_RECIPIENT_TYPE", schema = "MOHECORE")
public class LuNotificationRecipientType extends AbstractLuLookup {
}
