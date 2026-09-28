package com.dilaw.mohe.service.codelist;

import com.dilaw.mohe.config.CacheConfig;
import com.dilaw.mohe.model.AbstractLuLookup;
import com.dilaw.mohe.model.ChangeRequestField;
import com.dilaw.mohe.model.LuAccountNature;
import com.dilaw.mohe.model.LuGraphCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one reader of the code lists.
 *
 * <p>Every list in {@link CodeListRegistry} is loaded with one SELECT, held in
 * the {@code code-lists} cache (sixty seconds, see {@code CacheConfig}) and
 * evicted by every write the application makes to a lookup table — this
 * service's own writes and the lookup-screen services. Behaviour never asks
 * this service which codes "count as open": those sets are Java constants
 * ({@code codes/}); it asks for names, colours, icons and existence.
 */
@Service
public class CodeListService {

    private static final Set<String> SEVERITIES = Set.of("success", "info", "warn", "danger", "secondary", "contrast");

    private final CodeListLoader loader;

    @PersistenceContext
    private EntityManager em;

    public CodeListService(CodeListLoader loader) {
        this.loader = loader;
    }

    // ── reads ────────────────────────────────────────────────────────────────

    /** All rows of a list, active and soft-deleted, in table order (cached by {@link CodeListLoader}). */
    public List<CodeRow> rows(String name) {
        return loader.rows(name);
    }

    public List<CodeRow> active(String name) {
        return rows(name).stream().filter(CodeRow::active).toList();
    }

    /** The active codes of a list, in table order. */
    public Set<String> codes(String name) {
        return active(name).stream().map(CodeRow::code).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** The row for a code, active or not; case-insensitive on the code. */
    public Optional<CodeRow> find(String name, String code) {
        if (code == null) return Optional.empty();
        return rows(name).stream().filter(r -> code.equalsIgnoreCase(r.code())).findFirst();
    }

    /** The active row for a code, or 400 — for values arriving from a request. */
    public CodeRow require(String name, String code) {
        return find(name, code).filter(CodeRow::active)
                .orElseThrow(() -> new com.dilaw.mohe.exception.LocalizedStatusException(HttpStatus.BAD_REQUEST,
                        "'" + code + "' is not a " + name + " code", "codeList.notAllowed", code));
    }

    /** The name of a code in a language; the code itself when unknown. */
    public String label(String name, String code, String lang) {
        return find(name, code).map(r -> r.name(lang)).orElse(code == null ? "" : code);
    }

    /** The PrimeNG severity of a code; {@code secondary} when unknown or unset. */
    public String severity(String name, String code) {
        return find(name, code).map(CodeRow::uiSeverity).filter(SEVERITIES::contains).orElse("secondary");
    }

    /** Every list, keyed by name — the bulk payload the frontend loads once. */
    public Map<String, List<CodeRow>> all() {
        Map<String, List<CodeRow>> out = new LinkedHashMap<>();
        for (String n : CodeListRegistry.names()) {
            out.put(n, rows(n));
        }
        return out;
    }

    public boolean isSystem(String name, String code) {
        return find(name, code).map(CodeRow::system).orElse(false);
    }

    // ── writes (LU-backed lists only) ────────────────────────────────────────

    /** The fields a code-list row can be edited with. */
    public record CodeRowInput(String code, String nameAr, String nameEn, Integer sortOrder,
                               String uiSeverity, String icon, Boolean active, String extra) {
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public CodeRow create(String name, CodeRowInput in) {
        CodeListRegistry.LuList list = editable(name);
        validate(in, true, com.dilaw.mohe.util.LookupNamesRequired.englishOptional(list.entity()));
        if (find(name, in.code()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Code already exists: " + in.code());
        }
        AbstractLuLookup e;
        try {
            e = list.entity().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot instantiate " + list.entity(), ex);
        }
        e.setCode(in.code().trim());
        apply(e, in);
        e.setIsDeleted(Boolean.FALSE.equals(in.active()) ? Boolean.TRUE : Boolean.FALSE);
        e.setIsSystem(false);
        em.persist(e);
        em.flush();
        return toRow(e);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public CodeRow update(String name, Integer id, CodeRowInput in) {
        CodeListRegistry.LuList list = editable(name);
        validate(in, false, com.dilaw.mohe.util.LookupNamesRequired.englishOptional(list.entity()));
        AbstractLuLookup e = em.find(list.entity(), id);
        if (e == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No row " + id + " in " + name);
        }
        if (in.code() != null && !in.code().trim().equalsIgnoreCase(e.getCode())) {
            if (Boolean.TRUE.equals(e.getIsSystem())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "'" + e.getCode() + "' is a system code: the application depends on it and it cannot be renamed");
            }
            if (find(name, in.code()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Code already exists: " + in.code());
            }
            e.setCode(in.code().trim());
        }
        apply(e, in);
        if (in.active() != null) {
            if (!in.active() && Boolean.TRUE.equals(e.getIsSystem())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "'" + e.getCode() + "' is a system code: the application depends on it and it cannot be deactivated");
            }
            e.setIsDeleted(!in.active());
        }
        em.flush();
        return toRow(e);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CODE_LISTS, allEntries = true)
    public void delete(String name, Integer id) {
        CodeListRegistry.LuList list = editable(name);
        AbstractLuLookup e = em.find(list.entity(), id);
        if (e == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No row " + id + " in " + name);
        }
        if (Boolean.TRUE.equals(e.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + e.getCode() + "' is a system code: the application depends on it and it cannot be deleted");
        }
        e.setIsDeleted(true);
        em.flush();
    }

    /** Called by the lookup-screen services after any write of their own. */
    public void evictAll() {
        loader.evictAll();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static CodeListRegistry.LuList editable(String name) {
        return CodeListRegistry.lu(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                CodeListRegistry.exists(name)
                        ? name + " is read-only here; it is edited on its own screen"
                        : "Unknown code list: " + name));
    }

    /** @param englishOptional the table lets a row leave the English name blank (report labels, P5-6) */
    private static void validate(CodeRowInput in, boolean creating, boolean englishOptional) {
        if (creating && (in.code() == null || in.code().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
        }
        if (creating && (in.nameAr() == null || in.nameAr().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    englishOptional ? "nameAr is required" : "nameAr and nameEn are required");
        }
        if (creating && !englishOptional && (in.nameEn() == null || in.nameEn().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nameAr and nameEn are required");
        }
        if (in.uiSeverity() != null && !in.uiSeverity().isBlank() && !SEVERITIES.contains(in.uiSeverity())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "uiSeverity must be one of " + SEVERITIES);
        }
    }

    private static void apply(AbstractLuLookup e, CodeRowInput in) {
        if (in.nameAr() != null) e.setNameAr(in.nameAr().trim());
        if (in.nameEn() != null) e.setNameEn(in.nameEn().trim());
        if (in.sortOrder() != null) e.setSortOrder(in.sortOrder());
        if (in.uiSeverity() != null) e.setUiSeverity(in.uiSeverity().isBlank() ? null : in.uiSeverity());
        if (in.icon() != null) e.setIcon(in.icon().isBlank() ? null : in.icon().trim());
        if (in.extra() != null) {
            if (e instanceof LuGraphCategory g) g.setUiColor(in.extra().isBlank() ? null : in.extra().trim());
            else if (e instanceof LuAccountNature a) a.setNormalBalance(in.extra().isBlank() ? null : in.extra().trim());
            else if (e instanceof ChangeRequestField f) f.setLookupList(in.extra().isBlank() ? null : in.extra().trim());
        }
    }

    private static CodeRow toRow(AbstractLuLookup e) {
        String extra = null;
        if (e instanceof LuGraphCategory g) extra = g.getUiColor();
        else if (e instanceof LuAccountNature a) extra = a.getNormalBalance();
        else if (e instanceof ChangeRequestField f) extra = f.getLookupList();
        return new CodeRow(e.getId(), e.getCode(), e.getNameAr(), e.getNameEn(),
                e.getSortOrder() == null ? 0 : e.getSortOrder(), e.getUiSeverity(), e.getIcon(),
                Boolean.TRUE.equals(e.getIsSystem()), !Boolean.TRUE.equals(e.getIsDeleted()), extra);
    }
}
