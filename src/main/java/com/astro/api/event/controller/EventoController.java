package com.astro.api.event.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.event.dto.request.*;
import com.astro.api.event.dto.response.CandidatoEventoResponse;
import com.astro.api.event.dto.response.EventoResponse;
import com.astro.api.event.service.EventoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/eventos")
@PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
public class EventoController {
    private final EventoService eventoService;

    public EventoController(EventoService eventoService) { this.eventoService = eventoService; }

    @PostMapping
    @Operation(summary = "Cria um evento com turmas e participantes")
    public ResponseEntity<ApiResult<EventoResponse>> criar(@Valid @RequestBody CreateEventoRequest body,
                                                             @AuthenticationPrincipal AuthenticatedUser usuario,
                                                             HttpServletRequest request) {
        EventoResponse evento = eventoService.criar(body, usuario.firebaseUid());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success("Evento criado com sucesso", evento, request.getRequestURI()));
    }

    @GetMapping
    @Operation(summary = "Lista eventos do Workspace autenticado por período das turmas")
    public ResponseEntity<ApiResult<List<EventoResponse>>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @AuthenticationPrincipal AuthenticatedUser usuario, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Eventos retornados com sucesso", eventoService.listar(inicio, fim, usuario.firebaseUid()), request.getRequestURI()));
    }

    @GetMapping("/{eventoId}")
    public ResponseEntity<ApiResult<EventoResponse>> buscar(@PathVariable Long eventoId, @AuthenticationPrincipal AuthenticatedUser usuario, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Evento retornado com sucesso", eventoService.buscar(eventoId, usuario.firebaseUid()), request.getRequestURI()));
    }

    @GetMapping("/{eventoId}/candidatos")
    @Operation(summary = "Lista candidatos elegíveis conforme a NR já configurada no evento")
    public ResponseEntity<ApiResult<List<CandidatoEventoResponse>>> candidatos(@PathVariable Long eventoId, @AuthenticationPrincipal AuthenticatedUser usuario, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Candidatos retornados com sucesso", eventoService.candidatos(eventoId, usuario.firebaseUid()), request.getRequestURI()));
    }

    @PatchMapping("/{eventoId}")
    public ResponseEntity<ApiResult<EventoResponse>> atualizar(@PathVariable Long eventoId, @Valid @RequestBody UpdateEventoRequest body,
                                                                 @AuthenticationPrincipal AuthenticatedUser usuario, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Evento atualizado com sucesso", eventoService.atualizarEvento(eventoId, body, usuario.firebaseUid()), request.getRequestURI()));
    }

    @PatchMapping("/{eventoId}/turmas/{turmaId}")
    public ResponseEntity<ApiResult<EventoResponse>> atualizarTurma(@PathVariable Long eventoId, @PathVariable Long turmaId,
                                                                      @Valid @RequestBody UpdateTurmaRequest body,
                                                                      @AuthenticationPrincipal AuthenticatedUser usuario, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Turma atualizada com sucesso", eventoService.atualizarTurma(eventoId, turmaId, body, usuario.firebaseUid()), request.getRequestURI()));
    }

    @PutMapping("/{eventoId}/turmas/{turmaId}/participantes")
    public ResponseEntity<ApiResult<EventoResponse>> substituirParticipantes(@PathVariable Long eventoId, @PathVariable Long turmaId,
                                                                               @Valid @RequestBody UpdateParticipantesTurmaRequest body,
                                                                               @AuthenticationPrincipal AuthenticatedUser usuario, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Participantes atualizados com sucesso", eventoService.substituirParticipantes(eventoId, turmaId, body, usuario.firebaseUid()), request.getRequestURI()));
    }

    @PostMapping("/{eventoId}/cancelamento")
    public ResponseEntity<ApiResult<EventoResponse>> cancelar(@PathVariable Long eventoId, @Valid @RequestBody CancelEventoRequest body,
                                                                @AuthenticationPrincipal AuthenticatedUser usuario, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Evento cancelado com sucesso", eventoService.cancelar(eventoId, body, usuario.firebaseUid()), request.getRequestURI()));
    }
}
