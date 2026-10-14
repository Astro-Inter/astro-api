package com.astro.api.event.repository;

import com.astro.api.event.mapper.EventMapper;
import com.astro.api.event.service.EventService;
import com.astro.api.conformidade.repository.NrCatalogRepository;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MANAGER_TEST_DATABASE_URL", matches = ".+")
class EventPostgresTest {
    static final String SCHEMA = "event_test_" + UUID.randomUUID().toString().replace("-", "");
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    EventService service;

    @BeforeAll static void open() {
        var source = dataSource(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        new ResourceDatabasePopulator(new ClassPathResource("manager-fixture.sql"), new ClassPathResource("event-fixture.sql")).execute(source);
        context = new AnnotationConfigApplicationContext(Config.class);
    }
    @AfterAll static void close() {
        if (context != null) context.close();
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }
    @BeforeEach void seed() {
        service = context.getBean(EventService.class);
        jdbc.execute("TRUNCATE workspace, nr_catalogo CASCADE");
        jdbc.update("INSERT INTO workspace VALUES (1,'Astro','1'),(2,'Outro','2')");
        jdbc.update("INSERT INTO cargo VALUES (1,1,'Eletricista',true),(2,2,'Outro',true)");
        jdbc.update("INSERT INTO unidade VALUES (1,1,'Sede',true),(2,2,'Outra',true)");
        insertUser(1, "GESTOR", "ATIVO", 1);
        insertUser(2, "COLABORADOR", "ATIVO", 1);
        insertUser(3, "GESTOR", "ATIVO", 1);
        insertUser(4, "COLABORADOR", "DESATIVADO", 1);
        insertUser(5, "GESTOR", "ATIVO", 2);
        jdbc.update("INSERT INTO nr_catalogo VALUES (10,'NR 10',12,false)");
        jdbc.update("INSERT INTO cargo_nr VALUES (1,10)");
    }
    static void insertUser(long id, String type, String status, long unit) {
        jdbc.update("INSERT INTO usuario VALUES (?, ?, ?, ?, ?, ?, 'HIBRIDO', ?, '2026-01-01', ?, ?)",
                id, "User " + id, "user" + id + "@example.com", "uid" + id, String.format("%011d", id), type, status, unit, unit);
    }
    long event(long managerId, String status) {
        return jdbc.queryForObject("INSERT INTO evento(gestor_id,titulo,descricao,modo_conclusao,evidencia_obrigatoria,status,data_cancelamento,motivo_cancelamento) VALUES (?,'Evento','Descrição','GESTOR',false,?, CASE WHEN ?='CANCELADO' THEN timestamp '2026-10-14 12:00:00' END, CASE WHEN ?='CANCELADO' THEN 'Cancelado' END) RETURNING id_evento", Long.class, managerId,status,status,status);
    }
    long eventClass(long eventId, String name, String start, String end) {
        return jdbc.queryForObject("INSERT INTO turma(evento_id,nome,data_inicial,data_termino) VALUES (?,?,?::timestamp,?::timestamp) RETURNING id_turma", Long.class,eventId,name,start,end);
    }
    @Test void calendarUsesInclusiveOverlapWorkspaceAndStableOrdering() {
        long own=event(1,"CANCELADO"), other=event(5,"ATIVO");
        long crossesStart=eventClass(own,"A","2026-10-01 08:00","2026-10-02 00:00");
        long atEnd=eventClass(own,"B","2026-10-03 00:00","2026-10-03 01:00");
        long tie=eventClass(own,"C","2026-10-03 00:00","2026-10-03 02:00");
        eventClass(own,"Before","2026-10-01 08:00","2026-10-01 23:59:59");
        eventClass(own,"After","2026-10-03 00:00:01","2026-10-03 01:00");
        eventClass(other,"Other","2026-10-02 08:00","2026-10-02 10:00");
        var rows=service.findAll(LocalDateTime.of(2026,10,2,0,0),LocalDateTime.of(2026,10,3,0,0),"uid1");
        assertEquals(List.of(crossesStart,atEnd,tie),rows.stream().map(com.astro.api.event.dto.response.EventCalendarResponse::eventClassId).toList());
        assertTrue(rows.stream().allMatch(row -> row.status()==com.astro.api.event.model.EventStatus.CANCELADO));
        assertTrue(service.findAll(LocalDateTime.of(2027,1,1,0,0),LocalDateTime.of(2027,2,1,0,0),"uid1").isEmpty());
    }
    @Test void detailLoadsAllClassesOutsideCalendarAndProtectsOtherWorkspace() {
        long own=event(1,"ATIVO"), foreign=event(5,"ATIVO");
        long future=eventClass(own,"Future","2027-01-01 08:00","2027-01-01 09:00");
        eventClass(own,"Started","2026-10-14 12:00","2026-10-14 13:00");
        long participation=jdbc.queryForObject("INSERT INTO turma_funcionario(turma_id,usuario_id) VALUES (?,2) RETURNING id_turma_funcionario",Long.class,future);
        var detail=service.findById(own,"uid1");
        assertEquals(2,detail.eventClasses().size()); assertTrue(detail.canEdit()); assertFalse(detail.detailsEditable());
        assertEquals(participation,detail.eventClasses().getLast().participants().getFirst().participationId());
        assertFalse(service.findById(own,"uid3").canEdit());
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,()->service.findById(foreign,"uid1"));
    }
    @Test void candidatesExcludeInactiveForeignAndCreatorButIncludeOtherManagers() {
        var rows=service.findEligibleCandidates(10,"uid1");
        assertEquals(List.of(2L,3L),rows.stream().map(com.astro.api.event.dto.response.EventCandidateResponse::id).toList());
        assertTrue(rows.stream().allMatch(row -> row.unitId()==1L && row.workModel()==com.astro.api.user.model.WorkModel.HIBRIDO));
        jdbc.update("DELETE FROM cargo_nr"); assertTrue(service.findEligibleCandidates(10,"uid1").isEmpty());
        assertEquals(2,service.findEligibleCandidates(null,"uid1").size());
        jdbc.update("UPDATE usuario SET modalidade=null WHERE id_usuario=2");
        assertNull(service.findEligibleCandidates(null,"uid1").getFirst().workModel());
    }
    static DriverManagerDataSource dataSource() {
        String url=System.getenv("MANAGER_TEST_DATABASE_URL");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/astro525")) throw new IllegalArgumentException("Local disposable astro525 database required");
        var source=new DriverManagerDataSource(url,System.getenv().getOrDefault("MANAGER_TEST_DATABASE_USER","postgres"),System.getenv().getOrDefault("MANAGER_TEST_DATABASE_PASSWORD","astro525-isolated"));
        Properties properties=new Properties(); properties.setProperty("currentSchema",SCHEMA); source.setConnectionProperties(properties); return source;
    }
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={EventRepository.class,UserRepository.class,RoleRepository.class,NrCatalogRepository.class},
            excludeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=com.astro.api.conformidade.repository.NrDocumentRepository.class))
    static class Config {
        @Bean DataSource source() { return dataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);factory.setPackagesToScan("com.astro.api");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none"));return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean EventMapper mapper() { return new EventMapper(); }
        @Bean EventService service(EventRepository events,EventClassRepository classes,EventClassParticipantRepository participants,
                                   UserRepository users,RoleRepository roles,NrCatalogRepository nrs,EventMapper mapper,EventCandidateRepository candidates) {
            return new EventService(events,classes,participants,users,roles,nrs,mapper,Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"),ZoneOffset.UTC),candidates);
        }
    }
}
