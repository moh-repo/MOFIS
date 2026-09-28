package com.dilaw.mohe.report;

import com.dilaw.mohe.model.StudentListDetailed;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards finding 4 of {@code docs/report-data-audit.md}: an English report must
 * print English data, not English headings over Arabic values.
 */
class BilingualRowsTest {

    private static StudentListDetailed row() {
        StudentListDetailed r = new StudentListDetailed();
        r.setStudentId(1);
        r.setStudentName("محمد أحمد");
        r.setStudentCountryName("مصر");
        r.setStudentCountryNameEn("Egypt");
        r.setStudentMajorName("هندسة");
        r.setStudentMajorNameEn("Engineering");
        return r;
    }

    @Test
    @DisplayName("an English render swaps each lookup name for its English sibling")
    void englishRenderUsesEnglishNames() {
        Collection<?> out = BilingualRows.forLanguage(List.of(row()), "en");

        StudentListDetailed only = (StudentListDetailed) out.iterator().next();
        assertThat(only.getStudentCountryName()).isEqualTo("Egypt");
        assertThat(only.getStudentMajorName()).isEqualTo("Engineering");
        // Carried through untouched, so the rest of the report still fills.
        assertThat(only.getStudentId()).isEqualTo(1);
    }

    @Test
    @DisplayName("the person's own name is never translated")
    void studentNameIsNotTranslated() {
        Collection<?> out = BilingualRows.forLanguage(List.of(row()), "en");

        StudentListDetailed only = (StudentListDetailed) out.iterator().next();
        assertThat(only.getStudentName()).isEqualTo("محمد أحمد");
    }

    @Test
    @DisplayName("an Arabic render returns the very rows it was given")
    void arabicRenderIsUntouched() {
        List<StudentListDetailed> rows = List.of(row());

        assertThat(BilingualRows.forLanguage(rows, "ar")).isSameAs(rows);
        assertThat(BilingualRows.forLanguage(rows, null)).isSameAs(rows);
    }

    @Test
    @DisplayName("the rows handed in are never mutated — they may be cached entities")
    void doesNotMutateTheSourceRows() {
        StudentListDetailed original = row();

        BilingualRows.forLanguage(List.of(original), "en");

        assertThat(original.getStudentCountryName()).isEqualTo("مصر");
        assertThat(original.getStudentMajorName()).isEqualTo("هندسة");
    }

    @Test
    @DisplayName("a missing English name leaves the Arabic rather than blanking the cell")
    void blankEnglishFallsBackToArabic() {
        StudentListDetailed r = row();
        r.setStudentMajorNameEn("   ");
        r.setStudentCountryNameEn(null);

        StudentListDetailed only =
                (StudentListDetailed) BilingualRows.forLanguage(List.of(r), "en").iterator().next();

        assertThat(only.getStudentMajorName()).isEqualTo("هندسة");
        assertThat(only.getStudentCountryName()).isEqualTo("مصر");
    }

    @Getter
    @Setter
    public static class NoTranslations {
        private String label = "as recorded";
    }

    @Test
    @DisplayName("a row class with no English siblings is passed straight through")
    void rowsWithoutTranslationsAreUnchanged() {
        List<NoTranslations> rows = List.of(new NoTranslations());

        Collection<?> out = BilingualRows.forLanguage(rows, "en");

        assertThat(out.iterator().next()).isSameAs(rows.get(0));
    }

    /** No no-argument constructor, so no copy can be made. */
    @Getter
    public static class Uncopyable {
        private final String someName;
        private final String someNameEn;

        public Uncopyable(String someName, String someNameEn) {
            this.someName = someName;
            this.someNameEn = someNameEn;
        }
    }

    @Test
    @DisplayName("a row that cannot be copied renders as it stands rather than failing")
    void uncopyableRowsDegradeRatherThanThrow() {
        Uncopyable row = new Uncopyable("عربي", "English");

        Collection<?> out = BilingualRows.forLanguage(List.of(row), "en");

        assertThat(out).hasSize(1);
        assertThat(((Uncopyable) out.iterator().next()).getSomeName()).isEqualTo("عربي");
    }

    @Test
    @DisplayName("empty and null inputs are handled without a copy pass")
    void emptyInputs() {
        assertThat(BilingualRows.forLanguage(null, "en")).isNull();
        assertThat(BilingualRows.forLanguage(List.of(), "en")).isEmpty();
    }
}
