package com.dilaw.mohe.util;

import com.dilaw.mohe.dto.LookupOptionDto;
import org.springframework.context.i18n.LocaleContextHolder;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Report filter options in the reader's language, active rows only (live QA M13).
 *
 * <p>The report metadata services each mapped their lookup rows by hand to the Arabic
 * name and read them with {@code findAll()}, so an English screen showed Arabic genders,
 * statuses, majors and payment types, and the gender filter offered the inactive test
 * row the Add Student wizard already hides. Every one now builds its options here: the
 * language is the request's ({@code Accept-Language}, which the frontend sets on every
 * call), the name falls back to the other language and then to the id, and a row whose
 * {@code isActive} is {@code false} is left out. The two names are found by the shape
 * {@link LookupNamesRequired} uses, so the services pass only the id.
 */
public final class LocalizedLookupOptions {

    private LocalizedLookupOptions() {
    }

    private static final Map<Class<?>, Optional<Field>> ACTIVE_FIELD = new ConcurrentHashMap<>();

    /** The request asked for English; anything else reads Arabic, the system's first language. */
    public static boolean english() {
        return "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
    }

    /** The active rows as options named in the reader's language, sorted by that name. */
    public static <T> List<LookupOptionDto> of(Collection<T> rows, Function<T, Integer> id) {
        boolean arabic = !english();
        return rows.stream()
                .filter(Objects::nonNull)
                .filter(LocalizedLookupOptions::isActive)
                .map(row -> {
                    Integer key = id.apply(row);
                    return new LookupOptionDto(key, name(row, arabic, key));
                })
                .sorted(Comparator.comparing(LookupOptionDto::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /** The row's name in the requested language, falling back to the other, then to the id. */
    public static String name(Object row, boolean arabic, Object id) {
        List<Field> fields = LookupNamesRequired.nameFields(row.getClass());
        String ar = fields.isEmpty() ? null : text(fields.get(0), row);
        String en = fields.isEmpty() ? null : text(fields.get(1), row);
        return pick(arabic ? ar : en, arabic ? en : ar, id);
    }

    /**
     * The first of two names that is not blank, then the id. Not trimmed: some reports
     * filter on the name itself, and it must equal the stored value.
     */
    public static String pick(String primary, String fallback, Object id) {
        if (primary != null && !primary.isBlank()) return primary;
        if (fallback != null && !fallback.isBlank()) return fallback;
        return String.valueOf(id);
    }

    /** A row is offered unless its {@code isActive} flag says {@code false}; a table without one is all active. */
    static boolean isActive(Object row) {
        Optional<Field> field = ACTIVE_FIELD.computeIfAbsent(row.getClass(), LocalizedLookupOptions::findActiveField);
        if (field.isEmpty()) return true;
        try {
            return !Boolean.FALSE.equals(field.get().get(row));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Optional<Field> findActiveField(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("isActive");
                if (f.getType() == Boolean.class || f.getType() == boolean.class) {
                    f.setAccessible(true);
                    return Optional.of(f);
                }
            } catch (NoSuchFieldException ignored) {
                // look in the superclass
            }
        }
        return Optional.empty();
    }

    private static String text(Field f, Object row) {
        try {
            f.setAccessible(true);
            Object v = f.get(row);
            return v == null ? null : v.toString();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
