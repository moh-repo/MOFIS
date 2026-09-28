package com.dilaw.mohe.report;

import com.dilaw.mohe.model.QueryBuilder;
import com.dilaw.mohe.service.QueryBuilderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schema catalogue behind the query builder page.
 *
 * <p>It used to come from a view that hardcoded three of its six columns and
 * excluded every {@code V_*} object. These checks pin the parts that were
 * literals, because a literal passes any test that only asks whether a value is
 * present.
 */
@Tag("integration")
@SpringBootTest
class QueryBuilderCatalogTest {

    @Autowired private QueryBuilderService service;

    @Test
    @DisplayName("the catalogue covers views as well as tables")
    void catalogue_includesViews() {
        List<QueryBuilder> catalogue = service.getAllQueryMetadata();

        assertThat(catalogue).as("MOHECORE is expected to have columns").isNotEmpty();
        // The old view filtered NOT LIKE 'V_%', so every reporting view was
        // missing from the catalogue that is meant to describe the schema.
        assertThat(catalogue)
                .as("the reporting views must be listed")
                .anySatisfy(c -> assertThat(c.getTableName()).startsWith("V_"));
        assertThat(catalogue)
                .as("and the base tables must still be there")
                .anySatisfy(c -> assertThat(c.getTableName()).isEqualTo("STUDENT_DATA"));
    }

    @Test
    @DisplayName("no credential column is listed")
    void catalogue_hidesCredentials() {
        // This catalogue is what will decide which columns a composed query may
        // select, so anything that must never be returned must never appear.
        assertThat(service.getAllQueryMetadata()).allSatisfy(c -> {
            String column = c.getColumnName().toUpperCase(Locale.ROOT);
            assertThat(column).doesNotContain("PASSWORD");
            assertThat(column).doesNotContain("TOKEN");
            assertThat(column).doesNotContain("SECRET");
        });
    }

    @Test
    @DisplayName("superseded copies are left out")
    void catalogue_hidesBackups() {
        assertThat(service.getAllTableNames())
                .doesNotContain("Users___old")
                .doesNotContain("STUDENT_DATA_BKUP");
    }

    @Test
    @DisplayName("descriptions come from the database, not an empty literal")
    void descriptions_areRead() {
        List<QueryBuilder> described = service.getAllQueryMetadata().stream()
                .filter(c -> c.getColumnDescription() != null && !c.getColumnDescription().isBlank())
                .toList();

        // column_description was N'' on every row while 318 columns carried an
        // MS_Description property, so the documentation existed and was dropped.
        assertThat(described)
                .as("MS_Description properties exist in this schema and must be surfaced")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the filterable flag distinguishes something")
    void filterableFlag_isDerived() {
        List<QueryBuilder> catalogue = service.getAllQueryMetadata();
        List<QueryBuilder> filterable = service.getFilterableColumns();

        // The flag was the constant 1, so "filterable columns" was the whole
        // schema. ntext cannot be used in a WHERE or ORDER BY without a cast.
        assertThat(catalogue).anySatisfy(c -> assertThat(c.getIsFilterable()).isZero());
        assertThat(filterable)
                .as("filterable columns must be fewer than all columns")
                .hasSizeLessThan(catalogue.size());
        assertThat(filterable).allSatisfy(c -> assertThat(c.getIsFilterable()).isEqualTo(1));
        assertThat(catalogue)
                .filteredOn(c -> c.getIsFilterable() == 0)
                .allSatisfy(c -> assertThat(c.getDataType()).isIn(
                        "text", "ntext", "image", "xml", "varbinary", "binary",
                        "geography", "geometry", "hierarchyid", "sql_variant"));

        assertThat(service.getSortableColumns()).hasSameSizeAs(filterable);
    }

    @Test
    @DisplayName("columns for one table are that table's columns")
    void columnsByTable_areScoped() {
        List<QueryBuilder> columns = service.getColumnsByTableName("STUDENT_DATA");

        assertThat(columns).isNotEmpty();
        assertThat(columns).allSatisfy(c -> assertThat(c.getTableName()).isEqualTo("STUDENT_DATA"));
        assertThat(columns)
                .as("a known column must be present")
                .anySatisfy(c -> assertThat(c.getColumnName()).isEqualTo("STUDENT_NAME"));
    }

    @Test
    @DisplayName("the table list matches the catalogue it summarises")
    void tableNames_agreeWithCatalogue() {
        List<String> names = service.getAllTableNames();
        List<String> fromCatalogue = service.getAllQueryMetadata().stream()
                .map(QueryBuilder::getTableName).distinct().toList();

        // Order is left to the database: SQL Server sorts by its collation, which
        // places STUDY_FINANCE_BANK before STUDY_FIN_RECEIVED_STATUS where Java
        // does the opposite. What matters is that the two describe the same set.
        assertThat(names).containsExactlyInAnyOrderElementsOf(fromCatalogue);
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("join suggestions offer real relationships, declared and inferred")
    void joinSuggestions_areUsable() {
        List<com.dilaw.mohe.dto.JoinSuggestionDto> suggestions =
                service.getJoinSuggestions("STUDENT_DATA");

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).allSatisfy(s -> {
            assertThat(s.getFromColumn()).isNotBlank();
            assertThat(s.getToTable()).isNotBlank();
            assertThat(s.getToColumn()).isNotBlank();
            assertThat(s.getKind()).isIn("FK", "NAME");
            // A suggestion must name STUDENT_DATA on one side or it is not a
            // suggestion about STUDENT_DATA.
            assertThat(List.of(s.getFromTable(), s.getToTable())).contains("STUDENT_DATA");
        });

        // Both halves of the union must contribute: the declared foreign keys,
        // and the name matches that are the only way to reach the 39 reporting
        // views, which have no foreign keys at all.
        assertThat(suggestions).extracting(com.dilaw.mohe.dto.JoinSuggestionDto::getKind)
                .contains("FK", "NAME");
        assertThat(suggestions)
                .as("the reporting views are the objects most worth joining")
                .anySatisfy(s -> assertThat(s.getToTable()).startsWith("V_"));

        // A bare Id is on most tables; matching them would suggest joining
        // everything to everything.
        assertThat(suggestions).noneSatisfy(s ->
                assertThat(s.getFromColumn()).isEqualToIgnoringCase("Id"));
    }

    @Test
    @DisplayName("an object with no relationships suggests none, rather than failing")
    void joinSuggestions_toleratesAnIsolatedObject() {
        assertThat(service.getJoinSuggestions("NO_SUCH_TABLE_AT_ALL")).isEmpty();
    }
}
