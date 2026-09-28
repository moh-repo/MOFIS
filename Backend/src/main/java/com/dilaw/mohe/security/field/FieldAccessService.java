package com.dilaw.mohe.security.field;

import com.dilaw.mohe.config.CacheConfig;
import com.dilaw.mohe.config.FieldPermission;
import com.dilaw.mohe.config.OwnedByUser;
import com.dilaw.mohe.config.SecurityRequestUtils;
import com.dilaw.mohe.config.SensitiveField;
import com.dilaw.mohe.ums.repository.UmsPermissionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Field-level enforcement, both directions (security plan R-18, O-03).
 *
 * <p><b>Read.</b> {@link #mayView} answers for one annotated field: when the
 * UMS catalogue defines {@code <page>.<field>.VIEW}, the caller must hold it;
 * otherwise the annotation's {@code anyOf} fallback applies. The Jackson
 * writer calls this for every masked property of every response, so the
 * "is this code defined" lookup is one cached set, not a query.
 *
 * <p><b>Write.</b> {@link #assertMayEdit} takes the properties a request
 * changed and refuses with 400 (D-9 a) any the caller may not edit: the
 * defined {@code .EDIT} code, else {@code editAnyOf}. A field the caller may
 * not even see is refused on write as well — you cannot change what you
 * cannot read.
 *
 * <p>The rules read only the annotation and the security context, so the
 * service is safe to call from a serializer.
 */
@Service
public class FieldAccessService {

    private static volatile FieldAccessService current;

    private final UmsPermissionRepository permissionRepository;

    public FieldAccessService(UmsPermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
        current = this;
    }

    /** For the Jackson writer, which Spring does not construct. Empty before the context is up. */
    public static Optional<FieldAccessService> current() {
        return Optional.ofNullable(current);
    }

    // ── Rule ─────────────────────────────────────────────────────────────────

    /** One field's rule, read from either annotation. */
    public record Rule(String page, String field, String[] anyOf, String[] editAnyOf, boolean ownerVisible) {

        public static Rule of(FieldPermission fp) {
            return new Rule(fp.page(), fp.field(), fp.anyOf(), fp.editAnyOf(), fp.ownerVisible());
        }

        public static Rule of(SensitiveField sf) {
            return new Rule("", "", sf.anyOf(), new String[0], false);
        }

        public static Optional<Rule> of(Field f) {
            FieldPermission fp = f.getAnnotation(FieldPermission.class);
            if (fp != null) return Optional.of(of(fp));
            SensitiveField sf = f.getAnnotation(SensitiveField.class);
            if (sf != null) return Optional.of(of(sf));
            return Optional.empty();
        }

        public boolean catalogued() {
            return page != null && !page.isBlank() && field != null && !field.isBlank();
        }

        public String viewCode() {
            return page + "." + field + ".VIEW";
        }

        public String editCode() {
            return page + "." + field + ".EDIT";
        }
    }

    // ── Read side ────────────────────────────────────────────────────────────

    /** Whether the signed-in caller may see this field of this bean. */
    public boolean mayView(Rule rule, Object bean) {
        if (rule.ownerVisible() && bean instanceof OwnedByUser owned) {
            Long me = SecurityRequestUtils.currentUserId().orElse(null);
            if (me != null && me.equals(owned.ownerUserId())) return true;
        }
        return mayView(rule, authorities(), definedFieldCodes());
    }

    /**
     * The read rule for a caller holding {@code held}, without the owner
     * exception — there is no bean to own.
     *
     * <p>Pure, so {@link ReportFieldMask} can decide on the request thread and
     * apply the answer on a render thread that has no security context, and so a
     * scheduled export can be masked by its owner's codes rather than by the
     * empty set a {@code @Scheduled} thread would offer (plan W5-03).
     */
    public static boolean mayView(Rule rule, Set<String> held, Set<String> definedCodes) {
        if (rule.catalogued() && definedCodes.contains(rule.viewCode())) {
            return held.contains(rule.viewCode());
        }
        if (rule.anyOf().length == 0) return true;
        for (String code : rule.anyOf()) {
            if (held.contains(code)) return true;
        }
        return false;
    }

    // ── Write side ───────────────────────────────────────────────────────────

    /**
     * Refuses with 400 when {@code changedProperties} names a field of
     * {@code type} the caller may not edit.
     */
    public void assertMayEdit(Class<?> type, Collection<String> changedProperties) {
        if (changedProperties == null || changedProperties.isEmpty()) return;
        Set<String> held = authorities();
        List<String> refused = new ArrayList<>();
        for (Map.Entry<String, Rule> e : rulesOf(type).entrySet()) {
            if (!changedProperties.contains(e.getKey())) continue;
            if (!mayEdit(e.getValue(), held)) refused.add(e.getKey());
        }
        if (!refused.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You may not change: " + String.join(", ", refused)
                            + " (no EDIT permission on these fields; the rest of the form was not saved).");
        }
    }

    private boolean mayEdit(Rule rule, Set<String> held) {
        if (rule.catalogued() && definedFieldCodes().contains(rule.viewCode()) && !held.contains(rule.viewCode())) {
            return false; // cannot change what you cannot see
        }
        if (rule.catalogued() && definedFieldCodes().contains(rule.editCode())) {
            return held.contains(rule.editCode());
        }
        if (rule.editAnyOf().length == 0) return true;
        for (String code : rule.editAnyOf()) {
            if (held.contains(code)) return true;
        }
        return false;
    }

    /**
     * The annotated properties of {@code before}'s class whose value differs on
     * {@code after}, matched by name — {@code after} may be a DTO. With
     * {@code partial}, a null on {@code after} means "not submitted".
     */
    public List<String> changedProperties(Class<?> annotatedType, Object before, Object after, boolean partial) {
        List<String> changed = new ArrayList<>();
        if (after == null) return changed;
        Map<String, Field> afterFields = fieldsOf(after.getClass());
        Map<String, Field> beforeFields = before == null ? Map.of() : fieldsOf(before.getClass());
        for (String name : rulesOf(annotatedType).keySet()) {
            Field af = afterFields.get(name);
            if (af == null) continue;
            Object newValue = read(af, after);
            if (partial && newValue == null) continue;
            Object oldValue = null;
            Field bf = beforeFields.get(name);
            if (before != null && bf != null) oldValue = read(bf, before);
            if (!Objects.equals(normalise(oldValue), normalise(newValue))) changed.add(name);
        }
        return changed;
    }

    /** {@code changedProperties} followed by {@code assertMayEdit}. */
    public void assertMayEdit(Class<?> annotatedType, Object before, Object after, boolean partial) {
        assertMayEdit(annotatedType, changedProperties(annotatedType, before, after, partial));
    }

    // ── Catalogue ────────────────────────────────────────────────────────────

    /** Every active FIELD permission code, cached for a minute (the Fields screen changes them rarely). */
    @Cacheable(cacheNames = CacheConfig.FIELD_PERMISSIONS, key = "'codes'")
    public Set<String> definedFieldCodes() {
        return new HashSet<>(permissionRepository.findActiveFieldPermissionCodes());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** The annotated fields of a class, by property name, superclasses included. */
    public static Map<String, Rule> rulesOf(Class<?> type) {
        Map<String, Rule> rules = new LinkedHashMap<>();
        for (Field f : fieldsOf(type).values()) {
            Rule.of(f).ifPresent(r -> rules.put(f.getName(), r));
        }
        return rules;
    }

    private static Map<String, Field> fieldsOf(Class<?> type) {
        Map<String, Field> out = new LinkedHashMap<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                out.putIfAbsent(f.getName(), f);
            }
        }
        return out;
    }

    private static Object read(Field f, Object bean) {
        try {
            f.setAccessible(true);
            return f.get(bean);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Blank strings count as null so a form that posts "" for an empty column is not a change. */
    private static Object normalise(Object v) {
        if (v instanceof String s) return s.isBlank() ? null : s.trim();
        return v;
    }

    static Set<String> authorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Set.of();
        Set<String> out = new HashSet<>();
        for (GrantedAuthority a : auth.getAuthorities()) out.add(a.getAuthority());
        return out;
    }
}
