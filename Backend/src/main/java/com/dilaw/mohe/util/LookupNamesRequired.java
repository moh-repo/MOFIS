package com.dilaw.mohe.util;

import com.dilaw.mohe.exception.LocalizedArgumentException;
import com.dilaw.mohe.exception.LocalizedMessage;
import com.dilaw.mohe.model.AbstractLuLookup;
import com.dilaw.mohe.model.EnglishNameOptional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A lookup row has an Arabic and an English name (live QA M11, decision D20-a).
 *
 * <p>The lookup API saved rows with blank names on all 33 tables: the screen marks both
 * names required, the entities carry no constraint, and {@link LookupNameUniqueness}
 * skips a blank name on purpose. The LU_* tables name them {@code nameAr}/{@code nameEn};
 * each legacy table has one {@code <x>Name} and one {@code <x>NameEn} field. Both are
 * found here by that shape, so a table added later is covered without a list.
 */
public final class LookupNamesRequired {

    private LookupNamesRequired() {
    }

    private static final Map<Class<?>, List<Field>> NAME_FIELDS = new ConcurrentHashMap<>();

    /** The Arabic and English name fields of a lookup type, Arabic first; empty for any other type. */
    static List<Field> nameFields(Class<?> type) {
        return NAME_FIELDS.computeIfAbsent(type, LookupNamesRequired::findNameFields);
    }

    private static List<Field> findNameFields(Class<?> type) {
        try {
            if (AbstractLuLookup.class.isAssignableFrom(type)) {
                return List.of(AbstractLuLookup.class.getDeclaredField("nameAr"),
                        AbstractLuLookup.class.getDeclaredField("nameEn"));
            }
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
        Field arabic = null;
        Field english = null;
        for (Field f : type.getDeclaredFields()) {
            if (f.getType() != String.class) continue;
            if (f.getName().endsWith("NameEn")) english = f;
            else if (f.getName().endsWith("Name")) arabic = f;
        }
        if (arabic == null || english == null) return List.of();
        return List.of(arabic, english);
    }

    /** Whether the table lets its rows leave the English name blank (P5-6). */
    public static boolean englishOptional(Class<?> type) {
        return type != null && type.isAnnotationPresent(EnglishNameOptional.class);
    }

    /**
     * @throws LocalizedArgumentException (400) naming each blank name, in the reader's language;
     *         nothing for a type that is not a lookup. A table marked {@link EnglishNameOptional}
     *         needs the Arabic name only.
     */
    public static void require(Object entity) {
        if (entity == null) return;
        List<Field> fields = nameFields(entity.getClass());
        if (fields.isEmpty()) return;
        List<Object> missing = new ArrayList<>();
        List<String> english = new ArrayList<>();
        int checked = englishOptional(entity.getClass()) ? 1 : fields.size();
        for (int i = 0; i < checked; i++) {
            if (isBlank(read(fields.get(i), entity))) {
                boolean ar = i == 0;
                english.add(ar ? "the Arabic name" : "the English name");
                missing.add(LocalizedMessage.key(ar ? "lookup.name.ar" : "lookup.name.en", ar ? "the Arabic name" : "the English name"));
            }
        }
        if (!missing.isEmpty()) {
            throw new LocalizedArgumentException("Both names are required: " + String.join(", ", english),
                    "lookup.nameRequired", missing);
        }
    }

    private static Object read(Field f, Object entity) {
        try {
            f.setAccessible(true);
            return f.get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBlank(Object v) {
        return v == null || v.toString().isBlank();
    }
}
