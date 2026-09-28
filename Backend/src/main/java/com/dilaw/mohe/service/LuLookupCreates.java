package com.dilaw.mohe.service;

import com.dilaw.mohe.model.AbstractLuLookup;
import com.dilaw.mohe.util.LuLookupWrites;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Creating a row in a {@code MOHECORE.LU_*} code list from the System Coding screen.
 *
 * <p>Two things went wrong there (QA B15, live QA #20):
 * <ul>
 *   <li><b>Re-adding a deleted code failed with 409.</b> A delete on these lists
 *   only sets {@code IS_DELETED}, and the row keeps its code under the unique
 *   index, so adding the same code again was refused as a duplicate of a row
 *   nobody could see. The deleted row is brought back instead, with the names the
 *   user just typed.</li>
 *   <li><b>A code over the column's width failed without saying which field.</b>
 *   Thirteen of these tables hold a 10-character {@code CODE} while the mapped
 *   entity says 50, so the request reached the database and came back as a
 *   generic "a value is too long". The width is read from the table itself and
 *   the refusal names the field.</li>
 * </ul>
 */
@Component
public class LuLookupCreates {

    @PersistenceContext
    private EntityManager em;

    private final JdbcTemplate jdbc;

    /** Column width of CODE per table, read once: it only changes with a migration. */
    private final Map<String, Integer> codeWidths = new ConcurrentHashMap<>();

    public LuLookupCreates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public <T extends AbstractLuLookup> T create(T entity, UnaryOperator<T> save) {
        LuLookupWrites.prepareForCreate(entity);
        String code = entity.getCode() == null ? "" : entity.getCode().trim();
        entity.setCode(code);
        requireCodeFits(entity.getClass(), code);

        T deleted = softDeletedWithCode(entity, code);
        if (deleted == null) {
            return save.apply(entity);
        }
        deleted.setNameAr(entity.getNameAr());
        deleted.setNameEn(entity.getNameEn());
        if (entity.getSortOrder() != null && entity.getSortOrder() != 0) {
            deleted.setSortOrder(entity.getSortOrder());
        }
        deleted.setIsDeleted(false);
        return save.apply(deleted);
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractLuLookup> T softDeletedWithCode(T entity, String code) {
        if (code.isEmpty()) {
            return null;
        }
        Class<T> type = (Class<T>) entity.getClass();
        String name = em.getMetamodel().entity(type).getName();
        List<T> rows = em.createQuery(
                        "SELECT e FROM " + name + " e WHERE e.code = :code AND e.isDeleted = true", type)
                .setParameter("code", code)
                .setMaxResults(1)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void requireCodeFits(Class<?> type, String code) {
        Table table = type.getAnnotation(Table.class);
        if (table == null) {
            return;
        }
        String schema = table.schema().isEmpty() ? "MOHECORE" : table.schema();
        Integer width = codeWidths.computeIfAbsent(schema + "." + table.name(), key -> {
            List<Integer> found = jdbc.queryForList(
                    "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = 'CODE'",
                    Integer.class, schema, table.name());
            return found.isEmpty() || found.get(0) == null ? Integer.MAX_VALUE : found.get(0);
        });
        if (code.length() > width) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Code: at most " + width + " characters (this one has " + code.length() + ").");
        }
    }
}
