package com.dilaw.mohe.security.field;

import com.dilaw.mohe.config.MaskedAs;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.security.field.FieldAccessService.Rule;
import com.dilaw.mohe.ums.service.UserAuthorityCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Field masking for reports and exports (plan W5-03, security plan R-18 / O-03).
 *
 * <h2>Why a second mask</h2>
 *
 * <p>{@code SensitivePropertyWriter} masks a field on its way through Jackson,
 * which covers every JSON response and nothing else. A report never goes
 * through Jackson: Jasper reads the bean through its own reflection and
 * {@code ExcelExportService} reads the declared field directly. Every export
 * therefore printed the passport number, the national id and the bank account
 * to anyone who could reach the report — the very fields the screen had just
 * hidden from them. O-03 said as much when it was written: <i>"report templates
 * that print these fields must be checked"</i>.
 *
 * <h2>Where a row's rule comes from</h2>
 *
 * <p>Report rows are projections and view entities; repeating the annotations on
 * each of them would mean the rule for a passport number lived in a dozen
 * places and drifted in nine. A row property is resolved instead, in this order:
 *
 * <ol>
 *   <li>its own {@code @FieldPermission} or {@code @SensitiveField};</li>
 *   <li>{@link MaskedAs}, naming the entity field whose rule applies — for a row
 *       that renames the datum ({@code passportNumber} for
 *       {@code studentPassportNumber});</li>
 *   <li>a field of the same name on a {@link #RULE_OWNERS rule-owning entity}.
 *       The report views keep the entity's column names, so this resolves most
 *       rows with no annotation at all.</li>
 * </ol>
 *
 * <p>A property that matches none of the three is not sensitive and is printed.
 *
 * <h2>Masked how</h2>
 *
 * <p>The value is replaced by {@code null}, which is what O-03 specifies and
 * what the JSON writer already does. It is also the only answer that suits
 * every column type: a template declares {@code studentBirthdate} a date, and a
 * marker string in its place would fail the fill rather than hide a birthday.
 *
 * <h2>Which thread asks</h2>
 *
 * <p>{@link #viewer()} reads the {@code SecurityContext} and so must be called
 * on the request thread — {@code JasperReportService} renders on a pool thread
 * where the context is empty, and an empty context holds no codes, which would
 * quietly mask every report for everyone. {@link #viewerFor(Long)} serves the
 * scheduled exports, which have no request thread at all and are masked by the
 * permissions of the user whose schedule it is, exactly as their country scope
 * already is.
 */
@Service
public class ReportFieldMask {

    /**
     * The entities whose annotations are authoritative. A row property is
     * matched against these by name.
     *
     * <p>Keep this list short: every name on a rule owner masks that name on
     * every report row. The two here own the student PII and the bank account,
     * which is the whole of O-03.
     */
    private static final List<Class<?>> RULE_OWNERS = List.of(StudentDatum.class, StudentFinance.class);

    /** Row class → property name → the rule that governs it. Reflection once per class. */
    private static final Map<Class<?>, Map<String, Rule>> PLANS = new ConcurrentHashMap<>();

    /** Lazily merged rules of {@link #RULE_OWNERS}, by property name. */
    private static volatile Map<String, Rule> ownerRules;

    /** The same rules by {@code TABLE.COLUMN}, for rows that arrive as maps. */
    private static volatile Map<String, Rule> columnRules;

    private final FieldAccessService fieldAccess;

    /** Absent outside Spring; then a viewer can only be built from a context or by hand. */
    @Autowired(required = false)
    private UserAuthorityCache authorityCache;

    public ReportFieldMask(FieldAccessService fieldAccess) {
        this.fieldAccess = fieldAccess;
    }

    // ── Who is looking ───────────────────────────────────────────────────────

    /** The permission codes a render is masked against. */
    public record Viewer(Set<String> held) {

        public Viewer {
            held = held == null ? Set.of() : Set.copyOf(held);
        }
    }

    /**
     * The signed-in caller. Call this on the request thread: a pool thread has
     * no security context, and would mask everything.
     */
    public Viewer viewer() {
        return new Viewer(FieldAccessService.authorities());
    }

    /**
     * The user a scheduled export belongs to, read without a security context.
     *
     * <p>An account that cannot be resolved holds nothing, so its export is
     * masked rather than widened.
     */
    public Viewer viewerFor(Long userId) {
        if (userId == null || authorityCache == null) return new Viewer(Set.of());
        UserAuthorityCache.Snapshot snapshot = authorityCache.snapshot(userId);
        if (snapshot == null || snapshot.deleted()) return new Viewer(Set.of());
        return new Viewer(Set.copyOf(snapshot.authorities()));
    }

    // ── What to hide ─────────────────────────────────────────────────────────

    /** The properties of {@code rowType} this viewer may not see; empty for most reports. */
    public Set<String> hiddenOn(Class<?> rowType, Viewer viewer) {
        Map<String, Rule> rules = rulesFor(rowType);
        if (rules.isEmpty()) return Set.of();
        Set<String> defined = fieldAccess.definedFieldCodes();
        Set<String> hidden = new LinkedHashSet<>();
        for (Map.Entry<String, Rule> e : rules.entrySet()) {
            if (!FieldAccessService.mayView(e.getValue(), viewer.held(), defined)) hidden.add(e.getKey());
        }
        return hidden.isEmpty() ? Set.of() : Collections.unmodifiableSet(hidden);
    }

    /** {@link #hiddenOn(Class, Viewer)} for the signed-in caller. Request thread only. */
    public Set<String> hiddenOn(Class<?> rowType) {
        return hiddenOn(rowType, viewer());
    }

    /**
     * The rule on a database column, for rows that have no Java type at all.
     *
     * <p>The query builder returns {@code Map<String,Object>} rows straight from
     * JDBC, so there is no field to carry an annotation. The entities do carry
     * {@code @Table} and {@code @Column}, which name the same thing the query
     * selects, so the rule is found by the pair.
     *
     * @return the governing rule, or null when the column is not sensitive
     */
    public static Rule ruleForColumn(String table, String column) {
        if (table == null || column == null) return null;
        return columnRules().get(key(table, column));
    }

    /** Whether this viewer may see that column. */
    public boolean mayViewColumn(String table, String column, Viewer viewer) {
        Rule rule = ruleForColumn(table, column);
        return rule == null || FieldAccessService.mayView(rule, viewer.held(), fieldAccess.definedFieldCodes());
    }

    private static Map<String, Rule> columnRules() {
        Map<String, Rule> cached = columnRules;
        if (cached != null) return cached;
        Map<String, Rule> merged = new LinkedHashMap<>();
        for (Class<?> owner : RULE_OWNERS) {
            jakarta.persistence.Table table = owner.getAnnotation(jakarta.persistence.Table.class);
            if (table == null) continue;
            Map<String, Rule> rules = FieldAccessService.rulesOf(owner);
            for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    Rule rule = rules.get(f.getName());
                    jakarta.persistence.Column col = f.getAnnotation(jakarta.persistence.Column.class);
                    if (rule == null || col == null || col.name().isBlank()) continue;
                    merged.putIfAbsent(key(table.name(), col.name()), rule);
                }
            }
        }
        cached = Collections.unmodifiableMap(merged);
        columnRules = cached;
        return cached;
    }

    private static String key(String table, String column) {
        return table.trim().toUpperCase(java.util.Locale.ROOT) + "." + column.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** The class to read rules from: the first row that is not null. */
    public static Class<?> rowTypeOf(Iterable<?> rows) {
        if (rows == null) return null;
        for (Object row : rows) {
            if (row != null) return row.getClass();
        }
        return null;
    }

    // ── Rule discovery ───────────────────────────────────────────────────────

    /** Every governed property of a row class, by name. Empty when nothing on it is sensitive. */
    public static Map<String, Rule> rulesFor(Class<?> rowType) {
        if (rowType == null) return Map.of();
        return PLANS.computeIfAbsent(rowType, ReportFieldMask::planFor);
    }

    private static Map<String, Rule> planFor(Class<?> rowType) {
        Map<String, Rule> owners = ownerRules();
        Map<String, Rule> plan = new LinkedHashMap<>();
        for (Class<?> c = rowType; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || plan.containsKey(f.getName())) continue;
                Rule rule = ruleOf(f, owners);
                if (rule != null) plan.put(f.getName(), rule);
            }
        }
        return plan.isEmpty() ? Map.of() : Collections.unmodifiableMap(plan);
    }

    private static Rule ruleOf(Field f, Map<String, Rule> owners) {
        Rule own = Rule.of(f).orElse(null);
        if (own != null) return own;
        MaskedAs as = f.getAnnotation(MaskedAs.class);
        if (as != null) {
            Rule named = FieldAccessService.rulesOf(as.type()).get(as.field());
            if (named != null) return named;
            // A @MaskedAs pointing at a field that has no rule is a typo or a rule
            // that was removed. Masking is the safe reading of "this is sensitive".
            return new Rule("", "", new String[]{"__MASKED_AS_UNRESOLVED__"}, new String[0], false);
        }
        return owners.get(f.getName());
    }

    private static Map<String, Rule> ownerRules() {
        Map<String, Rule> cached = ownerRules;
        if (cached != null) return cached;
        Map<String, Rule> merged = new LinkedHashMap<>();
        for (Class<?> owner : RULE_OWNERS) {
            FieldAccessService.rulesOf(owner).forEach(merged::putIfAbsent);
        }
        cached = Collections.unmodifiableMap(merged);
        ownerRules = cached;
        return cached;
    }
}
