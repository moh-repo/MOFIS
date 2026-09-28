package com.dilaw.mohe.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Validates spreadsheet uploads by their magic bytes.
 *
 * <p>Separate from {@link FileUploadValidator} on purpose. That one guards
 * student documents and photographs and admits only JPEG, PNG, GIF and PDF;
 * adding the Office types to it would widen what can be attached to a student
 * record everywhere in the system, to serve one import screen. The two lists
 * have different reasons to change, so they are different classes.
 *
 * <p>Content type alone is not checked as a gate: browsers disagree about what
 * an .xlsx is (Chrome sends the OOXML type, some clients send
 * {@code application/octet-stream}), so the bytes are the authority and the
 * extension only picks which signature to expect.
 */
public final class SpreadsheetUploadValidator {

    /** XLSX/XLSM — a ZIP container. */
    private static final byte[] MAGIC_ZIP = { 0x50, 0x4B, 0x03, 0x04 };

    /** Legacy XLS — the OLE2 compound document header. */
    private static final byte[] MAGIC_OLE2 =
            { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 };

    private SpreadsheetUploadValidator() {
    }

    /**
     * @throws IllegalArgumentException if the file is empty, is not named as a
     *         spreadsheet, or does not begin with the signature its extension claims
     */
    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded.");
        }

        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        boolean xlsx = lower.endsWith(".xlsx") || lower.endsWith(".xlsm");
        boolean xls = lower.endsWith(".xls");
        if (!xlsx && !xls) {
            throw new IllegalArgumentException(
                    "Only Excel workbooks are accepted here (.xlsx, .xlsm, .xls).");
        }

        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file.", e);
        }

        boolean ok = xlsx ? startsWith(header, MAGIC_ZIP) : startsWith(header, MAGIC_OLE2);
        if (!ok) {
            throw new IllegalArgumentException(
                    "The file's contents do not match its extension. Upload rejected.");
        }
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
