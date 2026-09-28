package com.dilaw.mohe.controler;

import com.dilaw.mohe.scope.AsUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * A finance officer can name a student without being handed a student's record.
 *
 * <p>The finance recipients form has to say which student a payment was
 * collected for. Its dropdown was fed from {@code /api/v1/studentdata/}, which
 * is gated on {@code STUDENT_PII} — and the three roles that run the finance
 * screens (Finance Manager, FINANCE_OFFICER, WAFAR_OFFICER) hold no
 * {@code STUDENTS.*} permission at all, so it answered 403. (It was also the
 * wrong endpoint: it returns a bare array and ignores paging, while the caller
 * read {@code res.content}, so the dropdown was empty for admins too.)
 *
 * <p>{@code /options} exists for that dropdown. It answers with an id and a name
 * and nothing else, and these cases hold both halves of the bargain: finance can
 * read it, and the student records themselves are no easier to reach than before.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class StudentPickerAccessTest {

    private static final String OPTIONS = "/api/v1/studentdata/options";

    /** A finance officer, and nothing more — no STUDENTS.*, no admin role. */
    private static final List<String> FINANCE_ONLY = List.of("FINANCE.VIEW", "FINANCE.EDIT");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private MvcResult as(List<String> authorities, MockHttpServletRequestBuilder request) {
        Supplier<MvcResult> call = () -> {
            try {
                return mvc.perform(request).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        return AsUser.callWithAuthorities(AsUser.SUPERADMIN, authorities, call);
    }

    private MvcResult as(List<String> authorities, String url) {
        return as(authorities, get(url));
    }

    private JsonNode optionsAs(List<String> authorities, MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = as(authorities, request);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode optionsAs(List<String> authorities, String url) throws Exception {
        return optionsAs(authorities, get(url));
    }

    @Test
    @DisplayName("a finance officer can read the picker")
    void financeOfficer_readsThePicker() throws Exception {
        JsonNode rows = optionsAs(FINANCE_ONLY, OPTIONS + "?limit=5");

        assertThat(rows.isArray()).isTrue();
        assertThat(rows).isNotEmpty();
    }

    @Test
    @DisplayName("the picker answers with an id and a name, and nothing else")
    void picker_carriesNoPersonalData() throws Exception {
        JsonNode rows = optionsAs(FINANCE_ONLY, OPTIONS + "?limit=5");

        // The whole point of a separate endpoint: StudentDatumDto carries the
        // passport number and the date of birth, and this must not.
        rows.forEach(row -> {
            assertThat(row.fieldNames()).toIterable()
                    .containsExactlyInAnyOrder("studentId", "studentName");
        });
    }

    @Test
    @DisplayName("the picker is bounded however large a limit is asked for")
    void picker_isBounded() throws Exception {
        JsonNode rows = optionsAs(FINANCE_ONLY, OPTIONS + "?limit=100000");

        // A dropdown, not a bulk export: 941 students are in the table.
        assertThat(rows.size()).isLessThanOrEqualTo(200);
    }

    @Test
    @DisplayName("searching by name narrows the list")
    void picker_searchesByName() throws Exception {
        JsonNode all = optionsAs(FINANCE_ONLY, OPTIONS + "?limit=1");
        String name = all.get(0).path("studentName").asText();
        assertThat(name).isNotBlank();

        String term = name.substring(0, Math.min(4, name.length()));
        // Through .param() rather than the query string: the names are Arabic, and
        // MockMvc decodes a percent-encoded query as ISO-8859-1 unless told
        // otherwise — an artefact of the test harness, not of the endpoint.
        JsonNode found = optionsAs(FINANCE_ONLY,
                get(OPTIONS).param("limit", "50").param("search", term));

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(row ->
                assertThat(row.path("studentName").asText().toLowerCase())
                        .contains(term.toLowerCase()));
    }

    @Test
    @DisplayName("a digits-only term is read as the student's id")
    void picker_searchesById() throws Exception {
        JsonNode all = optionsAs(FINANCE_ONLY, OPTIONS + "?limit=1");
        int id = all.get(0).path("studentId").asInt();

        JsonNode found = optionsAs(FINANCE_ONLY, OPTIONS + "?search=" + id);

        // The options are labelled "id — name", so a user may well type either.
        assertThat(found).isNotEmpty();
        assertThat(found).anySatisfy(row -> assertThat(row.path("studentId").asInt()).isEqualTo(id));
    }

    @Test
    @DisplayName("ids= resolves exactly the students a list is showing")
    void picker_resolvesByIds() throws Exception {
        JsonNode all = optionsAs(FINANCE_ONLY, OPTIONS + "?limit=3");
        assertThat(all.size()).isGreaterThanOrEqualTo(2);
        int first = all.get(0).path("studentId").asInt();
        int second = all.get(1).path("studentId").asInt();

        JsonNode found = optionsAs(FINANCE_ONLY, OPTIONS + "?ids=" + first + "," + second);

        assertThat(found.size()).isEqualTo(2);
        assertThat(found).allSatisfy(row ->
                assertThat(row.path("studentId").asInt()).isIn(first, second));
    }

    @Test
    @DisplayName("the student records themselves are no easier to reach")
    void studentRecords_stillNeedTheStudentPermission() {
        // The picker opened one narrow endpoint. Everything that carries a
        // student's personal data still refuses a caller with no STUDENTS.*.
        assertThat(as(FINANCE_ONLY, "/api/v1/studentdata/").getResponse().getStatus())
                .as("the full student list").isEqualTo(403);
        assertThat(as(FINANCE_ONLY, "/api/v1/studentdata/studentall").getResponse().getStatus())
                .as("the paged student list").isEqualTo(403);
        assertThat(as(FINANCE_ONLY, "/api/v1/studentdata/1").getResponse().getStatus())
                .as("one student's record").isEqualTo(403);
    }

    @Test
    @DisplayName("a student officer can still use the picker too")
    void studentOfficer_readsThePicker() throws Exception {
        JsonNode rows = optionsAs(List.of("STUDENTS.VIEW"), OPTIONS + "?limit=5");
        assertThat(rows.isArray()).isTrue();
    }
}
