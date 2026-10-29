package com.astro.api.conformity.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.conformity.dto.request.ConformityApplicabilityRequest;
import com.astro.api.conformity.dto.request.CreateConformityRequest;
import com.astro.api.conformity.dto.request.UpdateConformityRequest;
import com.astro.api.conformity.dto.response.ConformityImportResponse;
import com.astro.api.conformity.dto.response.ConformityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Conformidades de colaboradores", description = "Conformidades individuais, validade e evidências.")
@ApiResponses({@ApiResponse(responseCode = "400", description = "Requisição inválida"),
        @ApiResponse(responseCode = "401", description = "Autenticação ausente ou inválida"),
        @ApiResponse(responseCode = "403", description = "Gestor ativo necessário"),
        @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")})
public interface CollaboratorConformitySwagger {

    @Operation(summary = "Importa conformidades de uma planilha Excel",
            description = "Parte multipart file: XLS/XLSX até 10 MB, primeira aba, cabeçalhos CPF, NR e Data de validade. Aceita datas reais do Excel ou dd/MM/yyyy e yyyy-MM-dd; data vazia e fórmulas são rejeitadas. Normaliza CPF com zeros à esquerda. A primeira linha válida confirmada do CPF/NR prevalece; repetições são rejeitadas. Sem aplicável, cria MANUAL; com aplicáveis, atualiza somente a validade do representante (maior validade, nulos por último, maior ID). Cada linha usa transação independente; retorna totais e rejeições por linha. Novas inclusões exigem colaborador ATIVO e NR não revogada; atualização de histórico autorizado preserva origem, aplicabilidade e conclusão.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Resultado do lote, inclusive rejeições"),
            @ApiResponse(responseCode = "413", description = "Arquivo maior que 10 MB")})
    ResponseEntity<ApiResult<ConformityImportResponse>> importFile( MultipartFile file,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Define explicitamente a aplicabilidade",
            description = "Recebe applicable obrigatório (true/false). Repetir o valor é idempotente. Atualiza somente aplicavel, preservando validade, origem, conclusão e histórico. Reativação exige colaborador ATIVO, NR não revogada e ausência de outro aplicável do par. Desativar o ID pode revelar outro registro aplicável do histórico na próxima listagem.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Aplicabilidade definida"),
            @ApiResponse(responseCode = "404", description = "Conformidade inexistente ou fora do escopo"),
            @ApiResponse(responseCode = "409", description = "Outra conformidade aplicável do par")})
    ResponseEntity<ApiResult<ConformityResponse>> changeApplicability( Long conformityId,
              ConformityApplicabilityRequest body,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Edita NR e validade de um registro",
            description = "Exige nrId e expiresAt no corpo; expiresAt nulo remove a validade e campo ausente é inválido. Mantém colaborador, ID, origem, aplicabilidade e conclusão. Ao trocar NR de aplicável, rejeita conflitos; a nova NR não pode estar revogada. NR de evento deve corresponder à conclusão vinculada. Não altera conclusao_evento. Retorna pelo ID editado, mesmo fora da representação da view.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Conformidade atualizada"),
            @ApiResponse(responseCode = "404", description = "Conformidade fora do escopo ou NR inexistente"),
            @ApiResponse(responseCode = "409", description = "Outra conformidade aplicável da NR de destino")})
    ResponseEntity<ApiResult<ConformityResponse>> update( Long conformityId,
              UpdateConformityRequest body,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Cadastra uma conformidade manual",
            description = "Recebe collaboratorId, nrId e expiresAt opcional/nulo. Exige colaborador ATIVO no escopo e NR existente não revogada. Cria MANUAL, aplicável, sem conclusão vinculada. Rejeita qualquer registro aplicável do par; mantém os anteriores não aplicáveis. Usa transação com bloqueio de escrita.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Conformidade criada"),
            @ApiResponse(responseCode = "404", description = "Colaborador fora do escopo ou NR inexistente"),
            @ApiResponse(responseCode = "409", description = "Registro aplicável já existente")})
    ResponseEntity<ApiResult<ConformityResponse>> create(  CreateConformityRequest body,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Consulta um registro de conformidade pelo ID",
            description = "Consulta a tabela e seus relacionamentos no escopo autorizado. Retorna registros não aplicáveis e históricos que já não representam o par na view. Preserva origem e conclusão vinculada.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Detalhes da conformidade"),
            @ApiResponse(responseCode = "404", description = "Registro inexistente ou fora do escopo")})
    ResponseEntity<ApiResult<ConformityResponse>> findById( Long conformityId,
             AuthenticatedUser principal, HttpServletRequest request);

    @Operation(summary = "Lista conformidades aplicáveis no escopo autorizado",
            description = "Consulta vw_conformidade_colaborador: uma linha por colaborador/NR, priorizando aplicáveis, maior validade (nulos por último) e maior ID. Inclui histórico de colaboradores inativos e NRs revogadas. Lista completa ordenada por nome, colaborador, NR e ID. Status segue CURRENT_DATE da sessão PostgreSQL; data nula significa Sem validade.")
    @ApiResponse(responseCode = "200", description = "Lista de conformidades, possivelmente vazia")
    ResponseEntity<ApiResult<List<ConformityResponse>>> findAll(
             AuthenticatedUser principal, HttpServletRequest request);
}
