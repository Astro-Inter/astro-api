package com.astro.api.conformity.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.conformity.dto.request.*;
import com.astro.api.conformity.dto.response.UnitConformityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Conformidades de unidades", description = "Conformidades aplicáveis às unidades e seu histórico.")
@ApiResponses({@ApiResponse(responseCode="400", description="Dados inválidos"),
        @ApiResponse(responseCode="401", description="Autenticação necessária"),
        @ApiResponse(responseCode="403", description="Gestor ativo necessário"),
        @ApiResponse(responseCode="404", description="Recurso fora do escopo ou inexistente"),
        @ApiResponse(responseCode="409", description="Conflito de conformidade"),
        @ApiResponse(responseCode="503", description="Dados indisponíveis")})
public interface UnitConformitySwagger {

    @Operation(summary="Lista conformidades aplicáveis de unidades", description="Um representante por unidade/NR, restrito ao workspace e às unidades autorizadas, ordenado por nome. Inclui unidades inativas e NRs históricas.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<UnitConformityResponse>>> list( AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary="Consulta uma conformidade de unidade", description="Permite consultar o histórico autorizado, inclusive registros não aplicáveis.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<UnitConformityResponse>> details( Long id,  AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary="Cria conformidade manual de unidade", description="Exige unidade ativa autorizada e NR vigente; rejeita outro aplicável do par. Mantém o histórico.")
    @ApiResponse(responseCode="201", description="Conformidade criada")
    ResponseEntity<ApiResult<UnitConformityResponse>> create(  CreateUnitConformityRequest body,  AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary="Edita NR e validade da conformidade de unidade", description="Exige expiresAt presente, aceitando null. Preserva destinatário e evidência; não permite trocar a NR vinculada a formulário.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<UnitConformityResponse>> update( Long id,   UpdateConformityRequest body,  AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary="Define aplicabilidade da conformidade de unidade", description="Idempotente por ID. Reativar exige unidade ativa, NR vigente e ausência de outro aplicável. Não exclui histórico.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<UnitConformityResponse>> applicability( Long id,   ConformityApplicabilityRequest body,  AuthenticatedUser user, HttpServletRequest request);
}
