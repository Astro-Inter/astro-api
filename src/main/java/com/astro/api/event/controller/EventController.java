package com.astro.api.event.controller;

import com.astro.api.event.swagger.EventSwagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.CompletionRejectionRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.CollaboratorEventResponse;
import com.astro.api.event.dto.response.CollaboratorEventDetailsResponse;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.dto.response.EventCompletionResponse;
import com.astro.api.event.dto.response.EventParticipantStatusResponse;
import com.astro.api.event.dto.response.ManagerCompletionModeEventDetailsResponse;
import com.astro.api.event.dto.response.CollaboratorCompletionModeEventDetailsResponse;
import com.astro.api.event.dto.response.MyEventResponse;
import com.astro.api.event.dto.response.ManagerEventParticipantDetailsResponse;
import com.astro.api.event.dto.response.ManagerCollaboratorCompletionDetailsResponse;
import com.astro.api.event.service.EventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController implements EventSwagger {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/my-events")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<List<MyEventResponse>>> findMyEvents(
             @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        List<MyEventResponse> events = eventService.findMyEvents(authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Eventos do gestor retornados com sucesso", events, request.getRequestURI()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<List<EventCalendarResponse>>> findAll(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        List<EventCalendarResponse> calendarEvents = eventService.findAll(
                startDate,
                endDate,
                authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Turmas retornadas com sucesso", calendarEvents, request.getRequestURI()));
    }

    @GetMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<EventResponse>> findById(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Evento retornado com sucesso",
                eventService.findById(eventId, authenticatedUser.firebaseUid()), request.getRequestURI()));
    }

    @GetMapping("/{eventId}/participants")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<List<EventParticipantStatusResponse>>> findParticipantsWithStatus(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        List<EventParticipantStatusResponse> participants = eventService.findParticipantsWithStatus(
                eventId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Participantes retornados com sucesso", participants, request.getRequestURI()));
    }

    @GetMapping("/participant/{participantId}/manager-conclusion/{eventId}")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<ManagerEventParticipantDetailsResponse>> findParticipantDetailsForManager(
            @PathVariable Long participantId,
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        ManagerEventParticipantDetailsResponse participant = eventService.findParticipantDetailsForManager(
                participantId, eventId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Dados do participante retornados com sucesso", participant, request.getRequestURI()));
    }

    @GetMapping("/participant/{participantId}/collaborator-conclusion/{eventId}")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<ManagerCollaboratorCompletionDetailsResponse>> findCollaboratorCompletionDetails(
            @PathVariable Long participantId,
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        ManagerCollaboratorCompletionDetailsResponse completion =
                eventService.findCollaboratorCompletionDetailsForManager(
                        participantId, eventId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Detalhes da conclusão retornados com sucesso", completion, request.getRequestURI()));
    }

    @PostMapping("/{eventId}/participants/{participantId}/manager-conclusion")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<EventCompletionResponse>> markParticipantCompletionByManager(
            @PathVariable Long eventId,
            @PathVariable Long participantId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        EventCompletionResponse completion = eventService.markParticipantCompletionByManager(
                eventId, participantId, authenticatedUser.firebaseUid());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(
                "Conclusão do participante registrada", completion, request.getRequestURI()));
    }

    @PatchMapping("/{eventId}/participants/{participantId}/rejection")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<EventCompletionResponse>> rejectCollaboratorCompletion(
            @PathVariable Long eventId,
            @PathVariable Long participantId,
            @Valid @RequestBody CompletionRejectionRequest body,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        EventCompletionResponse completion = eventService.rejectCollaboratorCompletionByManager(
                eventId, participantId, body.rejectionReason(), authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Conclusão do colaborador rejeitada", completion, request.getRequestURI()));
    }

    @PatchMapping("/{eventId}/participants/{participantId}/collaborator-conclusion/approval")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<EventCompletionResponse>> approveCollaboratorCompletion(
            @PathVariable Long eventId,
            @PathVariable Long participantId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        EventCompletionResponse completion = eventService.approveCollaboratorCompletionByManager(
                eventId, participantId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Conclusão do colaborador aprovada", completion, request.getRequestURI()));
    }

    @GetMapping("/details/{eventId}/manager-conclusion")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<ManagerCompletionModeEventDetailsResponse>> findManagerEventDetails(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        ManagerCompletionModeEventDetailsResponse event = eventService.findDetailsForManager(
                eventId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Detalhes do evento retornados com sucesso", event, request.getRequestURI()));
    }

    @GetMapping("/details/{eventId}/collaborator-conclusion")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<CollaboratorCompletionModeEventDetailsResponse>> findCollaboratorCompletionEventDetails(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        CollaboratorCompletionModeEventDetailsResponse event = eventService.findDetailsForCollaboratorCompletion(
                eventId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Detalhes do evento retornados com sucesso", event, request.getRequestURI()));
    }

    @GetMapping("/collaborator")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<List<CollaboratorEventResponse>>> findForCollaborator(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        List<CollaboratorEventResponse> events = eventService.findForCollaborator(
                authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Eventos do colaborador retornados com sucesso", events, request.getRequestURI()));
    }

    @GetMapping("/collaborator/{eventId}")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<CollaboratorEventDetailsResponse>> findCollaboratorEventDetails(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        CollaboratorEventDetailsResponse event = eventService.findDetailsForCollaborator(
                eventId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Detalhes do evento retornados com sucesso", event, request.getRequestURI()));
    }

    @PatchMapping("/collaborator/{eventId}/viewed")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<Void>> markCollaboratorEventAsViewed(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        eventService.markCollaboratorEventViewed(eventId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success("Evento marcado como visualizado", null,
                request.getRequestURI()));
    }

    @PostMapping(value = "/{eventId}/completion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<EventCompletionResponse>> requestCompletion(
            @PathVariable Long eventId,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        EventCompletionResponse completion = eventService.requestCompletion(
                eventId, files, authenticatedUser.firebaseUid());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(
                "Solicitação de conclusão enviada para validação", completion, request.getRequestURI()));
    }

    @GetMapping("/eligible-candidates")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<List<EventCandidateResponse>>> findEligibleCandidates(
            @RequestParam(required = false) Integer nrId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        List<EventCandidateResponse> candidates = eventService.findEligibleCandidates(
                nrId,
                authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Colaboradores retornados com sucesso", candidates, request.getRequestURI()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<EventResponse>> create(
            @Valid @RequestBody CreateEventRequest body,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        EventResponse event = eventService.create(body, authenticatedUser.firebaseUid());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(
                "Evento criado com sucesso", event, request.getRequestURI()));
    }

    @PatchMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<EventResponse>> update(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventRequest body,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        EventResponse event = eventService.update(eventId, body, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Evento atualizado com sucesso", event, request.getRequestURI()));
    }

    @PatchMapping("/{eventId}/cancellation")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<EventResponse>> cancel(
            @PathVariable Long eventId,
            @Valid @RequestBody CancelEventRequest body,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        EventResponse event = eventService.cancel(eventId, body, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Evento cancelado com sucesso", event, request.getRequestURI()));
    }
}
