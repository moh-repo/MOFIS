package com.dilaw.mohe.report;

import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Puts the export line ("Exported by ... on ... for ...") on every exported
 * file (security plan S-07): a footer on every PDF page, a trailing row and a
 * print footer on every sheet of a workbook. A leaked file then says who took
 * it out and when.
 *
 * <p>Stamping is a post-process on the bytes, so the forty-odd Jasper
 * templates and the two POI builders are untouched. It never throws: a file
 * that could not be stamped is returned as it was, with a warning, because a
 * missing footer is a lesser failure than a missing report.
 */
public final class ExportWatermark {

    private static final Logger log = LoggerFactory.getLogger(ExportWatermark.class);
    private static final float FONT_SIZE = 7.5f;
    private static final float MARGIN = 18f;
    private static volatile BaseFont font;

    private ExportWatermark() {}

    /** The same PDF with {@code text} in small grey type at the foot of each page. */
    public static byte[] pdf(byte[] pdf, String text) {
        if (pdf == null || text == null || text.isBlank()) return pdf;
        try {
            PdfReader reader = new PdfReader(pdf);
            ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length + 2048);
            PdfStamper stamper = new PdfStamper(reader, out);
            BaseFont bf = font();
            int pages = reader.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                Rectangle box = reader.getPageSizeWithRotation(i);
                PdfContentByte over = stamper.getOverContent(i);
                over.saveState();
                over.setColorFill(new Color(110, 110, 110));
                over.beginText();
                over.setFontAndSize(bf, FONT_SIZE);
                over.endText();
                com.lowagie.text.Phrase phrase = new com.lowagie.text.Phrase(text,
                        new com.lowagie.text.Font(bf, FONT_SIZE, com.lowagie.text.Font.NORMAL, new Color(110, 110, 110)));
                ColumnText.showTextAligned(over, Element.ALIGN_LEFT, phrase, box.getLeft() + MARGIN,
                        box.getBottom() + MARGIN * 0.6f, 0, PdfWriter.RUN_DIRECTION_LTR, 0);
                over.restoreState();
            }
            stamper.close();
            reader.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Export watermark not applied to a PDF: {}", e.getMessage());
            return pdf;
        }
    }

    /** The same workbook with {@code text} two rows under the data and in the print footer of every sheet. */
    public static byte[] xlsx(byte[] xlsx, String text) {
        if (xlsx == null || text == null || text.isBlank()) return xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
             ByteArrayOutputStream out = new ByteArrayOutputStream(xlsx.length + 2048)) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                stampSheet(wb, wb.getSheetAt(i), text);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Export watermark not applied to a workbook: {}", e.getMessage());
            return xlsx;
        }
    }

    /** For a builder that still holds the workbook open: the same stamp, without a re-parse. */
    public static void stampSheet(org.apache.poi.ss.usermodel.Workbook wb, Sheet sheet, String text) {
        if (sheet == null || text == null || text.isBlank()) return;
        int last = sheet.getLastRowNum();
        Row row = sheet.createRow(sheet.getPhysicalNumberOfRows() == 0 ? 0 : last + 2);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        CellStyle style = wb.createCellStyle();
        Font f = wb.createFont();
        f.setItalic(true);
        f.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        f.setFontHeightInPoints((short) 8);
        style.setFont(f);
        cell.setCellStyle(style);
        sheet.getFooter().setLeft(text);
    }

    private static BaseFont font() throws IOException {
        BaseFont bf = font;
        if (bf != null) return bf;
        synchronized (ExportWatermark.class) {
            if (font != null) return font;
            try (InputStream in = ExportWatermark.class.getResourceAsStream("/fonts/Cairo-Regular.ttf")) {
                if (in != null) {
                    font = BaseFont.createFont("Cairo-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true,
                            in.readAllBytes(), null);
                    return font;
                }
            } catch (Exception e) {
                log.debug("Cairo font unavailable for the watermark, using Helvetica: {}", e.getMessage());
            }
            font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            return font;
        }
    }
}
