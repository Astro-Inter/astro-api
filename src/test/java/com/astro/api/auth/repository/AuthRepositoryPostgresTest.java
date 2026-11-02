package com.astro.api.auth.repository;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FORM_TEST_JDBC_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")
class AuthRepositoryPostgresTest {
    @Test
    void callsVoidFunctionThroughJpaAndCommitsOnlyOneDailyAccess() {
        String schema = "daily_access_test_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv("FORM_TEST_JDBC_URL"), "postgres", "forms-recipients-isolated");
        Properties properties = new Properties();
        properties.setProperty("currentSchema", schema);
        dataSource.setConnectionProperties(properties);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + schema);
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        try {
            jdbc.execute("CREATE TABLE acesso (data DATE NOT NULL, firebase_uid VARCHAR(128) NOT NULL, PRIMARY KEY (data, firebase_uid))");
            jdbc.execute("""
                    CREATE FUNCTION fn_registrar_acesso_diario(p_firebase_uid VARCHAR) RETURNS VOID
                    LANGUAGE plpgsql AS $$ BEGIN
                        INSERT INTO acesso VALUES (CURRENT_DATE, p_firebase_uid)
                        ON CONFLICT (data, firebase_uid) DO NOTHING;
                    END; $$
                    """);
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.astro.api.user.model", "com.astro.api.unit.model",
                    "com.astro.api.workspace.model", "com.astro.api.role.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.default_schema", schema));
            factory.afterPropertiesSet();
            EntityManagerFactory entityManagerFactory = factory.getObject();
            assertNotNull(entityManagerFactory);
            AuthRepository repository = new JpaRepositoryFactory(
                    SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory)).getRepository(AuthRepository.class);
            TransactionTemplate transaction = new TransactionTemplate(new JpaTransactionManager(entityManagerFactory));

            transaction.executeWithoutResult(status -> repository.registerDailyAccess("verified-uid"));
            transaction.executeWithoutResult(status -> repository.registerDailyAccess("verified-uid"));
            transaction.executeWithoutResult(status -> repository.registerDailyAccess("admin-uid"));

            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM acesso", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM acesso WHERE firebase_uid = 'verified-uid' AND data = CURRENT_DATE", Integer.class));
        } finally {
            factory.destroy();
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
