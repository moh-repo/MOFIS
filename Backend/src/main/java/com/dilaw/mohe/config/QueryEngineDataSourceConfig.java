package com.dilaw.mohe.config;

import com.dilaw.mohe.config.tenant.TenantContextDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * The connection the query builder's composed statements run on.
 *
 * <p>The application's own connection is a member of {@code db_owner}. The query
 * engine validates every identifier against the schema catalogue and binds every
 * value, and it composes only SELECT — but those are application-level controls,
 * and a gap in them would be a gap that can write.
 *
 * <p>Setting {@code mohe.query-engine.datasource.url} (with username and
 * password) points this bean at a separate login. Granted only
 * {@code db_datareader}, it turns the worst case from a changed row into a failed
 * query. {@code V025__create_query_engine_readonly_login.sql} creates one.
 *
 * <p>Left unset, the engine shares the application's connection and logs that it
 * is doing so at startup — the feature works either way, but the boundary is
 * absent, and it should say so rather than look the same.
 */
@Configuration
public class QueryEngineDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(QueryEngineDataSourceConfig.class);

    @Bean("queryEngineJdbcTemplate")
    public JdbcTemplate queryEngineJdbcTemplate(
            @Qualifier("dataSource") DataSource primaryDataSource,
            @Value("${mohe.query-engine.datasource.url:}") String url,
            @Value("${mohe.query-engine.datasource.username:}") String username,
            @Value("${mohe.query-engine.datasource.password:}") String password) {

        if (url == null || url.isBlank()) {
            log.warn("Query builder is using the application's database connection. "
                    + "Composed queries will run with the application's privileges; set "
                    + "mohe.query-engine.datasource.* to a db_datareader login to bound them.");
            return new JdbcTemplate(primaryDataSource);
        }

        HikariDataSource readOnly = new HikariDataSource();
        readOnly.setJdbcUrl(url);
        readOnly.setUsername(username);
        readOnly.setPassword(password);
        readOnly.setReadOnly(true);
        readOnly.setPoolName("query-engine-ro");
        // Small: this pool serves interactive, admin-only queries, and should not
        // be able to crowd out the application's own connections.
        readOnly.setMaximumPoolSize(4);
        readOnly.setAutoCommit(true);
        log.info("Query builder is using a dedicated read-only connection ({})", username);
        // Wrapped like the primary datasource so Row-Level Security applies here
        // too. This matters most on this pool: the query builder composes SQL
        // from user input, so it is the one place where the database enforcing
        // tenancy itself — rather than the application remembering to — is doing
        // the real work.
        return new JdbcTemplate(new TenantContextDataSource(readOnly));
    }
}
