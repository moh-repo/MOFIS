package com.dilaw.mohe.config;

import com.dilaw.mohe.config.tenant.TenantContextDataSource;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = { "com.dilaw.mohe.repository",
        "com.dilaw.mohe.ums.repository",
        "com.dilaw.mohe.approval.repository",
        "com.dilaw.mohe.audit",
        "com.dilaw.mohe.systemsettings.repository",
        "com.dilaw.mohe.wafar.repository",
        "com.dilaw.mohe.studentportal.repository",
        "com.dilaw.mohe.notification.repository",
        "com.dilaw.mohe.alerteventcases.repository",
        // FINCORE: chart of accounts, fiscal calendar, fund chain. Both this list
        // and setPackagesToScan below are allow-lists, so a new module is invisible
        // to Spring until it appears in each — the failure is a missing-bean error
        // at context start, not at build time.
        "com.dilaw.mohe.accounting.repository" }, entityManagerFactoryRef = "entityManagerFactory", transactionManagerRef = "transactionManager")
public class PrimaryDataSourceConfig {

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.jpa.hibernate.ddl-auto:none}")
    private String ddlAuto;

    /*
     * SQL echo. Bound from the same properties Spring Boot's own JPA
     * auto-configuration would read, because this class builds the
     * EntityManagerFactory by hand and Boot's binding does not reach it: until
     * August 2026 (revision item 1.9) both were hard-coded to true here, which
     * silently overrode the
     * spring.jpa.show-sql=false in application.properties and printed every
     * statement to stdout in production. Hibernate 6 writes show_sql straight to
     * System.out, bypassing the logging framework, so no logging level could
     * quieten it. Default false; the dev profile turns it on.
     */
    @Value("${spring.jpa.show-sql:false}")
    private boolean showSql;

    @Value("${spring.jpa.properties.hibernate.format_sql:false}")
    private boolean formatSql;

    /**
     * The application datasource, wrapped so every connection carries the
     * caller's country scope.
     *
     * <p>{@link TenantContextDataSource} writes the scope into SESSION_CONTEXT on
     * borrow and clears it on close, which is what the Row-Level Security
     * policies in V031 filter against. The wrapper is harmless while those
     * policies are STATE = OFF — it only sets two session variables nothing reads
     * yet — so it can be deployed before they are enabled.
     */
    @Primary
    @Bean(name = "dataSource")
    @SuppressWarnings("null")
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        return new TenantContextDataSource(dataSource);
    }

    @Primary
    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            @Qualifier("dataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.dilaw.mohe.model", "com.dilaw.mohe.ums.model", "com.dilaw.mohe.approval.model", "com.dilaw.mohe.audit", "com.dilaw.mohe.systemsettings.model", "com.dilaw.mohe.wafar.model", "com.dilaw.mohe.studentportal.model", "com.dilaw.mohe.notification.model", "com.dilaw.mohe.alerteventcases.model", "com.dilaw.mohe.accounting.model");
        em.setPersistenceUnitName("default");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        properties.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");
        properties.put("hibernate.default_schema", "MOHECORE");
        properties.put("hibernate.show_sql", String.valueOf(showSql));
        properties.put("hibernate.format_sql", String.valueOf(formatSql));
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Primary
    @Bean(name = "transactionManager")
    @SuppressWarnings("null")
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
