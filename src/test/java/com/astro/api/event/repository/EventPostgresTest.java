package com.astro.api.event.repository;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.service.ViewedService;
import com.astro.api.common.storage.R2StorageService;
import com.astro.api.conformity.repository.ConformityRepository;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.conformity.repository.NrDocumentRepository;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventClassRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventClassRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventClassResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.mapper.EventMapper;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.service.EventService;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MANAGER_TEST_DATABASE_URL", matches = ".+")
class EventPostgresTest {
    static final String SCHEMA = "event_test_" + UUID.randomUUID().toString().replace("-", "");
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    EventService service;

    @BeforeAll static void open() throws IOException {
        String functionScriptPath = System.getenv("EVENT_CANDIDATES_SQL_PATH");
        if (functionScriptPath == null || functionScriptPath.isBlank()) {
            throw new IllegalStateException("Set EVENT_CANDIDATES_SQL_PATH to the official banco-de-dados function script before enabling PostgreSQL event tests");
        }
        String candidateFunction = Files.readString(
                Path.of(functionScriptPath), StandardCharsets.UTF_8);
        DriverManagerDataSource source = dataSource(); jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);
        new ResourceDatabasePopulator(new ClassPathResource("manager-fixture.sql"), new ClassPathResource("event-fixture.sql")).execute(source);
        // Let the PostgreSQL driver parse dollar-quoted function bodies.
        jdbc.execute(candidateFunction);
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
        List<EventCalendarResponse> rows=service.findAll(LocalDateTime.of(2026,10,2,0,0),LocalDateTime.of(2026,10,3,0,0),"uid1");
        assertEquals(List.of(crossesStart,atEnd,tie),rows.stream().map(EventCalendarResponse::eventClassId).toList());
        assertTrue(rows.stream().allMatch(row -> row.status()==EventStatus.CANCELADO));
        assertTrue(service.findAll(LocalDateTime.of(2027,1,1,0,0),LocalDateTime.of(2027,2,1,0,0),"uid1").isEmpty());
    }
    @Test void detailLoadsAllClassesOutsideCalendarAndProtectsOtherWorkspace() {
        long own=event(1,"ATIVO"), foreign=event(5,"ATIVO");
        long future=eventClass(own,"Future","2027-01-01 08:00","2027-01-01 09:00");
        eventClass(own,"Started","2026-10-14 12:00","2026-10-14 13:00");
        long participation=jdbc.queryForObject("INSERT INTO turma_funcionario(turma_id,usuario_id) VALUES (?,2) RETURNING id_turma_funcionario",Long.class,future);
        EventResponse detail=service.findById(own,"uid1");
        assertEquals(2,detail.eventClasses().size()); assertTrue(detail.canEdit()); assertFalse(detail.detailsEditable());
        assertEquals(participation,detail.eventClasses().getLast().participants().getFirst().participationId());
        assertFalse(service.findById(own,"uid3").canEdit());
        assertThrows(ResourceNotFoundException.class,()->service.findById(foreign,"uid1"));
    }
    @Test void candidatesExcludeInactiveForeignAndCreatorButIncludeOtherManagers() {
        List<EventCandidateResponse> rows=service.findEligibleCandidates(10,"uid1");
        assertEquals(List.of(2L,3L),rows.stream().map(EventCandidateResponse::id).toList());
        assertTrue(rows.stream().allMatch(row -> row.unitId()==1L && row.workModel()==WorkModel.HIBRIDO));
        jdbc.update("DELETE FROM cargo_nr"); assertTrue(service.findEligibleCandidates(10,"uid1").isEmpty());
        assertEquals(2,service.findEligibleCandidates(null,"uid1").size());
        jdbc.update("UPDATE usuario SET modalidade=null WHERE id_usuario=2");
        assertNull(service.findEligibleCandidates(null,"uid1").getFirst().workModel());
    }
    @Test void candidateFunctionRechecksCallerScopeAndDoesNotDuplicateNrAssociations() {
        jdbc.update("INSERT INTO nr_catalogo VALUES (35,'NR 35',12,false)");
        jdbc.update("INSERT INTO cargo_nr VALUES (1,35)");
        List<EventCandidateResponse> rows=service.findEligibleCandidates(null,"uid1");
        assertEquals(List.of(2L,3L),rows.stream().map(EventCandidateResponse::id).toList());
        assertEquals(rows,service.findEligibleCandidates(35,"uid1"));
        jdbc.update("UPDATE usuario SET nome='User 2' WHERE id_usuario=3");
        assertEquals(List.of(2L,3L),service.findEligibleCandidates(null,"uid1").stream().map(EventCandidateResponse::id).toList());
        for(String uid:Arrays.asList(null,""," ","missing","uid2","uid4")) {
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM fn_seleciona_candidatos_evento(?,NULL)",Integer.class,uid));
        }
        jdbc.update("UPDATE usuario SET status='DESATIVADO' WHERE id_usuario=1");
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM fn_seleciona_candidatos_evento('uid1',NULL)",Integer.class));
        jdbc.update("UPDATE usuario SET status='ATIVO',tipo='GESTOR_WORKSPACE' WHERE id_usuario=1");
        assertEquals(2,service.findEligibleCandidates(null,"uid1").size());
        assertTrue(service.findEligibleCandidates(null,"uid5").isEmpty());
    }
    CreateEventRequest createRequest(List<CreateEventClassRequest> classes) {
        return new CreateEventRequest("Evento","Descrição",10,"https://example.com",CompletionMode.GESTOR,true,classes);
    }
    CreateEventClassRequest createClass(String name,long userId) {
        return new CreateEventClassRequest(name,LocalDateTime.of(2026,10,15,8,0),LocalDateTime.of(2026,10,15,9,0),List.of(userId));
    }
    @Test void creationPersistsTheWholeAggregateAndDeterminesCreator() {
        EventResponse response=service.create(createRequest(List.of(createClass("A",2),createClass("B",3))),"uid1");
        assertEquals(1L,response.managerId());assertEquals(10,response.nrId());assertTrue(response.mandatoryEvidence());
        assertEquals(2,response.eventClasses().size());
        assertTrue(response.eventClasses().stream().allMatch(row->row.participants().getFirst().participationId()!=null));
        assertEquals(response,service.findById(response.id(),"uid1"));
    }
    @Test void creationRollsBackEarlierInsertsWhenParticipantPersistenceFails() {
        jdbc.execute("ALTER TABLE turma_funcionario ADD CONSTRAINT test_failure CHECK(usuario_id<>3)");
        try {
            assertThrows(DataIntegrityViolationException.class,()->service.create(createRequest(List.of(createClass("A",2),createClass("B",3))),"uid1"));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM evento",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM turma",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM turma_funcionario",Integer.class));
        } finally { jdbc.execute("ALTER TABLE turma_funcionario DROP CONSTRAINT test_failure"); }
    }
    @Test void creationRejectsForeignWorkspaceAndPartiallyOverlappingParticipants() {
        assertThrows(ResourceNotFoundException.class,()->service.create(createRequest(List.of(createClass("A",5))),"uid1"));
        LocalDateTime start=LocalDateTime.of(2026,10,15,8,0);
        CreateEventClassRequest a=new CreateEventClassRequest("A",start,start.plusHours(1),List.of(2L,3L));
        CreateEventClassRequest b=new CreateEventClassRequest("B",start,start.plusHours(1),List.of(3L,4L));
        assertThrows(BusinessException.class,()->service.create(createRequest(List.of(a,b)),"uid1"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM evento",Integer.class));
    }
    UpdateEventClassRequest updateClass(long id,String name,List<Long> participants) {
        return new UpdateEventClassRequest(id,name,LocalDateTime.of(2026,10,15,8,0),LocalDateTime.of(2026,10,15,9,0),participants);
    }
    UpdateEventRequest updateRequest(String title,List<UpdateEventClassRequest> classes) {
        return new UpdateEventRequest(title,"Descrição","https://example.com",classes);
    }
    @Test void updateRetainsParticipationIdentityConclusionsEvidenceAndImmutableConfiguration() {
        EventResponse created=service.create(createRequest(List.of(createClass("A",2))),"uid1");
        long classId=created.eventClasses().getFirst().id();long participation=created.eventClasses().getFirst().participants().getFirst().participationId();
        long conclusion=jdbc.queryForObject("INSERT INTO conclusao_evento(turma_funcionario_id,status) VALUES (?,'CONCLUIDO') RETURNING id_conclusao_evento",Long.class,participation);
        jdbc.update("INSERT INTO evidencia(conclusao_evento_id,caminho_objeto) VALUES (?,'example.pdf')",conclusion);
        EventResponse updated=service.update(created.id(),updateRequest("Editado",List.of(updateClass(classId,"Renomeado",List.of(2L)))),"uid1");
        assertEquals(participation,updated.eventClasses().getFirst().participants().getFirst().participationId());
        assertEquals(created.nrId(),updated.nrId());assertEquals(created.completionMode(),updated.completionMode());
        assertEquals(created.mandatoryEvidence(),updated.mandatoryEvidence());assertEquals(created.managerId(),updated.managerId());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM conclusao_evento",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM evidencia",Integer.class));
        assertThrows(BusinessException.class,()->service.update(created.id(),updateRequest("Outro",List.of(updateClass(classId,"Renomeado",List.of(3L)))),"uid1"));
        assertEquals("Editado",service.findById(created.id(),"uid1").title());
    }
    @Test void startedClassesAndMetadataRemainLockedWhileFutureClassesCanChange() {
        EventResponse created=service.create(createRequest(List.of(createClass("A",2),createClass("B",3))),"uid1");
        long started=created.eventClasses().getFirst().id(),future=created.eventClasses().getLast().id();
        jdbc.update("UPDATE turma SET data_inicial='2026-10-14 12:00',data_termino='2026-10-14 13:00' WHERE id_turma=?",started);
        jdbc.update("UPDATE usuario SET status='DESATIVADO' WHERE id_usuario=2");
        UpdateEventClassRequest historical=new UpdateEventClassRequest(started,"A",LocalDateTime.of(2026,10,14,12,0),LocalDateTime.of(2026,10,14,13,0),List.of(2L));
        List<UpdateEventClassRequest> desired=List.of(historical,updateClass(future,"Futura",List.of(3L)));
        EventResponse updated=service.update(created.id(),updateRequest("Evento",desired),"uid1");
        assertEquals("Futura",updated.eventClasses().getLast().name());assertFalse(updated.detailsEditable());
        assertEquals(created.eventClasses().getFirst().participants().getFirst().participationId(),updated.eventClasses().getFirst().participants().getFirst().participationId());
        assertThrows(BusinessException.class,()->service.update(created.id(),updateRequest("Outro",desired),"uid1"));
        assertThrows(BusinessException.class,()->service.update(created.id(),updateRequest("Evento",List.of(updateClass(future,"Futura",List.of(3L)))),"uid1"));
        assertThrows(BusinessException.class,()->service.update(created.id(),updateRequest("Evento",List.of(updateClass(started,"A",List.of(2L)),updateClass(future,"Futura",List.of(3L)))),"uid1"));
    }
    @Test void updateRejectsPartialOverlapDuplicateIdsAndClassesFromOtherEventsAtomically() {
        EventResponse created=service.create(createRequest(List.of(createClass("A",2),createClass("B",3))),"uid1");
        long first=created.eventClasses().getFirst().id(),second=created.eventClasses().getLast().id();
        EventResponse before=service.findById(created.id(),"uid1");
        List<UpdateEventClassRequest> overlap=List.of(updateClass(first,"Changed A",List.of(2L,3L)),updateClass(second,"Changed B",List.of(3L,4L)));
        assertThrows(BusinessException.class,()->service.update(created.id(),updateRequest("Outro",overlap),"uid1"));
        assertThrows(BusinessException.class,()->service.update(created.id(),updateRequest("Outro",List.of(updateClass(first,"A",List.of(2L)),updateClass(first,"B",List.of(3L)))),"uid1"));
        long other=event(1,"ATIVO"),otherClass=eventClass(other,"Other","2026-10-15 08:00","2026-10-15 09:00");
        assertThrows(ResourceNotFoundException.class,()->service.update(created.id(),updateRequest("Outro",List.of(updateClass(otherClass,"Other",List.of(2L)))),"uid1"));
        assertEquals(before,service.findById(created.id(),"uid1"));
    }
    @Test void swappingNamesAndReplacingOmittedFutureClassHonorImmediateUniqueConstraints() {
        EventResponse created=service.create(createRequest(List.of(createClass("A",2),createClass("B",3))),"uid1");
        long first=created.eventClasses().getFirst().id(),second=created.eventClasses().getLast().id();
        EventResponse swapped=service.update(created.id(),updateRequest("Evento",List.of(updateClass(first,"B",List.of(2L)),updateClass(second,"A",List.of(3L)))),"uid1");
        assertEquals(List.of("B","A"),swapped.eventClasses().stream().map(EventClassResponse::name).toList());
        UpdateEventClassRequest added=new UpdateEventClassRequest(null,"A",LocalDateTime.of(2026,10,16,8,0),LocalDateTime.of(2026,10,16,9,0),List.of(3L));
        EventResponse replaced=service.update(created.id(),updateRequest("Evento",List.of(updateClass(first,"B",List.of(2L)),added)),"uid1");
        assertNotEquals(second,replaced.eventClasses().getLast().id());
        assertEquals(created.eventClasses().getFirst().participants().getFirst().participationId(),replaced.eventClasses().getFirst().participants().getFirst().participationId());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM turma",Integer.class));assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM turma_funcionario",Integer.class));
    }
    @Test void failedUpdateRollsBackMetadataScheduleDeletionAndInsertion() {
        EventResponse created=service.create(createRequest(List.of(createClass("A",2))),"uid1");
        EventResponse before=service.findById(created.id(),"uid1");
        jdbc.execute("ALTER TABLE turma_funcionario ADD CONSTRAINT test_failure CHECK(usuario_id<>3)");
        try {
            assertThrows(DataIntegrityViolationException.class,()->service.update(created.id(),updateRequest("Outro",List.of(updateClass(created.eventClasses().getFirst().id(),"Novo",List.of(3L)))),"uid1"));
            assertEquals(before,service.findById(created.id(),"uid1"));
        } finally {jdbc.execute("ALTER TABLE turma_funcionario DROP CONSTRAINT test_failure");}
    }
    @Test void onlyActiveCreatorMayUpdateWithinWorkspace() {
        for (String status:List.of("ATIVO","CANCELADO","CONCLUIDO")) {
            long id=event(1,status);
            UpdateEventRequest body=new UpdateEventRequest("Evento","Descrição",null,List.of(new UpdateEventClassRequest(null,"A",LocalDateTime.of(2026,10,15,8,0),LocalDateTime.of(2026,10,15,9,0),List.of(2L))));
            assertThrows(AccessDeniedException.class,()->service.update(id,body,"uid3"));
            assertThrows(ResourceNotFoundException.class,()->service.update(id,body,"uid5"));
            if(!status.equals("ATIVO")) assertThrows(BusinessException.class,()->service.update(id,body,"uid1"));
        }
    }
    @Test void concurrentUpdatesSerializeTheAggregateAndKeepParticipationIds() throws Exception {
        EventResponse created=service.create(createRequest(List.of(createClass("A",2))),"uid1");
        CountDownLatch start=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Callable<EventResponse> first=()->{start.await();return service.update(created.id(),updateRequest("Primeiro",List.of(updateClass(created.eventClasses().getFirst().id(),"A",List.of(2L)))),"uid1");};
            Callable<EventResponse> second=()->{start.await();return service.update(created.id(),updateRequest("Segundo",List.of(updateClass(created.eventClasses().getFirst().id(),"A",List.of(2L)))),"uid1");};
            Future<EventResponse> a=executor.submit(first);Future<EventResponse> b=executor.submit(second);start.countDown();
            assertEquals("Primeiro",a.get(20,TimeUnit.SECONDS).title());assertEquals("Segundo",b.get(20,TimeUnit.SECONDS).title());
        }
        EventResponse after=service.findById(created.id(),"uid1");assertTrue(List.of("Primeiro","Segundo").contains(after.title()));
        assertEquals(created.eventClasses().getFirst().participants().getFirst().participationId(),after.eventClasses().getFirst().participants().getFirst().participationId());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM turma_funcionario",Integer.class));
    }
    @Test void cancellationIsDefinitiveAndKeepsAllHistoricalRowsAndIds() {
        EventResponse created=service.create(createRequest(List.of(createClass("A",2))),"uid1");
        long participation=created.eventClasses().getFirst().participants().getFirst().participationId();
        long conclusion=jdbc.queryForObject("INSERT INTO conclusao_evento(turma_funcionario_id,status) VALUES (?,'CONCLUIDO') RETURNING id_conclusao_evento",Long.class,participation);
        jdbc.update("INSERT INTO evidencia(conclusao_evento_id,caminho_objeto) VALUES (?,'example.pdf')",conclusion);
        EventResponse cancelled=service.cancel(created.id(),new CancelEventRequest("  Reagendado  "),"uid1");
        assertEquals("Reagendado",cancelled.cancellationReason());assertEquals(Instant.parse("2026-10-14T12:00:00Z"),cancelled.cancellationDate());
        assertFalse(cancelled.canEdit());assertFalse(cancelled.canCancel());assertFalse(cancelled.detailsEditable());assertFalse(cancelled.eventClasses().getFirst().editable());
        assertEquals(created.eventClasses().getFirst().id(),cancelled.eventClasses().getFirst().id());
        assertEquals(participation,cancelled.eventClasses().getFirst().participants().getFirst().participationId());
        assertEquals(cancelled,service.findById(created.id(),"uid1"));
        assertEquals(EventStatus.CANCELADO,service.findAll(LocalDateTime.of(2026,10,15,0,0),LocalDateTime.of(2026,10,16,0,0),"uid1").getFirst().status());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM conclusao_evento",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM evidencia",Integer.class));
        assertThrows(BusinessException.class,()->service.cancel(created.id(),new CancelEventRequest("Outro motivo"),"uid1"));
    }
    @Test void cancellationRejectsBadReasonsFinalStatesAndOtherCreatorsWithoutChanges() {
        long id=event(1,"ATIVO");
        for(String reason:Arrays.asList(null," "," ab ","x".repeat(1001))) assertThrows(BusinessException.class,()->service.cancel(id,new CancelEventRequest(reason),"uid1"));
        assertEquals(EventStatus.ATIVO,service.findById(id,"uid1").status());
        CancelEventRequest reason=new CancelEventRequest("Motivo válido");
        assertThrows(AccessDeniedException.class,()->service.cancel(id,reason,"uid3"));
        assertThrows(ResourceNotFoundException.class,()->service.cancel(id,reason,"uid5"));
        long completed=event(1,"CONCLUIDO");assertThrows(BusinessException.class,()->service.cancel(completed,reason,"uid1"));
        EventResponse boundary=service.cancel(id,new CancelEventRequest("  "+"x".repeat(1000)+"  "),"uid1");
        assertEquals(1000,boundary.cancellationReason().length());
    }
    @Test void twoConcurrentCancellationsCommitOnlyOneReason() throws Exception {
        long id=event(1,"ATIVO");CountDownLatch ready=new CountDownLatch(2);CountDownLatch start=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> cancel=()->{ready.countDown();assertTrue(start.await(10,TimeUnit.SECONDS));try{service.cancel(id,new CancelEventRequest("Cancelamento"),"uid1");return true;}catch(BusinessException exception){return false;}};
            Future<Boolean> a=executor.submit(cancel);Future<Boolean> b=executor.submit(cancel);assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
        assertEquals(EventStatus.CANCELADO,service.findById(id,"uid1").status());
    }
    @Test void editingFutureClassesPreservesTheExactDescriptionOfHistoricalEvents() {
        EventResponse created=service.create(createRequest(List.of(createClass("A",2),createClass("B",3))),"uid1");
        long started=created.eventClasses().getFirst().id(),future=created.eventClasses().getLast().id();
        jdbc.update("UPDATE evento SET descricao='  Descrição histórica  ' WHERE id_evento=?",created.id());
        jdbc.update("UPDATE turma SET data_inicial='2026-10-14 12:00',data_termino='2026-10-14 13:00' WHERE id_turma=?",started);
        EventResponse historical=service.findById(created.id(),"uid1");
        UpdateEventClassRequest unchanged=new UpdateEventClassRequest(started,"A",LocalDateTime.of(2026,10,14,12,0),LocalDateTime.of(2026,10,14,13,0),List.of(2L));
        UpdateEventRequest body=new UpdateEventRequest(historical.title(),historical.description(),historical.externalLink(),List.of(unchanged,updateClass(future,"Futura",List.of(3L))));
        EventResponse updated=service.update(created.id(),body,"uid1");
        assertEquals(historical.description(),updated.description());assertEquals("Futura",updated.eventClasses().getLast().name());
    }
    static DriverManagerDataSource dataSource() {
        String url=System.getenv("MANAGER_TEST_DATABASE_URL");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/astro525")) throw new IllegalArgumentException("Local disposable astro525 database required");
        DriverManagerDataSource source=new DriverManagerDataSource(url,System.getenv().getOrDefault("MANAGER_TEST_DATABASE_USER","postgres"),System.getenv().getOrDefault("MANAGER_TEST_DATABASE_PASSWORD","astro525-isolated"));
        Properties properties=new Properties(); properties.setProperty("currentSchema",SCHEMA); source.setConnectionProperties(properties); return source;
    }
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses={EventRepository.class,UserRepository.class,RoleRepository.class,NrCatalogRepository.class},
            excludeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=NrDocumentRepository.class))
    static class Config {
        @Bean DataSource source() { return dataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            LocalContainerEntityManagerFactoryBean factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);factory.setPackagesToScan("com.astro.api");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none"));return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean EventMapper mapper() { return new EventMapper(); }
        @Bean EventService service(EventRepository events,EventClassRepository classes,EventClassParticipantRepository participants,
                                   UserRepository users,RoleRepository roles,NrCatalogRepository nrs,EventMapper mapper,EventCandidateRepository candidates,
                                   EventCompletionRepository completions,EventEvidenceRepository evidence,
                                   ConformityRepository conformities) {
            return new EventService(events,classes,participants,users,roles,nrs,mapper,Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"),ZoneOffset.UTC),candidates,Mockito.mock(ViewedService.class),
                    completions,evidence,Mockito.mock(R2StorageService.class),conformities);
        }
    }
}
