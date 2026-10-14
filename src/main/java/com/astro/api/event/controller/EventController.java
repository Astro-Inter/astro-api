package com.astro.api.event.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.EventCalendarResponse;
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
    @Operation(summary = "Lista as turmas para o calendário administrativo",
            description = "Workspace autenticado; sobreposição inclusiva: término da turma >= startDate e início <= endDate. Datas ISO locais (yyyy-MM-ddTHH:mm:ss), sem conversão de fuso. Ordenação por início e ID; inclui cancelados com status explícito.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Turmas retornadas no envelope ApiResult, lista vazia se não houver resultados"),
            @ApiResponse(responseCode = "400", description = "Datas obrigatórias, formato ou intervalo inválido"),
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

    @GetMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Consulta o detalhe completo de um evento",
            description = "Consulta no workspace autenticado, incluindo todas as turmas. Somente o criador de evento ativo pode editar/cancelar. detailsEditable bloqueia metadados após o início de qualquer turma; editable indica turmas futuras. canEdit permite adicionar turmas futuras mesmo quando as existentes já iniciaram.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe no envelope ApiResult"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Gestor ativo necessário"),
            @ApiResponse(responseCode = "404", description = "Evento inexistente ou fora do workspace")
    })
    public ResponseEntity<ApiResult<EventResponse>> findById(
            @PathVariable Long eventId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Evento retornado com sucesso",
                eventService.findById(eventId, authenticatedUser.firebaseUid()), request.getRequestURI()));
    }

    @GetMapping("/eligible-candidates")
    @PreAuthorize("hasAnyRole('GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Lista usuários elegíveis para compor as turmas",
            description = "Usuários ativos do workspace autenticado, incluindo outros gestores e excluindo o solicitante. nrId é o ID persistido do catálogo; filtra a associação Role/NR. Retorna unidade e modalidade canônica, incluindo HIBRIDO; ordenado por nome e ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Colaboradores retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Identificador da NR inválido"),
            @ApiResponse(responseCode = "404", description = "NR não encontrada"),
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
    @Operation(summary = "Cria um evento completo com turmas e participantes",
            description = "Criação atômica como ATIVO, com criador e workspace derivados da autenticação. Título: 3–255; descrição: mínimo 3; link HTTP(S): máximo 2048. Ao menos uma turma e um participante ativo/elegível por turma; nomes únicos e participantes sem repetição em todo o evento. Datas locais, término posterior ao início; datas passadas continuam permitidas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Detalhe persistido no envelope ApiResult"),
            @ApiResponse(responseCode = "404", description = "NR ou participante não encontrado no workspace"),
            @ApiResponse(responseCode = "409", description = "Conflito de integridade dos dados"),
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
    @Operation(summary = "Atualiza um evento e suas turmas futuras",
            description = "Somente o criador de evento ATIVO no workspace autenticado. NR, conclusão, evidência e criador são imutáveis. Metadados bloqueados após qualquer turma iniciar; turmas iniciadas devem permanecer inalteradas. eventClasses representa o conjunto desejado: IDs existentes pertencem ao evento, sem repetição; sem ID adiciona turma; omissão remove futuras. Participações mantidas preservam IDs, conclusões e evidências; participações com conclusão não podem ser removidas. Escrita atômica e serializada por evento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe atualizado no envelope ApiResult"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "409", description = "Conflito de integridade dos dados"),
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
    @Operation(summary = "Cancela definitivamente um evento",
            description = "A ação Inativar do web corresponde ao cancelamento definitivo. Somente o criador de evento ATIVO no workspace pode cancelar. Justificativa obrigatória (3–1000 caracteres após trim); registra status, instante e motivo. Preserva turmas, participações, conclusões e evidências; não permite reativação. Operação atômica e serializada com a edição.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe cancelado no envelope ApiResult, com permissões falsas"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
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
