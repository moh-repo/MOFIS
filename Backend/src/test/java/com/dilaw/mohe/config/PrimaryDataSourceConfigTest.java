package com.dilaw.mohe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The SQL echo is a property, not a constant.
 *
 * <p>Revision item 1.9: {@code PrimaryDataSourceConfig} builds the
 * EntityManagerFactory itself, and until August 2026 it put
 * {@code hibernate.show_sql=true} / {@code format_sql=true} into it regardless of
 * {@code spring.jpa.show-sql=false} in {@code application.properties} — Hibernate 6
 * prints that straight to stdout, past the logging framework, so production wrote
 * every statement to the console. The factory is built here without a database
 * (nothing is initialised) and its property map inspected.
 */
class PrimaryDataSourceConfigTest {

    private static Map<String, Object> jpaProperties(boolean showSql, boolean formatSql) {
        PrimaryDataSourceConfig config = new PrimaryDataSourceConfig();
        ReflectionTestUtils.setField(config, "ddlAuto", "none");
        ReflectionTestUtils.setField(config, "showSql", showSql);
        ReflectionTestUtils.setField(config, "formatSql", formatSql);
        LocalContainerEntityManagerFactoryBean em = config.entityManagerFactory(mock(DataSource.class));
        return em.getJpaPropertyMap();
    }

    private static Properties load(String resource) throws IOException {
        Properties p = new Properties();
        try (InputStream in = PrimaryDataSourceConfigTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            p.load(in);
        }
        return p;
    }

    @Test
    @DisplayName("show_sql and format_sql are whatever the properties say — off by default")
    void boundFromProperties() {
        Map<String, Object> off = jpaProperties(false, false);
        assertThat(off).containsEntry("hibernate.show_sql", "false").containsEntry("hibernate.format_sql", "false");

        Map<String, Object> on = jpaProperties(true, true);
        assertThat(on).containsEntry("hibernate.show_sql", "true").containsEntry("hibernate.format_sql", "true");
    }

    @Test
    @DisplayName("the base properties keep the echo off and the dev profile keeps it on, as they were")
    void propertyFilesUnchanged() throws IOException {
        assertThat(load("/application.properties").getProperty("spring.jpa.show-sql")).isEqualTo("false");
        Properties dev = load("/application-dev.properties");
        assertThat(dev.getProperty("spring.jpa.show-sql")).isEqualTo("true");
        assertThat(dev.getProperty("spring.jpa.properties.hibernate.format_sql")).isEqualTo("true");
    }
}
