package com.dilaw.mohe.model;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A lookup table whose rows may leave the English name blank; the Arabic name is still
 * required (FIX-PLAN P5-6, 26 Sep 2026).
 *
 * <p>Live QA M11 (decision D20-a) made both names required on every lookup. One table
 * has rows with nothing to say in English: the report captions of LU_REPORT_LABEL, where
 * {@code LBL_YEAR_SUFFIX} is "م" after an Arabic year and nothing after an English one,
 * and the report renderer keeps an empty English caption empty on purpose
 * ({@code ReportParamBuilder.addReportLabels}). The rule is set on the table, not on a
 * label's code, so every caption of that table is treated alike.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnglishNameOptional {
}
