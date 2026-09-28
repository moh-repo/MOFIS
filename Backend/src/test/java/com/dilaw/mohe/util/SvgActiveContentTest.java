package com.dilaw.mohe.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * System test PRT-13: the logo's SVG screen looks at the document the browser will
 * see — character references decoded, case folded, whitespace inside a URL ignored —
 * rather than at the raw bytes, which {@code javascript&#58;} walked past.
 */
class SvgActiveContentTest {

    private static final String OPEN = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">";

    private static MockMultipartFile svg(String body) {
        return new MockMultipartFile("file", "logo.svg", "image/svg+xml", body.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            OPEN + "<a href=\"javascript&#58;alert(1)\"><circle r=\"4\"/></a></svg>",
            OPEN + "<a href=\"javascript&#x3A;alert(1)\"><circle r=\"4\"/></a></svg>",
            OPEN + "<a href=\"javascript&#0000058alert(1)\"><circle r=\"4\"/></a></svg>",
            OPEN + "<a href=\"java&#9;script:alert(1)\"><circle r=\"4\"/></a></svg>",
            OPEN + "<a href=\"&#106;&#97;&#118;&#97;&#115;&#99;&#114;&#105;&#112;&#116;:alert(1)\">x</a></svg>",
            OPEN + "<a href=\"javascript&amp;#58;alert(1)\">x</a></svg>",
            OPEN + "<a href=\"javascript&colon;alert(1)\">x</a></svg>",
            OPEN + "<a xlink:href=\"data:text/html;base64,PHNjcmlwdD4=\">x</a></svg>",
            OPEN + "<a href=\"vbscript:msgbox(1)\">x</a></svg>",
            "<svg/onload=\"alert(1)\" xmlns=\"http://www.w3.org/2000/svg\"></svg>",
            OPEN + "<circle r=\"4\" xlink:onclick=\"alert(1)\"/></svg>",
            OPEN + "<foreignObject><iframe srcdoc=\"&lt;script&gt;alert(1)&lt;/script&gt;\"></iframe></foreignObject></svg>",
            OPEN + "<svg:script>alert(1)</svg:script></svg>",
            OPEN + "<set attributeName=\"href\" to=\"javascript:alert(1)\"/></svg>",
            OPEN + "<style>@import url(//evil.example/x.css);</style></svg>",
            OPEN + "<embed src=\"x.swf\"/></svg>",
    })
    @DisplayName("active content is refused, however it is spelled")
    void refused(String body) {
        assertThat(FileUploadValidator.hasActiveSvgContent(body)).as(body).isTrue();
        assertThatThrownBy(() -> FileUploadValidator.detectLogoExtension(svg(body)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a real-world logo still passes: metadata, gradients, styles, Arabic text by reference, an embedded PNG")
    void cleanLogoAccepted() {
        String logo = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n"
                + OPEN
                + "<metadata><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"/></metadata>"
                + "<defs><linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"#0a0\"/></linearGradient>"
                + "<style>.a{fill:url(#g);opacity:.8}</style></defs>"
                + "<text x=\"1\" y=\"2\">&#1608;&#1586;&#1575;&#1585;&#1577; &amp; MOHE</text>"
                + "<image width=\"4\" height=\"4\" xlink:href=\"data:image/png;base64,iVBORw0KGgo=\"/>"
                + "<circle class=\"a\" r=\"4\"/></svg>";
        assertThat(FileUploadValidator.hasActiveSvgContent(logo)).isFalse();
        assertThat(FileUploadValidator.detectLogoExtension(svg(logo))).isEqualTo("svg");
    }

    @Test
    @DisplayName("references decode repeatedly; an unknown named one is not guessed at")
    void decoding() {
        assertThat(FileUploadValidator.decodeSvgReferences("a&#58;b&#x3a;c&amp;#58;")).isEqualTo("a:b:c:");
        assertThat(FileUploadValidator.decodeSvgReferences("x&Tab;y")).isNull();
        assertThat(FileUploadValidator.decodeSvgReferences("plain")).isEqualTo("plain");
    }
}
