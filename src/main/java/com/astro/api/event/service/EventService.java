package com.astro.api.event.service;

import com.astro.api.role.repository.RoleRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.conformidade.repository.NrCatalogRepository;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventClassRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventClassRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.mapper.EventMapper;
import com.astro.api.event.model.Event;
import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventClassParticipant;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.repository.EventClassParticipantRepository;
import com.astro.api.event.repository.EventClassRepository;
import com.astro.api.event.repository.EventRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final EventClassRepository eventClassRepository;
    private final EventClassParticipantRepository eventClassParticipantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NrCatalogRepository nrCatalogRepository;
    private final EventMapper eventMapper;

    public EventService(
            EventRepository eventRepository,
            EventClassRepository eventClassRepository,
            EventClassParticipantRepository eventClassParticipantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            NrCatalogRepository nrCatalogRepository,
            EventMapper eventMapper) {
        this.eventRepository = eventRepository;
        this.eventClassRepository = eventClassRepository;
        this.eventClassParticipantRepository = eventClassParticipantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.nrCatalogRepository = nrCatalogRepository;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request, String firebaseUid) {
        User manager = findAuthenticatedManager(firebaseUid);
        Workspace workspace = getWorkspace(manager);
        validateClassesForCreation(request.eventClasses());
        validateNrExists(request.nrId());

        Set<Long> participantIds = request.eventClasses().stream()
                .flatMap(eventClass -> eventClass.participantIds().stream())
                .collect(Collectors.toSet());
        Map<Long, User> participants = findAndValidateParticipants(
                participantIds, workspace, manager, request.nrId());

        Event event = createEvent(request, manager);
        List<EventClass> eventClasses = createEventClasses(event, request.eventClasses());
        List<EventClassParticipant> classParticipants = createClassParticipants(
                eventClasses, request.eventClasses(), participants);
        eventClassParticipantRepository.saveAll(classParticipants);

        return toResponse(event, eventClasses, classParticipants);
    }

    public List<EventCalendarResponse> findAll(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String firebaseUid) {
        validatePeriod(startDate, endDate);
        Workspace workspace = getWorkspace(findAuthenticatedManager(firebaseUid));
        List<Event> events = eventRepository.findByManager_Unit_Workspace(workspace);
        if (events.isEmpty()) {
            return List.of();
        }
        Map<Long, Event> eventsById = events.stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        return eventClassRepository.findByEventInOrderByStartDate(events).stream()
                .filter(eventClass -> isInPeriod(eventClass, startDate, endDate))
                .map(eventClass -> toCalendarResponse(
                        eventsById.get(eventClass.getEvent().getId()),
                        eventClass))
                .toList();
    }

    public List<EventCandidateResponse> findEligibleCandidates(Integer nrId, String firebaseUid) {
        User manager = findAuthenticatedManager(firebaseUid);
        Workspace workspace = getWorkspace(manager);
        List<User> eligibleUsers = userRepository
                .findByUnit_WorkspaceAndStatus(workspace, UserStatus.ATIVO)
                .stream()
                .filter(user -> !user.getId().equals(manager.getId()))
                .toList();
        if (nrId != null) {
            validateNrExists(nrId);
            Set<Long> eligibleRoleIds = findEligibleRoleIds(nrId, eligibleUsers);
            eligibleUsers = eligibleUsers.stream()
                    .filter(user -> user.getRole() != null
                            && eligibleRoleIds.contains(user.getRole().getId()))
                    .toList();
        }
        return eligibleUsers.stream().map(eventMapper::toCandidateResponse).toList();
    }

    @Transactional
    public EventResponse update(Long eventId, UpdateEventRequest request, String firebaseUid) {
        User manager = findAuthenticatedManager(firebaseUid);
        Event event = findEventInWorkspace(eventId, getWorkspace(manager));
        ensureManagerAndEditable(event, manager);
        List<EventClass> existingClasses = eventClassRepository.findByEventOrderByStartDate(event);
        List<EventClassParticipant> existingClassParticipants = eventClassParticipantRepository
                .findByEventClassIn(existingClasses);
        List<UpdatedEventClass> updatedClasses = prepareUpdatedClasses(
                event,
                existingClasses,
                existingClassParticipants,
                request.eventClasses());
        Set<Long> editableParticipantIds = validateUpdatedClasses(updatedClasses);
        Map<Long, User> editableParticipants = findAndValidateEditableParticipants(
                editableParticipantIds,
                manager,
                event.getNrId());

        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setExternalLink(request.externalLink());
        eventRepository.save(event);

        List<EventClass> removedClasses = findRemovedClasses(existingClasses, updatedClasses);
        removedClasses.forEach(this::ensureEventClassEditable);

        List<UpdatedEventClass> editableClasses = updatedClasses.stream()
                .filter(UpdatedEventClass::editable)
                .toList();
        List<EventClass> classesToPersist = editableClasses.stream()
                .map(UpdatedEventClass::eventClass)
                .toList();
        List<EventClass> savedClasses = eventClassRepository.saveAll(classesToPersist);
        List<EventClassParticipant> participantsToDelete = existingClassParticipants.stream()
                .filter(classParticipant -> classesToPersist.contains(classParticipant.getEventClass())
                        || removedClasses.contains(classParticipant.getEventClass()))
                .toList();
        eventClassParticipantRepository.deleteAll(participantsToDelete);
        eventClassRepository.deleteAll(removedClasses);
        List<EventClassParticipant> classParticipants = createUpdatedClassParticipants(
                savedClasses,
                editableClasses,
                editableParticipants);
        eventClassParticipantRepository.saveAll(classParticipants);
        List<EventClassParticipant> responseParticipants = new ArrayList<>(existingClassParticipants);
        responseParticipants.removeAll(participantsToDelete);
        responseParticipants.addAll(classParticipants);
        List<EventClass> responseClasses = updatedClasses.stream()
                .map(UpdatedEventClass::eventClass)
                .sorted((firstClass, secondClass) -> firstClass.getStartDate()
                        .compareTo(secondClass.getStartDate()))
                .toList();
        return toResponse(event, responseClasses, responseParticipants);
    }

    @Transactional
    public EventResponse cancel(Long eventId, CancelEventRequest request, String firebaseUid) {
        User manager = findAuthenticatedManager(firebaseUid);
        Event event = findEventInWorkspace(eventId, getWorkspace(manager));
        ensureManagerAndEditable(event, manager);

        event.setStatus(EventStatus.CANCELADO);
        event.setCancellationDate(Instant.now());
        event.setCancellationReason(request.reason().trim());
        eventRepository.save(event);
        return toResponse(event, eventClassRepository.findByEventOrderByStartDate(event));
    }

    private void validateNrExists(Integer nrId) {
        if (nrId != null && !nrCatalogRepository.existsById(nrId)) {
            throw new ResourceNotFoundException("NR não encontrada");
        }
    }

    private Event createEvent(CreateEventRequest request, User manager) {
        Event event = new Event();
        event.setTitle(request.title().trim());
        event.setDescription(request.description());
        event.setNrId(request.nrId());
        event.setExternalLink(request.externalLink());
        event.setCompletionMode(request.completionMode());
        event.setMandatoryEvidence(request.mandatoryEvidence());
        event.setStatus(EventStatus.ATIVO);
        event.setManager(manager);
        return eventRepository.save(event);
    }

    private List<EventClass> createEventClasses(
            Event event,
            List<CreateEventClassRequest> classRequests) {
        List<EventClass> eventClasses = classRequests.stream()
                .map(request -> createEventClass(event, request))
                .toList();
        return eventClassRepository.saveAll(eventClasses);
    }

    private List<EventClassParticipant> createClassParticipants(
            List<EventClass> eventClasses,
            List<CreateEventClassRequest> classRequests,
            Map<Long, User> participants) {
        List<EventClassParticipant> classParticipants = new ArrayList<>();
        for (int classIndex = 0; classIndex < eventClasses.size(); classIndex++) {
            EventClass eventClass = eventClasses.get(classIndex);
            for (Long participantId : classRequests.get(classIndex).participantIds()) {
                classParticipants.add(createClassParticipant(
                        eventClass, participants.get(participantId)));
            }
        }
        return classParticipants;
    }

    private List<UpdatedEventClass> prepareUpdatedClasses(
            Event event,
            List<EventClass> existingClasses,
            List<EventClassParticipant> existingClassParticipants,
            List<UpdateEventClassRequest> classRequests) {
        Map<Long, EventClass> existingClassesById = existingClasses.stream()
                .collect(Collectors.toMap(EventClass::getId, Function.identity()));
        Map<Long, Set<Long>> existingParticipantIdsByClassId = existingClassParticipants.stream()
                .collect(Collectors.groupingBy(
                        classParticipant -> classParticipant.getEventClass().getId(),
                        Collectors.mapping(
                                classParticipant -> classParticipant.getUser().getId(),
                                Collectors.toSet())));
        Set<Long> requestedClassIds = new HashSet<>();
        List<UpdatedEventClass> updatedClasses = new ArrayList<>();
        for (UpdateEventClassRequest classRequest : classRequests) {
            EventClass eventClass = classRequest.id() == null
                    ? newEventClass(event)
                    : getExistingEventClass(classRequest.id(), existingClassesById, requestedClassIds);
            validateDates(classRequest.startDate(), classRequest.endDate());
            if (eventClass.getId() != null && !isEventClassEditable(eventClass)) {
                ensureStartedClassIsUnchanged(
                        eventClass,
                        classRequest,
                        existingParticipantIdsByClassId.getOrDefault(eventClass.getId(), Set.of()));
                updatedClasses.add(new UpdatedEventClass(eventClass, classRequest, false));
                continue;
            }
            eventClass.setName(classRequest.name().trim());
            eventClass.setStartDate(classRequest.startDate());
            eventClass.setEndDate(classRequest.endDate());
            updatedClasses.add(new UpdatedEventClass(eventClass, classRequest, true));
        }
        return updatedClasses;
    }

    private EventClass getExistingEventClass(
            Long eventClassId,
            Map<Long, EventClass> existingClassesById,
            Set<Long> requestedClassIds) {
        if (!requestedClassIds.add(eventClassId)) {
            throw new BusinessException("Turma repetida na atualização do evento");
        }
        EventClass eventClass = existingClassesById.get(eventClassId);
        if (eventClass == null) {
            throw new ResourceNotFoundException("Turma não encontrada");
        }
        return eventClass;
    }

    private void ensureStartedClassIsUnchanged(
            EventClass eventClass,
            UpdateEventClassRequest request,
            Set<Long> existingParticipantIds) {
        Set<Long> requestedParticipantIds = new HashSet<>(request.participantIds());
        boolean hasChanged = !eventClass.getName().equals(request.name().trim())
                || !eventClass.getStartDate().equals(request.startDate())
                || !eventClass.getEndDate().equals(request.endDate())
                || !existingParticipantIds.equals(requestedParticipantIds);
        if (hasChanged) {
            throw new BusinessException("Turma já iniciada");
        }
    }

    private List<EventClass> findRemovedClasses(
            List<EventClass> existingClasses,
            List<UpdatedEventClass> updatedClasses) {
        Set<Long> requestedClassIds = updatedClasses.stream()
                .map(UpdatedEventClass::eventClass)
                .map(EventClass::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return existingClasses.stream()
                .filter(eventClass -> !requestedClassIds.contains(eventClass.getId()))
                .toList();
    }

    private Set<Long> validateUpdatedClasses(List<UpdatedEventClass> updatedClasses) {
        Set<Long> allParticipantIds = new HashSet<>();
        Set<Long> editableParticipantIds = new HashSet<>();
        for (UpdatedEventClass updatedClass : updatedClasses) {
            List<Long> classParticipantIds = updatedClass.request().participantIds();
            Set<Long> uniqueClassParticipantIds = new HashSet<>(classParticipantIds);
            validateUniqueParticipants(classParticipantIds, uniqueClassParticipantIds);
            if (!allParticipantIds.addAll(uniqueClassParticipantIds)) {
                throw new BusinessException("Participante repetido em duas turmas do evento");
            }
            if (updatedClass.editable()) {
                editableParticipantIds.addAll(uniqueClassParticipantIds);
            }
        }
        return editableParticipantIds;
    }

    private Map<Long, User> findAndValidateEditableParticipants(
            Set<Long> participantIds,
            User manager,
            Integer nrId) {
        if (participantIds.isEmpty()) {
            return Map.of();
        }
        return findAndValidateParticipants(
                participantIds,
                getWorkspace(manager),
                manager,
                nrId);
    }

    private List<EventClassParticipant> createUpdatedClassParticipants(
            List<EventClass> savedClasses,
            List<UpdatedEventClass> updatedClasses,
            Map<Long, User> participants) {
        List<EventClassParticipant> classParticipants = new ArrayList<>();
        for (int classIndex = 0; classIndex < savedClasses.size(); classIndex++) {
            EventClass eventClass = savedClasses.get(classIndex);
            for (Long participantId : updatedClasses.get(classIndex).request().participantIds()) {
                classParticipants.add(createClassParticipant(
                        eventClass,
                        participants.get(participantId)));
            }
        }
        return classParticipants;
    }

    private void validateClassesForCreation(List<CreateEventClassRequest> classRequests) {
        if (classRequests == null || classRequests.isEmpty()) {
            throw new BusinessException("Evento deve possuir ao menos uma turma");
        }
        Set<Long> allParticipantIds = new HashSet<>();
        for (CreateEventClassRequest classRequest : classRequests) {
            if (classRequest.participantIds() == null || classRequest.participantIds().isEmpty()) {
                throw new BusinessException("Turma deve possuir ao menos um participante");
            }
            validateDates(classRequest.startDate(), classRequest.endDate());
            Set<Long> classParticipantIds = new HashSet<>(classRequest.participantIds());
            validateUniqueParticipants(classRequest.participantIds(), classParticipantIds);
            if (!allParticipantIds.addAll(classParticipantIds)) {
                throw new BusinessException("Participante repetido em duas turmas do evento");
            }
        }
    }

    private void validateUniqueParticipants(List<Long> requestedIds, Set<Long> uniqueIds) {
        if (uniqueIds.size() != requestedIds.size()) {
            throw new BusinessException("Participante repetido na turma");
        }
        if (uniqueIds.isEmpty()) {
            throw new BusinessException("Turma deve possuir ao menos um participante");
        }
    }

    private Map<Long, User> findAndValidateParticipants(
            Collection<Long> participantIds,
            Workspace workspace,
            User manager,
            Integer nrId) {
        List<User> participants = userRepository.findAllById(participantIds);
        Map<Long, User> usersById = participants.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (usersById.size() != participantIds.size()) {
            throw new ResourceNotFoundException("Participante não encontrado");
        }
        Set<Long> eligibleRoleIds = nrId == null
                ? Set.of()
                : findEligibleRoleIds(nrId, participants);
        for (Long participantId : participantIds) {
            validateParticipant(usersById.get(participantId), workspace, manager, nrId, eligibleRoleIds);
        }
        return usersById;
    }

    private void validateParticipant(
            User participant,
            Workspace workspace,
            User manager,
            Integer nrId,
            Set<Long> eligibleRoleIds) {
        if (participant.getStatus() != UserStatus.ATIVO) {
            throw new BusinessException("Participante não está ATIVO");
        }
        if (participant.getUnit() == null || participant.getUnit().workspace == null
                || !workspace.id.equals(participant.getUnit().workspace.id)) {
            throw new BusinessException("Participante pertence a outro Workspace");
        }
        if (manager.getId().equals(participant.getId())) {
            throw new BusinessException("Gestor criador não pode participar do próprio evento");
        }
        if (nrId != null && (participant.getRole() == null
                || !eligibleRoleIds.contains(participant.getRole().getId()))) {
            throw new BusinessException("Participante não elegível para a NR");
        }
    }

    private Set<Long> findEligibleRoleIds(Integer nrId, Collection<User> users) {
        List<Long> roleIds = users.stream()
                .map(User::getRole)
                .filter(Objects::nonNull)
                .map(role -> role.getId())
                .distinct()
                .toList();
        return roleIds.isEmpty()
                ? Set.of()
                : new HashSet<>(roleRepository.findIdsAssociatedWithNr(nrId, roleIds));
    }

    private User findAuthenticatedManager(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado"));
    }

    private Workspace getWorkspace(User user) {
        if (user.getUnit() == null || user.getUnit().workspace == null) {
            throw new BusinessException("Usuário autenticado não pertence a um Workspace");
        }
        return user.getUnit().workspace;
    }

    private Event findEventInWorkspace(Long eventId, Workspace workspace) {
        return eventRepository.findByIdAndManager_Unit_Workspace(eventId, workspace)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado"));
    }

    private void ensureManagerAndEditable(Event event, User manager) {
        if (!event.getManager().getId().equals(manager.getId())) {
            throw new BusinessException("Operação não autorizada para este evento");
        }
        ensureEditable(event);
    }

    private void ensureEditable(Event event) {
        if (event.getStatus() == EventStatus.CANCELADO) {
            throw new BusinessException("Evento cancelado não pode ser alterado");
        }
        if (event.getStatus() == EventStatus.CONCLUIDO) {
            throw new BusinessException("Evento concluído não pode ser alterado");
        }
    }

    private void ensureEventClassEditable(EventClass eventClass) {
        if (!isEventClassEditable(eventClass)) {
            throw new BusinessException("Turma já iniciada");
        }
    }

    private boolean isEventClassEditable(EventClass eventClass) {
        return LocalDateTime.now().isBefore(eventClass.getStartDate());
    }

    private void validateDates(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new BusinessException("Data final deve ser posterior à data inicial");
        }
    }

    private EventClass createEventClass(Event event, CreateEventClassRequest request) {
        EventClass eventClass = newEventClass(event);
        eventClass.setName(request.name().trim());
        eventClass.setStartDate(request.startDate());
        eventClass.setEndDate(request.endDate());
        return eventClass;
    }

    private EventClass newEventClass(Event event) {
        EventClass eventClass = new EventClass();
        eventClass.setEvent(event);
        return eventClass;
    }

    private EventClassParticipant createClassParticipant(EventClass eventClass, User user) {
        EventClassParticipant classParticipant = new EventClassParticipant();
        classParticipant.setEventClass(eventClass);
        classParticipant.setUser(user);
        return classParticipant;
    }

    private boolean isInPeriod(
            EventClass eventClass,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        return (startDate == null || !eventClass.getEndDate().isBefore(startDate))
                && (endDate == null || !eventClass.getStartDate().isAfter(endDate));
    }

    private void validatePeriod(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BusinessException("Data final deve ser posterior à data inicial");
        }
    }

    private EventCalendarResponse toCalendarResponse(Event event, EventClass eventClass) {
        return eventMapper.toCalendarResponse(event, eventClass);
    }

    private EventResponse toResponse(Event event, List<EventClass> eventClasses) {
        List<EventClassParticipant> classParticipants = eventClassParticipantRepository
                .findByEventClassIn(eventClasses);
        return toResponse(event, eventClasses, classParticipants);
    }

    private EventResponse toResponse(
            Event event,
            List<EventClass> eventClasses,
            List<EventClassParticipant> classParticipants) {
        return eventMapper.toResponse(event, eventClasses, classParticipants);
    }

    private record UpdatedEventClass(
            EventClass eventClass,
            UpdateEventClassRequest request,
            boolean editable) {
    }

}
