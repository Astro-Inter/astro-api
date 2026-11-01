package com.astro.api.role.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.role.dto.request.RoleRequest;
import com.astro.api.role.dto.request.UpdateRoleNrsRequest;
import com.astro.api.role.dto.response.RoleNrOptionResponse;
import com.astro.api.role.dto.response.RoleNrResponse;
import com.astro.api.role.dto.response.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Cargos", description = "Gestão de cargos e das NRs vinculadas.")
public interface RoleSwagger {

    @Operation(summary = "Cria cargo no workspace do gestor")
    @ApiResponse(responseCode = "201", description = "Recurso criado")
    ResponseEntity<ApiResult<Void>> create(  RoleRequest body,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Alterna o status do cargo entre ativo e inativo")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<Boolean>> toggleStatus( Long roleId,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Atualiza nome e status do cargo")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<RoleResponse>> update( Long roleId,
              RoleRequest body,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Vincula ou desvincula as NRs informadas do cargo",
            description = "Rejeita NRs marcadas ausentes do catálogo relacional e ignora itens desmarcados ausentes. NRs omitidas mantêm seus vínculos.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<Void>> updateNrs( Long roleId,
              UpdateRoleNrsRequest body,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Lista cargos do workspace do gestor")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<RoleResponse>>> findAll(
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Consulta NRs vinculadas ao cargo")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<RoleNrResponse>>> findNrs(
             Long roleId,
             AuthenticatedUser user, HttpServletRequest request);

    @Operation(summary = "Lista opções de NRs e indica os vínculos do cargo",
            description = "Oferece NRs de colaboradores presentes no catálogo relacional e preserva as NRs já vinculadas para permitir sua remoção.")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<RoleNrOptionResponse>>> findNrOptions(
             Long roleId,
             AuthenticatedUser user, HttpServletRequest request);
}
