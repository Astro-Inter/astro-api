package com.astro.api.chat.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.chat.dto.response.ChatContactResponse;
import com.astro.api.chat.dto.response.ChatMessagePageResponse;
import com.astro.api.chat.dto.response.ChatResponse;
import com.astro.api.chat.service.ChatContactService;
import com.astro.api.chat.service.ChatService;
import com.astro.api.common.response.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chats")
public class ChatController {
    private final ChatService chatService;
    private final ChatContactService chatContactService;

    public ChatController(ChatService chatService, ChatContactService chatContactService) {
        this.chatService = chatService;
        this.chatContactService = chatContactService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public List<ChatResponse> findChats(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return chatService.findChats(authenticatedUser);
    }

    @GetMapping("/contacts")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<ApiResult<List<ChatContactResponse>>> findContacts(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Contatos retornados com sucesso",
                chatContactService.findContacts(authenticatedUser), request.getRequestURI()));
    }

    @GetMapping("/{conversationId}/messages")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    @Operation(summary = "Consulta o histórico paginado de uma conversa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensagens retornadas em ordem cronológica"),
            @ApiResponse(responseCode = "400", description = "Identificador, limite ou cursor inválido"),
            @ApiResponse(responseCode = "401", description = "Token Firebase ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não participante ou conversa fora do Workspace"),
            @ApiResponse(responseCode = "404", description = "Conversa ou colaborador não encontrado"),
            @ApiResponse(responseCode = "503", description = "Serviço de dados indisponível")
    })
    public ResponseEntity<ApiResult<ChatMessagePageResponse>> findMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(required = false) String before,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResult.success("Mensagens retornadas com sucesso",
                chatService.findMessages(authenticatedUser, conversationId.toString(), limit, before),
                request.getRequestURI()));
    }
}
