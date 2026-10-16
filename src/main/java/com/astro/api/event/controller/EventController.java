package com.astro.api.event.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.CollaboratorEventResponse;
import com.astro.api.event.dto.response.CollaboratorEventDetailsResponse;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Lista as turmas para o calendário administrativo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Turmas retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
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

    @GetMapping("/collaborator")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Lista os eventos do colaborador autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
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
    @Operation(summary = "Retorna os detalhes do evento na turma do colaborador autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado para o colaborador")
    })
    public ResponseEntity<ApiResult<CollaboratorEventDetailsResponse>> findCollaboratorEventDetails(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        CollaboratorEventDetailsResponse event = eventService.findDetailsForCollaborator(
                eventId, authenticatedUser.firebaseUid());
        return ResponseEntity.ok(ApiResult.success(
                "Detalhes do evento retornados com sucesso", event, request.getRequestURI()));
    }

    @GetMapping("/eligible-candidates")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Lista colaboradores elegíveis para compor as turmas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Colaboradores retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "NR inválida"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
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
    @Operation(summary = "Cria um evento completo com turmas e participantes")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evento criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados ou regras de negócio inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
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
    @Operation(summary = "Atualiza um evento e suas turmas futuras")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Evento ou turma não pode ser alterado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado no Workspace"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
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
    @Operation(summary = "Cancela definitivamente um evento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento cancelado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Evento concluído ou já cancelado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado no Workspace"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
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
