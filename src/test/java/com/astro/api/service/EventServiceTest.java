package com.astro.api.service;

import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformidade.repository.NrCatalogRepository;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventClassRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.mapper.EventMapper;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.Event;
import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.repository.EventClassParticipantRepository;
import com.astro.api.event.repository.EventClassRepository;
import com.astro.api.event.repository.EventRepository;
import com.astro.api.event.service.EventService;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventServiceTest {
    private EventRepository eventRepository;
    private EventClassRepository eventClassRepository;
    private EventClassParticipantRepository eventClassParticipantRepository;
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private NrCatalogRepository nrCatalogRepository;
    private EventService service;
    private com.astro.api.event.repository.EventCandidateRepository candidateRepository;
    private User manager;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        eventClassRepository = mock(EventClassRepository.class);
        eventClassParticipantRepository = mock(EventClassParticipantRepository.class);
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        nrCatalogRepository = mock(NrCatalogRepository.class);
        candidateRepository = mock(com.astro.api.event.repository.EventCandidateRepository.class);
        service = new EventService(
                eventRepository,
                eventClassRepository,
                eventClassParticipantRepository,
                userRepository,
                roleRepository,
                nrCatalogRepository,
                new EventMapper(), java.time.Clock.fixed(
                        java.time.Instant.parse("2026-10-14T12:00:00Z"), java.time.ZoneOffset.UTC),
                candidateRepository);
        workspace = new Workspace();
        workspace.id = 10L;
        manager = createUser(1L, UserStatus.ATIVO, workspace);
        manager.setFirebaseUid("manager");
        manager.setType(com.astro.api.user.model.UserType.GESTOR);
        when(userRepository.findByFirebaseUid("manager")).thenReturn(Optional.of(manager));
    }

    @Test
    void createsEventWithoutNrUsingBatchOperations() {
        User participant = createUser(2L, UserStatus.ATIVO, workspace);
        when(userRepository.findEventParticipantsInWorkspace(any(), eq(workspace))).thenReturn(List.of(participant));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            event.setId(11L);
            return event;
        });
        when(eventClassRepository.saveAll(any())).thenAnswer(invocation -> {
            List<EventClass> eventClasses = invocation.getArgument(0);
            eventClasses.getFirst().setId(21L);
            return eventClasses;
        });
        when(eventClassParticipantRepository.saveAll(any())).thenAnswer(
                invocation -> invocation.getArgument(0));

        var response = service.create(createRequest(null, List.of(2L)), "manager");

        assertEquals(EventStatus.ATIVO, response.status());
        assertEquals(1, response.eventClasses().size());
        verify(userRepository).findEventParticipantsInWorkspace(any(), eq(workspace));
        verify(eventClassRepository).saveAll(any());
        verify(eventClassParticipantRepository).saveAll(any());
    }

    @Test
    void rejectsEventWithoutClasses() {
        CreateEventRequest request = new CreateEventRequest(
                "Event",
                null,
                null,
                null,
                CompletionMode.GESTOR,
                false,
                List.of());

        assertThrows(BusinessException.class, () -> service.create(request, "manager"));
        verifyNoInteractions(eventRepository, eventClassRepository, eventClassParticipantRepository);
    }

    @Test
    void rejectsParticipantInTwoClassesBeforeDatabaseAccess() {
        LocalDateTime startDate = LocalDateTime.now().plusDays(1);
        CreateEventClassRequest firstClass = new CreateEventClassRequest(
                "A",
                startDate,
                startDate.plusHours(1),
                List.of(2L));
        CreateEventClassRequest secondClass = new CreateEventClassRequest(
                "B",
                startDate.plusDays(1),
                startDate.plusDays(1).plusHours(1),
                List.of(2L));
        CreateEventRequest request = new CreateEventRequest(
                "Event",
                null,
                null,
                null,
                CompletionMode.GESTOR,
                false,
                List.of(firstClass, secondClass));

        assertThrows(BusinessException.class, () -> service.create(request, "manager"));
        verify(userRepository, never()).findEventParticipantsInWorkspace(any(), eq(workspace));
    }

    @Test
    void rejectsInactiveParticipant() {
        User participant = createUser(2L, UserStatus.DESATIVADO, workspace);
        when(userRepository.findEventParticipantsInWorkspace(any(), eq(workspace))).thenReturn(List.of(participant));

        assertThrows(BusinessException.class, () -> service.create(
                createRequest(null, List.of(2L)), "manager"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsManagerAsParticipant() {
        when(userRepository.findEventParticipantsInWorkspace(any(), eq(workspace))).thenReturn(List.of(manager));

        assertThrows(BusinessException.class, () -> service.create(
                createRequest(null, List.of(1L)), "manager"));
    }

    @Test
    void rejectsParticipantWithoutEligibleRoleForNr() {
        User participant = createUser(2L, UserStatus.ATIVO, workspace);
        when(nrCatalogRepository.existsById(10)).thenReturn(true);
        when(userRepository.findEventParticipantsInWorkspace(any(), eq(workspace))).thenReturn(List.of(participant));
        when(roleRepository.findIdsAssociatedWithNr(eq(10), any())).thenReturn(List.of());

        assertThrows(BusinessException.class, () -> service.create(
                createRequest(10, List.of(2L)), "manager"));
    }

    @Test
    void doesNotChangeEventStatusWhenLatestClassEnded() {
        Event event = createEvent(EventStatus.ATIVO);
        EventClass eventClass = createEventClass(
                event,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1));
        var start = LocalDateTime.of(2026, 10, 1, 0, 0);
        var end = LocalDateTime.of(2026, 11, 1, 0, 0);
        var row = mock(com.astro.api.event.repository.EventCalendarProjection.class);
        when(row.getEventTitle()).thenReturn("Event");
        when(row.getStatus()).thenReturn(EventStatus.ATIVO);
        when(eventClassRepository.findCalendar(workspace, start, end)).thenReturn(List.of(row));
        var response = service.findAll(start, end, "manager");
        assertEquals(1, response.size());
        assertEquals("Event", response.getFirst().eventTitle());
        assertEquals(EventStatus.ATIVO, response.getFirst().status());
        assertEquals(EventStatus.ATIVO, event.getStatus());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsCalendarPeriodWithEndDateBeforeStartDate() {
        LocalDateTime startDate = LocalDateTime.now();

        assertThrows(BusinessException.class, () -> service.findAll(
                startDate,
                startDate.minusHours(1),
                "manager"));
        verifyNoInteractions(eventRepository, eventClassRepository);
    }

    @Test
    void cancelsActiveEvent() {
        Event event = createEvent(EventStatus.ATIVO);
        EventClass eventClass = createEventClass(
                event,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1));
        when(eventRepository.findLockedInWorkspace(30L, workspace))
                .thenReturn(Optional.of(event));
        when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(eventClass));
        when(eventClassParticipantRepository.findByEventClassIn(List.of(eventClass))).thenReturn(List.of());

        var response = service.cancel(30L, new CancelEventRequest("Valid reason"), "manager");

        assertEquals(EventStatus.CANCELADO, response.status());
        assertNotNull(event.getCancellationDate());
        assertEquals("Valid reason", event.getCancellationReason());
    }

    @Test
    void detailIncludesAllClassesInStableOrderAndPartialEditingPermissions() {
        Event event = createEvent(EventStatus.ATIVO);
        EventClass future = createEventClass(event, LocalDateTime.of(2026, 10, 15, 8, 0), LocalDateTime.of(2026, 10, 15, 9, 0));
        future.setId(41L);
        EventClass started = createEventClass(event, LocalDateTime.of(2026, 10, 14, 12, 0), LocalDateTime.of(2026, 10, 14, 13, 0));
        when(eventRepository.findByIdAndManager_Unit_Workspace(30L, workspace)).thenReturn(Optional.of(event));
        when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(future, started));
        var first = new com.astro.api.event.model.EventClassParticipant();
        first.setId(91L); first.setEventClass(future); first.setUser(createUser(3L, UserStatus.ATIVO, workspace));
        var second = new com.astro.api.event.model.EventClassParticipant();
        second.setId(92L); second.setEventClass(future); second.setUser(createUser(2L, UserStatus.ATIVO, workspace));
        when(eventClassParticipantRepository.findByEventClassIn(List.of(future, started))).thenReturn(List.of(first, second));
        var response = service.findById(30L, "manager");
        assertEquals(1L, response.managerId());
        org.junit.jupiter.api.Assertions.assertTrue(response.canEdit());
        org.junit.jupiter.api.Assertions.assertTrue(response.canCancel());
        org.junit.jupiter.api.Assertions.assertFalse(response.detailsEditable());
        assertEquals(List.of(40L, 41L), response.eventClasses().stream().map(com.astro.api.event.dto.response.EventClassResponse::id).toList());
        org.junit.jupiter.api.Assertions.assertFalse(response.eventClasses().getFirst().editable());
        org.junit.jupiter.api.Assertions.assertTrue(response.eventClasses().getLast().editable());
        assertEquals(List.of(2L, 3L), response.eventClasses().getLast().participants().stream().map(com.astro.api.event.dto.response.EventParticipantResponse::id).toList());
        assertEquals(92L, response.eventClasses().getLast().participants().getFirst().participationId());
        org.junit.jupiter.api.Assertions.assertTrue(response.eventClasses().getFirst().participants().isEmpty());
    }

    @Test
    void detailIsVisibleToOtherManagerButCannotBeModified() {
        Event event = createEvent(EventStatus.ATIVO);
        event.setManager(createUser(99L, UserStatus.ATIVO, workspace));
        when(eventRepository.findByIdAndManager_Unit_Workspace(30L, workspace)).thenReturn(Optional.of(event));
        var response = service.findById(30L, "manager");
        org.junit.jupiter.api.Assertions.assertFalse(response.canEdit());
        org.junit.jupiter.api.Assertions.assertFalse(response.canCancel());
        org.junit.jupiter.api.Assertions.assertFalse(response.detailsEditable());
    }

    @Test
    void detailHidesMissingAndForeignWorkspaceEventsAndBlocksInactiveManager() {
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class, () -> service.findById(999L, "manager"));
        manager.setStatus(UserStatus.DESATIVADO);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.findById(30L, "manager"));
    }

    @Test
    void finalEventStatesDisableEveryPermission() {
        for (EventStatus status : List.of(EventStatus.CANCELADO, EventStatus.CONCLUIDO)) {
            Event event = createEvent(status);
            var future = createEventClass(event, LocalDateTime.of(2026, 10, 15, 8, 0), LocalDateTime.of(2026, 10, 15, 9, 0));
            when(eventRepository.findByIdAndManager_Unit_Workspace(30L, workspace)).thenReturn(Optional.of(event));
            when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(future));
            var response = service.findById(30L, "manager");
            org.junit.jupiter.api.Assertions.assertFalse(response.canEdit());
            org.junit.jupiter.api.Assertions.assertFalse(response.canCancel());
            org.junit.jupiter.api.Assertions.assertFalse(response.detailsEditable());
            org.junit.jupiter.api.Assertions.assertFalse(response.eventClasses().getFirst().editable());
        }
    }

    @Test
    void requiresBothCalendarLimitsAndReturnsEmptyCalendar() {
        var start = LocalDateTime.of(2026, 10, 1, 0, 0);
        var end = start.plusDays(1);
        assertThrows(BusinessException.class, () -> service.findAll(null, end, "manager"));
        assertThrows(BusinessException.class, () -> service.findAll(start, null, "manager"));
        assertThrows(BusinessException.class, () -> service.findAll(start, start, "manager"));
        org.junit.jupiter.api.Assertions.assertTrue(service.findAll(start, end, "manager").isEmpty());
    }

    @Test
    void candidatesUseWorkspaceCreatorAndNrFiltersAndCanonicalWorkModel() {
        var row = mock(com.astro.api.event.repository.EventCandidateProjection.class);
        when(row.getId()).thenReturn(3L); when(row.getRoleId()).thenReturn(103L);
        when(row.getUnitId()).thenReturn(7L); when(row.getUnitName()).thenReturn("Sede");
        when(row.getWorkModel()).thenReturn("HIBRIDO");
        when(nrCatalogRepository.existsById(10)).thenReturn(true);
        when(candidateRepository.findEligibleCandidates(10L,1L,10)).thenReturn(List.of(row));
        var response = service.findEligibleCandidates(10,"manager").getFirst();
        assertEquals(7L,response.unitId()); assertEquals("Sede",response.unitName());
        assertEquals(com.astro.api.user.model.WorkModel.HIBRIDO,response.workModel());
        verify(candidateRepository).findEligibleCandidates(10L,1L,10);
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,()->service.findEligibleCandidates(99,"manager"));
        assertThrows(BusinessException.class,()->service.findEligibleCandidates(0,"manager"));
        org.junit.jupiter.api.Assertions.assertTrue(service.findEligibleCandidates(null,"manager").isEmpty());
    }

    @Test
    void rejectsPartiallyOverlappingParticipantsAndDuplicateNormalizedClassNames() {
        var start=LocalDateTime.of(2026,10,15,8,0);
        var a=new CreateEventClassRequest("A",start,start.plusHours(1),List.of(2L,3L));
        var b=new CreateEventClassRequest("B",start,start.plusHours(1),List.of(3L,4L));
        var request=new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,List.of(a,b));
        assertThrows(BusinessException.class,()->service.create(request,"manager"));
        var duplicateName=new CreateEventClassRequest(" A ",start,start.plusHours(1),List.of(4L));
        assertThrows(BusinessException.class,()->service.create(new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,List.of(a,duplicateName)),"manager"));
        verify(userRepository,never()).findEventParticipantsInWorkspace(any(),any());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsNullClassesAndNullNonpositiveRepeatedOrEmptyParticipants() {
        var start=LocalDateTime.of(2026,10,15,8,0);
        for (List<Long> ids : java.util.Arrays.asList(null,List.<Long>of(),java.util.Arrays.asList((Long)null),List.of(0L),List.of(-1L),List.of(2L,2L))) {
            var item=new CreateEventClassRequest("A",start,start.plusHours(1),ids);
            assertThrows(BusinessException.class,()->service.create(new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,List.of(item)),"manager"));
        }
        assertThrows(BusinessException.class,()->service.create(new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,java.util.Arrays.asList((CreateEventClassRequest)null)),"manager"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsMissingParticipantsAndInvalidClassDates() {
        assertThrows(com.astro.api.common.exception.ResourceNotFoundException.class,()->service.create(createRequest(null,List.of(999L)),"manager"));
        var start=LocalDateTime.of(2026,10,15,8,0);
        for (LocalDateTime end:List.of(start,start.minusHours(1))) {
            var item=new CreateEventClassRequest("A",start,end,List.of(2L));
            assertThrows(BusinessException.class,()->service.create(new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,List.of(item)),"manager"));
        }
    }

    @Test
    void updateRejectsPartialOverlapWithoutChangingManagedClasses() {
        Event event=createEvent(EventStatus.ATIVO);
        var start=LocalDateTime.of(2026,10,15,8,0);
        EventClass a=createEventClass(event,start,start.plusHours(1));
        EventClass b=createEventClass(event,start,start.plusHours(1));b.setId(41L);b.setName("B");
        when(eventRepository.findLockedInWorkspace(30L,workspace)).thenReturn(Optional.of(event));
        when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(a,b));
        var request=new com.astro.api.event.dto.request.UpdateEventRequest("Changed","Descrição",null,List.of(
                new com.astro.api.event.dto.request.UpdateEventClassRequest(40L,"New A",start,start.plusHours(1),List.of(2L,3L)),
                new com.astro.api.event.dto.request.UpdateEventClassRequest(41L,"New B",start,start.plusHours(1),List.of(3L,4L))));
        assertThrows(BusinessException.class,()->service.update(30L,request,"manager"));
        assertEquals("Class",a.getName());assertEquals("B",b.getName());assertEquals("Event",event.getTitle());
        verify(eventClassRepository,never()).saveAll(any());verify(eventClassParticipantRepository,never()).deleteAll(any());
    }

    @Test
    void updatePreservesUnchangedParticipantRecordsInsteadOfDeletingAndRecreating() {
        Event event=createEvent(EventStatus.ATIVO);var start=LocalDateTime.of(2026,10,15,8,0);
        EventClass a=createEventClass(event,start,start.plusHours(1));
        User user=createUser(2L,UserStatus.ATIVO,workspace);
        var participation=new com.astro.api.event.model.EventClassParticipant();participation.setId(90L);participation.setEventClass(a);participation.setUser(user);
        when(eventRepository.findLockedInWorkspace(30L,workspace)).thenReturn(Optional.of(event));
        when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(a));
        when(eventClassParticipantRepository.findByEventClassIn(List.of(a))).thenReturn(List.of(participation));
        when(userRepository.findEventParticipantsInWorkspace(any(),eq(workspace))).thenReturn(List.of(user));
        var body=new com.astro.api.event.dto.request.UpdateEventRequest("Event","Descrição",null,List.of(
                new com.astro.api.event.dto.request.UpdateEventClassRequest(40L,"Class",start,start.plusHours(2),List.of(2L))));
        var response=service.update(30L,body,"manager");
        assertEquals(90L,response.eventClasses().getFirst().participants().getFirst().participationId());
        verify(eventClassParticipantRepository).deleteAll(List.of());
        verify(eventClassParticipantRepository).saveAll(List.of());
        verify(eventClassParticipantRepository,never()).hasConclusions(any());
    }

    @Test
    void cancellationNormalizesAndValidatesReasonBeforeChangingEvent() {
        Event event=createEvent(EventStatus.ATIVO);
        when(eventRepository.findLockedInWorkspace(30L,workspace)).thenReturn(Optional.of(event));
        for(String reason:java.util.Arrays.asList(null," "," ab ","😀😀","x".repeat(1001))) {
            assertThrows(BusinessException.class,()->service.cancel(30L,new CancelEventRequest(reason),"manager"));
            assertEquals(EventStatus.ATIVO,event.getStatus());
        }
        var response=service.cancel(30L,new CancelEventRequest("  Motivo válido  "),"manager");
        assertEquals("Motivo válido",response.cancellationReason());
        assertEquals(java.time.Instant.parse("2026-10-14T12:00:00Z"),response.cancellationDate());
        org.junit.jupiter.api.Assertions.assertFalse(response.canEdit());org.junit.jupiter.api.Assertions.assertFalse(response.canCancel());
        assertThrows(BusinessException.class,()->service.cancel(30L,new CancelEventRequest("Outro motivo"),"manager"));
        verify(eventClassParticipantRepository,never()).deleteAll(any());verify(eventClassRepository,never()).deleteAll(any());
    }

    private CreateEventRequest createRequest(Integer nrId, List<Long> participantIds) {
        LocalDateTime startDate = LocalDateTime.now().plusDays(1);
        CreateEventClassRequest eventClass = new CreateEventClassRequest(
                "Class",
                startDate,
                startDate.plusHours(2),
                participantIds);
        return new CreateEventRequest(
                "Event",
                "Description",
                nrId,
                null,
                CompletionMode.COLABORADOR,
                true,
                List.of(eventClass));
    }

    private User createUser(Long id, UserStatus status, Workspace owner) {
        Unit unit = new Unit();
        unit.workspace = owner;
        Role role = new Role();
        role.setId(id + 100);
        role.setWorkspace(owner);
        role.setName("Cargo");
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setEmail("u" + id + "@astro.com");
        user.setStatus(status);
        user.setUnit(unit);
        user.setRole(role);
        return user;
    }

    private Event createEvent(EventStatus status) {
        Event event = new Event();
        event.setId(30L);
        event.setManager(manager);
        event.setStatus(status);
        event.setTitle("Event");
        event.setCompletionMode(CompletionMode.GESTOR);
        return event;
    }

    private EventClass createEventClass(
            Event event,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        EventClass eventClass = new EventClass();
        eventClass.setId(40L);
        eventClass.setEvent(event);
        eventClass.setName("Class");
        eventClass.setStartDate(startDate);
        eventClass.setEndDate(endDate);
        return eventClass;
    }
}
