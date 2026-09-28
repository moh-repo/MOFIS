package com.dilaw.mohe.scope;

import com.dilaw.mohe.controler.DataImportController;
import com.dilaw.mohe.service.DataImportService;
import com.dilaw.mohe.ums.service.CountryScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The generic table loader, which sat outside the country model entirely.
 *
 * <p>{@code /api/v1/import} accepts {@code SETTINGS.VIEW}, which the attaché
 * administrators hold, and offers every base table in the database as a target —
 * {@code UMSCORE.Users} and {@code UMSCORE.UserPermissions} among them. Table and
 * column names cannot be bound as parameters, so they were concatenated into the
 * statement straight from the request body.
 *
 * <p>Two independent controls, asserted separately here because either one alone
 * would still leave the tool dangerous: membership of the ministry, and an
 * allow-list resolving every identifier against the catalog.
 */
@Tag("integration")
@SpringBootTest
class DataImportGuardTest {

    @Autowired private DataImportController controller;
    @Autowired private DataImportService service;

    /**
     * SETTINGS.VIEW is exactly what the attaché administrators hold and what the
     * class-level {@code @PreAuthorize} accepts. Granting it is the point: it puts
     * the permission gate out of the way so the ministry gate is what is measured.
     */
    /**
     * Data import is a write, and since the August 2026 revision {@code SYSTEM_ADMIN}
     * admits {@code SETTINGS.VIEW} on GETs only — the write side needs
     * {@code SETTINGS.EDIT}. These tests are about the country gate behind the
     * permission gate, so they hold the permission that gets them there.
     */
    private static final List<String> SETTINGS_EDIT = List.of("SETTINGS.EDIT");

    @Test
    @DisplayName("a country administrator cannot list import targets or import")
    void countryAdminIsRefused() {
        assertThat(AsUser.callWithAuthorities(AsUser.SUPERADMIN, SETTINGS_EDIT,
                () -> controller.getAvailableTables().getStatusCode()))
                .as("positive control: the ministry must still be able to use the tool")
                .isEqualTo(HttpStatus.OK);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.getAvailableTables()))
                .as("holds SETTINGS.VIEW, so only the ministry check can be refusing this")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        DataImportController.ImportRequest request = new DataImportController.ImportRequest();
        request.setTargetTable("MOHECORE.STUDENT_DATA");
        request.setSourceColumns(List.of("STUDENT_NAME"));
        request.setTargetColumns(List.of("STUDENT_NAME"));
        request.setRows(List.of(List.of("should never be written")));

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SA_ADMIN, SETTINGS_EDIT,
                () -> controller.executeImport(request)))
                .as("an attaché admin could otherwise INSERT into any table in the database")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * The scholarship importer is narrower than the generic loader — one fixed
     * table — but it writes into a country's folder on disk as well as the
     * database, and {@code countryId} arrives in the request. Without the
     * ministry gate an attaché administrator could file its workbook under any
     * other attaché.
     *
     * <p>Only the refusal is asserted. A FORBIDDEN {@code ResponseStatusException}
     * can come from nowhere else in these methods: every other failure is caught
     * and returned as a 400 or 500 body, so an assertion that specific cannot
     * pass by accident. The ministry positive control is
     * {@link #countryAdminIsRefused()} above.
     */
    @Test
    @DisplayName("a country administrator cannot import scholarship data for anyone")
    void countryAdminCannotImportScholarshipData() {
        MockMultipartFile workbook = new MockMultipartFile("file", "q3.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 0x50, 0x4B, 3, 4 });

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.previewScholarshipData(workbook)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SA_ADMIN, SETTINGS_EDIT,
                () -> controller.importScholarshipData(workbook, 1, false, false)))
                .as("Saudi's administrator must not be able to file a workbook under Egypt")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // The same call carrying the two destructive flags. The gate has to be
        // what refuses it, and it has to refuse before anything is deleted —
        // an attaché admin who could reach this would empty the table for
        // every country in the ministry.
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.SA_ADMIN, SETTINGS_EDIT,
                () -> controller.importScholarshipData(workbook, 1, true, true)))
                .as("deleteCountryData/deleteAllData must not be a way around the ministry gate")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.getScholarshipDataCounts(null)))
                .as("nor count what they cannot see")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.getScholarshipData(null, 0, 10, null, null, null, null)))
                .as("nor read every attaché's imported rows")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // The filter dropdowns are a read of the same table by another name:
        // the distinct student countries and degrees in it are exactly the
        // shape of what this gate exists to keep from a country admin.
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.getScholarshipFilters(null)))
                .as("nor enumerate them through the filter options")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        /*
         * The ETL. Its run endpoint replaces a country's staged rows, so the
         * gate has to stop a country admin before the delete, not after — and
         * its preview and data endpoints read the same ministry-wide table the
         * list endpoints above are guarded for.
         */
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.previewEtl(1)))
                .as("an attaché admin cannot preview another country's ETL")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.runEtl(1)))
                .as("and above all cannot run it, which replaces staged rows")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.etlCounts(null)))
                .as("nor count what is staged across every country")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.etlStagedData(null, 0, 10, null, null, null, false, null)))
                .as("nor read the staged rows")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.etlStagedFilters(null)))
                .as("nor enumerate them through the staged filter options")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // A staged record is addressed by a bare integer, so without a gate in
        // front of it the whole table could be walked one id at a time.
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.etlStagedRecord(1685)))
                .as("nor open one staged record by guessing its id")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Writing is the one that matters most here: an unguarded PUT would
        // let a country admin rewrite any student in the ministry.
        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.updateEtlStagedRecord(1685, java.util.Map.of("STUDENT_NAME", "x"))))
                .as("and above all cannot edit one")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.clearEtlStagedEdit(1685)))
                .as("nor hand somebody else's corrected row back to the ETL")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                () -> controller.etlEditLookups()))
                .as("nor read the lookup lists behind the edit form")
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Puts a request on the thread carrying the topbar selector's header. */
    private void withSelectedCountry(String countryId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (countryId != null) {
            request.addHeader(CountryScopeService.HEADER_SELECTED_COUNTRY, countryId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * Choosing a country in the topbar must not stop the ministry using the tool.
     *
     * <p>{@code getScope()} narrows to the selected country and sets
     * {@code ministryUser=false} so that the JPA country specs filter. Read as an
     * identity check that false made every endpoint on this screen answer 403 to
     * a superadmin who merely had Egypt selected — and the selector is on by
     * default once anyone has used it, so the whole page simply stopped working.
     */
    @Test
    @DisplayName("a ministry user with a country selected can still use the tool")
    void selectedCountryDoesNotRevokeMinistry() {
        withSelectedCountry(null);
        assertThat(AsUser.callWithAuthorities(AsUser.SUPERADMIN, SETTINGS_EDIT,
                () -> controller.getAvailableTables().getStatusCode()))
                .as("baseline: no country selected")
                .isEqualTo(HttpStatus.OK);

        withSelectedCountry("1");
        assertThat(AsUser.callWithAuthorities(AsUser.SUPERADMIN, SETTINGS_EDIT,
                () -> controller.getAvailableTables().getStatusCode()))
                .as("same user, same rights, one dropdown changed")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("selecting a country does not let an attaché admin in either")
    void selectedCountryDoesNotGrantMinistry() {
        // The other direction of the same fix: the gate now reads the user's own
        // scope, so it must not have become a way in for someone who is not
        // ministry, whatever they select.
        for (String selected : new String[] { null, "1", "1004" }) {
            withSelectedCountry(selected);
            assertThatThrownBy(() -> AsUser.callWithAuthorities(AsUser.JO_ADMIN, SETTINGS_EDIT,
                    () -> controller.getAvailableTables()))
                    .as("selected=%s", selected)
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    @DisplayName("a table outside the catalog is rejected, not concatenated")
    void unknownTableIsRejected() {
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN,
                () -> service.executeImport("MOHECORE.NoSuchTable", List.of("A"), List.of("A"), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown target table");

        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN,
                () -> service.executeImport("MOHECORE.STUDENT_DATA; DROP TABLE x --",
                        List.of("A"), List.of("A"), List.of())))
                .as("the injection shape: an identifier is never taken from the caller's string")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a column the target table does not have is rejected")
    void unknownColumnIsRejected() {
        assertThatThrownBy(() -> AsUser.call(AsUser.SUPERADMIN,
                () -> service.executeImport("MOHECORE.STUDENT_DATA",
                        List.of("nope"), List.of("NOT_A_REAL_COLUMN"), List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown column");
    }

    @Test
    @DisplayName("a real table and column still resolve, so the allow-list has not broken the tool")
    void validRequestStillResolves() {
        Map<String, Object> result = AsUser.call(AsUser.SUPERADMIN,
                () -> service.executeImport("MOHECORE.STUDENT_DATA",
                        List.of("STUDENT_NAME"), List.of("STUDENT_NAME"), List.of()));

        assertThat(result.get("totalRows"))
                .as("zero rows in, zero rows out — the point is that it validated rather than threw")
                .isEqualTo(0);
        assertThat(result.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("the table name is matched case-insensitively, as the catalog listing is")
    void tableMatchIsCaseInsensitive() {
        Map<String, Object> result = AsUser.call(AsUser.SUPERADMIN,
                () -> service.executeImport("mohecore.student_data",
                        List.of("student_name"), List.of("student_name"), List.of()));
        assertThat(result.get("success")).isEqualTo(true);
    }
}
