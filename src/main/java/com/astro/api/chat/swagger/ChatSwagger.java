package com.astro.api.chat.swagger;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.chat.dto.response.ChatContactResponse;
import com.astro.api.chat.dto.response.ChatMessagePageResponse;
import com.astro.api.chat.dto.response.ChatResponse;
import com.astro.api.common.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "Conversas", description = "Contatos, conversas, histórico e confirmação de leitura.")
public interface ChatSwagger {

    @Operation(summary = "Lista as conversas do usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    List<ChatResponse> findChats( AuthenticatedUser authenticatedUser);

    @Operation(summary = "Lista contatos disponíveis para o usuário autenticado")
    @ApiResponse(responseCode = "200", description = "Operação concluída com sucesso")
    ResponseEntity<ApiResult<List<ChatContactResponse>>> findContacts(
             AuthenticatedUser authenticatedUser, HttpServletRequest request);

    @Operation(summary = "Consulta o histórico paginado de uma conversa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensagens retornadas em ordem cronológica"),
            @ApiResponse(responseCode = "400", description = "Identificador, limite ou cursor inválido"),
            @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não participante ou conversa fora do Workspace"),
            @ApiResponse(responseCode = "404", description = "Conversa ou colaborador não encontrado"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<ApiResult<ChatMessagePageResponse>> findMessages(
             UUID conversationId,
             int limit,
             String before,
             AuthenticatedUser authenticatedUser,
            HttpServletRequest request);

    @Operation(summary = "Marca a conversa como lida para o usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contador de mensagens não lidas removido"),
            @ApiResponse(responseCode = "400", description = "Identificador da conversa inválido"),
            @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não participante ou conversa fora do Workspace"),
            @ApiResponse(responseCode = "404", description = "Conversa ou colaborador não encontrado"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    ResponseEntity<Void> markAsRead( String conversationId,
                                            AuthenticatedUser authenticatedUser);
}
