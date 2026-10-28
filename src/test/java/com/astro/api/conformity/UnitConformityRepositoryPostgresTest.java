package com.astro.api.conformity;

import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.repository.UnitConformityRepository;
import jakarta.persistence.EntityManager;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "UNIT_TEST_JDBC_URL",
        matches = "jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")
class UnitConformityRepositoryPostgresTest {
    private static final String SCHEMA = "conformity_exists_test_" + UUID.randomUUID().toString().replace("-", "");
    private static DriverManagerDataSource dataSource;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManager entityManager;
    private static UnitConformityRepository repository;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void open() {
        String databaseUrl = System.getenv("UNIT_TEST_JDBC_URL");
        if (!databaseUrl.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/forms_recipients_test")) {
            throw new IllegalArgumentException("Repository tests require the local disposable PostgreSQL database");
        }
        dataSource = new DriverManagerDataSource(databaseUrl, "postgres", "forms-recipients-isolated");
        Properties connection = new Properties();
        connection.setProperty("currentSchema", SCHEMA);
        dataSource.setConnectionProperties(connection);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        jdbc.execute("""
                CREATE TABLE conformidade (
                    id_conformidade BIGSERIAL PRIMARY KEY, tipo VARCHAR(20), unidade_id BIGINT,
                    formulario_resposta_id VARCHAR(100), usuario_id BIGINT, nr_id INTEGER,
                    aplicavel BOOLEAN, data_validade DATE, origem VARCHAR(50), conclusao_evento_id BIGINT
                )
                """);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(Conformity.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.afterPropertiesSet();
        assertNotNull(factory.getObject());
        entityManager = factory.getObject().createEntityManager();
        repository = new JpaRepositoryFactory(entityManager).getRepository(UnitConformityRepository.class);
    }

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE conformidade RESTART IDENTITY");
        jdbc.update("""
                INSERT INTO conformidade (tipo,unidade_id,usuario_id,nr_id,aplicavel,origem,formulario_resposta_id)
                VALUES ('UNIDADE',2,NULL,1,TRUE,'FORMULARIO','approved-form'),
                       ('UNIDADE',2,NULL,6,FALSE,'MANUAL',NULL),
                       ('UNIDADE',3,NULL,1,TRUE,'MANUAL',NULL),
                       ('COLABORADOR',NULL,2,1,TRUE,'MANUAL',NULL)
                """);
    }

    @AfterAll
    static void close() {
        try {
            if (entityManager != null) entityManager.close();
        } finally {
            try {
                if (factory != null) factory.destroy();
            } finally {
                if (dataSource != null) new JdbcTemplate(dataSource).execute("DROP SCHEMA " + SCHEMA + " CASCADE");
            }
        }
    }

    @Test
    void creationChecksOnlyTheApplicableUnitAndNr() {
        assertTrue(repository.existsByTypeAndUnitIdAndNrIdAndApplicableTrue("UNIDADE",2L,1));
        assertFalse(repository.existsByTypeAndUnitIdAndNrIdAndApplicableTrue("UNIDADE",2L,6));
        assertFalse(repository.existsByTypeAndUnitIdAndNrIdAndApplicableTrue("UNIDADE",4L,1));
        assertFalse(repository.existsByTypeAndUnitIdAndNrIdAndApplicableTrue("COLABORADOR",2L,1));
    }

    @Test
    void editingExcludesOnlyItselfAndStillDetectsAnotherApplicableRecord() {
        assertFalse(repository.existsByTypeAndUnitIdAndNrIdAndApplicableTrueAndIdNot("UNIDADE",2L,1,1L));
        assertTrue(repository.existsByTypeAndUnitIdAndNrIdAndApplicableTrueAndIdNot("UNIDADE",2L,1,99L));
        jdbc.update("INSERT INTO conformidade(tipo,unidade_id,nr_id,aplicavel,origem) VALUES('UNIDADE',2,1,TRUE,'MANUAL')");
        assertTrue(repository.existsByTypeAndUnitIdAndNrIdAndApplicableTrueAndIdNot("UNIDADE",2L,1,1L));
    }

    @Test
    void formExistenceRemainsTrueWhenItsHistoricalRecordIsNotApplicable() {
        assertTrue(repository.existsByFormResponseId("approved-form"));
        assertFalse(repository.existsByFormResponseId("missing-form"));
        jdbc.update("UPDATE conformidade SET aplicavel=FALSE WHERE formulario_resposta_id='approved-form'");
        assertTrue(repository.existsByFormResponseId("approved-form"));
    }
}
