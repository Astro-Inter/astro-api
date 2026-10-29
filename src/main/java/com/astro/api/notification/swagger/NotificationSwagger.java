package com.astro.api.notification.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.common.response.ApiResult;
import com.astro.api.notification.dto.response.NotificationDetailsResponse;
import com.astro.api.notification.dto.response.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Notificações", description = "Consulta e confirmação de visualização de notificações.")
public interface NotificationSwagger {

    @Operation(summary = "Lista todas as notificações do usuário autenticado",
            description = "Requer autenticação. A consulta não altera o estado de visualização das notificações.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificações retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Usuário autenticado não encontrado")
    })
    ResponseEntity<ApiResult<List<NotificationResponse>>> findAll(
             AuthenticatedUser authenticatedUser, HttpServletRequest request);

    @Operation(summary = "Consulta uma notificação do usuário autenticado pelo ID",
            description = "Retorna a mensagem, o título e o ID do evento associado, quando houver. A consulta não altera o estado de visualização.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificação retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Notificação inexistente ou não pertencente ao usuário"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<NotificationDetailsResponse>> findById(
             String notificationId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Marca uma notificação do usuário autenticado como visualizada",
            description = "A notificação é identificada pelo ID e a operação é idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificação marcada como visualizada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Não autorizado"),
            @ApiResponse(responseCode = "404", description = "Notificação inexistente ou não pertencente ao usuário"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<Void>> markAsViewed(
             String notificationId,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);
}
