package com.dilaw.mohe.report.jasper;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRChild;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.JRElementGroup;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignFrame;
import net.sf.jasperreports.engine.design.JRDesignTextElement;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;

import java.util.List;

/**
 * Turns a right-to-left form design into its left-to-right mirror image.
 *
 * <p>Jasper's {@code x}, {@code textAlignment} and {@code runDirection} are static XML
 * attributes — none of them can be an expression — so a template laid out for Arabic
 * prints its boxes in Arabic order whatever language the text is in. The payment
 * authorisation voucher is such a template: نموذج رقم ١١ is a prescribed form, laid out
 * right to left, with the direction nailed to 55 of its 63 text elements.
 *
 * <p>Rather than keep a second English template in step by hand — two files, one
 * layout, drifting — the design is mirrored in memory before it is compiled: every
 * element's x becomes {@code width - x - elementWidth}, right alignment becomes left
 * and left becomes right, and the run direction is flipped. Centre stays centre, and
 * the vertical layout is untouched, so the form keeps its section order and its box
 * heights; it is the same document read from the other side.
 *
 * <p>Opt-in per template, by a report property, so the other forty-odd reports — which
 * are ordinary left-to-right layouts carrying Arabic text — are left alone.
 */
public final class MirroredDesign {

    /** A template declares {@code <property name="mohe.mirror.for.ltr" value="true"/>}. */
    public static final String MIRROR_PROPERTY = "mohe.mirror.for.ltr";

    private MirroredDesign() {
    }

    /** True when this design asks to be mirrored for a left-to-right rendering. */
    public static boolean isMirrorable(JasperDesign design) {
        return Boolean.parseBoolean(design.getPropertiesMap().getProperty(MIRROR_PROPERTY));
    }

    /**
     * Turns every {@code runDirection="RTL"} in the source into {@code "LTR"}.
     *
     * <p>Done on the XML rather than on the loaded design because JasperReports 6.20
     * has no setter for it: {@code runDirection} is a {@code <textElement>} attribute
     * the loader consumes, and neither {@code JRDesignTextElement} nor its subclasses
     * expose it, nor is there a report property equivalent. The attribute name appears
     * nowhere else in a JRXML, so the substitution cannot catch anything it should not.
     *
     * <p>Without this the glyphs would still be laid out from the right inside a
     * mirrored box, which is most visible where a line mixes Arabic and Latin.
     */
    public static String flipRunDirection(String jrxml) {
        return jrxml.replace("runDirection=\"RTL\"", "runDirection=\"LTR\"");
    }

    /**
     * Mirrors every band of the design in place.
     *
     * <p>Mirroring about the column width rather than the page width: elements are
     * positioned relative to their band, and a band is one column wide. Using the page
     * width would shift the whole form by the margins.
     */
    public static JasperDesign mirror(JasperDesign design) {
        int width = design.getColumnWidth();
        for (JRBand band : allBands(design)) {
            if (band != null) {
                mirrorChildren(band.getChildren(), width);
            }
        }
        return design;
    }

    private static List<JRBand> allBands(JasperDesign design) {
        List<JRBand> bands = new java.util.ArrayList<>();
        bands.add(design.getBackground());
        bands.add(design.getTitle());
        bands.add(design.getPageHeader());
        bands.add(design.getColumnHeader());
        bands.add(design.getColumnFooter());
        bands.add(design.getPageFooter());
        bands.add(design.getLastPageFooter());
        bands.add(design.getSummary());
        bands.add(design.getNoData());
        if (design.getDetailSection() != null && design.getDetailSection().getBands() != null) {
            bands.addAll(List.of(design.getDetailSection().getBands()));
        }
        for (var group : design.getGroupsList()) {
            var g = (net.sf.jasperreports.engine.design.JRDesignGroup) group;
            if (g.getGroupHeaderSection() != null && g.getGroupHeaderSection().getBands() != null) {
                bands.addAll(List.of(g.getGroupHeaderSection().getBands()));
            }
            if (g.getGroupFooterSection() != null && g.getGroupFooterSection().getBands() != null) {
                bands.addAll(List.of(g.getGroupFooterSection().getBands()));
            }
        }
        return bands;
    }

    /**
     * @param containerWidth the width the children are positioned within — the band's,
     *                       or a frame's own width for the elements nested inside it.
     */
    private static void mirrorChildren(List<JRChild> children, int containerWidth) {
        if (children == null) {
            return;
        }
        for (JRChild child : children) {
            if (child instanceof JRDesignElement element) {
                mirrorElement(element, containerWidth);
            } else if (child instanceof JRElementGroup group) {
                mirrorChildren(group.getChildren(), containerWidth);
            }
        }
    }

    private static void mirrorElement(JRDesignElement element, int containerWidth) {
        // The reflection about the container's centre line: an element that ended
        // flush with the right edge now starts flush with the left.
        element.setX(containerWidth - element.getX() - element.getWidth());

        if (element instanceof JRDesignTextElement text) {
            text.setHorizontalTextAlign(flip(text.getHorizontalTextAlign()));
            flipPadding(text);
            // The base direction of the text itself is not set here: see
            // flipRunDirection, which does it on the XML because JasperReports 6.20
            // exposes no setter for it on the design.
        }

        if (element instanceof JRDesignFrame frame) {
            // A frame's children are positioned inside it, so they mirror about the
            // frame's own width rather than the band's.
            mirrorChildren(frame.getChildren(), frame.getWidth());
        }
    }

    /** Centre and justified are already symmetric; only the two edges swap. */
    private static HorizontalTextAlignEnum flip(HorizontalTextAlignEnum align) {
        if (align == HorizontalTextAlignEnum.RIGHT) {
            return HorizontalTextAlignEnum.LEFT;
        }
        if (align == HorizontalTextAlignEnum.LEFT) {
            return HorizontalTextAlignEnum.RIGHT;
        }
        return align;
    }

    /**
     * Padding is part of the mirror too: a label indented from the right edge should
     * be indented from the left by the same amount, or it drifts against its rule.
     */
    private static void flipPadding(JRDesignTextElement text) {
        var box = text.getLineBox();
        if (box == null) {
            return;
        }
        Integer left = box.getOwnLeftPadding();
        Integer right = box.getOwnRightPadding();
        if (left != null || right != null) {
            box.setLeftPadding(right);
            box.setRightPadding(left);
        }
    }
}
