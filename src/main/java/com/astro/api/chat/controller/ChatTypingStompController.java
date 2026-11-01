package com.astro.api.chat.controller;

import com.astro.api.chat.dto.request.ChatTypingRequest;
import com.astro.api.chat.dto.response.ChatSendErrorResponse;
import com.astro.api.chat.security.ChatPrincipal;
import com.astro.api.chat.service.ChatTypingService;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ResourceNotFoundException;
import java.security.Principal;
import com.mongodb.MongoException;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

@Controller
public class ChatTypingStompController {
    private final ChatTypingService service;

    public ChatTypingStompController(ChatTypingService service) { this.service = service; }

    @MessageMapping("/chat.typing")
    public void typing(@Payload ChatTypingRequest request, Principal principal) {
        if (!(principal instanceof ChatPrincipal chatPrincipal)) throw new AccessDeniedException("Sessão não autenticada");
        service.notifyTyping(chatPrincipal, request);
    }

    @MessageExceptionHandler({AccessDeniedException.class, ResourceNotFoundException.class,
            BusinessException.class, IllegalArgumentException.class, DataAccessException.class, MongoException.class})
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatSendErrorResponse handleTypingError(Exception exception) {
        String code = "TYPING_UNAVAILABLE";
        if (exception instanceof AccessDeniedException || exception instanceof ResourceNotFoundException) code = "TYPING_FORBIDDEN";
        else if (exception instanceof BusinessException || exception instanceof IllegalArgumentException) code = "TYPING_INVALID_REQUEST";
        return new ChatSendErrorResponse(null, code, "Não foi possível atualizar o indicador de digitação");
    }
}
