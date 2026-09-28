package com.dilaw.mohe.systemsettings;

import com.dilaw.mohe.config.FileEncryptionProperties;
import com.dilaw.mohe.service.FileEncryptionService;
import com.dilaw.mohe.systemsettings.controller.SysSettingsController;
import com.dilaw.mohe.systemsettings.model.SysSettings;
import com.dilaw.mohe.systemsettings.repository.SysSettingsRepository;
import com.dilaw.mohe.systemsettings.service.SysSettingsService;
import com.dilaw.mohe.util.FileUploadValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The system logo: what may be uploaded, how it is named, how it is served.
 *
 * <p>Revision item 1.11. The upload used to skip {@code FileUploadValidator}, took
 * the extension from the client's filename and served the file back — anonymously,
 * on the login page — with a content type chosen by that extension. A
 * {@code SETTINGS.EDIT} holder could upload a scripted {@code logo.svg} and have it
 * run on the application origin in every visitor's browser.
 *
 * <p>Real {@link FileEncryptionService} (the dev key) and a real temp directory; the
 * settings row is a mocked repository holding one entity.
 */
class LogoUploadValidationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final String CLEAN_SVG =
            "<?xml version=\"1.0\"?>\n<!-- ministry -->\n<svg xmlns=\"http://www.w3.org/2000/svg\"><circle r=\"4\"/></svg>";
    private static final String SCRIPTED_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(document.cookie)</script></svg>";
    private static final String HANDLER_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"fetch('/x')\"><circle r=\"4\"/></svg>";

    @TempDir
    Path uploads;

    private SysSettingsService service;
    private SysSettings stored;

    @BeforeEach
    void wire() {
        FileEncryptionProperties props = new FileEncryptionProperties();
        ReflectionTestUtils.setField(props, "keyBase64", "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=");
        ReflectionTestUtils.invokeMethod(props, "validate");

        SysSettingsRepository repo = mock(SysSettingsRepository.class);
        when(repo.findById(1)).thenAnswer(inv -> Optional.ofNullable(stored));
        when(repo.save(any(SysSettings.class))).thenAnswer(inv -> stored = inv.getArgument(0));

        service = new SysSettingsService(repo, new FileEncryptionService(props));
        ReflectionTestUtils.setField(service, "uploadDir", uploads.toString());
    }

    private static MockMultipartFile upload(String filename, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", filename, contentType, bytes);
    }

    private static void assertRejected(MockMultipartFile file, String because) {
        assertThatThrownBy(() -> FileUploadValidator.detectLogoExtension(file))
                .as(because).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a .png whose bytes are HTML is rejected")
    void htmlDressedAsPng() {
        MockMultipartFile file = upload("logo.png", "image/png", "<html><script>1</script></html>".getBytes(StandardCharsets.UTF_8));
        assertRejected(file, "declared PNG, no PNG signature");
        assertThatThrownBy(() -> service.uploadLogo(file))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stored).as("nothing stored").isNull();
    }

    @Test
    @DisplayName("an SVG with a script or an event handler is rejected; a clean one is accepted")
    void scriptedSvg() {
        assertRejected(upload("logo.svg", "image/svg+xml", SCRIPTED_SVG.getBytes(StandardCharsets.UTF_8)), "<script>");
        assertRejected(upload("logo.svg", "image/svg+xml", HANDLER_SVG.getBytes(StandardCharsets.UTF_8)), "onload=");
        assertRejected(upload("logo.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><a href=\"JavaScript:x()\">a</a></svg>".getBytes(StandardCharsets.UTF_8)), "javascript:");
        assertThat(FileUploadValidator.detectLogoExtension(
                upload("logo.svg", "image/svg+xml", CLEAN_SVG.getBytes(StandardCharsets.UTF_8)))).isEqualTo("svg");
    }

    @Test
    @DisplayName("a clean PNG is stored under the detected extension, whatever the client called it")
    void storedUnderDetectedExtension() throws Exception {
        service.uploadLogo(upload("anything.svg", "image/png", PNG));

        assertThat(stored.getSysLogoFilename()).isEqualTo("system-logo.png");
        assertThat(stored.getSysLogoPath()).isEqualTo("logos/system-logo.png");
        assertThat(Files.exists(uploads.resolve("logos/system-logo.png"))).isTrue();
        assertThat(service.getLogoContentType()).isEqualTo("image/png");
        assertThat(service.getLogoBytes()).as("round trip through encryption").isEqualTo(PNG);

        // Replaced by an SVG: the PNG goes, the settings row follows.
        service.uploadLogo(upload("logo.svg", "image/svg+xml", CLEAN_SVG.getBytes(StandardCharsets.UTF_8)));
        assertThat(stored.getSysLogoFilename()).isEqualTo("system-logo.svg");
        assertThat(Files.exists(uploads.resolve("logos/system-logo.png"))).isFalse();
    }

    @Test
    @DisplayName("a declared type that does not match the bytes is rejected even when both are allowed")
    void declaredTypeMustMatchBytes() {
        assertRejected(upload("logo.svg", "image/svg+xml", PNG), "PNG bytes declared as SVG");
        assertRejected(upload("logo.gif", "image/gif", PNG), "PNG bytes declared as GIF");
        assertRejected(upload("logo.webp", "image/webp", PNG), "type not in the allow-list");
    }

    @Test
    @DisplayName("GET /logo carries Content-Security-Policy: sandbox and X-Content-Type-Options: nosniff")
    void getCarriesHeaders() throws Exception {
        service.uploadLogo(upload("logo.svg", "image/svg+xml", CLEAN_SVG.getBytes(StandardCharsets.UTF_8)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SysSettingsController(service)).build();

        mvc.perform(get("/api/v1/sys-settings/logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(CLEAN_SVG.getBytes(StandardCharsets.UTF_8)));
    }
}
