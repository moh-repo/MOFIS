package com.dilaw.mohe.report.jasper;

import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.xml.JRReportSaxParserFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParser;

/**
 * Fixes JasperReports 6.21.3 + Java 17 SAX parser incompatibility.
 *
 * Root cause (confirmed via JAR bytecode inspection):
 * - BaseSaxParserFactory.createParser() calls configureParser() unconditionally.
 * - configureParser() calls SAXParser.setProperty(schemaLanguage) then setProperty(schemaSource).
 * - In Java 17's Xerces JAXPSAXParser, setProperty(schemaLanguage) does NOT store the value
 *   in fInitProperties when the parser was created with setValidating(false), so the subsequent
 *   setProperty(schemaSource) throws SAXNotSupportedException claiming schemaLanguage wasn't set.
 * - isValidating() alone cannot fix this because configureParser() is called regardless of its value.
 *
 * Fix: override configureParser() to be a no-op, bypassing all schema property setup.
 * Schema validation is not needed for JRXML compilation (pure parsing only).
 *
 * Registered via net.sf.jasperreports.compiler.xml.parser.factory in DefaultJasperReportsContext
 * so JRXmlDigesterFactory instantiates this class instead of JRReportSaxParserFactory.
 */
public class NonValidatingSaxParserFactory extends JRReportSaxParserFactory {

    public NonValidatingSaxParserFactory(JasperReportsContext jasperReportsContext) {
        super(jasperReportsContext);
    }

    @Override
    protected boolean isValidating() {
        return false;
    }

    @Override
    protected void configureParser(SAXParser parser) throws SAXException {
        // No-op: bypasses setProperty(schemaLanguage/schemaSource) which fails on Java 17
    }
}
