package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;

/**
 * The shape every {@code MOHECORE.LU_*} code list shares.
 *
 * <p>Since V072 the table also owns how a code is shown — {@code UI_SEVERITY}
 * (a PrimeNG tag severity: success, info, warn, danger, secondary, contrast)
 * and {@code ICON} (a PrimeIcons name) — and whether the application depends
 * on it: {@code IS_SYSTEM = 1} marks a code that a Java constant names, which
 * the code-list API refuses to delete or rename and the startup guard checks
 * against the constants ({@code CodeListStartupGuard}). Names, order and
 * colours are the database's; behaviour keys are Java's.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractLuLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID", nullable = false)
    private Integer id;

    @Nationalized
    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @Nationalized
    @Column(name = "NAME_AR", nullable = false, length = 200)
    private String nameAr;

    @Nationalized
    @Column(name = "NAME_EN", nullable = false, length = 200)
    private String nameEn;

    @ColumnDefault("0")
    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    @ColumnDefault("0")
    @Column(name = "IS_DELETED", nullable = false)
    private Boolean isDeleted = false;

    /** PrimeNG tag severity for this code; null renders as {@code secondary}. */
    @Nationalized
    @Column(name = "UI_SEVERITY", length = 20)
    private String uiSeverity;

    /** PrimeIcons name (for example {@code pi-check}); null shows no icon. */
    @Nationalized
    @Column(name = "ICON", length = 50)
    private String icon;

    /** A Java constant depends on this code: it cannot be deleted or renamed. */
    @ColumnDefault("0")
    @Column(name = "IS_SYSTEM", nullable = false)
    private Boolean isSystem = false;
}
