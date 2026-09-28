package com.dilaw.mohe.architecture;

import com.dilaw.mohe.model.StudentCountry;
import com.dilaw.mohe.model.StudentDatum;
import com.dilaw.mohe.model.StudentFinance;
import com.dilaw.mohe.repository.StudentFinanceRepository;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.repository.Query;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What Hibernate on this version does with {@code StudentDatum}'s
 * {@code @Where(clause = "IS_DELETED = 0 OR IS_DELETED IS NULL")} (FIX-PLAN P5-11,
 * 26 Sep 2026: "verify the @Where behaviour on our Hibernate version").
 *
 * <p>The real SQL is produced by Hibernate itself — the entities, the SQL Server dialect,
 * the JPQL — and recorded by a statement inspector; the JDBC connection underneath is a
 * mock, so no database is needed. The test states what the rendering shows: the
 * restriction is added where StudentDatum is queried as an entity (root, join, subquery),
 * which native SQL and {@code @Subselect} text never get; and the finance sums state their
 * own condition, so they do not depend on the deprecated annotation.
 */
class HibernateWhereRenderingTest {

    private static final List<String> SQL = new ArrayList<>();
    private static SessionFactory factory;

    /** A connection provider that hands out mocks: the SQL is rendered and recorded, never run. */
    public static class MockConnections implements ConnectionProvider {
        @Override
        public Connection getConnection() throws java.sql.SQLException {
            // Plain mocks, not deep stubs: a deep-stubbed SQLWarning chain never ends.
            java.sql.ResultSet empty = Mockito.mock(java.sql.ResultSet.class);
            Mockito.when(empty.next()).thenReturn(false);
            java.sql.PreparedStatement statement = Mockito.mock(java.sql.PreparedStatement.class);
            Mockito.when(statement.executeQuery()).thenReturn(empty);
            Mockito.when(statement.getResultSet()).thenReturn(empty);
            Connection connection = Mockito.mock(Connection.class);
            Mockito.when(connection.prepareStatement(Mockito.anyString())).thenReturn(statement);
            Mockito.when(connection.prepareStatement(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
                    .thenReturn(statement);
            Mockito.when(connection.getAutoCommit()).thenReturn(true);
            return connection;
        }

        @Override
        public void closeConnection(Connection conn) {
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return false;
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            return null;
        }
    }

    @BeforeAll
    static void boot() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.connection.provider_class", new MockConnections())
                .applySetting("hibernate.session_factory.statement_inspector", (StatementInspector) sql -> {
                    SQL.add(sql);
                    return sql;
                })
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        factory = new MetadataSources(registry)
                .addAnnotatedClass(StudentFinance.class)
                .addAnnotatedClass(StudentDatum.class)
                .addAnnotatedClass(StudentCountry.class)
                .buildMetadata()
                .buildSessionFactory();
    }

    @AfterAll
    static void close() {
        if (factory != null) factory.close();
    }

    /** The SQL Hibernate sends for {@code jpql}. */
    private static String render(String jpql) {
        SQL.clear();
        try (Session session = factory.openSession()) {
            session.createQuery(jpql).getResultList();
        }
        assertThat(SQL).as("one statement for " + jpql).isNotEmpty();
        return SQL.get(SQL.size() - 1).toUpperCase(Locale.ROOT);
    }

    private static final String RESTRICTION = "IS_DELETED=0 OR";

    private static String squeeze(String sql) {
        return sql.replace(" ", "");
    }

    @Test
    @DisplayName("Hibernate 6.6: @Where is applied where StudentDatum is the root, a join, or a subquery root")
    void whereIsAppliedToTheEntity() {
        assertThat(org.hibernate.Version.getVersionString()).startsWith("6.6");

        assertThat(squeeze(render("SELECT sd FROM StudentDatum sd"))).as("root").contains(RESTRICTION.replace(" ", ""));
        assertThat(squeeze(render("SELECT sf FROM StudentFinance sf JOIN StudentDatum sd ON sd.studentId = sf.id")))
                .as("entity join").contains(RESTRICTION.replace(" ", ""));
        assertThat(squeeze(render("SELECT f FROM StudentFinance f WHERE f.id IN (SELECT s.studentId FROM StudentDatum s)")))
                .as("subquery root").contains(RESTRICTION.replace(" ", ""));
        assertThat(render("SELECT sf FROM StudentFinance sf")).as("the finance entity has no restriction of its own")
                .doesNotContain("IS_DELETED");
    }

    @Test
    @DisplayName("the finance sums state the live-student condition themselves (P5-11)")
    void financeSumsCarryTheirOwnCondition() throws Exception {
        for (String name : List.of("sumReceivedAmount", "sumReceivedAmountForStudyCountries", "findUnpaidForQuarter")) {
            String jpql = Arrays.stream(StudentFinanceRepository.class.getMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow()
                    .getAnnotation(Query.class).value();
            assertThat(jpql).as(name).contains("isDeleted = false OR");
        }
        String sum = render("SELECT COALESCE(SUM(f.studentFinRecievedAmount), 0L) FROM StudentFinance f WHERE "
                + StudentFinanceRepository.OF_LIVE_STUDENT);
        assertThat(sum).contains("EXISTS").contains("IS_DELETED");
    }
}
