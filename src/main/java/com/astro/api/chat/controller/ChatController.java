package com.astro.api.chat.controller;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.chat.dto.response.ChatContactResponse;
import com.astro.api.chat.service.ChatContactService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chats")
public class ChatController {
    private final ChatContactService chatContactService;

    public ChatController(ChatContactService chatContactService) {
        this.chatContactService = chatContactService;
    }

    @GetMapping("/contacts")
    @PreAuthorize("hasAnyRole('COLABORADOR', 'GESTOR', 'GESTOR_WORKSPACE')")
    public ResponseEntity<List<ChatContactResponse>> findContacts(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(chatContactService.findContacts(authenticatedUser));
    }
}
