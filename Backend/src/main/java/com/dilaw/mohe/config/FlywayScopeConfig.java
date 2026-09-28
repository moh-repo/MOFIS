package com.dilaw.mohe.config;

import com.dilaw.mohe.config.tenant.TenantContext;
import com.dilaw.mohe.config.tenant.TenantContextDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Runs the Flyway migration under the system tenant scope (security plan O-29).
 *
 * <p>{@code TenantContextDataSource} stamps every borrowed connection with the
 * thread's scope, and at startup that is {@code DENY}. A migration is by nature
 * about every country's rows - a backfill, a re-created view, a new column with
 * a default - so under {@code DENY} each RLS-guarded {@code UPDATE} in a script
 * would touch no rows and report success. The strategy is otherwise Flyway's
 * default: migrate on startup, validate first.
 *
 * <p>Where the application runs as the least-privilege {@code mohe_app} login
 * (S-01, V090) the migrations need a login with DDL rights: set
 * {@code SPRING_FLYWAY_USER} / {@code SPRING_FLYWAY_PASSWORD} (and
 * {@code SPRING_FLYWAY_URL} when it differs from the application's). The
 * datasource built here for them is wrapped the same way as the application's,
 * so the scope above reaches the migration's connection too; Spring Boot's own
 * derived datasource would not be, and the row-level security policy would
 * then hide every tenanted row from a backfill.
 */
@Configuration
public class FlywayScopeConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        // Schema migrations run before any request exists and on nobody's behalf;
        // they must see and change every country's rows, which is what the
        // system scope is for (TenantContext names migrations as a caller).
        return flyway -> TenantContext.runAsSystem(() -> flyway.migrate());
    }

    @Bean
    @FlywayDataSource
    @ConditionalOnProperty(name = "spring.flyway.user")
    public DataSource flywayDataSource(@Value("${spring.flyway.url:${spring.datasource.url}}") String url,
                                       @Value("${spring.flyway.user}") String user,
                                       @Value("${spring.flyway.password:}") String password,
                                       @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        ds.setPoolName("flyway");
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return new TenantContextDataSource(ds);
    }
}
