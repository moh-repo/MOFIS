package com.dilaw.mohe.util;

import com.dilaw.mohe.model.AbstractLuLookup;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The rules the Lookup Tables screen has to obey when it writes to a
 * {@code MOHECORE.LU_*} code list.
 *
 * <p>These lists are not free text. {@code CodeListRegistry} ties each of them to
 * a class of Java constants, and {@code CodeListStartupGuard} refuses to start the
 * application when a constant's code has gone from the database. So
 * {@code CodeListService} — what the Code Lists screen calls — refuses to rename or
 * delete a row marked {@code IS_SYSTEM}, and refuses to deactivate one.
 *
 * <p>The Lookup Tables screen reaches sixteen of the same tables through
 * {@code LookupTableController}, and reached them with no guard at all: every row
 * of {@code LU_WAFAR_SPENDING_STATUS}, {@code LU_PAYMENT_AUTH_STATUS},
 * {@code LU_QUARTER_NUMBER} and {@code LU_REPORT_LABEL} is a system row, and for
 * ten of the sixteen the delete was {@code repository.deleteById} — so one click on
 * that screen could stop the application starting again. The wording here is
 * {@code CodeListService}'s, so a user meets one sentence and not two.
 */
public final class LuLookupWrites {

    private LuLookupWrites() {
    }

    /**
     * Refuses a change of {@code CODE} on a system row. Everything else about such
     * a row — both names, the sort order — stays editable, which is the same line
     * the Code Lists screen draws: the constants name the code, not the label.
     */
    public static void requireRenameable(AbstractLuLookup existing, String newCode) {
        if (newCode == null || newCode.trim().equalsIgnoreCase(existing.getCode())) {
            return;
        }
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + existing.getCode() + "' is a system code: the application depends on it and it cannot be renamed");
        }
    }

    /** Refuses to delete a row a Java constant names. */
    public static void requireDeletable(AbstractLuLookup existing) {
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + existing.getCode() + "' is a system code: the application depends on it and it cannot be deleted");
        }
    }

    /**
     * Copies the columns the Lookup Tables screen offers onto the loaded row.
     *
     * <p>{@code IS_SYSTEM}, {@code IS_DELETED}, {@code UI_SEVERITY} and {@code ICON}
     * are deliberately not among them: the first two are the application's to set,
     * and the last two belong to the Code Lists screen, which is where a code's
     * colour and icon are chosen. Saving the request body over the row — which is
     * what these services used to do — let any of the four be cleared by a payload
     * that simply did not mention it.
     */
    public static void copyEditable(AbstractLuLookup from, AbstractLuLookup to) {
        requireRenameable(to, from.getCode());
        if (from.getCode() != null) {
            to.setCode(from.getCode().trim());
        }
        to.setNameAr(from.getNameAr());
        to.setNameEn(from.getNameEn());
        if (from.getSortOrder() != null) {
            to.setSortOrder(from.getSortOrder());
        }
    }

    /** A code created from this screen is a new one, never a system one. */
    public static void prepareForCreate(AbstractLuLookup entity) {
        entity.setIsDeleted(false);
        entity.setIsSystem(false);
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
    }
}
