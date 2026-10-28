package com.astro.api.event.mapper;

import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.CollaboratorEventResponse;
import com.astro.api.event.dto.response.EventClassResponse;
import com.astro.api.event.dto.response.EventCompletionResponse;
import com.astro.api.event.dto.response.EventEvidenceResponse;
import com.astro.api.event.dto.response.EventParticipantResponse;
import com.astro.api.event.dto.response.EventParticipantStatusResponse;
import com.astro.api.event.dto.response.ManagerEventParticipantDetailsResponse;
import com.astro.api.event.dto.response.ManagerCollaboratorCompletionDetailsResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.dto.response.ManagerCompletionModeEventDetailsResponse;
import com.astro.api.event.dto.response.MyEventResponse;
import com.astro.api.event.model.Event;
import com.astro.api.event.model.CompletionMode;
import com.astro.api.event.model.EventStatus;
import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventClassParticipant;
import com.astro.api.event.model.EventCompletion;
import com.astro.api.event.model.EventEvidence;
import com.astro.api.event.model.ParticipationStatus;
import com.astro.api.event.repository.EventCalendarProjection;
import com.astro.api.event.repository.EventCandidateProjection;
import com.astro.api.event.repository.MyEventProjection;
import com.astro.api.user.model.User;
import com.astro.api.user.model.WorkModel;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EventMapper {
    private static final Comparator<EventClassParticipant> PARTICIPANT_ORDER = Comparator
            .comparing((EventClassParticipant participant) -> participant.getUser().getName())
            .thenComparing(participant -> participant.getUser().getId());

    public MyEventResponse toMyEventResponse(MyEventProjection row) {
        return new MyEventResponse(row.getId(), row.getTitle(), EventStatus.valueOf(row.getStatus()),
                CompletionMode.valueOf(row.getCompletionMode()), row.getPendingCount());
    }

    public EventCompletionResponse toCompletionResponse(EventCompletion completion) {
        return new EventCompletionResponse(
                completion.getId(), completion.getStatus(), completion.getCompletionDate());
    }

    public EventResponse toResponse(
            Event event,
            List<EventClass> eventClasses,
            List<EventClassParticipant> classParticipants,
            boolean canEdit, boolean canCancel, boolean detailsEditable, Set<Long> editableClassIds) {
        Map<Long, List<EventParticipantResponse>> participantsByClassId = classParticipants.stream().sorted(PARTICIPANT_ORDER)
                .collect(Collectors.groupingBy(
                        classParticipant -> classParticipant.getEventClass().getId(),
                        Collectors.mapping(this::toParticipantResponse, Collectors.toList())));
        List<EventClassResponse> classResponses = eventClasses.stream()
                .sorted(Comparator.comparing(EventClass::getStartDate)
                        .thenComparing(EventClass::getId))
                .map(eventClass -> toClassResponse(
                        eventClass,
                        participantsByClassId.getOrDefault(eventClass.getId(), List.of()),
                        editableClassIds.contains(eventClass.getId())))
                .toList();

        return new EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getNrId(),
                event.getExternalLink(),
                event.getCompletionMode(),
                event.isMandatoryEvidence(),
                event.getStatus(),
                event.getCancellationDate(),
                event.getCancellationReason(),
                event.getManager().getId(),
                canEdit, canCancel, detailsEditable,
                classResponses);
    }

    public EventCandidateResponse toCandidateResponse(
            EventCandidateProjection row) {
        return new EventCandidateResponse(row.getId(), row.getName(), row.getEmail(), row.getRoleId(),
                row.getRoleName(), row.getUnitId(), row.getUnitName(), row.getWorkModel() == null ? null
                : WorkModel.valueOf(row.getWorkModel()));
    }

    public EventCalendarResponse toCalendarResponse(
            EventCalendarProjection row) {
        return new EventCalendarResponse(row.getEventId(), row.getEventTitle(), row.getEventClassId(),
                row.getClassName(), row.getStartDate(), row.getEndDate(), row.getStatus());
    }

    public CollaboratorEventResponse toCollaboratorEventResponse(
            EventClassParticipant participation,
            boolean viewed,
            ParticipationStatus participationStatus) {
        EventClass eventClass = participation.getEventClass();
        Event event = eventClass.getEvent();
        return new CollaboratorEventResponse(
                event.getId(),
                event.getTitle(),
                eventClass.getEndDate(),
                viewed,
                participationStatus,
                eventClass.getName(),
                event.getStatus());
    }

    public EventEvidenceResponse toEvidenceResponse(EventEvidence evidence, String downloadUrl) {
        return new EventEvidenceResponse(
                evidence.getId(),
                evidence.getOriginalName(),
                downloadUrl,
                evidence.getMimeType(),
                evidence.getSizeBytes());
    }

    public EventParticipantStatusResponse toParticipantStatusResponse(
            EventClassParticipant participation,
            ParticipationStatus participationStatus,
            String profilePhotoUrl) {
        return new EventParticipantStatusResponse(
                participation.getUser().getId(),
                participation.getUser().getName(),
                participation.getEventClass().getEvent().getTitle(),
                participationStatus,
                profilePhotoUrl);
    }

    public ManagerEventParticipantDetailsResponse toManagerEventParticipantDetailsResponse(
            EventClassParticipant participation,
            String profilePhotoUrl) {
        return new ManagerEventParticipantDetailsResponse(
                participation.getUser().getId(),
                profilePhotoUrl,
                participation.getEventClass().getEvent().getTitle(),
                participation.getUser().getName(),
                participation.getUser().getRole() == null ? null : participation.getUser().getRole().getName(),
                participation.getUser().getUnit() == null ? null : participation.getUser().getUnit().name,
                participation.getEventClass().getEndDate());
    }

    public ManagerCollaboratorCompletionDetailsResponse toManagerCollaboratorCompletionDetailsResponse(
            EventClassParticipant participation,
            String profilePhotoUrl,
            EventCompletion completion,
            List<EventEvidenceResponse> evidences) {
        Event event = participation.getEventClass().getEvent();
        return new ManagerCollaboratorCompletionDetailsResponse(
                profilePhotoUrl,
                participation.getUser().getName(),
                participation.getUser().getRole() == null ? null : participation.getUser().getRole().getName(),
                participation.getUser().getUnit() == null ? null : participation.getUser().getUnit().name,
                completion.getCompletionDate(),
                participation.getEventClass().getEndDate(),
                event.isMandatoryEvidence(),
                evidences,
                event.getTitle());
    }

    public CommonEventDetails toCommonEventDetails(Event event) {
        return new CommonEventDetails(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getExternalLink(),
                event.getCompletionMode(),
                event.getManager().getId(),
                event.getManager().getName());
    }

    public ManagerCompletionModeEventDetailsResponse toManagerCompletionModeEventDetailsResponse(
            CommonEventDetails commonDetails,
            LocalDateTime lastClassLimitDate,
            long participantCount) {
        return new ManagerCompletionModeEventDetailsResponse(
                commonDetails.eventId(),
                commonDetails.eventTitle(),
                commonDetails.eventDescription(),
                commonDetails.externalLink(),
                participantCount,
                lastClassLimitDate,
                commonDetails.completionMode(),
                commonDetails.managerId(),
                commonDetails.managerName());
    }

    public record CommonEventDetails(
            Long eventId,
            String eventTitle,
            String eventDescription,
            String externalLink,
            CompletionMode completionMode,
            Long managerId,
            String managerName) {
    }

    private EventParticipantResponse toParticipantResponse(EventClassParticipant classParticipant) {
        User user = classParticipant.getUser();
        return new EventParticipantResponse(user.getId(), user.getName(), user.getEmail(), classParticipant.getId());
    }

    private EventClassResponse toClassResponse(
            EventClass eventClass,
            List<EventParticipantResponse> participants, boolean editable) {
        return new EventClassResponse(
                eventClass.getId(),
                eventClass.getName(),
                eventClass.getStartDate(),
                eventClass.getEndDate(),
                editable,
                participants);
    }
}
