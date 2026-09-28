package com.dilaw.mohe.report.jasper;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.util.Collection;
import java.util.Set;

/**
 * The report's rows with the fields this caller may not see removed
 * (plan W5-03, security plan R-18 / O-03).
 *
 * <p>The mask is applied here rather than by copying the rows first. The rows
 * are {@code @Immutable} view entities that may be shared with a persistence
 * context or a second-level cache, so blanking them in place would leak into
 * whatever else holds that instance; and copying them needs a no-argument
 * constructor and a setter per property, which several row types do not have —
 * a row that could not be copied would then have to be printed unmasked or not
 * at all. Refusing the value as the template asks for it needs neither.
 *
 * <p>The field names Jasper asks for are the bean property names the
 * {@code <field name="...">} declarations carry, which is what the mask is
 * keyed by.
 */
final class MaskedBeanDataSource extends JRBeanCollectionDataSource {

    private final Set<String> hidden;

    private MaskedBeanDataSource(Collection<?> beans, Set<String> hidden) {
        super(beans);
        this.hidden = hidden;
    }

    /**
     * A plain bean source when nothing is hidden — the common case, and it keeps
     * the aggregate reports off this path entirely.
     */
    static JRBeanCollectionDataSource of(Collection<?> beans, Set<String> hidden) {
        return hidden == null || hidden.isEmpty()
                ? new JRBeanCollectionDataSource(beans)
                : new MaskedBeanDataSource(beans, hidden);
    }

    @Override
    public Object getFieldValue(JRField field) throws JRException {
        if (field != null && hidden.contains(field.getName())) {
            return null;
        }
        return super.getFieldValue(field);
    }
}
