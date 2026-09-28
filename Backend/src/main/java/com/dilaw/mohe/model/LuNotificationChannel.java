package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Code list LU_NOTIFICATION_CHANNEL; shape, colours and system flag come from {@link AbstractLuLookup}. */
@Entity
@Table(name = "LU_NOTIFICATION_CHANNEL", schema = "MOHECORE")
public class LuNotificationChannel extends AbstractLuLookup {
}
