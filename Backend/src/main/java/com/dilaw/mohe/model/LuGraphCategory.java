package com.dilaw.mohe.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Code list LU_GRAPH_CATEGORY (V073): the branches of the student graph
 * ({@code cat_personal}, {@code cat_finance} …) with the colour each branch is
 * drawn in, which used to be a map in the Angular component.
 */
@Getter
@Setter
@Entity
@Table(name = "LU_GRAPH_CATEGORY", schema = "MOHECORE")
public class LuGraphCategory extends AbstractLuLookup {

    /** Hex colour of the branch, for example {@code #4CAF50}. */
    @Nationalized
    @Column(name = "UI_COLOR", length = 9)
    @Size(max = 9)
    private String uiColor;
}
