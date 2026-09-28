package com.dilaw.mohe.report;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** S-07: the export line lands on every PDF page and on every sheet. */
class ExportWatermarkTest {

    private static final String LINE = "Exported by jo_admin on 2026-09-06 21:05 for Jordan";

    private static byte[] twoPagePdf() throws Exception {
        Document doc = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph("page one"));
        doc.newPage();
        doc.add(new Paragraph("page two"));
        doc.close();
        return out.toByteArray();
    }

    @Test
    @DisplayName("every page of a PDF carries the line; the pages themselves are kept")
    void pdfFooter() throws Exception {
        byte[] stamped = ExportWatermark.pdf(twoPagePdf(), LINE);
        PdfReader reader = new PdfReader(stamped);
        assertThat(reader.getNumberOfPages()).isEqualTo(2);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int i = 1; i <= 2; i++) {
            String text = extractor.getTextFromPage(i);
            assertThat(text).contains("page " + (i == 1 ? "one" : "two")).contains("jo_admin").contains("Jordan");
        }
        reader.close();
    }

    @Test
    @DisplayName("a workbook gets the line under the data and in the print footer of each sheet")
    void workbookFooter() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet s1 = wb.createSheet("Report");
            s1.createRow(0).createCell(0).setCellValue("header");
            s1.createRow(1).createCell(0).setCellValue("row");
            wb.createSheet("Empty");
            wb.write(out);
        }
        byte[] stamped = ExportWatermark.xlsx(out.toByteArray(), LINE);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(stamped))) {
            XSSFSheet s1 = wb.getSheet("Report");
            assertThat(s1.getRow(1).getCell(0).getStringCellValue()).as("data untouched").isEqualTo("row");
            assertThat(s1.getRow(3).getCell(0).getStringCellValue()).isEqualTo(LINE);
            assertThat(s1.getFooter().getLeft()).isEqualTo(LINE);
            XSSFSheet s2 = wb.getSheet("Empty");
            assertThat(s2.getRow(0).getCell(0).getStringCellValue()).isEqualTo(LINE);
        }
    }

    @Test
    @DisplayName("nothing to say, or nothing to stamp, returns the input")
    void passThrough() throws Exception {
        byte[] pdf = twoPagePdf();
        assertThat(ExportWatermark.pdf(pdf, "")).isSameAs(pdf);
        assertThat(ExportWatermark.pdf(null, LINE)).isNull();
        byte[] junk = "not a pdf".getBytes();
        assertThat(ExportWatermark.pdf(junk, LINE)).as("unstampable bytes come back as they were").isSameAs(junk);
        assertThat(ExportWatermark.xlsx(junk, LINE)).isSameAs(junk);
    }
}
