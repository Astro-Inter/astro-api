package com.astro.api.service;

import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.service.ViewedService;
import com.astro.api.common.storage.R2StorageService;
import com.astro.api.conformity.model.Conformity;
import com.astro.api.conformity.repository.ConformityRepository;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventClassRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventClassRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.CollaboratorEventDetailsResponse;
import com.astro.api.event.dto.response.CollaboratorEventResponse;
import com.astro.api.event.dto.response.EventCompletionResponse;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventClassResponse;
import com.astro.api.event.dto.response.EventParticipantResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.dto.response.MyEventResponse;
import com.astro.api.event.mapper.EventMapper;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.Event;
import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventClassParticipant;
import com.astro.api.event.model.EventCompletion;
import com.astro.api.event.model.EventCompletionStatus;
import com.astro.api.event.model.EventEvidence;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.ParticipationStatus;
import com.astro.api.event.repository.EventCalendarProjection;
import com.astro.api.event.repository.MyEventProjection;
import com.astro.api.event.repository.EventCandidateProjection;
import com.astro.api.event.repository.EventCandidateRepository;
import com.astro.api.event.repository.EventClassParticipantRepository;
import com.astro.api.event.repository.EventClassRepository;
import com.astro.api.event.repository.EventCompletionRepository;
import com.astro.api.event.repository.EventEvidenceRepository;
import com.astro.api.event.repository.EventRepository;
import com.astro.api.event.service.EventService;
import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.unit.model.Unit;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.WorkModel;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private ViewedService viewedService;
    private EventCompletionRepository eventCompletionRepository;
    private EventEvidenceRepository eventEvidenceRepository;
    private R2StorageService storageService;
    private ConformityRepository conformityRepository;
    private EventService service;
    private EventCandidateRepository candidateRepository;
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
        candidateRepository = mock(EventCandidateRepository.class);
        viewedService = mock(ViewedService.class);
        eventCompletionRepository = mock(EventCompletionRepository.class);
        eventEvidenceRepository = mock(EventEvidenceRepository.class);
        storageService = mock(R2StorageService.class);
        conformityRepository = mock(ConformityRepository.class);
        service = new EventService(
                eventRepository,
                eventClassRepository,
                eventClassParticipantRepository,
                userRepository,
                roleRepository,
                nrCatalogRepository,
                new EventMapper(), Clock.fixed(
                        Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC),
                candidateRepository, viewedService,
                eventCompletionRepository, eventEvidenceRepository,
                storageService, conformityRepository);
        workspace = new Workspace();
        workspace.id = 10L;
        manager = createUser(1L, UserStatus.ATIVO, workspace);
        manager.setFirebaseUid("manager");
        manager.setType(UserType.GESTOR);
        when(userRepository.findByFirebaseUid("manager")).thenReturn(Optional.of(manager));
    }

    @ParameterizedTest
    @EnumSource(UserType.class)
    void createsEventWithoutNrForEveryEmployeeProfileUsingBatchOperations(UserType participantType) {
        User participant = createUser(2L, UserStatus.ATIVO, workspace);
        participant.setType(participantType);
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

        EventResponse response = service.create(createRequest(null, List.of(2L)), "manager");

        assertEquals(EventStatus.ATIVO, response.status());
        assertEquals(1, response.eventClasses().size());
        assertEquals(2L, response.eventClasses().getFirst().participants().getFirst().id());
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
    void listsMyEventsUsingAuthenticatedManagerIdAndPreservesViewValues() {
        MyEventProjection row = mock(MyEventProjection.class);
        when(row.getId()).thenReturn(101L);
        when(row.getTitle()).thenReturn("Treinamento de EPI");
        when(row.getStatus()).thenReturn("ATIVO");
        when(row.getCompletionMode()).thenReturn("COLABORADOR");
        when(row.getPendingCount()).thenReturn(4L);
        when(eventRepository.findMyEvents(1L)).thenReturn(List.of(row));

        List<MyEventResponse> response = service.findMyEvents("manager");

        assertEquals(List.of(new MyEventResponse(101L, "Treinamento de EPI", EventStatus.ATIVO,
                CompletionMode.COLABORADOR, 4L)), response);
        verify(eventRepository).findMyEvents(1L);
    }

    @Test
    void returnsEmptyMyEventsListWhenViewHasNoRows() {
        when(eventRepository.findMyEvents(1L)).thenReturn(List.of());

        assertEquals(List.of(), service.findMyEvents("manager"));
        verify(eventRepository).findMyEvents(1L);
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

    @ParameterizedTest
    @EnumSource(value = UserType.class, names = {"GESTOR", "GESTOR_WORKSPACE"})
    void rejectsTheCreatorAsParticipantForBothManagerProfiles(UserType creatorType) {
        manager.setType(creatorType);
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
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 1, 0, 0);
        EventCalendarProjection row = mock(EventCalendarProjection.class);
        when(row.getEventTitle()).thenReturn("Event");
        when(row.getStatus()).thenReturn(EventStatus.ATIVO);
        when(eventClassRepository.findCalendar(workspace, start, end)).thenReturn(List.of(row));
        List<EventCalendarResponse> response = service.findAll(start, end, "manager");
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

        EventResponse response = service.cancel(30L, new CancelEventRequest("Valid reason"), "manager");

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
        EventClassParticipant first = new EventClassParticipant();
        first.setId(91L); first.setEventClass(future); first.setUser(createUser(3L, UserStatus.ATIVO, workspace));
        EventClassParticipant second = new EventClassParticipant();
        second.setId(92L); second.setEventClass(future); second.setUser(createUser(2L, UserStatus.ATIVO, workspace));
        when(eventClassParticipantRepository.findByEventClassIn(List.of(future, started))).thenReturn(List.of(first, second));
        EventResponse response = service.findById(30L, "manager");
        assertEquals(1L, response.managerId());
        Assertions.assertTrue(response.canEdit());
        Assertions.assertTrue(response.canCancel());
        Assertions.assertFalse(response.detailsEditable());
        assertEquals(List.of(40L, 41L), response.eventClasses().stream().map(EventClassResponse::id).toList());
        Assertions.assertFalse(response.eventClasses().getFirst().editable());
        Assertions.assertTrue(response.eventClasses().getLast().editable());
        assertEquals(List.of(2L, 3L), response.eventClasses().getLast().participants().stream().map(EventParticipantResponse::id).toList());
        assertEquals(92L, response.eventClasses().getLast().participants().getFirst().participationId());
        Assertions.assertTrue(response.eventClasses().getFirst().participants().isEmpty());
    }

    @Test
    void detailIsVisibleToOtherManagerButCannotBeModified() {
        Event event = createEvent(EventStatus.ATIVO);
        event.setManager(createUser(99L, UserStatus.ATIVO, workspace));
        when(eventRepository.findByIdAndManager_Unit_Workspace(30L, workspace)).thenReturn(Optional.of(event));
        EventResponse response = service.findById(30L, "manager");
        Assertions.assertFalse(response.canEdit());
        Assertions.assertFalse(response.canCancel());
        Assertions.assertFalse(response.detailsEditable());
    }

    @Test
    void detailHidesMissingAndForeignWorkspaceEventsAndBlocksInactiveManager() {
        assertThrows(ResourceNotFoundException.class, () -> service.findById(999L, "manager"));
        manager.setStatus(UserStatus.DESATIVADO);
        assertThrows(AccessDeniedException.class, () -> service.findById(30L, "manager"));
    }

    @Test
    void finalEventStatesDisableEveryPermission() {
        for (EventStatus status : List.of(EventStatus.CANCELADO, EventStatus.CONCLUIDO)) {
            Event event = createEvent(status);
            EventClass future = createEventClass(event, LocalDateTime.of(2026, 10, 15, 8, 0), LocalDateTime.of(2026, 10, 15, 9, 0));
            when(eventRepository.findByIdAndManager_Unit_Workspace(30L, workspace)).thenReturn(Optional.of(event));
            when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(future));
            EventResponse response = service.findById(30L, "manager");
            Assertions.assertFalse(response.canEdit());
            Assertions.assertFalse(response.canCancel());
            Assertions.assertFalse(response.detailsEditable());
            Assertions.assertFalse(response.eventClasses().getFirst().editable());
        }
    }

    @Test
    void requiresBothCalendarLimitsAndReturnsEmptyCalendar() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        assertThrows(BusinessException.class, () -> service.findAll(null, end, "manager"));
        assertThrows(BusinessException.class, () -> service.findAll(start, null, "manager"));
        assertThrows(BusinessException.class, () -> service.findAll(start, start, "manager"));
        Assertions.assertTrue(service.findAll(start, end, "manager").isEmpty());
    }

    @Test
    void candidatesUseWorkspaceCreatorAndNrFiltersAndCanonicalWorkModel() {
        EventCandidateProjection row = mock(EventCandidateProjection.class);
        when(row.getId()).thenReturn(3L); when(row.getRoleId()).thenReturn(103L);
        when(row.getUnitId()).thenReturn(7L); when(row.getUnitName()).thenReturn("Sede");
        when(row.getWorkModel()).thenReturn("HIBRIDO");
        when(nrCatalogRepository.existsById(10)).thenReturn(true);
        when(candidateRepository.findEligibleCandidates("manager",10)).thenReturn(List.of(row));
        EventCandidateResponse response = service.findEligibleCandidates(10,"manager").getFirst();
        assertEquals(7L,response.unitId()); assertEquals("Sede",response.unitName());
        assertEquals(WorkModel.HIBRIDO,response.workModel());
        verify(candidateRepository).findEligibleCandidates("manager",10);
        assertThrows(ResourceNotFoundException.class,()->service.findEligibleCandidates(99,"manager"));
        assertThrows(BusinessException.class,()->service.findEligibleCandidates(0,"manager"));
        Assertions.assertTrue(service.findEligibleCandidates(null,"manager").isEmpty());
    }

    @Test
    void rejectsPartiallyOverlappingParticipantsAndDuplicateNormalizedClassNames() {
        LocalDateTime start=LocalDateTime.of(2026,10,15,8,0);
        CreateEventClassRequest a=new CreateEventClassRequest("A",start,start.plusHours(1),List.of(2L,3L));
        CreateEventClassRequest b=new CreateEventClassRequest("B",start,start.plusHours(1),List.of(3L,4L));
        CreateEventRequest request=new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,List.of(a,b));
        assertThrows(BusinessException.class,()->service.create(request,"manager"));
        CreateEventClassRequest duplicateName=new CreateEventClassRequest(" A ",start,start.plusHours(1),List.of(4L));
        assertThrows(BusinessException.class,()->service.create(new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,List.of(a,duplicateName)),"manager"));
        verify(userRepository,never()).findEventParticipantsInWorkspace(any(),any());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsNullClassesAndNullNonpositiveRepeatedOrEmptyParticipants() {
        LocalDateTime start=LocalDateTime.of(2026,10,15,8,0);
        for (List<Long> ids : Arrays.asList(null,List.<Long>of(),Arrays.asList((Long)null),List.of(0L),List.of(-1L),List.of(2L,2L))) {
            CreateEventClassRequest item=new CreateEventClassRequest("A",start,start.plusHours(1),ids);
            assertThrows(BusinessException.class,()->service.create(new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,List.of(item)),"manager"));
        }
        assertThrows(BusinessException.class,()->service.create(new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,Arrays.asList((CreateEventClassRequest)null)),"manager"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsMissingParticipantsAndInvalidClassDates() {
        assertThrows(ResourceNotFoundException.class,()->service.create(createRequest(null,List.of(999L)),"manager"));
        LocalDateTime start=LocalDateTime.of(2026,10,15,8,0);
        for (LocalDateTime end:List.of(start,start.minusHours(1))) {
            CreateEventClassRequest item=new CreateEventClassRequest("A",start,end,List.of(2L));
            assertThrows(BusinessException.class,()->service.create(new CreateEventRequest("Evento","Descrição",null,null,CompletionMode.GESTOR,false,List.of(item)),"manager"));
        }
    }

    @Test
    void updateRejectsPartialOverlapWithoutChangingManagedClasses() {
        Event event=createEvent(EventStatus.ATIVO);
        LocalDateTime start=LocalDateTime.of(2026,10,15,8,0);
        EventClass a=createEventClass(event,start,start.plusHours(1));
        EventClass b=createEventClass(event,start,start.plusHours(1));b.setId(41L);b.setName("B");
        when(eventRepository.findLockedInWorkspace(30L,workspace)).thenReturn(Optional.of(event));
        when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(a,b));
        UpdateEventRequest request=new UpdateEventRequest("Changed","Descrição",null,List.of(
                new UpdateEventClassRequest(40L,"New A",start,start.plusHours(1),List.of(2L,3L)),
                new UpdateEventClassRequest(41L,"New B",start,start.plusHours(1),List.of(3L,4L))));
        assertThrows(BusinessException.class,()->service.update(30L,request,"manager"));
        assertEquals("Class",a.getName());assertEquals("B",b.getName());assertEquals("Event",event.getTitle());
        verify(eventClassRepository,never()).saveAll(any());verify(eventClassParticipantRepository,never()).deleteAll(any());
    }

    @Test
    void updatePreservesUnchangedParticipantRecordsInsteadOfDeletingAndRecreating() {
        Event event=createEvent(EventStatus.ATIVO);LocalDateTime start=LocalDateTime.of(2026,10,15,8,0);
        EventClass a=createEventClass(event,start,start.plusHours(1));
        User user=createUser(2L,UserStatus.ATIVO,workspace);
        EventClassParticipant participation=new EventClassParticipant();participation.setId(90L);participation.setEventClass(a);participation.setUser(user);
        when(eventRepository.findLockedInWorkspace(30L,workspace)).thenReturn(Optional.of(event));
        when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(a));
        when(eventClassParticipantRepository.findByEventClassIn(List.of(a))).thenReturn(List.of(participation));
        when(userRepository.findEventParticipantsInWorkspace(any(),eq(workspace))).thenReturn(List.of(user));
        UpdateEventRequest body=new UpdateEventRequest("Event","Descrição",null,List.of(
                new UpdateEventClassRequest(40L,"Class",start,start.plusHours(2),List.of(2L))));
        EventResponse response=service.update(30L,body,"manager");
        assertEquals(90L,response.eventClasses().getFirst().participants().getFirst().participationId());
        verify(eventClassParticipantRepository).deleteAll(List.of());
        verify(eventClassParticipantRepository).saveAll(List.of());
        verify(eventClassParticipantRepository,never()).hasConclusions(any());
    }

    @Test
    void cancellationNormalizesAndValidatesReasonBeforeChangingEvent() {
        Event event=createEvent(EventStatus.ATIVO);
        when(eventRepository.findLockedInWorkspace(30L,workspace)).thenReturn(Optional.of(event));
        for(String reason:Arrays.asList(null," "," ab ","😀😀","x".repeat(1001))) {
            assertThrows(BusinessException.class,()->service.cancel(30L,new CancelEventRequest(reason),"manager"));
            assertEquals(EventStatus.ATIVO,event.getStatus());
        }
        EventResponse response=service.cancel(30L,new CancelEventRequest("  Motivo válido  "),"manager");
        assertEquals("Motivo válido",response.cancellationReason());
        assertEquals(Instant.parse("2026-10-14T12:00:00Z"),response.cancellationDate());
        Assertions.assertFalse(response.canEdit());Assertions.assertFalse(response.canCancel());
        assertThrows(BusinessException.class,()->service.cancel(30L,new CancelEventRequest("Outro motivo"),"manager"));
        verify(eventClassParticipantRepository,never()).deleteAll(any());verify(eventClassRepository,never()).deleteAll(any());
    }

    @Test
    void listsCollaboratorClassesFromLatestToOldestDeadline() {
        User collaborator = createUser(2L, UserStatus.ATIVO, workspace);
        collaborator.setFirebaseUid("collaborator");
        Event pastEvent = createEvent(EventStatus.CANCELADO);
        pastEvent.setId(31L);
        Event futureEvent = createEvent(EventStatus.ATIVO);
        futureEvent.setId(32L);
        EventClass pastClass = createEventClass(
                pastEvent, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        EventClass futureClass = createEventClass(
                futureEvent, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
        EventClassParticipant pastParticipation = new EventClassParticipant();
        pastParticipation.setEventClass(pastClass);
        pastParticipation.setUser(collaborator);
        EventClassParticipant futureParticipation = new EventClassParticipant();
        futureParticipation.setEventClass(futureClass);
        futureParticipation.setUser(collaborator);
        when(userRepository.findByFirebaseUid("collaborator")).thenReturn(Optional.of(collaborator));
        when(eventClassParticipantRepository.findByUser_IdOrderByEventClass_EndDateDesc(2L))
                .thenReturn(List.of(futureParticipation, pastParticipation));
        when(viewedService.findViewed(2L, "events", List.of(32L, 31L))).thenReturn(Set.of("31"));

        List<CollaboratorEventResponse> response = service.findForCollaborator("collaborator");

        assertEquals(2, response.size());
        assertEquals(ParticipationStatus.PENDENTE, response.getFirst().participationStatus());
        assertEquals(EventStatus.ATIVO, response.getFirst().eventStatus());
        assertEquals(false, response.getFirst().viewed());
        assertEquals(ParticipationStatus.PENDENTE, response.get(1).participationStatus());
        assertEquals(EventStatus.CANCELADO, response.get(1).eventStatus());
        assertEquals(true, response.get(1).viewed());
    }

    @Test
    void collaboratorDetailsKeepCompletionEvidenceAndComplianceAfterMerge() {
        User collaborator = createUser(2L, UserStatus.ATIVO, workspace);
        when(userRepository.findByFirebaseUid("collaborator")).thenReturn(Optional.of(collaborator));
        Event event = createEvent(EventStatus.ATIVO);
        event.setNrId(10);
        EventClass eventClass = createEventClass(event,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        EventClassParticipant participation = new EventClassParticipant();
        participation.setId(90L);
        participation.setEventClass(eventClass);
        participation.setUser(collaborator);
        when(eventClassParticipantRepository.findFirstByEventClass_Event_IdAndUser_Id(30L, 2L))
                .thenReturn(Optional.of(participation));
        EventCompletion completion = new EventCompletion();
        completion.setId(70L);
        completion.setStatus(EventCompletionStatus.CONCLUIDO);
        when(eventCompletionRepository.findByParticipation_Id(90L)).thenReturn(Optional.of(completion));
        Conformity conformity = new Conformity();
        conformity.setExpiresAt(LocalDate.of(2027, 1, 1));
        when(conformityRepository.findByEventCompletionId(70L)).thenReturn(Optional.of(conformity));
        EventEvidence evidence = new EventEvidence();
        evidence.setId(80L);
        evidence.setOriginalName("certificate.pdf");
        evidence.setObjectPath("test/certificate.pdf");
        evidence.setMimeType("application/pdf");
        evidence.setSizeBytes(3L);
        when(eventEvidenceRepository.findByCompletion_IdOrderById(70L)).thenReturn(List.of(evidence));
        when(storageService.getEventEvidenceDownloadUrl("test/certificate.pdf"))
                .thenReturn("https://storage.example.com/certificate.pdf");

        CollaboratorEventDetailsResponse response = service.findDetailsForCollaborator(30L, "collaborator");

        assertEquals(30L, response.eventId());
        assertEquals(manager.getId(), response.managerId());
        assertEquals(conformity.getExpiresAt(), response.complianceValidity());
        assertEquals(80L, response.evidences().getFirst().evidenceId());
        assertEquals("https://storage.example.com/certificate.pdf", response.evidences().getFirst().url());
    }

    @Test
    void collaboratorCanRequestCompletionAfterAdministrativeFlowMerge() {
        User collaborator = createUser(2L, UserStatus.ATIVO, workspace);
        when(userRepository.findByFirebaseUid("collaborator")).thenReturn(Optional.of(collaborator));
        Event event = createEvent(EventStatus.ATIVO);
        event.setCompletionMode(CompletionMode.COLABORADOR);
        EventClass eventClass = createEventClass(event,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        EventClassParticipant participation = new EventClassParticipant();
        participation.setId(90L);
        participation.setEventClass(eventClass);
        participation.setUser(collaborator);
        when(eventClassParticipantRepository.findByEventClass_Event_IdAndUser_Id(30L, 2L))
                .thenReturn(Optional.of(participation));
        when(eventCompletionRepository.findByParticipation_Id(90L)).thenReturn(Optional.empty());
        when(eventCompletionRepository.save(any(EventCompletion.class))).thenAnswer(invocation -> {
            EventCompletion completion = invocation.getArgument(0);
            completion.setId(70L);
            return completion;
        });
        when(eventEvidenceRepository.findByCompletion_Id(70L)).thenReturn(List.of());

        EventCompletionResponse response = service.requestCompletion(30L, List.of(), "collaborator");

        assertEquals(70L, response.id());
        assertEquals(EventCompletionStatus.PENDENTE, response.status());
        assertNotNull(response.completionDate());
        verifyNoInteractions(storageService);
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
