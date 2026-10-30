package com.astro.api.user.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.user.dto.request.CollaboratorStatusRequest;
import com.astro.api.user.dto.request.CreateCollaboratorRequest;
import com.astro.api.user.dto.request.UpdateCollaboratorRequest;
import com.astro.api.user.dto.response.CollaboratorDetailsResponse;
import com.astro.api.user.dto.response.CollaboratorEventsResponse;
import com.astro.api.user.dto.response.CollaboratorPendingCountResponse;
import com.astro.api.user.dto.response.CollaboratorProfileResponse;
import com.astro.api.user.dto.response.CollaboratorSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Colaboradores", description = "Cadastro e gestão de colaboradores no escopo do gestor.")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Parâmetro ou corpo inválido"),
        @ApiResponse(responseCode = "403", description = "Autenticação ausente ou inválida, ou sem permissão de gestor ativo"),
        @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
})
public interface CollaboratorSwagger {

    @Operation(summary = "Lista colaboradores do escopo do gestor",
            description = "Inclui COLABORADOR, GESTOR e GESTOR_WORKSPACE no workspace e unidades autorizadas, ordenados por nome e ID, com os três status, URL de foto e iniciais. canManage informa permissão para edição e status. excludeSelf=true oculta somente o usuário autenticado desta listagem; o padrão false mantém todos para seleções de participantes e conformidades. Não expõe CPF nem dados de autenticação.")
    @ApiResponse(responseCode = "200", description = "Lista de colaboradores, possivelmente vazia")
    ResponseEntity<ApiResult<List<CollaboratorSummaryResponse>>> findAll(
             AuthenticatedUser user,
             boolean excludeSelf, HttpServletRequest request);

    @Operation(summary = "Lista colaboradores do workspace com a contagem de eventos pendentes",
            description = "Inclui COLABORADOR, GESTOR e GESTOR_WORKSPACE das unidades autorizadas no workspace do gestor autenticado; exclui o próprio gestor. Retorna id, name, profilePhotoUrl, roleName e pendingCount. A contagem considera cada evento uma vez, eventos cancelados e prazos expirados são ignorados, e eventos em análise não contam como pendentes. Usuários inativos ou pré-cadastrados retornam contagem zero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo dos colaboradores, possivelmente vazio"),
            @ApiResponse(responseCode = "403", description = "Autenticação ausente ou inválida, ou solicitante não é gestor ativo")
    })
    ResponseEntity<ApiResult<List<CollaboratorPendingCountResponse>>> pendingCount(
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Consulta detalhes do colaborador para informações e edição",
            description = "Inclui COLABORADOR, GESTOR e GESTOR_WORKSPACE, com CPF, modalidade, URL de foto, iniciais e canManage; restringe a consulta ao workspace e às unidades autorizadas. Ambos os gestores podem consultar os três perfis. Não expõe dados de autenticação.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Detalhes do colaborador"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou fora do escopo")})
    ResponseEntity<ApiResult<CollaboratorDetailsResponse>> findById(
             Long collaboratorId,  AuthenticatedUser user,
            HttpServletRequest request);

    @Operation(summary = "Lista os eventos atribuídos a um colaborador",
            description = "Retorna todas as participações do colaborador, incluindo eventos cancelados e concluídos. O status da conclusão usa PENDENTE quando ainda não há conclusão e EM_ANALISE quando foi enviada para validação. Restringe a consulta ao workspace e às unidades autorizadas do gestor autenticado.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Eventos retornados, possivelmente vazio"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou fora do escopo")})
    ResponseEntity<ApiResult<CollaboratorEventsResponse>> findEventsById(
             Long collaboratorId,  AuthenticatedUser user,
            HttpServletRequest request);

    @Operation(summary = "Consulta o perfil de um colaborador pelo ID",
            description = "Retorna nome, cargo, foto, unidade, modalidade, e-mail, quantidade de eventos pendentes atribuídos e não respondidos e as NRs ativas vinculadas ao cargo com validade individual. Aceita COLABORADOR, GESTOR e GESTOR_WORKSPACE; restringe a consulta ao workspace e às unidades autorizadas do gestor autenticado.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Perfil retornado"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou fora do escopo")})
    ResponseEntity<ApiResult<CollaboratorProfileResponse>> findProfileById(
             Long collaboratorId,  AuthenticatedUser user,
            HttpServletRequest request);

    @Operation(summary = "Pré-cadastra um colaborador",
            description = "Exige nome, CPF válido, e-mail, roleId, unitId e modalidade. Normaliza CPF e e-mail, valida vínculos ativos e cria COLABORADOR/PRE_CADASTRADO sem senha. Reutiliza a fila de conclusão do cadastro.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Colaborador pré-cadastrado"),
            @ApiResponse(responseCode = "404", description = "Cargo ou unidade inexistente no workspace"),
            @ApiResponse(responseCode = "409", description = "CPF ou e-mail já cadastrado")})
    ResponseEntity<ApiResult<CollaboratorDetailsResponse>> create(
              CreateCollaboratorRequest body,  AuthenticatedUser user,
            HttpServletRequest request);

    @Operation(summary = "Solicita o reenvio da chave de acesso do colaborador",
            description = "Exige gestor ativo e acesso ao workspace e à unidade. Aceita apenas COLABORADOR/PRE_CADASTRADO sem Firebase UID. Inclui o e-mail armazenado na mesma fila Redis do cadastro; não gera a chave nem confirma a entrega. Falhas do Redis são propagadas.")
    @ApiResponses({@ApiResponse(responseCode = "202", description = "E-mail incluído na fila de pré-cadastro"),
            @ApiResponse(responseCode = "400", description = "Usuário não elegível para reenvio"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou fora do escopo")})
    ResponseEntity<ApiResult<Void>> resendAccessKey(Long collaboratorId, AuthenticatedUser user,
            HttpServletRequest request);

    @Operation(summary = "Edita todos os campos administrativos do colaborador",
            description = "Gestor do workspace pode editar os três perfis no escopo; gestor comum pode editar colaboradores e sua própria conta, mas recebe 403 para outros gestores e gestor do workspace. Exige todos os campos editáveis não nulos, incluindo status. Mantém vínculos inativos quando não alterados. Preserva ID, tipo, criado_em e Firebase UID. Mudanças de e-mail vinculadas ao Firebase usam compensação em caso de falha.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Colaborador atualizado"),
            @ApiResponse(responseCode = "404", description = "Colaborador, cargo ou unidade inexistente no escopo"),
            @ApiResponse(responseCode = "409", description = "CPF ou e-mail em conflito"),
            @ApiResponse(responseCode = "500", description = "Falha de sincronização com a autenticação")})
    ResponseEntity<ApiResult<CollaboratorDetailsResponse>> update( Long collaboratorId,
              UpdateCollaboratorRequest body,  AuthenticatedUser user,
            HttpServletRequest request);

    @Operation(summary = "Altera explicitamente o status do colaborador",
            description = "Gestor do workspace pode administrar os três perfis no escopo; gestor comum não pode alterar o status de outros gestores ou do gestor do workspace (403). Aceita ATIVO ou DESATIVADO. Repetir o status atual é idempotente. Ativação exige cadastro concluído e cargo/unidade ativos; não conclui pré-cadastro. A desativação impede operações protegidas na próxima requisição.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Status aplicado, com detalhes do colaborador"),
            @ApiResponse(responseCode = "404", description = "Colaborador inexistente ou fora do escopo")})
    ResponseEntity<ApiResult<CollaboratorDetailsResponse>> changeStatus( Long collaboratorId,
              CollaboratorStatusRequest body,  AuthenticatedUser user,
            HttpServletRequest request);
}
