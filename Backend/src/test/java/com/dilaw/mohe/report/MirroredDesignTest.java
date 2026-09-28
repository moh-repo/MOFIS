package com.dilaw.mohe.report;

import com.dilaw.mohe.report.jasper.MirroredDesign;
import net.sf.jasperreports.engine.JRChild;
import net.sf.jasperreports.engine.JRElementGroup;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignTextElement;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirroring the prescribed voucher for an English reading.
 *
 * <p>نموذج رقم ١١ is laid out right to left — the amount column is the rightmost, the
 * sections run أ, ب, ج, د from the right — and Jasper's x, textAlignment and
 * runDirection are all static XML attributes, so none of it can follow a language
 * parameter. Rather than keep a second English template in step by hand, the design is
 * mirrored in memory before it is compiled.
 *
 * <p>What is pinned here is that the mirror is a mirror: every element reflected about
 * the same axis, alignment swapped at the edges and left alone in the middle, and the
 * vertical layout untouched, so the form keeps its rows.
 */
class MirroredDesignTest {

    private static final String TEMPLATE = "/reports/payment-auth-form.jrxml";

    private static JasperDesign load() throws Exception {
        try (InputStream is = MirroredDesignTest.class.getResourceAsStream(TEMPLATE)) {
            assertThat(is).as("the voucher template is not on the classpath").isNotNull();
            return JRXmlLoader.load(is);
        }
    }

    private static String source() throws Exception {
        try (InputStream is = MirroredDesignTest.class.getResourceAsStream(TEMPLATE)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Every element of every band, flattened, in a stable order. */
    private static List<JRDesignElement> elements(JasperDesign design) {
        List<JRDesignElement> out = new ArrayList<>();
        collect(design.getPageHeader() == null ? null : design.getPageHeader().getChildren(), out);
        for (var band : design.getDetailSection().getBands()) {
            collect(band.getChildren(), out);
        }
        collect(design.getPageFooter() == null ? null : design.getPageFooter().getChildren(), out);
        return out;
    }

    private static void collect(List<JRChild> children, List<JRDesignElement> out) {
        if (children == null) {
            return;
        }
        for (JRChild child : children) {
            if (child instanceof JRDesignElement e) {
                out.add(e);
            } else if (child instanceof JRElementGroup g) {
                collect(g.getChildren(), out);
            }
        }
    }

    @Test
    @DisplayName("the voucher asks to be mirrored; that is what makes it opt-in")
    void theVoucherOptsIn() throws Exception {
        assertThat(MirroredDesign.isMirrorable(load())).isTrue();
    }

    @Test
    @DisplayName("an ordinary report is left exactly as it is")
    void otherReportsAreNotMirrored() throws Exception {
        // The analytical reports are left-to-right layouts that merely carry Arabic
        // text. Mirroring one would scramble it.
        try (InputStream is = MirroredDesignTest.class.getResourceAsStream("/reports/payment-auth.jrxml")) {
            assertThat(MirroredDesign.isMirrorable(JRXmlLoader.load(is))).isFalse();
        }
    }

    @Test
    @DisplayName("every element is reflected about the column's centre line")
    void everyElementIsReflected() throws Exception {
        JasperDesign original = load();
        int width = original.getColumnWidth();
        List<JRDesignElement> before = elements(original);

        List<JRDesignElement> after = elements(MirroredDesign.mirror(load()));

        assertThat(after).hasSameSizeAs(before);
        for (int i = 0; i < before.size(); i++) {
            JRDesignElement was = before.get(i);
            JRDesignElement now = after.get(i);
            assertThat(now.getX())
                    .as("element %d at x=%d w=%d", i, was.getX(), was.getWidth())
                    .isEqualTo(width - was.getX() - was.getWidth());
            assertThat(now.getWidth()).as("widths are not touched").isEqualTo(was.getWidth());
        }
    }

    @Test
    @DisplayName("the rows stay where they are: only the horizontal is mirrored")
    void theVerticalIsUntouched() throws Exception {
        List<JRDesignElement> before = elements(load());
        List<JRDesignElement> after = elements(MirroredDesign.mirror(load()));

        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i).getY()).as("element %d moved vertically", i)
                    .isEqualTo(before.get(i).getY());
            assertThat(after.get(i).getHeight()).isEqualTo(before.get(i).getHeight());
        }
    }

    @Test
    @DisplayName("no element is pushed off the page by the reflection")
    void nothingLeavesThePage() throws Exception {
        JasperDesign mirrored = MirroredDesign.mirror(load());
        int width = mirrored.getColumnWidth();

        for (JRDesignElement e : elements(mirrored)) {
            assertThat(e.getX()).as("element at x=%d is off the left edge", e.getX())
                    .isGreaterThanOrEqualTo(0);
            assertThat(e.getX() + e.getWidth())
                    .as("element ending at %d overruns the column width %d", e.getX() + e.getWidth(), width)
                    .isLessThanOrEqualTo(width);
        }
    }

    @Test
    @DisplayName("the two edges swap and the middle stays put")
    void alignmentFlipsAtTheEdgesOnly() throws Exception {
        List<JRDesignElement> before = elements(load());
        List<JRDesignElement> after = elements(MirroredDesign.mirror(load()));

        int flipped = 0;
        for (int i = 0; i < before.size(); i++) {
            if (!(before.get(i) instanceof JRDesignTextElement was)
                    || !(after.get(i) instanceof JRDesignTextElement now)) {
                continue;
            }
            HorizontalTextAlignEnum from = was.getHorizontalTextAlign();
            HorizontalTextAlignEnum to = now.getHorizontalTextAlign();
            if (from == HorizontalTextAlignEnum.RIGHT) {
                assertThat(to).isEqualTo(HorizontalTextAlignEnum.LEFT);
                flipped++;
            } else if (from == HorizontalTextAlignEnum.LEFT) {
                assertThat(to).isEqualTo(HorizontalTextAlignEnum.RIGHT);
                flipped++;
            } else {
                assertThat(to).as("centre is already symmetric").isEqualTo(from);
            }
        }
        assertThat(flipped).as("nothing was aligned to an edge, so nothing was tested").isPositive();
    }

    @Test
    @DisplayName("the text direction is flipped too, on the source")
    void runDirectionIsFlipped() throws Exception {
        String original = source();
        assertThat(original).as("the template is laid out RTL").contains("runDirection=\"RTL\"");

        String flipped = MirroredDesign.flipRunDirection(original);

        assertThat(flipped).doesNotContain("runDirection=\"RTL\"");
        assertThat(flipped).contains("runDirection=\"LTR\"");
        // Nothing else moved: the substitution is the same length as what it replaced.
        assertThat(flipped).hasSameSizeAs(original);
    }

    @Test
    @DisplayName("mirroring twice returns the design to where it started")
    void mirroringIsItsOwnInverse() throws Exception {
        List<JRDesignElement> once = elements(load());
        List<JRDesignElement> twice = elements(MirroredDesign.mirror(MirroredDesign.mirror(load())));

        for (int i = 0; i < once.size(); i++) {
            assertThat(twice.get(i).getX()).as("element %d", i).isEqualTo(once.get(i).getX());
        }
    }

    @Test
    @DisplayName("on the page, the amount column moves from the right edge to the left")
    void theAmountColumnCrossesThePage() throws Exception {
        // The design assertions above are about coordinates; this is about the printed
        // page, which is what a reader sees. In Arabic the amount is the rightmost
        // column of the expenditure table; mirrored, it must be the leftmost.
        int width = load().getColumnWidth();

        int rtlX = amountColumnX(load());
        int ltrX = amountColumnX(MirroredDesign.mirror(load()));

        assertThat(rtlX).as("the amount column starts in the right-hand third")
                .isGreaterThan(width * 2 / 3);
        assertThat(ltrX).as("mirrored, it starts at the left edge")
                .isLessThan(width / 3);
    }

    /** Where the amount column head sits — the element bound to LBL_AMOUNT_CUR. */
    private static int amountColumnX(JasperDesign design) {
        for (JRDesignElement e : elements(design)) {
            if (e instanceof net.sf.jasperreports.engine.design.JRDesignTextField field
                    && field.getExpression() != null
                    && field.getExpression().getText().contains("LBL_AMOUNT_CUR")) {
                return e.getX();
            }
        }
        throw new AssertionError("no element is bound to LBL_AMOUNT_CUR");
    }

    @Test
    @DisplayName("the mirrored design still compiles")
    void theMirroredDesignCompiles() throws Exception {
        // The point of the whole exercise: a design Jasper will not compile is worse
        // than a form that reads backwards.
        JasperDesign design = JRXmlLoader.load(new ByteArrayInputStream(
                MirroredDesign.flipRunDirection(source()).getBytes(StandardCharsets.UTF_8)));

        assertThat(net.sf.jasperreports.engine.JasperCompileManager
                .compileReport(MirroredDesign.mirror(design))).isNotNull();
    }
}
