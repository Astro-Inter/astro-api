package com.astro.api.chat.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.chat.dto.response.ChatContactResponse;
import com.astro.api.chat.mapper.ChatContactMapper;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.model.User;
import com.astro.api.user.model.UserStatus;
import com.astro.api.user.model.UserType;
import com.astro.api.user.repository.UserRepository;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatContactService {
    private static final List<UserType> ALL_CHAT_TYPES = List.of(
            UserType.COLABORADOR, UserType.GESTOR, UserType.GESTOR_WORKSPACE);
    private static final List<UserType> MANAGER_TYPES = List.of(
            UserType.GESTOR, UserType.GESTOR_WORKSPACE);

    private final UserRepository userRepository;
    private final ChatContactMapper chatContactMapper;

    public ChatContactService(UserRepository userRepository, ChatContactMapper chatContactMapper) {
        this.userRepository = userRepository;
        this.chatContactMapper = chatContactMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatContactResponse> findContacts(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.firebaseUid() == null
                || authenticatedUser.firebaseUid().isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar contatos");
        }

        User currentUser = userRepository.findByFirebaseUid(authenticatedUser.firebaseUid())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado"));
        if (currentUser.getId() == null || currentUser.getStatus() == UserStatus.DESATIVADO
                || currentUser.getType() == null || currentUser.getUnit() == null
                || currentUser.getUnit().workspace == null
                || currentUser.getUnit().workspace.id == null
                || authenticatedUser.role() == null
                || !currentUser.getType().name().equals(authenticatedUser.role().name())) {
            throw new AccessDeniedException("Usuário ativo com tipo e workspace válidos necessário");
        }

        Long workspaceId = currentUser.getUnit().workspace.id;
        List<UserType> allowedTypes = currentUser.getType() == UserType.COLABORADOR
                ? MANAGER_TYPES : ALL_CHAT_TYPES;

        return userRepository.findChatContacts(workspaceId, currentUser.getId(), UserStatus.DESATIVADO, allowedTypes)
                .stream()
                .map(chatContactMapper::toResponse)
                .toList();
    }
}
