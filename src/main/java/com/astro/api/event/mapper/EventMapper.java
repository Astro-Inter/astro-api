package com.astro.api.event.mapper;

import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.CollaboratorEventResponse;
import com.astro.api.event.dto.response.EventClassResponse;
import com.astro.api.event.dto.response.EventParticipantResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.model.Event;
import com.astro.api.event.model.EventClass;
import com.astro.api.event.model.EventClassParticipant;
import com.astro.api.user.model.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EventMapper {

    public EventResponse toResponse(
            Event event,
            List<EventClass> eventClasses,
            List<EventClassParticipant> classParticipants) {
        Map<Long, List<EventParticipantResponse>> participantsByClassId = classParticipants.stream()
                .collect(Collectors.groupingBy(
                        classParticipant -> classParticipant.getEventClass().getId(),
                        Collectors.mapping(this::toParticipantResponse, Collectors.toList())));
        List<EventClassResponse> classResponses = eventClasses.stream()
                .map(eventClass -> toClassResponse(
                        eventClass,
                        participantsByClassId.getOrDefault(eventClass.getId(), List.of())))
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
                classResponses);
    }

    public EventCandidateResponse toCandidateResponse(User user) {
        Long roleId = user.getRole() == null ? null : user.getRole().getId();
        String roleName = user.getRole() == null ? null : user.getRole().getName();
        return new EventCandidateResponse(user.getId(), user.getName(), user.getEmail(), roleId, roleName);
    }

    public EventCalendarResponse toCalendarResponse(
            Event event,
            EventClass eventClass) {
        return new EventCalendarResponse(
                event.getId(),
                event.getTitle(),
                eventClass.getId(),
                eventClass.getName(),
                eventClass.getStartDate(),
                eventClass.getEndDate());
    }

    public CollaboratorEventResponse toCollaboratorEventResponse(
            EventClassParticipant participation,
            boolean viewed,
            String classStatus) {
        EventClass eventClass = participation.getEventClass();
        Event event = eventClass.getEvent();
        return new CollaboratorEventResponse(
                event.getTitle(),
                eventClass.getEndDate(),
                viewed,
                classStatus,
                eventClass.getName(),
                event.getStatus());
    }

    private EventParticipantResponse toParticipantResponse(EventClassParticipant classParticipant) {
        User user = classParticipant.getUser();
        return new EventParticipantResponse(user.getId(), user.getName(), user.getEmail());
    }

    private EventClassResponse toClassResponse(
            EventClass eventClass,
            List<EventParticipantResponse> participants) {
        return new EventClassResponse(
                eventClass.getId(),
                eventClass.getName(),
                eventClass.getStartDate(),
                eventClass.getEndDate(),
                participants);
    }
}
