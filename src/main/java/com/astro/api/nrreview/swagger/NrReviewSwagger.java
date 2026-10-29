package com.astro.api.nrreview.swagger;

import com.astro.api.common.response.ApiResult;
import com.astro.api.nrreview.dto.NrApprovalRequest;
import com.astro.api.nrreview.dto.NrApprovalResult;
import com.astro.api.nrreview.dto.NrSnapshot;
import com.astro.api.nrreview.dto.NrUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Tag(name = "Revisão de NRs", description = "Revisão e aprovação do catálogo, exclusiva para ADMIN.")
public interface NrReviewSwagger {

    @Operation(summary = "Lista NRs pendentes de revisão")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ApiResult<List<NrSnapshot>> pending(HttpServletRequest request);

    @Operation(summary = "Consulta uma NR pendente de revisão")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ApiResult<NrSnapshot> candidate( int id, HttpServletRequest request);

    @Operation(summary = "Lista o catálogo de NRs aprovadas")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ApiResult<List<NrSnapshot>> catalog(HttpServletRequest request);

    @Operation(summary = "Consulta uma NR aprovada")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ApiResult<NrSnapshot> approved( int id, HttpServletRequest request);

    @Operation(summary = "Atualiza uma NR do catálogo aprovado")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ApiResult<NrSnapshot> update( int id,   NrUpdateRequest requestBody,
            HttpServletRequest request);

    @Operation(summary = "Aprova uma NR pendente")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ApiResult<NrApprovalResult> approve( int id,   NrApprovalRequest requestBody,
            HttpServletRequest request);
}
