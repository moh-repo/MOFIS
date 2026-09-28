package com.dilaw.mohe.scope;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A JDBC template on the DDL login for the few tests that change the
 * database's shape rather than its rows - switching the tenant policy on and
 * off, above all.
 *
 * <p>Since dev runs as the least-privilege {@code mohe_app} login (S-01, V090),
 * the application's own datasource is denied {@code ALTER ANY SECURITY
 * POLICY} on purpose. The migrations' login ({@code spring.flyway.user}) has
 * DDL rights; when it is not configured the application's login is used, as
 * before the switch. Not tenant-aware: nothing here reads tenanted rows.
 */
@Component
public class DdlJdbc {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;

    public DdlJdbc(@Value("${spring.flyway.url:${spring.datasource.url}}") String url,
                   @Value("${spring.flyway.user:${spring.datasource.username}}") String user,
                   @Value("${spring.flyway.password:${spring.datasource.password}}") String password,
                   @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setPoolName("test-ddl");
        dataSource.setMaximumPoolSize(1);
        dataSource.setMinimumIdle(0);
        jdbc = new JdbcTemplate(dataSource);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** {@code ALTER SECURITY POLICY MOHECORE.tenant_country_policy WITH (STATE = ON|OFF)}. */
    public void setTenantPolicy(boolean on) {
        jdbc.execute("ALTER SECURITY POLICY MOHECORE.tenant_country_policy WITH (STATE = " + (on ? "ON" : "OFF") + ")");
    }

    @PreDestroy
    void close() {
        dataSource.close();
    }
}
