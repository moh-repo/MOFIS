package com.dilaw.mohe.controler;

import com.dilaw.mohe.scope.AsUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Id;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Add, view, edit and delete on every list the Lookup Tables screen offers.
 *
 * <p>The owner asked for all four operations to work on every Administration
 * page. This is the standing proof for the largest of them: the screen offers 33
 * lists, and until 30 August 2026 <b>Add was impossible on 21 of them</b> —
 * {@code @GeneratedValue(IDENTITY)} mapped onto columns that are not identity
 * columns, so the insert asked for a generated key that never came. Nothing
 * caught it because no test had ever created a row in any of them.
 *
 * <p>Driven over HTTP paths rather than through the service beans, because the
 * paths are the contract the screen actually uses ({@code lookuptable.service.ts}
 * builds {@code /api/lookup-tables/{tableType}} from the same ids), and because
 * the security expression on this controller is verb-aware
 * ({@code isGetRequest() or SYSTEM_ADMIN}) — a direct bean call cannot exercise
 * that. The filter chain is off, so this proves the controller, service and
 * database path and <b>not</b> authentication; JWT and CAPTCHA are covered
 * elsewhere, and the authority checks still run because method security is AOP.
 *
 * <p>The ids and the bodies are discovered, not listed: each case reads the
 * controller's own {@code @PostMapping} to find the entity type, then fills its
 * string properties with a marker. Parents that a row cannot be created without
 * are borrowed from rows that already exist. A list added to the controller is
 * therefore swept the day it appears.
 *
 * <p>Every row is deleted at the end of its own case, and each is named with a
 * {@code ZT-} marker so anything a crash leaves behind is recognisable.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LookupTablesCrudSweepTest {

    private static final String BASE = "/api/lookup-tables";

    /** What the writes need; a GET needs only SETTINGS.VIEW. */
    private static final List<String> ADMIN = List.of("SETTINGS.EDIT", "SETTINGS.VIEW");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * A row cannot exist without these, so they are borrowed from rows that do:
     * property name on the entity, and where to read a real value from.
     */
    private static final Map<String, String[]> PARENTS = Map.of(
            "moheMainId", new String[] { "/mohe-main", "id" },
            "studentCountryId", new String[] { "/country", "studentCountryId" },
            "studentGovId", new String[] { "/governorate", "studentGovId" },
            "studentAgencyTypeId", new String[] { "/student-agency-type", "id" });

    private final Map<String, JsonNode> borrowed = new LinkedHashMap<>();

    /** One case per list the controller declares a create for. */
    List<String> lookupIds() {
        List<String> ids = new ArrayList<>();
        for (Method method : LookupTableController.class.getDeclaredMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            if (post == null || post.value().length == 0) continue;
            String path = post.value()[0].replaceFirst("^/", "");
            if (path.isEmpty() || path.contains("/")) continue;   // /batch and friends
            if (entityOf(method) != null) ids.add(path);
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /** The entity a create binds, or null when the handler takes something else. */
    private static Class<?> entityOf(Method create) {
        for (Class<?> type : create.getParameterTypes()) {
            if (type.getName().startsWith("com.dilaw.mohe.model.")) return type;
        }
        return null;
    }

    private Method createMethodFor(String lookupId) {
        for (Method method : LookupTableController.class.getDeclaredMethods()) {
            PostMapping post = method.getAnnotation(PostMapping.class);
            if (post != null && post.value().length > 0
                    && post.value()[0].replaceFirst("^/", "").equals(lookupId)) {
                return method;
            }
        }
        throw new IllegalStateException("no create handler for " + lookupId);
    }

    /** {@code SCHEMA.TABLE} for the entity, for the cleanup below. */
    private static String tableOf(Class<?> entity) {
        jakarta.persistence.Table table = entity.getAnnotation(jakarta.persistence.Table.class);
        return table.schema() + "." + table.name();
    }

    /** The id's column name, which differs from its property name. */
    private static String idColumnOf(Class<?> entity) {
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getAnnotation(Id.class) == null) continue;
                jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
                return column != null && !column.name().isEmpty() ? column.name() : field.getName();
            }
        }
        throw new IllegalStateException("no @Id on " + entity.getSimpleName());
    }

    /** The JSON property the entity's id is called; it is not always "id". */
    private static String idPropertyOf(Class<?> entity) {
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getAnnotation(Id.class) != null) return field.getName();
            }
        }
        throw new IllegalStateException("no @Id on " + entity.getSimpleName());
    }

    /**
     * A body the entity will accept: every string it exposes set to a marker, and
     * the parents it cannot do without borrowed from existing rows.
     */
    private ObjectNode bodyFor(Class<?> entity, String marker) throws Exception {
        ObjectNode body = json.createObjectNode();
        String idProperty = idPropertyOf(entity);

        for (Method setter : entity.getMethods()) {
            if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) continue;
            String property = Character.toLowerCase(setter.getName().charAt(3))
                    + setter.getName().substring(4);
            if (property.equals(idProperty)) continue;

            Class<?> type = setter.getParameterTypes()[0];
            if (type == String.class) {
                // Fit the column. A code marker used to be a flat eight characters,
                // which overflowed STUDY_CURRENCY_IN.ISO_CODE char(3) and came back
                // as a 409 duplicate; the width is now read from the entity's own
                // @Column(length), so a narrower column added later cannot repeat it.
                body.put(property, markerFor(entity, property, marker));
            } else if (type == Boolean.class || type == boolean.class) {
                if (property.equals("isActive")) body.put(property, true);
            }
        }

        // After the generic fill, so a parent id is never left holding a marker.
        for (Map.Entry<String, String[]> parent : PARENTS.entrySet()) {
            if (!hasProperty(entity, parent.getKey())) continue;
            JsonNode value = borrow(parent.getValue()[0], parent.getValue()[1]);
            if (value != null) body.set(parent.getKey(), value);
        }
        return body;
    }

    /**
     * A value that fits: the plain marker where the column is wide, and a short
     * upper-case form trimmed to {@code @Column(length)} where it is not. Code
     * columns take the short form whatever their width, because a code is read
     * back as one and a hyphenated marker is not one.
     */
    private static String markerFor(Class<?> entity, String property, String marker) {
        Integer length = declaredLengthOf(entity, property);
        boolean isCode = property.toLowerCase(Locale.ROOT).contains("code");
        String value = isCode ? marker.replace("-", "").toUpperCase(Locale.ROOT) : marker;
        if (length != null && length < value.length()) return value.substring(0, length);
        return value;
    }

    /** The length the entity declares for a property's column, or null when it declares none. */
    private static Integer declaredLengthOf(Class<?> entity, String property) {
        for (Class<?> c = entity; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!field.getName().equals(property)) continue;
                jakarta.persistence.Column column = field.getAnnotation(jakarta.persistence.Column.class);
                // 255 is the annotation's default, i.e. "unspecified", not a real width.
                return column == null || column.length() == 255 ? null : column.length();
            }
        }
        return null;
    }

    private static boolean hasProperty(Class<?> entity, String property) {
        String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (Method method : entity.getMethods()) {
            if (method.getName().equals(setter) && method.getParameterCount() == 1) return true;
        }
        return false;
    }

    /** The first id on an existing list, so a child can point at something real. */
    private JsonNode borrow(String listPath, String idProperty) throws Exception {
        if (borrowed.containsKey(listPath)) return borrowed.get(listPath);
        JsonNode rows = rowsOf(listPath);
        JsonNode value = rows.isEmpty() ? null : rows.get(0).get(idProperty);
        borrowed.put(listPath, value);
        return value;
    }

    /** The rows of a list, whether the endpoint pages them or not. */
    private JsonNode rowsOf(String listPath) throws Exception {
        MvcResult result = AsUser.callWithAuthorities(AsUser.SUPERADMIN, ADMIN, () -> {
            try {
                return mvc.perform(get(BASE + listPath).param("size", "2000")).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(result.getResponse().getStatus())
                .as("listing %s", listPath).isEqualTo(200);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.has("content") ? body.get("content") : body;
    }

    /** Whether the list the screen renders holds this id. */
    private boolean listContains(String lookupId, String idProperty, String id) throws Exception {
        for (JsonNode row : rowsOf("/" + lookupId)) {
            JsonNode rowId = row.get(idProperty);
            if (rowId != null && rowId.asText().equals(id)) return true;
        }
        return false;
    }

    private MvcResult call(java.util.function.Supplier<MvcResult> request) {
        return AsUser.callWithAuthorities(AsUser.SUPERADMIN, ADMIN, request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lookupIds")
    @DisplayName("create, read, update and delete each lookup the screen offers")
    void everyLookupSupportsAllFourOperations(String lookupId) throws Exception {
        Class<?> entity = entityOf(createMethodFor(lookupId));
        String idProperty = idPropertyOf(entity);
        String marker = "ZT-" + UUID.randomUUID().toString().substring(0, 8);
        String path = BASE + "/" + lookupId;

        // Every list the screen writes to must offer all four verbs.
        assertThat(declaresMapping(lookupId, GetMapping.class)).as("%s has a list route", lookupId).isTrue();
        assertThat(declaresMapping(lookupId + "/{id}", PutMapping.class)).as("%s has an update route", lookupId).isTrue();
        assertThat(declaresMapping(lookupId + "/{id}", DeleteMapping.class)).as("%s has a delete route", lookupId).isTrue();

        String createBody = json.writeValueAsString(bodyFor(entity, marker));
        MvcResult created = call(() -> {
            try {
                return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(createBody)).andReturn();
            } catch (Exception e) { throw new IllegalStateException(e); }
        });
        assertThat(created.getResponse().getStatus())
                .as("ADD on %s: %s", lookupId, created.getResponse().getContentAsString())
                .isEqualTo(200);

        JsonNode createdRow = json.readTree(created.getResponse().getContentAsString());
        JsonNode idNode = createdRow.get(idProperty);
        assertThat(idNode).as("ADD on %s returned no %s", lookupId, idProperty).isNotNull();
        String id = idNode.asText();

        // The sixteen bilingual lists have no by-id route — the screen's view
        // dialog shows the row it already holds, so nothing calls one. Where a
        // route exists it must answer; where it does not, the row must at least be
        // findable in the list, which is where the screen gets it from.
        boolean hasByIdRoute = declaresMapping(lookupId + "/{id}", GetMapping.class);

        try {
            // VIEW
            if (hasByIdRoute) {
                MvcResult read = call(() -> {
                    try { return mvc.perform(get(path + "/" + id)).andReturn(); }
                    catch (Exception e) { throw new IllegalStateException(e); }
                });
                assertThat(read.getResponse().getStatus()).as("VIEW on %s", lookupId).isEqualTo(200);
            } else {
                assertThat(listContains(lookupId, idProperty, id))
                        .as("VIEW on %s: the created row is not in the list either", lookupId)
                        .isTrue();
            }

            // EDIT: the same body with new names, so the change is visible.
            String renamed = "ZT-" + UUID.randomUUID().toString().substring(0, 8);
            ObjectNode editBody = (ObjectNode) json.readTree(created.getResponse().getContentAsString());
            List<String> renamedProperties = new ArrayList<>();
            editBody.fieldNames().forEachRemaining(name -> {
                if (editBody.get(name).isTextual() && editBody.get(name).asText().startsWith("ZT-")
                        && !name.toLowerCase(Locale.ROOT).contains("code")) {
                    renamedProperties.add(name);
                }
            });
            // Through markerFor, so a narrow column is not overflowed on the way back.
            renamedProperties.forEach(name -> editBody.put(name, markerFor(entity, name, renamed)));
            assertThat(renamedProperties).as("%s has a name to edit", lookupId).isNotEmpty();

            String editJson = json.writeValueAsString(editBody);
            MvcResult edited = call(() -> {
                try {
                    return mvc.perform(put(path + "/" + id)
                            .contentType(MediaType.APPLICATION_JSON).content(editJson)).andReturn();
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            assertThat(edited.getResponse().getStatus())
                    .as("EDIT on %s: %s", lookupId, edited.getResponse().getContentAsString())
                    .isEqualTo(200);

            String afterEdit = hasByIdRoute
                    ? call(() -> {
                        try { return mvc.perform(get(path + "/" + id)).andReturn(); }
                        catch (Exception e) { throw new IllegalStateException(e); }
                    }).getResponse().getContentAsString()
                    : rowsOf("/" + lookupId).toString();
            assertThat(afterEdit)
                    .as("EDIT on %s did not stick", lookupId)
                    .contains(renamed);
        } finally {
            MvcResult deleted = call(() -> {
                try { return mvc.perform(delete(path + "/" + id)).andReturn(); }
                catch (Exception e) { throw new IllegalStateException(e); }
            });
            assertThat(deleted.getResponse().getStatus())
                    .as("DELETE on %s: %s", lookupId, deleted.getResponse().getContentAsString())
                    .isEqualTo(200);

            // Gone from the list the screen renders — true whether the delete is
            // a real one or the soft delete the bilingual lists use.
            JsonNode remaining = rowsOf("/" + lookupId);
            List<String> stillThere = new ArrayList<>();
            remaining.forEach(row -> {
                JsonNode rowId = row.get(idProperty);
                if (rowId != null && rowId.asText().equals(id)) stillThere.add(id);
            });
            assertThat(stillThere)
                    .as("DELETE on %s left the row in the list", lookupId)
                    .isEmpty();

            // The bilingual lists delete softly, which is right for the product
            // and would leave one invisible row per run behind here. The row this
            // case created is removed for real, by its own id.
            jdbc.update("DELETE FROM " + tableOf(entity) + " WHERE " + idColumnOf(entity) + " = ?", id);
        }
    }

    private static boolean declaresMapping(String path, Class<? extends java.lang.annotation.Annotation> mapping) {
        for (Method method : LookupTableController.class.getDeclaredMethods()) {
            java.lang.annotation.Annotation annotation = method.getAnnotation(mapping);
            if (annotation == null) continue;
            String[] value = switch (annotation) {
                case GetMapping get -> get.value();
                case PutMapping put -> put.value();
                case DeleteMapping delete -> delete.value();
                default -> new String[0];
            };
            for (String candidate : value) {
                if (candidate.replaceFirst("^/", "").equals(path)) return true;
            }
        }
        return false;
    }
}
