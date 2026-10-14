package com.astro.api.service;

import com.astro.api.role.model.Role;
import com.astro.api.role.repository.RoleRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.conformity.repository.NrCatalogRepository;
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
        service = new EventService(
                eventRepository,
                eventClassRepository,
                eventClassParticipantRepository,
                userRepository,
                roleRepository,
                nrCatalogRepository,
                new EventMapper());
        workspace = new Workspace();
        workspace.id = 10L;
        manager = createUser(1L, UserStatus.ATIVO, workspace);
        manager.setFirebaseUid("manager");
        when(userRepository.findByFirebaseUid("manager")).thenReturn(Optional.of(manager));
    }

    @Test
    void createsEventWithoutNrUsingBatchOperations() {
        User participant = createUser(2L, UserStatus.ATIVO, workspace);
        when(userRepository.findAllById(any())).thenReturn(List.of(participant));
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
        verify(userRepository).findAllById(any());
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
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void rejectsInactiveParticipant() {
        User participant = createUser(2L, UserStatus.DESATIVADO, workspace);
        when(userRepository.findAllById(any())).thenReturn(List.of(participant));

        assertThrows(BusinessException.class, () -> service.create(
                createRequest(null, List.of(2L)), "manager"));
        verifyNoInteractions(eventRepository);
    }

    @Test
    void rejectsManagerAsParticipant() {
        when(userRepository.findAllById(any())).thenReturn(List.of(manager));

        assertThrows(BusinessException.class, () -> service.create(
                createRequest(null, List.of(1L)), "manager"));
    }

    @Test
    void rejectsParticipantWithoutEligibleRoleForNr() {
        User participant = createUser(2L, UserStatus.ATIVO, workspace);
        when(nrCatalogRepository.existsById(10)).thenReturn(true);
        when(userRepository.findAllById(any())).thenReturn(List.of(participant));
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
        when(eventRepository.findByManager_Unit_Workspace(workspace)).thenReturn(List.of(event));
        when(eventClassRepository.findByEventInOrderByStartDate(List.of(event)))
                .thenReturn(List.of(eventClass));

        var response = service.findAll(null, null, "manager");

        assertEquals(1, response.size());
        assertEquals("Event", response.getFirst().eventTitle());
        assertEquals(EventStatus.ATIVO, event.getStatus());
        verify(eventRepository, never()).save(event);
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
        when(eventRepository.findByIdAndManager_Unit_Workspace(30L, workspace))
                .thenReturn(Optional.of(event));
        when(eventClassRepository.findByEventOrderByStartDate(event)).thenReturn(List.of(eventClass));
        when(eventClassParticipantRepository.findByEventClassIn(List.of(eventClass))).thenReturn(List.of());

        var response = service.cancel(30L, new CancelEventRequest("Valid reason"), "manager");

        assertEquals(EventStatus.CANCELADO, response.status());
        assertNotNull(event.getCancellationDate());
        assertEquals("Valid reason", event.getCancellationReason());
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
