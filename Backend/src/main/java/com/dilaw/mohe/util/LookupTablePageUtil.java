package com.dilaw.mohe.util;

import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** In-memory pagination, search, and sort for small lookup-table datasets. */
public final class LookupTablePageUtil {

    private static final int MAX_PAGE_SIZE = 500;

    private LookupTablePageUtil() {
    }

    /**
     * The whole table in the page shape: every row that matches {@code search}, sorted,
     * as one page (live QA M7/M8). For a caller that asks for no page; not capped at
     * {@link #MAX_PAGE_SIZE}, since these tables are small (the largest is 444 rows).
     */
    public static <T> Map<String, Object> whole(List<T> all, String search, String sortBy, String sortDir) {
        List<T> source = all != null ? all : List.of();
        List<T> sorted = sort(filter(source, search), sortBy, sortDir);
        Map<String, Object> response = new HashMap<>();
        response.put("content", new ArrayList<>(sorted));
        response.put("totalElements", sorted.size());
        response.put("totalPages", sorted.isEmpty() ? 0 : 1);
        response.put("number", 0);
        response.put("size", sorted.size());
        return response;
    }

    public static <T> Map<String, Object> page(List<T> all, int page, int size, String search, String sortBy, String sortDir) {
        List<T> source = all != null ? all : List.of();
        List<T> filtered = filter(source, search);
        List<T> sorted = sort(filtered, sortBy, sortDir);
        int p = Math.max(0, page);
        int s = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        int total = sorted.size();
        int from = Math.min(p * s, total);
        int to = Math.min(from + s, total);
        List<T> content = new ArrayList<>(sorted.subList(from, to));

        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        response.put("totalElements", total);
        response.put("totalPages", s == 0 ? 0 : (int) Math.ceil((double) total / s));
        response.put("number", p);
        response.put("size", s);
        return response;
    }

    private static <T> List<T> filter(List<T> items, String search) {
        if (search == null || search.isBlank()) {
            return items;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        return items.stream().filter(item -> matchesSearch(item, needle)).toList();
    }

    private static boolean matchesSearch(Object item, String needle) {
        if (item == null) {
            return false;
        }
        // Walk the hierarchy: the LU entities keep their columns on
        // AbstractLuLookup, where getDeclaredFields() of the subclass sees nothing.
        Class<?> type = item.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    Object value = field.get(item);
                    if (value != null && value.toString().toLowerCase(Locale.ROOT).contains(needle)) {
                        return true;
                    }
                } catch (IllegalAccessException ignored) {
                    // skip inaccessible fields
                }
            }
            type = type.getSuperclass();
        }
        return item.toString().toLowerCase(Locale.ROOT).contains(needle);
    }

    private static <T> List<T> sort(List<T> items, String sortBy, String sortDir) {
        if (items.isEmpty()) {
            return items;
        }
        String fieldName = (sortBy != null && !sortBy.isBlank()) ? sortBy : "id";
        boolean desc = sortDir != null && sortDir.equalsIgnoreCase("desc");
        Comparator<T> comparator = (a, b) -> compareField(a, b, fieldName);
        if (desc) {
            comparator = comparator.reversed();
        }
        return items.stream().sorted(comparator).toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareField(Object a, Object b, String fieldName) {
        Comparable va = (Comparable) readField(a, fieldName);
        Comparable vb = (Comparable) readField(b, fieldName);
        if (va == null && vb == null) {
            return 0;
        }
        if (va == null) {
            return -1;
        }
        if (vb == null) {
            return 1;
        }
        return va.compareTo(vb);
    }

    private static Object readField(Object item, String fieldName) {
        Class<?> type = item.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(item);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    public static Sort toSort(String sortBy, String sortDir, java.util.Set<String> allowed, String defaultField) {
        String prop = (sortBy != null && allowed.contains(sortBy)) ? sortBy : defaultField;
        return (sortDir != null && sortDir.equalsIgnoreCase("desc"))
                ? Sort.by(prop).descending()
                : Sort.by(prop).ascending();
    }
}
