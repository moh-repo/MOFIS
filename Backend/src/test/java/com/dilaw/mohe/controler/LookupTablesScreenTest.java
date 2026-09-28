package com.dilaw.mohe.controler;

import com.dilaw.mohe.model.LuWafarSpendingStatus;
import com.dilaw.mohe.scope.AsUser;
import com.dilaw.mohe.service.LuWafarSpendingStatusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the Lookup Tables screen asks of {@code LookupTableController}.
 *
 * <p>The screen offers 33 lookups across six tabs, one lazy `p-table` for all of
 * them, and one Delete selected button. It sends {@code page}, {@code size},
 * {@code search}, {@code sortBy} and {@code sortDir} on every load, and it did
 * not matter what it sent:
 *
 * <ul>
 *   <li>the sixteen bilingual {@code LU_*} lists returned a bare {@code List},
 *       so Report Labels put all 301 of its rows on the page whose paginator
 *       said "1 to 10 of 301", and the search box narrowed nothing;</li>
 *   <li>seven more built their own {@code {content, totalElements}} out of
 *       {@code getAll()} and dropped the three parameters;</li>
 *   <li>{@code sortBy} and {@code sortDir} reached one endpoint out of 33 —
 *       every other one called a four-argument helper that passed null for
 *       both, so a click on a column header did nothing at all.</li>
 * </ul>
 *
 * <p>Walked by path rather than by calling 33 methods by name: the point is that
 * every lookup the screen can select behaves the same, and a lookup added to the
 * screen later should have to answer this too.
 */
@Tag("integration")
@SpringBootTest
class LookupTablesScreenTest {

    /** Every lookup id the six tabs can select (lookuptables.component.ts). */
    private static final List<String> SCREEN_TABLES = List.of(
            // Student
            "student-gender", "student-degree", "student-current-level", "student-agency-type",
            "student-ministry-type", "student-study-type", "student-study-status", "death-procedures",
            // Study & University
            "study-status", "study-degree-in", "student-university-type", "student-major",
            "study-received-whom-status",
            // Finance
            "wafar-spending-statuses", "wafar-reconciliation-statuses", "payment-auth-statuses",
            "payment-amount-sources", "books-allowance-pay-statuses", "loan-statuses",
            // Operations
            "change-request-statuses", "quarter-numbers", "mohe-main", "mohe-sub-main",
            // System
            "notification-channels", "notification-recipient-types", "notification-scopes",
            "student-portal-account-statuses", "report-dimensions", "trend-classes", "report-labels",
            // Location
            "country", "governorate", "municipality");

    /** The sixteen that also answer dropdowns, and so must still answer with a list. */
    private static final List<String> BILINGUAL = List.of(
            "wafar-spending-statuses", "wafar-reconciliation-statuses", "payment-auth-statuses",
            "payment-amount-sources", "change-request-statuses", "student-portal-account-statuses",
            "notification-channels", "notification-recipient-types", "notification-scopes",
            "quarter-numbers", "books-allowance-pay-statuses", "loan-statuses",
            "report-dimensions", "trend-classes", "report-labels", "death-procedures");

    @Autowired private LookupTableController controller;
    @Autowired private LuWafarSpendingStatusService wafarSpendingStatuses;
    // Actuator contributes a second one; the MVC mapping is the one that routes the screen.
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    // ── calling the screen's endpoints the way the screen does ────────────────

    /**
     * Invokes the GET handler for one lookup path with the arguments the screen
     * sends. Parameters this test says nothing about (a cascade's {@code govId},
     * a dimension's {@code groupCode}) go in as null, which is what the screen
     * sends for them too.
     */
    private Object get(String path, Integer page, Integer size, String search, String sortBy, String sortDir) {
        Method handler = handlerFor(path);
        Object[] args = new Object[handler.getParameterCount()];
        Parameter[] params = handler.getParameters();
        for (int i = 0; i < params.length; i++) {
            args[i] = switch (params[i].getName()) {
                case "page" -> page;
                case "size" -> size;
                case "search" -> search;
                case "sortBy" -> sortBy;
                case "sortDir" -> sortDir;
                default -> null;
            };
            if (args[i] == null && params[i].getType().isPrimitive()) {
                args[i] = params[i].getType() == int.class ? 0 : null;
            }
        }
        // R-12: no expression accepts ROLE_ADMIN any more; the screen's reads need SETTINGS.VIEW.
        Object body = AsUser.callWithAuthorities(AsUser.ADMIN, List.of("SETTINGS.VIEW", "SETTINGS.EDIT"), () -> {
            try {
                return ((ResponseEntity<?>) handler.invoke(controller, args)).getBody();
            } catch (ReflectiveOperationException e) {
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(path, e);
            }
        });
        assertThat(body).as("%s returned no body", path).isNotNull();
        return body;
    }

    private Method handlerFor(String path) {
        for (Method m : LookupTableController.class.getDeclaredMethods()) {
            GetMapping mapping = m.getAnnotation(GetMapping.class);
            if (mapping != null && List.of(mapping.value()).contains("/" + path)) {
                return m;
            }
        }
        throw new AssertionError("no GET handler for /" + path);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pageOf(String path, Integer page, Integer size, String search,
            String sortBy, String sortDir) {
        Object body = get(path, page, size, search, sortBy, sortDir);
        assertThat(body)
                .as("%s did not answer a paged request with a page; the screen's paginator "
                        + "and search box are reading a list it cannot narrow", path)
                .isInstanceOf(Map.class);
        Map<String, Object> map = (Map<String, Object>) body;
        assertThat(map).as("%s page has no content", path).containsKeys("content", "totalElements");
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> content(Map<String, Object> page) {
        return (List<Object>) page.get("content");
    }

    private static int total(Map<String, Object> page) {
        return ((Number) page.get("totalElements")).intValue();
    }

    /**
     * The row's key column, whatever the table calls it.
     *
     * <p>Needed twice over: to tell two rows apart without relying on
     * {@code toString}, which none of these entities overrides — so every fetch
     * would look like a different row and every assertion here would pass —
     * and to name the column to sort by, since {@code STUDENT_COUNTRY} calls its
     * key {@code studentCountryId} and {@code STUDENT_GENDER} calls its {@code id}.
     */
    private static java.lang.reflect.Field idField(Object row) {
        java.lang.reflect.Field fallback = null;
        for (Class<?> type = row.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (field.getName().equals("id")) {
                    return field;
                }
                if (fallback == null && field.getName().endsWith("Id")) {
                    fallback = field;
                }
            }
        }
        assertThat(fallback).as("no key column on %s", row.getClass().getSimpleName()).isNotNull();
        return fallback;
    }

    private static String identity(Object row) {
        java.lang.reflect.Field field = idField(row);
        field.setAccessible(true);
        try {
            return row.getClass().getSimpleName() + "#" + field.get(row);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── the tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every lookup the screen offers returns one row when it asks for one")
    void sizeIsHonoured() {
        List<String> ignored = new ArrayList<>();
        for (String path : SCREEN_TABLES) {
            Map<String, Object> first = pageOf(path, 0, 1, null, null, null);
            if (total(first) == 0) {
                continue;
            }
            if (content(first).size() != 1) {
                ignored.add(path + " sent " + content(first).size() + " rows for size=1");
            }
        }
        assertThat(ignored)
                .as("these lookups ignore the page size, so their paginator is decoration")
                .isEmpty();
    }

    @Test
    @DisplayName("the second page is a different row from the first")
    void pagingReallyPages() {
        List<String> stuck = new ArrayList<>();
        for (String path : SCREEN_TABLES) {
            Map<String, Object> first = pageOf(path, 0, 1, null, null, null);
            if (total(first) < 2) {
                continue;
            }
            Map<String, Object> second = pageOf(path, 1, 1, null, null, null);
            if (content(second).isEmpty() || identity(content(first).get(0)).equals(identity(content(second).get(0)))) {
                stuck.add(path);
            }
        }
        assertThat(stuck).as("page 2 of these lookups shows page 1").isEmpty();
    }

    @Test
    @DisplayName("a search that cannot match empties the list")
    void searchNarrows() {
        List<String> unfiltered = new ArrayList<>();
        for (String path : SCREEN_TABLES) {
            Map<String, Object> found = pageOf(path, 0, 10, "zzz-no-such-lookup-value-zzz", null, null);
            if (total(found) != 0) {
                unfiltered.add(path + " still reported " + total(found) + " rows");
            }
        }
        assertThat(unfiltered).as("the search box does nothing on these lookups").isEmpty();
    }

    @Test
    @DisplayName("descending is not ascending")
    void sortDirectionIsHonoured() {
        List<String> unsorted = new ArrayList<>();
        for (String path : SCREEN_TABLES) {
            Map<String, Object> unsortedFirst = pageOf(path, 0, 1, null, null, null);
            if (total(unsortedFirst) < 2) {
                continue;
            }
            String key = idField(content(unsortedFirst).get(0)).getName();
            Map<String, Object> ascending = pageOf(path, 0, 1, null, key, "asc");
            Map<String, Object> descending = pageOf(path, 0, 1, null, key, "desc");
            if (identity(content(ascending).get(0)).equals(identity(content(descending).get(0)))) {
                unsorted.add(path);
            }
        }
        assertThat(unsorted)
                .as("a click on a column header changes nothing on these lookups")
                .isEmpty();
    }

    @Test
    @DisplayName("a dropdown still gets a plain list from the bilingual lookups")
    void theListContractIsUnchanged() {
        for (String path : BILINGUAL) {
            assertThat(get(path, null, null, null, null, null))
                    .as("%s no longer answers a caller that asks for no page; "
                            + "LookupService fetches a dozen of these for dropdowns", path)
                    .isInstanceOf(List.class);
        }
    }

    @Test
    @DisplayName("delete selected has a route on every bilingual lookup")
    void batchDeleteIsRouted() {
        Set<String> declared = new java.util.HashSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (!method.getBeanType().equals(LookupTableController.class)) {
                return;
            }
            if (!methodsOf(info).contains(RequestMethod.DELETE)) {
                return;
            }
            declared.addAll(patternsOf(info));
        });
        List<String> missing = BILINGUAL.stream()
                .map(p -> "/api/lookup-tables/" + p + "/batch")
                .filter(p -> !declared.contains(p))
                .toList();
        assertThat(missing)
                .as("the screen calls DELETE .../batch; without a route /{id} matches with "
                        + "an id of \"batch\" and the button answers 400")
                .isEmpty();
    }

    private static Set<RequestMethod> methodsOf(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods();
    }

    private static Set<String> patternsOf(RequestMappingInfo info) {
        return info.getPathPatternsCondition() == null
                ? info.getPatternValues()
                : info.getPathPatternsCondition().getPatternValues();
    }

    // ── the codes the application depends on ──────────────────────────────────

    /**
     * A row a Java constant names cannot be deleted or renamed from this screen
     * either.
     *
     * <p>{@code CodeListRegistry} ties each bilingual lookup to a class of
     * constants and {@code CodeListStartupGuard} refuses to start the application
     * when one of their codes has gone. The Code Lists screen has refused these
     * two writes since the codes were introduced; this screen reaches the same
     * tables and refused nothing, and for ten of the sixteen its delete was a hard
     * one. Every row of {@code LU_WAFAR_SPENDING_STATUS} is a system row.
     */
    @Test
    @DisplayName("a system code cannot be deleted from the lookup tables screen")
    void systemCodeCannotBeDeleted() {
        LuWafarSpendingStatus system = aSystemWafarSpendingStatus();
        assertThatThrownBy(() -> wafarSpendingStatuses.delete(system.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(wafarSpendingStatuses.getById(system.getId()))
                .as("the row was refused and left alone").isNotNull();
    }

    @Test
    @DisplayName("a system code cannot be renamed from the lookup tables screen")
    void systemCodeCannotBeRenamed() {
        LuWafarSpendingStatus system = aSystemWafarSpendingStatus();
        LuWafarSpendingStatus edit = new LuWafarSpendingStatus();
        edit.setCode(system.getCode() + "_RENAMED");
        edit.setNameAr(system.getNameAr());
        edit.setNameEn(system.getNameEn());
        edit.setSortOrder(system.getSortOrder());
        assertThatThrownBy(() -> wafarSpendingStatuses.update(system.getId(), edit))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(wafarSpendingStatuses.getById(system.getId()).getCode())
                .as("the code was refused and left alone").isEqualTo(system.getCode());
    }

    private LuWafarSpendingStatus aSystemWafarSpendingStatus() {
        Optional<LuWafarSpendingStatus> system = wafarSpendingStatuses.getAll().stream()
                .filter(row -> Boolean.TRUE.equals(row.getIsSystem()))
                .findFirst();
        assertThat(system)
                .as("no system row in LU_WAFAR_SPENDING_STATUS; the seed has changed and this "
                        + "test would otherwise pass without asserting anything")
                .isPresent();
        return system.get();
    }
}
