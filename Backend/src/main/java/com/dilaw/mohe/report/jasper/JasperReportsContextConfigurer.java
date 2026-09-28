package com.dilaw.mohe.report.jasper;

import net.sf.jasperreports.engine.JasperReportsContext;

/**
 * Shared JRXML compiler settings for runtime and tests.
 * Uses a non-validating SAX parser so legacy DTD default attributes
 * (isStretchWithOverflow, isSplitAllowed) are not injected into every element.
 */
final class JasperReportsContextConfigurer {

    private static final String PARSER_FACTORY =
            "com.dilaw.mohe.report.jasper.NonValidatingSaxParserFactory";

    private JasperReportsContextConfigurer() {
    }

    static void apply(JasperReportsContext ctx) {
        ctx.setProperty("net.sf.jasperreports.compiler.xml.validation", "false");
        ctx.setProperty("net.sf.jasperreports.compiler.xml.parser.factory", PARSER_FACTORY);
    }
}
