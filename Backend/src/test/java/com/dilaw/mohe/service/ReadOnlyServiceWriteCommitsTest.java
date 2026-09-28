package com.dilaw.mohe.service;

import com.dilaw.mohe.model.StudyCurrencyIn;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A write made through a service whose class is {@code @Transactional(readOnly = true)}
 * must reach the database.
 *
 * <p>Thirty-nine services carried that class-level annotation and wrote inside it. A
 * read-only transaction puts the Hibernate session in {@code FlushMode.MANUAL}, so a
 * {@code save} of an existing row — a merge — is applied to the managed copy and never
 * flushed: the call returns the updated object and the table keeps the old value.
 * Inserts survived only because IDENTITY keys force an immediate INSERT. The write
 * methods now carry their own {@code @Transactional}; this test proves one of them
 * commits, re-reading through a fresh {@code EntityManager} and through plain JDBC so
 * no first-level cache can answer for the database.
 *
 * <p>Runs outside any test transaction on purpose — the service's own transaction has
 * to commit. The original value is restored by JDBC afterwards.
 */
@Tag("integration")
@SpringBootTest
class ReadOnlyServiceWriteCommitsTest {

    private static final String SELECT_NAME_EN =
            "SELECT STUDY_CURRENCY_IN_NAME_EN FROM MOHECORE.STUDY_CURRENCY_IN WHERE STUDY_CURRENCY_IN_ID = ?";

    @Autowired private StudyCurrencyInService service;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbc;

    private Integer id;
    private String originalNameEn;

    @BeforeEach
    void pickARow() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT TOP 1 STUDY_CURRENCY_IN_ID AS id, STUDY_CURRENCY_IN_NAME_EN AS name_en "
                        + "FROM MOHECORE.STUDY_CURRENCY_IN ORDER BY STUDY_CURRENCY_IN_ID");
        assumeTrue(!rows.isEmpty(), "no STUDY_CURRENCY_IN rows to update");
        id = ((Number) rows.get(0).get("id")).intValue();
        originalNameEn = (String) rows.get(0).get("name_en");
    }

    @AfterEach
    void restore() {
        if (id != null) {
            jdbc.update("UPDATE MOHECORE.STUDY_CURRENCY_IN SET STUDY_CURRENCY_IN_NAME_EN = ? WHERE STUDY_CURRENCY_IN_ID = ?",
                    originalNameEn, id);
        }
    }

    @Test
    @DisplayName("an update through a read-only-classed lookup service is committed and visible to a fresh reader")
    void updateSurvivesReRead() {
        String marker = (originalNameEn == null ? "" : originalNameEn) + " [tx " + System.nanoTime() % 100_000 + "]";

        StudyCurrencyIn row = service.getById(id);
        row.setStudyCurrencyInNameEn(marker);
        StudyCurrencyIn returned = service.update(id, row);
        assertThat(returned.getStudyCurrencyInNameEn())
                .as("the returned object always carried the change; the table is what matters")
                .isEqualTo(marker);

        EntityManager fresh = entityManagerFactory.createEntityManager();
        try {
            StudyCurrencyIn reread = fresh.find(StudyCurrencyIn.class, id);
            assertThat(reread.getStudyCurrencyInNameEn()).as("fresh persistence context").isEqualTo(marker);
        } finally {
            fresh.close();
        }
        assertThat(jdbc.queryForObject(SELECT_NAME_EN, String.class, id)).as("plain JDBC").isEqualTo(marker);
    }
}
