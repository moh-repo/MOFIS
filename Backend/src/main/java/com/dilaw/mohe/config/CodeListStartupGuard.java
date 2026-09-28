package com.dilaw.mohe.config;

import com.dilaw.mohe.codes.CodeList;
import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.service.codelist.CodeListRegistry;
import com.dilaw.mohe.service.codelist.CodeListService;
import com.dilaw.mohe.service.codelist.CodeRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Refuses to start against a database whose code lists disagree with the code.
 *
 * <p>Step 1 asks {@code INFORMATION_SCHEMA} for every table and column the
 * registry needs — before any JPA read, so a database behind V072/V073 fails
 * with "apply V072" rather than a Hibernate stack trace on the first dropdown.
 * Step 2 compares each {@code @CodeList} class's constants with the list:
 * {@code EXACT} means the constants are exactly the {@code IS_SYSTEM} rows;
 * {@code SUBSET} means each constant is an active row. Any difference names
 * the list and the code and stops the boot — the test suite boots the same
 * context, so a stale test database fails the same way, in one line.
 */
@Component
public class CodeListStartupGuard {

    private static final Logger log = LoggerFactory.getLogger(CodeListStartupGuard.class);
    static final String CONSTANTS_PACKAGE = "com.dilaw.mohe.codes";
    private static final List<String> LU_COLUMNS =
            List.of("ID", "CODE", "NAME_AR", "NAME_EN", "SORT_ORDER", "IS_DELETED", "UI_SEVERITY", "ICON", "IS_SYSTEM");

    private final JdbcTemplate jdbc;
    private final CodeListService codeLists;

    public CodeListStartupGuard(JdbcTemplate jdbc, CodeListService codeLists) {
        this.jdbc = jdbc;
        this.codeLists = codeLists;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        // tenant-bypass: startup check of reference tables, before any request
        // exists to carry a scope; reads code lists only, never tenanted rows.
        TenantContext.runAsSystem(() -> {
            List<String> schemaProblems = checkSchema();
            if (!schemaProblems.isEmpty()) {
                throw new IllegalStateException("The database is behind the code lists the application needs. "
                        + "Apply Backend/docs/scripts V072, V073 and V080 with sqlcmd -I -f 65001 -b (see MIGRATION_RUNBOOK.md). Missing: "
                        + String.join("; ", schemaProblems));
            }
            List<String> codeProblems = checkConstants();
            if (!codeProblems.isEmpty()) {
                throw new IllegalStateException("Code lists and Java constants disagree — "
                        + "restore the row in the lookup table or change the constant: " + String.join("; ", codeProblems));
            }
            log.info("Code lists verified: {} lists, {} constant classes",
                    CodeListRegistry.names().size(), constantClasses().size());
        });
    }

    public List<String> checkSchema() {
        List<String> problems = new ArrayList<>();
        for (CodeListRegistry.LuList lu : CodeListRegistry.luLists().values()) {
            List<String> needed = new ArrayList<>(LU_COLUMNS);
            if (lu.extraColumn() != null) needed.add(lu.extraColumn());
            problems.addAll(missingColumns(lu.table(), needed));
        }
        for (CodeListRegistry.Adapter a : CodeListRegistry.adapters().values()) {
            problems.addAll(missingColumns(a.table(), a.requiredColumns()));
        }
        problems.addAll(missingColumns("MOHECORE.SYS_PARAMETER", List.of("PARAM_KEY", "PARAM_VALUE", "VALUE_TYPE")));
        problems.addAll(missingColumns("UMSCORE.UMS_Reports", List.of("ParentCode", "Icon", "PageCode", "PermissionCodes", "SortOrder")));
        // V080: the sign-in lockout ladder. Without it nobody could sign in, and
        // the failure would surface as a stack trace at the login form rather
        // than as the name of the script to run.
        problems.addAll(missingColumns("MOHECORE.AUTH_LOCKOUT_POLICY", List.of("STAGE_ORDER", "MAX_ATTEMPTS", "LOCK_MINUTES", "IS_ACTIVE")));
        problems.addAll(missingColumns("UMSCORE.Users", List.of("LockedUntil", "LockoutStage", "LastFailedLoginAt")));
        return problems;
    }

    private List<String> missingColumns(String qualifiedTable, List<String> columns) {
        String[] parts = qualifiedTable.split("\\.", 2);
        Set<String> present = new java.util.HashSet<>(jdbc.queryForList(
                "SELECT UPPER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                String.class, parts[0], parts[1]));
        if (present.isEmpty()) {
            return List.of(qualifiedTable + " (table)");
        }
        return columns.stream()
                .filter(c -> !present.contains(c.toUpperCase()))
                .map(c -> qualifiedTable + "." + c)
                .toList();
    }

    public List<String> checkConstants() {
        List<String> problems = new ArrayList<>();
        for (Class<?> cls : constantClasses()) {
            CodeList ann = cls.getAnnotation(CodeList.class);
            String list = ann.value();
            if (!CodeListRegistry.exists(list)) {
                problems.add(cls.getSimpleName() + " names unknown list '" + list + "'");
                continue;
            }
            Set<String> constants = constantsOf(cls);
            List<CodeRow> rows = codeLists.rows(list);
            Set<String> active = rows.stream().filter(CodeRow::active).map(CodeRow::code).collect(Collectors.toSet());
            for (String c : constants) {
                if (!active.contains(c)) {
                    problems.add(list + ": '" + c + "' (" + cls.getSimpleName() + ") is not an active row");
                }
            }
            if (ann.mode() == CodeList.Mode.EXACT) {
                Set<String> system = rows.stream().filter(CodeRow::system).map(CodeRow::code).collect(Collectors.toCollection(TreeSet::new));
                for (String s : system) {
                    if (!constants.contains(s)) {
                        problems.add(list + ": '" + s + "' is flagged IS_SYSTEM but no constant in "
                                + cls.getSimpleName() + " names it");
                    }
                }
            }
        }
        return problems;
    }

    static Set<String> constantsOf(Class<?> cls) {
        Set<String> out = new LinkedHashSet<>();
        if (cls.isEnum()) {
            for (Object e : cls.getEnumConstants()) {
                out.add(((Enum<?>) e).name());
            }
            return out;
        }
        for (Field f : cls.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) && Modifier.isFinal(m) && f.getType() == String.class) {
                try {
                    out.add((String) f.get(null));
                } catch (IllegalAccessException ignored) {
                    // public constants only
                }
            }
        }
        return out;
    }

    public static List<Class<?>> constantClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition bd) {
                        return true; // enums and final constant holders alike
                    }
                };
        scanner.addIncludeFilter(new AnnotationTypeFilter(CodeList.class));
        List<Class<?>> classes = new ArrayList<>();
        for (String pkg : List.of(CONSTANTS_PACKAGE, "com.dilaw.mohe.approval.model")) {
            for (var bd : scanner.findCandidateComponents(pkg)) {
                try {
                    classes.add(Class.forName(bd.getBeanClassName()));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        classes.sort(java.util.Comparator.comparing(Class::getName));
        return classes;
    }

    /** For the tests: constants per list, as the guard sees them. */
    Map<String, Set<String>> constantsByList() {
        return constantClasses().stream().collect(Collectors.toMap(
                c -> c.getAnnotation(CodeList.class).value(), CodeListStartupGuard::constantsOf, (a, b) -> {
                    Set<String> m = new LinkedHashSet<>(a);
                    m.addAll(b);
                    return m;
                }, java.util.LinkedHashMap::new));
    }
}
