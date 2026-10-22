package com.astro.api.chat.service;

import com.astro.api.auth.security.AuthenticatedUser;
import com.astro.api.chat.dto.response.ChatLastMessageResponse;
import com.astro.api.chat.dto.response.ChatParticipantResponse;
import com.astro.api.chat.dto.response.ChatResponse;
import com.astro.api.chat.model.ConversationDocument;
import com.astro.api.chat.repository.ChatMongoRepository;
import com.astro.api.chat.repository.LastMessageProjection;
import com.astro.api.user.repository.UserChatProjection;
import com.astro.api.common.exception.ResourceNotFoundException;
import com.astro.api.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ChatService {
    private final UserRepository userRepository;
    private final ChatMongoRepository chatMongoRepository;
    private final StringRedisTemplate redisTemplate;

    public ChatService(UserRepository userRepository, ChatMongoRepository chatMongoRepository,
                       StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.chatMongoRepository = chatMongoRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> findChats(AuthenticatedUser authenticatedUser) {
        String uid = authenticatedUser == null ? null : authenticatedUser.firebaseUid();
        if (uid == null || uid.isBlank()) {
            throw new AccessDeniedException("Usuário autenticado necessário para consultar conversas");
        }
        UserChatProjection currentUser = userRepository.findChatUserByFirebaseUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado"));
        if (currentUser.getWorkspaceId() == null) {
            throw new AccessDeniedException("Colaborador autenticado sem Workspace associado");
        }

        List<ConversationDocument> conversations = chatMongoRepository.findConversationsFor(currentUser.getId());
        if (conversations.isEmpty()) {
            return List.of();
        }

        Map<String, Long> otherParticipantByConversation = new LinkedHashMap<>();
        Set<Long> participantIds = new LinkedHashSet<>();
        for (ConversationDocument conversation : conversations) {
            if (conversation.id() == null || conversation.participants() == null
                    || conversation.participants().size() != 2
                    || conversation.participants().stream().filter(currentUser.getId()::equals).count() != 1) {
                continue;
            }
            Long otherId = conversation.participants().stream()
                    .filter(id -> !currentUser.getId().equals(id)).findFirst().orElse(null);
            if (otherId != null) {
                otherParticipantByConversation.put(conversation.id(), otherId);
                participantIds.add(otherId);
            }
        }
        if (otherParticipantByConversation.isEmpty()) {
            return List.of();
        }

        Map<Long, UserChatProjection> participants = new HashMap<>();
        userRepository.findChatUsersByIds(participantIds).forEach(user -> {
            if (user.getWorkspaceId() != null && user.getWorkspaceId().equals(currentUser.getWorkspaceId())) {
                participants.put(user.getId(), user);
            }
        });
        otherParticipantByConversation.entrySet().removeIf(entry -> !participants.containsKey(entry.getValue()));
        if (otherParticipantByConversation.isEmpty()) {
            return List.of();
        }

        List<LastMessageProjection> lastMessages = chatMongoRepository.findLatestMessages(
                otherParticipantByConversation.keySet());
        Map<String, LastMessageProjection> messageByConversation = new HashMap<>();
        lastMessages.forEach(message -> {
            if (message.conversationId() != null && message.date() != null) {
                messageByConversation.put(message.conversationId(), message);
            }
        });
        List<LastMessageProjection> includedMessages = otherParticipantByConversation.keySet().stream()
                .map(messageByConversation::get).filter(message -> message != null)
                .sorted(Comparator.comparing(LastMessageProjection::date).reversed()
                        .thenComparing(LastMessageProjection::conversationId, Comparator.reverseOrder()))
                .toList();
        if (includedMessages.isEmpty()) {
            return List.of();
        }

        List<String> keys = includedMessages.stream()
                .map(message -> unreadKey(message.conversationId(), currentUser.getId())).toList();
        List<String> counts = redisTemplate.opsForValue().multiGet(keys);
        Map<String, Integer> unreadByConversation = new HashMap<>();
        for (int index = 0; index < includedMessages.size(); index++) {
            String count = counts == null || index >= counts.size() ? null : counts.get(index);
            unreadByConversation.put(includedMessages.get(index).conversationId(), parseCount(count));
        }

        List<ChatResponse> response = new ArrayList<>(includedMessages.size());
        for (LastMessageProjection message : includedMessages) {
            UserChatProjection participant = participants.get(
                    otherParticipantByConversation.get(message.conversationId()));
            if (participant == null) continue;
            response.add(new ChatResponse(message.conversationId(),
                    new ChatParticipantResponse(participant.getId(), participant.getName(), participant.getRole()),
                    new ChatLastMessageResponse(message.senderId(), message.content()),
                    unreadByConversation.getOrDefault(message.conversationId(), 0)));
        }
        return List.copyOf(response);
    }

    private String unreadKey(String conversationId, Long userId) {
        return "chat:unread:%s:%d".formatted(conversationId, userId);
    }

    private int parseCount(String count) {
        if (count == null) return 0;
        try {
            int value = Integer.parseInt(count);
            if (value < 0) throw new IllegalStateException("Contador Redis de mensagens não lidas inválido");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Contador Redis de mensagens não lidas inválido", exception);
        }
    }
}
