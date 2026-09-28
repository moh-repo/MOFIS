package com.dilaw.mohe.report.jasper;

import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.JRDesignField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rows a template is filled from, with the hidden fields withheld
 * (plan W5-03).
 *
 * <p>The mask is refused at the moment the template asks for the value, rather
 * than by blanking the rows first: the rows are {@code @Immutable} entities
 * that may be shared with a persistence context, and several row types cannot
 * be copied at all.
 */
class MaskedBeanDataSourceTest {

    public static class Row {
        private String studentName = "Layla";
        private String studentPassportNumber = "A1234567";

        public String getStudentName() {
            return studentName;
        }

        public String getStudentPassportNumber() {
            return studentPassportNumber;
        }
    }

    private static JRDesignField field(String name) {
        JRDesignField f = new JRDesignField();
        f.setName(name);
        f.setValueClass(String.class);
        return f;
    }

    private static Object valueOf(JRBeanCollectionDataSource ds, String name) throws Exception {
        return ds.getFieldValue(field(name));
    }

    @Test
    @DisplayName("a hidden field is null and the rest of the row prints")
    void theHiddenFieldIsWithheld() throws Exception {
        JRBeanCollectionDataSource ds =
                MaskedBeanDataSource.of(List.of(new Row()), Set.of("studentPassportNumber"));
        assertThat(ds.next()).isTrue();

        assertThat(valueOf(ds, "studentPassportNumber")).isNull();
        assertThat(valueOf(ds, "studentName")).isEqualTo("Layla");
    }

    @Test
    @DisplayName("the row itself is never touched, so whatever else holds it is unharmed")
    void theRowIsNotMutated() throws Exception {
        Row row = new Row();
        JRBeanCollectionDataSource ds = MaskedBeanDataSource.of(List.of(row), Set.of("studentPassportNumber"));
        ds.next();
        valueOf(ds, "studentPassportNumber");

        assertThat(row.getStudentPassportNumber()).isEqualTo("A1234567");
    }

    @Test
    @DisplayName("nothing hidden is a plain bean source, so the aggregate reports never reach this path")
    void nothingHiddenIsNotWrapped() throws Exception {
        JRBeanCollectionDataSource plain = MaskedBeanDataSource.of(List.of(new Row()), Set.of());
        assertThat(plain).isNotInstanceOf(MaskedBeanDataSource.class);

        plain.next();
        assertThat(valueOf(plain, "studentPassportNumber")).isEqualTo("A1234567");
    }
}
