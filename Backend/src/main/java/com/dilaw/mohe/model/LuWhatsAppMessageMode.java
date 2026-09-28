package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Code list LU_WHATSAPP_MESSAGE_MODE (V120); shape, colours and system flag come
 * from {@link AbstractLuLookup}.
 *
 * <p>The three codes are the ones V118 wrote into the check constraint on
 * {@code NOTIFICATION_SETTINGS.WHATSAPP_MESSAGE_MODE} and that
 * {@code WhatsAppMessageMode} names in Java, so the rows are {@code IS_SYSTEM}:
 * the code-list API refuses to rename, deactivate or delete one.
 */
@Entity
@Table(name = "LU_WHATSAPP_MESSAGE_MODE", schema = "MOHECORE")
public class LuWhatsAppMessageMode extends AbstractLuLookup {
}
