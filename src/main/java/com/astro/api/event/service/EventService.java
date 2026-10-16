package com.astro.api.event.service;

import com.astro.api.role.repository.RoleRepository;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.common.service.ViewedService;
import com.astro.api.conformity.repository.NrCatalogRepository;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventClassRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventClassRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.CollaboratorEventResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.mapper.EventMapper;
import com.astro.api.event.model.Event;
import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventClassParticipant;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.repository.EventClassParticipantRepository;
import com.astro.api.event.repository.EventClassRepository;
import com.astro.api.event.repository.EventRepository;
import com.astro.api.event.repository.EventCandidateRepository;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserType;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.repository.UserRepository;
import com.astro.api.workspace.model.Workspace;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private static final String EVENTS_RESOURCE = "events";
    private final EventRepository eventRepository;
    private final EventClassRepository eventClassRepository;
    private final EventClassParticipantRepository eventClassParticipantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final NrCatalogRepository nrCatalogRepository;
    private final EventMapper eventMapper;
    private final Clock clock;
    private final EventCandidateRepository candidateRepository;
    private final ViewedService viewedService;

    public EventService(
            EventRepository eventRepository,
            EventClassRepository eventClassRepository,
            EventClassParticipantRepository eventClassParticipantRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            NrCatalogRepository nrCatalogRepository,
            EventMapper eventMapper, @Qualifier("eventClock") Clock clock,
            EventCandidateRepository candidateRepository, ViewedService viewedService) {
        this.eventRepository = eventRepository;
        this.eventClassRepository = eventClassRepository;
        this.eventClassParticipantRepository = eventClassParticipantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.nrCatalogRepository = nrCatalogRepository;
        this.eventMapper = eventMapper;
        this.clock = clock;
        this.candidateRepository = candidateRepository;
        this.viewedService = viewedService;
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

        return toResponse(event, eventClasses, classParticipants, manager);
    }

    @Transactional(readOnly = true)
    public EventResponse findById(Long eventId, String firebaseUid) {
        User manager = findAuthenticatedManager(firebaseUid);
        Event event = findEventInWorkspace(eventId, getWorkspace(manager));
        return toResponse(event, eventClassRepository.findByEventOrderByStartDate(event), manager);
    }

    @Transactional(readOnly = true)
    public List<EventCalendarResponse> findAll(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String firebaseUid) {
        validatePeriod(startDate, endDate);
        Workspace workspace = getWorkspace(findAuthenticatedManager(firebaseUid));
        return eventClassRepository.findCalendar(workspace, startDate, endDate).stream()
                .map(eventMapper::toCalendarResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EventCandidateResponse> findEligibleCandidates(Integer nrId, String firebaseUid) {
        User manager = findAuthenticatedManager(firebaseUid);
        getWorkspace(manager);
        if (nrId != null && nrId <= 0) throw new BusinessException("Identificador da NR deve ser positivo");
        validateNrExists(nrId);
        return candidateRepository.findEligibleCandidates(firebaseUid, nrId).stream()
                .map(eventMapper::toCandidateResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CollaboratorEventResponse> findForCollaborator(String firebaseUid) {
        User user = findAuthenticatedUser(firebaseUid);
        List<EventClassParticipant> participations = eventClassParticipantRepository
                .findByUser_IdOrderByEventClass_StartDateAsc(user.getId());
        if (participations.isEmpty()) {
            return List.of();
        }
        Set<String> viewedEvents = viewedService.findViewed(user.getId(), EVENTS_RESOURCE,
                participations.stream()
                        .map(participation -> participation.getEventClass().getEvent().getId())
                        .toList());
        LocalDateTime now = LocalDateTime.now();
        return participations.stream()
                .map(participation -> eventMapper.toCollaboratorEventResponse(
                        participation,
                        viewedEvents.contains(participation.getEventClass().getEvent().getId().toString()),
                        classStatus(participation.getEventClass(), now)))
                .toList();
    }

    @Transactional
    public EventResponse update(Long eventId, UpdateEventRequest request, String firebaseUid) {
        User manager = findAuthenticatedManager(firebaseUid);
        Event event = findLockedEventInWorkspace(eventId, getWorkspace(manager));
        ensureManagerAndEditable(event, manager);
        LocalDateTime editTime = LocalDateTime.now(clock);
        List<EventClass> existingClasses = eventClassRepository.findByEventOrderByStartDate(event);
        List<EventClassParticipant> existingParticipants = eventClassParticipantRepository.findByEventClassIn(existingClasses);
        ensureMetadataEditable(event, request, existingClasses, editTime);
        List<UpdatedEventClass> updatedClasses = prepareUpdatedClasses(
                event, existingClasses, existingParticipants, request.eventClasses(), editTime);
        List<EventClass> removedClasses = findRemovedClasses(existingClasses, updatedClasses);
        if (removedClasses.stream().anyMatch(eventClass -> !editTime.isBefore(eventClass.getStartDate()))) {
            throw new BusinessException("Turma já iniciada não pode ser removida");
        }
        Set<Long> editableParticipantIds = validateUpdatedClasses(updatedClasses);
        Map<Long, User> users = findAndValidateEditableParticipants(editableParticipantIds, manager, event.getNrId());
        Map<Long, UpdatedEventClass> desiredById = updatedClasses.stream()
                .filter(item -> item.eventClass().getId() != null)
                .collect(Collectors.toMap(item -> item.eventClass().getId(), Function.identity()));
        List<EventClassParticipant> removedParticipants = existingParticipants.stream().filter(participant -> {
            UpdatedEventClass desired = desiredById.get(participant.getEventClass().getId());
            return desired == null || !desired.request().participantIds().contains(participant.getUser().getId());
        }).toList();
        if (!removedParticipants.isEmpty() && eventClassParticipantRepository.hasConclusions(
                removedParticipants.stream().map(EventClassParticipant::getId).toList())) {
            throw new BusinessException("Participação com conclusão não pode ser removida");
        }

        // Flush removals first: Hibernate otherwise inserts before deletes under unique constraints.
        eventClassParticipantRepository.deleteAll(removedParticipants);
        eventClassParticipantRepository.flush();
        eventClassRepository.deleteAll(removedClasses);
        eventClassRepository.flush();
        releaseChangedClassNames(updatedClasses);
        List<EventClass> classesToPersist = new ArrayList<>();
        for (UpdatedEventClass item : updatedClasses) {
            if (!item.editable()) continue;
            EventClass eventClass = item.eventClass();
            eventClass.setName(item.request().name());
            eventClass.setStartDate(item.request().startDate());
            eventClass.setEndDate(item.request().endDate());
            classesToPersist.add(eventClass);
        }
        eventClassRepository.saveAll(classesToPersist);
        eventClassRepository.flush();
        List<EventClassParticipant> retained = new ArrayList<>(existingParticipants);
        retained.removeAll(removedParticipants);
        List<EventClassParticipant> additions = new ArrayList<>();
        Map<Long, Set<Long>> retainedByClass = retained.stream().collect(Collectors.groupingBy(
                participant -> participant.getEventClass().getId(),
                Collectors.mapping(participant -> participant.getUser().getId(), Collectors.toSet())));
        for (UpdatedEventClass item : updatedClasses) {
            if (!item.editable()) continue;
            Set<Long> retainedIds = retainedByClass.getOrDefault(item.eventClass().getId(), Set.of());
            for (Long userId : item.request().participantIds()) {
                if (!retainedIds.contains(userId)) additions.add(createClassParticipant(item.eventClass(), users.get(userId)));
            }
        }
        eventClassParticipantRepository.saveAll(additions);
        retained.addAll(additions);
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setExternalLink(request.externalLink());
        eventRepository.saveAndFlush(event);
        return toResponse(event, updatedClasses.stream().map(UpdatedEventClass::eventClass).toList(), retained, manager);
    }

    private void ensureMetadataEditable(Event event, UpdateEventRequest request,
                                        List<EventClass> existingClasses, LocalDateTime editTime) {
        boolean started = existingClasses.stream().anyMatch(eventClass -> !editTime.isBefore(eventClass.getStartDate()));
        if (started && (!Objects.equals(event.getTitle(), request.title())
                || !Objects.equals(event.getDescription(), request.description())
                || !Objects.equals(event.getExternalLink(), request.externalLink()))) {
            throw new BusinessException("Título, descrição e link não podem ser alterados após o início de uma turma");
        }
    }

    private void releaseChangedClassNames(List<UpdatedEventClass> updatedClasses) {
        boolean changed = false;
        Set<String> reservedNames = updatedClasses.stream().map(item -> item.request().name()).collect(Collectors.toSet());
        for (UpdatedEventClass item : updatedClasses) {
            EventClass eventClass = item.eventClass();
            reservedNames.add(eventClass.getName());
        }
        for (UpdatedEventClass item : updatedClasses) {
            EventClass eventClass = item.eventClass();
            if (item.editable() && eventClass.getId() != null && !eventClass.getName().equals(item.request().name())) {
                String temporaryName;
                do { temporaryName = "_" + java.util.UUID.randomUUID(); } while (!reservedNames.add(temporaryName));
                eventClass.setName(temporaryName);
                changed = true;
            }
        }
        // Transactional temporary names allow swaps without violating uq_turma_evento_nome.
        if (changed) eventClassRepository.flush();
    }

    @Transactional
    public EventResponse cancel(Long eventId, CancelEventRequest request, String firebaseUid) {
        User manager = findAuthenticatedManager(firebaseUid);
        Event event = findLockedEventInWorkspace(eventId, getWorkspace(manager));
        ensureManagerAndEditable(event, manager);

        if (request.reason() == null
                || request.reason().codePointCount(0, request.reason().length()) < 3
                || request.reason().length() > 1000) {
            throw new BusinessException("Motivo do cancelamento deve ter entre 3 e 1000 caracteres");
        }
        event.setStatus(EventStatus.CANCELADO);
        event.setCancellationDate(clock.instant());
        event.setCancellationReason(request.reason());
        eventRepository.saveAndFlush(event);
        return toResponse(event, eventClassRepository.findByEventOrderByStartDate(event), manager);
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
            List<UpdateEventClassRequest> classRequests, LocalDateTime editTime) {
        if (classRequests == null || classRequests.isEmpty()) throw new BusinessException("Evento deve possuir ao menos uma turma");
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
        Set<String> classNames = new HashSet<>();
        for (UpdateEventClassRequest classRequest : classRequests) {
            if (classRequest == null) throw new BusinessException("Turma não pode ser nula");
            validateClassName(classRequest.name(), classNames);
            if (classRequest.participantIds() == null) throw new BusinessException("Turma deve possuir ao menos um participante");
            validateUniqueParticipants(classRequest.participantIds(), new HashSet<>(classRequest.participantIds()));
            EventClass eventClass = classRequest.id() == null
                    ? newEventClass(event)
                    : getExistingEventClass(classRequest.id(), existingClassesById, requestedClassIds);
            validateDates(classRequest.startDate(), classRequest.endDate());
            if (eventClass.getId() != null && !editTime.isBefore(eventClass.getStartDate())) {
                ensureStartedClassIsUnchanged(
                        eventClass,
                        classRequest,
                        existingParticipantIdsByClassId.getOrDefault(eventClass.getId(), Set.of()));
                updatedClasses.add(new UpdatedEventClass(eventClass, classRequest, false));
                continue;
            }
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
            for (Long participantId : uniqueClassParticipantIds) {
                if (!allParticipantIds.add(participantId)) throw new BusinessException("Participante repetido em duas turmas do evento");
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

    private void validateClassesForCreation(List<CreateEventClassRequest> classRequests) {
        if (classRequests == null || classRequests.isEmpty()) {
            throw new BusinessException("Evento deve possuir ao menos uma turma");
        }
        Set<Long> allParticipantIds = new HashSet<>();
        Set<String> classNames = new HashSet<>();
        for (CreateEventClassRequest classRequest : classRequests) {
            if (classRequest == null) throw new BusinessException("Turma não pode ser nula");
            validateClassName(classRequest.name(), classNames);
            if (classRequest.participantIds() == null || classRequest.participantIds().isEmpty()) {
                throw new BusinessException("Turma deve possuir ao menos um participante");
            }
            validateDates(classRequest.startDate(), classRequest.endDate());
            Set<Long> classParticipantIds = new HashSet<>(classRequest.participantIds());
            validateUniqueParticipants(classRequest.participantIds(), classParticipantIds);
            for (Long participantId : classParticipantIds) {
                if (!allParticipantIds.add(participantId)) {
                    throw new BusinessException("Participante repetido em duas turmas do evento");
                }
            }
        }
    }

    private void validateClassName(String name, Set<String> names) {
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new BusinessException("Nome da turma deve ter entre 1 e 255 caracteres");
        }
        if (!names.add(name.trim())) throw new BusinessException("Nome de turma repetido no evento");
    }

    private void validateUniqueParticipants(List<Long> requestedIds, Set<Long> uniqueIds) {
        if (requestedIds == null || uniqueIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException("Identificador do participante deve ser positivo");
        }
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
        List<User> participants = userRepository.findEventParticipantsInWorkspace(participantIds, workspace);
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
                .filter(user -> user.getStatus() == UserStatus.ATIVO
                        && (user.getType() == UserType.GESTOR || user.getType() == UserType.GESTOR_WORKSPACE))
                .orElseThrow(() -> new AccessDeniedException("Gestor ativo necessário"));
    }

    private User findAuthenticatedUser(String firebaseUid) {
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
        if (eventId == null || eventId <= 0) throw new BusinessException("Identificador do evento deve ser positivo");
        return eventRepository.findByIdAndManager_Unit_Workspace(eventId, workspace)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado"));
    }

    private Event findLockedEventInWorkspace(Long eventId, Workspace workspace) {
        if (eventId == null || eventId <= 0) throw new BusinessException("Identificador do evento deve ser positivo");
        return eventRepository.findLockedInWorkspace(eventId, workspace)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado"));
    }

    private void ensureManagerAndEditable(Event event, User manager) {
        if (!event.getManager().getId().equals(manager.getId())) {
            throw new AccessDeniedException("Operação não autorizada para este evento");
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

    private void validatePeriod(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            throw new BusinessException("Data final deve ser posterior à data inicial");
        }
    }

    private String classStatus(EventClass eventClass, LocalDateTime now) {
        if (eventClass.getEvent().getStatus() == EventStatus.CANCELADO) {
            return "CANCELADO";
        }
        return eventClass.getEndDate().isBefore(now) ? "CONCLUIDO" : "PENDENTE";
    }

    private EventResponse toResponse(Event event, List<EventClass> eventClasses, User manager) {
        List<EventClassParticipant> classParticipants = eventClassParticipantRepository
                .findByEventClassIn(eventClasses);
        return toResponse(event, eventClasses, classParticipants, manager);
    }

    private EventResponse toResponse(
            Event event,
            List<EventClass> eventClasses,
            List<EventClassParticipant> classParticipants, User manager) {
        boolean ownerAndActive = event.getStatus() == EventStatus.ATIVO
                && event.getManager().getId().equals(manager.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        boolean detailsEditable = ownerAndActive
                && eventClasses.stream().allMatch(eventClass -> now.isBefore(eventClass.getStartDate()));
        Set<Long> editableClassIds = ownerAndActive ? eventClasses.stream()
                .filter(eventClass -> now.isBefore(eventClass.getStartDate()))
                .map(EventClass::getId).collect(Collectors.toSet()) : Set.of();
        // An active owner may always add future classes, even when all current classes started.
        return eventMapper.toResponse(event, eventClasses, classParticipants,
                ownerAndActive, ownerAndActive, detailsEditable, editableClassIds);
    }

    private record UpdatedEventClass(
            EventClass eventClass,
            UpdateEventClassRequest request,
            boolean editable) {
    }

}
