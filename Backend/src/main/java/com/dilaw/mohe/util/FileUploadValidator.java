package com.dilaw.mohe.util;

import com.dilaw.mohe.exception.LocalizedArgumentException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Validates uploaded files against an allowed MIME type whitelist and checks
 * file magic bytes to prevent extension-spoofing attacks.
 */
public final class FileUploadValidator {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "application/pdf"
    );

    // Magic byte signatures: JPEG, PNG, GIF, PDF
    private static final byte[][] MAGIC_JPEG = { { (byte)0xFF, (byte)0xD8, (byte)0xFF } };
    private static final byte[][] MAGIC_PNG  = { { (byte)0x89, 0x50, 0x4E, 0x47 } };
    private static final byte[][] MAGIC_GIF  = { { 0x47, 0x49, 0x46, 0x38 } };
    private static final byte[][] MAGIC_PDF  = { { 0x25, 0x50, 0x44, 0x46 } }; // %PDF

    private FileUploadValidator() {}

    /**
     * Validates that the file's declared content-type is in the whitelist and that
     * the first bytes match a known signature for that type.
     *
     * @throws IllegalArgumentException if the file is rejected
     */
    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        // Refusals carry a message key (live QA L13, P30: they were English on the
        // Arabic screen); the English text stays as the exception message, for logs.
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new LocalizedArgumentException(
                    "File type not allowed: " + contentType + ". Accepted types: JPEG, PNG, GIF, PDF.",
                    "upload.typeNotAllowed");
        }

        byte[] header;
        try (InputStream is = file.getInputStream()) {
            header = is.readNBytes(8);
        } catch (IOException e) {
            LocalizedArgumentException refused = new LocalizedArgumentException(
                    "Could not read file for validation.", "upload.unreadable");
            refused.initCause(e);
            throw refused;
        }
        if (!matchesMagic(contentType, header)) {
            throw new LocalizedArgumentException(
                    "File content does not match its declared type. Upload rejected.", "upload.contentMismatch");
        }
    }

    /** The file-name extensions each accepted type may carry. */
    private static final java.util.Map<String, java.util.List<String>> EXTENSIONS = java.util.Map.of(
            "application/pdf", java.util.List.of("pdf"),
            "image/jpeg", java.util.List.of("jpg", "jpeg"),
            "image/png", java.util.List.of("png"),
            "image/gif", java.util.List.of("gif"));

    /**
     * The file's name must end in an extension of the type {@link #validate} found in
     * its bytes (live QA L12, P9: a PNG named {@code photo.exe} was stored, and handed
     * back under that name). Call after {@link #validate}, which has already matched the
     * declared type to the bytes.
     *
     * @throws LocalizedArgumentException (400) naming the extensions the file may have
     */
    public static void requireMatchingExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> allowed = EXTENSIONS.getOrDefault(type, java.util.List.of());
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().strip();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        if (!allowed.contains(extension)) {
            String expected = String.join(" / ", allowed.stream().map(e -> "." + e).toList());
            throw new LocalizedArgumentException(
                    "The file name must end in " + expected + " for this type of file.",
                    "upload.extensionMismatch", expected);
        }
    }

    // ── System logo ──────────────────────────────────────────────────────────

    /** What a logo may be. SVG is allowed because the ministry's own logo is one. */
    private static final Set<String> LOGO_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/svg+xml"
    );

    /**
     * Active content inside an SVG. An SVG is a document, not a picture: served
     * inline as {@code image/svg+xml} from the application's own origin, a
     * {@code <script>} or an {@code onload=} runs with that origin's cookies and
     * storage. {@code <!ENTITY} is refused as well — an external entity is the
     * XML parser's way of reading a file. A logo has no legitimate use for any
     * of these.
     *
     * <p>PRT-13: matched against the text after character references are decoded
     * ({@link #decodeSvgReferences}) and lower-cased. The old pattern ran on the raw
     * bytes, so {@code href="javascript&#58;..."} — which the XML parser turns into
     * {@code javascript:} — passed, as did {@code <svg/onload=...>} (no space before
     * the handler) and elements that load or embed a document ({@code foreignObject},
     * {@code iframe}, {@code srcdoc}...).
     */
    private static final java.util.regex.Pattern SVG_ACTIVE_CONTENT = java.util.regex.Pattern.compile(
            "<\\s*(?:[a-z0-9_.-]+:)?(?:script|foreignobject|iframe|frame|frameset|embed|object|applet|handler|listener|base|meta|link)\\b"
                    + "|[\\s/\"'](?:[a-z0-9_.-]+:)?on[a-z]+\\s*="
                    + "|<!entity|<!doctype[^>]*\\[|<\\?xml-stylesheet"
                    + "|\\bsrcdoc\\s*=|\\bformaction\\s*=|@import|expression\\s*\\(");

    /**
     * A URL scheme that runs or embeds a document, checked with every character up to
     * the space removed as well, because a browser drops tabs and newlines inside a URL
     * ({@code java&#9;script:}). {@code data:} is allowed only for an embedded PNG, JPEG or
     * GIF, which is how drawing programs store a raster inside an SVG.
     */
    private static final java.util.regex.Pattern SVG_ACTIVE_SCHEME = java.util.regex.Pattern.compile(
            "(?:javascript|vbscript|livescript|mhtml):|data:(?!image/(?:png|jpe?g|gif)[;,])");

    /** The five references XML defines without a DTD; any other named one is refused. */
    private static final java.util.Map<String, String> XML_ENTITIES = java.util.Map.of(
            "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'");
    private static final java.util.regex.Pattern CHARACTER_REFERENCE = java.util.regex.Pattern.compile(
            "&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z][a-zA-Z0-9]*);?");

    /**
     * PRT-13: {@code text} with its character references resolved — numeric ones and the
     * five XML names, repeated until nothing changes so {@code &amp;#58;} is caught too.
     * A named reference XML does not define (HTML's {@code &colon;}, {@code &Tab;}) makes
     * the result null: a logo has no use for one, and it is the classic way to hide a
     * scheme from a filter.
     */
    static String decodeSvgReferences(String text) {
        String current = text;
        for (int round = 0; round < 5; round++) {
            java.util.regex.Matcher m = CHARACTER_REFERENCE.matcher(current);
            StringBuilder out = new StringBuilder();
            boolean changed = false;
            while (m.find()) {
                String ref = m.group(1);
                String replacement;
                if (ref.startsWith("#")) {
                    int codePoint;
                    try {
                        codePoint = ref.length() > 1 && (ref.charAt(1) == 'x' || ref.charAt(1) == 'X')
                                ? Integer.parseInt(ref.substring(2), 16) : Integer.parseInt(ref.substring(1));
                    } catch (NumberFormatException tooLong) {
                        return null;
                    }
                    if (!Character.isValidCodePoint(codePoint)) return null;
                    replacement = new String(Character.toChars(codePoint));
                } else {
                    replacement = XML_ENTITIES.get(ref);
                    if (replacement == null) return null;
                }
                m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(replacement));
                changed = true;
            }
            m.appendTail(out);
            if (!changed) return current;
            current = out.toString();
        }
        return current;
    }

    /** True when the SVG's text, decoded, contains anything {@link #SVG_ACTIVE_CONTENT} or {@link #SVG_ACTIVE_SCHEME} names. */
    static boolean hasActiveSvgContent(String text) {
        String decoded = decodeSvgReferences(text);
        if (decoded == null) return true;
        String lower = decoded.toLowerCase(java.util.Locale.ROOT);
        if (SVG_ACTIVE_CONTENT.matcher(lower).find()) return true;
        StringBuilder compact = new StringBuilder(lower.length());
        lower.codePoints().filter(c -> c > 0x20 && c != 0x7F && c != 0xA0).forEach(compact::appendCodePoint);
        return SVG_ACTIVE_SCHEME.matcher(compact).find();
    }

    /**
     * Validates a system-logo upload and returns the extension the file must be
     * stored under: {@code png}, {@code jpg}, {@code gif} or {@code svg}.
     *
     * <p>The extension comes from the <em>detected</em> type — the bytes — never
     * from the client's filename. Until August 2026 (revision item 1.11) the logo
     * upload skipped this class entirely, took the extension from the filename,
     * and served the file back on an anonymous endpoint with a content type
     * chosen by that extension: any {@code SETTINGS.EDIT} holder could upload
     * {@code logo.svg} containing a script and have it served as
     * {@code image/svg+xml} from the application origin to every visitor of the
     * login page. The declared MIME type must be one of the four, the bytes must
     * carry that type's signature, and an SVG must contain no active content.
     *
     * @throws IllegalArgumentException if the file is rejected
     */
    public static String detectLogoExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !LOGO_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "File type not allowed: " + contentType + ". Accepted types: PNG, JPEG, GIF, SVG.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file for validation.", e);
        }
        String detected = detectLogoType(bytes);
        if (detected == null || !detected.equals(contentType.toLowerCase())) {
            throw new IllegalArgumentException("File content does not match its declared type. Upload rejected.");
        }
        return switch (detected) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            default -> "svg";
        };
    }

    /** The MIME type the bytes say they are, or null when they are none of the four. */
    private static String detectLogoType(byte[] bytes) {
        if (startsWith(bytes, MAGIC_PNG[0])) return "image/png";
        if (startsWith(bytes, MAGIC_JPEG[0])) return "image/jpeg";
        if (startsWith(bytes, MAGIC_GIF[0])) return "image/gif";
        if (isSvg(bytes)) return "image/svg+xml";
        return null;
    }

    /**
     * An SVG has no magic number: it is text whose first element, after an
     * optional BOM, XML declaration, comments and DOCTYPE, is {@code <svg}. The
     * whole document is then screened for {@link #SVG_ACTIVE_CONTENT}.
     */
    private static boolean isSvg(byte[] bytes) {
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        // Strip the prolog: whitespace, <?xml ...?>, <!-- ... -->, <!DOCTYPE ...> in any order.
        String s = text.stripLeading();
        while (true) {
            if (s.startsWith("<?")) {
                int end = s.indexOf("?>");
                if (end < 0) return false;
                s = s.substring(end + 2).stripLeading();
            } else if (s.startsWith("<!--")) {
                int end = s.indexOf("-->");
                if (end < 0) return false;
                s = s.substring(end + 3).stripLeading();
            } else if (s.regionMatches(true, 0, "<!DOCTYPE", 0, 9)) {
                int end = s.indexOf('>');
                if (end < 0) return false;
                s = s.substring(end + 1).stripLeading();
            } else {
                break;
            }
        }
        if (!s.regionMatches(true, 0, "<svg", 0, 4)) {
            return false;
        }
        if (hasActiveSvgContent(text)) {
            throw new IllegalArgumentException("SVG contains script or event handlers. Upload rejected.");
        }
        return true;
    }

    // ── Database backup ──────────────────────────────────────────────────────

    /** Microsoft Tape Format: every SQL Server backup file begins with {@code TAPE}. */
    private static final byte[] MAGIC_MTF = { 'T', 'A', 'P', 'E' };

    /**
     * Validates a backup uploaded for restore, before any SQL runs.
     *
     * <p>The name must be a bare {@code .bak} filename (no path separators, no
     * {@code ..} — the same rules as {@code SafePathUtils.resolveFilenameUnderRoot}),
     * and the content must be either a backup this application encrypted
     * ({@code MENC} header, see {@code FileCipher}) or a plain SQL Server backup
     * ({@code TAPE}). Until August 2026 (revision item 1.12) the {@code .bak}
     * suffix was the only check, and whatever was uploaded went straight into
     * {@code RESTORE DATABASE ... WITH REPLACE}. The header is a cheap first gate;
     * {@code BackupService} then reads the real header with
     * {@code RESTORE HEADERONLY} before restoring.
     *
     * @throws IllegalArgumentException if the file is rejected
     */
    public static void validateBackupUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        if (!name.toLowerCase().endsWith(".bak")) {
            throw new IllegalArgumentException("Invalid file type. Only .bak files are allowed");
        }
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(4);
            if (!startsWith(header, MAGIC_MTF) && !startsWith(header, com.dilaw.mohe.util.crypto.FileCipher.MAGIC)) {
                throw new IllegalArgumentException("File is not a SQL Server backup. Upload rejected.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file for validation.", e);
        }
    }

    /** True when the (decrypted) bytes begin with the SQL Server backup signature. */
    public static boolean isSqlServerBackup(byte[] head) {
        return startsWith(head, MAGIC_MTF);
    }

    private static boolean matchesMagic(String contentType, byte[] header) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> startsWith(header, MAGIC_JPEG[0]);
            case "image/png"  -> startsWith(header, MAGIC_PNG[0]);
            case "image/gif"  -> startsWith(header, MAGIC_GIF[0]);
            case "application/pdf" -> startsWith(header, MAGIC_PDF[0]);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        if (data.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) return false;
        }
        return true;
    }
}
