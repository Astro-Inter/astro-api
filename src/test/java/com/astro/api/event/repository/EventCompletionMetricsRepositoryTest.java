package com.astro.api.event.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventCompletionMetricsRepositoryTest {
    private static LocalContainerEntityManagerFactoryBean factoryBean;
    private static EntityManagerFactory factory;
    private static EntityManager entityManager;
    private static EventClassParticipantRepository repository;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void open() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:event_completion_metrics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table turma (id_turma bigint primary key, evento_id bigint, data_inicial timestamp, data_termino timestamp)");
        jdbc.execute("create table turma_colaborador (id_turma_colaborador bigint primary key, turma_id bigint, usuario_id bigint)");
        jdbc.execute("create table conclusao_evento (id_conclusao_evento bigint primary key, turma_colaborador_id bigint unique, status varchar(20))");
        factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPackagesToScan("com.astro.api");
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factoryBean.afterPropertiesSet();
        factory = factoryBean.getObject();
        entityManager = factory.createEntityManager();
        repository = new JpaRepositoryFactory(entityManager).getRepository(EventClassParticipantRepository.class);
    }

    @AfterAll
    static void close() {
        if (entityManager != null) entityManager.close();
        if (factoryBean != null) factoryBean.destroy();
    }

    @BeforeEach
    void seed() {
        jdbc.update("delete from conclusao_evento");
        jdbc.update("delete from turma_colaborador");
        jdbc.update("delete from turma");
        jdbc.update("insert into turma values (10, 1, '2020-01-01', '2020-01-02'), (11, 1, '2099-01-01', '2099-01-02'), (12, 2, '2020-01-01', '2020-01-02')");
        jdbc.update("insert into turma_colaborador values (20, 10, 100), (21, 11, 101), (22, 10, 102), (23, 11, 103), (24, 12, 104)");
        jdbc.update("insert into conclusao_evento values (30, 20, 'PENDENTE'), (31, 21, 'CONCLUIDO'), (32, 22, 'REJEITADO'), (33, 24, 'PENDENTE')");
    }

    @Test
    void countsAllClassesAndOnlyRequestedEventWithRejectionsAsValidated() {
        assertMetrics(repository.findCompletionMetricsByEventId(1L), 4, 1, 2, 1);
        assertMetrics(repository.findCompletionMetricsByEventId(2L), 1, 1, 0, 0);
    }

    @Test
    void resubmissionMovesRejectedConclusionBackToPending() {
        jdbc.update("update conclusao_evento set status = 'PENDENTE' where id_conclusao_evento = 32");

        assertMetrics(repository.findCompletionMetricsByEventId(1L), 4, 2, 1, 1);
    }

    @Test
    void returnsZeroCountsForEventWithoutParticipants() {
        assertMetrics(repository.findCompletionMetricsByEventId(3L), 0, 0, 0, 0);
    }

    @Test
    void countsDistinctUsersAcrossClasses() {
        jdbc.update("insert into turma_colaborador values (25, 11, 100)");
        jdbc.update("insert into conclusao_evento values (34, 25, 'PENDENTE')");

        assertMetrics(repository.findCompletionMetricsByEventId(1L), 4, 1, 2, 1);
    }

    private void assertMetrics(EventCompletionMetricsProjection metrics, long participants,
                               long pending, long validated, long notSubmitted) {
        assertEquals(participants, metrics.getParticipantCount());
        assertEquals(pending, metrics.getPendingValidationCount());
        assertEquals(validated, metrics.getValidatedCount());
        assertEquals(notSubmitted, metrics.getNotSubmittedCount());
    }
}
