package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Code list LU_FEE_SCOPE. The codes repeat per {@code SCOPE_TYPE} (SEMESTER,
 * YEAR), so the unique key is (SCOPE_TYPE, CODE), not CODE alone.
 */
@Getter
@Setter
@Entity
@Table(name = "LU_FEE_SCOPE", schema = "MOHECORE")
public class LuFeeScope extends AbstractLuLookup {

    @Nationalized
    @Column(name = "SCOPE_TYPE", nullable = false, length = 20)
    private String scopeType;
}
