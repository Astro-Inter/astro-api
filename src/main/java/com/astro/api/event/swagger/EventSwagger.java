package com.astro.api.event.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.event.dto.request.CancelEventRequest;
import com.astro.api.event.dto.request.CompletionRejectionRequest;
import com.astro.api.event.dto.request.CreateEventRequest;
import com.astro.api.event.dto.request.UpdateEventRequest;
import com.astro.api.event.dto.response.CollaboratorCompletionModeEventDetailsResponse;
import com.astro.api.event.dto.response.CollaboratorEventDetailsResponse;
import com.astro.api.event.dto.response.CollaboratorEventResponse;
import com.astro.api.event.dto.response.EventCalendarResponse;
import com.astro.api.event.dto.response.EventCandidateResponse;
import com.astro.api.event.dto.response.EventCompletionResponse;
import com.astro.api.event.dto.response.EventParticipantStatusResponse;
import com.astro.api.event.dto.response.EventResponse;
import com.astro.api.event.dto.response.ManagerCollaboratorCompletionDetailsResponse;
import com.astro.api.event.dto.response.ManagerCompletionModeEventDetailsResponse;
import com.astro.api.event.dto.response.ManagerEventParticipantDetailsResponse;
import com.astro.api.event.dto.response.MyEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Eventos e turmas", description = "Calendário, participantes, conclusões e evidências de eventos.")
public interface EventSwagger {

    @Operation(summary = "Lista os eventos criados pelo gestor autenticado",
            description = "Retorna todos os status em ordem decrescente de pendências, com ID decrescente como desempate. Os dados e pendências vêm da view vw_eventos_gestor; o gestor é identificado pela autenticação Firebase.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de eventos no envelope ApiResult, ou lista vazia",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResultListMyEventResponse"))),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Acesso permitido somente a gestores")
    })
    ResponseEntity<ApiResult<List<MyEventResponse>>> findMyEvents(
            @Parameter(hidden = true)  AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Lista as turmas para o calendário administrativo",
            description = "Workspace autenticado; sobreposição inclusiva: término da turma >= startDate e início <= endDate. Datas ISO locais (yyyy-MM-ddTHH:mm:ss), sem conversão de fuso. Ordenação por início e ID; inclui cancelados com status explícito.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Turmas retornadas no envelope ApiResult, lista vazia se não houver resultados",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResultListEventCalendarResponse"))),
            @ApiResponse(responseCode = "400", description = "Datas obrigatórias, formato ou intervalo inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
    ResponseEntity<ApiResult<List<EventCalendarResponse>>> findAll(
            @Parameter(description = "Início inclusivo do intervalo, data/hora local sem offset", example = "2026-11-01T00:00:00")
             LocalDateTime startDate,
            @Parameter(description = "Fim inclusivo, posterior ao início, sem offset", example = "2026-11-30T23:59:59")
             LocalDateTime endDate,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Consulta o detalhe completo de um evento",
            description = "Consulta no workspace autenticado, incluindo todas as turmas. Somente o criador de evento ativo pode editar/cancelar. detailsEditable bloqueia metadados após o início de qualquer turma; editable indica turmas futuras. canEdit permite adicionar turmas futuras mesmo quando as existentes já iniciaram.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe no envelope ApiResult",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResultEventResponse"))),
            @ApiResponse(responseCode = "400", description = "Identificador inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Gestor ativo necessário"),
            @ApiResponse(responseCode = "404", description = "Evento inexistente ou fora do workspace")
    })
    ResponseEntity<ApiResult<EventResponse>> findById(
            @Parameter(description = "ID persistido do evento no workspace autenticado", example = "1", schema = @Schema(minimum = "1"))
             Long eventId,
             AuthenticatedUser authenticatedUser, HttpServletRequest request);

    @Operation(summary = "Lista participantes de um evento e o status de conclusão de cada um",
            description = "Consulta no workspace autenticado. O status é PENDENTE quando ainda não houve solicitação, EM_ANALISE após envio, CONCLUIDO após aprovação e REJEITADO quando devolvido para correção.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participantes retornados no envelope ApiResult"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Gestor ativo necessário"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado no workspace")
    })
    ResponseEntity<ApiResult<List<EventParticipantStatusResponse>>> findParticipantsWithStatus(
            @Parameter(description = "ID persistido do evento no workspace autenticado", example = "1", schema = @Schema(minimum = "1"))
             Long eventId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Consulta os dados de um participante para conclusão pelo gestor",
            description = "Exige gestor ativo e evento no workspace autenticado. participantId é o ID do usuário participante.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados do participante retornados no envelope ApiResult"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Gestor ativo necessário"),
            @ApiResponse(responseCode = "404", description = "Evento fora do workspace ou participante não vinculado ao evento")
    })
    ResponseEntity<ApiResult<ManagerEventParticipantDetailsResponse>> findParticipantDetailsForManager(
            @Parameter(description = "ID do usuário participante", example = "25", schema = @Schema(minimum = "1"))
             Long participantId,
            @Parameter(description = "ID persistido do evento no workspace autenticado", example = "1", schema = @Schema(minimum = "1"))
             Long eventId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Consulta os detalhes da conclusão enviada pelo colaborador",
            description = "Exige gestor ativo e evento no workspace autenticado, em modo COLABORADOR, com conclusão registrada. Retorna o status da conclusão, a foto do participante e URLs temporárias das evidências.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes da conclusão retornados no envelope ApiResult"),
            @ApiResponse(responseCode = "400", description = "Evento não permite conclusão pelo colaborador"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Gestor ativo necessário"),
            @ApiResponse(responseCode = "404", description = "Evento, participação ou conclusão não encontrada"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<ManagerCollaboratorCompletionDetailsResponse>> findCollaboratorCompletionDetails(
            @Parameter(description = "ID do usuário participante", example = "25", schema = @Schema(minimum = "1"))
             Long participantId,
            @Parameter(description = "ID persistido do evento no workspace autenticado", example = "1", schema = @Schema(minimum = "1"))
             Long eventId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Marca a conclusão de um participante pelo gestor",
            description = "Disponível ao criador do evento ativo no modo GESTOR. A validade é a data de conclusão mais os meses de reciclagem da NR no MongoDB; sem NR, fica nula.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conclusão registrada no envelope ApiResult"),
            @ApiResponse(responseCode = "400", description = "Evento inativo, modo incompatível, conclusão já registrada ou tempo de reciclagem inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Gestor ativo e criador do evento necessários"),
            @ApiResponse(responseCode = "404", description = "Evento fora do workspace, participante não vinculado ou NR ausente no MongoDB"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<EventCompletionResponse>> markParticipantCompletionByManager(
             Long eventId,
             Long participantId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Rejeita a conclusão enviada por um colaborador",
            description = "Exige justificativa não vazia e gestor criador do evento. Disponível para conclusão pendente em evento no modo COLABORADOR, mesmo após o término da turma.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conclusão rejeitada no envelope ApiResult"),
            @ApiResponse(responseCode = "400", description = "Justificativa ausente ou conclusão não pendente"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Gestor ativo e criador do evento necessários"),
            @ApiResponse(responseCode = "404", description = "Evento, participante ou conclusão não encontrada"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<EventCompletionResponse>> rejectCollaboratorCompletion(
             Long eventId,
             Long participantId,
              CompletionRejectionRequest body,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Aprova a conclusão enviada pelo colaborador",
            description = "Exige gestor criador do evento e uma conclusão PENDENTE no modo COLABORADOR. Preserva as evidências enviadas e registra a validação.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conclusão aprovada no envelope ApiResult"),
            @ApiResponse(responseCode = "400", description = "Modo incompatível ou conclusão não pendente"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Gestor ativo e criador do evento necessários"),
            @ApiResponse(responseCode = "404", description = "Evento, participante ou conclusão não encontrada"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<EventCompletionResponse>> approveCollaboratorCompletion(
             Long eventId,
             Long participantId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Retorna os detalhes de um evento para conclusão pelo gestor",
            description = "Consulta no workspace autenticado. Retorna participantes, link externo, modo de conclusão e o prazo de término da última turma. A validade não é retornada neste fluxo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado no workspace"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<ManagerCompletionModeEventDetailsResponse>> findManagerEventDetails(
             Long eventId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Retorna os detalhes de um evento com conclusão pelo colaborador para o gestor",
            description = "Consulta no workspace autenticado, considerando todas as turmas. Inclui participantes, prazo da última turma e contagens de validações pendentes, feitas (aprovadas ou rejeitadas) e usuários sem envio. Reenvios voltam às pendentes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Identificador inválido ou evento sem conclusão pelo colaborador"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado no workspace"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<CollaboratorCompletionModeEventDetailsResponse>> findCollaboratorCompletionEventDetails(
             Long eventId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Lista os eventos do colaborador autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
    ResponseEntity<ApiResult<List<CollaboratorEventResponse>>> findForCollaborator(
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Retorna os detalhes do evento na turma do colaborador autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado para o colaborador")
    })
    ResponseEntity<ApiResult<CollaboratorEventDetailsResponse>> findCollaboratorEventDetails(
             Long eventId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Marca como visualizado um evento do colaborador autenticado",
            description = "Exige participação no evento. A operação é idempotente e não altera o status da conclusão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento marcado como visualizado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado para o colaborador"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<Void>> markCollaboratorEventAsViewed(
             Long eventId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Envia a solicitação de conclusão de um evento pelo colaborador",
            description = "Recebe de zero a três arquivos no campo opcional files. Cada arquivo pode ter até 50 MB; eventos que exigem evidência precisam receber ao menos um arquivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitação enviada para validação"),
            @ApiResponse(responseCode = "400", description = "Dados ou regras de negócio inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Evento ou participação não encontrada"),
            @ApiResponse(responseCode = "413", description = "Upload excede o limite permitido")
    })
    ResponseEntity<ApiResult<EventCompletionResponse>> requestCompletion(
             Long eventId,
             List<MultipartFile> files,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Lista usuários elegíveis para compor as turmas",
            description = "Usuários ativos do workspace autenticado, incluindo outros gestores e excluindo o solicitante. nrId é o ID persistido do catálogo; filtra a associação Role/NR. Retorna unidade e modalidade canônica, incluindo HIBRIDO; ordenado por nome e ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Colaboradores retornados com sucesso",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResultListEventCandidateResponse"))),
            @ApiResponse(responseCode = "400", description = "Identificador da NR inválido"),
            @ApiResponse(responseCode = "404", description = "NR não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
    ResponseEntity<ApiResult<List<EventCandidateResponse>>> findEligibleCandidates(
            @Parameter(description = "ID do catálogo de NRs, não o texto NR 10", example = "10", schema = @Schema(minimum = "1"))
             Integer nrId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Cria um evento completo com turmas e participantes",
            description = "Criação atômica como ATIVO, com criador e workspace derivados da autenticação. Título: 3–255; descrição: mínimo 3; link HTTP(S): máximo 2048. Ao menos uma turma e um participante ativo/elegível por turma; nomes únicos e participantes sem repetição em todo o evento. Datas locais, término posterior ao início; datas passadas continuam permitidas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Detalhe persistido no envelope ApiResult",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResultEventResponse"))),
            @ApiResponse(responseCode = "404", description = "NR ou participante não encontrado no workspace"),
            @ApiResponse(responseCode = "409", description = "Conflito de integridade dos dados"),
            @ApiResponse(responseCode = "400", description = "Dados ou regras de negócio inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
    ResponseEntity<ApiResult<EventResponse>> create(
              CreateEventRequest body,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Atualiza um evento e suas turmas futuras",
            description = "Somente o criador de evento ATIVO no workspace autenticado. NR, conclusão, evidência e criador são imutáveis. Metadados bloqueados após qualquer turma iniciar; turmas iniciadas devem permanecer inalteradas. eventClasses representa o conjunto desejado: IDs existentes pertencem ao evento, sem repetição; sem ID adiciona turma; omissão remove futuras. Participações mantidas preservam IDs, conclusões e evidências; participações com conclusão não podem ser removidas. Escrita atômica e serializada por evento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe atualizado no envelope ApiResult",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResultEventResponse"))),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "409", description = "Conflito de integridade dos dados"),
            @ApiResponse(responseCode = "400", description = "Evento ou turma não pode ser alterado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado no Workspace"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
    ResponseEntity<ApiResult<EventResponse>> update(
            @Parameter(description = "ID persistido do evento no workspace autenticado", example = "1", schema = @Schema(minimum = "1"))
             Long eventId,
              UpdateEventRequest body,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Cancela definitivamente um evento",
            description = "A ação Inativar do web corresponde ao cancelamento definitivo. Somente o criador de evento ATIVO no workspace pode cancelar. Justificativa obrigatória (3–1000 caracteres após trim); registra status, instante e motivo. Preserva turmas, participações, conclusões e evidências; não permite reativação. Operação atômica e serializada com a edição.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhe cancelado no envelope ApiResult, com permissões falsas",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResultEventResponse"))),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "400", description = "Evento concluído ou já cancelado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado no Workspace"),
            @ApiResponse(responseCode = "403", description = "Não autorizado")
    })
    ResponseEntity<ApiResult<EventResponse>> cancel(
            @Parameter(description = "ID persistido do evento no workspace autenticado", example = "1", schema = @Schema(minimum = "1"))
             Long eventId,
              CancelEventRequest body,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);
}
