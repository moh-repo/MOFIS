package com.dilaw.mohe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Report captions; a caption may have no English text (LBL_YEAR_SUFFIX): {@link EnglishNameOptional}. */
@Entity
@EnglishNameOptional
@Table(name = "LU_REPORT_LABEL", schema = "MOHECORE")
public class LuReportLabel extends AbstractLuLookup {

    /**
     * A caption with no English text is stored as "", not NULL: NAME_EN is NOT NULL, and
     * a new label saved without the English name arrived as null and was refused "A
     * required value is missing" (live QA R5-24). Here rather than in a controller, so the
     * lookup screen and Code Lists both get it.
     */
    @PrePersist
    @PreUpdate
    void storeMissingEnglishAsEmpty() {
        if (getNameEn() == null) {
            setNameEn("");
        }
    }
}
