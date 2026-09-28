package com.dilaw.mohe.report.jasper;

import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.fonts.FontFamily;
import net.sf.jasperreports.engine.fonts.SimpleFontFace;
import net.sf.jasperreports.engine.fonts.SimpleFontFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/**
 * Ensures the "Cairo" Jasper font family is available even when bundled TTF files
 * are missing or corrupt. Falls back to a system font that supports Arabic.
 */
@Component
public class JasperFontRegistrar {

    private static final Logger log = LoggerFactory.getLogger(JasperFontRegistrar.class);
    public static final String FONT_FAMILY = "Cairo";

    private static final List<String> ARABIC_FALLBACK_FAMILIES = List.of(
            "Cairo", "Segoe UI", "Tahoma", "Arial Unicode MS", "Arial", "DejaVu Sans"
    );

    private final SimpleJasperReportsContext reportsContext;

    public JasperFontRegistrar() {
        SimpleJasperReportsContext ctx = new SimpleJasperReportsContext();
        ctx.setParent(DefaultJasperReportsContext.getInstance());
        JasperReportsContextConfigurer.apply(ctx);
        this.reportsContext = ctx;
    }

    @PostConstruct
    void registerFonts() {
        try {
            SimpleFontFamily family = buildCairoFamily();
            reportsContext.setExtensions(FontFamily.class, List.of(family));
            log.info("Registered Jasper font family '{}'", FONT_FAMILY);
        } catch (Exception e) {
            log.warn("Could not register Jasper font family '{}': {}", FONT_FAMILY, e.getMessage());
        }
    }

    public JasperReportsContext getReportsContext() {
        return reportsContext;
    }

    private SimpleFontFamily buildCairoFamily() throws IOException {
        SimpleFontFamily family = new SimpleFontFamily(reportsContext);
        family.setName(FONT_FAMILY);
        family.setPdfEmbedded(true);
        family.setPdfEncoding("Identity-H");

        Path regular = resolveFontPath("Cairo-Regular.ttf", false);
        Path bold = resolveFontPath("Cairo-Bold.ttf", true);

        SimpleFontFace normalFace = new SimpleFontFace(reportsContext);
        normalFace.setTtf(regular.toString());
        family.setNormalFace(normalFace);

        SimpleFontFace boldFace = new SimpleFontFace(reportsContext);
        boldFace.setTtf(bold.toString());
        family.setBoldFace(boldFace);
        family.setItalicFace(normalFace);
        family.setBoldItalicFace(boldFace);
        return family;
    }

    private Path resolveFontPath(String resourceName, boolean bold) throws IOException {
        String classpath = "/fonts/" + resourceName;
        try (InputStream in = JasperFontRegistrar.class.getResourceAsStream(classpath)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                if (isValidFont(bytes)) {
                    Path tmp = Files.createTempFile("mohe-" + resourceName.replace('.', '-'), ".ttf");
                    tmp.toFile().deleteOnExit();
                    Files.write(tmp, bytes);
                    return tmp;
                }
            }
        }
        return exportSystemFont(bold);
    }

    private static boolean isValidFont(byte[] bytes) {
        if (bytes == null || bytes.length < 10_000) {
            return false;
        }
        // TrueType: 0x00010000; OpenType with CFF: 'OTTO'
        return (bytes[0] == 0x00 && bytes[1] == 0x01 && bytes[2] == 0x00 && bytes[3] == 0x00)
                || (bytes[0] == 'O' && bytes[1] == 'T' && bytes[2] == 'T' && bytes[3] == 'O');
    }

    private Path exportSystemFont(boolean bold) throws IOException {
        String familyName = findAvailableFamily();
        int style = bold ? Font.BOLD : Font.PLAIN;
        Path tmp = Files.createTempFile("mohe-cairo-fallback-", bold ? "-bold.ttf" : ".ttf");
        tmp.toFile().deleteOnExit();
        // java.awt.Font cannot export TTF directly — use physical font file from GraphicsEnvironment
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (Font f : ge.getAllFonts()) {
            if (f.getFamily().equalsIgnoreCase(familyName) && f.getStyle() == style) {
                try {
                    if (f.getFontName().contains("Bold") == bold || (!bold && f.getStyle() == Font.PLAIN)) {
                        // Use font2D - not accessible. Fall through to createFont from file.
                    }
                } catch (Exception ignored) {
                }
            }
        }
        // Last resort: register using AWT logical font mapped path via createTemp copy from Windows Fonts dir
        Path systemFont = locateSystemFontFile(familyName, bold);
        if (systemFont != null) {
            Files.copy(systemFont, tmp, StandardCopyOption.REPLACE_EXISTING);
            log.warn("Using system font '{}' for Jasper '{}'", familyName, FONT_FAMILY);
            return tmp;
        }
        throw new IOException("No usable font found for family " + familyName);
    }

    private static String findAvailableFamily() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        List<String> available = Arrays.asList(ge.getAvailableFontFamilyNames());
        for (String candidate : ARABIC_FALLBACK_FAMILIES) {
            if (available.stream().anyMatch(a -> a.equalsIgnoreCase(candidate))) {
                return candidate;
            }
        }
        return "SansSerif";
    }

    private static Path locateSystemFontFile(String familyName, boolean bold) {
        String winDir = System.getenv("WINDIR");
        if (winDir == null) {
            return null;
        }
        Path fontsDir = Path.of(winDir, "Fonts");
        if (!Files.isDirectory(fontsDir)) {
            return null;
        }
        String[] candidates = switch (familyName.toLowerCase()) {
            case "segoe ui" -> bold
                    ? new String[]{"segoeuib.ttf", "Segoe UI Bold.ttf"}
                    : new String[]{"segoeui.ttf", "Segoe UI.ttf"};
            case "tahoma" -> bold
                    ? new String[]{"tahomabd.ttf", "Tahoma Bold.ttf"}
                    : new String[]{"tahoma.ttf", "Tahoma.ttf"};
            case "arial" -> bold
                    ? new String[]{"arialbd.ttf", "Arial Bold.ttf"}
                    : new String[]{"arial.ttf", "Arial.ttf"};
            default -> bold
                    ? new String[]{familyName.toLowerCase().replace(" ", "") + "bd.ttf"}
                    : new String[]{familyName.toLowerCase().replace(" ", "") + ".ttf"};
        };
        for (String name : candidates) {
            Path p = fontsDir.resolve(name);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }
}
