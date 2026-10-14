package com.astro.api.event.mapper;

import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.EventClassResponse;
import com.astro.api.event.dto.response.EventParticipantResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.model.Event;
import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventClassParticipant;
import com.astro.api.user.model.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EventMapper {

    public EventResponse toResponse(
            Event event,
            List<EventClass> eventClasses,
            List<EventClassParticipant> classParticipants,
            boolean canEdit, boolean canCancel, boolean detailsEditable, Set<Long> editableClassIds) {
        Comparator<EventClassParticipant> participantOrder = Comparator
                .comparing((EventClassParticipant participant) -> participant.getUser().getName())
                .thenComparing(participant -> participant.getUser().getId());
        Map<Long, List<EventParticipantResponse>> participantsByClassId = classParticipants.stream().sorted(participantOrder)
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

    public EventCandidateResponse toCandidateResponse(User user) {
        Long roleId = user.getRole() == null ? null : user.getRole().getId();
        String roleName = user.getRole() == null ? null : user.getRole().getName();
        return new EventCandidateResponse(user.getId(), user.getName(), user.getEmail(), roleId, roleName);
    }

    public EventCalendarResponse toCalendarResponse(
            com.astro.api.event.repository.EventCalendarProjection row) {
        return new EventCalendarResponse(row.getEventId(), row.getEventTitle(), row.getEventClassId(),
                row.getClassName(), row.getStartDate(), row.getEndDate(), row.getStatus());
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
