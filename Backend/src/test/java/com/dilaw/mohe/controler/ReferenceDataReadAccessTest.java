package com.dilaw.mohe.controler;

import com.dilaw.mohe.scope.AsUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A finance officer can read the reference lists their own screens are built on.
 *
 * <p>The finance recipients form asks for four catalogues: family relationships
 * and received reasons from {@code /api/v1/ref-data}, the document types a file
 * can be filed under, and the wafar types that name what a student's money is
 * held against. All four were gated to system admin, so for the people who
 * actually fill the form in they answered 403 — and the page, which swallows the
 * error, rendered empty dropdowns. Replacing a free-text box with an empty
 * dropdown is worse than leaving it alone, so the gates come first.
 *
 * <p>The gate is {@link com.dilaw.mohe.config.MoheSecurityExpressions#LOOKUP_TABLE},
 * the expression the lookup tables already use: a GET is reference data, a write
 * still needs an admin. The second half of that is what the write cases below
 * pin down — opening a read must not open a write.
 *
 * <p>{@code addFilters = false} turns off the JWT chain, not method security,
 * which is AOP: the {@code @PreAuthorize} expressions under test still run.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ReferenceDataReadAccessTest {

    /** A finance officer, and nothing more — no SETTINGS, no WAFAR, no admin role. */
    private static final List<String> FINANCE_ONLY = List.of("FINANCE.VIEW", "FINANCE.EDIT");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private MvcResult as(List<String> authorities, Supplier<MvcResult> request) {
        return AsUser.callWithAuthorities(AsUser.SUPERADMIN, authorities, request);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/v1/ref-data/family-relationships",
            "/api/v1/ref-data/fin-received-reasons",
            "/api/v1/ref-data/travel-types",
            "/api/v1/ref-data/student-info-request-kinds",
            "/api/v1/document-types/active",
            "/api/v1/studyWafarType/",
            "/api/v1/lookup/study-countries",
            "/api/v1/lookup/study-governments",
            "/api/v1/lookup/study-finance-banks",
            "/api/v1/lookup/study-finance-bank-branches",
            "/api/v1/lookup/study-finance-dofs",
            "/api/v1/lookup/study-fin-received-statuses"
    })
    @DisplayName("a finance officer can read every reference list the form needs")
    void financeOfficer_readsReferenceLists(String path) {
        MvcResult result = as(FINANCE_ONLY, () -> {
            try {
                return mvc.perform(get(path)).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(result.getResponse().getStatus())
                .as("GET %s as a finance officer", path)
                .isEqualTo(200);
    }

    @Test
    @DisplayName("a finance officer can read the quarters their records name")
    void financeOfficer_readsTheQuarters() {
        // The books allowance and finance screens name a MOHE quarter on records
        // they own. The gate was MOHE_QUARTER, which none of the three finance
        // roles hold, so the dropdown answered 403 and the list printed raw ids.
        MvcResult result = as(FINANCE_ONLY, () -> {
            try {
                return mvc.perform(get("/api/v1/moheQuarter/")).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(result.getResponse().getStatus())
                .as("GET /api/v1/moheQuarter/ as a finance officer")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("reading the quarters did not make them writable")
    void financeOfficer_cannotWriteQuarters() {
        // Only the list GET was widened. A quarter carries BUDGET_AMOUNT and
        // PAID_AMOUNT, and creating one archives and advances every student.
        MvcResult result = as(FINANCE_ONLY, () -> {
            try {
                return mvc.perform(delete("/api/v1/moheQuarter/99999999")).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(result.getResponse().getStatus())
                .as("DELETE /api/v1/moheQuarter/99999999 as a finance officer")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("opening the document-types read did not open its writes")
    void documentTypes_stillCannotBeWritten() {
        MvcResult result = as(FINANCE_ONLY, () -> {
            try {
                return mvc.perform(post("/api/v1/document-types/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        // 403 from the method-security gate. Anything in the 2xx range would mean
        // the class-level annotation had been widened along with the GET.
        assertThat(result.getResponse().getStatus())
                .as("POST /api/v1/document-types/ as a finance officer")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("opening the wafar-type read did not open its writes")
    void wafarTypes_stillCannotBeWritten() {
        // Probed with DELETE rather than POST: the create takes a @Valid body, and
        // bean validation answers 400 before method security is ever reached, so a
        // POST would prove nothing about the gate. DELETE takes no body, and the id
        // is one that does not exist — if the gate had been widened the service
        // would say so with a 404 rather than removing anything real.
        MvcResult result = as(FINANCE_ONLY, () -> {
            try {
                return mvc.perform(delete("/api/v1/studyWafarType/99999999")).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(result.getResponse().getStatus())
                .as("DELETE /api/v1/studyWafarType/99999999 as a finance officer")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("the recipient list behind the lookup controller keeps a real gate")
    void receivedWhoms_isNotTreatedAsACatalogue() {
        // It returns names and passport numbers, not a catalogue, so opening the
        // lists around it must not have opened this one to everybody.
        MvcResult result = as(List.of("STUDENTS.VIEW"), () -> {
            try {
                return mvc.perform(get("/api/v1/lookup/received-whoms")).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(result.getResponse().getStatus())
                .as("GET /api/v1/lookup/received-whoms without any finance authority")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("a status carries the severity the database gives it")
    void receivedStatuses_carryTheirSeverity() throws Exception {
        MvcResult result = as(FINANCE_ONLY, () -> {
            try {
                return mvc.perform(get("/api/v1/lookup/study-fin-received-statuses")).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        // STUDY_FIN_RECEIVED_STATUS.UI_SEVERITY has held a value for all nine
        // statuses since May 2026, but the entity never mapped it, so the screen
        // guessed from the id and painted eight of the nine the same grey.
        JsonNode rows = json.readTree(result.getResponse().getContentAsString());
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row ->
                assertThat(row.hasNonNull("uiSeverity"))
                        .as("status %s carries a uiSeverity", row.path("id").asText())
                        .isTrue());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/v1/document-types/",
            "/api/v1/document-types/1"
    })
    @DisplayName("the rest of the document-types controller still needs an admin")
    void documentTypes_otherReadsStillNeedAdmin(String path) {
        MvcResult result = as(FINANCE_ONLY, () -> {
            try {
                return mvc.perform(get(path)).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        // Only /active was opened; the class gate still stands over everything else.
        assertThat(result.getResponse().getStatus())
                .as("GET %s as a finance officer", path)
                .isEqualTo(403);
    }
}
