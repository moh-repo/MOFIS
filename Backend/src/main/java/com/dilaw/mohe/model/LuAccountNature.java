package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Code list LU_ACCOUNT_NATURE (V073): the five natures of a chart-of-accounts
 * account and the side ({@code D} or {@code C}) their normal balance sits on.
 */
@Getter
@Setter
@Entity
@Table(name = "LU_ACCOUNT_NATURE", schema = "MOHECORE")
public class LuAccountNature extends AbstractLuLookup {

    @Nationalized
    @Column(name = "NORMAL_BALANCE", length = 1)
    @Size(max = 1)
    private String normalBalance;
}
