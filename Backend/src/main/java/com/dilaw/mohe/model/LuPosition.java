package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Code list LU_POSITION (V093): the positions staff hold — attaché, deputy
 * attaché, finance officer, ministry director and so on. Security plan N-03:
 * an approval level may require a position, resolved per country at approval
 * time. {@code SORT_ORDER} doubles as rank. Maintained in Settings like every
 * other code list (decision D-17 a).
 */
@Entity
@Table(name = "LU_POSITION", schema = "MOHECORE")
public class LuPosition extends AbstractLuLookup {
}
