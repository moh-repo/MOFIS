package com.dilaw.mohe.util;

import com.dilaw.mohe.exception.LocalizedStatusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Objects;

/**
 * One name per list: the legacy lookup tables (STUDENT_DEGREE, STUDY_COUNTRY_IN and
 * the rest) carry no unique index on their name column, so the API accepted a second
 * "جامعية" degree with a 201 (live test M12, 23 Sep 2026). The rule is the same for
 * every list — count the rows whose trimmed, lower-cased name matches, leaving out
 * the row being edited — so it lives here and each service calls it before it saves.
 *
 * <p>Lists that hang off a parent (a college under its university, a branch under
 * its bank, a governorate under its country) are unique <em>within the parent</em>:
 * every university has a "كلية الطب", and refusing the second one would be a worse
 * fault than the one this fixes. Those services call the overload that names the
 * parent attribute.
 *
 * <p>A refused name is a 409 in the reader's language ({@code lookup.duplicateName}).
 * Nothing is done for a blank name — whether a name is required is the form's rule,
 * not this one's — and an edit that keeps the row's own name is never refused, so
 * the duplicates the tables already hold stay editable (see {@link #same}).
 */
public final class LookupNameUniqueness {

    private LookupNameUniqueness() {
    }

    /**
     * Refuses {@code name} when another row of the whole list already carries it.
     *
     * @param em            the entity manager the service's transaction runs on
     * @param entity        the lookup's entity class
     * @param nameAttribute the JPA attribute holding the name that must be unique
     * @param name          the name the caller wants to save; blank or null is not checked
     * @param idAttribute   the JPA attribute holding the id
     * @param excludeId     the row being edited, left out of the count; null on create
     * @throws LocalizedStatusException 409 when another row in the list already has the name
     */
    public static <T> void require(EntityManager em, Class<T> entity, String nameAttribute, String name,
                                   String idAttribute, Integer excludeId) {
        require(em, entity, nameAttribute, name, idAttribute, excludeId, null, null);
    }

    /**
     * As {@link #require(EntityManager, Class, String, String, String, Integer)}, counting
     * only the rows under the same parent ({@code parentAttribute = parent}, or
     * {@code IS NULL} when the parent is null).
     */
    public static <T> void require(EntityManager em, Class<T> entity, String nameAttribute, String name,
                                   String idAttribute, Integer excludeId,
                                   String parentAttribute, Object parent) {
        if (name == null || name.isBlank()) {
            return;
        }
        String wanted = normalize(name);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(entity);
        Predicate where = cb.equal(cb.lower(cb.trim(root.<String>get(nameAttribute))), wanted);
        if (excludeId != null) {
            where = cb.and(where, cb.notEqual(root.get(idAttribute), excludeId));
        }
        if (parentAttribute != null) {
            where = cb.and(where, parent == null
                    ? cb.isNull(root.get(parentAttribute))
                    : cb.equal(root.get(parentAttribute), parent));
        }
        Long count = em.createQuery(query.select(cb.count(root)).where(where)).getSingleResult();
        if (count != null && count > 0) {
            throw new LocalizedStatusException(HttpStatus.CONFLICT,
                    "A row named \"" + name.trim() + "\" already exists in this list.",
                    "lookup.duplicateName", name.trim());
        }
    }

    /**
     * Whether two names are the same list name — equal once trimmed and lower-cased,
     * the way the database is asked. An edit that keeps the row's name skips the check.
     */
    public static boolean same(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            return false;
        }
        return normalize(a).equals(normalize(b));
    }

    /**
     * Whether an edit of a row in a parented list claims nothing new: it keeps the
     * row's name <em>and</em> its parent. Moving "الفرع الرئيسي" to another bank is a
     * new claim under that bank, even though the name is the row's own.
     */
    public static boolean unchanged(String currentName, String newName, Object currentParent, Object newParent) {
        return same(currentName, newName) && Objects.equals(currentParent, newParent);
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
