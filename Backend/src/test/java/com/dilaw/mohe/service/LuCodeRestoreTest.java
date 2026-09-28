package com.dilaw.mohe.service;

import com.dilaw.mohe.model.LuBooksAllowancePayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Creating a code on the System Coding screen (QA B15, live QA #20).
 *
 * <p>Books-allowance pay status is the table the live test used: its CODE is ten
 * characters wide, and "re-add fails with 409" after a delete. Rolled back.
 */
@Tag("integration")
@SpringBootTest
@Transactional
class LuCodeRestoreTest {

    @Autowired private LuExtendedLookupAdminService service;

    private static LuBooksAllowancePayStatus code(String code, String name) {
        LuBooksAllowancePayStatus row = new LuBooksAllowancePayStatus();
        row.setCode(code);
        row.setNameAr(name);
        row.setNameEn(name);
        return row;
    }

    @Test
    @DisplayName("a deleted code added again comes back, with the new names, instead of a 409")
    void deletedCodeIsRestored() {
        LuBooksAllowancePayStatus first = service.createBooksAllowancePayStatus(code("ZZRESTORE", "first"));
        service.deleteBooksAllowancePayStatus(first.getId());

        LuBooksAllowancePayStatus again = service.createBooksAllowancePayStatus(code("ZZRESTORE", "again"));

        assertThat(again.getId()).as("the same row, not a duplicate").isEqualTo(first.getId());
        assertThat(again.getIsDeleted()).isFalse();
        assertThat(again.getNameEn()).isEqualTo("again");
        assertThat(service.getAllBooksAllowancePayStatuses()).extracting(LuBooksAllowancePayStatus::getCode)
                .contains("ZZRESTORE");
    }

    @Test
    @DisplayName("a code wider than its column is refused before the database, naming the field and the limit")
    void tooLongCodeNamesTheField() {
        assertThatThrownBy(() -> service.createBooksAllowancePayStatus(code("ZZ345678901", "long")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Code")
                .hasMessageContaining("10");
    }
}
