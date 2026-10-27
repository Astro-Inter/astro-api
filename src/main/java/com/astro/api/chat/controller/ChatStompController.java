package com.astro.api.chat.controller;

import com.astro.api.chat.dto.request.ChatSendRequest;
import com.astro.api.chat.dto.response.ChatSendErrorResponse;
import com.astro.api.chat.dto.response.ChatSendResponse;
import com.astro.api.chat.security.ChatPrincipal;
import com.astro.api.chat.service.ChatService;
import com.astro.api.chat.service.ChatSendException;
import com.astro.api.chat.service.ChatDeliveryUnavailableException;
import com.astro.api.chat.service.ChatUnreadCounterException;
import com.astro.api.common.exception.BusinessException;
import com.astro.api.common.exception.ConflictException;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.mongodb.MongoException;
import java.security.Principal;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

@Controller
public class ChatStompController {
    private final ChatService chatService;

    public ChatStompController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat.send")
    @SendToUser(value = "/queue/messages", broadcast = false)
    public ChatSendResponse send(@Payload ChatSendRequest request, Principal principal) {
        String clientMessageId = request == null ? null : request.getClientMessageId();
        if (!(principal instanceof ChatPrincipal chatPrincipal)) {
            throw new ChatSendException(clientMessageId, "UNAUTHENTICATED", "Sessão STOMP não autenticada", null);
        }
        try {
            return chatService.send(chatPrincipal, request);
        } catch (ChatSendException exception) {
            throw exception;
        } catch (AccessDeniedException exception) {
            throw new ChatSendException(clientMessageId, "FORBIDDEN", exception.getMessage(), exception);
        } catch (ResourceNotFoundException exception) {
            throw new ChatSendException(clientMessageId, "NOT_FOUND", exception.getMessage(), exception);
        } catch (ConflictException exception) {
            throw new ChatSendException(clientMessageId, "CONFLICT", exception.getMessage(), exception);
        } catch (BusinessException | IllegalArgumentException exception) {
            throw new ChatSendException(clientMessageId, "INVALID_REQUEST", exception.getMessage(), exception);
        } catch (ChatUnreadCounterException exception) {
            throw new ChatSendException(clientMessageId, "UNREAD_COUNTER_UNAVAILABLE",
                    "Mensagem persistida; contador não confirmado. Reenvie usando o mesmo clientMessageId.", exception);
        } catch (ChatDeliveryUnavailableException exception) {
            throw new ChatSendException(clientMessageId, "DELIVERY_UNAVAILABLE",
                    "Mensagem persistida e contada; entrega ao destinatário não confirmada. Reenvie usando o mesmo clientMessageId.", exception);
        } catch (MongoException | DataAccessException exception) {
            throw new ChatSendException(clientMessageId, "PERSISTENCE_UNAVAILABLE",
                    "Não foi possível persistir a mensagem. Reenvie usando o mesmo clientMessageId.", exception);
        } catch (RuntimeException exception) {
            throw new ChatSendException(clientMessageId, "SEND_FAILED",
                    "Não foi possível concluir o envio. Reenvie usando o mesmo clientMessageId.", exception);
        }
    }

    @MessageExceptionHandler(ChatSendException.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatSendErrorResponse handleSendError(ChatSendException exception) {
        return new ChatSendErrorResponse(exception.clientMessageId(), exception.code(), exception.getMessage());
    }
}
