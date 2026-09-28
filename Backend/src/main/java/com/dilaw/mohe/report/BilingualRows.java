package com.dilaw.mohe.report;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a report's lookup names to the language the report is being rendered
 * in, so an English report prints English data and not merely English headings.
 *
 * <h2>Why this exists</h2>
 *
 * Every report accepts {@code ?lang=ar|en}, and {@code ReportParamBuilder}
 * switches the titles, column headings, labels and footer correctly. The row
 * data did not follow: the report views expose one Arabic name column per
 * lookup, so {@code student-list.jrxml?lang=en} printed English headings over
 * Arabic country, major and degree names. That was finding 4 of
 * {@code docs/report-data-audit.md}.
 *
 * <h2>How it works</h2>
 *
 * The views now expose a paired {@code *_NAME_EN} column beside each Arabic
 * name, and the mapped entity carries it as a sibling property: {@code
 * studentCountryName} and {@code studentCountryNameEn}. For an English render
 * this class returns copies of the rows in which each such property has been
 * replaced by its English sibling.
 *
 * <p>The substitution happens on the row, not in the template. Templates keep
 * printing {@code $F{studentCountryName}} and never branch on {@code $P{LANG}} —
 * which is the rule the report layer already follows, and the reason a single
 * class can serve all 50-odd templates instead of each one growing a conditional.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <ul>
 *   <li><b>It does not fabricate a translation.</b> A blank English name leaves
 *       the Arabic in place. Showing Arabic where no English was recorded is
 *       worse than nothing only if you would rather print an empty cell; the
 *       ministry's lookups are fully translated today, so this is a guard rather
 *       than a routine path.</li>
 *   <li><b>It does not mutate the rows it is given.</b> The entities are
 *       {@code @Immutable} and may be sitting in a persistence context or a
 *       second-level cache; writing English into them would leak into whatever
 *       else holds that instance. Every substitution goes onto a fresh copy.</li>
 *   <li><b>It does nothing at all for Arabic.</b> The Arabic render is the
 *       common case and returns the original collection untouched.</li>
 * </ul>
 */
public final class BilingualRows {

    /** Property plans are derived by reflection once per row class. */
    private static final Map<Class<?>, List<Swap>> PLANS = new ConcurrentHashMap<>();

    /** A property to overwrite, and the English sibling to take the value from. */
    private record Swap(Method read, Method write, Method readEn) { }

    /** Marks a class this cannot copy, so the reflection is not retried per row. */
    private static final List<Swap> NOT_COPYABLE = Collections.emptyList();

    private BilingualRows() { }

    /**
     * The rows to fill the template with.
     *
     * @param rows the report rows as the service queried them
     * @param lang the {@code LANG} Jasper parameter; only {@code "en"} changes anything
     * @return the original collection for Arabic, otherwise copies carrying English names
     */
    public static Collection<?> forLanguage(Collection<?> rows, Object lang) {
        if (rows == null || rows.isEmpty() || !"en".equalsIgnoreCase(String.valueOf(lang))) {
            return rows;
        }
        List<Object> out = new ArrayList<>(rows.size());
        for (Object row : rows) {
            out.add(row == null ? null : englishCopy(row));
        }
        return out;
    }

    /** A copy of {@code row} with every translatable property set to its English sibling. */
    private static Object englishCopy(Object row) {
        List<Swap> plan = PLANS.computeIfAbsent(row.getClass(), BilingualRows::planFor);
        if (plan.isEmpty()) {
            // Nothing to translate, or the class cannot be copied. Either way the
            // original row is the right answer -- an untranslated report beats a
            // failed one.
            return row;
        }
        try {
            Object copy = row.getClass().getDeclaredConstructor().newInstance();
            for (PropertyDescriptor pd : properties(row.getClass())) {
                Method read = pd.getReadMethod();
                Method write = pd.getWriteMethod();
                if (read != null && write != null) {
                    write.invoke(copy, read.invoke(row));
                }
            }
            for (Swap swap : plan) {
                Object english = swap.readEn().invoke(row);
                if (english instanceof String s && !s.isBlank()) {
                    swap.write().invoke(copy, s);
                }
            }
            return copy;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A row that cannot be copied is rendered as it stands rather than
            // failing the export. The Arabic name is a visible, recoverable
            // problem; a 500 on a report the user asked for is not.
            PLANS.put(row.getClass(), NOT_COPYABLE);
            return row;
        }
    }

    /**
     * The translatable properties of a row class: every readable/writable String
     * property {@code x} that has a readable String sibling {@code xEn}.
     *
     * <p>Returns empty when the class has no no-argument constructor, since the
     * copy could not be made.
     */
    private static List<Swap> planFor(Class<?> type) {
        try {
            type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            return NOT_COPYABLE;
        }
        Map<String, PropertyDescriptor> byName = new java.util.HashMap<>();
        for (PropertyDescriptor pd : properties(type)) {
            byName.put(pd.getName(), pd);
        }
        List<Swap> plan = new ArrayList<>();
        for (PropertyDescriptor pd : byName.values()) {
            if (pd.getPropertyType() != String.class
                    || pd.getReadMethod() == null || pd.getWriteMethod() == null) {
                continue;
            }
            PropertyDescriptor en = byName.get(pd.getName() + "En");
            if (en != null && en.getPropertyType() == String.class && en.getReadMethod() != null) {
                plan.add(new Swap(pd.getReadMethod(), pd.getWriteMethod(), en.getReadMethod()));
            }
        }
        return plan.isEmpty() ? NOT_COPYABLE : List.copyOf(plan);
    }

    private static PropertyDescriptor[] properties(Class<?> type) {
        try {
            BeanInfo info = Introspector.getBeanInfo(type, Object.class);
            return info.getPropertyDescriptors();
        } catch (IntrospectionException e) {
            return new PropertyDescriptor[0];
        }
    }
}
